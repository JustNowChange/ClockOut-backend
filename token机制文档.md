# ClockOut 双 Token 机制文档

> 每一步均附代码位置（文件 + 行号）作为证明。所有行号基于 2026-10-05 当前代码（单文档 JSON 模型 + 单设备互踢）。

## 一、机制概览

采用 **双 Token + Redis 单文档 JSON 白名单 + 刷新真轮换** 模型：短期访问令牌 + 长期刷新令牌，共享同一会话标识 `jti`；每 uid 一个 Redis JSON 文档，**删除文档 = 立即踢下线，覆盖文档 = 新设备挤掉旧设备，刷新 = jti 换代且旧 token 立即作废**。

### Redis 存储结构

| 项 | 内容 |
|---|---|
| key | `auth:user:{uid}`（一 uid 一 key） |
| TTL | 7 天（= 刷新令牌有效期，会话绝对有效期，刷新不续期） |
| value | JSON（[AuthUserState.java](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/un/AuthUserState.java)），含 `session`（jti/loginTime/loginIp）与 `ban`（banType/reason/banEnd） |
| 写入/读取 | [UserCacheUtils.saveAuthState L63-L74](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/utils/UserCacheUtils.java#L63-L74)、[getAuthState L47-L60](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/utils/UserCacheUtils.java#L47-L60) |

JSON 示例：

```json
{
  "session": { "jti": "9f8e7d6a...", "loginTime": 1728000000000, "loginIp": "1.2.3.4" },
  "ban":     { "banType": 0, "reason": "", "banEnd": null }
}
```

### 其余要素

| 要素 | 值 | 代码位置 |
|---|---|---|
| 访问令牌类型标记 | `"access"` | [JwtClaimsConstant.java](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/constant/JwtClaimsConstant.java) |
| 刷新令牌类型标记 | `"refresh"` | [JwtClaimsConstant.java](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/constant/JwtClaimsConstant.java) |
| 用户标识 claim / 会话标识 claim | `uid` / `jti`（access、refresh 共用） | [loginController.java#L63](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L63) |
| 访问令牌 TTL / 请求头 | 30 分钟 / `token` | [application.yaml#L43-L44](file:///h:/项目1/ClockOut/demo/src/main/resources/application.yaml#L43-L44) |
| 刷新令牌 TTL / 请求头 | 7 天 / `RefreshToken` | [application.yaml#L46](file:///h:/项目1/ClockOut/demo/src/main/resources/application.yaml#L46)、[RefreshToken.java#L41](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L41) |
| 拦截器顺序 | JWT → 封禁 → 管理员(仅 /api/admin/**) → 限流 | [WebMvcConfig.java#L33-L65](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/config/WebMvcConfig.java#L33-L65) |
| 不纳入 JSON 的数据 | 限流 `rate_limit:*`（高频 INCR 计数）、简历缓存 | [RateLimitInterceptor.java#L31](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/RateLimitInterceptor.java#L31) |

---

## 二、流程 A：登录签发双 Token + 整包覆盖写

接口：`POST /api/auth/login`（JWT 白名单内）

| 步骤 | 动作 | 代码位置 |
|---|---|---|
| A1 | 账号密码鉴权（查询过滤 `is_delete=0`） | [loginController.java#L44](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L44)、[UserMapper.java#L13](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/mapper/UserMapper.java#L13) |
| A2 | 登录时校验封禁（登录接口不走封禁拦截器，直接查库；**存在任意一条未解封且生效记录即封禁**，取 id 最大一条用于提示） | [loginController.java#L46-L56](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L46-L56)、[UserMapper.java#L20-L25](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/mapper/UserMapper.java#L20-L25) |
| A3 | 记录本次登录时间与 IP（写 user_account） | [loginController.java#L59-L60](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L59-L60) |
| A4 | 生成 jti（本次会话唯一编号） | [loginController.java#L63](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L63) |
| A5 | 签发访问令牌：`uid` + `tokenType=access` + `jti`（30min） | [loginController.java#L66-L73](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L66-L73) |
| A6 | 签发刷新令牌：`uid` + `tokenType=refresh` + **同一 jti**（7 天） | [loginController.java#L76-L83](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L76-L83) |
| A7 | 组装 JSON（session + ban 快照，ban 复用 A2 查询结果） | [loginController.java#L85-L109](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L85-L109) |
| A8 | **整包覆盖写** `auth:user:{uid}`，TTL 7 天；覆盖即挤掉旧设备（无需 SCAN） | [loginController.java#L112](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L112)、[UserCacheUtils.java#L63-L74](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/utils/UserCacheUtils.java#L63-L74) |
| A9 | 双 token 放响应体返回 | [loginController.java#L115-L122](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L115-L122) |

---

## 三、流程 B：业务请求校验（一次 RTT，两层判定）

JWT 拦截器用 **一次 GET** 取回完整 JSON（会话 + 封禁两份数据，原两次 RTT 合并为一次）；封禁判定由 BanInterceptor 读同一对象**纯本地完成**，拦截器不做周期性查库——封禁状态只在封号（DEL key）/解封（Lua 补丁）时改变。

**前端侧（请求发出前）主动预检**：[http.ts#L76-L93](file:///H:/node-v24.18.0-win-x64/node-v24.18.0-win-x64/Workspace/Clock%20Out/src/utils/http.ts#L76-L93) 请求拦截器先本地解 token 的 `exp`，剩余不足 20 秒（`REFRESH_AHEAD_MS`）则**先静默刷新再发原请求**，不产生红色 401、POST 不丢失；主动预检与 401 兜底共用同一刷新并发锁（[startRefresh L139-L162](file:///H:/node-v24.18.0-win-x64/node-v24.18.0-win-x64/Workspace/Clock%20Out/src/utils/http.ts#L139-L162)）。401 响应拦截器保留作为兜底（服务端踢下线/时钟漂移场景）。

| 步骤 | 动作 | 代码位置 |
|---|---|---|
| B1 | JWT 白名单放行（login/register/refresh/logout） | [WebMvcConfig.java#L34-L44](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/config/WebMvcConfig.java#L34-L44) |
| B2 | 非 Controller 方法直接放行 | [JWTtoken.java#L28-L32](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/JWTtoken.java#L28-L32) |
| B3 | 从请求头 `token` 取访问令牌，验签 + 过期校验 | [JWTtoken.java#L35-L42](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/JWTtoken.java#L35-L42) |
| B4 | 校验 `tokenType=access`，刷新令牌一律 401 | [JWTtoken.java#L44-L48](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/JWTtoken.java#L44-L48) |
| B5 | **GET 完整 JSON**（仅有的一次 Redis RTT） | [JWTtoken.java#L53](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/JWTtoken.java#L53) |
| B6 | **会话白名单校验**：JSON 不存在 / session.jti 与 token 不一致（登出/被踢/旧设备）→ 401 + JSON 提示 | [JWTtoken.java#L56-L64](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/JWTtoken.java#L56-L64) |
| B7 | 完整状态通过 request attribute 递给 BanInterceptor；uid 写入 ThreadLocal | [JWTtoken.java#L67-L69](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/JWTtoken.java#L67-L69) |
| B8 | 验签/解析失败 401 | [JWTtoken.java#L73-L78](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/JWTtoken.java#L73-L78) |
| B9 | **请求结束清理 ThreadLocal**（防 Tomcat 线程复用串号） | [JWTtoken.java#L84-L88](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/JWTtoken.java#L84-L88) |
| B10 | BanInterceptor 读递入状态（attribute 缺失时自己 GET 降级），全程不访问 DB | [BanInterceptor.java#L43-L50](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/BanInterceptor.java#L43-L50) |
| B11 | 封禁中（本地判定：2 永久 / 1 临时且 banEnd 未到期）→ 403 + 封禁原因；临时封禁快照到期自动放行 | [BanInterceptor.java#L55-L65](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/BanInterceptor.java#L55-L65) |
| B12 | 管理员校验（仅 /api/admin/**）：`BaseContext.uid == sky.admin-uid` | [AdminInterceptor.java#L27-L49](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/AdminInterceptor.java#L27-L49) |

> Lua 补丁脚本与执行（解封用）：[UserCacheUtils.java#L32-L41](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/utils/UserCacheUtils.java#L32-L41)、[L117-L126](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/utils/UserCacheUtils.java#L117-L126)。

---

## 四、流程 C：刷新真轮换（jti 换代，旧 token 立即失效）

接口：`POST /api/auth/refresh`（JWT 白名单内）

核心机制：**Refresh Token Rotation**——每次刷新生成新 jti，Lua 脚本原子完成「校验旧 jti → ban 快照判定 → 换新 jti」，旧双 token 在轮换完成瞬间全部失效；整个 refresh 仅 1 次 Redis 访问，**0 次数据库查询**。

| 步骤 | 动作 | 代码位置 |
|---|---|---|
| C1 | 从请求头 `RefreshToken` 取刷新令牌 | [RefreshToken.java#L46-L49](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L46-L49) |
| C2 | 验签 + 过期校验，失败提示重新登录 | [RefreshToken.java#L52-L57](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L52-L57) |
| C3 | 校验 `tokenType=refresh`（拿 access 来刷新直接拒绝） | [RefreshToken.java#L59-L62](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L59-L62) |
| C4 | 取出 uid/旧 jti，生成**新 jti** | [RefreshToken.java#L64-L71](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L64-L71) |
| C5 | **Lua 原子轮换**：① 旧 jti 与 JSON 不一致（旧 token 重放/已换代）→ invalid；② ban 快照生效（2 永久 / 1 临时未到期）→ banned；③ 通过则换新 jti，PTTL 保持会话剩余 TTL（绝对有效期不续期） | [UserCacheUtils.java#L49-L67](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/utils/UserCacheUtils.java#L49-L67)、[调用处 L72-L91](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L72-L91) |
| C6 | 用**新 jti** 签发新双 token 返回；前端同时更新本地 access + refresh（旧凭证作废） | [RefreshToken.java#L93-L103](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L93-L103)、[buildTokenPair L127-L148](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L127-L148) |

---

## 五、流程 D：登出（删整 key，双 token 立即失效）

接口：`POST /api/auth/logout`（JWT 白名单 —— access 过期后仍可登出；封禁白名单 —— 被封用户也能登出）

| 步骤 | 动作 | 代码位置 |
|---|---|---|
| D1 | 从请求头取刷新令牌（缺失也允许，幂等） | [RefreshToken.java#L110-L111](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L110-L111) |
| D2 | 解析取 uid，DEL `auth:user:{uid}` 整 key | [RefreshToken.java#L112-L116](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L112-L116)、[UserCacheUtils.java#L107-L111](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/utils/UserCacheUtils.java#L107-L111) |
| D3 | 解析失败不阻塞登出（幂等） | [RefreshToken.java#L117-L120](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L117-L120) |

---

## 六、踢人/封禁管理接口（管理端）

`/api/admin/**` 由 AdminInterceptor 保护（仅 `sky.admin-uid` 放行），operatorUid 取自 BaseContext。

| 接口 | 行为 | 代码位置 |
|---|---|---|
| `GET /api/admin/info` | 当前登录管理员资料 | [admin/AdminController.java#L34-L50](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/admin/AdminController.java#L34-L50) |
| `GET /api/admin/stats` | 总览统计（在线=存在 `auth:user:{uid}` key） | [admin/AdminController.java#L56-L59](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/admin/AdminController.java#L56-L59)、[UserCacheUtils.java#L106-L125](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/utils/UserCacheUtils.java#L106-L125) |
| `GET /api/admin/users` | 用户分页列表（keyword/封禁/在线筛选，参数非法兜底） | [admin/AdminController.java#L65-L91](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/admin/AdminController.java#L65-L91) |
| `POST /api/admin/users/{uid}/ban` | 封禁：**同事务先接管（解除）全部未解封旧记录**再写 `user_ban`（保证唯一生效记录）+ **DEL 整 key（立即踢下线）**；校验 banType/原因/banEnd、禁止自操作 | [admin/AdminController.java#L97-L133](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/admin/AdminController.java#L97-L133)、[UserServiceImpl.java#L157-L178](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/service/serviceImpl/UserServiceImpl.java#L157-L178) |
| `POST /api/admin/users/{uid}/unban` | 解封：**一次性解除全部未解封记录**（多次封禁可能残留多条）+ Lua 补丁 ban | [admin/AdminController.java#L139-L154](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/admin/AdminController.java#L139-L154)、[UserServiceImpl.java#L180-L190](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/service/serviceImpl/UserServiceImpl.java#L180-L190) |
| `POST /api/admin/users/{uid}/kick` | 踢人：DEL 整 key 不写封禁，立即 401 | [admin/AdminController.java#L160-L178](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/admin/AdminController.java#L160-L178)、[UserServiceImpl.java#L195-L197](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/service/serviceImpl/UserServiceImpl.java#L195-L197) |

### 踢人生效路径总结

| 场景 | 触发动作 | 生效延迟 |
|---|---|---|
| 用户登出 | DEL 整 key | 立即 |
| 管理员踢人 | DEL 整 key | 立即（下次请求 401） |
| 管理员封禁 | 写库 + DEL 整 key | 立即（0 秒） |
| 同账号再次登录（任意设备） | SET 整包覆盖，jti 变更 | 立即（旧设备下次请求 401） |
| 管理员解封 | 写库 + Lua 补丁 banType=0（在线时） | 立即 |
| 临时封禁到期 | 封号时 key 已删；到期后用户重新登录，登录查库判定已失效才放行 | 重新登录时生效 |
| 7 天未活跃 | key TTL 自然过期 | 到期即生效 |

---

## 七、安全机制

### 7.0 封禁判定语义与联合索引

封禁 = `user_ban` 中**存在任意一条** `revoke_time IS NULL` 且（`ban_type=2` 永久，或 `ban_type=1` 且 `ban_end > NOW()`）的记录。**新封禁插入前先接管（解除）该 uid 全部未解封旧记录**（同事务），因此正常数据下每 uid 只有一条生效记录，banType 筛选与行内展示天然一致；`COUNT(DISTINCT uid)` 作为防御性去重保留。

| 查询 | 命中索引 |
|---|---|
| 单 uid 校验（登录/refresh） | `idx_uid(uid)` |
| 全量聚合（封禁人数/列表装配） | `idx_revoke_uid(revoke_time, uid)`：IS NULL 走索引前缀，uid 有序支持 DISTINCT |

代码：[AdminUserMapper.xml#L9-L16](file:///h:/项目1/ClockOut/demo/src/main/resources/mapper/AdminUserMapper.xml#L9-L16)、接管逻辑 [UserServiceImpl.java#L161-L164](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/service/serviceImpl/UserServiceImpl.java#L161-L164)、[UserServiceImpl.java#L128-L129](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/service/serviceImpl/UserServiceImpl.java#L128-L129)。

### 7.1 单文档白名单

Redis 有文档且 jti 匹配才放行 → 登出/踢人/封禁删 key、新设备登录覆盖 jti，未过期 token 也立即失效。

| 机制点 | 代码位置 |
|---|---|
| 业务请求 jti 校验 | [JWTtoken.java#L56-L64](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/JWTtoken.java#L56-L64) |
| refresh 同样校验（原子轮换内） | [RefreshToken.java#L72-L91](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L72-L91) |

### 7.2 双 Token 类型隔离

| 机制点 | 代码位置 |
|---|---|
| 业务接口拒收 refresh（401） | [JWTtoken.java#L44-L48](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/JWTtoken.java#L44-L48) |
| 刷新接口拒收 access | [RefreshToken.java#L59-L62](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/RefreshToken.java#L59-L62) |

### 7.3 单设备互踢 + Lua 防竞态

每 uid 唯一 key：登录覆盖即互踢，无需 SCAN；解封经 Lua 原子补丁 ban，不会覆盖 session。拦截器全程不查库。

| 机制点 | 代码位置 |
|---|---|
| 登录整包覆盖 | [loginController.java#L112](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L112) |
| 封号 DEL 整 key | [UserServiceImpl.java#L178](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/service/serviceImpl/UserServiceImpl.java#L178) |
| 解封 Lua 补丁 ban | [UserCacheUtils.java#L117-L126](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/utils/UserCacheUtils.java#L117-L126) |

### 7.4 ThreadLocal 卫生

[JWTtoken.java#L84-L88](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/JWTtoken.java#L84-L88)。

---

## 八、配置基础

| 配置项 | 代码位置 |
|---|---|
| `@ConfigurationProperties(prefix="sky.jwt")` | [JwtProperties.java#L7-L17](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/properties/JwtProperties.java#L7-L17) |
| 密钥 / access TTL / 请求头名 / refresh TTL | [application.yaml#L41-L46](file:///h:/项目1/ClockOut/demo/src/main/resources/application.yaml#L41-L46) |
| 管理员 uid（环境变量 `ADMIN_UID`） | [application.yaml#L48](file:///h:/项目1/ClockOut/demo/src/main/resources/application.yaml#L48) |

## 九、已知边界

| 边界 | 说明 | 相关位置 |
|---|---|---|
| 轮换"最后一跳"风险 | 服务端 Lua 已换代但响应丢失时，旧 token 全部失效，用户需重新登录（真轮换固有取舍，前端并发锁已将概率降到最低） | [UserCacheUtils.java#L63-L67](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/utils/UserCacheUtils.java#L63-L67) |
| 会话绝对有效期 7 天 | 刷新 PTTL 保持剩余时间、不续期，到期强制重登 | [UserCacheUtils.java#L64-L66](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/utils/UserCacheUtils.java#L64-L66) |
| 直接改数据库不会作用于在线会话 | 拦截器不做周期性查库；封禁/解封必须走管理端接口（写库 + 操作 Redis）才即时生效；绕过接口改库，用户重新登录时才会查到 | [BanInterceptor.java#L55-L56](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/BanInterceptor.java#L55-L56) |
| 被挤下线无主动通知 | 无 WebSocket，旧设备靠下次请求 401 感知 | [loginController.java#L85-L87](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/Controller/loginController.java#L85-L87) |
| 管理员单 uid 配置 | 多管理员需扩展角色体系 | [AdminInterceptor.java](file:///h:/项目1/ClockOut/demo/src/main/java/com/example/demo/interceptor/AdminInterceptor.java) |

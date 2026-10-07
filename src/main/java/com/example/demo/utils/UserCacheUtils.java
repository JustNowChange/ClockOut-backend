package com.example.demo.utils;

import com.example.demo.un.AuthUserState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 用户维度的 Redis 状态工具
 * 单文档模型: auth:user:{uid} 存 JSON(AuthUserState), 合并会话与封禁两份数据
 * - 登录整包覆盖写(新jti覆盖旧jti = 单设备挤下线, 无需SCAN)
 * - 封禁查库回写走 Lua 只补丁 ban 字段, 不覆盖并发新登录的 session
 * - 限流 rate_limit:* 不在这里(高频INCR, 独立key)
 */
@Component
public class UserCacheUtils {

    // 用户鉴权状态在Redis中的key前缀, 一uid一key
    private static final String AUTH_KEY_PREFIX = "auth:user:";

    // Lua: 只更新ban三个字段, 保留session; key已消失(登出/踢人)则返回0不处理
    // ARGV: 1=banType 2=reason 3=banEnd(空串=null)
    private static final String PATCH_BAN_LUA =
            "local raw = redis.call('GET', KEYS[1]) " +
            "if raw == false then return 0 end " +
            "local obj = cjson.decode(raw) " +
            "if obj.ban == nil then obj.ban = {} end " +
            "obj.ban.banType = tonumber(ARGV[1]) " +
            "obj.ban.reason = ARGV[2] " +
            "if ARGV[3] == '' then obj.ban.banEnd = nil else obj.ban.banEnd = tonumber(ARGV[3]) end " +
            "redis.call('SET', KEYS[1], cjson.encode(obj)) " +
            "return 1";

    // Lua: 刷新令牌真轮换(Refresh Token Rotation), 整链路一次RTT原子完成
    //   1)校验旧jti与会话一致(旧token被重放/已换代 -> invalid)
    //   2)ban快照本地判定(banType 2永久, 1临时且banEnd为数字且未到期 -> banned), 不查数据库
    //   3)session.jti换为新jti, PTTL保持会话剩余TTL(绝对有效期不随刷新续期)
    // 防御手工改库: banEnd不是合法数字(日期字符串/空串等)时tonumber返回nil, 按非生效处理, 绝不抛错500
    // 返回: {'ok'} / {'invalid'} / {'banned', reason}
    // ARGV: 1=旧jti 2=新jti 3=当前epoch毫秒
    private static final String ROTATE_SESSION_LUA =
            "local raw = redis.call('GET', KEYS[1]) " +
            "if raw == false then return {'invalid'} end " +
            "local ok, obj = pcall(cjson.decode, raw) " +
            "if not ok or type(obj) ~= 'table' then return {'invalid'} end " +
            "if obj.session == nil or obj.session.jti ~= ARGV[1] then return {'invalid'} end " +
            "local now = tonumber(ARGV[3]) or 0 " +
            "local ban = obj.ban " +
            "if ban ~= nil then " +
            "  if ban.banType == 2 then return {'banned', ban.reason or ''} end " +
            "  if ban.banType == 1 then " +
            // 先取数字再比较: banEnd为非数字字符串/空串时endNum为nil, 跳过比较不抛错
            "    local endNum = tonumber(ban.banEnd) " +
            "    if endNum ~= nil and endNum > now then " +
            "      return {'banned', ban.reason or ''} " +
            "    end " +
            "  end " +
            "end " +
            "obj.session.jti = ARGV[2] " +
            "local ttl = redis.call('PTTL', KEYS[1]) " +
            "if ttl < 0 then ttl = 604800000 end " +
            "redis.call('SET', KEYS[1], cjson.encode(obj), 'PX', ttl) " +
            "return {'ok'}";

    // 轮换结果码(对应Lua返回数组第一个元素)
    public static final String ROTATE_OK = "ok";
    public static final String ROTATE_INVALID = "invalid";
    public static final String ROTATE_BANNED = "banned";

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /** 读取用户完整鉴权状态(key不存在返回null) */
    public AuthUserState getAuthState(Long uid) {
        String json = redisTemplate.opsForValue().get(AUTH_KEY_PREFIX + uid);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AuthUserState.class);
        } catch (Exception e) {
            // JSON损坏: 删除脏数据, 按不存在处理(下次登录重建)
            redisTemplate.delete(AUTH_KEY_PREFIX + uid);
            return null;
        }
    }

    /**
     * 登录整包覆盖写: TTL=刷新令牌有效期(会话绝对有效期, 刷新不续期)
     * 覆盖即挤掉旧会话(单设备), 无需先SCAN删除
     */
    public void saveAuthState(Long uid, AuthUserState state, long ttlMillis) {
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(AUTH_KEY_PREFIX + uid, json, ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("鉴权状态序列化失败", e);
        }
    }

    /** 删除用户全部状态(登出/踢人/封禁): 一uid一key, 直接DEL */
    public boolean deleteAuthState(Long uid) {
        Boolean deleted = redisTemplate.delete(AUTH_KEY_PREFIX + uid);
        return Boolean.TRUE.equals(deleted);
    }

    /**
     * Lua原子补丁ban字段(解封时调用), 不触碰session
     * @return false=key已消失无需补丁
     */
    public boolean patchBan(Long uid, AuthUserState.Ban ban) {
        String banEnd = ban.getBanEnd() == null ? "" : String.valueOf(ban.getBanEnd());
        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(PATCH_BAN_LUA, Long.class),
                Collections.singletonList(AUTH_KEY_PREFIX + uid),
                String.valueOf(ban.getBanType()),
                ban.getReason() == null ? "" : ban.getReason(),
                banEnd);
        return result != null && result == 1L;
    }

    /**
     * 刷新令牌真轮换(原子): 校验旧jti + ban快照判定 + 换新jti, 一次Redis RTT, 不查数据库
     * @return 结果数组: [0]=ok/invalid/banned, [1]=banned时为封禁原因
     */
    @SuppressWarnings("rawtypes")
    public java.util.List<String> rotateSession(Long uid, String oldJti, String newJti, long nowMillis) {
        java.util.List result = redisTemplate.execute(
                new DefaultRedisScript<>(ROTATE_SESSION_LUA, java.util.List.class),
                Collections.singletonList(AUTH_KEY_PREFIX + uid),
                oldJti, newJti, String.valueOf(nowMillis));
        @SuppressWarnings("unchecked")
        java.util.List<String> typed = result;
        return typed;
    }

    /**
     * 获取当前全部在线uid(auth:user:{uid} key存在即在线)
     * SCAN游标遍历从key中解析uid; SCAN异常时返回已收集部分
     */
    public Set<Long> getOnlineUids() {
        Set<Long> uids = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(AUTH_KEY_PREFIX + "*").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                // key结构 auth:user:{uid}
                String[] parts = cursor.next().split(":");
                if (parts.length >= 3) {
                    try {
                        uids.add(Long.parseLong(parts[2]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            // SCAN失败: 用已收集的部分兜底
        }
        return uids;
    }
}

package com.example.demo.Controller;

import com.example.demo.Result.Result;
import com.example.demo.Vo.userVO;
import com.example.demo.constant.JwtClaimsConstant;
import com.example.demo.properties.JwtProperties;
import com.example.demo.request.userRequest;
import com.example.demo.service.UserService;
import com.example.demo.un.UserAccount;
import com.example.demo.un.UserBan;
import com.example.demo.un.AuthUserState;
import com.example.demo.utils.JWTutil;
import com.example.demo.utils.UserCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class loginController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private UserCacheUtils userCacheUtils;

    /**
     * 登录
     * @param employee
     * @return
     */
    @PostMapping("/login")
    public Result<userVO> login(@RequestBody userRequest employee, HttpServletRequest request) {


        UserAccount user = userService.login(employee);
        if (user == null) {
            return Result.error("用户名或密码错误");
        }

        // TODO 前端做封禁提示
        // 登录时校验封禁(封禁拦截器不覆盖登录接口, 这里直接查)
        UserBan ban = userService.getLatestBan(user.getUid());
        if (ban != null && ban.isEffective()) {

            log.warn("[登录] uid={} 封禁中(banType={}), 拒绝登录", user.getUid(), ban.getBanType());
            return Result.error("账号已被封禁: " + ban.getBanReason());
        }

        // 记录本次登录时间与IP
        String ip = getClientIp(request);
        userService.updateLastLogin(user.getUid(), ip);

        // jti: 本次会话(本标签页)唯一编号, 同时写入双token与Redis会话记录
        String jti = UUID.randomUUID().toString().replace("-", "");

        // 访问令牌(短期): 必须带 tokenType=access + jti, 拦截器据此校验会话有效性
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.UID, user.getUid());
        claims.put(JwtClaimsConstant.TOKEN_TYPE, JwtClaimsConstant.ACCESS_TOKEN);
        claims.put(JwtClaimsConstant.JTI, jti);
        String token = JWTutil.createJWT(
                jwtProperties.getSecretKey(),
                jwtProperties.getTtl(),
                claims);

        // 刷新令牌(长期): tokenType=refresh + 同一jti, 仅用于 /refresh 换新token(会话不变)
        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put(JwtClaimsConstant.UID, user.getUid());
        refreshClaims.put(JwtClaimsConstant.TOKEN_TYPE, JwtClaimsConstant.REFRESH_TOKEN);
        refreshClaims.put(JwtClaimsConstant.JTI, jti);
        String refreshToken = JWTutil.createJWT(
                jwtProperties.getSecretKey(),
                jwtProperties.getRefreshTtl(),
                refreshClaims);

        // 单设备登录: 组装完整状态后整包覆盖写 auth:user:{uid}
        // 新jti覆盖旧jti, 旧设备/旧标签页下次请求即401下线(无需先SCAN删除)
        long now = System.currentTimeMillis();

        AuthUserState.Session sessionState = new AuthUserState.Session();
        sessionState.setJti(jti);
        sessionState.setLoginTime(now);
        sessionState.setLoginIp(ip);

        // 封禁快照: 登录时已查过ban(L51), 直接复用
        AuthUserState.Ban banState = new AuthUserState.Ban();
        if (ban != null) {
            banState.setBanType(ban.getBanType());
            banState.setReason(ban.getBanReason());
            banState.setBanEnd(ban.getBanEnd() == null ? null
                    : ban.getBanEnd().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        } else {
            banState.setBanType(0);
            banState.setReason("");
            banState.setBanEnd(null);
        }

        AuthUserState authState = new AuthUserState();
        authState.setSession(sessionState);
        authState.setBan(banState);

        // TTL=刷新令牌有效期(会话绝对有效期, 刷新不续期)
        userCacheUtils.saveAuthState(user.getUid(), authState, jwtProperties.getRefreshTtl());
        log.info("[登录] jti={}, 鉴权状态JSON已写入Redis", jti.substring(0, Math.min(8, jti.length())));

        // 刷新令牌直接放入响应体, 前端存入标签页级 sessionStorage（支持同浏览器多账号并行）
        userVO employeeLoginVO = userVO.builder()
                .id(user.getUid())
                .name(user.getName())
                .username(user.getUsername())
                .token(token)
                .refreshToken(refreshToken)
                .build();

        log.info("[登录] step7 登录成功: uid={}, username={}, ip={}", user.getUid(), user.getUsername(), ip);




        return Result.success(employeeLoginVO);
    }

    /*
    * 获取用户信息
    *
    */
    @GetMapping("/user-info/{uid}")
    public Result<userVO> getUserInfo(@PathVariable long uid) {

        UserAccount user = userService.getUserInfo(uid);
        if (user == null) {
            return Result.error("用户不存在");
        }

        //拷贝
        userVO employeeLoginVO = userVO.builder()
                .id(user.getUid())
                .name(user.getName())
                .email(user.getEmail())
                .build();

        return Result.success(employeeLoginVO);

    }

    /**
     * 获取客户端真实IP
     * RemoteIpValve 已在Tomcat底层处理: 直连时忽略伪造XFF取TCP真实地址,
     * 经受信代理时从右向左取真实客户端IP, 处理完后getRemoteAddr()即为可信值
     */
    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}

package com.example.demo.Controller;

import com.example.demo.Result.Result;
import com.example.demo.Vo.userVO;
import com.example.demo.constant.JwtClaimsConstant;
import com.example.demo.properties.JwtProperties;
import com.example.demo.utils.JWTutil;
import com.example.demo.utils.UserCacheUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 刷新令牌: 访问令牌过期后, 前端携带刷新令牌换取新的双token
 *
 * 真轮换(Refresh Token Rotation): 每次刷新jti换代, 旧双token立即失效;
 * 轮换由Lua脚本原子完成(校验旧jti + ban快照判定 + 换新jti, 保持会话剩余TTL),
 * 整个refresh仅1次Redis访问, 不查数据库。
 * 会话TTL=刷新令牌有效期(7天绝对有效期, 不随刷新续期), 到期强制重新登录。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class RefreshToken {

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private UserCacheUtils userCacheUtils;

    // 前端回传刷新令牌所用的请求头名称
    private static final String REFRESH_TOKEN_HEADER = "RefreshToken";

    @PostMapping("/refresh")
    public Result<userVO> refresh(HttpServletRequest request) {
        // 1、从请求头取刷新令牌
        String refreshToken = request.getHeader(REFRESH_TOKEN_HEADER);
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return Result.error("刷新令牌缺失, 请重新登录");
        }

        // 2、验签 + 过期校验 + 取出 uid/旧jti
        Claims claims;
        try {
            claims = JWTutil.parseJWT(jwtProperties.getSecretKey(), refreshToken);
        } catch (Exception e) {
            return Result.error("刷新令牌无效或已过期, 请重新登录");
        }

        // 3、类型必须是 refresh(拿访问令牌来刷新直接拒绝)
        if (!JwtClaimsConstant.REFRESH_TOKEN.equals(claims.get(JwtClaimsConstant.TOKEN_TYPE))) {
            return Result.error("令牌类型错误, 请重新登录");
        }

        Long uid = Long.valueOf(claims.get(JwtClaimsConstant.UID).toString());
        String oldJti = (String) claims.get(JwtClaimsConstant.JTI);
        if (oldJti == null || oldJti.isEmpty()) {
            return Result.error("登录状态已失效, 请重新登录");
        }

        // 4、原子轮换: 旧jti校验 + ban快照判定(0次查库) + 换新jti, 保持会话剩余TTL
        String newJti = UUID.randomUUID().toString().replace("-", "");
        List<String> result = userCacheUtils.rotateSession(
                uid, oldJti, newJti, System.currentTimeMillis());
        String code = (result == null || result.isEmpty())
                ? UserCacheUtils.ROTATE_INVALID : result.get(0);

        // 5、按轮换结果分流
        switch (code) {
            case UserCacheUtils.ROTATE_BANNED:
                String reason = result.size() > 1 ? result.get(1) : "";
                log.warn("[刷新] uid={} 封禁中, 拒绝续期", uid);
                return Result.error("账号已被封禁: " + reason);
            case UserCacheUtils.ROTATE_INVALID:
                // 旧jti与服务端不一致: token已被上一次刷新换代/登出/被踢, 旧token重放拒绝
                log.warn("[刷新] uid={} jti已失效(旧token重放), 拒绝续期", uid);
                return Result.error("登录状态已失效, 请重新登录");
            case UserCacheUtils.ROTATE_OK:
                break;
            default:
                return Result.error("登录状态已失效, 请重新登录");
        }

        // 6、通过: 用新jti签发双token, 旧双token在Lua轮换完成时已全部失效
        Map<String, String> tokenPair = buildTokenPair(uid, newJti);

        log.info("[刷新] uid={} 轮换完成, jti已换代, 旧token全部失效", uid);

        userVO vo = userVO.builder()
                .id(uid)
                .token(tokenPair.get("accessToken"))
                .refreshToken(tokenPair.get("refreshToken"))
                .build();
        return Result.success(vo);
    }

    /**
     * 退出登录: 删除 auth:user:{uid} 整key, 双token立即失效
     */
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        String refreshToken = request.getHeader(REFRESH_TOKEN_HEADER);
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            try {
                Claims claims = JWTutil.parseJWT(jwtProperties.getSecretKey(), refreshToken);
                Long uid = Long.valueOf(claims.get(JwtClaimsConstant.UID).toString());
                userCacheUtils.deleteAuthState(uid);
                log.info("[登出] uid={} 鉴权状态已删除", uid);
            } catch (Exception e) {
                // 令牌无效也视为登出成功(幂等), 前端清本地token即可
            }
        }
        return Result.success("退出成功");
    }

    /**
     * 签发双token: access与refresh携带同一jti, 对应 auth:user:{uid} 中的同一会话
     */
    private Map<String, String> buildTokenPair(long uid, String jti) {
        // 访问令牌(短期)
        Map<String, Object> accessClaims = new HashMap<>();
        accessClaims.put(JwtClaimsConstant.UID, uid);
        accessClaims.put(JwtClaimsConstant.TOKEN_TYPE, JwtClaimsConstant.ACCESS_TOKEN);
        accessClaims.put(JwtClaimsConstant.JTI, jti);
        String accessToken = JWTutil.createJWT(
                jwtProperties.getSecretKey(), jwtProperties.getTtl(), accessClaims);

        // 刷新令牌(长期), 同一jti
        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put(JwtClaimsConstant.UID, uid);
        refreshClaims.put(JwtClaimsConstant.TOKEN_TYPE, JwtClaimsConstant.REFRESH_TOKEN);
        refreshClaims.put(JwtClaimsConstant.JTI, jti);
        String refreshToken = JWTutil.createJWT(
                jwtProperties.getSecretKey(), jwtProperties.getRefreshTtl(), refreshClaims);

        Map<String, String> pair = new HashMap<>();
        pair.put("accessToken", accessToken);
        pair.put("refreshToken", refreshToken);
        return pair;
    }
}

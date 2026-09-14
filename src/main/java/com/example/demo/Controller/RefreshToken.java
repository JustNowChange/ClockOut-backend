package com.example.demo.Controller;

import com.example.demo.Result.Result;
import com.example.demo.Vo.userVO;
import com.example.demo.constant.JwtClaimsConstant;
import com.example.demo.properties.JwtProperties;
import com.example.demo.utils.JWTutil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 刷新令牌: 访问令牌过期后, 前端携带刷新令牌换取新的双token
 *
 * 多会话并行模型(同浏览器多标签页可登录不同账号):
 * - 刷新令牌不再走 httpOnly Cookie, 由前端放在请求头 RefreshToken 中携带
 * - Redis 按 refresh:login:{userId}:{jti} 为每个标签页会话存一条独立记录
 *
 * 安全策略(轮转): 每次刷新签发全新刷新令牌(新jti), 旧令牌立即作废;
 * 若发现"签名合法但已不在Redis中"的刷新令牌被重复使用,
 * 判定为令牌泄露, 清除该用户全部会话, 强制重新登录
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class RefreshToken {

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 前端回传刷新令牌所用的请求头名称
    private static final String REFRESH_TOKEN_HEADER = "RefreshToken";
    // 刷新令牌在Redis中的key前缀, 完整key为 refresh:login:{userId}:{jti}
    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:login:";

    @PostMapping("/refresh")
    public Result<userVO> refresh(HttpServletRequest request) {
        // 1、从请求头取刷新令牌
        String refreshToken = request.getHeader(REFRESH_TOKEN_HEADER);
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return Result.error("刷新令牌缺失, 请重新登录");
        }

        // 2、验签 + 过期校验 + 取出 empId / jti
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

        Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
        String jti = claims.get(JwtClaimsConstant.JTI) == null ? null : claims.get(JwtClaimsConstant.JTI).toString();
        if (jti == null || jti.isEmpty()) {
            return Result.error("刷新令牌缺少会话标识, 请重新登录");
        }

        String redisKey = REFRESH_TOKEN_KEY_PREFIX + empId + ":" + jti;
        String stored = stringRedisTemplate.opsForValue().get(redisKey);

        // 4、与Redis比对
        if (stored == null) {
            // 无记录: 已过期/已登出/已被轮转, 或旧令牌被重复使用
            return Result.error("登录状态已失效, 请重新登录");
        }
        if (!stored.equals(refreshToken)) {
            // 签名合法但与服务端记录不一致: 旧刷新令牌被重用 -> 疑似泄露, 清除该用户全部会话
            deleteAllUserSessions(empId);
            return Result.error("检测到登录状态异常, 请重新登录");
        }

        // 5、通过: 轮转签发新双token
        // 旧jti记录立即删除, 新刷新令牌使用新jti独立存储(只影响当前标签页会话)
        stringRedisTemplate.delete(redisKey);
        Map<String, String> tokenPair = buildTokenPair(empId);

        log.info("[刷新] empId={}, jti={} -> 新jti, 轮转完成(仅当前会话)", empId, jti.substring(0, Math.min(8, jti.length())));

        userVO vo = userVO.builder()
                .id(empId.intValue())
                .token(tokenPair.get("accessToken"))
                .refreshToken(tokenPair.get("refreshToken"))
                .build();
        return Result.success(vo);
    }

    /**
     * 退出登录: 只删除当前标签页这一条刷新令牌记录(按jti), 不影响同账号其它标签页;
     * 访问令牌等其自然过期。
     */
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        String refreshToken = request.getHeader(REFRESH_TOKEN_HEADER);
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            try {
                Claims claims = JWTutil.parseJWT(jwtProperties.getSecretKey(), refreshToken);
                Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
                Object jtiObj = claims.get(JwtClaimsConstant.JTI);
                if (jtiObj != null) {
                    stringRedisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + empId + ":" + jtiObj);
                    log.info("[登出] 已删除会话记录: empId={}, jti={}", empId, jtiObj);
                }
            } catch (Exception e) {
                // 刷新令牌无效/过期: 本就无需删除, 不阻塞登出
                log.warn("[登出] 刷新令牌解析失败(忽略): {}", e.getMessage());
            }
        }
        return Result.success("退出成功");
    }

    /**
     * 签发双token并将刷新令牌(带jti)写入Redis, 每个会话一条独立记录
     */
    private Map<String, String> buildTokenPair(long userId) {
        // 访问令牌(短期)
        Map<String, Object> accessClaims = new HashMap<>();
        accessClaims.put(JwtClaimsConstant.EMP_ID, userId);
        accessClaims.put(JwtClaimsConstant.TOKEN_TYPE, JwtClaimsConstant.ACCESS_TOKEN);
        String accessToken = JWTutil.createJWT(
                jwtProperties.getSecretKey(), jwtProperties.getTtl(), accessClaims);

        // 刷新令牌(长期), 携带新的唯一jti
        String jti = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put(JwtClaimsConstant.EMP_ID, userId);
        refreshClaims.put(JwtClaimsConstant.TOKEN_TYPE, JwtClaimsConstant.REFRESH_TOKEN);
        refreshClaims.put(JwtClaimsConstant.JTI, jti);
        String refreshToken = JWTutil.createJWT(
                jwtProperties.getSecretKey(), jwtProperties.getRefreshTtl(), refreshClaims);

        // 按 用户ID+jti 独立存储, 同账号多标签各一条, 轮转互不覆盖
        stringRedisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + userId + ":" + jti,
                refreshToken,
                jwtProperties.getRefreshTtl(),
                TimeUnit.MILLISECONDS);

        Map<String, String> pair = new HashMap<>();
        pair.put("accessToken", accessToken);
        pair.put("refreshToken", refreshToken);
        return pair;
    }

    /**
     * 删除某用户的全部刷新会话(令牌重用/疑似泄露时调用)。
     * 使用 SCAN 游标遍历 refresh:login:{userId}:* , 避免 KEYS 阻塞 Redis。
     */
    private void deleteAllUserSessions(Long userId) {
        String pattern = REFRESH_TOKEN_KEY_PREFIX + userId + ":*";
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.error("[刷新] SCAN 用户会话失败: empId={}", userId, e);
        }
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            log.warn("[刷新] 疑似令牌泄露, 已清除用户全部会话: empId={}, 共{}条", userId, keys.size());
        }
    }
}

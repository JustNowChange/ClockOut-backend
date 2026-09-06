package com.example.demo.Controller;

import com.example.demo.Result.Result;
import com.example.demo.Vo.userVO;
import com.example.demo.constant.JwtClaimsConstant;
import com.example.demo.context.BaseContext;
import com.example.demo.properties.JwtProperties;
import com.example.demo.utils.JWTutil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 刷新令牌: 访问令牌过期后, 前端携带刷新令牌换取新的双token
 * 安全策略(轮转): 每次刷新签发全新刷新令牌, 旧令牌立即作废;
 * 若发现"签名合法但已不在Redis中"的刷新令牌被重复使用,
 * 判定为令牌泄露, 清除该用户全部登录态, 强制重新登录
 */
@RestController
@RequestMapping("/api/auth")
public class RefreshToken {

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 刷新令牌在Redis中的key前缀, 完整key为 refresh:login:{userId}
    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:login:";

@PostMapping("/refresh")
public Result<userVO> refresh(@RequestBody Map<String, String> body) {
    String refreshToken = body.get("refreshToken");

    // 1、缺失
    if (refreshToken == null || refreshToken.trim().isEmpty()) {
        return Result.error("刷新令牌缺失, 请重新登录");
    }

    // 2、验签 + 过期校验
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
    String redisKey = REFRESH_TOKEN_KEY_PREFIX + empId;
    String stored = stringRedisTemplate.opsForValue().get(redisKey);

    // 4、与Redis比对
    if (stored == null) {
        // 无记录: 已过期/已登出, 或旧令牌被重复使用
        return Result.error("登录状态已失效, 请重新登录");
    }
    if (!stored.equals(refreshToken)) {
        // 签名合法但与服务端记录不一致: 旧刷新令牌被重用 -> 疑似泄露, 清除登录态
        stringRedisTemplate.delete(redisKey);
        return Result.error("检测到登录状态异常, 请重新登录");
    }

    // 5、通过: 轮转签发新双token(旧刷新令牌被Redis覆盖, 立即作废)
    Map<String, String> tokenPair = buildTokenPair(empId);
    userVO vo = userVO.builder()
            .id(empId.intValue())
            .token(tokenPair.get("accessToken"))
            .refreshToken(tokenPair.get("refreshToken"))
            .build();
    return Result.success(vo);
}

/**
 * 退出登录: 删除Redis中的刷新令牌(访问令牌等其自然过期)
 */
@PostMapping("/logout")
public Result<String> logout() {
    Long empId = BaseContext.getCurrentId();
    if (empId != null) {
        stringRedisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + empId);
    }
    return Result.success("退出成功");
}

/**
 * 签发双token并将刷新令牌写入Redis
 */
private Map<String, String> buildTokenPair(long userId) {
    // 访问令牌(短期)
    Map<String, Object> accessClaims = new HashMap<>();
    accessClaims.put(JwtClaimsConstant.EMP_ID, userId);
    accessClaims.put(JwtClaimsConstant.TOKEN_TYPE, JwtClaimsConstant.ACCESS_TOKEN);
    String accessToken = JWTutil.createJWT(
            jwtProperties.getSecretKey(), jwtProperties.getTtl(), accessClaims);

    // 刷新令牌(长期)
    Map<String, Object> refreshClaims = new HashMap<>();
    refreshClaims.put(JwtClaimsConstant.EMP_ID, userId);
    refreshClaims.put(JwtClaimsConstant.TOKEN_TYPE, JwtClaimsConstant.REFRESH_TOKEN);
    String refreshToken = JWTutil.createJWT(
            jwtProperties.getSecretKey(), jwtProperties.getRefreshTtl(), refreshClaims);

    // 存Redis, TTL与刷新令牌有效期一致; 登录/刷新时覆盖即完成轮转
    stringRedisTemplate.opsForValue().set(
            REFRESH_TOKEN_KEY_PREFIX + userId,
            refreshToken,
            jwtProperties.getRefreshTtl(),
            TimeUnit.MILLISECONDS);

    Map<String, String> pair = new HashMap<>();
        pair.put("accessToken", accessToken);
        pair.put("refreshToken", refreshToken);
        return pair;
    }
}
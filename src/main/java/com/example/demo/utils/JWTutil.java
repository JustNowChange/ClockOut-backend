package com.example.demo.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JWTutil { /**
 * 生成jwt
 * 使用Hs256算法, 私匙使用固定秘钥
 *
 * @param secretKey jwt秘钥
 * @param ttlMillis jwt过期时间(毫秒)
 * @param claims    设置的信息
 * @return
 */
public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
    // 1. 字符串密钥转为SecretKey
    SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    // 2. 计算过期时间
    long expireTime = System.currentTimeMillis() + ttlMillis;
    // 3. 构建token
    return Jwts.builder()
            .setClaims(claims)
            .setExpiration(new Date(expireTime))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
}

    /**
     * Token解密
     *
     * @param secretKey jwt秘钥 此秘钥一定要保留好在服务端, 不能暴露出去, 否则sign就可以被伪造, 如果对接多个客户端建议改造成多个
     * @param token     加密后的token
     * @return
     */
    public static Claims parseJWT(String secretKey, String token) {
        // 得到DefaultJwtParser
        Claims claims = Jwts.parser()
                // 设置签名的秘钥
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                // 设置需要解析的jwt
                .parseClaimsJws(token).getBody();
        return claims;
    }
}

package com.example.demo.interceptor;

import com.example.demo.context.BaseContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流拦截器（基于 Redis 计数）
 * 策略：按"用户ID或IP"做维度，限制单位时间内的请求数
 * 例如：每分钟最多 60 次请求
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 时间窗口（秒），默认 60 秒 */
    private static final long WINDOW_SECONDS = 60;
    /** 窗口内最大请求数 */
    private static final long MAX_REQUESTS = 60;
    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "rate_limit:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只拦截 Controller 方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 限流维度：已登录用户用 userId，未登录用 IP
        String identity;
        try {
            Long userId = BaseContext.getCurrentId();
            identity = userId != null ? "u:" + userId : "ip:" + getClientIp(request);
        } catch (Exception e) {
            identity = "ip:" + getClientIp(request);
        }

        String key = KEY_PREFIX + identity;
        Long count = redisTemplate.opsForValue().increment(key);

        // 第一次访问时设置过期时间
        if (count != null && count == 1) {
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        // 超过阈值则拒绝
        if (count != null && count > MAX_REQUESTS) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":0,\"msg\":\"请求过于频繁，请稍后再试\",\"data\":null}");
            return false;
        }

        return true;
    }

    /** 获取客户端真实 IP（兼容代理） */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}

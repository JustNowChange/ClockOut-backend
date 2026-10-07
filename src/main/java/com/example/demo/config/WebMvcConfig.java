package com.example.demo.config;

import com.example.demo.interceptor.AdminInterceptor;
import com.example.demo.interceptor.BanInterceptor;
import com.example.demo.interceptor.JWTtoken;
import com.example.demo.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JWTtoken jwT;
    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;
    @Autowired
    private AdminInterceptor adminInterceptor;
    @Autowired
    private BanInterceptor banInterceptor;


    /**
     * 注册自定义拦截器
     * 执行顺序: JWT认证 -> 封禁校验 -> 限流; 管理员校验仅挂在 /api/admin/**
     * @param registry
     */
    public void addInterceptors(InterceptorRegistry registry) {
        // JWT 认证拦截器
        registry.addInterceptor(jwT)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/image/**"
                );

        // 封禁校验拦截器: 登录用户每次请求都校验封禁状态(Redis缓存 user:ban:{uid}, TTL 60s)
        registry.addInterceptor(banInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/image/**"
                );

        // 管理员拦截器: 仅保护 /api/admin/**, 以 sky.admin-uid 判定
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**");

        // 限流拦截器：所有 /api/** 接口均受限流保护，按用户或IP计数
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/image/**");


     }

}

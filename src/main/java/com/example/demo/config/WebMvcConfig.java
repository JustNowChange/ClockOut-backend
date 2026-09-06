package com.example.demo.config;

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


    /**
     * 注册自定义拦截器
     * TODO:放行的接口
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
                        "/image/**"
                );

        // 限流拦截器：所有 /api/** 接口均受限流保护，按用户或IP计数
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/image/**");

     }

}
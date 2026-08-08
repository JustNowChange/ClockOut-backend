package com.example.demo.config;

import com.example.demo.interceptor.JWTtoken;
import com.example.demo.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;


@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private UploadProperties uploadProperties;
    @Autowired
    private JWTtoken jwT;
    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String localPath = uploadProperties.getLocalPath();
        if (!localPath.endsWith("/")) {
            localPath = localPath + "/";
        }
        registry.addResourceHandler(uploadProperties.getAccessPrefix() + "**")
                .addResourceLocations("file:" + localPath)
                .setCachePeriod(3600 * 24 * 30);
    }


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
                        "/image/**"
                );

        // 限流拦截器：所有 /api/** 接口均受限流保护，按用户或IP计数
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/image/**");

     }

}
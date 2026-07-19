package com.example.demo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private UploadProperties uploadProperties;

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
}
package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "upload")
public class UploadProperties {
    // 和yml里local-path对应，驼峰自动匹配短横线
    private String localPath;
    private String accessPrefix;

    // getter、setter
    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getAccessPrefix() {
        return accessPrefix;
    }

    public void setAccessPrefix(String accessPrefix) {
        this.accessPrefix = accessPrefix;
    }
}
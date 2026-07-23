package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {
    // getter、setter
    // 和yml里local-path对应，驼峰自动匹配短横线
    private String localPath;
    private String accessPrefix;

}
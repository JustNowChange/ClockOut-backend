package com.example.demo.request;

import lombok.Data;

@Data
public class ModuleUpdateRequest {
    private String moduleTitle;
    private String content;  // JSON 字符串
}

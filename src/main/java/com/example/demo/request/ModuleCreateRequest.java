package com.example.demo.request;

import lombok.Data;

@Data
public class ModuleCreateRequest {
    private String moduleType;
    private String moduleTitle;
    private String content;  // JSON 字符串,默认 "[]"
}

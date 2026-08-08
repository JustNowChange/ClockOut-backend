package com.example.demo.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeDetailVO {
    private Long id;
    private Long userId;
    private CoreInfo core;
    private List<ModuleInfo> modules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoreInfo {
        private String name;
        private String title;
        private String phone;
        private String email;
        private String location;
        private String github;
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModuleInfo {
        private Long id;
        private String moduleType;
        private String moduleTitle;
        private Integer sortOrder;
        private Object content;  // JSON 解析后的对象/数组
    }
}

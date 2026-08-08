package com.example.demo.un;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ResumeModule {
    private Long id;
    private Long resumeId;
    private String moduleType;
    private String moduleTitle;
    private Integer sortOrder;
    private String content;  // JSON 字符串,MyBatis 直接存 String
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.example.demo.un;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Resume {
    private Long id;
    private Long userId;
    private String name;
    private String title;
    private String phone;
    private String email;
    private String location;
    private String github;
    private String summary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

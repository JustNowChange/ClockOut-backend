package com.example.demo.request;

import lombok.Data;

@Data
public class ResumeCoreUpdateRequest {
    private String name;
    private String title;
    private String phone;
    private String email;
    private String location;
    private String github;
    private String summary;
}

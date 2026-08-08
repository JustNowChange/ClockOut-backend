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
public class ResumeListItemVO {
    private Long id;
    private Long userId;
    private String name;
    private String title;
    private String education;
    private String experience;
    private Integer projectCount;
    private List<String> skills;
}

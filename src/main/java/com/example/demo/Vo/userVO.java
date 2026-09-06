package com.example.demo.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class userVO {
    private int id;
    private String name;
    private String username;
    private String password;
    private String token;
    private String refreshToken;
    private int status;

}

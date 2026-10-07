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
    /** 用户uid(对外字段名保持id, 兼容前端) */
    private Long id;
    /** 昵称 */
    private String name;
    private String username;
    private String email;
    /** 访问令牌(短期) */
    private String token;
    /** 刷新令牌(长期) */
    private String refreshToken;
}

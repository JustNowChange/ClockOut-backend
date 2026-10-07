package com.example.demo.Vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理端用户列表行
 * GET /api/admin/users
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserListItemVO {
    /** 用户uid */
    private Long uid;
    /** 账号名 */
    private String username;
    /** 昵称 */
    private String name;
    private String email;
    /** 注册时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerTime;
    /** 上一次成功登录时间(从未登录为null) */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;
    /** 上次登录IP */
    private String lastLoginIp;
    /** 是否在线(Redis存在该uid的会话) */
    private Boolean online;
    /** 封禁状态: 0正常 1临时封禁 2永久封禁(最新一条生效中的封禁) */
    private Integer banType;
    /** 封禁原因(未封禁为null) */
    private String banReason;
    /** 临时封禁到期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime banEnd;
}

package com.example.demo.un;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号实体, 对应 user_account 表
 */
@Data
public class UserAccount {
    /** 用户唯一ID */
    private Long uid;

    /** 账号名 */
    private String username;

    /** 密码哈希(MD5 hex), 不要明文 */
    private String passwordHash;

    /** 昵称 */
    private String name;

    private String email;

    /** 注册时间 */
    private LocalDateTime registerTime;

    /** 上一次成功登录时间 */
    private LocalDateTime lastLoginTime;

    /** 上次登录IP */
    private String lastLoginIp;

    /** 软删除 0正常 1删除 */
    private Integer isDelete;
}

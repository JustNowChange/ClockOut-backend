package com.example.demo.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 封禁用户请求
 * POST /api/admin/users/{uid}/ban
 */
@Data
public class BanRequest {

    /** 封禁类型: 1临时封禁 2永久封禁 */
    private Integer banType;

    /** 封禁原因(必填, 最长512) */
    private String banReason;

    /** 临时封禁到期时间(banType=1时必填且须晚于当前时间; 永久封禁忽略) */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime banEnd;
}

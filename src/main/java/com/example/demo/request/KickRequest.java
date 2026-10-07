package com.example.demo.request;

import lombok.Data;

/**
 * 踢出登录请求
 * POST /api/admin/users/{uid}/kick
 */
@Data
public class KickRequest {
    /** 踢出原因(可选, 仅日志记录不入库) */
    private String reason;
}

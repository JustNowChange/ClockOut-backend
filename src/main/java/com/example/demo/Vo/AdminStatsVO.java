package com.example.demo.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端控制台总览统计
 * GET /api/admin/stats
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsVO {

    /** 用户总数(排除软删除) */
    private Long totalUsers;

    /** 在线人数(Redis存在有效会话) */
    private Long onlineUsers;

    /** 封禁中人数(临时+永久) */
    private Long bannedUsers;

    /** 今日新增用户数 */
    private Long todayNewUsers;
}

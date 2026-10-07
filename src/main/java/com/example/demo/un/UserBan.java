package com.example.demo.un;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户封禁记录实体, 对应 user_ban 表
 */
@Data
public class UserBan {
    private Long id;
    /** 被封用户uid */
    private Long uid;
    /** 0无封禁 1临时封禁 2永久封禁 */
    private Integer banType;
    /** 封禁开始时间 */
    private LocalDateTime banStart;
    /** 封禁到期时间, 永久封禁填NULL */
    private LocalDateTime banEnd;
    /** 封禁原因 */
    private String banReason;
    /** 操作管理员uid */
    private Long operatorUid;
    private LocalDateTime createTime;
    /** 手动解封时间 */
    private LocalDateTime revokeTime;
    /** 解封管理员uid */
    private Long revokeOperator;

    /**
     * 是否处于生效中的封禁: 未手动解封 且 (永久封禁 或 临时封禁未到期)
     */
    public boolean isEffective() {
        if (banType == null || banType == 0) {
            return false;
        }
        if (revokeTime != null) {
            return false;
        }
        if (banType == 2) {
            return true;
        }
        // 临时封禁: 有到期时间且尚未到期
        return banEnd != null && banEnd.isAfter(LocalDateTime.now());
    }
}

package com.example.demo.un;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 用户维度鉴权状态(Redis auth:user:{uid} 的 JSON 载体)
 * 合并原会话记录与封禁缓存为单文档: 一次GET取回, 新增字段直接在此类加属性
 *
 * 时间统一用 epoch 毫秒(Long), 避免 LocalDateTime 序列化配置
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthUserState {

    /** 登录会话; 无登录时为null(正常情况key不存在) */
    private Session session;
    /** 封禁状态快照 + 最近一次核对时间 */
    private Ban ban;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Session {
        /** 本次会话唯一编号, 与双token中的jti一致 */
        private String jti;
        /** 登录时间(epoch毫秒) */
        private Long loginTime;
        /** 登录IP */
        private String loginIp;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ban {
        /** 0无封禁 1临时封禁 2永久封禁 */
        private Integer banType;
        /** 封禁原因(无封禁为空串) */
        private String reason;
        /** 临时封禁到期时间(epoch毫秒); 永久封禁/无封禁为null, 拦截器本地比对到期自动放行 */
        private Long banEnd;
    }
}

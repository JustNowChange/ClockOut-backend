package com.example.demo.utils;

import org.apache.commons.text.StringEscapeUtils;

/**
 * 数据脱敏与安全工具类
 */
public class SecurityUtils {

    /**
     * 手机号脱敏:中间4位用*替代
     * 示例:13812345678 -> 138****5678
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) return "";
        // 兼容各种格式:1380000138 / 138-0000-0138 / 138 0000 0138
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() <= 7) {
            // 太短,全脱敏
            return "***";
        }
        // 前3位 + **** + 后4位
        StringBuilder sb = new StringBuilder();
        sb.append(digits, 0, 3);
        sb.append("****");
        sb.append(digits, digits.length() - 4, digits.length());

        // 保持原格式的分隔符
        if (phone.contains("-") || phone.contains(" ")) {
            return sb.substring(0, 3) + "-" + sb.substring(3, 7) + "-" + sb.substring(7);
        }
        return sb.toString();
    }

    /**
     * 邮箱脱敏:前3位+***+@后域名
     * 示例:zhangsan@example.com -> zha***@example.com
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) return "";
        int atIndex = email.indexOf('@');
        if (atIndex <= 3) {
            // 太短,全脱敏
            return "***@" + (atIndex >= 0 ? email.substring(atIndex + 1) : "");
        }
        String prefix = email.substring(0, Math.min(3, atIndex));
        String domain = email.substring(atIndex);
        return prefix + "***" + domain;
    }

    /**
     * HTML 转义(防XSS)
     * 将特殊字符转为HTML实体
     */
    public static String escapeHtml(String input) {
        if (input == null || input.isEmpty()) return "";
        return StringEscapeUtils.escapeHtml4(input);
    }

    /**
     * 全字段HTML转义(用于简历内容)
     */
    public static String sanitizeText(String text) {
        if (text == null) return "";
        return escapeHtml(text.trim());
    }

    /**
     * 截断字段长度
     * @param text 文本
     * @param maxLength 最大长度
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength);
    }
}

package com.example.demo.service;

import com.example.demo.Vo.AdminStatsVO;
import com.example.demo.Vo.AdminUserPageVO;
import com.example.demo.request.BanRequest;
import com.example.demo.request.userRequest;
import com.example.demo.un.UserAccount;
import com.example.demo.un.UserBan;

public interface UserService {
    UserAccount login(userRequest employee);

    // 通用更新: 按uid更新user中非null字段, 返回受影响行数
    int CommoUpdateUser(UserAccount user);

    // 拿uid查基本信息
    UserAccount getUserInfo(Long uid);

    // 查询用户最新一条封禁记录(无记录返回null)
    UserBan getLatestBan(Long uid);

    // 登录成功后更新上次登录时间与IP
    void updateLastLogin(Long uid, String ip);

    // ==================== 管理端 ====================

    // 控制台总览统计
    AdminStatsVO getAdminStats();

    // 用户分页列表(参数已在Controller层完成校验)
    AdminUserPageVO pageAdminUsers(int page, int pageSize, String keyword,
                                   Integer banType, Integer online);

    // 封禁用户(插入ban记录 + 清缓存 + 删全部会话), 参数已在Controller校验
    void banUser(Long uid, BanRequest request, Long operatorUid);

    // 解封用户, 返回受影响行数(0表示该用户没有生效中的封禁)
    int unbanUser(Long uid, Long operatorUid);

    // 踢出登录(删除全部会话, 不写封禁), 返回删除的会话key数
    long kickUser(Long uid);
}

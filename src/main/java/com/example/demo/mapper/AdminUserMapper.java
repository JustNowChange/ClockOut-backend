package com.example.demo.mapper;

import com.example.demo.un.UserAccount;
import com.example.demo.un.UserBan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 管理端用户管理 Mapper
 * 与 UserMapper 分离, 专门承载管理端用户列表/统计/封禁的动态 SQL
 */
@Mapper
public interface AdminUserMapper {

    // 管理端: 用户总数(排除软删除)
    long adminCountAllUsers();

    // 管理端: 今日新增用户数
    long adminCountTodayNewUsers();

    // 管理端: 封禁中用户数(临时+永久)
    long adminCountBannedUsers();

    // 管理端: 按条件统计用户数(keyword模糊username/name, banType封禁筛选, online在线筛选)
    long adminCountUsers(@Param("keyword") String keyword,
                         @Param("banType") Integer banType,
                         @Param("online") Integer online,
                         @Param("onlineUids") List<Long> onlineUids);

    // 管理端: 按条件分页查询用户
    List<UserAccount> adminPageUsers(@Param("offset") int offset,
                                     @Param("limit") int limit,
                                     @Param("keyword") String keyword,
                                     @Param("banType") Integer banType,
                                     @Param("online") Integer online,
                                     @Param("onlineUids") List<Long> onlineUids);

    // 管理端: 查全部生效中的封禁记录(每uid取最新一条)
    List<UserBan> adminListEffectiveBans();

    // 管理端: 插入封禁记录, 回填自增id
    int adminInsertBan(UserBan ban);

    // 管理端: 解封(更新该uid最新一条未解封记录)
    int adminRevokeLatestBan(@Param("uid") Long uid, @Param("operatorUid") Long operatorUid);
}

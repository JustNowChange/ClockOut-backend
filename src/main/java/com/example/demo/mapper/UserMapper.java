package com.example.demo.mapper;

import com.example.demo.un.UserAccount;
import com.example.demo.un.UserBan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {
    // 登录: 按账号名查询(排除软删除账号)
    @Select("select * from user_account where username = #{username} and is_delete = 0")
    UserAccount login(String username);

    // 按uid查询用户资料(排除软删除账号)
    @Select("select * from user_account where uid = #{uid} and is_delete = 0")
    UserAccount getUserInfo(Long uid);

    // 取一条"生效中"的封禁记录: 存在任意一条未解封且生效(永久/临时未到期)即封禁; 同uid多条取id最大一条
    // 历史上临时封禁已过期(即使未手动解封)不算生效
    @Select("select * from user_ban where uid = #{uid} and revoke_time is null " +
            "and (ban_type = 2 or (ban_type = 1 and ban_end > now())) " +
            "order by id desc limit 1")
    UserBan getLatestBan(Long uid);

    // 登录成功后更新上次登录时间与IP
    @Update("update user_account set last_login_time = now(), last_login_ip = #{ip} where uid = #{uid}")
    int updateLastLogin(@Param("uid") Long uid, @Param("ip") String ip);

    // 通用更新: 按uid更新非null字段, 返回受影响行数
    int CommoUpdateUser(UserAccount user);
}

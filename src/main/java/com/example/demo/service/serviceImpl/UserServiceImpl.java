package com.example.demo.service.serviceImpl;

import com.example.demo.Vo.AdminStatsVO;
import com.example.demo.Vo.AdminUserListItemVO;
import com.example.demo.Vo.AdminUserPageVO;
import com.example.demo.mapper.AdminUserMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.request.BanRequest;
import com.example.demo.request.userRequest;
import com.example.demo.service.UserService;
import com.example.demo.un.UserAccount;
import com.example.demo.un.UserBan;
import com.example.demo.un.AuthUserState;
import com.example.demo.utils.UserCacheUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    public UserMapper userMapper;
    @Autowired
    private AdminUserMapper adminUserMapper;
    @Autowired
    private UserCacheUtils userCacheUtils;

    public UserAccount login(userRequest employee) {
        String username = employee.getUsername();

        UserAccount user = userMapper.login(username);

        String password = employee.getPassword();
        if(user == null){
           System.out.println("用户不存在");
           return null;
        }
        // 密码MD5比对(沿用旧哈希方式, 新表存password_hash字段)
        password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!password.equals(user.getPasswordHash())) {
            //密码错误
            System.out.println("密码错误");
            return null;
        }

        System.out.println("登录成功");
        return user;
    }

    /**
    * 通用修改: 按uid更新user中非null字段, 返回受影响行数
    *
    * */
    public int CommoUpdateUser(UserAccount user) {
        return userMapper.CommoUpdateUser(user);
    }

    /**
     * 按uid获取用户信息
     */
    public UserAccount getUserInfo(Long uid) {
        return userMapper.getUserInfo(uid);
    }

    /**
     * 查询用户最新一条封禁记录(无记录返回null)
     */
    public UserBan getLatestBan(Long uid) {
        return userMapper.getLatestBan(uid);
    }

    /**
     * 登录成功后更新上次登录时间与IP
     */
    public void updateLastLogin(Long uid, String ip) {
        userMapper.updateLastLogin(uid, ip);
    }

    // ==================== 管理端 ====================

    /**
     * 控制台总览统计
     */
    public AdminStatsVO getAdminStats() {
        // 在线uid集合: stats和列表共用一次SCAN
        Set<Long> onlineUids = userCacheUtils.getOnlineUids();

        return AdminStatsVO.builder()
                .totalUsers(adminUserMapper.adminCountAllUsers())
                .onlineUsers((long) onlineUids.size())
                .bannedUsers(adminUserMapper.adminCountBannedUsers())
                .todayNewUsers(adminUserMapper.adminCountTodayNewUsers())
                .build();
    }

    /**
     * 用户分页列表
     */
    public AdminUserPageVO pageAdminUsers(int page, int pageSize, String keyword,
                                          Integer banType, Integer online) {
        // keyword空串归一为null
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        } else if (keyword != null) {
            keyword = keyword.trim();
        }

        // 一次SCAN拿全部在线uid, 供筛选和在线标记共用
        List<Long> onlineUidList = new ArrayList<>(userCacheUtils.getOnlineUids());

        long total = adminUserMapper.adminCountUsers(keyword, banType, online, onlineUidList);

        List<AdminUserListItemVO> list = new ArrayList<>();
        if (total > 0) {
            int offset = (page - 1) * pageSize;
            List<UserAccount> accounts = adminUserMapper.adminPageUsers(
                    offset, pageSize, keyword, banType, online, onlineUidList);

            // 生效封禁记录按uid索引, 装配每行ban信息
            Map<Long, UserBan> banMap = adminUserMapper.adminListEffectiveBans().stream()
                    .collect(Collectors.toMap(UserBan::getUid, Function.identity(), (a, b) -> a));

            for (UserAccount ua : accounts) {
                UserBan ban = banMap.get(ua.getUid());
                list.add(AdminUserListItemVO.builder()
                        .uid(ua.getUid())
                        .username(ua.getUsername())
                        .name(ua.getName())
                        .email(ua.getEmail())
                        .registerTime(ua.getRegisterTime())
                        .lastLoginTime(ua.getLastLoginTime())
                        .lastLoginIp(ua.getLastLoginIp())
                        .online(onlineUidList.contains(ua.getUid()))
                        .banType(ban == null ? 0 : ban.getBanType())
                        .banReason(ban == null ? null : ban.getBanReason())
                        .banEnd(ban == null ? null : ban.getBanEnd())
                        .build());
            }
        }

        return AdminUserPageVO.builder()
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .list(list)
                .build();
    }

    /**
     * 封禁用户: 先接管(解除)该uid全部未解封旧记录, 再插入新ban记录 + 删除 auth:user:{uid} 整key
     * 接管保证同一uid同一时刻只有一条生效封禁(旧记录revoke_time记当前管理员, 历史可追溯)
     */
    @org.springframework.transaction.annotation.Transactional
    public void banUser(Long uid, BanRequest request, Long operatorUid) {
        // 新封禁接管旧封禁: 旧记录全部标记revoke, 避免多条生效记录导致筛选与展示不一致
        adminUserMapper.adminRevokeLatestBan(uid, operatorUid);

        UserBan ban = new UserBan();
        ban.setUid(uid);
        //TODO 这的临时封禁可替换为redis 后异步发给数据库(临时不走)
        ban.setBanType(request.getBanType());
        ban.setBanReason(request.getBanReason());
        // 永久封禁banEnd存null, 临时封禁取请求到期时间
        ban.setBanEnd(request.getBanType() == 1 ? request.getBanEnd() : null);
        ban.setBanStart(java.time.LocalDateTime.now());
        ban.setOperatorUid(operatorUid);
        adminUserMapper.adminInsertBan(ban);

        // 整key删除: 被封用户下次请求401, refresh同步失效
        userCacheUtils.deleteAuthState(uid);
    }

    /**
     * 解封用户, 返回受影响行数(0表示该用户没有生效中的封禁)
     * Lua补丁ban快照(key存在时); 用户不在线key不存在则仅落库, 下次登录自然生效
     */
    public int unbanUser(Long uid, Long operatorUid) {
        int rows = adminUserMapper.adminRevokeLatestBan(uid, operatorUid);
        if (rows > 0) {
            AuthUserState.Ban banState = new AuthUserState.Ban();
            banState.setBanType(0);
            banState.setReason("");
            banState.setBanEnd(null);
            userCacheUtils.patchBan(uid, banState);
        }
        return rows;
    }

    /**
     * 踢出登录: 删除 auth:user:{uid} 整key, 返回是否删除(单设备模型0或1)
     */
    public long kickUser(Long uid) {
        return userCacheUtils.deleteAuthState(uid) ? 1 : 0;
    }
}

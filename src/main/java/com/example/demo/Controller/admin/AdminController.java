package com.example.demo.Controller.admin;

import com.example.demo.Result.Result;
import com.example.demo.Vo.AdminStatsVO;
import com.example.demo.Vo.AdminUserPageVO;
import com.example.demo.Vo.userVO;
import com.example.demo.context.BaseContext;
import com.example.demo.request.BanRequest;
import com.example.demo.request.KickRequest;
import com.example.demo.service.UserService;
import com.example.demo.un.UserAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 管理员专属接口（/api/admin/**）
 * 经过 JWT 登录校验 + BanInterceptor 封禁校验 + AdminInterceptor 管理员uid校验(sky.admin-uid)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserService userService;

    /**
     * 管理端首页信息：当前登录管理员的资料
     * GET /api/admin/info
     */
    @GetMapping("/info")
    public Result<userVO> info() {
        Long userId = BaseContext.getCurrentId();
        //查数据库
        UserAccount currentUser = userService.getUserInfo(userId);
        if (currentUser == null) {
            return Result.error("用户不存在");
        }

        userVO vo = userVO.builder()
                .id(currentUser.getUid())
                .name(currentUser.getName())
                .username(currentUser.getUsername())
                .email(currentUser.getEmail())
                .build();
        return Result.success(vo);
    }

    /**
     * 控制台总览统计
     * GET /api/admin/stats
     */
    @GetMapping("/stats")
    public Result<AdminStatsVO> stats() {
        return Result.success(userService.getAdminStats());
    }

    /**
     * 用户分页列表
     * GET /api/admin/users?page=&pageSize=&keyword=&banType=&online=
     */
    @GetMapping("/users")
    public Result<AdminUserPageVO> users(@RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "10") Integer pageSize,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer banType,
                                         @RequestParam(required = false) Integer online) {
        // 分页参数兜底: page最小1, pageSize限制1~50
        if (page == null || page < 1) {
            page = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }
        if (pageSize > 50) {
            pageSize = 50;
        }
        // 筛选值非法时按"不筛选"处理
        if (banType != null && (banType < 0 || banType > 2)) {
            banType = null;
        }
        if (online != null && online != 0 && online != 1) {
            online = null;
        }

        return Result.success(
                userService.pageAdminUsers(page, pageSize, keyword, banType, online));
    }

    /**
     * 封禁用户 这里是id查寻uid 封禁 需改为ip封禁
     * POST /api/admin/users/{uid}/ban
     */
    @PostMapping("/users/{uid}/ban")
    public Result<String> ban(@PathVariable Long uid, @RequestBody BanRequest request) {
        Long operatorUid = BaseContext.getCurrentId();

        // 不能封禁自己
        if (uid.equals(operatorUid)) {
            return Result.error("不能对自己执行该操作");
        }
        if (userService.getUserInfo(uid) == null) {
            return Result.error("用户不存在");
        }

        // 参数校验
        if (request.getBanType() == null
                || (request.getBanType() != 1 && request.getBanType() != 2)) {
            return Result.error("封禁类型错误");
        }
        if (request.getBanReason() == null || request.getBanReason().trim().isEmpty()) {
            return Result.error("封禁原因不能为空");
        }
        if (request.getBanReason().length() > 512) {
            return Result.error("封禁原因最长512字");
        }
        if (request.getBanType() == 1) {
            if (request.getBanEnd() == null) {
                return Result.error("临时封禁必须填写到期时间");
            }
            if (!request.getBanEnd().isAfter(LocalDateTime.now())) {
                return Result.error("封禁到期时间必须晚于当前时间");
            }
        }

        userService.banUser(uid, request, operatorUid);
        log.warn("[管理端] uid={} 封禁完成(banType={}), 操作者uid={}",
                uid, request.getBanType(), operatorUid);
        return Result.success("封禁成功");
    }

    /**
     * 解封用户
     * POST /api/admin/users/{uid}/unban
     */
    @PostMapping("/users/{uid}/unban")
    public Result<String> unban(@PathVariable Long uid) {
        Long operatorUid = BaseContext.getCurrentId();

        // 不能解封自己(自己是管理员且不可能处于封禁中, 统一拦掉)
        if (uid.equals(operatorUid)) {
            return Result.error("不能对自己执行该操作");
        }

        int rows = userService.unbanUser(uid, operatorUid);
        if (rows == 0) {
            return Result.error("该用户没有生效中的封禁");
        }
        log.info("[管理端] uid={} 解封完成, 操作者uid={}", uid, operatorUid);
        return Result.success("解封成功");
    }

    /**
     * 踢出登录
     * POST /api/admin/users/{uid}/kick
     */
    @PostMapping("/users/{uid}/kick")
    public Result<String> kick(@PathVariable Long uid,
                               @RequestBody(required = false) KickRequest request) {
        Long operatorUid = BaseContext.getCurrentId();

        // 不能踢出自己
        if (uid.equals(operatorUid)) {
            return Result.error("不能对自己执行该操作");
        }
        if (userService.getUserInfo(uid) == null) {
            return Result.error("用户不存在");
        }

        long deleted = userService.kickUser(uid);
        log.warn("[管理端] uid={} 已踢出(删除会话{}条), 操作者uid={}, 原因={}",
                uid, deleted, operatorUid,
                request == null ? null : request.getReason());
        return Result.success("已踢出");
    }
}

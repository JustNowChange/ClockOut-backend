package com.example.demo.Controller.admin;

import com.example.demo.Result.Result;
import com.example.demo.Vo.userVO;
import com.example.demo.context.BaseContext;
import com.example.demo.service.UserService;
import com.example.demo.un.user;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员专属接口（/api/admin/**）
 * 经过 JWT 登录校验 + AdminInterceptor 角色校验（user.status=2）
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
        user currentUser = userService.getUserInfo(userId.intValue());
        if (currentUser == null) {
            return Result.error("用户不存在");
        }

        userVO vo = userVO.builder()
                .id(currentUser.getId())
                .name(currentUser.getName())
                .username(currentUser.getUsername())
                .status(currentUser.getStatus())
                .build();
        return Result.success(vo);
    }
}

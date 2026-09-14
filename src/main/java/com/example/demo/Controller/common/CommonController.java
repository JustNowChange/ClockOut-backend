package com.example.demo.Controller.common;

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
 * 登录用户共用接口（/api/common/**）
 * 管理员与普通用户均可访问，只需通过 JWT 登录校验
 */
@Slf4j
@RestController
@RequestMapping("/api/common")
public class CommonController {

    @Autowired
    private UserService userService;

    /**
     * 当前登录用户资料（依据 token 解析，无需在路径传 id）
     * GET /api/common/me
     */
    @GetMapping("/me")
    public Result<userVO> me() {
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

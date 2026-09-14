package com.example.demo.interceptor;

import com.example.demo.context.BaseContext;
import com.example.demo.service.UserService;
import com.example.demo.un.user;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理员角色拦截器
 * 仅放行 user.status = 2 的请求；其余登录用户返回 403。
 * 注册在 JWT 拦截器之后，因此 BaseContext.getCurrentId() 一定可用。
 */
@Slf4j
@Component
public class AdminInterceptor implements HandlerInterceptor {

    // user.status 角色定义：1-普通用户 2-管理员
    private static final int ADMIN_STATUS = 2;

    @Autowired
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非 Controller 方法（静态资源等）直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            log.warn("[管理员拦截] {} | BaseContext 无用户ID", request.getRequestURI());
            reject(response);
            return false;
        }

        user currentUser = userService.getUserInfo(userId.intValue());
        if (currentUser == null || currentUser.getStatus() != ADMIN_STATUS) {
            log.warn("[管理员拦截] {} | userId={} 非管理员(status={})，拒绝访问",
                    request.getRequestURI(), userId, currentUser == null ? "null" : currentUser.getStatus());
            reject(response);
            return false;
        }

        log.info("[管理员拦截] {} | userId={} 管理员校验通过", request.getRequestURI(), userId);
        return true;
    }

    private void reject(HttpServletResponse response) throws Exception {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":0,\"msg\":\"需要管理员权限\",\"data\":null}");
    }
}

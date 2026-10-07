package com.example.demo.interceptor;

import com.example.demo.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理员拦截器
 * 以配置的 uid 判定管理员身份(sky.admin-uid), 其余登录用户返回 403。
 * 注册在 JWT/Ban 拦截器之后, BaseContext.getCurrentId() 一定可用。
 */
@Slf4j
@Component
public class AdminInterceptor implements HandlerInterceptor {

    // 管理员uid, 从配置 sky.admin-uid 读取, 默认1
    @Value("${sky.admin-uid}")
    private long adminUid;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非 Controller 方法(静态资源等)直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            log.warn("[管理员拦截] {} | BaseContext 无用户ID", request.getRequestURI());
            reject(response);
            return false;
        }

        if (userId != adminUid) {
            log.warn("[管理员拦截] {} | userId={} 非管理员(管理员uid={}), 拒绝访问",
                    request.getRequestURI(), userId, adminUid);
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

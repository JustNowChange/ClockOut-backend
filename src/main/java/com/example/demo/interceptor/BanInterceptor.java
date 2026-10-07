package com.example.demo.interceptor;

import com.example.demo.context.BaseContext;
import com.example.demo.un.AuthUserState;
import com.example.demo.utils.UserCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 封禁校验拦截器: 登录用户每次请求都校验封禁状态
 * 注册在 JWT 拦截器之后: JWT拦截器已GET完整JSON并通过 ATTR_AUTH_STATE 递入, 本拦截器0次Redis/DB访问
 * 封禁状态只在"封号/解封"时由管理端操作Redis(封号DEL整key, 解封Lua补丁ban), 拦截器不做周期性查库
 */
@Slf4j
@Component
public class BanInterceptor implements HandlerInterceptor {

    /** request attribute key: JWT拦截器预取的完整鉴权状态 */
    public static final String ATTR_AUTH_STATE = "auth.user.state";

    @Autowired
    private UserCacheUtils userCacheUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非 Controller 方法(静态资源等)直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        Long uid = BaseContext.getCurrentId();
        if (uid == null) {
            // 正常流程 JWT 拦截器已先写入 uid, 这里兜底放行
            return true;
        }

        // 优先用JWT拦截器递入的状态; 缺失(非标准链路)时自己查Redis降级
        AuthUserState state = (AuthUserState) request.getAttribute(ATTR_AUTH_STATE);
        if (state == null) {
            state = userCacheUtils.getAuthState(uid);
            if (state == null) {
                return true;
            }
        }

        long now = System.currentTimeMillis();
        AuthUserState.Ban banState = state.getBan();

        // 纯本地判定: banType 2永久, 1临时且banEnd未到期; 临时封禁到期自动放行(无需查库)
        if (isBanEffective(banState, now)) {
            String reason = banState.getReason() == null ? "" : banState.getReason()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
            log.warn("[封禁拦截] {} | uid={} 封禁中, 拒绝访问", request.getRequestURI(), uid);
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":0,\"msg\":\"账号已被封禁: " + reason + "\",\"data\":null}");
            return false;
        }

        return true;
    }

    /** 判断ban快照是否生效: 1临时且banEnd晚于当前, 2永久 */
    private boolean isBanEffective(AuthUserState.Ban banState, long now) {
        if (banState == null || banState.getBanType() == null) {
            return false;
        }
        if (banState.getBanType() == 2) {
            return true;
        }
        return banState.getBanType() == 1
                && banState.getBanEnd() != null && banState.getBanEnd() > now;
    }
}

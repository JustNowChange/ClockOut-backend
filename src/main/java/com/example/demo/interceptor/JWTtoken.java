package com.example.demo.interceptor;

import com.example.demo.constant.JwtClaimsConstant;
import com.example.demo.context.BaseContext;
import com.example.demo.properties.JwtProperties;
import com.example.demo.un.AuthUserState;
import com.example.demo.utils.JWTutil;
import com.example.demo.utils.UserCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import io.jsonwebtoken.Claims;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class JWTtoken implements HandlerInterceptor {
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private UserCacheUtils userCacheUtils;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getTokenName());
        String uri = request.getRequestURI();
        log.info("[JWT拦截] {} | 收到请求, token={}", uri,
                token == null ? "缺失" : token.substring(0, Math.min(20, token.length())) + "...");

        //2、校验令牌
        try {
            Claims claims = JWTutil.parseJWT(jwtProperties.getSecretKey(), token);
            // 双token: 业务接口只接受访问令牌, 刷新令牌一律401
            if (!JwtClaimsConstant.ACCESS_TOKEN.equals(claims.get(JwtClaimsConstant.TOKEN_TYPE))) {
                log.warn("[JWT拦截] {} | tokenType={} 非access令牌, 拒绝", uri, claims.get(JwtClaimsConstant.TOKEN_TYPE));
                response.setStatus(401);
                return false;
            }
            Long uid = Long.valueOf(claims.get(JwtClaimsConstant.UID).toString());
            String jti = (String) claims.get(JwtClaimsConstant.JTI);

            // 一次GET取回完整JSON(会话+封禁), 原两次Redis RTT合并为一次
            AuthUserState state = userCacheUtils.getAuthState(uid);

            // 会话白名单: JSON不存在/session不匹配token的jti = 登出/被踢/旧token
            if (jti == null || jti.isEmpty()
                    || state == null || state.getSession() == null
                    || !jti.equals(state.getSession().getJti())) {
                log.warn("[JWT拦截] {} | uid={} 会话不存在或jti不匹配(登出/被踢/旧token), 拒绝", uri, uid);
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":0,\"msg\":\"登录状态已失效, 请重新登录\",\"data\":null}");
                return false;
            }

            // 把完整状态递给BanInterceptor, 其无需再访问Redis
            request.setAttribute(BanInterceptor.ATTR_AUTH_STATE, state);

            BaseContext.setCurrentId(uid);
            log.info("[JWT拦截] {} | 校验通过, uid={}", uri, uid);
            //3、通过，放行
            return true;
        } catch (Exception ex) {
            log.warn("[JWT拦截] {} | 验签/解析失败(缺失或已过期): {}", uri, ex.getMessage());
            //4、不通过，响应401状态码
            response.setStatus(401);
            return false;
        }
    }

    /**
     * 请求结束清理ThreadLocal, 防止Tomcat线程复用导致uid残留串号
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}

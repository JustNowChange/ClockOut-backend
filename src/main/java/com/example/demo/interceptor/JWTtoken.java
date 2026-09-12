package com.example.demo.interceptor;

import com.example.demo.constant.JwtClaimsConstant;
import com.example.demo.context.BaseContext;
import com.example.demo.properties.JwtProperties;
import com.example.demo.utils.JWTutil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import io.jsonwebtoken.Claims;
import javax.net.ssl.HandshakeCompletedListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class JWTtoken implements HandlerInterceptor {
    @Autowired
    private JwtProperties jwtProperties;

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
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            BaseContext.setCurrentId(empId);
            log.info("[JWT拦截] {} | 校验通过, empId={}", uri, empId);
            //3、通过，放行
            return true;
        } catch (Exception ex) {
            log.warn("[JWT拦截] {} | 验签/解析失败(缺失或已过期): {}", uri, ex.getMessage());
            //4、不通过，响应401状态码
            response.setStatus(401);
            return false;
        }
    }
}

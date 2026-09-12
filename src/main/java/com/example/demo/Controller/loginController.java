package com.example.demo.Controller;

import com.example.demo.Result.Result;
import com.example.demo.Vo.userVO;
import com.example.demo.constant.JwtClaimsConstant;
import com.example.demo.properties.JwtProperties;
import com.example.demo.request.userRequest;
import com.example.demo.service.UserService;
import com.example.demo.un.user;
import com.example.demo.utils.JWTutil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class loginController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 刷新令牌在Redis中的key前缀
    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:login:";

    /**
     * 登录
     * @param employee
     * @return
     */
    @PostMapping("/login")
    public Result<userVO> login(@RequestBody userRequest employee, HttpServletResponse response) {

        System.out.println("登录");

        user user = userService.login(employee);
        if (user == null) {
            return Result.error("用户名或密码错误");
        }


        // 访问令牌(短期): 必须带 tokenType=access, 拦截器校验此标记才放行
        Map<String, Object> claims = new HashMap<>();

        claims.put(JwtClaimsConstant.EMP_ID, user.getId());

        claims.put(JwtClaimsConstant.TOKEN_TYPE, JwtClaimsConstant.ACCESS_TOKEN);

        String token = JWTutil.createJWT(
                jwtProperties.getSecretKey(),
                jwtProperties.getTtl(),
                claims);

        // 刷新令牌(长期): tokenType=refresh, 仅用于 /refresh 换新token
        Map<String, Object> refreshClaims = new HashMap<>();

        refreshClaims.put(JwtClaimsConstant.EMP_ID, user.getId());

        refreshClaims.put(JwtClaimsConstant.TOKEN_TYPE, JwtClaimsConstant.REFRESH_TOKEN);

        String refreshToken = JWTutil.createJWT(
                jwtProperties.getSecretKey(),
                jwtProperties.getRefreshTtl(),
                refreshClaims);
        log.info("[登录] step4 refresh签发完成(7天): {}...", refreshToken.substring(0, Math.min(20, refreshToken.length())));

        // 刷新令牌写入Redis, TTL与刷新令牌有效期一致; 刷新轮转时覆盖
        stringRedisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + user.getId(),
                refreshToken,
                jwtProperties.getRefreshTtl(),
                TimeUnit.MILLISECONDS);

        // 方案2: 刷新令牌通过httpOnly Cookie下发, 不进JSON响应体(JS无法读取, 防XSS窃取)
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false)   //TODO 本地http调试用; 生产HTTPS环境改为true
                .sameSite("Strict")
                .path("/api/auth")   // 仅认证相关接口携带
                .maxAge(jwtProperties.getRefreshTtl() / 1000)   // 与刷新令牌有效期一致(秒)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("[登录] step6 refresh已通过httpOnly Cookie下发");

        userVO employeeLoginVO = userVO.builder()
                .id(user.getId())
                .username(employee.getUsername())
                .token(token)
                .build();

        log.info("[登录] step7 登录成功: empId={}, username={}", user.getId(), employee.getUsername());
        return Result.success(employeeLoginVO);
    }

    /*
    * 获取用户信息
    *
    */
    @GetMapping("/user-info/{id}")
    public Result<userVO> getUserInfo(@PathVariable int id) {

        user user = userService.getUserInfo(id);

        //拷贝
        userVO employeeLoginVO = userVO.builder()
                .id(user.getId())
                .name(user.getName())
                .status(user.getStatus())
                .build();

        return Result.success(employeeLoginVO);

    }
}

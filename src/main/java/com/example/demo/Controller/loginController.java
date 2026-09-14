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
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

    // 刷新令牌在Redis中的key前缀, 完整key为 refresh:login:{userId}:{jti}
    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:login:";

    /**
     * 登录
     * @param employee
     * @return
     */
    @PostMapping("/login")
    public Result<userVO> login(@RequestBody userRequest employee) {

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
        // jti: 本次会话(本标签页)唯一编号, 同一用户多标签并行时各持一条独立Redis记录
        String jti = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> refreshClaims = new HashMap<>();

        refreshClaims.put(JwtClaimsConstant.EMP_ID, user.getId());

        refreshClaims.put(JwtClaimsConstant.TOKEN_TYPE, JwtClaimsConstant.REFRESH_TOKEN);

        refreshClaims.put(JwtClaimsConstant.JTI, jti);

        String refreshToken = JWTutil.createJWT(
                jwtProperties.getSecretKey(),
                jwtProperties.getRefreshTtl(),
                refreshClaims);
        log.info("[登录] step4 refresh签发完成(7天), jti={}...", jti.substring(0, Math.min(8, jti.length())));

        // 刷新令牌写入Redis: 按 用户ID+jti 存储, 多标签/多端互不覆盖; TTL与刷新令牌一致
        stringRedisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + user.getId() + ":" + jti,
                refreshToken,
                jwtProperties.getRefreshTtl(),
                TimeUnit.MILLISECONDS);

        // 刷新令牌直接放入响应体, 前端存入标签页级 sessionStorage（支持同浏览器多账号并行）
        userVO employeeLoginVO = userVO.builder()
                .id(user.getId())
                .username(employee.getUsername())
                .token(token)
                .refreshToken(refreshToken)
                .status(user.getStatus())  // 角色：1-普通用户 2-管理员，前端据此分流
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

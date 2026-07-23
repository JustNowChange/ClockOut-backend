package com.example.demo.Controller;

import com.example.demo.Result.Result;
import com.example.demo.Vo.userVO;
import com.example.demo.constant.JwtClaimsConstant;
import com.example.demo.properties.JwtProperties;
import com.example.demo.request.userRequest;
import com.example.demo.service.UserService;
import com.example.demo.un.user;
import com.example.demo.utils.JWTutil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class loginController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;

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

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, user.getId());
        String token = JWTutil.createJWT(
                jwtProperties.getSecretKey(),
                jwtProperties.getTtl(),
                claims);
        userVO employeeLoginVO = userVO.builder()
                .id(user.getId())
                .username(employee.getUsername())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }
}

package com.example.demo.service.serviceImpl;

import com.example.demo.mapper.UserMapper;
import com.example.demo.request.userRequest;
import com.example.demo.service.UserService;
import com.example.demo.un.user;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    public UserMapper userMapper;

    public user login(userRequest employee) {
        String username = employee.getUsername();

        user user = userMapper.login(username);

        String password = employee.getPassword();
        if(user == null){
           System.out.println("用户不存在");
           return null;
        }
        // 密码加密
        password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!password.equals(user.getPassword())) {
            //密码错误
            System.out.println("密码错误");
            return null;
        }

        System.out.println("登录成功");
        return user;
    }
}

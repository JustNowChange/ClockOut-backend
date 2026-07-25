package com.example.demo.mapper;

import com.example.demo.un.user;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Select("select * from user where username = #{username}")
    user login(String  username);

    @Select("select * from user where id = #{id}")
    user getUserInfo(int id);
}

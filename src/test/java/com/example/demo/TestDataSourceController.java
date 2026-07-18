package com.example.demo;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;

@SpringBootTest
@RestController
public class TestDataSourceController {
    @Resource
    private DataSource dataSource;


    @GetMapping("/test/db")
    public String testDb() {
        return "数据源初始化成功："+dataSource.getClass().getName();
    }
}

package com.example.demo.Controller;

import com.example.demo.Result.Result;
import com.example.demo.service.ClockService;
import com.example.demo.un.days;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/*
*
* 首先是图片上传问题,一般来说是用oss ,但是要钱(就不做替换为直接插入数据)
* 还有api访问什么的,是直接后接数字啥的很容易更改
* 因为现在是直接发送api的,后端回反复调用数据库的
* 没有用户限制,就不知道哪个用户恶意调用数据库(数据库也是要钱的)
*  github在提交的yml中暴露了数据库信息(优先处理)
* 后续考虑定时提醒任务,发送邮件给用户,提醒用户
* 后续添加功能,先确定功能,后实现,后优化!!!
*
*
* */

@RestController
@RequestMapping("/api")
public class Controller {
    @Autowired
    private ClockService clockService;


    /**
     * 获取学习天数,渲染页面
     * @return
     */
    @GetMapping("/study/days")
    public Result<List<days>> getStudyDays() {
        List<days> getlist = clockService.getlist();
        return Result.success(getlist);
    }

    /**
     * 获取学习天数详情
     * @param dayId
     * @return
     */
    @GetMapping("/clock/detail/{dayId}")
    public Result<days> getClockDetail(@PathVariable("dayId") Integer dayId) {
        days day = clockService.getDetail(dayId);
        return Result.success(day);
    }
    /**
     * 测试后续可删
     * @return
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("ok");
    }
}
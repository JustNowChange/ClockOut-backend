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


@RestController
@RequestMapping("/api")
public class Controller {
    @Autowired
    private ClockService clockService;


    @GetMapping("/study/days")
    public Result<List<days>> getStudyDays() {
        List<days> getlist = clockService.getlist();
        return Result.success(getlist);
    }

    @GetMapping("/clock/detail/{dayId}")
    public Result<days> getClockDetail(@PathVariable("dayId") Integer dayId) {
        days day = clockService.getDetail(dayId);
        return Result.success(day);
    }
}
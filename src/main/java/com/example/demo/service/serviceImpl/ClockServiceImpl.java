package com.example.demo.service.serviceImpl;

import com.example.demo.mapper.ClockMapper;
import com.example.demo.service.ClockService;
import com.example.demo.un.days;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClockServiceImpl implements ClockService {

    @Autowired
    public ClockMapper clockMapper;

    public List<days> getlist() {

        return clockMapper.getlist();
    }


    public void updateStatus(int id) {
        clockMapper.updateStatus(id);
    }
}

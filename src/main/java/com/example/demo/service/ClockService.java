package com.example.demo.service;

import com.example.demo.un.days;
import java.util.List;

public interface ClockService {

    List<days> getlist();

    void updateStatus(int id);

    days getDetail(int id);
}

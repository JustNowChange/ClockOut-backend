package com.example.demo.mapper;

import com.example.demo.un.days;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ClockMapper {

    @Select("select * from study_days")
    List<days> getlist();

    @Update("update study_days set status = 1, completeTime = NOW() where id = #{id}")
    void updateStatus(int id);

    @Select("select * from study_days where id = #{id}")
    days getDetail(int id);
}

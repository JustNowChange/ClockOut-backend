package com.example.demo.mapper;

import com.example.demo.un.Resume;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ResumeMapper {

    @Select("SELECT * FROM resume WHERE user_id = #{userId} LIMIT 1")
    Resume getByUserId(Long userId);

    @Select("SELECT * FROM resume WHERE id = #{id} LIMIT 1")
    Resume getById(Long id);

    @Insert("INSERT INTO resume (user_id, name, title, phone, email, location, github, summary) " +
            "VALUES (#{userId}, #{name}, #{title}, #{phone}, #{email}, #{location}, #{github}, #{summary})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Resume resume);

    @Update("UPDATE resume SET name=#{name}, title=#{title}, phone=#{phone}, email=#{email}, " +
            "location=#{location}, github=#{github}, summary=#{summary} WHERE id=#{id}")
    int updateCore(Resume resume);

    @Select("SELECT r.* FROM resume r WHERE r.user_id != #{excludeUserId} ORDER BY r.id DESC")
    List<Resume> listAllExcept(Long excludeUserId);
}

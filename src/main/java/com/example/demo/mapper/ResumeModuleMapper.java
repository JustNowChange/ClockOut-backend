package com.example.demo.mapper;

import com.example.demo.un.ResumeModule;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ResumeModuleMapper {

    @Select("SELECT * FROM resume_module WHERE resume_id = #{resumeId} ORDER BY sort_order ASC, id ASC")
    List<ResumeModule> listByResumeId(Long resumeId);

    @Select("SELECT * FROM resume_module WHERE id = #{id} LIMIT 1")
    ResumeModule getById(Long id);

    @Select("SELECT COALESCE(MAX(sort_order), -1) FROM resume_module WHERE resume_id = #{resumeId}")
    Integer getMaxSortOrder(Long resumeId);

    @Insert("INSERT INTO resume_module (resume_id, module_type, module_title, sort_order, content) " +
            "VALUES (#{resumeId}, #{moduleType}, #{moduleTitle}, #{sortOrder}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ResumeModule module);

    @Update("UPDATE resume_module SET module_title=#{moduleTitle}, content=#{content} WHERE id=#{id}")
    int updateModule(@Param("id") Long id,
                     @Param("moduleTitle") String moduleTitle,
                     @Param("content") String content);

    @Update("UPDATE resume_module SET sort_order=#{sortOrder} WHERE id=#{id}")
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);

    @Delete("DELETE FROM resume_module WHERE id=#{id}")
    int deleteById(Long id);
}

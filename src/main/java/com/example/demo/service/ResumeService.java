package com.example.demo.service;

import com.example.demo.Vo.ResumeDetailVO;
import com.example.demo.Vo.ResumeListItemVO;
import com.example.demo.request.ModuleCreateRequest;
import com.example.demo.request.ModuleSortRequest;
import com.example.demo.request.ModuleUpdateRequest;
import com.example.demo.request.ResumeCoreUpdateRequest;
import com.example.demo.un.ResumeModule;

import java.util.List;

public interface ResumeService {

    /** 获取当前用户的完整简历(没有则创建空简历) */
    ResumeDetailVO getOrCreateCurrentResume(Long userId);

    /** 更新当前用户简历核心信息 */
    ResumeDetailVO.CoreInfo updateCore(Long userId, ResumeCoreUpdateRequest req);

    /** 新增模块 */
    ResumeModule addModule(Long userId, ModuleCreateRequest req);

    /** 更新模块标题+content */
    ResumeModule updateModule(Long userId, Long moduleId, ModuleUpdateRequest req);

    /** 删除模块(校验所属权) */
    void deleteModule(Long userId, Long moduleId);

    /** 调整模块排序 */
    void updateSort(Long userId, ModuleSortRequest req);

    /** 查看指定简历详情(只读,无需权限,查别人的) */
    ResumeDetailVO getResumeById(Long id);

    /** 查看指定简历详情(带脱敏,非本人查看时脱敏电话邮箱) */
    ResumeDetailVO getResumeByIdWithMask(Long id, Long requesterUserId);

    /** 他人简历列表(排除自己) */
    List<ResumeListItemVO> listResumes(Long currentUserId);
}

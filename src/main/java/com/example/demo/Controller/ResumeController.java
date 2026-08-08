package com.example.demo.Controller;

import com.example.demo.Result.Result;
import com.example.demo.Vo.ResumeDetailVO;
import com.example.demo.Vo.ResumeListItemVO;
import com.example.demo.context.BaseContext;
import com.example.demo.request.ModuleCreateRequest;
import com.example.demo.request.ModuleSortRequest;
import com.example.demo.request.ModuleUpdateRequest;
import com.example.demo.request.ResumeCoreUpdateRequest;
import com.example.demo.service.ResumeService;
import com.example.demo.un.ResumeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    /**
     * 1. 获取自己的完整简历(没有则创建空简历)
     * GET /api/resume
     * 需要登录
     */
    @GetMapping("")
    public Result<ResumeDetailVO> getMyResume() {
        Long userId = BaseContext.getCurrentId();
        ResumeDetailVO vo = resumeService.getOrCreateCurrentResume(userId);
        return Result.success(vo);
    }

    /**
     * TODO api没做 这里直接发送单个数据,其他的数据会默认空,不知道前端做保存没有
     * 2. 更新自己简历的核心信息
     * PUT /api/resume
     * 需要登录
     */
    @PutMapping
    public Result<ResumeDetailVO.CoreInfo> updateCore(@RequestBody ResumeCoreUpdateRequest req) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(resumeService.updateCore(userId, req));
    }

    /**
     * 3. 他人简历列表(排除自己)
     * GET /api/resume/list
     * 无参数
     * 需要登录(也可以不登录,但登录了能排除自己)
     */
    @GetMapping("/list")
    public Result<List<ResumeListItemVO>> list() {
        Long userId = BaseContext.getCurrentId();  // 未登录拦截器会 401,这里能拿到就排除自己
        List<ResumeListItemVO> list = resumeService.listResumes(userId);
        return Result.success(list);
    }

    /**
     * 4. 查看指定简历详情(只读,用于点击他人简历查看)
     * GET /api/resume/{id}
     * 不需要登录(也支持登录访问)
     * 非本人查看时自动脱敏(电话、邮箱)
     */
    @GetMapping("/{id}")
    public Result<ResumeDetailVO> getResumeById(@PathVariable Long id) {
        try {
            // 获取当前请求者ID(可能为null,因为此接口放行)
            Long requesterId = null;
            try {
                requesterId = BaseContext.getCurrentId();
            } catch (Exception ignored) {
                // 未登录时 getCurrentId 可能返回 null,忽略
            }
            // 使用带脱敏的查询
            ResumeDetailVO vo = resumeService.getResumeByIdWithMask(id, requesterId);
            return Result.success(vo);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * POST /api/resume/module
     * 需要登录
     */
    @PostMapping("/module")
    public Result<ResumeModule> addModule(@RequestBody ModuleCreateRequest req) {
        if (req.getModuleType() == null || req.getModuleType().isEmpty()) {
            return Result.error("moduleType 不能为空");
        }
        Long userId = BaseContext.getCurrentId();
        ResumeModule module = resumeService.addModule(userId, req);
        return Result.success(module);
    }

    /**
     * 6. 更新模块(标题+content)
     * PUT /api/resume/module/{id}
     * 需要登录
     */
    @PutMapping("/module/{id}")
    public Result<ResumeModule> updateModule(@PathVariable Long id,
                                             @RequestBody ModuleUpdateRequest req) {
        try {
            Long userId = BaseContext.getCurrentId();
            ResumeModule m = resumeService.updateModule(userId, id, req);
            System.out.println(m+"-----");
            return Result.success(m);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 7. 删除模块
     * DELETE /api/resume/module/{id}
     * 需要登录
     */
    @DeleteMapping("/module/{id}")
    public Result<?> deleteModule(@PathVariable Long id) {
        try {
            Long userId = BaseContext.getCurrentId();
            resumeService.deleteModule(userId, id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 8. 批量调整模块排序
     * PATCH /api/resume/module/sort
     * 需要登录
     */
    @PatchMapping("/module/sort")
    public Result<?> updateSort(@RequestBody ModuleSortRequest req) {
        try {
            Long userId = BaseContext.getCurrentId();
            resumeService.updateSort(userId, req);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}

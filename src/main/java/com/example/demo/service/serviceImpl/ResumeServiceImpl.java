package com.example.demo.service.serviceImpl;

import com.example.demo.Vo.ResumeDetailVO;
import com.example.demo.Vo.ResumeListItemVO;
import com.example.demo.mapper.ResumeMapper;
import com.example.demo.mapper.ResumeModuleMapper;
import com.example.demo.request.ModuleCreateRequest;
import com.example.demo.request.ModuleSortRequest;
import com.example.demo.request.ModuleUpdateRequest;
import com.example.demo.request.ResumeCoreUpdateRequest;
import com.example.demo.service.ResumeService;
import com.example.demo.un.Resume;
import com.example.demo.un.ResumeModule;
import com.example.demo.utils.ResumeCacheUtils;
import com.example.demo.utils.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ResumeServiceImpl implements ResumeService {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    @Autowired
    private ResumeMapper resumeMapper;
    @Autowired
    private ResumeModuleMapper moduleMapper;
    @Autowired
    private ResumeCacheUtils cacheUtils;

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String DEFAULT_CONTENT = "[]";

    // 字段长度限制
    private static final int MAX_NAME = 50;
    private static final int MAX_TITLE = 100;
    private static final int MAX_PHONE = 30;
    private static final int MAX_EMAIL = 100;
    private static final int MAX_LOCATION = 100;
    private static final int MAX_GITHUB = 100;
    private static final int MAX_SUMMARY = 2000;
    private static final int MAX_MODULE_TITLE = 50;

    // 格式校验正则
    private static final Pattern PATTERN_PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern PATTERN_PHONE_FLEX = Pattern.compile("^1[3-9]\\d{1}-?\\d{4}-?\\d{4}$");
    private static final Pattern PATTERN_EMAIL = Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PATTERN_GITHUB_URL = Pattern.compile("^https?://(www\\.)?github\\.com/[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?(/.*)?$");
    private static final Pattern PATTERN_GITHUB_USERNAME = Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?$");

    private static final String[] DEFAULT_MODULE_TYPES = {"education", "experience", "project", "skill", "award"};
    private static final Map<String, String> DEFAULT_TITLES = new HashMap<>();
    static {
        DEFAULT_TITLES.put("education", "教育背景");
        DEFAULT_TITLES.put("experience", "工作经历");
        DEFAULT_TITLES.put("project", "项目经验");
        DEFAULT_TITLES.put("skill", "专业技能");
        DEFAULT_TITLES.put("award", "荣誉奖项");
    }

    // ================= 字段校验+XSS转义 =================

    private String checkAndSanitize(String value, String fieldName, int maxLength) {
        if (value == null) return "";
        if (value.length() > maxLength) {
            throw new RuntimeException(fieldName + " 长度不能超过 " + maxLength + " 个字符");
        }
        return SecurityUtils.sanitizeText(value);
    }

    private void validatePhone(String phone) {
        if (phone == null || phone.isEmpty()) return;
        String digitsOnly = phone.replaceAll("[\\s-]", "");
        if (!PATTERN_PHONE.matcher(digitsOnly).matches()) {
            throw new RuntimeException("手机号格式不正确，请输入有效的11位手机号");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isEmpty()) return;
        if (!PATTERN_EMAIL.matcher(email).matches()) {
            throw new RuntimeException("邮箱格式不正确");
        }
    }

    private void validateGithub(String github) {
        if (github == null || github.isEmpty()) return;
        String trimmed = github.trim();
        // 允许两种格式: GitHub URL 或 纯用户名
        if (PATTERN_GITHUB_URL.matcher(trimmed).matches()) {
            return;
        }
        if (PATTERN_GITHUB_USERNAME.matcher(trimmed).matches() && trimmed.length() <= 39) {
            return;
        }
        throw new RuntimeException("GitHub 格式不正确，请输入有效的 GitHub 链接或用户名");
    }

    /** 校验并转义核心字段 */
    private void validateAndSanitizeCore(ResumeCoreUpdateRequest req) {
        req.setName(checkAndSanitize(req.getName(), "姓名", MAX_NAME));
        req.setTitle(checkAndSanitize(req.getTitle(), "职位", MAX_TITLE));
        String phone = checkAndSanitize(req.getPhone(), "电话", MAX_PHONE);
        validatePhone(phone);
        req.setPhone(phone);
        String email = checkAndSanitize(req.getEmail(), "邮箱", MAX_EMAIL);
        validateEmail(email);
        req.setEmail(email);
        req.setLocation(checkAndSanitize(req.getLocation(), "所在地", MAX_LOCATION));
        String github = checkAndSanitize(req.getGithub(), "GitHub", MAX_GITHUB);
        validateGithub(github);
        req.setGithub(github);
        req.setSummary(checkAndSanitize(req.getSummary(), "个人简介", MAX_SUMMARY));
    }

    /** 校验模块 */
    private void validateModule(String moduleType, String moduleTitle, String content) {
        if (moduleType == null || moduleType.isEmpty()) {
            throw new RuntimeException("模块类型不能为空");
        }
        String title = checkAndSanitize(moduleTitle, "模块标题", MAX_MODULE_TITLE);
        // content 是 JSON,做长度限制 + 结构校验
        if (content != null && !content.isEmpty()) {
            if (content.length() > 50000) {
                throw new RuntimeException("模块内容过长");
            }
            validateModuleContent(content);
        }
    }

    /**
     * 校验模块 content 的 JSON 结构
     * 要求：必须是合法 JSON 数组，且数组元素为对象（Map），防止存入畸形数据
     */
    private void validateModuleContent(String content) {
        try {
            Object parsed = OM.readValue(content, Object.class);
            if (!(parsed instanceof List)) {
                throw new RuntimeException("模块内容必须是 JSON 数组");
            }
            List<?> list = (List<?>) parsed;
            // 限制数组元素数量，防止超长 payload
            if (list.size() > 500) {
                throw new RuntimeException("模块条目数量不能超过 500");
            }
            // 校验每个元素必须是对象（Map），防止存入标量数组等畸形结构
            for (Object item : list) {
                if (item != null && !(item instanceof Map)) {
                    throw new RuntimeException("模块条目必须是 JSON 对象");
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("模块内容不是合法的 JSON 格式");
        }
    }

    // ================= 内部工具方法 =================

    private ResumeDetailVO toDetailVO(Resume resume, List<ResumeModule> modules) {
        ResumeDetailVO.CoreInfo core = ResumeDetailVO.CoreInfo.builder()
                .name(resume.getName())
                .title(resume.getTitle())
                .phone(resume.getPhone())
                .email(resume.getEmail())
                .location(resume.getLocation())
                .github(resume.getGithub())
                .summary(resume.getSummary())
                .build();

        List<ResumeDetailVO.ModuleInfo> moduleInfos = modules.stream().map(m -> {
            Object contentObj;
            try {
                contentObj = OM.readValue(m.getContent() == null ? DEFAULT_CONTENT : m.getContent(), Object.class);
            } catch (Exception e) {
                contentObj = new ArrayList<>();
            }
            return ResumeDetailVO.ModuleInfo.builder()
                    .id(m.getId())
                    .moduleType(m.getModuleType())
                    .moduleTitle(m.getModuleTitle())
                    .sortOrder(m.getSortOrder())
                    .content(contentObj)
                    .build();
        }).collect(Collectors.toList());

        return ResumeDetailVO.builder()
                .id(resume.getId())
                .userId(resume.getUserId())
                .core(core)
                .modules(moduleInfos)
                .build();
    }

    /** 脱敏处理(非本人查看时调用) — 深拷贝后脱敏,避免污染缓存对象 */
    private ResumeDetailVO desensitize(ResumeDetailVO vo, Long requesterUserId) {
        // 只有非本人查看时才脱敏
        if (requesterUserId != null && requesterUserId.equals(vo.getUserId())) {
            return vo;  // 本人查看,不脱敏
        }
        // 深拷贝,避免修改缓存里的原始对象
        try {
            String json = OM.writeValueAsString(vo);
            ResumeDetailVO copy = OM.readValue(json, ResumeDetailVO.class);
            ResumeDetailVO.CoreInfo core = copy.getCore();
            core.setPhone(SecurityUtils.maskPhone(core.getPhone()));
            core.setEmail(SecurityUtils.maskEmail(core.getEmail()));
            return copy;
        } catch (Exception e) {
            // 拷贝失败,降级:只拷贝 core 部分
            ResumeDetailVO.CoreInfo origCore = vo.getCore();
            ResumeDetailVO.CoreInfo maskedCore = ResumeDetailVO.CoreInfo.builder()
                    .name(origCore.getName())
                    .title(origCore.getTitle())
                    .phone(SecurityUtils.maskPhone(origCore.getPhone()))
                    .email(SecurityUtils.maskEmail(origCore.getEmail()))
                    .location(origCore.getLocation())
                    .github(origCore.getGithub())
                    .summary(origCore.getSummary())
                    .build();
            vo.setCore(maskedCore);
            return vo;
        }
    }

    /** 创建空简历 + 默认模块 */
    private Resume createEmptyResume(Long userId) {
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setName("");
        resume.setTitle("");
        resume.setPhone("");
        resume.setEmail("");
        resume.setLocation("");
        resume.setGithub("");
        resume.setSummary("");
        resumeMapper.insert(resume);

        int sort = 0;
        for (String type : DEFAULT_MODULE_TYPES) {
            ResumeModule m = new ResumeModule();
            m.setResumeId(resume.getId());
            m.setModuleType(type);
            m.setModuleTitle(DEFAULT_TITLES.get(type));
            m.setSortOrder(sort++);
            m.setContent(DEFAULT_CONTENT);
            moduleMapper.insert(m);
        }
        return resume;
    }

    /** 校验模块属于该用户 */
    private ResumeModule checkModuleOwnership(Long userId, Long moduleId) {
        ResumeModule m = moduleMapper.getById(moduleId);
        if (m == null) {
            throw new RuntimeException("模块不存在");
        }
        Resume resume = resumeMapper.getById(m.getResumeId());
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw new RuntimeException("无权限操作此模块");
        }
        return m;
    }

    /** 清除相关缓存 */
    private void invalidateCache(Long resumeId) {
        cacheUtils.deleteDetail(resumeId);
        cacheUtils.clearListCache();  // 列表缓存失效
    }

    // ================= 接口实现 =================

    @Override
    public ResumeDetailVO getOrCreateCurrentResume(Long userId) {
        Resume resume = resumeMapper.getByUserId(userId);
        if (resume == null) {
            resume = createEmptyResume(userId);
        }
        List<ResumeModule> modules = moduleMapper.listByResumeId(resume.getId());
        return toDetailVO(resume, modules);
    }

    @Override
    public ResumeDetailVO.CoreInfo updateCore(Long userId, ResumeCoreUpdateRequest req) {
        // 校验+转义
        validateAndSanitizeCore(req);

        Resume resume = resumeMapper.getByUserId(userId);
        if (resume == null) {
            resume = createEmptyResume(userId);
        }
        resume.setName(req.getName());
        resume.setTitle(req.getTitle());
        resume.setPhone(req.getPhone());
        resume.setEmail(req.getEmail());
        resume.setLocation(req.getLocation());
        resume.setGithub(req.getGithub());
        resume.setSummary(req.getSummary());
        resumeMapper.updateCore(resume);

        // 清除缓存
        invalidateCache(resume.getId());

        // 审计日志：记录核心信息修改
        auditLog.info("[审计] 用户={} 修改简历核心信息 resumeId={} 姓名={}", userId, resume.getId(), req.getName());

        return ResumeDetailVO.CoreInfo.builder()
                .name(resume.getName())
                .title(resume.getTitle())
                .phone(resume.getPhone())
                .email(resume.getEmail())
                .location(resume.getLocation())
                .github(resume.getGithub())
                .summary(resume.getSummary())
                .build();
    }

    @Override
    public ResumeModule addModule(Long userId, ModuleCreateRequest req) {
        // 校验
        validateModule(req.getModuleType(), req.getModuleTitle(), req.getContent());

        Resume resume = resumeMapper.getByUserId(userId);
        if (resume == null) {
            resume = createEmptyResume(userId);
        }
        Integer maxSort = moduleMapper.getMaxSortOrder(resume.getId());
        int newSort = (maxSort == null ? -1 : maxSort) + 1;

        ResumeModule m = new ResumeModule();
        m.setResumeId(resume.getId());
        m.setModuleType(req.getModuleType());
        m.setModuleTitle(SecurityUtils.sanitizeText(req.getModuleTitle()));
        m.setSortOrder(newSort);
        m.setContent(req.getContent() == null || req.getContent().isEmpty() ? DEFAULT_CONTENT : req.getContent());
        moduleMapper.insert(m);

        // 清除缓存
        invalidateCache(resume.getId());

        // 审计日志：记录新增模块
        auditLog.info("[审计] 用户={} 新增简历模块 resumeId={} moduleId={} type={}", userId, resume.getId(), m.getId(), req.getModuleType());

        return m;
    }

    @Override
    public ResumeModule updateModule(Long userId, Long moduleId, ModuleUpdateRequest req) {
        ResumeModule m = checkModuleOwnership(userId, moduleId);

        // 校验
        String title = req.getModuleTitle() == null ? m.getModuleTitle() : req.getModuleTitle();
        String content = req.getContent() == null ? m.getContent() : req.getContent();
        validateModule(m.getModuleType(), title, content);

        moduleMapper.updateModule(moduleId, SecurityUtils.sanitizeText(title), content);
        m = moduleMapper.getById(moduleId);

        // 清除缓存
        Resume resume = resumeMapper.getById(m.getResumeId());
        if (resume != null) invalidateCache(resume.getId());

        // 审计日志：记录修改模块
        auditLog.info("[审计] 用户={} 修改简历模块 moduleId={} resumeId={} 标题={}", userId, moduleId, m.getResumeId(), title);

        return m;
    }

    @Override
    public void deleteModule(Long userId, Long moduleId) {
        ResumeModule m = checkModuleOwnership(userId, moduleId);
        Long resumeId = m.getResumeId();
        moduleMapper.deleteById(moduleId);

        // 清除缓存
        invalidateCache(resumeId);

        // 审计日志：记录删除模块（警告级别，便于追溯）
        auditLog.warn("[审计] 用户={} 删除简历模块 moduleId={} resumeId={} type={}", userId, moduleId, resumeId, m.getModuleType());
    }

    @Override
    public void updateSort(Long userId, ModuleSortRequest req) {
        if (req.getModuleOrders() == null) return;
        Long resumeId = null;
        for (ModuleSortRequest.SortItem item : req.getModuleOrders()) {
            ResumeModule m = checkModuleOwnership(userId, item.getId());
            if (resumeId == null) resumeId = m.getResumeId();
            moduleMapper.updateSortOrder(item.getId(), item.getSortOrder());
        }

        // 清除缓存
        if (resumeId != null) invalidateCache(resumeId);
    }

    @Override
    public ResumeDetailVO getResumeById(Long id) {
        // 先查缓存
        ResumeDetailVO cached = cacheUtils.getDetail(id, ResumeDetailVO.class);
        if (cached != null) {
            return cached;
        }

        Resume resume = resumeMapper.getById(id);
        if (resume == null) {
            throw new RuntimeException("简历不存在");
        }
        List<ResumeModule> modules = moduleMapper.listByResumeId(resume.getId());
        ResumeDetailVO vo = toDetailVO(resume, modules);

        // 缓存
        cacheUtils.setDetail(id, vo);

        return vo;
    }

    /** 带脱敏的详情查询 */
    @Override
    public ResumeDetailVO getResumeByIdWithMask(Long id, Long requesterUserId) {
        ResumeDetailVO vo = getResumeById(id);
        return desensitize(vo, requesterUserId);
    }

    @Override
    public List<ResumeListItemVO> listResumes(Long currentUserId) {
        // 先查缓存
        TypeReference<List<ResumeListItemVO>> typeRef = new TypeReference<List<ResumeListItemVO>>() {};
        List<ResumeListItemVO> cached = cacheUtils.getList(currentUserId, typeRef);
        if (cached != null) {
            return cached;
        }

        Long exclude = currentUserId == null ? -1L : currentUserId;
        List<Resume> resumes = resumeMapper.listAllExcept(exclude);
        List<ResumeListItemVO> result = new ArrayList<>();
        for (Resume r : resumes) {
            List<ResumeModule> modules = moduleMapper.listByResumeId(r.getId());
            result.add(toListItemVO(r, modules));
        }

        // 缓存
        cacheUtils.setList(currentUserId, result);

        return result;
    }

    // ============ List 接口聚合逻辑 ============
    private ResumeListItemVO toListItemVO(Resume r, List<ResumeModule> modules) {
        String education = "";
        String experience = "";
        int projectCount = 0;
        List<String> skills = new ArrayList<>();

        Map<String, ResumeModule> byType = modules.stream()
                .collect(Collectors.toMap(ResumeModule::getModuleType, m -> m, (a, b) -> a));

        // education
        ResumeModule edu = byType.get("education");
        if (edu != null) {
            try {
                List<Map<String, Object>> list = OM.readValue(edu.getContent() == null ? DEFAULT_CONTENT : edu.getContent(),
                        new TypeReference<List<Map<String, Object>>>() {});
                if (!list.isEmpty()) {
                    Object school = list.get(0).get("school");
                    education = school == null ? "" : school.toString();
                }
            } catch (Exception ignored) {}
        }

        // experience
        ResumeModule exp = byType.get("experience");
        if (exp != null) {
            try {
                List<Map<String, Object>> list = OM.readValue(exp.getContent() == null ? DEFAULT_CONTENT : exp.getContent(),
                        new TypeReference<List<Map<String, Object>>>() {});
                if (!list.isEmpty()) {
                    Map<String, Object> first = list.get(0);
                    Object company = first.get("company");
                    Object position = first.get("position");
                    if (company != null && !company.toString().isEmpty()) {
                        experience = company.toString();
                        if (position != null && !position.toString().isEmpty()) {
                            experience += " · " + position.toString();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // project count
        ResumeModule proj = byType.get("project");
        if (proj != null) {
            try {
                List<Object> list = OM.readValue(proj.getContent() == null ? DEFAULT_CONTENT : proj.getContent(),
                        new TypeReference<List<Object>>() {});
                projectCount = list.size();
            } catch (Exception ignored) {}
        }

        // skills (取前2个分类的items 合并 截取前4个)
        ResumeModule sk = byType.get("skill");
        if (sk != null) {
            try {
                List<Map<String, Object>> cats = OM.readValue(sk.getContent() == null ? DEFAULT_CONTENT : sk.getContent(),
                        new TypeReference<List<Map<String, Object>>>() {});
                int catCount = 0;
                for (Map<String, Object> cat : cats) {
                    if (catCount++ >= 2) break;
                    Object items = cat.get("items");
                    if (items instanceof Collection) {
                        for (Object o : (Collection<?>) items) {
                            skills.add(o.toString());
                            if (skills.size() >= 4) break;
                        }
                    }
                    if (skills.size() >= 4) break;
                }
            } catch (Exception ignored) {}
        }

        return ResumeListItemVO.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .name(r.getName())
                .title(r.getTitle())
                .education(education)
                .experience(experience)
                .projectCount(projectCount)
                .skills(skills)
                .build();
    }
}

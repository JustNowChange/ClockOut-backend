package com.example.demo.Controller;

import com.example.demo.config.UploadProperties;
import com.example.demo.service.ClockService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/api")
public class UploadController  {
    // 注入yml配置的上传路径
    @Resource
    private UploadProperties uploadProperties;
    @Autowired
    private ClockService clockService;

    /**
     * 图片上传接口
     * @param file 前端传的图片文件
     * @return 返回可直接访问的图片url
     */
    @PostMapping("/upload/image")
    @Transactional
    public String uploadImage(@RequestParam("image") MultipartFile file,
                              @RequestParam("dayId") String dayId                   ) throws IOException {
        // 1. 获取原始文件名，截取后缀 .jpg/.png
        String originalName = file.getOriginalFilename();
        String suffix = originalName.substring(originalName.lastIndexOf("."));
        // 2. UUID生成唯一文件名，防止重名覆盖
        //String newFileName = UUID.randomUUID() + suffix;
        String newFileName = "第"+dayId+"天" + suffix;

        // 3. 拼接完整本地存储路径
        File saveFile = new File(uploadProperties.getLocalPath() + newFileName);
        // 文件夹不存在则自动创建
        if (!saveFile.getParentFile().exists()) {
            saveFile.getParentFile().mkdirs();
        }


        // 更新状态和完成时间
        clockService.updateStatus(Integer.parseInt(dayId));
        // 4. 写入本地磁盘
        file.transferTo(saveFile);

        // 5. 返回浏览器可访问的url（前缀+文件名）
        return uploadProperties.getAccessPrefix() + newFileName;
    }
}

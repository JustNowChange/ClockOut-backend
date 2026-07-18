package com.example.demo.Controller;

import com.example.demo.config.UploadProperties;
import com.example.demo.service.ClockService;
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
    @Autowired
    private ClockService clockService;

    @Autowired
    private UploadProperties uploadProperties;

    @PostMapping("/upload/image")
    @Transactional
    public String uploadImage(@RequestParam("image") MultipartFile file,
                              @RequestParam("dayId") String dayId) throws IOException {
        String originalName = file.getOriginalFilename();
        String suffix = originalName.substring(originalName.lastIndexOf("."));
        String newFileName = "第"+dayId+"天" + suffix;

        clockService.updateStatus(Integer.parseInt(dayId));

        File saveFile = new File(uploadProperties.getLocalPath() + newFileName);
        if (!saveFile.getParentFile().exists()) {
            saveFile.getParentFile().mkdirs();
        }
        file.transferTo(saveFile);

        return uploadProperties.getAccessPrefix() + newFileName;
    }
}

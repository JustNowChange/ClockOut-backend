package com.example.demo.Controller;

import com.example.demo.config.CosConfig;
import com.example.demo.service.ClockService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import javax.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api")
public class UploadController  {
    @Autowired
    private ClockService clockService;

    @Autowired(required = false)
    private COSClient cosClient;

    @Autowired(required = false)
    private CosConfig cosConfig;

    @PostMapping("/upload/image")
    @Transactional
    public String uploadImage(@RequestParam("image") MultipartFile file,
                              @RequestParam("dayId") String dayId) throws IOException {
        String originalName = file.getOriginalFilename();
        String suffix = originalName.substring(originalName.lastIndexOf("."));
        String newFileName = "第"+dayId+"天" + suffix;

        clockService.updateStatus(Integer.parseInt(dayId));

        if (cosClient != null && cosConfig != null && cosConfig.getBucketName() != null && !cosConfig.getBucketName().isEmpty()) {
            try (InputStream inputStream = file.getInputStream()) {
                PutObjectRequest putObjectRequest = new PutObjectRequest(
                        cosConfig.getBucketName(),
                        newFileName,
                        inputStream,
                        null
                );
                PutObjectResult result = cosClient.putObject(putObjectRequest);
                return cosConfig.getDomain() + "/" + newFileName;
            }
        } else {
            return "/image/" + newFileName;
        }
    }
}

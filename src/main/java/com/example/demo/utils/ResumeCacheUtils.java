package com.example.demo.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis缓存服务(简历相关)
 */
@Component
public class ResumeCacheUtils {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${resume.cache.enabled:true}")
    private boolean cacheEnabled;
    @Value("${resume.cache.list-ttl:60}")
    private long listTtl;
    @Value("${resume.cache.detail-ttl:300}")
    private long detailTtl;

    private static final String KEY_LIST = "resume:list";
    private static final String KEY_DETAIL_PREFIX = "resume:detail:";
    private static final String KEY_USER_LIST_PREFIX = "resume:list:";  // 按用户区分(排除自己)

    /**
     * 获取简历列表缓存
     */
    public <T> T getList(Long userId, TypeReference<T> typeRef) {
        if (!cacheEnabled) return null;
        try {
            String key = userId != null ? KEY_USER_LIST_PREFIX + userId : KEY_LIST;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                System.out.println("[Redis缓存] 列表命中, key=" + key);
                return objectMapper.readValue(json, typeRef);
            }
            System.out.println("[Redis缓存] 列表未命中, key=" + key);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 列表反序列化失败: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 缓存简历列表
     */
    public <T> void setList(Long userId, T data) {
        if (!cacheEnabled) return;
        try {
            String key = userId != null ? KEY_USER_LIST_PREFIX + userId : KEY_LIST;
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, listTtl, TimeUnit.SECONDS);
            System.out.println("[Redis缓存] 列表已写入, key=" + key + ", ttl=" + listTtl + "s, size=" + json.length());
        } catch (Exception e) {
            System.err.println("[Redis缓存] 列表写入失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取简历详情缓存
     */
    public <T> T getDetail(Long resumeId, Class<T> clazz) {
        if (!cacheEnabled) return null;
        try {
            String key = KEY_DETAIL_PREFIX + resumeId;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                System.out.println("[Redis缓存] 详情命中, key=" + key);
                return objectMapper.readValue(json, clazz);
            }
            System.out.println("[Redis缓存] 详情未命中, key=" + key);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 详情反序列化失败: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 缓存简历详情
     */
    public <T> void setDetail(Long resumeId, T data) {
        if (!cacheEnabled) return;
        try {
            String key = KEY_DETAIL_PREFIX + resumeId;
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, detailTtl, TimeUnit.SECONDS);
            System.out.println("[Redis缓存] 详情已写入, key=" + key + ", ttl=" + detailTtl + "s, size=" + json.length());
        } catch (Exception e) {
            System.err.println("[Redis缓存] 详情写入失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 删除简历详情缓存
     */
    public void deleteDetail(Long resumeId) {
        if (!cacheEnabled) return;
        try {
            redisTemplate.delete(KEY_DETAIL_PREFIX + resumeId);
            System.out.println("[Redis缓存] 详情已删除, key=" + KEY_DETAIL_PREFIX + resumeId);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 详情删除失败: " + e.getMessage());
        }
    }

    /**
     * 清空列表缓存
     */
    public void clearListCache() {
        if (!cacheEnabled) return;
        try {
            redisTemplate.delete(KEY_LIST);
            // 模糊删除所有 list 缓存
            redisTemplate.delete(redisTemplate.keys(KEY_USER_LIST_PREFIX + "*"));
            System.out.println("[Redis缓存] 列表缓存已清空");
        } catch (Exception e) {
            System.err.println("[Redis缓存] 列表清空失败: " + e.getMessage());
        }
    }

    /**
     * 清空所有简历缓存
     */
    public void clearAllCache() {
        if (!cacheEnabled) return;
        try {
            redisTemplate.delete(KEY_LIST);
            redisTemplate.delete(redisTemplate.keys(KEY_USER_LIST_PREFIX + "*"));
            redisTemplate.delete(redisTemplate.keys(KEY_DETAIL_PREFIX + "*"));
            System.out.println("[Redis缓存] 所有缓存已清空");
        } catch (Exception e) {
            System.err.println("[Redis缓存] 全部清空失败: " + e.getMessage());
        }
    }
}

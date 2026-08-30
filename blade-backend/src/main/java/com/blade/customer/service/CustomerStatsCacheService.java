package com.blade.customer.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 客户统计缓存失效服务（系列 E）。
 * 订单、状态和财务动作发生后，订单动作服务通过本服务失效相关客户的
 * 偏好/统计缓存（customer:preference:{customerId}:*），保证统计一致性。
 */
@Service
public class CustomerStatsCacheService {

    public static final String PREFERENCE_KEY_PREFIX = "customer:preference:";

    private final RedisTemplate<String, Object> redisTemplate;

    public CustomerStatsCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 失效单个客户的偏好统计缓存（模式：customer:preference:{customerId}:*）。
     */
    public void evictPreferenceCache(Long customerId) {
        if (customerId == null) {
            return;
        }
        Set<String> keys = redisTemplate.keys(PREFERENCE_KEY_PREFIX + customerId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}

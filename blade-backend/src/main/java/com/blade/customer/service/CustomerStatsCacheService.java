package com.blade.customer.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 客户统计缓存失效服务（系列 E）。
 * 订单、状态和财务动作发生后，订单动作服务通过本服务失效相关客户的
 * 偏好/统计缓存，保证统计一致性。
 *
 * <p>缓存键格式固定为 {@code customer:preference:{customerId}:{scopeFingerprint}:{start}:{end}}，
 * 客户 ID 必须紧邻前缀，因此 {@link #evictPreferenceCache(Long)} 一次可清除该客户
 * 所有范围指纹、所有时间窗的结果（{@code customer:preference:{customerId}:*}）。</p>
 */
@Service
public class CustomerStatsCacheService {

    public static final String PREFERENCE_KEY_PREFIX = "customer:preference:";

    private final RedisTemplate<String, Object> redisTemplate;

    public CustomerStatsCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 失效单个客户的全部偏好统计缓存（模式：customer:preference:{customerId}:*）。
     * 覆盖该客户所有档口/人员范围指纹与所有时间窗，避免仅失效当前操作人范围造成脏读。
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

package com.blade.common.tenant;

import com.blade.common.exception.BusinessException;

public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    /**
     * 统一租户上下文入口：缺失时 fail closed 抛业务 403，绝不回退 tenant=1。
     * 认证链（AuthService/JWT/Agent/Collector）与后台按租户循环的任务必须在调用前显式设置。
     */
    public static Long requireTenantId() {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            throw BusinessException.of(403, "缺少租户上下文");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * 判断是否为超级管理员租户
     */
    public static boolean isSuperAdmin() {
        Long tenantId = getTenantId();
        return tenantId != null && tenantId == 0L;
    }
}

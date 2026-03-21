package com.blade.common.tenant;

public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
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

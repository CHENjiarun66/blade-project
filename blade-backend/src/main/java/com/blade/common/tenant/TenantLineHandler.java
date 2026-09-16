package com.blade.common.tenant;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Arrays;
import java.util.List;

public class TenantLineHandler implements com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler {

    // 不需要租户过滤的表（无 tenant_id 字段的关联表）
    private static final List<String> IGNORE_TABLES = Arrays.asList(
        "sys_tenant",
        // 权限定义按 V54 确认为全局共享；租户差异位于角色及角色-权限关联。
        "sys_permission",
        "product_color_rel",
        "product_size_rel",
        "sys_role_permission",
        "sys_user_role"
    );

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = 1L;
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName);
    }
}

package com.blade.outlet;

import com.blade.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutletPermissionMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void outletPermissionCodesAreGloballyUnique() {
        for (String code : new String[]{
                "menu:outlet", "btn:outlet:create", "btn:outlet:edit", "btn:outlet:disable",
                "data:outlet:all", "data:order:peopleAll", "agent:outlets:read"}) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_permission WHERE code = ?", Integer.class, code);
            assertEquals(1, count, "权限 " + code + " 必须全局唯一（恰好一行）");
        }
    }

    @Test
    void ownerAdminHaveManagementAndDataScope() {
        for (String roleCode : new String[]{"ROLE_OWNER", "ROLE_ADMIN"}) {
            assertTrue(hasRolePermission(roleCode, "menu:outlet"), roleCode + " 应有 menu:outlet");
            assertTrue(hasRolePermission(roleCode, "btn:outlet:create"), roleCode + " 应有 btn:outlet:create");
            assertTrue(hasRolePermission(roleCode, "data:outlet:all"), roleCode + " 应有 data:outlet:all");
            assertTrue(hasRolePermission(roleCode, "data:order:peopleAll"), roleCode + " 应有 data:order:peopleAll");
        }
    }

    @Test
    void financeHasDataScopeButNoManagementButtons() {
        assertTrue(hasRolePermission("ROLE_FINANCE", "data:outlet:all"));
        assertTrue(hasRolePermission("ROLE_FINANCE", "data:order:peopleAll"));
        assertEquals(0, countRolePermission("ROLE_FINANCE", "btn:outlet:create"), "财务不应有 btn:outlet:create");
    }

    @Test
    void salesHasNoAllOutletScope() {
        assertEquals(0, countRolePermission("ROLE_SALES", "data:outlet:all"), "销售员不应有 data:outlet:all");
    }

    @Test
    void legacyViewAllPermissionIsNotDeleted() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE code = 'btn:order:viewAll'", Integer.class);
        assertEquals(1, count, "btn:order:viewAll 不得删除");
    }

    private boolean hasRolePermission(String roleCode, String permCode) {
        return countRolePermission(roleCode, permCode) > 0;
    }

    private int countRolePermission(String roleCode, String permCode) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE r.role_code = ? AND p.code = ?
                """, Integer.class, roleCode, permCode);
        return count == null ? 0 : count;
    }
}

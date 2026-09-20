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

    @Test
    void grantRestoresSoftDeletedRelation() {
        jdbc.update("""
                UPDATE sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                SET rp.deleted = 1
                WHERE r.role_code = 'ROLE_OWNER' AND p.code = 'data:outlet:all'
                """);
        runOwnerAdminGrant();
        Integer deleted = jdbc.queryForObject("""
                SELECT rp.deleted FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE r.role_code = 'ROLE_OWNER' AND p.code = 'data:outlet:all'
                """, Integer.class);
        assertEquals(0, deleted, "软删关系应被 V64 赋权恢复");
    }

    @Test
    void viewAllCompatMigratesValidSameTenantRelation() {
        String roleCode = "ROLE_OT" + (System.nanoTime() % 100000L);
        jdbc.update("INSERT INTO sys_role (role_name, role_code, description, tenant_id, status, deleted) VALUES (?, ?, ?, 1, 1, 0)",
                "档口迁移测试", roleCode, "测试");
        Long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code = ?", Long.class, roleCode);
        Long viewAllId = jdbc.queryForObject("SELECT id FROM sys_permission WHERE code = 'btn:order:viewAll'", Long.class);
        jdbc.update("INSERT INTO sys_role_permission (role_id, permission_id, tenant_id, deleted) VALUES (?, ?, 1, 0)",
                roleId, viewAllId);

        runViewAllCompat();

        Integer n = countRolePermission(roleCode, "data:outlet:all");
        assertEquals(1, n, "有效 viewAll 关系应迁移到 data:outlet:all");
        Integer tenantId = jdbc.queryForObject("""
                SELECT rp.tenant_id FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE r.role_code = ? AND p.code = 'data:outlet:all'
                """, Integer.class, roleCode);
        assertEquals(1, tenantId, "新关系 tenant_id 应等于角色租户");
    }

    @Test
    void viewAllCompatIgnoresSoftDeletedSourceRelation() {
        String roleCode = "ROLE_OS" + (System.nanoTime() % 100000L);
        jdbc.update("INSERT INTO sys_role (role_name, role_code, description, tenant_id, status, deleted) VALUES (?, ?, ?, 1, 1, 0)",
                "档口软删测试", roleCode, "测试");
        Long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code = ?", Long.class, roleCode);
        Long viewAllId = jdbc.queryForObject("SELECT id FROM sys_permission WHERE code = 'btn:order:viewAll'", Long.class);
        jdbc.update("INSERT INTO sys_role_permission (role_id, permission_id, tenant_id, deleted) VALUES (?, ?, 1, 1)",
                roleId, viewAllId);

        runViewAllCompat();

        assertEquals(0, countRolePermission(roleCode, "data:outlet:all"),
                "软删 viewAll 关系不得迁移出新数据权限");
    }

    private void runOwnerAdminGrant() {
        jdbc.update("""
                INSERT INTO sys_role_permission (role_id, permission_id, tenant_id)
                SELECT r.id, p.id, r.tenant_id
                FROM sys_role r, sys_permission p
                WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')
                  AND p.code IN ('menu:outlet','btn:outlet:create','btn:outlet:edit','btn:outlet:disable','data:outlet:all','data:order:peopleAll')
                  AND r.deleted = 0 AND r.status = 1 AND p.deleted = 0 AND p.status = 1
                ON DUPLICATE KEY UPDATE tenant_id = VALUES(tenant_id), deleted = 0
                """);
    }

    private void runViewAllCompat() {
        jdbc.update("""
                INSERT INTO sys_role_permission (role_id, permission_id, tenant_id)
                SELECT rp.role_id, p.id, rp.tenant_id
                FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id AND r.tenant_id = rp.tenant_id
                               AND r.deleted = 0 AND r.status = 1
                JOIN sys_permission old ON old.id = rp.permission_id AND old.code = 'btn:order:viewAll'
                                      AND old.deleted = 0 AND old.status = 1
                JOIN sys_permission p ON p.code IN ('data:outlet:all','data:order:peopleAll')
                                     AND p.deleted = 0 AND p.status = 1
                WHERE rp.deleted = 0
                ON DUPLICATE KEY UPDATE tenant_id = VALUES(tenant_id), deleted = 0
                """);
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

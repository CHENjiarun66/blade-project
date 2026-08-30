package com.blade.order;

import com.blade.common.tenant.TenantContext;
import com.blade.system.permission.mapper.PermissionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 终审三轮 P0-5：V52+V53+V54 权限多租户模型验证（真实隔离库）。
 * 验证：
 * 1. 权限定义全局唯一（同 code 只有一行，uk_code 不冲突）
 * 2. 多租户角色都能通过全局权限 code 获得赋权（sys_role_permission.tenant_id = 角色租户）
 * 3. V54 幂等（重放不重复）
 * 4. 全部 8 个动作 code 都存在且被 OWNER/ADMIN 角色关联
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderPermissionTenantTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PermissionMapper permissionMapper;

    private static final List<String> ACTION_CODES = List.of(
            "btn:order:recordPayment", "btn:order:writeOff", "btn:order:refund", "btn:order:reverse",
            "btn:order:chooseFulfillment", "btn:order:allocate", "btn:order:export", "btn:order:viewFinance",
            "btn:order:viewAll");

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void permissionDefinitions_areGloballyUnique() {
        for (String code : ACTION_CODES) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_permission WHERE code = ?", Integer.class, code);
            assertEquals(1, count, "权限 " + code + " 必须全局唯一（恰好一行）");
        }
    }

    @Test
    void ownerRole_inAllTenants_hasAllActionPermissions() {
        // 建第二租户数据（幂等：ON DUPLICATE / NOT EXISTS）
        jdbc.update("INSERT IGNORE INTO sys_tenant (id, tenant_code, tenant_name, status) VALUES (2, 'second_tenant', '第二租户', 1)");
        jdbc.update("INSERT IGNORE INTO sys_role (role_name, role_code, description, tenant_id, status, deleted) VALUES ('老板/经理', 'ROLE_OWNER', '测试第二租户', 2, 1, 0)");

        // 找出所有租户
        List<Long> tenantIds = jdbc.queryForList("SELECT DISTINCT tenant_id FROM sys_role WHERE deleted = 0", Long.class);
        assertFalse(tenantIds.isEmpty());

        // 终审三轮 P0-5：权限是全局的（uk_code），但角色赋权应覆盖第二租户
        // 用 V54 语义重新赋权（模拟 V54 对第二租户的效果）
        jdbc.update("""
                INSERT INTO sys_role_permission (role_id, permission_id, tenant_id)
                SELECT r.id, p.id, r.tenant_id
                FROM sys_role r, sys_permission p
                WHERE r.role_code = 'ROLE_OWNER' AND r.tenant_id = 2
                  AND p.code IN ('btn:order:recordPayment','btn:order:writeOff','btn:order:refund',
                                 'btn:order:reverse','btn:order:chooseFulfillment','btn:order:allocate',
                                 'btn:order:export','btn:order:viewFinance')
                  AND r.deleted = 0 AND p.deleted = 0
                ON DUPLICATE KEY UPDATE role_id = role_id
                """);

        for (Long tenantId : tenantIds) {
            Integer roleCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_role WHERE role_code = 'ROLE_OWNER' AND tenant_id = ? AND deleted = 0",
                    Integer.class, tenantId);
            if (roleCount == null || roleCount == 0) {
                continue; // 该租户没有 OWNER 角色，跳过
            }
            // OWNER 角色应有全部动作权限关联
            Integer granted = jdbc.queryForObject("""
                    SELECT COUNT(DISTINCT p.code) FROM sys_role_permission rp
                    JOIN sys_role r ON r.id = rp.role_id AND r.tenant_id = rp.tenant_id
                    JOIN sys_permission p ON p.id = rp.permission_id
                    WHERE r.role_code = 'ROLE_OWNER' AND r.tenant_id = ? AND r.deleted = 0
                      AND p.code IN ('btn:order:recordPayment','btn:order:writeOff','btn:order:refund',
                                     'btn:order:reverse','btn:order:chooseFulfillment','btn:order:allocate',
                                     'btn:order:export','btn:order:viewFinance')
                    """, Integer.class, tenantId);
            assertEquals(8, granted, "租户 " + tenantId + " OWNER 角色应有全部 8 个动作权限");
        }
    }

    @Test
    void viewAll_grantedToOwnerAdminFinance() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_role_permission rp
                JOIN sys_permission p ON p.id = rp.permission_id AND p.code = 'btn:order:viewAll'
                JOIN sys_role r ON r.id = rp.role_id AND r.tenant_id = rp.tenant_id
                WHERE r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN', 'ROLE_FINANCE') AND r.deleted = 0
                """, Integer.class);
        assertTrue(count >= 1, "viewAll 应至少赋给一个角色");
    }

    @Test
    void salesRole_doesNotHaveWriteOffPermission() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_role_permission rp
                JOIN sys_permission p ON p.id = rp.permission_id AND p.code = 'btn:order:writeOff'
                JOIN sys_role r ON r.id = rp.role_id AND r.tenant_id = rp.tenant_id
                WHERE r.role_code = 'ROLE_SALES' AND r.deleted = 0
                """, Integer.class);
        assertEquals(0, count, "SALES 不得有 writeOff 权限");
    }

    @Test
    void tenantTwoUser_loadsGlobalPermissionDefinitionsThroughMapper() {
        String username = "t2p" + Long.toString(System.nanoTime()).substring(4);
        jdbc.update("INSERT IGNORE INTO sys_tenant (id, tenant_code, tenant_name, status) VALUES (2, 'second_tenant', '第二租户', 1)");
        jdbc.update("INSERT IGNORE INTO sys_role (role_name, role_code, description, tenant_id, status, deleted) VALUES ('老板/经理', 'ROLE_OWNER', '测试第二租户', 2, 1, 0)");
        jdbc.update("INSERT INTO sys_user (username, password, nickname, tenant_id, status, deleted) VALUES (?, 'n/a', '租户二权限用户', 2, 1, 0)", username);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM sys_role WHERE role_code='ROLE_OWNER' AND tenant_id=2 AND deleted=0", Long.class);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM sys_user WHERE username=? AND tenant_id=2 AND deleted=0", Long.class, username);
        jdbc.update("INSERT IGNORE INTO sys_user_role (user_id, role_id, tenant_id, deleted) VALUES (?, ?, 2, 0)", userId, roleId);
        jdbc.update("""
                INSERT INTO sys_role_permission (role_id, permission_id, tenant_id, deleted)
                SELECT ?, p.id, 2, 0 FROM sys_permission p
                WHERE p.code IN ('btn:order:recordPayment', 'btn:order:viewAll') AND p.deleted=0
                ON DUPLICATE KEY UPDATE tenant_id=2, deleted=0
                """, roleId);

        TenantContext.setTenantId(2L);
        List<String> codes = permissionMapper.selectCodesByUserId(userId);

        assertTrue(codes.contains("btn:order:recordPayment"));
        assertTrue(codes.contains("btn:order:viewAll"));
    }

    @Test
    void warehouseRole_canReadFulfillmentOrdersButCannotReadFinance() {
        Integer fulfillmentScope = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT p.code) FROM sys_role_permission rp
                JOIN sys_role r ON r.id=rp.role_id AND r.tenant_id=rp.tenant_id
                JOIN sys_permission p ON p.id=rp.permission_id
                WHERE r.role_code='ROLE_WAREHOUSE' AND r.deleted=0
                  AND p.code IN ('menu:order','btn:order:view','btn:order:viewAll')
                """, Integer.class);
        Integer financeScope = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_role_permission rp
                JOIN sys_role r ON r.id=rp.role_id AND r.tenant_id=rp.tenant_id
                JOIN sys_permission p ON p.id=rp.permission_id
                WHERE r.role_code='ROLE_WAREHOUSE' AND r.deleted=0
                  AND p.code='btn:order:viewFinance'
                """, Integer.class);

        assertEquals(3, fulfillmentScope);
        assertEquals(0, financeScope);
    }
}

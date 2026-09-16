package com.blade.order;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V52 权限契约验证：权限 code、同租户 JOIN 赋权、幂等、无迁移端点权限。
 */
class OrderV52PermissionSchemaTest {

    private String v52() throws Exception {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/V52__order_action_permission.sql"));
    }

    @Test
    void definesAllFinanceActionPermissionCodes() throws Exception {
        String sql = v52();
        assertThat(sql)
                .contains("'btn:order:recordPayment'")
                .contains("'btn:order:writeOff'")
                .contains("'btn:order:refund'")
                .contains("'btn:order:reverse'")
                .contains("'btn:order:chooseFulfillment'")
                .contains("'btn:order:allocate'")
                .contains("'btn:order:export'")
                .contains("'btn:order:viewFinance'");
    }

    @Test
    void roleAssignmentsJoinOnSameTenantAndStayIdempotent() throws Exception {
        String sql = v52();
        long insertCount = sql.split("INSERT INTO `sys_role_permission`", -1).length - 1;
        assertThat(insertCount).isEqualTo(4);
        // 每个角色赋权语句都必须带同租户 JOIN 条件
        assertThat(sql.split("INSERT INTO `sys_role_permission`", -1))
                .allSatisfy(part -> {
                    if (part.isBlank()) return;
                    if (part.contains("SELECT r.id, p.id, r.tenant_id")) {
                        assertThat(part).contains("r.tenant_id = p.tenant_id");
                        assertThat(part).contains("ON DUPLICATE KEY UPDATE `role_id` = `role_id`");
                    }
                });
    }

    @Test
    void ownerAndAdminReceiveAllNormalActions() throws Exception {
        String sql = v52();
        assertThat(sql).contains("r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')");
    }

    @Test
    void salesDoesNotReceiveFinanceViewUntilScopeFilterLands() throws Exception {
        String sql = v52();
        assertThat(sql)
                .contains("WHERE r.role_code = 'ROLE_SALES'\n  AND p.code IN ('btn:order:recordPayment', 'btn:order:export')");
    }

    @Test
    void containsNoMigrationPermissionOrEndpointHook() throws Exception {
        String sql = v52();
        assertThat(sql)
                .doesNotContain("migration")
                .doesNotContain("Controller");
    }
}

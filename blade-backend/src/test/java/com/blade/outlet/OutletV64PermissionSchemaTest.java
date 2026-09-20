package com.blade.outlet;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OutletV64PermissionSchemaTest {

    private String v64() throws Exception {
        return Files.readString(Path.of("src/main/resources/db/migration/V64__outlet_permissions.sql"));
    }

    @Test
    void seedsAllOutletPermissionCodes() throws Exception {
        String sql = v64();
        assertThat(sql)
                .contains("'menu:outlet'")
                .contains("'btn:outlet:create'")
                .contains("'btn:outlet:edit'")
                .contains("'btn:outlet:disable'")
                .contains("'data:outlet:all'")
                .contains("'data:order:peopleAll'")
                .contains("'agent:outlets:read'");
    }

    @Test
    void grantsRestoreTenantAndDeletedOnDuplicate() throws Exception {
        String sql = v64();
        // 三段赋权 ON DUPLICATE 都必须恢复 tenant_id 与 deleted，不能只 no-op role_id=role_id
        assertThat(sql)
                .contains("`tenant_id` = VALUES(`tenant_id`), `deleted` = 0")
                .doesNotContain("`role_id` = `role_id`")
                .doesNotContain("INSERT IGNORE");
    }

    @Test
    void grantsOnlyFromValidRolesAndPermissions() throws Exception {
        String sql = v64();
        assertThat(sql)
                .contains("r.deleted = 0 AND r.status = 1 AND p.deleted = 0 AND p.status = 1")
                .contains("r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')")
                .contains("r.role_code = 'ROLE_FINANCE'");
    }

    @Test
    void viewAllCompatOnlyMigratesValidSameTenantRelations() throws Exception {
        String sql = v64();
        assertThat(sql)
                .contains("r.tenant_id = rp.tenant_id")
                .contains("r.deleted = 0 AND r.status = 1")
                .contains("old.deleted = 0 AND old.status = 1")
                .contains("p.deleted = 0 AND p.status = 1")
                .contains("WHERE rp.deleted = 0")
                .contains("old.code = 'btn:order:viewAll'")
                .contains("'data:outlet:all'", "'data:order:peopleAll'")
                .doesNotContain("DELETE FROM `sys_permission`");
    }
}

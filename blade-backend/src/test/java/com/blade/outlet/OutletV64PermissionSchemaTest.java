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
    void grantsManagementButtonsOnlyToOwnerAdmin() throws Exception {
        String sql = v64();
        assertThat(sql)
                .contains("r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')")
                .contains("'btn:outlet:create'", "'btn:outlet:edit'", "'btn:outlet:disable'")
                .contains("r.role_code = 'ROLE_FINANCE'");
    }

    @Test
    void mapsExistingViewAllHoldersToNewDataScopeWithoutDeletingOld() throws Exception {
        String sql = v64();
        assertThat(sql)
                .contains("old.code = 'btn:order:viewAll'")
                .contains("'data:outlet:all'", "'data:order:peopleAll'")
                .doesNotContain("DELETE FROM `sys_permission`");
    }
}

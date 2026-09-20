package com.blade.outlet;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** V65 迁移静态契约：data:outlet:unassigned + order_draft.created_by_user_id + 索引。 */
class OutletV65SchemaTest {

    private String v65() throws Exception {
        return Files.readString(Path.of("src/main/resources/db/migration/V65__outlet_access_policy.sql"));
    }

    @Test
    void addsUnassignedPermissionGrantedOnlyToOwnerAdmin() throws Exception {
        String sql = v65();
        assertThat(sql)
                .contains("'data:outlet:unassigned'")
                .contains("r.role_code IN ('ROLE_OWNER', 'ROLE_ADMIN')")
                .doesNotContain("ROLE_FINANCE")
                .doesNotContain("ROLE_SALES");
    }

    @Test
    void addsDraftCreatorColumnAndIndexes() throws Exception {
        String sql = v65();
        assertThat(sql)
                .contains("`created_by_user_id` bigint DEFAULT NULL")
                .contains("idx_order_draft_tenant_outlet")
                .contains("idx_order_draft_tenant_creator")
                .doesNotContain("UPDATE `order_draft`");
    }
}

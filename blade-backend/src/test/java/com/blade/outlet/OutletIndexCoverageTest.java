package com.blade.outlet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TEST-OUTLET-004（本地）：档口范围相关索引核对。
 *
 * <p>只断言索引存在，不对 EXPLAIN 输出做脆弱断言；生产规模性能结论保持 pending。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class OutletIndexCoverageTest {

    private static final List<String[]> EXPECTED = List.of(
            new String[]{"sale_order", "idx_tenant_id"},
            new String[]{"sale_order", "idx_status"},
            new String[]{"sale_order", "idx_so_source_outlet"},
            new String[]{"order_draft", "idx_order_draft_tenant_status"},
            new String[]{"order_draft", "idx_order_draft_source_outlet"},
            new String[]{"order_draft", "idx_order_draft_tenant_outlet"},
            new String[]{"order_draft", "idx_order_draft_tenant_creator"},
            new String[]{"sales_outlet", "uk_outlet_code_tenant"},
            new String[]{"sales_outlet", "idx_outlet_tenant_status"},
            new String[]{"sys_user_outlet", "idx_user_outlet_user"},
            new String[]{"sys_user_outlet", "idx_user_outlet_outlet"},
            new String[]{"agent_key_outlet", "idx_agent_key_outlet_key"},
            new String[]{"agent_key_outlet", "uk_agent_key_outlet"}
    );

    @Autowired private JdbcTemplate jdbc;

    @Test
    void outletScopeIndexesExist() {
        for (String[] expected : EXPECTED) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                    Integer.class, expected[0], expected[1]);
            assertEquals(1, count, "缺少索引 " + expected[1] + " on " + expected[0]);
        }
    }
}

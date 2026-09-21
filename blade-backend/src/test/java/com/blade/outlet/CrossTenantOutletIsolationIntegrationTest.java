package com.blade.outlet;

import com.blade.agent.dto.AgentKeyManagementDTO;
import com.blade.agent.service.AgentKeyManagementService;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.outlet.dto.OutletUpdateDTO;
import com.blade.outlet.service.OutletService;
import com.blade.system.user.dto.UserCreateDTO;
import com.blade.system.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二批A：跨租户真实 DB 反例（非 mock）。
 *
 * <p>tenant1 上下文下：用户绑定 tenant2 档口必须失败且不写关系；Agent Key 绑定/默认 tenant2
 * 档口必须失败且无 key/绑定副作用；档口服务读、改、启停 tenant2 档口一律 404 且原行不变。
 * 覆盖既有 mock 用例 {@code UserOutletBindingTest.crossTenantOutletIsRejectedAsNotFound}
 * 的真实数据库版本。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CrossTenantOutletIsolationIntegrationTest {

    private static final long TENANT_1 = 1L;
    private static final long TENANT_2 = 2L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserService userService;
    @Autowired private AgentKeyManagementService agentKeyService;
    @Autowired private OutletService outletService;

    private String suffix;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_1);
        suffix = Long.toString(System.nanoTime()).substring(8);
    }

    @AfterEach
    void tearDown() {
        // 非事务用例：清理残留（正常路径下服务已回滚，仅清理种子档口与潜在失败残留）
        jdbc.update("DELETE FROM sys_user_outlet WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'xt_u_%')");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'xt_u_%')");
        jdbc.update("DELETE FROM sys_user WHERE username LIKE 'xt_u_%'");
        jdbc.update("DELETE FROM agent_key_outlet WHERE agent_key_id IN (SELECT id FROM agent_key WHERE name LIKE 'xt%')");
        jdbc.update("DELETE FROM agent_key WHERE name LIKE 'xt%'");
        jdbc.update("DELETE FROM sales_outlet WHERE outlet_code LIKE 'XT-%'");
        TenantContext.clear();
    }

    @Test
    void tenant1UserCannotBindTenant2OutletAndNoBindingWritten() {
        long outlet2 = seedOutlet(TENANT_2, "XT-U-" + suffix);
        Long salesRoleId = jdbc.queryForObject(
                "SELECT id FROM sys_role WHERE role_code='ROLE_SALES' AND tenant_id=? AND deleted=0",
                Long.class, TENANT_1);

        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("xt_u_" + suffix);
        dto.setPassword("xt-pass-123");
        dto.setRoleIds(new Long[]{salesRoleId});
        dto.setOutletIds(new Long[]{outlet2});

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(dto));
        assertTrue(ex.getCode() == 404 || ex.getCode() == 400, "跨租户档口必须以不存在/非法拒绝，实际 " + ex.getCode());
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user WHERE username=?", "xt_u_" + suffix),
                "失败必须整体回滚，不得残留用户");
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user_outlet WHERE outlet_id=?", outlet2),
                "失败不得写入任何跨租户绑定");
    }

    @Test
    void tenant1AgentKeyCannotBindOrDefaultTenant2OutletAndNoKeyWritten() {
        long outlet2 = seedOutlet(TENANT_2, "XT-A-" + suffix);
        String assignedName = "xt_assigned_" + suffix;
        String allName = "xt_all_" + suffix;

        BusinessException assignedEx = assertThrows(BusinessException.class, () -> agentKeyService.create(
                new AgentKeyManagementDTO.CreateRequest(assignedName, List.of("products:read"),
                        30, "ASSIGNED", List.of(outlet2), null)));
        assertTrue(assignedEx.getCode() >= 400, "ASSIGNED 跨租户必须失败");
        assertEquals(0, count("SELECT COUNT(*) FROM agent_key WHERE name=?", assignedName));

        BusinessException allEx = assertThrows(BusinessException.class, () -> agentKeyService.create(
                new AgentKeyManagementDTO.CreateRequest(allName, List.of("products:read"),
                        30, "ALL", null, outlet2)));
        assertTrue(allEx.getCode() >= 400, "ALL 默认跨租户档口必须失败");
        assertEquals(0, count("SELECT COUNT(*) FROM agent_key WHERE name=?", allName));

        assertEquals(0, count("SELECT COUNT(*) FROM agent_key_outlet WHERE outlet_id=?", outlet2),
                "失败不得写入任何跨租户 Key 档口绑定");
    }

    @Test
    void tenant1CannotReadUpdateOrToggleTenant2Outlet() {
        long outlet2 = seedOutlet(TENANT_2, "XT-O-" + suffix);
        String originalName = jdbc.queryForObject(
                "SELECT outlet_name FROM sales_outlet WHERE id=?", String.class, outlet2);

        assertEquals(404, assertThrows(BusinessException.class, () -> outletService.getById(outlet2)).getCode(),
                "跨租户读取按既有语义 404");

        OutletUpdateDTO update = new OutletUpdateDTO();
        update.setId(outlet2);
        update.setOutletCode("XT-O-" + suffix);
        update.setOutletName("cross-tenant-hacked");
        assertEquals(404, assertThrows(BusinessException.class, () -> outletService.update(update)).getCode(),
                "跨租户修改 404");

        assertEquals(404, assertThrows(BusinessException.class, () -> outletService.updateStatus(outlet2, 0)).getCode(),
                "跨租户启停 404");

        assertEquals(originalName, jdbc.queryForObject(
                "SELECT outlet_name FROM sales_outlet WHERE id=?", String.class, outlet2), "跨租户修改不得生效");
        assertEquals(1, jdbc.queryForObject(
                "SELECT status FROM sales_outlet WHERE id=?", Integer.class, outlet2), "跨租户启停不得生效");
    }

    // ==================== fixtures ====================

    private long seedOutlet(long tenantId, String code) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default)"
                + " VALUES(?,?,?,'STORE',1,0,0,0)", tenantId, code, "跨租户档口" + code);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=? AND outlet_code=?",
                Long.class, tenantId, code);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}

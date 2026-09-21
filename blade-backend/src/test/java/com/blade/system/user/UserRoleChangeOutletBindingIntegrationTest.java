package com.blade.system.user;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.system.user.dto.UserUpdateDTO;
import com.blade.system.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P1 审计整改：角色被改为销售员时，即使 outletIds=null（旧客户端保留绑定），
 * 也必须基于最终有效绑定校验"销售员至少一个启用档口"，失败整体回滚；
 * 显式 outletIds 去重不得触发唯一键 500。
 *
 * <p>非 {@code @Transactional}：需要观察真实事务回滚后的提交状态。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class UserRoleChangeOutletBindingIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserService userService;

    private static final String PREFIX = "p1role_";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM sys_user_outlet WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)", PREFIX + "%");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)", PREFIX + "%");
        jdbc.update("DELETE FROM sys_user WHERE username LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM sales_outlet WHERE outlet_code LIKE 'P1-%'");
        TenantContext.clear();
    }

    @Test
    void changingRoleToSalesWithoutAnyBindingIsRejectedAndRoleUnchanged() {
        Long userId = user("nonbind", "ROLE_WAREHOUSE");

        BusinessException ex = assertThrows(BusinessException.class, () -> updateRoles(userId, "ROLE_SALES"));

        assertEquals(400, ex.getCode());
        assertEquals("ROLE_WAREHOUSE", roleCodeOf(userId));
    }

    @Test
    void changingRoleToSalesKeepsExistingValidBinding() {
        Long outlet = outlet("P1-A", 1);
        Long userId = user("bound", "ROLE_WAREHOUSE");
        bind(userId, outlet);

        updateRoles(userId, "ROLE_SALES");

        assertEquals("ROLE_SALES", roleCodeOf(userId));
        assertEquals(1, bindingCount(userId));
    }

    @Test
    void disabledBindingDoesNotSatisfySalesRequirementAndRoleUnchanged() {
        Long disabledOutlet = outlet("P1-D", 0);
        Long userId = user("disabledbind", "ROLE_WAREHOUSE");
        bind(userId, disabledOutlet);

        BusinessException ex = assertThrows(BusinessException.class, () -> updateRoles(userId, "ROLE_SALES"));

        assertEquals(400, ex.getCode());
        assertEquals("ROLE_WAREHOUSE", roleCodeOf(userId));
    }

    @Test
    void explicitDuplicateOutletIdsAreDedupedWithoutUniqueViolation() {
        Long outlet = outlet("P1-B", 1);
        Long userId = user("dupe", "ROLE_WAREHOUSE");

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setId(userId);
        dto.setRoleIds(new Long[]{roleId("ROLE_SALES")});
        dto.setOutletIds(new Long[]{outlet, outlet});
        dto.setDefaultOutletId(outlet);
        userService.update(dto);

        assertEquals("ROLE_SALES", roleCodeOf(userId));
        assertEquals(1, bindingCount(userId));
    }

    private void updateRoles(Long userId, String roleCode) {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setId(userId);
        dto.setRoleIds(new Long[]{roleId(roleCode)});
        // 旧客户端语义：outletIds=null 表示保留现有绑定
        dto.setOutletIds(null);
        userService.update(dto);
    }

    private Long user(String name, String roleCode) {
        String username = PREFIX + name;
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,'测试',1,1,0)",
                username, "x");
        Long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id,tenant_id,deleted) VALUES(?,?,1,0)",
                userId, roleId(roleCode));
        return userId;
    }

    private Long roleId(String roleCode) {
        return jdbc.queryForObject(
                "SELECT id FROM sys_role WHERE role_code=? AND tenant_id=1 AND deleted=0", Long.class, roleCode);
    }

    private Long outlet(String code, int status) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default)"
                + " VALUES(1,?,?,'STORE',?,0,0,0)", code, "档口" + code, status);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE outlet_code=?", Long.class, code);
    }

    private void bind(Long userId, Long outletId) {
        jdbc.update("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) VALUES(1,?,?,1,1,0)",
                userId, outletId);
    }

    private int bindingCount(Long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_outlet WHERE user_id=? AND status=1 AND deleted=0", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private String roleCodeOf(Long userId) {
        return jdbc.queryForObject("SELECT r.role_code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id "
                + "WHERE ur.user_id=? AND ur.deleted=0", String.class, userId);
    }
}

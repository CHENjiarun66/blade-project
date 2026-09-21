package com.blade.outlet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V68 真实唯一约束：每 tenant+user / tenant+agent_key 最多一条有效默认。
 *
 * <p>验证普通非默认可共存、第二条有效默认被唯一键拒绝、软删/禁用后可设新默认、
 * 不同 tenant/subject 不冲突。用例在事务内自造数据并回滚，不依赖生产。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutletSubjectDefaultUniquenessIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private long base() {
        return 900_000_000L + Math.abs(System.nanoTime() % 100_000_000L);
    }

    @Test
    void userTwoNonDefaultsCoexistAndSecondDefaultRejected() {
        long tenant = 1L;
        long u = base();
        long o1 = base() + 1;
        long o2 = base() + 2;

        insertUserBinding(tenant, u, o1, 0, 1, 0);
        insertUserBinding(tenant, u, o2, 0, 1, 0);
        assertEquals(2, count("SELECT COUNT(*) FROM sys_user_outlet WHERE tenant_id=? AND user_id=?", tenant, u));

        jdbc.update("UPDATE sys_user_outlet SET is_default=1 WHERE tenant_id=? AND user_id=? AND outlet_id=?", tenant, u, o1);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE sys_user_outlet SET is_default=1 WHERE tenant_id=? AND user_id=? AND outlet_id=?",
                        tenant, u, o2),
                "同用户第二条有效默认必须被 uk_user_outlet_default 拒绝");
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_outlet WHERE tenant_id=? AND user_id=? AND is_default=1 AND deleted=0 AND status=1", tenant, u));
    }

    @Test
    void userSoftDeletedDefaultAllowsNewDefault() {
        long tenant = 1L;
        long u = base() + 10;
        long o1 = base() + 11;
        long o2 = base() + 12;

        insertUserBinding(tenant, u, o1, 1, 1, 0);
        // 软删旧默认后，新默认可写入
        jdbc.update("UPDATE sys_user_outlet SET deleted=1 WHERE tenant_id=? AND user_id=? AND outlet_id=?", tenant, u, o1);
        insertUserBinding(tenant, u, o2, 1, 1, 0);

        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_outlet WHERE tenant_id=? AND user_id=? AND is_default=1 AND deleted=0 AND status=1", tenant, u));
    }

    @Test
    void userDifferentUserAndTenantDoNotConflict() {
        long u1 = base() + 20;
        long u2 = base() + 21;
        insertUserBinding(1L, u1, base() + 22, 1, 1, 0);
        insertUserBinding(1L, u2, base() + 23, 1, 1, 0);
        insertUserBinding(2L, u1, base() + 24, 1, 1, 0);

        assertEquals(2, count("SELECT COUNT(*) FROM sys_user_outlet WHERE user_id=? AND is_default=1 AND deleted=0 AND status=1", u1));
    }

    @Test
    void agentKeyTwoNonDefaultsCoexistAndSecondDefaultRejected() {
        long tenant = 1L;
        long k = base() + 30;
        long o1 = base() + 31;
        long o2 = base() + 32;

        insertKeyBinding(tenant, k, o1, 0, 1);
        insertKeyBinding(tenant, k, o2, 0, 1);
        assertEquals(2, count("SELECT COUNT(*) FROM agent_key_outlet WHERE tenant_id=? AND agent_key_id=?", tenant, k));

        jdbc.update("UPDATE agent_key_outlet SET is_default=1 WHERE tenant_id=? AND agent_key_id=? AND outlet_id=?", tenant, k, o1);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE agent_key_outlet SET is_default=1 WHERE tenant_id=? AND agent_key_id=? AND outlet_id=?",
                        tenant, k, o2),
                "同 Key 第二条有效默认必须被 uk_agent_key_outlet_default 拒绝");
        assertEquals(1, count("SELECT COUNT(*) FROM agent_key_outlet WHERE tenant_id=? AND agent_key_id=? AND is_default=1 AND status=1", tenant, k));
    }

    @Test
    void agentKeyDisabledDefaultAllowsNewDefault() {
        long tenant = 1L;
        long k = base() + 40;
        long o1 = base() + 41;
        long o2 = base() + 42;

        insertKeyBinding(tenant, k, o1, 1, 1);
        jdbc.update("UPDATE agent_key_outlet SET status=0 WHERE tenant_id=? AND agent_key_id=? AND outlet_id=?", tenant, k, o1);
        insertKeyBinding(tenant, k, o2, 1, 1);

        assertEquals(1, count("SELECT COUNT(*) FROM agent_key_outlet WHERE tenant_id=? AND agent_key_id=? AND is_default=1 AND status=1", tenant, k));
    }

    @Test
    void agentKeyDifferentKeyAndTenantDoNotConflict() {
        long k1 = base() + 50;
        long k2 = base() + 51;
        insertKeyBinding(1L, k1, base() + 52, 1, 1);
        insertKeyBinding(1L, k2, base() + 53, 1, 1);
        insertKeyBinding(2L, k1, base() + 54, 1, 1);

        assertEquals(2, count("SELECT COUNT(*) FROM agent_key_outlet WHERE agent_key_id=? AND is_default=1 AND status=1", k1));
    }

    // ==================== helpers ====================

    private void insertUserBinding(long tenantId, long userId, long outletId, int isDefault, int status, int deleted) {
        jdbc.update("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) "
                + "VALUES(?,?,?,?,?,?)", tenantId, userId, outletId, isDefault, status, deleted);
    }

    private void insertKeyBinding(long tenantId, long keyId, long outletId, int isDefault, int status) {
        jdbc.update("INSERT INTO agent_key_outlet(tenant_id,agent_key_id,outlet_id,is_default,status) "
                + "VALUES(?,?,?,?,?)", tenantId, keyId, outletId, isDefault, status);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}

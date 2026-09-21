package com.blade.outlet;

import com.blade.common.tenant.TenantContext;
import com.blade.outlet.dto.OutletUpdateDTO;
import com.blade.outlet.service.OutletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二批B：租户默认档口数据库不变量（V67）与并发安全，真实 DB 非 mock。
 *
 * <p>覆盖：唯一索引直接拒绝同租户两个默认；不同租户各自默认允许；禁用默认清除标记；
 * 服务连续/并发设置默认后每租户恰好一个；跨租户互不清除。用例清理自建数据，不依赖生产。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SalesOutletTenantDefaultIntegrationTest {

    private static final long TENANT_1 = 1L;
    private static final long TENANT_2 = 2L;
    private static final String PREFIX = "B2B-DEF-";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OutletService outletService;

    private String suffix;

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM sales_outlet WHERE outlet_code LIKE ?", PREFIX + "%");
        TenantContext.clear();
    }

    private String nextSuffix() {
        suffix = Long.toString(System.nanoTime()).substring(8);
        return suffix;
    }

    @Test
    void uniqueIndexRejectsTwoDefaultsForSameTenant() {
        String s = nextSuffix();
        long a = insertOutlet(TENANT_1, PREFIX + "A-" + s, 1, 0);
        long b = insertOutlet(TENANT_1, PREFIX + "B-" + s, 1, 0);

        jdbc.update("UPDATE sales_outlet SET is_tenant_default=1 WHERE id=?", a);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE sales_outlet SET is_tenant_default=1 WHERE id=?", b),
                "V67 唯一索引必须直接拒绝同租户第二个默认");
        assertEquals(1, countDefaults(TENANT_1));
    }

    @Test
    void differentTenantsCanEachHaveOwnDefault() {
        String s = nextSuffix();
        long a = insertOutlet(TENANT_1, PREFIX + "T1-" + s, 1, 0);
        long c = insertOutlet(TENANT_2, PREFIX + "T2-" + s, 1, 0);

        jdbc.update("UPDATE sales_outlet SET is_tenant_default=1 WHERE id=?", a);
        jdbc.update("UPDATE sales_outlet SET is_tenant_default=1 WHERE id=?", c);

        assertEquals(1, countDefaults(TENANT_1));
        assertEquals(1, countDefaults(TENANT_2));
    }

    @Test
    void serviceKeepsOneDefaultAndDoesNotClearOtherTenant() {
        String s = nextSuffix();
        long a = insertOutlet(TENANT_1, PREFIX + "S1A-" + s, 1, 0);
        long b = insertOutlet(TENANT_1, PREFIX + "S1B-" + s, 1, 0);
        long c = insertOutlet(TENANT_2, PREFIX + "S2-" + s, 1, 0);

        setDefaultViaService(TENANT_1, a, PREFIX + "S1A-" + s);
        assertEquals(a, singleDefaultId(TENANT_1));

        setDefaultViaService(TENANT_1, b, PREFIX + "S1B-" + s);
        assertEquals(b, singleDefaultId(TENANT_1), "第二个默认必须顶掉第一个");

        setDefaultViaService(TENANT_2, c, PREFIX + "S2-" + s);
        assertEquals(c, singleDefaultId(TENANT_2));
        assertEquals(b, singleDefaultId(TENANT_1), "设置其它租户默认不得清除本租户默认");
    }

    @Test
    void disablingDefaultOutletClearsFlag() {
        String s = nextSuffix();
        long a = insertOutlet(TENANT_1, PREFIX + "DIS-" + s, 1, 0);
        setDefaultViaService(TENANT_1, a, PREFIX + "DIS-" + s);
        assertEquals(a, singleDefaultId(TENANT_1));

        TenantContext.setTenantId(TENANT_1);
        outletService.updateStatus(a, 0);

        assertEquals(0, countDefaults(TENANT_1));
        assertEquals(0, jdbc.queryForObject("SELECT is_tenant_default FROM sales_outlet WHERE id=?", Integer.class, a));
    }

    @Test
    void disabledOutletCannotBeSetDefault() {
        String s = nextSuffix();
        long a = insertOutlet(TENANT_1, PREFIX + "OFF-" + s, 0, 0);
        TenantContext.setTenantId(TENANT_1);
        OutletUpdateDTO dto = new OutletUpdateDTO();
        dto.setId(a);
        dto.setOutletCode(PREFIX + "OFF-" + s);
        dto.setOutletName("禁用档口");
        dto.setIsTenantDefault(1);
        assertThrows(com.blade.common.exception.BusinessException.class, () -> outletService.update(dto));
        assertEquals(0, countDefaults(TENANT_1));
    }

    @Test
    void concurrentSetDefaultLeavesExactlyOnePerTenant() throws Exception {
        String s = nextSuffix();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(insertOutlet(TENANT_1, PREFIX + "C" + i + "-" + s, 1, 0));
        }

        int n = ids.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long id = ids.get(i);
            String code = PREFIX + "C" + i + "-" + s;
            futures.add(pool.submit(() -> {
                TenantContext.setTenantId(TENANT_1);
                ready.countDown();
                try {
                    start.await();
                    OutletUpdateDTO dto = new OutletUpdateDTO();
                    dto.setId(id);
                    dto.setOutletCode(code);
                    dto.setOutletName("并发默认" + id);
                    dto.setIsTenantDefault(1);
                    outletService.update(dto);
                } finally {
                    TenantContext.clear();
                }
                return null;
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "并发线程未就绪");
        start.countDown();

        List<Throwable> errors = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        pool.shutdownNow();

        assertTrue(errors.isEmpty(), "租户级串行锁下并发设置默认不应失败: " + errors);
        assertEquals(1, countDefaults(TENANT_1), "并发后每租户必须恰好一个默认");
    }

    // ==================== helpers ====================

    private void setDefaultViaService(long tenantId, long outletId, String code) {
        TenantContext.setTenantId(tenantId);
        OutletUpdateDTO dto = new OutletUpdateDTO();
        dto.setId(outletId);
        dto.setOutletCode(code);
        dto.setOutletName("服务默认" + outletId);
        dto.setIsTenantDefault(1);
        outletService.update(dto);
    }

    private long insertOutlet(long tenantId, String code, int status, int isDefault) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,is_tenant_default,deleted,sort) "
                + "VALUES(?,?,?,'STORE',?,?,0,0)", tenantId, code, "B2B档口" + code, status, isDefault);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=? AND outlet_code=?",
                Long.class, tenantId, code);
    }

    private int countDefaults(long tenantId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM sales_outlet "
                + "WHERE tenant_id=? AND deleted=0 AND is_tenant_default=1", Integer.class, tenantId);
        return c == null ? 0 : c;
    }

    private Long singleDefaultId(long tenantId) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM sales_outlet "
                + "WHERE tenant_id=? AND deleted=0 AND is_tenant_default=1", Long.class, tenantId);
        assertEquals(1, ids.size(), "tenant " + tenantId + " 应恰好一个默认，实际 " + ids);
        return ids.get(0);
    }
}

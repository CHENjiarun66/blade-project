package com.blade.analytics;

import com.blade.analytics.dto.AnalyticsRankingDTO;
import com.blade.analytics.dto.AnalyticsSummaryDTO;
import com.blade.analytics.enums.AnalyticsDimension;
import com.blade.analytics.enums.AnalyticsSortBy;
import com.blade.analytics.service.AnalyticsService;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.dashboard.dto.DashboardStatsDTO;
import com.blade.dashboard.enums.PeriodType;
import com.blade.dashboard.service.DashboardService;
import com.blade.order.dto.OrderExportDTO;
import com.blade.order.dto.OrderPageDTO;
import com.blade.order.service.OrderAccessPolicy;
import com.blade.order.service.OrderReadScope;
import com.blade.order.service.OrderService;
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Series E1：Dashboard / Analytics / 导出的档口 × 人员范围隔离。
 *
 * <p>真实隔离库，覆盖单档口销售员、Owner 多档口汇总与对比、越权 ID 403、
 * 未归档排除、待归档权限、导出与列表一致、租户隔离与范围指纹。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutletAnalyticsScopeTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AnalyticsService analyticsService;
    @Autowired private DashboardService dashboardService;
    @Autowired private OrderService orderService;
    @Autowired private OrderAccessPolicy orderAccessPolicy;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ==================== 上下文 / 种子 ====================

    private void auth(long userId, String... authorities) {
        TenantContext.setTenantId(1L);
        User principal = new User();
        principal.setId(userId);
        principal.setUsername("u" + userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private long seedOutlet(long tenantId, String code, int status) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default) "
                + "VALUES(?,?,?,'STORE',?,0,0,0)", tenantId, code, "档口" + code, status);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=? AND outlet_code=?", Long.class, tenantId, code);
    }

    private long seedUser(long tenantId, String name) {
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,?,1,?,0)",
                name, "x", name, tenantId);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name);
    }

    private void bindUser(long userId, long outletId) {
        jdbc.update("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) VALUES(1,?,?,0,1,0)",
                userId, outletId);
    }

    private long seedOrder(long tenantId, String no, Long outletId, Long salesmanId,
                           LocalDate orderDate, String customerName, BigDecimal total, BigDecimal grossProfit) {
        jdbc.update("""
                INSERT INTO sale_order(order_no,order_date,order_type,customer_name,total_amount,paid_amount,
                  gross_received_amount,cash_refund_amount,sales_return_amount,net_received_amount,balance_amount,
                  write_off_amount,gross_profit,fulfillment_status,fulfillment_mode,collection_status,
                  salesman_id,source_outlet_id,tenant_id,deleted,version)
                VALUES(?,?,'SPOT',?,?,0,?,0,0,0,?,0,?,'CONFIRMED','UNDECIDED','PARTIAL',?,?,?,0,0)
                """, no, orderDate, customerName, total, total, total, grossProfit, salesmanId, outletId, tenantId);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no=?", Long.class, no);
    }

    private void seedItem(long tenantId, long orderId, String productName, String skuCode,
                          int quantity, BigDecimal price, BigDecimal cost, BigDecimal profit) {
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));
        jdbc.update("""
                INSERT INTO sale_order_item(order_id,sku_id,sku_code,product_name,color_name,size_name,
                  price,cost_price,quantity,subtotal,cost_amount,gross_profit,tenant_id)
                VALUES(?,NULL,?,?, '黑色','均码',?,?,?,?,?,?,?)
                """, orderId, skuCode, productName, price, cost, quantity, subtotal,
                cost.multiply(BigDecimal.valueOf(quantity)), profit, tenantId);
    }

    private DashboardQueryDTO query(LocalDate start, LocalDate end, List<Long> outletIds, Boolean pendingArchive) {
        DashboardQueryDTO query = new DashboardQueryDTO();
        query.setPeriodType(PeriodType.CUSTOM);
        query.setStartDate(start);
        query.setEndDate(end);
        query.setSourceOutletIds(outletIds);
        query.setPendingArchive(pendingArchive);
        return query;
    }

    // ==================== 单档口销售员 ====================

    @Test
    void salespersonAnalytics_onlyOwnOutletExcludesOtherAndUnassigned() {
        long outletA = seedOutlet(1L, "E1-A", 1);
        long outletB = seedOutlet(1L, "E1-B", 1);
        long salesA = seedUser(1L, "e1salesA" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e1salesB" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);

        LocalDate today = LocalDate.now();
        long orderA = seedOrder(1L, "E1-A-" + System.nanoTime(), outletA, salesA, today, "A客户",
                new BigDecimal("100.00"), new BigDecimal("40.00"));
        seedItem(1L, orderA, "商品A", "SKU-A", 2, new BigDecimal("50.00"), new BigDecimal("30.00"), new BigDecimal("40.00"));
        long orderB = seedOrder(1L, "E1-B-" + System.nanoTime(), outletB, salesB, today, "B客户",
                new BigDecimal("200.00"), new BigDecimal("80.00"));
        seedItem(1L, orderB, "商品B", "SKU-B", 5, new BigDecimal("40.00"), new BigDecimal("20.00"), new BigDecimal("80.00"));
        long orderNull = seedOrder(1L, "E1-N-" + System.nanoTime(), null, salesA, today, "待归档客户",
                new BigDecimal("50.00"), new BigDecimal("10.00"));
        seedItem(1L, orderNull, "商品N", "SKU-N", 1, new BigDecimal("50.00"), new BigDecimal("40.00"), new BigDecimal("10.00"));

        auth(salesA, "menu:analytics");

        AnalyticsSummaryDTO summary = analyticsService.getSummary(query(today, today, null, null));
        assertEquals(1L, summary.getOrderCount(), "销售员只应看到 A 档口本人订单");
        assertEquals(0, summary.getSalesAmount().compareTo(new BigDecimal("100.00")), "不得含 B 档口或未归档");
        assertEquals(2L, summary.getSalesQuantity());

        List<AnalyticsRankingDTO> ranking = analyticsService.getProductRanking(
                query(today, today, null, null), AnalyticsDimension.PRODUCT, AnalyticsSortBy.SALES, 10);
        assertEquals(1, ranking.size());
        assertEquals("商品A", ranking.get(0).getLabel());

        assertEquals(0L, analyticsService.getProductDetail(query(today, today, null, null), "商品B")
                .getTotalSalesQuantity(), "销售员看不到 B 档口商品明细");

        DashboardStatsDTO stats = dashboardService.getStats(query(today, today, null, null));
        assertEquals(1L, stats.getPeriodOrders());
        assertEquals(0, stats.getPeriodSales().compareTo(new BigDecimal("100.00")));
        assertEquals(1L, stats.getPendingOrders(), "待处理计数不得含 B 或未归档");
    }

    @Test
    void salespersonDashboard_trendWeekAndPreviousExcludeOtherOutlet() {
        long outletA = seedOutlet(1L, "E1-W-A", 1);
        long outletB = seedOutlet(1L, "E1-W-B", 1);
        long salesA = seedUser(1L, "e1wsalesA" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e1wsalesB" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);

        LocalDate today = LocalDate.now();
        long orderA = seedOrder(1L, "E1-W-A-" + System.nanoTime(), outletA, salesA, today, "WA",
                new BigDecimal("100.00"), new BigDecimal("40.00"));
        seedItem(1L, orderA, "商品WA", "SKU-WA", 1, new BigDecimal("100.00"), new BigDecimal("60.00"), new BigDecimal("40.00"));
        long orderB = seedOrder(1L, "E1-W-B-" + System.nanoTime(), outletB, salesB, today, "WB",
                new BigDecimal("900.00"), new BigDecimal("400.00"));
        seedItem(1L, orderB, "商品WB", "SKU-WB", 9, new BigDecimal("100.00"), new BigDecimal("55.00"), new BigDecimal("400.00"));

        auth(salesA, "menu:analytics");
        var trend = analyticsService.getTrend(query(today, today, null, null));
        assertEquals(1L, trend.getOrderCounts().get(trend.getOrderCounts().size() - 1));
        assertEquals(0, trend.getSalesAmounts().get(trend.getSalesAmounts().size() - 1).compareTo(new BigDecimal("100.00")));

        DashboardStatsDTO stats = dashboardService.getStats(query(today, today, null, null));
        assertEquals(1L, stats.getWeekOrders(), "本周订单只含 A");
        assertEquals(0, stats.getWeekSales().compareTo(new BigDecimal("100.00")), "本周销售额不得含 B");
        assertEquals(1L, stats.getPeriodOrders());
        assertEquals(0, stats.getPeriodSales().compareTo(new BigDecimal("100.00")));
    }

    // ==================== Owner 汇总与对比 ====================

    @Test
    void ownerMultiOutletAggregates_andCompareBySelection() {
        long outletA = seedOutlet(1L, "E1-O-A", 1);
        long outletB = seedOutlet(1L, "E1-O-B", 1);
        long salesA = seedUser(1L, "e1osalesA" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e1osalesB" + System.nanoTime() % 100000);
        long owner = seedUser(1L, "e1owner" + System.nanoTime() % 100000);

        LocalDate today = LocalDate.now();
        long orderA = seedOrder(1L, "E1-O-A-" + System.nanoTime(), outletA, salesA, today, "OA",
                new BigDecimal("100.00"), new BigDecimal("40.00"));
        seedItem(1L, orderA, "商品OA", "SKU-OA", 1, new BigDecimal("100.00"), new BigDecimal("60.00"), new BigDecimal("40.00"));
        long orderB = seedOrder(1L, "E1-O-B-" + System.nanoTime(), outletB, salesB, today, "OB",
                new BigDecimal("200.00"), new BigDecimal("80.00"));
        seedItem(1L, orderB, "商品OB", "SKU-OB", 1, new BigDecimal("200.00"), new BigDecimal("120.00"), new BigDecimal("80.00"));

        auth(owner, "data:outlet:all", "data:order:peopleAll", "ROLE_OWNER");

        assertEquals(2L, analyticsService.getSummary(query(today, today, List.of(outletA, outletB), null)).getOrderCount());
        assertEquals(1L, analyticsService.getSummary(query(today, today, List.of(outletA), null)).getOrderCount());
        assertEquals(0, analyticsService.getSummary(query(today, today, List.of(outletA), null))
                .getSalesAmount().compareTo(new BigDecimal("100.00")));
        assertEquals(1L, analyticsService.getSummary(query(today, today, List.of(outletB), null)).getOrderCount());
        assertEquals(0, analyticsService.getSummary(query(today, today, List.of(outletB), null))
                .getSalesAmount().compareTo(new BigDecimal("200.00")));
    }

    // ==================== 越权 ====================

    @Test
    void salespersonExplicitForeignOutletReturns403() {
        long outletA = seedOutlet(1L, "E1-X-A", 1);
        long outletB = seedOutlet(1L, "E1-X-B", 1);
        long salesA = seedUser(1L, "e1xsalesA" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        LocalDate today = LocalDate.now();

        auth(salesA, "menu:analytics");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> analyticsService.getSummary(query(today, today, List.of(outletB), null))).getCode(),
                "销售员显式请求 B 必须 403，而不是空结果或忽略参数");
    }

    @Test
    void multiSelectWithUnauthorizedIdReturns403() {
        long outletA = seedOutlet(1L, "E1-M-A", 1);
        long outletB = seedOutlet(1L, "E1-M-B", 1);
        long salesA = seedUser(1L, "e1msalesA" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        LocalDate today = LocalDate.now();

        auth(salesA, "menu:analytics");
        DashboardQueryDTO mixed = query(today, today, List.of(outletA, outletB), null);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> dashboardService.getStats(mixed)).getCode(), "多选混入未授权 ID 必须整体 403");
    }

    @Test
    void pendingArchiveRequiresUnassignedAndIsSeparateFromSales() {
        long outletA = seedOutlet(1L, "E1-P-A", 1);
        long salesA = seedUser(1L, "e1psalesA" + System.nanoTime() % 100000);
        long owner = seedUser(1L, "e1powner" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        LocalDate today = LocalDate.now();

        auth(salesA, "menu:analytics");
        assertEquals(403, assertThrows(BusinessException.class,
                () -> dashboardService.getStats(query(today, today, null, true))).getCode(),
                "无 data:outlet:unassigned 不得看待归档");

        // 先取不含待归档订单的基线，再插入一条未归档已收款订单
        auth(owner, "data:outlet:all", "data:order:peopleAll", "data:outlet:unassigned", "ROLE_OWNER");
        DashboardStatsDTO baseline = dashboardService.getStats(query(today, today, null, null));
        long orderNull = seedOrder(1L, "E1-P-N-" + System.nanoTime(), null, salesA, today, "待归档",
                new BigDecimal("50.00"), new BigDecimal("10.00"));
        seedItem(1L, orderNull, "商品P", "SKU-P", 1, new BigDecimal("50.00"), new BigDecimal("40.00"), new BigDecimal("10.00"));

        DashboardStatsDTO stats = dashboardService.getStats(query(today, today, null, true));
        assertTrue(stats.getPendingArchiveCount() >= 1L, "授权者可见待归档数量");
        assertNotNull(stats.getPendingArchiveCount());
        assertEquals(baseline.getPeriodOrders(), stats.getPeriodOrders(), "待归档不得混入销售订单数");
        assertEquals(0, baseline.getPeriodSales().compareTo(stats.getPeriodSales()), "待归档不得混入销售额");
    }

    // ==================== 导出与列表一致 ====================

    @Test
    void exportMatchesOrderListVisibility_andBlocksForeignOutlet() {
        long outletA = seedOutlet(1L, "E1-E-A", 1);
        long outletB = seedOutlet(1L, "E1-E-B", 1);
        long salesA = seedUser(1L, "e1esalesA" + System.nanoTime() % 100000);
        long salesB = seedUser(1L, "e1esalesB" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);
        LocalDate today = LocalDate.now();
        long orderA = seedOrder(1L, "E1-E-A-" + System.nanoTime(), outletA, salesA, today, "EA",
                new BigDecimal("100.00"), new BigDecimal("40.00"));
        long orderB = seedOrder(1L, "E1-E-B-" + System.nanoTime(), outletB, salesB, today, "EB",
                new BigDecimal("200.00"), new BigDecimal("80.00"));
        String noA = jdbc.queryForObject("SELECT order_no FROM sale_order WHERE id=?", String.class, orderA);
        String noB = jdbc.queryForObject("SELECT order_no FROM sale_order WHERE id=?", String.class, orderB);

        auth(salesA, "menu:order", "btn:order:export");
        OrderPageDTO dto = new OrderPageDTO();
        dto.setCurrent(1L);
        dto.setSize(100L);
        Set<String> listNos = orderService.pageList(dto).getRecords().stream()
                .map(vo -> vo.getOrderNo()).collect(Collectors.toSet());
        Set<String> exportNos = orderService.exportOrders(dto).stream()
                .map(OrderExportDTO::getOrderNo).collect(Collectors.toSet());
        assertEquals(listNos, exportNos, "导出可见集合必须与列表一致");
        assertEquals(Set.of(noA), exportNos, "销售员不得导出 B");
        assertFalse(exportNos.contains(noB));

        OrderPageDTO foreign = new OrderPageDTO();
        foreign.setCurrent(1L);
        foreign.setSize(100L);
        foreign.setSourceOutletId(outletB);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> orderService.exportOrders(foreign)).getCode(), "导出越权档口必须 403");

        OrderPageDTO pending = new OrderPageDTO();
        pending.setCurrent(1L);
        pending.setSize(100L);
        pending.setUnassignedOnly(true);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> orderService.exportOrders(pending)).getCode(), "无 unassigned 不得导出待归档");
    }

    // ==================== 租户隔离 / 指纹 ====================

    @Test
    void analyticsExcludesOtherTenantOrders() {
        long outletA = seedOutlet(1L, "E1-T-A", 1);
        long salesA = seedUser(1L, "e1tsalesA" + System.nanoTime() % 100000);
        long owner = seedUser(1L, "e1towner" + System.nanoTime() % 100000);
        LocalDate today = LocalDate.now();
        seedOrder(1L, "E1-T-A-" + System.nanoTime(), outletA, salesA, today, "TA",
                new BigDecimal("100.00"), new BigDecimal("40.00"));

        long outletOther = seedOutlet(2L, "E1-T-OTHER", 1);
        long userOther = seedUser(2L, "e1tother" + System.nanoTime() % 100000);
        seedOrder(2L, "E1-T-OTHER-" + System.nanoTime(), outletOther, userOther, today, "OTHER",
                new BigDecimal("9999.00"), new BigDecimal("999.00"));

        auth(owner, "data:outlet:all", "data:order:peopleAll", "ROLE_OWNER");
        // 用显式档口隔离本用例数据，其它历史租户 1 订单不计入
        AnalyticsSummaryDTO summary = analyticsService.getSummary(query(today, today, List.of(outletA), null));
        assertEquals(1L, summary.getOrderCount(), "不得含其它租户订单");
        assertEquals(0, summary.getSalesAmount().compareTo(new BigDecimal("100.00")));
    }

    @Test
    void scopeFingerprintChangesWithActorAndSelection() {
        long outletA = seedOutlet(1L, "E1-F-A", 1);
        long outletB = seedOutlet(1L, "E1-F-B", 1);
        long salesA = seedUser(1L, "e1fsalesA" + System.nanoTime() % 100000);
        long owner = seedUser(1L, "e1fowner" + System.nanoTime() % 100000);
        bindUser(salesA, outletA);

        auth(salesA, "menu:analytics");
        OrderReadScope salesScope = orderAccessPolicy.resolveReadScope(List.of(outletA), null);
        String salesFingerprint = salesScope.cacheFingerprint();
        assertEquals(salesFingerprint, orderAccessPolicy.resolveReadScope(List.of(outletA), null).cacheFingerprint(),
                "同 actor + 同选择指纹稳定");

        auth(owner, "data:outlet:all", "data:order:peopleAll", "data:outlet:unassigned", "ROLE_OWNER");
        String ownerFingerprint = orderAccessPolicy.resolveReadScope(null, null).cacheFingerprint();
        assertNotEquals(salesFingerprint, ownerFingerprint, "不同 actor 指纹必须不同（禁止跨用户复用缓存）");
        assertNotEquals(ownerFingerprint, orderAccessPolicy.resolveReadScope(List.of(outletA), null).cacheFingerprint(),
                "显式选择必须改变指纹");
        assertNotEquals(ownerFingerprint, orderAccessPolicy.resolveReadScope(null, true).cacheFingerprint(),
                "pendingArchive 必须改变指纹");
    }
}

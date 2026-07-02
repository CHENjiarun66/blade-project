package com.blade.analytics;

import com.blade.analytics.dto.AnalyticsRankingDTO;
import com.blade.analytics.dto.AnalyticsSummaryDTO;
import com.blade.analytics.enums.AnalyticsDimension;
import com.blade.analytics.enums.AnalyticsSortBy;
import com.blade.analytics.service.impl.AnalyticsServiceImpl;
import com.blade.common.tenant.TenantContext;
import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.dashboard.enums.PeriodType;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnalyticsServiceTest {

    private FakeMapperHandler orderHandler;
    private FakeMapperHandler itemHandler;
    private AnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        orderHandler = new FakeMapperHandler();
        itemHandler = new FakeMapperHandler();
        OrderMapper orderMapper = fakeMapper(OrderMapper.class, orderHandler);
        OrderItemMapper orderItemMapper = fakeMapper(OrderItemMapper.class, itemHandler);
        service = new AnalyticsServiceImpl(orderMapper, orderItemMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void getSummary_hidesProfitWithoutPermission() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "sales", "n/a", List.of(new SimpleGrantedAuthority("menu:analytics"))));
        orderHandler.thenSelectList(List.of(
                order(1L, "100.00", "0", "30.00", "40.00"),
                order(2L, "200.00", "50.00", "200.00", "90.00")
        ));
        itemHandler.thenSelectList(List.of(item(1L, "624-1#", "A", "黑", "L", 2, "100.00", "60.00", "40.00")));

        AnalyticsSummaryDTO summary = service.getSummary(weekQuery());

        assertEquals(2L, summary.getOrderCount());
        assertEquals(new BigDecimal("250.00"), summary.getSalesAmount());
        assertEquals(2L, summary.getSalesQuantity());
        assertEquals(new BigDecimal("50.00"), summary.getRefundAmount());
        assertNull(summary.getGrossProfit());
        assertNull(summary.getGrossProfitRate());
        assertEquals(false, summary.getProfitVisible());
    }

    @Test
    void getSummary_subtractsWriteOffFromSalesAndGrossProfit() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("data:analytics:profit"))));
        Order order = order(1L, "100.00", "10.00", "50.00", "50.00");
        order.setWriteOffAmount(new BigDecimal("15.00"));
        orderHandler.thenSelectList(List.of(order));
        itemHandler.thenSelectList(List.of(item(1L, "624-1#", "A", "黑", "L", 2,
                "100.00", "50.00", "50.00")));

        AnalyticsSummaryDTO summary = service.getSummary(weekQuery());

        assertEquals(new BigDecimal("75.00"), summary.getSalesAmount());
        assertEquals(new BigDecimal("25.00"), summary.getGrossProfit());
        assertEquals(true, summary.getProfitVisible());
    }

    @Test
    void getProductRanking_includesProfitWithPermission() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("data:analytics:profit"))));
        orderHandler.thenSelectList(List.of(order(1L, "300.00", "0", "300.00", "120.00")));
        itemHandler.thenSelectList(List.of(
                item(1L, "624-1#", "A", "黑", "L", 2, "200.00", "120.00", "80.00"),
                item(1L, "624-2#", "B", "白", "M", 1, "100.00", "60.00", "40.00")
        ));

        List<AnalyticsRankingDTO> ranking = service.getProductRanking(
                weekQuery(), AnalyticsDimension.PRODUCT, AnalyticsSortBy.GROSS_PROFIT, 20);

        assertEquals(2, ranking.size());
        assertEquals("624-1#", ranking.get(0).getProductName());
        assertEquals(2L, ranking.get(0).getSalesQuantity());
        assertEquals(new BigDecimal("80.00"), ranking.get(0).getGrossProfit());
        assertEquals(new BigDecimal("40.00"), ranking.get(0).getGrossProfitRate());
    }

    private DashboardQueryDTO weekQuery() {
        DashboardQueryDTO query = new DashboardQueryDTO();
        query.setPeriodType(PeriodType.WEEK);
        return query;
    }

    private Order order(Long id, String totalAmount, String refundAmount, String paidAmount, String grossProfit) {
        Order order = new Order();
        order.setId(id);
        order.setTotalAmount(new BigDecimal(totalAmount));
        order.setRefundAmount(new BigDecimal(refundAmount));
        order.setPaidAmount(new BigDecimal(paidAmount));
        order.setGrossProfit(new BigDecimal(grossProfit));
        order.setPaymentStatus(1);
        order.setDeleted(0);
        return order;
    }

    private OrderItem item(Long orderId, String productName, String skuCode, String colorName, String sizeName,
                           Integer quantity, String subtotal, String costAmount, String grossProfit) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductName(productName);
        item.setSkuCode(skuCode);
        item.setColorName(colorName);
        item.setSizeName(sizeName);
        item.setQuantity(quantity);
        item.setSubtotal(new BigDecimal(subtotal));
        item.setCostAmount(new BigDecimal(costAmount));
        item.setGrossProfit(new BigDecimal(grossProfit));
        item.setTenantId(1L);
        return item;
    }

    @SuppressWarnings("unchecked")
    private <T> T fakeMapper(Class<T> mapperType, FakeMapperHandler handler) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[] { mapperType },
                handler
        );
    }

    private static class FakeMapperHandler implements java.lang.reflect.InvocationHandler {
        private final Queue<Object> selectListResults = new ArrayDeque<>();

        @SafeVarargs
        final void thenSelectList(List<?>... results) {
            selectListResults.addAll(List.of(results));
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return switch (method.getName()) {
                case "selectList" -> selectListResults.isEmpty() ? List.of() : selectListResults.remove();
                case "toString" -> "FakeMapper";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == void.class) {
                return null;
            }
            return 0;
        }
    }
}

package com.blade.dashboard;

import com.blade.common.tenant.TenantContext;
import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.dashboard.dto.DashboardStatsDTO;
import com.blade.dashboard.dto.OrderStatusDTO;
import com.blade.dashboard.dto.TopProductDTO;
import com.blade.dashboard.enums.PeriodType;
import com.blade.dashboard.service.impl.DashboardServiceImpl;
import com.blade.inventory.mapper.InventoryMapper;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServiceTest {

    private static final Long TEST_TENANT_ID = 1L;

    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;
    private ProductMapper productMapper;
    private ProductSkuMapper productSkuMapper;
    private InventoryMapper inventoryMapper;
    private WarehouseMapper warehouseMapper;

    private DashboardServiceImpl dashboardService;
    private FakeMapperHandler orderHandler;
    private FakeMapperHandler orderItemHandler;
    private FakeMapperHandler productHandler;
    private FakeMapperHandler inventoryHandler;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TEST_TENANT_ID);
        orderHandler = new FakeMapperHandler();
        orderItemHandler = new FakeMapperHandler();
        productHandler = new FakeMapperHandler();
        inventoryHandler = new FakeMapperHandler();
        orderMapper = fakeMapper(OrderMapper.class, orderHandler);
        orderItemMapper = fakeMapper(OrderItemMapper.class, orderItemHandler);
        productMapper = fakeMapper(ProductMapper.class, productHandler);
        productSkuMapper = fakeMapper(ProductSkuMapper.class, new FakeMapperHandler());
        inventoryMapper = fakeMapper(InventoryMapper.class, inventoryHandler);
        warehouseMapper = fakeMapper(WarehouseMapper.class, new FakeMapperHandler());
        dashboardService = new DashboardServiceImpl(
                orderMapper,
                orderItemMapper,
                productMapper,
                productSkuMapper,
                inventoryMapper,
                warehouseMapper
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getStats_usesPaidOrdersAndNetSales() {
        Order depositOrder = order(1L, "100.00", "0", "30.00", "40.00", 1, 0);
        Order refundedOrder = order(2L, "200.00", "50.00", "200.00", "90.00", 2, 5);
        Order previousOrder = order(3L, "80.00", "0", "80.00", "30.00", 2, 5);
        Order weekOrder = order(4L, "300.00", "100.00", "100.00", "120.00", 1, 6);

        orderHandler.thenSelectList(List.of(depositOrder, refundedOrder), List.of(previousOrder), List.of(weekOrder), List.of(previousOrder));
        orderItemHandler.thenSelectList(List.of(item(3), item(2)), List.of(item(1)));
        productHandler.thenSelectCount(10L);
        orderHandler.thenSelectCount(3L, 3L);
        inventoryHandler.thenSelectCount(2L);

        DashboardStatsDTO stats = dashboardService.getStats(defaultWeekQuery());

        assertEquals(2L, stats.getPeriodOrders());
        assertEquals(new BigDecimal("250.00"), stats.getPeriodSales());
        assertEquals(new BigDecimal("80.00"), stats.getPeriodGrossProfit());
        assertEquals(5L, stats.getPeriodSalesQuantity());
        assertEquals(400L, stats.getPeriodSalesQuantityTrend());
        assertEquals(1L, stats.getWeekOrders());
        assertEquals(new BigDecimal("200.00"), stats.getWeekSales());
        assertEquals(new BigDecimal("20.00"), stats.getWeekGrossProfit());
        assertEquals(new BigDecimal("125.00"), stats.getAvgOrderValue());
    }

    @Test
    void getTopProducts_includesPaidStatusZeroDepositOrders() {
        orderHandler.thenSelectList(List.of(order(1L, "100.00", "0", "20.00", "40.00", 1, 0)));

        OrderItem item = new OrderItem();
        item.setProductName("624-1#");
        item.setQuantity(2);
        item.setSubtotal(new BigDecimal("100.00"));
        orderItemHandler.thenSelectList(List.of(item));

        List<TopProductDTO> topProducts = dashboardService.getTopProducts(defaultWeekQuery());

        assertEquals(1, topProducts.size());
        assertEquals("624-1#", topProducts.get(0).getProductName());
        assertEquals(2L, topProducts.get(0).getTotalQuantity());
        assertEquals(new BigDecimal("100.00"), topProducts.get(0).getTotalAmount());
    }

    @Test
    void getOrderStatusDistribution_returnsAllStatuses() {
        orderHandler.thenSelectCount(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);

        List<OrderStatusDTO> statuses = dashboardService.getOrderStatusDistribution(defaultWeekQuery());

        assertEquals(9, statuses.size());
        assertEquals(0, statuses.get(0).getStatus());
        assertEquals("待付款", statuses.get(0).getLabel());
        assertEquals(8, statuses.get(8).getStatus());
        assertEquals("已退货", statuses.get(8).getLabel());
        assertTrue(statuses.stream().allMatch(s -> s.getCount() > 0));
    }

    private DashboardQueryDTO defaultWeekQuery() {
        DashboardQueryDTO query = new DashboardQueryDTO();
        query.setPeriodType(PeriodType.WEEK);
        return query;
    }

    private Order order(Long id, String totalAmount, String refundAmount, String paidAmount, String grossProfit, Integer paymentStatus, Integer status) {
        Order order = new Order();
        order.setId(id);
        order.setTotalAmount(new BigDecimal(totalAmount));
        order.setRefundAmount(new BigDecimal(refundAmount));
        order.setPaidAmount(new BigDecimal(paidAmount));
        order.setGrossProfit(new BigDecimal(grossProfit));
        order.setPaymentStatus(paymentStatus);
        order.setStatus(status);
        return order;
    }

    private OrderItem item(Integer quantity) {
        OrderItem item = new OrderItem();
        item.setQuantity(quantity);
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
        private final Queue<Long> selectCountResults = new ArrayDeque<>();

        @SafeVarargs
        final void thenSelectList(List<?>... results) {
            selectListResults.addAll(List.of(results));
        }

        void thenSelectCount(Long... results) {
            selectCountResults.addAll(List.of(results));
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return switch (method.getName()) {
                case "selectList" -> selectListResults.isEmpty() ? List.of() : selectListResults.remove();
                case "selectCount" -> selectCountResults.isEmpty() ? 0L : selectCountResults.remove();
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

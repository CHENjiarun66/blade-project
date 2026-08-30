package com.blade.order;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderAdjustmentLog;
import com.blade.order.entity.OrderItem;
import com.blade.order.enums.FulfillmentMode;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderPlaceholderSplitService;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductSkuMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 占位 SKU 拆分守恒与审计测试（BE-610~612）。
 * Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPlaceholderSplitTest {

    private static final Long TENANT_ID = 1L;

    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private OrderAdjustmentLogMapper adjustmentLogMapper;
    @Mock private ProductSkuMapper productSkuMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ProductColorMapper productColorMapper;
    @Mock private ProductSizeMapper productSizeMapper;

    private OrderPlaceholderSplitService splitService;
    private List<OrderAdjustmentLog> auditLogs;

    @BeforeAll
    static void initMyBatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, GlobalConfigUtils.defaults());
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
        TableInfoHelper.initTableInfo(assistant, ProductSku.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        com.blade.system.user.entity.User principal = new com.blade.system.user.entity.User();
        principal.setId(9L);
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.setContext(securityContext);

        auditLogs = new ArrayList<>();
        lenient().when(adjustmentLogMapper.insert(any(OrderAdjustmentLog.class)))
                .thenAnswer(inv -> { auditLogs.add(inv.getArgument(0)); return 1; });

        splitService = new OrderPlaceholderSplitService(orderMapper, orderItemMapper,
                adjustmentLogMapper, productSkuMapper, productMapper, productColorMapper, productSizeMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Order splittableOrder(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setTenantId(TENANT_ID);
        order.setStatus(1);
        order.setFulfillmentStatus(FulfillmentStatus.WAITING_ALLOCATION.name());
        order.setFulfillmentMode(FulfillmentMode.STOCK_LINKED.name());
        when(orderMapper.selectByIdForUpdate(id, TENANT_ID)).thenReturn(order);
        return order;
    }

    private OrderItem placeholderItem(Long id, Long orderId, int qty, String price, String cost) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(orderId);
        item.setSkuId(900L);
        item.setQuantity(qty);
        item.setPrice(new BigDecimal(price));
        item.setCostPrice(new BigDecimal(cost));
        item.setSubtotal(new BigDecimal(price).multiply(BigDecimal.valueOf(qty)));
        item.setCostAmount(new BigDecimal(cost).multiply(BigDecimal.valueOf(qty)));
        item.setGrossProfit(item.getSubtotal().subtract(item.getCostAmount()));
        item.setTenantId(TENANT_ID);
        return item;
    }

    private ProductSku sku(Long id, String type) {
        ProductSku sku = new ProductSku();
        sku.setId(id);
        sku.setSkuType(type);
        sku.setSkuCode("SKU-" + id);
        sku.setProductId(10L);
        return sku;
    }

    @Test
    void split_conservesQuantitySubtotalAndCost_withAudit() {
        splittableOrder(1L);
        OrderItem placeholder = placeholderItem(100L, 1L, 10, "40.00", "20.00");
        when(orderItemMapper.selectOne(any())).thenReturn(placeholder);
        when(productSkuMapper.selectById(900L)).thenReturn(sku(900L, "PLACEHOLDER"));
        when(productSkuMapper.selectBatchIds(any())).thenReturn(List.of(sku(11L, "NORMAL"), sku(12L, "NORMAL")));

        List<OrderPlaceholderSplitService.SplitTarget> targets = new ArrayList<>();
        targets.add(target(11L, 6));
        targets.add(target(12L, 4));

        List<OrderItem> created = splitService.splitPlaceholderItem(1L, 100L, targets, "按客户要求分色");

        assertEquals(2, created.size());
        // 数量守恒
        assertEquals(10, created.stream().mapToInt(OrderItem::getQuantity).sum());
        // 销售额守恒
        assertEquals(0, created.stream().map(i -> i.getSubtotal()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(new BigDecimal("400.00")));
        // 成本快照守恒
        assertEquals(0, created.stream().map(i -> i.getCostAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(new BigDecimal("200.00")));
        // 审计：两条拆分来源记录（多目标时带行序号后缀）
        assertEquals(2, auditLogs.size());
        assertTrue(auditLogs.get(0).getAdjustmentType().startsWith(OrderPlaceholderSplitService.SPLIT_ADJUSTMENT_TYPE));
        assertEquals(900L, auditLogs.get(0).getOriginalSkuId());
        assertEquals(11L, auditLogs.get(0).getNewSkuId());
        verify(orderItemMapper).deleteById(100L);
    }

    @Test
    void split_rejectsQuantityMismatch() {
        splittableOrder(2L);
        when(orderItemMapper.selectOne(any())).thenReturn(placeholderItem(200L, 2L, 10, "40.00", "20.00"));
        when(productSkuMapper.selectById(900L)).thenReturn(sku(900L, "PLACEHOLDER"));
        when(productSkuMapper.selectBatchIds(any())).thenReturn(List.of(sku(11L, "NORMAL")));

        List<OrderPlaceholderSplitService.SplitTarget> targets = new ArrayList<>();
        targets.add(target(11L, 7));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> splitService.splitPlaceholderItem(2L, 200L, targets, null));
        assertTrue(ex.getMessage().contains("10"));
    }

    @Test
    void split_rejectsPlaceholderTarget() {
        splittableOrder(3L);
        when(orderItemMapper.selectOne(any())).thenReturn(placeholderItem(300L, 3L, 5, "40.00", "20.00"));
        when(productSkuMapper.selectById(900L)).thenReturn(sku(900L, "PLACEHOLDER"));
        when(productSkuMapper.selectBatchIds(any())).thenReturn(List.of(sku(999L, "PLACEHOLDER")));

        List<OrderPlaceholderSplitService.SplitTarget> targets = new ArrayList<>();
        targets.add(target(999L, 5));

        assertThrows(BusinessException.class,
                () -> splitService.splitPlaceholderItem(3L, 300L, targets, null));
    }

    @Test
    void split_rejectsShippedOrder() {
        Order order = splittableOrder(4L);
        order.setFulfillmentStatus(FulfillmentStatus.SHIPPED.name());
        order.setStatus(4);

        List<OrderPlaceholderSplitService.SplitTarget> targets = new ArrayList<>();
        targets.add(target(11L, 1));

        assertThrows(BusinessException.class,
                () -> splitService.splitPlaceholderItem(4L, 400L, targets, null));
    }

    @Test
    void split_rejectsCrossSpuTarget() {
        splittableOrder(5L);
        OrderItem placeholder = placeholderItem(500L, 5L, 10, "40.00", "20.00");
        when(orderItemMapper.selectOne(any())).thenReturn(placeholder);
        ProductSku placeholderSku = sku(900L, "PLACEHOLDER");
        placeholderSku.setProductId(77L);
        when(productSkuMapper.selectById(900L)).thenReturn(placeholderSku);
        ProductSku otherSpu = sku(11L, "NORMAL");
        otherSpu.setProductId(88L); // 不同款
        when(productSkuMapper.selectBatchIds(any())).thenReturn(List.of(otherSpu));

        List<OrderPlaceholderSplitService.SplitTarget> targets = new ArrayList<>();
        targets.add(target(11L, 10));

        com.blade.common.exception.BusinessException ex = assertThrows(com.blade.common.exception.BusinessException.class,
                () -> splitService.splitPlaceholderItem(5L, 500L, targets, null));
        assertTrue(ex.getMessage().contains("同一款商品"));
        verify(orderItemMapper, org.mockito.Mockito.never()).deleteById(anyLong());
    }

    @Test
    void split_rejectsNegativeAndZeroQuantity() {
        splittableOrder(6L);
        OrderItem placeholder = placeholderItem(600L, 6L, 10, "40.00", "20.00");
        when(orderItemMapper.selectOne(any())).thenReturn(placeholder);
        when(productSkuMapper.selectById(900L)).thenReturn(sku(900L, "PLACEHOLDER"));
        when(productSkuMapper.selectBatchIds(any())).thenReturn(List.of(sku(11L, "NORMAL")));

        List<OrderPlaceholderSplitService.SplitTarget> targets = new ArrayList<>();
        targets.add(target(11L, 8));
        targets.add(target(12L, 2));
        targets.get(1).setQuantity(-2); // 负数绕过守恒的反例：8 + (-2) = 6 ≠ 10，但同符号组合仍须拒绝

        assertThrows(com.blade.common.exception.BusinessException.class,
                () -> splitService.splitPlaceholderItem(6L, 600L, targets, null));

        // 单个负数量 + 合计凑巧相等（20 + (-10) = 10）也必须拒绝
        List<OrderPlaceholderSplitService.SplitTarget> tricky = new ArrayList<>();
        tricky.add(target(11L, 20));
        tricky.add(target(12L, -10));
        targets.clear();
        targets.addAll(tricky);
        assertThrows(com.blade.common.exception.BusinessException.class,
                () -> splitService.splitPlaceholderItem(6L, 600L, targets, null));
        verify(orderItemMapper, org.mockito.Mockito.never()).deleteById(600L);
    }

    @Test
    void split_rejectsDuplicateTargetSku() {
        splittableOrder(7L);
        when(orderItemMapper.selectOne(any())).thenReturn(placeholderItem(700L, 7L, 10, "40.00", "20.00"));
        when(productSkuMapper.selectById(900L)).thenReturn(sku(900L, "PLACEHOLDER"));
        when(productSkuMapper.selectBatchIds(any())).thenReturn(List.of(sku(11L, "NORMAL")));

        List<OrderPlaceholderSplitService.SplitTarget> targets = new ArrayList<>();
        targets.add(target(11L, 5));
        targets.add(target(11L, 5));

        assertThrows(com.blade.common.exception.BusinessException.class,
                () -> splitService.splitPlaceholderItem(7L, 700L, targets, null));
        verify(orderItemMapper, org.mockito.Mockito.never()).deleteById(700L);
    }

    private OrderPlaceholderSplitService.SplitTarget target(Long skuId, int qty) {
        OrderPlaceholderSplitService.SplitTarget t = new OrderPlaceholderSplitService.SplitTarget();
        t.setSkuId(skuId);
        t.setQuantity(qty);
        return t;
    }
}

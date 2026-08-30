package com.blade.order;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.inventory.entity.Warehouse;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.order.dto.OrderDeliveryDTO;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderDelivery;
import com.blade.order.entity.OrderDeliveryItem;
import com.blade.order.entity.OrderItem;
import com.blade.order.enums.FulfillmentMode;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.mapper.OrderDeliveryItemMapper;
import com.blade.order.mapper.OrderDeliveryMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderDeliveryService;
import com.blade.order.service.impl.OrderDeliveryServiceImpl;
import com.blade.order.service.OrderService;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.system.user.entity.User;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 终审 P1-1 反例：出库单创建的数据范围与明细完整性校验。
 * Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderDeliveryIntegrityTest {

    private static final Long TENANT_ID = 1L;

    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private OrderDeliveryMapper deliveryMapper;
    @Mock private OrderDeliveryItemMapper deliveryItemMapper;
    @Mock private WarehouseMapper warehouseMapper;
    @Mock private OrderService orderService;
    @Mock private RedissonClient redissonClient;

    private OrderDeliveryService deliveryService;

    @BeforeAll
    static void initMyBatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, GlobalConfigUtils.defaults());
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
        TableInfoHelper.initTableInfo(assistant, OrderDelivery.class);
        TableInfoHelper.initTableInfo(assistant, OrderDeliveryItem.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        User principal = new User();
        principal.setId(1L);
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.setContext(securityContext);

        deliveryService = new OrderDeliveryServiceImpl(deliveryMapper, deliveryItemMapper,
                orderMapper, orderItemMapper, warehouseMapper,
                mock(ProductSkuMapper.class), mock(ProductColorMapper.class),
                mock(ProductSizeMapper.class), mock(ProductMapper.class),
                orderService, redissonClient);

        // 仓库与插入桩：各用例的校验都发生在仓库检查之后
        Warehouse w = new Warehouse();
        w.setId(1L);
        w.setWarehouseName("主仓");
        lenient().when(warehouseMapper.selectById(1L)).thenReturn(w);
        lenient().when(deliveryMapper.insert(any(OrderDelivery.class))).thenAnswer(inv -> {
            inv.getArgument(0, OrderDelivery.class).setId(99L);
            return 1;
        });
        lenient().when(deliveryItemMapper.insert(any(OrderDeliveryItem.class))).thenReturn(1);
        // 出库单号计数器（生成发生在明细校验之前）
        org.redisson.api.RAtomicLong counter = mock(org.redisson.api.RAtomicLong.class);
        lenient().when(counter.get()).thenReturn(0L);
        lenient().when(counter.incrementAndGet()).thenReturn(1L);
        lenient().when(counter.expire(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(java.util.concurrent.TimeUnit.class))).thenReturn(true);
        lenient().when(redissonClient.getAtomicLong(org.mockito.ArgumentMatchers.anyString())).thenReturn(counter);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Order stockLinkedOrder(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setTenantId(TENANT_ID);
        order.setStatus(3);
        order.setFulfillmentStatus(FulfillmentStatus.READY_TO_SHIP.name());
        order.setFulfillmentMode(FulfillmentMode.STOCK_LINKED.name());
        when(orderMapper.selectByIdForUpdate(id, TENANT_ID)).thenReturn(order);
        return order;
    }

    private OrderItem orderItem(Long id, Long orderId, Long skuId, int qty, Integer outQty) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(orderId);
        item.setSkuId(skuId);
        item.setQuantity(qty);
        item.setOutQuantity(outQty);
        item.setTenantId(TENANT_ID);
        when(orderItemMapper.selectOne(any())).thenReturn(item);
        return item;
    }

    private OrderDeliveryDTO dto(Long orderId, Long orderItemId, Long skuId, int quantity) {
        OrderDeliveryDTO dto = new OrderDeliveryDTO();
        dto.setOrderId(orderId);
        dto.setWarehouseId(1L);
        OrderDeliveryDTO.OrderDeliveryItemDTO item = new OrderDeliveryDTO.OrderDeliveryItemDTO();
        item.setOrderItemId(orderItemId);
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        dto.setItems(List.of(item));
        return dto;
    }

    @Test
    void create_rejectsWhenTenantContextMissing() {
        TenantContext.clear();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> deliveryService.create(dto(1L, 10L, 100L, 1)));
        assertEquals(403, ex.getCode());
    }

    @Test
    void create_rejectsCrossTenantOrder() {
        when(orderMapper.selectByIdForUpdate(1L, TENANT_ID)).thenReturn(null); // 租户过滤后查不到
        BusinessException ex = assertThrows(BusinessException.class,
                () -> deliveryService.create(dto(1L, 10L, 100L, 1)));
        assertEquals(404, ex.getCode());
    }

    @Test
    void create_rejectsLegacyUnmigratedOrder() {
        Order order = stockLinkedOrder(1L);
        order.setFulfillmentStatus(null);
        order.setFulfillmentMode(null);
        assertThrows(BusinessException.class, () -> deliveryService.create(dto(1L, 10L, 100L, 1)));
    }

    @Test
    void create_rejectsForeignOrderItem() {
        stockLinkedOrder(1L);
        when(orderItemMapper.selectOne(any())).thenReturn(null); // 明细不属于该订单/租户
        BusinessException ex = assertThrows(BusinessException.class,
                () -> deliveryService.create(dto(1L, 999L, 100L, 1)));
        assertTrue(ex.getMessage().contains("不属于当前订单"));
    }

    @Test
    void create_rejectsSkuMismatch() {
        stockLinkedOrder(1L);
        orderItem(10L, 1L, 100L, 5, 0);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> deliveryService.create(dto(1L, 10L, 200L, 1)));
        assertTrue(ex.getMessage().contains("不一致"));
    }

    @Test
    void create_rejectsZeroAndNegativeQuantity() {
        stockLinkedOrder(1L);
        orderItem(10L, 1L, 100L, 5, 0);
        assertThrows(BusinessException.class, () -> deliveryService.create(dto(1L, 10L, 100L, 0)));
        assertThrows(BusinessException.class, () -> deliveryService.create(dto(1L, 10L, 100L, -3)));
    }

    @Test
    void create_rejectsQuantityOverShippable() {
        stockLinkedOrder(1L);
        orderItem(10L, 1L, 100L, 5, 2); // 可发 3
        BusinessException ex = assertThrows(BusinessException.class,
                () -> deliveryService.create(dto(1L, 10L, 100L, 4)));
        assertTrue(ex.getMessage().contains("可发数量"));
    }

    @Test
    void create_acceptsValidDeliveryWithinShippable() {
        stockLinkedOrder(1L);
        orderItem(10L, 1L, 100L, 5, 2); // 可发 3
        Long id = deliveryService.create(dto(1L, 10L, 100L, 3));
        assertNotNull(id);
    }
}

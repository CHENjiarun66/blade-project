package com.blade.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.blade.common.tenant.TenantContext;
import com.blade.inventory.controller.InventoryController;
import com.blade.inventory.dto.OutByPlanDTO;
import com.blade.inventory.entity.Inventory;
import com.blade.inventory.mapper.InventoryLogMapper;
import com.blade.inventory.mapper.InventoryMapper;
import com.blade.inventory.service.InventoryService;
import com.blade.inventory.service.impl.InventoryServiceImpl;
import com.blade.order.entity.OrderDeliveryPlan;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.blade.file.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SOW-2 / BE-142: Inventory outByPlan refactor + controller rejection —
 * focused unit tests. Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryOutByPlanSoftCouplingTest {

    // ── InventoryServiceImpl mocks ────────────────────────────────────
    @Mock private InventoryMapper inventoryMapper;
    @Mock private InventoryLogMapper inventoryLogMapper;
    @Mock private com.blade.inventory.mapper.InventoryGlobalReserveMapper globalReserveMapper;
    @Mock private ProductSkuMapper productSkuMapper;
    @Mock private RedissonClient redissonClient;
    @Mock private OrderDeliveryPlanMapper deliveryPlanMapper;
    @Mock private FileService fileService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    // ── InventoryController (manual, not @InjectMocks to avoid wiring all deps) ──

    @BeforeAll
    static void initMyBatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, OrderDeliveryPlan.class);
        TableInfoHelper.initTableInfo(assistant, Inventory.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        // Stub Redisson lock to succeed
        RLock lock = mock(RLock.class);
        try {
            lenient().when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        } catch (InterruptedException e) {
            // won't happen in mock
        }
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ── helpers ──────────────────────────────────────────────────────

    private OrderDeliveryPlan stubPlan(Long id, Long skuId, Long warehouseId,
                                       int allocatedQty, int outQty, Long tenantId) {
        OrderDeliveryPlan p = new OrderDeliveryPlan();
        p.setId(id);
        p.setOrderId(1L);
        p.setSkuId(skuId);
        p.setWarehouseId(warehouseId);
        p.setPlannedQty(allocatedQty + outQty);
        p.setAllocatedQty(allocatedQty);
        p.setOutQty(outQty);
        p.setStatus(OrderDeliveryPlan.Status.ALLOCATED);
        p.setTenantId(tenantId);
        return p;
    }

    private Inventory stubInventory(Long id, Long skuId, Long warehouseId,
                                    int quantity, int reservedQty, Integer globalReservedQty,
                                    Long tenantId) {
        Inventory inv = new Inventory();
        inv.setId(id);
        inv.setSkuId(skuId);
        inv.setWarehouseId(warehouseId);
        inv.setQuantity(quantity);
        inv.setReservedQty(reservedQty);
        inv.setGlobalReservedQty(globalReservedQty);
        inv.setVersion(1);
        inv.setTenantId(tenantId);
        return inv;
    }

    // ══════════════════════════════════════════════════════════════════
    // outByPlan — positive qty validation
    // ══════════════════════════════════════════════════════════════════

    @Test
    void outByPlan_nullQuantity_shouldThrow() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.outByPlan(1L, null, 1L));
        assertTrue(ex.getMessage().contains("大于0"));
        verifyNoInteractions(inventoryMapper);
    }

    @Test
    void outByPlan_zeroQuantity_shouldThrow() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.outByPlan(1L, 0, 1L));
        assertTrue(ex.getMessage().contains("大于0"));
        verifyNoInteractions(inventoryMapper);
    }

    // ══════════════════════════════════════════════════════════════════
    // outByPlan — tenant-scoped queries
    // ══════════════════════════════════════════════════════════════════

    @Test
    void outByPlan_shouldQueryPlanWithTenantFilter() {
        OrderDeliveryPlan plan = stubPlan(1L, 100L, 10L, 5, 0, 1L);
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        Inventory inv = stubInventory(1L, 100L, 10L, 20, 2, null, 1L);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        when(inventoryMapper.deductQuantity(eq(1L), eq(1L), eq(5))).thenReturn(1);

        inventoryService.outByPlan(1L, 5, 1L);

        // Capture plan query wrapper and assert it contains tenant filter
        ArgumentCaptor<LambdaQueryWrapper<OrderDeliveryPlan>> planCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deliveryPlanMapper).selectOne(planCaptor.capture());
        String planSql = planCaptor.getValue().getSqlSegment();
        assertNotNull(planSql);
        assertTrue(planSql.contains("id"), "Plan query must filter by id: " + planSql);
        assertTrue(planSql.contains("tenant_id"), "Plan query must filter by tenant_id: " + planSql);

        // Capture inventory query wrapper and assert it contains tenant filter
        ArgumentCaptor<LambdaQueryWrapper<Inventory>> invCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryMapper).selectOne(invCaptor.capture());
        String invSql = invCaptor.getValue().getSqlSegment();
        assertNotNull(invSql);
        assertTrue(invSql.contains("sku_id"), "Inventory query must filter by sku_id: " + invSql);
        assertTrue(invSql.contains("warehouse_id"), "Inventory query must filter by warehouse_id: " + invSql);
        assertTrue(invSql.contains("tenant_id"), "Inventory query must filter by tenant_id: " + invSql);
    }

    @Test
    void outByPlan_planNotFoundInTenant_shouldThrow() {
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.outByPlan(1L, 5, 1L));
        assertTrue(ex.getMessage().contains("不存在"));
        verifyNoInteractions(inventoryMapper);
    }

    // ══════════════════════════════════════════════════════════════════
    // outByPlan — does NOT touch global_reserved_qty
    // ══════════════════════════════════════════════════════════════════

    @Test
    void outByPlan_shouldNeverChangeGlobalReservedQty() {
        // Even if global_reserved_qty is non-null, the new outByPlan ignores it
        OrderDeliveryPlan plan = stubPlan(2L, 200L, 20L, 10, 0, 1L);
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        Inventory inv = stubInventory(2L, 200L, 20L, 50, 5, 30, 1L);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        when(inventoryMapper.deductQuantity(eq(2L), eq(1L), eq(10))).thenReturn(1);

        inventoryService.outByPlan(2L, 10, 1L);

        // Must use deductQuantity (only quantity, no global_reserved_qty column)
        verify(inventoryMapper).deductQuantity(eq(2L), eq(1L), eq(10));

        // Must NOT call the generic update that could touch global_reserved_qty
        verify(inventoryMapper, never()).update(isNull(), any());
    }

    @Test
    void outByPlan_availableCalculation_shouldOnlySubtractReservedQty() {
        // Available = quantity - reserved_qty, NOT minus global_reserved_qty
        OrderDeliveryPlan plan = stubPlan(3L, 300L, 30L, 10, 0, 1L);
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        // quantity=15, reserved_qty=3 => available=12, global_reserved_qty=50 (ignored)
        // requested=10, 10 <= 12 => should succeed
        Inventory inv = stubInventory(3L, 300L, 30L, 15, 3, 50, 1L);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        when(inventoryMapper.deductQuantity(eq(3L), eq(1L), eq(10))).thenReturn(1);

        // Should NOT throw — global_reserved_qty=50 is irrelevant
        assertDoesNotThrow(() -> inventoryService.outByPlan(3L, 10, 1L));
    }

    // ══════════════════════════════════════════════════════════════════
    // outByPlan — insufficient stock error
    // ══════════════════════════════════════════════════════════════════

    @Test
    void outByPlan_insufficientAvailable_shouldThrowWithSkuWarehouseDetails() {
        OrderDeliveryPlan plan = stubPlan(4L, 400L, 40L, 10, 0, 1L);
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        // quantity=5, reserved_qty=3 => available=2, need=10
        Inventory inv = stubInventory(4L, 400L, 40L, 5, 3, 30, 1L);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.outByPlan(4L, 10, 1L));
        assertTrue(ex.getMessage().contains("SKU[400]"));
        assertTrue(ex.getMessage().contains("仓库[40]"));
        assertTrue(ex.getMessage().contains("可用:2"));
        assertTrue(ex.getMessage().contains("需要:10"));

        // Must NOT attempt deduct
        verify(inventoryMapper, never()).deductQuantity(anyLong(), anyLong(), anyInt());
    }

    @Test
    void outByPlan_deductReturnsZero_shouldThrowDetailedError() {
        OrderDeliveryPlan plan = stubPlan(5L, 500L, 50L, 5, 0, 1L);
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        Inventory inv = stubInventory(5L, 500L, 50L, 20, 10, 0, 1L);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        // deductQuantity returns 0 (concurrent modification or insufficient)
        when(inventoryMapper.deductQuantity(eq(5L), eq(1L), eq(5))).thenReturn(0);

        // Re-read after failure — uses tenant-scoped selectOne (not selectById)
        Inventory recheck = stubInventory(5L, 500L, 50L, 20, 12, 0, 1L); // someone else reserved
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv, recheck);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.outByPlan(5L, 5, 1L));
        assertTrue(ex.getMessage().contains("库存不足或已被修改"));
        assertTrue(ex.getMessage().contains("SKU[500]"));

        // Must NOT use plain selectById for re-read — must use tenant-scoped selectOne
        verify(inventoryMapper, never()).selectById(anyLong());
    }

    // ══════════════════════════════════════════════════════════════════
    // outByPlan — exceed allocatedQty
    // ══════════════════════════════════════════════════════════════════

    @Test
    void outByPlan_exceedAllocatedQty_shouldThrow() {
        OrderDeliveryPlan plan = stubPlan(6L, 600L, 60L, 5, 3, 1L); // allocated=5, out=3 => max=2
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.outByPlan(6L, 5, 1L)); // request 5 > max 2
        assertTrue(ex.getMessage().contains("超过"));
        verifyNoInteractions(inventoryMapper);
    }

    // ══════════════════════════════════════════════════════════════════
    // outByPlan — null allocatedQty / outQty rejection
    // ══════════════════════════════════════════════════════════════════

    @Test
    void outByPlan_nullAllocatedQty_shouldThrowWithoutTouchingInventory() {
        OrderDeliveryPlan plan = stubPlan(61L, 610L, 61L, 5, 0, 1L);
        plan.setAllocatedQty(null); // corrupt plan data
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.outByPlan(61L, 5, 1L));
        assertTrue(ex.getMessage().contains("配货数量或已出库数量为空"));
        verifyNoInteractions(inventoryMapper);
    }

    @Test
    void outByPlan_nullOutQty_shouldThrowWithoutTouchingInventory() {
        OrderDeliveryPlan plan = stubPlan(62L, 620L, 62L, 5, 0, 1L);
        plan.setOutQty(null); // corrupt plan data
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.outByPlan(62L, 5, 1L));
        assertTrue(ex.getMessage().contains("配货数量或已出库数量为空"));
        verifyNoInteractions(inventoryMapper);
    }

    @Test
    void outByPlan_nullInventoryQuantity_shouldThrowWithoutDeducting() {
        OrderDeliveryPlan plan = stubPlan(63L, 630L, 63L, 5, 0, 1L);
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        Inventory inv = stubInventory(63L, 630L, 63L, 0, 0, null, 1L);
        inv.setQuantity(null); // corrupt inventory data
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.outByPlan(63L, 5, 1L));
        assertTrue(ex.getMessage().contains("库存数量为空"));
        verify(inventoryMapper, never()).deductQuantity(anyLong(), anyLong(), anyInt());
    }

    // ══════════════════════════════════════════════════════════════════
    // outByPlan — plan/log update in same transaction
    // ══════════════════════════════════════════════════════════════════

    @Test
    void outByPlan_shouldUpdatePlanOutQtyAndInsertLog() {
        OrderDeliveryPlan plan = stubPlan(7L, 700L, 70L, 10, 0, 1L);
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        Inventory inv = stubInventory(7L, 700L, 70L, 50, 5, 20, 1L);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        when(inventoryMapper.deductQuantity(eq(7L), eq(1L), eq(5))).thenReturn(1);

        inventoryService.outByPlan(7L, 5, 1L);

        // Plan updated
        ArgumentCaptor<OrderDeliveryPlan> planCaptor = ArgumentCaptor.forClass(OrderDeliveryPlan.class);
        verify(deliveryPlanMapper).updateById(planCaptor.capture());
        assertEquals(7L, planCaptor.getValue().getId());
        assertEquals(5, planCaptor.getValue().getOutQty());

        // Log inserted
        verify(inventoryLogMapper).insert(any(com.blade.inventory.entity.InventoryLog.class));
    }

    @Test
    void outByPlan_fullyOut_shouldSetPlanStatusToOUT() {
        OrderDeliveryPlan plan = stubPlan(8L, 800L, 80L, 5, 0, 1L); // allocated=5, out=0
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        Inventory inv = stubInventory(8L, 800L, 80L, 20, 0, 0, 1L);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        when(inventoryMapper.deductQuantity(eq(8L), eq(1L), eq(5))).thenReturn(1);

        inventoryService.outByPlan(8L, 5, 1L);

        ArgumentCaptor<OrderDeliveryPlan> planCaptor = ArgumentCaptor.forClass(OrderDeliveryPlan.class);
        verify(deliveryPlanMapper).updateById(planCaptor.capture());
        assertEquals(OrderDeliveryPlan.Status.OUT, planCaptor.getValue().getStatus());
        assertEquals(5, planCaptor.getValue().getOutQty());
    }

    // ══════════════════════════════════════════════════════════════════
    // outByPlan — missing warehouse
    // ══════════════════════════════════════════════════════════════════

    @Test
    void outByPlan_noWarehouse_shouldThrow() {
        OrderDeliveryPlan plan = stubPlan(9L, 900L, null, 5, 0, 1L);
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.outByPlan(9L, 5, 1L));
        assertTrue(ex.getMessage().contains("仓库"));
    }

    // ══════════════════════════════════════════════════════════════════
    // outByPlan — reservedQty null treated as 0
    // ══════════════════════════════════════════════════════════════════

    @Test
    void outByPlan_nullReservedQty_shouldTreatAsZero() {
        OrderDeliveryPlan plan = stubPlan(10L, 1000L, 100L, 5, 0, 1L);
        when(deliveryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        Inventory inv = stubInventory(10L, 1000L, 100L, 10, 0, null, 1L);
        inv.setReservedQty(null); // explicitly null
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        when(inventoryMapper.deductQuantity(eq(10L), eq(1L), eq(5))).thenReturn(1);

        // available = 10 - 0 = 10 >= 5, should succeed
        assertDoesNotThrow(() -> inventoryService.outByPlan(10L, 5, 1L));
    }

    // ══════════════════════════════════════════════════════════════════
    // Controller — direct rejection
    // ══════════════════════════════════════════════════════════════════

    @Test
    void controllerOutByPlan_shouldAlwaysReject() {
        InventoryController controller = new InventoryController();
        // Inject a no-op InventoryService — the controller must reject BEFORE
        // touching the service
        InventoryService noopService = mock(InventoryService.class);
        // Use reflection or a test-friendly approach
        // Since the controller uses @Autowired, we manually set the field for testing
        try {
            java.lang.reflect.Field field = InventoryController.class.getDeclaredField("inventoryService");
            field.setAccessible(true);
            field.set(controller, noopService);
        } catch (Exception e) {
            fail("Failed to inject InventoryService mock: " + e.getMessage());
        }

        OutByPlanDTO dto = new OutByPlanDTO();
        dto.setPlanId(1L);
        dto.setQuantity(5);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> controller.outByPlan(dto));
        assertTrue(ex.getMessage().contains("请通过订单确认发货操作出库"));

        // InventoryService.outByPlan must NOT be called
        verifyNoInteractions(noopService);
    }
}

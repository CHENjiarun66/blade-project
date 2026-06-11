package com.blade.catalog;

import com.blade.catalog.service.impl.CatalogServiceImpl;
import com.blade.inventory.entity.Inventory;
import com.blade.inventory.mapper.InventoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests availability computation: negative clamping, aggregation across warehouses,
 * and no raw quantity exposure.
 */
@ExtendWith(MockitoExtension.class)
class CatalogAvailabilityTest {

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private com.blade.product.mapper.ProductMapper productMapper;
    @Mock
    private com.blade.product.mapper.ProductSkuMapper productSkuMapper;
    @Mock
    private com.blade.product.mapper.ProductCategoryMapper productCategoryMapper;
    @Mock
    private com.blade.product.mapper.ProductColorMapper productColorMapper;
    @Mock
    private com.blade.product.mapper.ProductSizeMapper productSizeMapper;
    @Mock
    private com.blade.product.mapper.ProductColorRelMapper productColorRelMapper;
    @Mock
    private com.blade.product.mapper.ProductSizeRelMapper productSizeRelMapper;
    @Mock
    private com.blade.file.mapper.FileBusinessBindMapper fileBusinessBindMapper;
    @Mock
    private com.blade.file.mapper.FileStorageMapper fileStorageMapper;

    @InjectMocks
    private CatalogServiceImpl catalogService;

    @BeforeEach
    void setUp() {
        // mocks are initialized by MockitoExtension
    }

    // ── computeSkuStock ──

    @Test
    void computeSkuStock_positiveAvailable_shouldReturnTrue() {
        Inventory inv = new Inventory();
        inv.setSkuId(1L);
        inv.setQuantity(10);
        inv.setReservedQty(2);
        inv.setGlobalReservedQty(1);
        // available = max(0, 10-2-1) = 7 > 0

        when(inventoryMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(inv));

        Map<Long, Boolean> result = catalogService.computeSkuStock(Set.of(1L));
        assertTrue(result.get(1L));
    }

    @Test
    void computeSkuStock_zeroAvailable_shouldReturnFalse() {
        Inventory inv = new Inventory();
        inv.setSkuId(1L);
        inv.setQuantity(3);
        inv.setReservedQty(3);
        inv.setGlobalReservedQty(0);
        // available = max(0, 3-3-0) = 0 → false

        when(inventoryMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(inv));

        Map<Long, Boolean> result = catalogService.computeSkuStock(Set.of(1L));
        assertFalse(result.get(1L));
    }

    @Test
    void computeSkuStock_negativeClamped_shouldReturnFalse() {
        // reserved + globalReserved > quantity → available should be clamped to 0
        Inventory inv = new Inventory();
        inv.setSkuId(1L);
        inv.setQuantity(5);
        inv.setReservedQty(4);
        inv.setGlobalReservedQty(3);
        // max(0, 5-4-3) = max(0, -2) = 0

        when(inventoryMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(inv));

        Map<Long, Boolean> result = catalogService.computeSkuStock(Set.of(1L));
        assertFalse(result.get(1L), "Negative availability must be clamped to 0 (false)");
    }

    @Test
    void computeSkuStock_aggregateAcrossWarehouses_shouldReturnTrueIfAnyPositive() {
        // warehouse A: 0 available, warehouse B: 5 available → overall hasStock=true
        Inventory invA = new Inventory();
        invA.setSkuId(1L);
        invA.setQuantity(2);
        invA.setReservedQty(2);
        invA.setGlobalReservedQty(0);
        // available = max(0, 2-2-0) = 0

        Inventory invB = new Inventory();
        invB.setSkuId(1L);
        invB.setQuantity(10);
        invB.setReservedQty(3);
        invB.setGlobalReservedQty(2);
        // available = max(0, 10-3-2) = 5

        when(inventoryMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(invA, invB));

        Map<Long, Boolean> result = catalogService.computeSkuStock(Set.of(1L));
        assertTrue(result.get(1L), "Stock should be true if any warehouse has positive available");
    }

    @Test
    void computeSkuStock_allWarehousesZeroOrNegative_shouldReturnFalse() {
        Inventory invA = new Inventory();
        invA.setSkuId(1L);
        invA.setQuantity(0);
        invA.setReservedQty(0);
        invA.setGlobalReservedQty(1); // negative

        Inventory invB = new Inventory();
        invB.setSkuId(1L);
        invB.setQuantity(3);
        invB.setReservedQty(3);
        invB.setGlobalReservedQty(0); // 0

        when(inventoryMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(invA, invB));

        Map<Long, Boolean> result = catalogService.computeSkuStock(Set.of(1L));
        assertFalse(result.get(1L));
    }

    @Test
    void computeSkuStock_nullFieldsTreatedAsZero() {
        Inventory inv = new Inventory();
        inv.setSkuId(1L);
        inv.setQuantity(5);
        // reservedQty=null, globalReservedQty=null → treated as 0
        // available = max(0, 5-0-0) = 5

        when(inventoryMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(inv));

        Map<Long, Boolean> result = catalogService.computeSkuStock(Set.of(1L));
        assertTrue(result.get(1L));
    }

    @Test
    void computeSkuStock_emptyInput_returnsEmptyMap() {
        Map<Long, Boolean> result = catalogService.computeSkuStock(Collections.emptySet());
        assertTrue(result.isEmpty());
    }

    @Test
    void computeSkuStock_skuWithNoInventory_returnsFalse() {
        when(inventoryMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());

        Map<Long, Boolean> result = catalogService.computeSkuStock(Set.of(99L));
        assertFalse(result.get(99L));
    }

    // ── previewUrl ──

    @Test
    void previewUrl_shouldBuildCorrectPath() {
        assertEquals("/api/files/42/preview", CatalogServiceImpl.previewUrl(42L));
    }
}

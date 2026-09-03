package com.blade.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.common.exception.BusinessException;
import com.blade.product.entity.ProductSku;
import com.blade.product.enums.ProductSkuType;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.product.service.InventorySkuEligibilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventorySkuEligibilityServiceTest {

    @Mock
    private ProductSkuMapper skuMapper;

    @Test
    void normalSkuCanWriteInventory() {
        ProductSku sku = sku(ProductSkuType.NORMAL, 10L);
        when(skuMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sku);

        assertDoesNotThrow(() -> service().requireEligible(1L, 1L));
    }

    @Test
    void currentDefaultCanWriteInventory() {
        ProductSku sku = sku(ProductSkuType.DEFAULT, 10L);
        when(skuMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sku);
        when(skuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertDoesNotThrow(() -> service().requireEligible(1L, 1L));
    }

    @Test
    void placeholderCannotWriteInventory() {
        when(skuMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(sku(ProductSkuType.PLACEHOLDER, 10L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().requireEligible(1L, 1L));
        assertTrue(ex.getMessage().contains("整款录入"));
    }

    @Test
    void historicalDefaultCannotWriteInventoryAfterVariantsExist() {
        when(skuMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(sku(ProductSkuType.DEFAULT, 10L));
        when(skuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().requireEligible(1L, 1L));
        assertTrue(ex.getMessage().contains("历史无规格"));
    }

    @Test
    void missingOrCrossTenantSkuCannotWriteInventory() {
        when(skuMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().requireEligible(1L, 2L));
        assertTrue(ex.getMessage().contains("当前租户"));
    }

    private InventorySkuEligibilityService service() {
        return new InventorySkuEligibilityService(skuMapper);
    }

    private ProductSku sku(ProductSkuType type, Long productId) {
        ProductSku sku = new ProductSku();
        sku.setId(1L);
        sku.setProductId(productId);
        sku.setSkuType(type.name());
        sku.setTenantId(1L);
        sku.setDeleted(0);
        sku.setStatus(1);
        return sku;
    }
}

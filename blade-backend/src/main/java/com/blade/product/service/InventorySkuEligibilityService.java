package com.blade.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.common.exception.BusinessException;
import com.blade.product.entity.ProductSku;
import com.blade.product.enums.ProductSkuType;
import com.blade.product.mapper.ProductSkuMapper;
import org.springframework.stereotype.Service;

/**
 * 库存写入的 SKU 语义闸门。
 * PLACEHOLDER 只表达“整款录入但规格未定”，历史 DEFAULT 只保留订单引用；两者都不能形成新库存事实。
 */
@Service
public class InventorySkuEligibilityService {

    private final ProductSkuMapper skuMapper;

    public InventorySkuEligibilityService(ProductSkuMapper skuMapper) {
        this.skuMapper = skuMapper;
    }

    public ProductSku requireEligible(Long skuId, Long tenantId) {
        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getId, skuId)
                .eq(ProductSku::getTenantId, tenantId)
                .eq(ProductSku::getDeleted, 0)
                .last("LIMIT 1"));
        if (sku == null) {
            throw BusinessException.of(404, "SKU不存在或不属于当前租户");
        }
        if (ProductSkuSemantics.isPlaceholder(sku)) {
            throw BusinessException.of(400, "整款录入SKU不能用于库存操作，请先明确颜色和尺码");
        }
        if (ProductSkuSemantics.isDefault(sku)) {
            Long activeVariants = skuMapper.selectCount(new LambdaQueryWrapper<ProductSku>()
                    .eq(ProductSku::getProductId, sku.getProductId())
                    .eq(ProductSku::getTenantId, tenantId)
                    .eq(ProductSku::getSkuType, ProductSkuType.NORMAL.name())
                    .eq(ProductSku::getStatus, 1)
                    .eq(ProductSku::getDeleted, 0));
            if (activeVariants != null && activeVariants > 0) {
                throw BusinessException.of(400, "历史无规格SKU不能用于库存操作，请选择当前具体规格");
            }
        }
        return sku;
    }
}

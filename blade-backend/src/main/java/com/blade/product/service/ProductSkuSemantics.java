package com.blade.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.product.entity.ProductSku;
import com.blade.product.enums.ProductSkuType;
import com.blade.product.mapper.ProductSkuMapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * SKU 语义判断的唯一入口：DEFAULT 表示商品本身无规格，PLACEHOLDER 表示有规格但本次未明确。
 * 当商品后来增加真实规格时，历史 DEFAULT 仍保留原 ID，但在待履约与规格分析中视为“历史无规格”。
 */
public final class ProductSkuSemantics {

    private ProductSkuSemantics() {
    }

    public static boolean isPlaceholder(ProductSku sku) {
        return sku != null && ProductSkuType.PLACEHOLDER.name().equals(sku.getSkuType());
    }

    public static boolean isDefault(ProductSku sku) {
        return sku != null && ProductSkuType.DEFAULT.name().equals(sku.getSkuType());
    }

    public static boolean requiresVariantResolution(ProductSku sku, Set<Long> variantProductIds) {
        if (isPlaceholder(sku)) {
            return true;
        }
        return isDefault(sku)
                && sku.getProductId() != null
                && variantProductIds.contains(sku.getProductId());
    }

    public static Set<Long> findProductsWithActiveVariants(ProductSkuMapper mapper,
                                                            Collection<ProductSku> referencedSkus) {
        Set<Long> productIds = new HashSet<>();
        if (referencedSkus != null) {
            referencedSkus.stream()
                    .filter(Objects::nonNull)
                    .map(ProductSku::getProductId)
                    .filter(Objects::nonNull)
                    .forEach(productIds::add);
        }
        if (productIds.isEmpty()) {
            return Set.of();
        }
        List<ProductSku> activeVariants = mapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .in(ProductSku::getProductId, productIds)
                .eq(ProductSku::getSkuType, ProductSkuType.NORMAL.name())
                .eq(ProductSku::getStatus, 1)
                .eq(ProductSku::getDeleted, 0));
        Set<Long> result = new HashSet<>();
        activeVariants.stream()
                .map(ProductSku::getProductId)
                .filter(Objects::nonNull)
                .forEach(result::add);
        return result;
    }
}

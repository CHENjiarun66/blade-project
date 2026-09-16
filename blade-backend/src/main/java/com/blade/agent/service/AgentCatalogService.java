package com.blade.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.order.draft.dto.OrderDraftDTO.CatalogCandidate;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductColor;
import com.blade.product.entity.ProductSize;
import com.blade.product.entity.ProductSku;
import com.blade.product.enums.ProductSkuType;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentCatalogService {
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final ProductColorMapper colorMapper;
    private final ProductSizeMapper sizeMapper;

    public List<CatalogCandidate> search(String keyword,
                                         String productCode,
                                         String colorName,
                                         String sizeCode,
                                         int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getStatus, 1)
                .orderByDesc(ProductSku::getId));
        if (skus.isEmpty()) {
            return List.of();
        }

        Set<Long> productIds = new LinkedHashSet<>();
        Set<Long> colorIds = new LinkedHashSet<>();
        Set<Long> sizeIds = new LinkedHashSet<>();
        skus.forEach(sku -> {
            if (sku.getProductId() != null) productIds.add(sku.getProductId());
            if (sku.getColorId() != null) colorIds.add(sku.getColorId());
            if (sku.getSizeId() != null) sizeIds.add(sku.getSizeId());
        });

        Map<Long, Product> products = indexById(productMapper.selectBatchIds(productIds));
        Map<Long, ProductColor> colors = indexColorById(colorMapper.selectBatchIds(colorIds));
        Map<Long, ProductSize> sizes = indexSizeById(sizeMapper.selectBatchIds(sizeIds));
        String normalizedKeyword = normalize(keyword);
        String normalizedProductCode = normalize(productCode);
        String normalizedColor = normalize(colorName);
        String normalizedSize = normalize(sizeCode);
        Map<Long, Long> activeRealSkuCounts = skus.stream()
                .filter(sku -> !isPlaceholder(sku))
                .filter(sku -> sku.getProductId() != null)
                .collect(java.util.stream.Collectors.groupingBy(ProductSku::getProductId,
                        java.util.stream.Collectors.counting()));

        return skus.stream()
                .map(sku -> candidate(sku, products.get(sku.getProductId()),
                        colors.get(sku.getColorId()), sizes.get(sku.getSizeId()),
                        normalizedKeyword, normalizedProductCode, normalizedColor, normalizedSize,
                        activeRealSkuCounts.getOrDefault(sku.getProductId(), 0L)))
                .filter(match -> match.score.compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> b.score.compareTo(a.score))
                .limit(limit)
                .map(match -> match.candidate)
                .toList();
    }

    private Match candidate(ProductSku sku,
                            Product product,
                            ProductColor color,
                            ProductSize size,
                            String keyword,
                            String productCode,
                            String colorName,
                            String sizeCode,
                            long activeRealSkuCount) {
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            return Match.none();
        }
        boolean placeholder = isPlaceholder(sku);
        boolean hasSpecificVariant = !colorName.isBlank() || !sizeCode.isBlank();
        if (placeholder && hasSpecificVariant) {
            return Match.none();
        }
        String candidateProductCode = normalize(product.getProductCode());
        String candidateSkuCode = normalize(sku.getSkuCode());
        String candidateName = normalize(product.getName());
        String candidateColor = normalize(color == null ? null : color.getColorName());
        String candidateSize = normalize(size == null ? null : size.getSizeCode());
        List<String> reasons = new ArrayList<>();
        BigDecimal score = BigDecimal.ZERO;

        String primary = !productCode.isBlank() ? productCode : keyword;
        if (!primary.isBlank()) {
            if (candidateSkuCode.equals(primary)) {
                score = new BigDecimal("1.00");
                reasons.add("sku_code_exact");
            } else if (candidateProductCode.equals(primary)) {
                score = new BigDecimal("0.95");
                reasons.add("product_code_normalized");
            } else if (candidateProductCode.startsWith(primary) || primary.startsWith(candidateProductCode)) {
                score = new BigDecimal("0.86");
                reasons.add("product_code_prefix");
            } else if (candidateProductCode.contains(primary) || primary.contains(candidateProductCode)) {
                score = new BigDecimal("0.74");
                reasons.add("product_code_contains");
            } else if (candidateName.contains(primary) || candidateSkuCode.contains(primary)) {
                score = new BigDecimal("0.62");
                reasons.add("keyword_contains");
            } else {
                return Match.none();
            }
        } else {
            score = new BigDecimal("0.40");
        }

        if (placeholder && candidateProductCode.equals(primary) && !hasSpecificVariant) {
            score = BigDecimal.ONE;
            reasons.add("spu_placeholder");
        } else if (!placeholder && activeRealSkuCount == 1 && candidateProductCode.equals(primary)) {
            score = score.add(new BigDecimal("0.03"));
            reasons.add("single_saleable_sku");
        }

        if (!colorName.isBlank()) {
            if (candidateColor.isBlank()
                    || (!candidateColor.contains(colorName) && !colorName.contains(candidateColor))) {
                return Match.none();
            }
            score = score.add(new BigDecimal("0.04"));
            reasons.add("color_name");
        }
        if (!sizeCode.isBlank()) {
            if (!candidateSize.equals(sizeCode)) {
                return Match.none();
            }
            score = score.add(new BigDecimal("0.02"));
            reasons.add("size_code");
        }

        CatalogCandidate result = new CatalogCandidate();
        result.setSkuId(sku.getId());
        result.setSkuCode(sku.getSkuCode());
        result.setSkuType(normalizeSkuType(sku));
        result.setPlaceholder(placeholder);
        result.setProductId(product.getId());
        result.setProductCode(product.getProductCode());
        result.setProductName(product.getName());
        result.setColorCode(color == null ? null : color.getColorCode());
        result.setColorName(color == null ? null : color.getColorName());
        result.setSizeCode(size == null ? null : size.getSizeCode());
        result.setSystemReferencePrice(sku.getPrice() != null ? sku.getPrice() : product.getWholesalePrice());
        result.setMatchScore(score.min(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP));
        result.setMatchReasons(reasons);
        return new Match(result, result.getMatchScore());
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s#＃_\\-./\\\\]", "")
                .trim();
    }

    private String normalizeSkuType(ProductSku sku) {
        return sku.getSkuType() == null || sku.getSkuType().isBlank()
                ? ProductSkuType.NORMAL.name()
                : sku.getSkuType();
    }

    private boolean isPlaceholder(ProductSku sku) {
        return ProductSkuType.PLACEHOLDER.name().equals(normalizeSkuType(sku));
    }

    private Map<Long, Product> indexById(List<Product> rows) {
        Map<Long, Product> result = new HashMap<>();
        rows.forEach(row -> result.put(row.getId(), row));
        return result;
    }

    private Map<Long, ProductColor> indexColorById(List<ProductColor> rows) {
        Map<Long, ProductColor> result = new HashMap<>();
        rows.forEach(row -> result.put(row.getId(), row));
        return result;
    }

    private Map<Long, ProductSize> indexSizeById(List<ProductSize> rows) {
        Map<Long, ProductSize> result = new HashMap<>();
        rows.forEach(row -> result.put(row.getId(), row));
        return result;
    }

    private record Match(CatalogCandidate candidate, BigDecimal score) {
        static Match none() {
            return new Match(null, BigDecimal.ZERO);
        }
    }
}

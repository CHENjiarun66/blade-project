package com.blade.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blade.agent.dto.AgentProductDTO;
import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.product.dto.ProductCreateDTO;
import com.blade.product.dto.ProductPageDTO;
import com.blade.product.dto.ProductVO;
import com.blade.product.entity.Product;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.service.ProductService;
import com.blade.product.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentProductService {
    private static final Set<String> RESERVED_COLOR_CODES = Set.of("NA", "UNSPECIFIED");
    private static final Set<String> RESERVED_SIZE_CODES = Set.of("NA", "UNSPEC");

    private final ProductService productService;
    private final ProductCategoryService categoryService;
    private final ProductMapper productMapper;

    public PageResult<AgentProductDTO.ProductView> page(ProductPageDTO query) {
        PageResult<ProductVO> source = productService.pageList(query);
        return new PageResult<>(source.getRecords().stream().map(this::toView).toList(),
                source.getTotal(), source.getSize(), source.getCurrent());
    }

    public AgentProductDTO.ProductView detail(Long id) {
        return toView(productService.getById(id));
    }

    public AgentProductDTO.OptionsView options() {
        List<AgentProductDTO.CategoryView> categories = categoryService.listAll().stream()
                .filter(value -> Integer.valueOf(1).equals(value.getStatus()))
                .map(value -> new AgentProductDTO.CategoryView(value.getId(), value.getCategoryName(),
                        value.getParentId(), value.getSort(), value.getStatus()))
                .toList();
        List<AgentProductDTO.ColorView> colors = productService.listAllColors().stream()
                .filter(value -> Integer.valueOf(1).equals(value.getStatus()))
                .filter(value -> !RESERVED_COLOR_CODES.contains(normalizeCode(value.getColorCode())))
                .map(value -> new AgentProductDTO.ColorView(value.getId(), value.getColorCode(),
                        value.getColorName(), value.getStatus()))
                .toList();
        List<AgentProductDTO.SizeView> sizes = productService.listAllSizes().stream()
                .filter(value -> Integer.valueOf(1).equals(value.getStatus()))
                .filter(value -> !RESERVED_SIZE_CODES.contains(normalizeCode(value.getSizeCode())))
                .map(value -> new AgentProductDTO.SizeView(value.getId(), value.getSizeCode(),
                        value.getSort(), value.getStatus()))
                .toList();
        return new AgentProductDTO.OptionsView(categories, colors, sizes);
    }

    @Transactional
    public AgentProductDTO.CreateResult create(AgentProductDTO.CreateRequest request) {
        String productCode = request.productCode().trim();
        Product existing = productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                .eq(Product::getProductCode, productCode)
                .last("LIMIT 1"));
        if (existing != null) {
            ProductVO existingView = productService.getById(existing.getId());
            return new AgentProductDTO.CreateResult(existing.getId(), existing.getProductCode(),
                    "DUPLICATE", skuCount(existingView), null);
        }

        ProductCreateDTO dto = new ProductCreateDTO();
        dto.setProductCode(productCode);
        dto.setName(request.name().trim());
        dto.setCategoryId(resolveCategoryId(request.categoryId()));
        dto.setUnit(request.unit() == null || request.unit().isBlank() ? "件" : request.unit().trim());
        dto.setCostPrice(request.costPrice());
        dto.setWholesalePrice(request.wholesalePrice());
        dto.setWeight(request.weight());
        dto.setDescription(trimToNull(request.description()));
        dto.setRemark(trimToNull(request.remark()));
        dto.setStatus(1);
        dto.setColorIds(resolveColorIds(request.colorCodes()));
        dto.setSizeIds(resolveSizeIds(request.sizeCodes()));

        Long id = productService.create(dto);
        ProductVO created = productService.getById(id);
        return new AgentProductDTO.CreateResult(id, productCode, "CREATED", skuCount(created), request.costPrice());
    }

    private List<Long> resolveColorIds(List<String> requestedCodes) {
        List<String> codes = normalizeCodes(requestedCodes, RESERVED_COLOR_CODES, "颜色");
        if (codes.isEmpty()) return List.of();
        Map<String, ProductVO.ColorVO> available = productService.listAllColors().stream()
                .collect(Collectors.toMap(color -> normalizeCode(color.getColorCode()), Function.identity(), (a, b) -> a));
        return codes.stream().map(code -> {
            ProductVO.ColorVO color = available.get(code);
            if (color == null || !Integer.valueOf(1).equals(color.getStatus())) {
                throw BusinessException.of(400, "颜色编码不存在或未启用: " + code);
            }
            return color.getId();
        }).toList();
    }

    private Long resolveCategoryId(Long categoryId) {
        if (categoryId == null) return null;
        boolean allowed = categoryService.listAll().stream()
                .anyMatch(category -> categoryId.equals(category.getId()) && Integer.valueOf(1).equals(category.getStatus()));
        if (!allowed) throw BusinessException.of(400, "商品分类不存在或未启用: " + categoryId);
        return categoryId;
    }

    private List<Long> resolveSizeIds(List<String> requestedCodes) {
        List<String> codes = normalizeCodes(requestedCodes, RESERVED_SIZE_CODES, "尺码");
        if (codes.isEmpty()) return List.of();
        Map<String, ProductVO.SizeVO> available = productService.listAllSizes().stream()
                .collect(Collectors.toMap(size -> normalizeCode(size.getSizeCode()), Function.identity(), (a, b) -> a));
        return codes.stream().map(code -> {
            ProductVO.SizeVO size = available.get(code);
            if (size == null || !Integer.valueOf(1).equals(size.getStatus())) {
                throw BusinessException.of(400, "尺码编码不存在或未启用: " + code);
            }
            return size.getId();
        }).toList();
    }

    private List<String> normalizeCodes(List<String> rawCodes, Set<String> reserved, String field) {
        if (rawCodes == null || rawCodes.isEmpty()) return List.of();
        LinkedHashSet<String> codes = rawCodes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalizeCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String code : codes) {
            if (reserved.contains(code)) {
                throw BusinessException.of(400, field + "不能使用系统保留编码: " + code);
            }
        }
        return List.copyOf(codes);
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private AgentProductDTO.ProductView toView(ProductVO product) {
        List<AgentProductDTO.ColorView> colors = product.getColors() == null ? List.of() : product.getColors().stream()
                .map(value -> new AgentProductDTO.ColorView(value.getId(), value.getColorCode(), value.getColorName(), value.getStatus()))
                .toList();
        List<AgentProductDTO.SizeView> sizes = product.getSizes() == null ? List.of() : product.getSizes().stream()
                .map(value -> new AgentProductDTO.SizeView(value.getId(), value.getSizeCode(), value.getSort(), value.getStatus()))
                .toList();
        List<AgentProductDTO.SkuView> skus = product.getSkus() == null ? List.of() : product.getSkus().stream()
                .map(value -> new AgentProductDTO.SkuView(value.getId(), value.getSkuCode(), value.getSkuType(),
                        value.isPlaceholder(), value.getColorName(), value.getSizeName(), value.getPrice(),
                        value.getBarCode(), value.getStatus()))
                .toList();
        return new AgentProductDTO.ProductView(product.getId(), product.getProductCode(), product.getName(),
                product.getCategoryId(), product.getCategoryName(), product.getSupplierId(), product.getSupplierName(),
                product.getUnit(), product.getWholesalePrice(), product.getWeight(), product.getDescription(),
                product.getImageUrl(), product.getRemark(), product.getStatus(), colors, sizes, skus,
                product.getCreateTime(), product.getUpdateTime());
    }

    private int skuCount(ProductVO product) {
        return product.getSkus() == null ? 0 : product.getSkus().size();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

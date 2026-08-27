package com.blade.catalog.service.impl;

import com.blade.catalog.dto.*;
import com.blade.catalog.service.CatalogService;
import com.blade.common.result.PageResult;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.inventory.entity.Inventory;
import com.blade.inventory.mapper.InventoryMapper;
import com.blade.product.entity.*;
import com.blade.product.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final ProductColorMapper productColorMapper;
    private final ProductSizeMapper productSizeMapper;
    private final ProductColorRelMapper productColorRelMapper;
    private final ProductSizeRelMapper productSizeRelMapper;
    private final InventoryMapper inventoryMapper;
    private final FileBusinessBindMapper fileBusinessBindMapper;
    private final FileStorageMapper fileStorageMapper;

    // ──────────────────────────────────────────────
    // pageList
    // ──────────────────────────────────────────────

    @Override
    public PageResult<CatalogProductVO> pageList(CatalogPageDTO dto) {
        dto.normalize();

        // 1. Base product query with keyword + categoryId
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        qw.eq(Product::getStatus, 1)
          .eq(Product::getDeleted, 0);

        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            String kw = dto.getKeyword().trim();
            qw.and(w -> w.like(Product::getProductCode, kw).or().like(Product::getName, kw));
        }
        if (dto.getCategoryId() != null) {
            qw.eq(Product::getCategoryId, dto.getCategoryId());
        }

        // colorId / sizeId filtering: build a set of productIds first
        Set<Long> colorFilteredIds = null;
        Set<Long> sizeFilteredIds = null;

        if (dto.getColorId() != null) {
            List<ProductColorRel> rels = productColorRelMapper.selectList(
                new LambdaQueryWrapper<ProductColorRel>().eq(ProductColorRel::getColorId, dto.getColorId()));
            colorFilteredIds = rels.stream().map(ProductColorRel::getProductId).collect(Collectors.toSet());
        }
        if (dto.getSizeId() != null) {
            List<ProductSizeRel> rels = productSizeRelMapper.selectList(
                new LambdaQueryWrapper<ProductSizeRel>().eq(ProductSizeRel::getSizeId, dto.getSizeId()));
            sizeFilteredIds = rels.stream().map(ProductSizeRel::getProductId).collect(Collectors.toSet());
        }
        if (colorFilteredIds != null) {
            if (colorFilteredIds.isEmpty()) {
                return emptyPage(dto);
            }
            qw.in(Product::getId, colorFilteredIds);
        }
        if (sizeFilteredIds != null) {
            if (sizeFilteredIds.isEmpty()) {
                return emptyPage(dto);
            }
            qw.in(Product::getId, sizeFilteredIds);
        }

        if ("in_stock".equals(dto.getStockMode())) {
            Set<Long> inStockProductIds = loadInStockProductIds();
            if (inStockProductIds.isEmpty()) {
                return emptyPage(dto);
            }
            qw.in(Product::getId, inStockProductIds);
        }

        // hasImage filter: product.imageUrl is non-null non-empty (numeric fileId)
        if (Boolean.TRUE.equals(dto.getHasImage())) {
            qw.isNotNull(Product::getImageUrl).ne(Product::getImageUrl, "");
        }

        qw.orderByDesc(Product::getId);

        IPage<Product> page = new Page<>(dto.getCurrent(), dto.getSize());
        IPage<Product> productPage = productMapper.selectPage(page, qw);

        List<Product> products = productPage.getRecords();
        if (products.isEmpty()) {
            return PageResult.of(Collections.emptyList(), productPage.getTotal(), dto.getSize(), dto.getCurrent());
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();

        // 2. Batch-load related data
        // categories
        Set<Long> catIds = products.stream().map(Product::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> catNameMap = Collections.emptyMap();
        if (!catIds.isEmpty()) {
            catNameMap = productCategoryMapper.selectBatchIds(catIds).stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getCategoryName));
        }

        // colors
        Map<Long, List<CatalogProductVO.ColorSizeEntry>> prodColors = loadProductColors(productIds);

        // sizes
        Map<Long, List<CatalogProductVO.ColorSizeEntry>> prodSizes = loadProductSizes(productIds);

        // SKUs
        List<ProductSku> allSkus = productSkuMapper.selectList(
            new LambdaQueryWrapper<ProductSku>()
                .in(ProductSku::getProductId, productIds)
                .eq(ProductSku::getStatus, 1)
                .ne(ProductSku::getSkuType, "PLACEHOLDER")
                .eq(ProductSku::getDeleted, 0));
        Map<Long, List<ProductSku>> skusByProduct = allSkus.stream()
            .collect(Collectors.groupingBy(ProductSku::getProductId));

        // inventory availability per SKU
        Set<Long> skuIds = allSkus.stream().map(ProductSku::getId).collect(Collectors.toSet());
        Map<Long, Boolean> skuStockMap = computeSkuStock(skuIds);

        // colors & sizes for SKUs
        Set<Long> skuColorIds = allSkus.stream().map(ProductSku::getColorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProductColor> colorMap = skuColorIds.isEmpty() ? Collections.emptyMap()
            : productColorMapper.selectBatchIds(skuColorIds).stream()
                .collect(Collectors.toMap(ProductColor::getId, c -> c));

        Set<Long> skuSizeIds = allSkus.stream().map(ProductSku::getSizeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProductSize> sizeMap = skuSizeIds.isEmpty() ? Collections.emptyMap()
            : productSizeMapper.selectBatchIds(skuSizeIds).stream()
                .collect(Collectors.toMap(ProductSize::getId, s -> s));

        // file bindings for products + SKUs
        Map<Long, List<FileBusinessBind>> prodBindings = loadBindings("product", new HashSet<>(productIds));
        Map<Long, List<FileBusinessBind>> skuBindings = loadBindings("sku", skuIds);

        // active file storage for all bound fileIds
        Set<Long> allFileIds = new HashSet<>();
        prodBindings.values().forEach(l -> l.forEach(b -> allFileIds.add(b.getFileId())));
        skuBindings.values().forEach(l -> l.forEach(b -> allFileIds.add(b.getFileId())));
        // also collect product.imageUrl fileIds
        products.forEach(p -> {
            Long fid = parseFileId(p.getImageUrl());
            if (fid != null) allFileIds.add(fid);
        });
        Map<Long, FileStorage> fileMap = loadActiveFiles(allFileIds);

        // 3. Build VOs with stockMode post-filter
        List<CatalogProductVO> vos = new ArrayList<>();
        for (Product p : products) {
            CatalogProductVO vo = toVO(p, catNameMap, prodColors, prodSizes,
                skusByProduct, skuStockMap, colorMap, sizeMap,
                prodBindings, skuBindings, fileMap);
            vos.add(vo);
        }

        return PageResult.of(vos, productPage.getTotal(), dto.getSize(), dto.getCurrent());
    }

    // ──────────────────────────────────────────────
    // getById
    // ──────────────────────────────────────────────

    @Override
    public CatalogProductVO getById(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null || p.getDeleted() == 1 || p.getStatus() == 0) {
            return null;
        }

        List<Long> productIds = List.of(id);

        Map<Long, String> catNameMap = Collections.emptyMap();
        if (p.getCategoryId() != null) {
            ProductCategory cat = productCategoryMapper.selectById(p.getCategoryId());
            if (cat != null) catNameMap = Map.of(cat.getId(), cat.getCategoryName());
        }

        Map<Long, List<CatalogProductVO.ColorSizeEntry>> prodColors = loadProductColors(productIds);
        Map<Long, List<CatalogProductVO.ColorSizeEntry>> prodSizes = loadProductSizes(productIds);

        List<ProductSku> allSkus = productSkuMapper.selectList(
            new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, id)
                .eq(ProductSku::getStatus, 1)
                .ne(ProductSku::getSkuType, "PLACEHOLDER")
                .eq(ProductSku::getDeleted, 0));
        Map<Long, List<ProductSku>> skusByProduct = Map.of(id, allSkus);

        Set<Long> skuIds = allSkus.stream().map(ProductSku::getId).collect(Collectors.toSet());
        Map<Long, Boolean> skuStockMap = computeSkuStock(skuIds);

        Set<Long> skuColorIds = allSkus.stream().map(ProductSku::getColorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProductColor> colorMap = skuColorIds.isEmpty() ? Collections.emptyMap()
            : productColorMapper.selectBatchIds(skuColorIds).stream()
                .collect(Collectors.toMap(ProductColor::getId, c -> c));

        Set<Long> skuSizeIds = allSkus.stream().map(ProductSku::getSizeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProductSize> sizeMap = skuSizeIds.isEmpty() ? Collections.emptyMap()
            : productSizeMapper.selectBatchIds(skuSizeIds).stream()
                .collect(Collectors.toMap(ProductSize::getId, s -> s));

        Map<Long, List<FileBusinessBind>> prodBindings = loadBindings("product", new HashSet<>(productIds));
        Map<Long, List<FileBusinessBind>> skuBindings = loadBindings("sku", skuIds);

        Set<Long> allFileIds = new HashSet<>();
        prodBindings.values().forEach(l -> l.forEach(b -> allFileIds.add(b.getFileId())));
        skuBindings.values().forEach(l -> l.forEach(b -> allFileIds.add(b.getFileId())));
        Long fid = parseFileId(p.getImageUrl());
        if (fid != null) allFileIds.add(fid);
        Map<Long, FileStorage> fileMap = loadActiveFiles(allFileIds);

        return toVO(p, catNameMap, prodColors, prodSizes,
            skusByProduct, skuStockMap, colorMap, sizeMap,
            prodBindings, skuBindings, fileMap);
    }

    // ──────────────────────────────────────────────
    // getFilters
    // ──────────────────────────────────────────────

    @Override
    public CatalogFiltersVO getFilters() {
        CatalogFiltersVO vo = new CatalogFiltersVO();

        // categories: all enabled, not deleted
        List<ProductCategory> categories = productCategoryMapper.selectList(
            new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getStatus, 1)
                .eq(ProductCategory::getDeleted, 0)
                .orderByAsc(ProductCategory::getSort));
        vo.setCategories(categories.stream()
            .map(c -> new CatalogFiltersVO.FilterOption(c.getId(), c.getCategoryName()))
            .toList());

        // colors: distinct colors from active products
        List<ProductColor> colors = productColorMapper.selectList(
            new LambdaQueryWrapper<ProductColor>()
                .eq(ProductColor::getStatus, 1)
                .eq(ProductColor::getDeleted, 0));
        vo.setColors(colors.stream()
            .map(c -> new CatalogFiltersVO.FilterOption(c.getId(), c.getColorName(), c.getColorCode()))
            .toList());

        // sizes: distinct sizes from active products
        List<ProductSize> sizes = productSizeMapper.selectList(
            new LambdaQueryWrapper<ProductSize>()
                .eq(ProductSize::getStatus, 1)
                .eq(ProductSize::getDeleted, 0)
                .orderByAsc(ProductSize::getSort));
        vo.setSizes(sizes.stream()
            .map(s -> new CatalogFiltersVO.FilterOption(s.getId(), s.getSizeCode()))
            .toList());

        // stockModes
        vo.setStockModes(List.of(
            new CatalogFiltersVO.FilterOption(null, "全部"),
            new CatalogFiltersVO.FilterOption(null, "有现货", "in_stock")
        ));

        return vo;
    }

    // ══════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════

    private CatalogProductVO toVO(Product p,
                                  Map<Long, String> catNameMap,
                                  Map<Long, List<CatalogProductVO.ColorSizeEntry>> prodColors,
                                  Map<Long, List<CatalogProductVO.ColorSizeEntry>> prodSizes,
                                  Map<Long, List<ProductSku>> skusByProduct,
                                  Map<Long, Boolean> skuStockMap,
                                  Map<Long, ProductColor> colorMap,
                                  Map<Long, ProductSize> sizeMap,
                                  Map<Long, List<FileBusinessBind>> prodBindings,
                                  Map<Long, List<FileBusinessBind>> skuBindings,
                                  Map<Long, FileStorage> fileMap) {

        CatalogProductVO vo = new CatalogProductVO();
        vo.setId(p.getId());
        vo.setProductCode(p.getProductCode());
        vo.setName(p.getName());
        vo.setCategoryId(p.getCategoryId());
        vo.setCategoryName(catNameMap.getOrDefault(p.getCategoryId(), null));
        vo.setCreateTime(p.getCreateTime());

        // main image
        Long mainFid = parseFileId(p.getImageUrl());
        vo.setMainImageUrl(mainFid != null ? previewUrl(mainFid) : null);

        // gallery images from file_business_bind
        List<String> galleryUrls = new ArrayList<>();
        List<FileBusinessBind> pBinds = prodBindings.getOrDefault(p.getId(), Collections.emptyList());
        for (FileBusinessBind bind : pBinds) {
            FileStorage fs = fileMap.get(bind.getFileId());
            if (fs != null) {
                if ("main".equals(bind.getBindRole()) && vo.getMainImageUrl() == null) {
                    vo.setMainImageUrl(previewUrl(fs.getId()));
                } else if ("gallery".equals(bind.getBindRole())) {
                    galleryUrls.add(previewUrl(fs.getId()));
                }
            }
        }
        vo.setImageUrls(galleryUrls);
        vo.setHasImage(vo.getMainImageUrl() != null || !galleryUrls.isEmpty());

        // colors / sizes
        vo.setColors(prodColors.getOrDefault(p.getId(), Collections.emptyList()));
        vo.setSizes(prodSizes.getOrDefault(p.getId(), Collections.emptyList()));

        // SKUs
        List<ProductSku> skus = skusByProduct.getOrDefault(p.getId(), Collections.emptyList());
        List<CatalogSkuVO> skuVOs = new ArrayList<>();
        boolean anyStock = false;
        for (ProductSku sku : skus) {
            CatalogSkuVO sv = new CatalogSkuVO();
            sv.setId(sku.getId());
            sv.setSkuCode(sku.getSkuCode());
            sv.setColorId(sku.getColorId());
            sv.setSizeId(sku.getSizeId());

            if (sku.getColorId() != null) {
                ProductColor c = colorMap.get(sku.getColorId());
                sv.setColorName(c != null ? c.getColorName() : null);
            }
            if (sku.getSizeId() != null) {
                ProductSize s = sizeMap.get(sku.getSizeId());
                sv.setSizeCode(s != null ? s.getSizeCode() : null);
            }

            boolean skuStock = skuStockMap.getOrDefault(sku.getId(), false);
            sv.setHasStock(skuStock);
            sv.setStockStatus(skuStock ? "有现货" : "暂无现货");
            if (skuStock) anyStock = true;

            // SKU images
            List<FileBusinessBind> sBinds = skuBindings.getOrDefault(sku.getId(), Collections.emptyList());
            List<String> skuUrls = new ArrayList<>();
            for (FileBusinessBind bind : sBinds) {
                FileStorage fs = fileMap.get(bind.getFileId());
                if (fs != null) {
                    skuUrls.add(previewUrl(fs.getId()));
                }
            }
            sv.setImageUrls(skuUrls);

            skuVOs.add(sv);
        }
        vo.setSkus(skuVOs);
        vo.setHasStock(anyStock);
        vo.setStockStatus(anyStock ? "有现货" : "暂无现货");

        return vo;
    }

    /**
     * Compute stock availability per SKU.
     * available = sum(max(quantity - reservedQty - globalReservedQty, 0)) across all warehouses.
     * hasStock = sum > 0.
     */
    public Map<Long, Boolean> computeSkuStock(Set<Long> skuIds) {
        if (skuIds.isEmpty()) return Collections.emptyMap();
        List<Inventory> rows = inventoryMapper.selectList(
            new LambdaQueryWrapper<Inventory>().in(Inventory::getSkuId, skuIds));
        Map<Long, Boolean> result = new HashMap<>();
        for (Long sid : skuIds) result.put(sid, false);
        for (Inventory inv : rows) {
            int available = Math.max(0,
                (inv.getQuantity() == null ? 0 : inv.getQuantity())
                - (inv.getReservedQty() == null ? 0 : inv.getReservedQty())
                - (inv.getGlobalReservedQty() == null ? 0 : inv.getGlobalReservedQty()));
            if (available > 0) {
                result.put(inv.getSkuId(), true);
            }
        }
        return result;
    }

    private Set<Long> loadInStockProductIds() {
        List<Inventory> inventoryRows = inventoryMapper.selectList(
            new LambdaQueryWrapper<Inventory>()
                .apply("(quantity - IFNULL(reserved_qty, 0) - IFNULL(global_reserved_qty, 0)) > 0"));
        Set<Long> stockSkuIds = inventoryRows.stream()
            .map(Inventory::getSkuId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (stockSkuIds.isEmpty()) {
            return Collections.emptySet();
        }

        List<ProductSku> skus = productSkuMapper.selectList(
            new LambdaQueryWrapper<ProductSku>()
                .in(ProductSku::getId, stockSkuIds)
                .eq(ProductSku::getStatus, 1)
                .ne(ProductSku::getSkuType, "PLACEHOLDER")
                .eq(ProductSku::getDeleted, 0));
        return skus.stream()
            .map(ProductSku::getProductId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private PageResult<CatalogProductVO> emptyPage(CatalogPageDTO dto) {
        return PageResult.of(Collections.emptyList(), 0, dto.getSize(), dto.getCurrent());
    }

    private Map<Long, List<CatalogProductVO.ColorSizeEntry>> loadProductColors(List<Long> productIds) {
        List<ProductColorRel> rels = productColorRelMapper.selectList(
            new LambdaQueryWrapper<ProductColorRel>().in(ProductColorRel::getProductId, productIds));
        Set<Long> colorIds = rels.stream().map(ProductColorRel::getColorId).collect(Collectors.toSet());
        Map<Long, ProductColor> cmap = colorIds.isEmpty() ? Collections.emptyMap()
            : productColorMapper.selectBatchIds(colorIds).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1 && c.getDeleted() != null && c.getDeleted() == 0)
                .collect(Collectors.toMap(ProductColor::getId, c -> c));
        Map<Long, List<CatalogProductVO.ColorSizeEntry>> result = new HashMap<>();
        for (ProductColorRel rel : rels) {
            ProductColor c = cmap.get(rel.getColorId());
            if (c == null) continue;
            result.computeIfAbsent(rel.getProductId(), k -> new ArrayList<>())
                .add(new CatalogProductVO.ColorSizeEntry(c.getId(), c.getColorName(), c.getColorCode()));
        }
        return result;
    }

    private Map<Long, List<CatalogProductVO.ColorSizeEntry>> loadProductSizes(List<Long> productIds) {
        List<ProductSizeRel> rels = productSizeRelMapper.selectList(
            new LambdaQueryWrapper<ProductSizeRel>().in(ProductSizeRel::getProductId, productIds));
        Set<Long> sizeIds = rels.stream().map(ProductSizeRel::getSizeId).collect(Collectors.toSet());
        Map<Long, ProductSize> smap = sizeIds.isEmpty() ? Collections.emptyMap()
            : productSizeMapper.selectBatchIds(sizeIds).stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 1 && s.getDeleted() != null && s.getDeleted() == 0)
                .collect(Collectors.toMap(ProductSize::getId, s -> s));
        Map<Long, List<CatalogProductVO.ColorSizeEntry>> result = new HashMap<>();
        for (ProductSizeRel rel : rels) {
            ProductSize s = smap.get(rel.getSizeId());
            if (s == null) continue;
            result.computeIfAbsent(rel.getProductId(), k -> new ArrayList<>())
                .add(new CatalogProductVO.ColorSizeEntry(s.getId(), s.getSizeCode(), s.getSizeCode()));
        }
        return result;
    }

    private Map<Long, List<FileBusinessBind>> loadBindings(String businessType, Set<Long> businessIds) {
        if (businessIds.isEmpty()) return Collections.emptyMap();
        List<FileBusinessBind> binds = fileBusinessBindMapper.selectList(
            new LambdaQueryWrapper<FileBusinessBind>()
                .eq(FileBusinessBind::getBusinessType, businessType)
                .in(FileBusinessBind::getBusinessId, businessIds)
                .eq(FileBusinessBind::getDeleted, 0));
        return binds.stream().collect(Collectors.groupingBy(FileBusinessBind::getBusinessId));
    }

    private Map<Long, FileStorage> loadActiveFiles(Set<Long> fileIds) {
        if (fileIds.isEmpty()) return Collections.emptyMap();
        List<FileStorage> files = fileStorageMapper.selectList(
            new LambdaQueryWrapper<FileStorage>()
                .in(FileStorage::getId, fileIds)
                .eq(FileStorage::getStatus, 1));
        return files.stream().collect(Collectors.toMap(FileStorage::getId, f -> f));
    }

    private Long parseFileId(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        try {
            return Long.parseLong(imageUrl.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String previewUrl(Long fileId) {
        return "/api/files/" + fileId + "/preview";
    }
}

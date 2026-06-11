package com.blade.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileService;
import com.blade.product.dto.*;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductCategory;
import com.blade.product.entity.ProductColor;
import com.blade.product.entity.ProductColorRel;
import com.blade.product.entity.ProductSize;
import com.blade.product.entity.ProductSizeRel;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.*;
import com.blade.product.service.ProductService;
import com.blade.system.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductColorMapper colorMapper;
    private final ProductSizeMapper sizeMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductColorRelMapper colorRelMapper;
    private final ProductSizeRelMapper sizeRelMapper;
    private final JdbcTemplate jdbcTemplate;
    private final FileService fileService;
    private final FileBusinessBindMapper fileBusinessBindMapper;
    private final FileStorageMapper fileStorageMapper;

    @Autowired
    public ProductServiceImpl(ProductMapper productMapper,
                             ProductCategoryMapper categoryMapper,
                             ProductColorMapper colorMapper,
                             ProductSizeMapper sizeMapper,
                             ProductSkuMapper skuMapper,
                             ProductColorRelMapper colorRelMapper,
                             ProductSizeRelMapper sizeRelMapper,
                             JdbcTemplate jdbcTemplate,
                             FileService fileService,
                             FileBusinessBindMapper fileBusinessBindMapper,
                             FileStorageMapper fileStorageMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.colorMapper = colorMapper;
        this.sizeMapper = sizeMapper;
        this.skuMapper = skuMapper;
        this.colorRelMapper = colorRelMapper;
        this.sizeRelMapper = sizeRelMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.fileService = fileService;
        this.fileBusinessBindMapper = fileBusinessBindMapper;
        this.fileStorageMapper = fileStorageMapper;
    }

    @Override
    public PageResult<ProductVO> pageList(ProductPageDTO dto) {
        Page<Product> page = new Page<>(dto.getCurrent(), dto.getSize());
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        if (dto.getKeyword() != null && !dto.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(Product::getName, dto.getKeyword())
                    .or()
                    .like(Product::getProductCode, dto.getKeyword()));
        }
        if (dto.getCategoryId() != null) {
            wrapper.eq(Product::getCategoryId, dto.getCategoryId());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(Product::getStatus, dto.getStatus());
        }

        wrapper.orderByDesc(Product::getCreateTime);

        IPage<Product> result = productMapper.selectPage(page, wrapper);

        List<ProductVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public ProductVO getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }
        return convertToVO(product);
    }

    @Override
    @Transactional
    public Long create(ProductCreateDTO dto) {
        LambdaQueryWrapper<Product> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(Product::getProductCode, dto.getProductCode());
        if (productMapper.selectCount(checkWrapper) > 0) {
            throw new RuntimeException("商品编码已存在");
        }

        Product product = new Product();
        product.setProductCode(dto.getProductCode());
        product.setName(dto.getName());
        product.setCategoryId(dto.getCategoryId());
        product.setSupplierId(dto.getSupplierId());
        product.setUnit(dto.getUnit() != null ? dto.getUnit() : "件");
        product.setCostPrice(dto.getCostPrice());
        product.setWholesalePrice(dto.getWholesalePrice());
        product.setWeight(dto.getWeight());
        product.setDescription(dto.getDescription());
        product.setImageUrl(dto.getImageUrl());
        product.setRemark(dto.getRemark());
        product.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        product.setTenantId(TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L);

        productMapper.insert(product);
        fileService.bindFilesFromJson("product", product.getId(), product.getImageUrl());
        syncMainImageBinding(product, product.getTenantId());

        if (dto.getColorIds() != null && !dto.getColorIds().isEmpty()) {
            saveColorRelations(product.getId(), dto.getColorIds());
        }

        if (dto.getSizeIds() != null && !dto.getSizeIds().isEmpty()) {
            saveSizeRelations(product.getId(), dto.getSizeIds());
            if (dto.getColorIds() != null && !dto.getColorIds().isEmpty()) {
                autoGenerateSkus(product.getId(), dto.getColorIds(), dto.getSizeIds(), dto.getWholesalePrice());
            }
        }

        return product.getId();
    }

    @Override
    @Transactional
    public void update(ProductUpdateDTO dto) {
        Product product = productMapper.selectById(dto.getId());
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }

        if (dto.getName() != null) {
            product.setName(dto.getName());
        }
        if (dto.getCategoryId() != null) {
            product.setCategoryId(dto.getCategoryId());
        }
        if (dto.getSupplierId() != null) {
            product.setSupplierId(dto.getSupplierId());
        }
        if (dto.getUnit() != null) {
            product.setUnit(dto.getUnit());
        }
        if (dto.getCostPrice() != null) {
            product.setCostPrice(dto.getCostPrice());
        }
        if (dto.getWholesalePrice() != null) {
            product.setWholesalePrice(dto.getWholesalePrice());
        }
        if (dto.getWeight() != null) {
            product.setWeight(dto.getWeight());
        }
        if (dto.getDescription() != null) {
            product.setDescription(dto.getDescription());
        }
        if (dto.getImageUrl() != null) {
            product.setImageUrl(dto.getImageUrl());
        }
        if (dto.getRemark() != null) {
            product.setRemark(dto.getRemark());
        }
        if (dto.getStatus() != null) {
            product.setStatus(dto.getStatus());
        }

        productMapper.updateById(product);
        fileService.bindFilesFromJson("product", product.getId(), product.getImageUrl());
        syncMainImageBinding(product, product.getTenantId());

        if (dto.getColorIds() != null) {
            colorRelMapper.deleteByProductId(dto.getId());
            saveColorRelations(dto.getId(), dto.getColorIds());
        }

        if (dto.getSizeIds() != null) {
            sizeRelMapper.deleteByProductId(dto.getId());
            saveSizeRelations(dto.getId(), dto.getSizeIds());
        }

        if (dto.getColorIds() != null || dto.getSizeIds() != null) {
            syncProductSkus(product);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }

        LambdaQueryWrapper<ProductSku> skuWrapper = new LambdaQueryWrapper<>();
        skuWrapper.eq(ProductSku::getProductId, id);
        skuMapper.delete(skuWrapper);

        colorRelMapper.deleteByProductId(id);
        sizeRelMapper.deleteByProductId(id);
        productMapper.deleteById(id);
    }

    @Override
    public List<ProductVO.ColorVO> listAllColors() {
        LambdaQueryWrapper<ProductColor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductColor::getStatus, 1).orderByAsc(ProductColor::getId);
        List<ProductColor> colors = colorMapper.selectList(wrapper);

        return colors.stream().map(color -> {
            ProductVO.ColorVO vo = new ProductVO.ColorVO();
            vo.setId(color.getId());
            vo.setColorCode(color.getColorCode());
            vo.setColorName(color.getColorName());
            vo.setStatus(color.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public Long createColor(ColorCreateDTO dto) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        ProductColor color = new ProductColor();
        color.setColorCode(dto.getColorCode());
        color.setColorName(dto.getColorName());
        color.setTenantId(tenantId);
        color.setDeleted(0);
        color.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        colorMapper.insert(color);
        return color.getId();
    }

    @Override
    public void updateColor(ColorUpdateDTO dto) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        ProductColor color = colorMapper.selectById(dto.getId());
        if (color == null) {
            throw new RuntimeException("颜色不存在");
        }
        color.setColorCode(dto.getColorCode());
        color.setColorName(dto.getColorName());
        if (dto.getStatus() != null) {
            color.setStatus(dto.getStatus());
        }
        colorMapper.updateById(color);
    }

    @Override
    public void deleteColor(Long id) {
        colorMapper.deleteById(id);
    }

    @Override
    public List<ProductVO.SizeVO> listAllSizes() {
        LambdaQueryWrapper<ProductSize> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductSize::getStatus, 1).orderByAsc(ProductSize::getSort);
        List<ProductSize> sizes = sizeMapper.selectList(wrapper);

        return sizes.stream().map(size -> {
            ProductVO.SizeVO vo = new ProductVO.SizeVO();
            vo.setId(size.getId());
            vo.setSizeCode(size.getSizeCode());
            vo.setSort(size.getSort());
            vo.setStatus(size.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public Long createSize(SizeCreateDTO dto) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        // 使用原生 SQL 检查是否有软删除的同名尺码（绕过 MyBatis-Plus 逻辑删除过滤器）
        String checkSql = "SELECT id, size_code, sort, tenant_id, deleted, create_time FROM product_size WHERE size_code = ? AND tenant_id = ? AND deleted = 1";
        List<ProductSize> deletedList = jdbcTemplate.query(checkSql, (rs, rowNum) -> {
            ProductSize s = new ProductSize();
            s.setId(rs.getLong("id"));
            s.setSizeCode(rs.getString("size_code"));
            s.setSort(rs.getInt("sort"));
            s.setTenantId(rs.getLong("tenant_id"));
            s.setDeleted(rs.getInt("deleted"));
            return s;
        }, dto.getSizeCode(), tenantId);

        if (!deletedList.isEmpty()) {
            // 恢复软删除的记录（使用原生 SQL 绕过所有过滤器）
            Long deletedId = deletedList.get(0).getId();
            int sortValue = dto.getSort() != null ? dto.getSort() : 0;
            String updateSql = "UPDATE product_size SET deleted = 0, sort = ? WHERE id = ?";
            jdbcTemplate.update(updateSql, sortValue, deletedId);
            return deletedId;
        }

        ProductSize size = new ProductSize();
        size.setSizeCode(dto.getSizeCode());
        size.setSort(dto.getSort() != null ? dto.getSort() : 0);
        size.setTenantId(tenantId);
        size.setDeleted(0);
        size.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        sizeMapper.insert(size);
        return size.getId();
    }

    @Override
    public void updateSize(SizeUpdateDTO dto) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        ProductSize size = sizeMapper.selectById(dto.getId());
        if (size == null) {
            throw new RuntimeException("尺码不存在");
        }
        size.setSizeCode(dto.getSizeCode());
        if (dto.getSort() != null) {
            size.setSort(dto.getSort());
        }
        if (dto.getStatus() != null) {
            size.setStatus(dto.getStatus());
        }
        sizeMapper.updateById(size);
    }

    @Override
    public void deleteSize(Long id) {
        sizeMapper.deleteById(id);
    }

    @Override
    public List<SkuVO> listAllSkus() {
        return skuMapper.selectAllSkuList();
    }

    // ==================== BE-1005: 商品/SKU 图片绑定 ====================

    @Override
    @Transactional
    public void bindFiles(Long productId, ProductFileBindingDTO dto) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        // 1. 验证商品存在且属于当前租户
        LambdaQueryWrapper<Product> productWrapper = new LambdaQueryWrapper<>();
        productWrapper.eq(Product::getId, productId);
        productWrapper.eq(Product::getTenantId, tenantId);
        Product product = productMapper.selectOne(productWrapper);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }

        // 2. 收集所有引用的文件 ID
        Set<Long> allFileIds = new HashSet<>();
        if (dto.getMainFileId() != null) {
            allFileIds.add(dto.getMainFileId());
        }
        if (dto.getGalleryFileIds() != null) {
            allFileIds.addAll(dto.getGalleryFileIds());
        }
        if (dto.getSkuImageBindings() != null) {
            for (SkuImageBindingDTO skuBinding : dto.getSkuImageBindings()) {
                if (skuBinding.getSkuId() == null) {
                    throw new RuntimeException("SKU ID不能为空");
                }
                if (skuBinding.getFileIds() != null) {
                    allFileIds.addAll(skuBinding.getFileIds());
                }
            }
        }

        // 3. 验证所有文件存在且属于当前租户且 status=1
        if (!allFileIds.isEmpty()) {
            Long fileCount = fileStorageMapper.selectCount(
                    new LambdaQueryWrapper<FileStorage>()
                            .in(FileStorage::getId, allFileIds)
                            .eq(FileStorage::getTenantId, tenantId)
                            .eq(FileStorage::getStatus, 1));
            if (fileCount != allFileIds.size()) {
                throw new RuntimeException("部分文件不存在");
            }
        }

        // 4. 处理主图
        if (dto.getMainFileId() != null) {
            softDeleteBindings("product", productId, "main", tenantId);
            insertBinding(dto.getMainFileId(), "product", productId, "main", 1, 0, tenantId);
            product.setImageUrl(String.valueOf(dto.getMainFileId()));
            productMapper.updateById(product);
        }

        // 5. 处理图集
        if (dto.getGalleryFileIds() != null) {
            softDeleteBindings("product", productId, "gallery", tenantId);
            int sort = 0;
            for (Long fileId : dto.getGalleryFileIds()) {
                insertBinding(fileId, "product", productId, "gallery", 0, sort, tenantId);
                sort++;
            }
        }

        // 6. 处理 SKU 图片
        if (dto.getSkuImageBindings() != null) {
            for (SkuImageBindingDTO skuBinding : dto.getSkuImageBindings()) {
                Long skuId = skuBinding.getSkuId();
                // 验证 SKU 属于当前商品、当前租户、status=1、deleted=0
                LambdaQueryWrapper<ProductSku> skuWrapper = new LambdaQueryWrapper<>();
                skuWrapper.eq(ProductSku::getId, skuId);
                skuWrapper.eq(ProductSku::getProductId, productId);
                skuWrapper.eq(ProductSku::getTenantId, tenantId);
                skuWrapper.eq(ProductSku::getStatus, 1);
                skuWrapper.eq(ProductSku::getDeleted, 0);
                if (skuMapper.selectOne(skuWrapper) == null) {
                    throw new RuntimeException("SKU 不存在: " + skuId);
                }

                softDeleteBindings("sku", skuId, "sku_image", tenantId);
                if (skuBinding.getFileIds() != null) {
                    int sort = 0;
                    for (Long fileId : skuBinding.getFileIds()) {
                        insertBinding(fileId, "sku", skuId, "sku_image", 0, sort, tenantId);
                        sort++;
                    }
                }
            }
        }
    }

    // ==================== BE-1005 内部辅助方法 ====================

    private void softDeleteBindings(String businessType, Long businessId, String bindRole, Long tenantId) {
        LambdaUpdateWrapper<FileBusinessBind> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FileBusinessBind::getBusinessType, businessType);
        wrapper.eq(FileBusinessBind::getBusinessId, businessId);
        wrapper.eq(FileBusinessBind::getBindRole, bindRole);
        wrapper.eq(FileBusinessBind::getTenantId, tenantId);
        wrapper.eq(FileBusinessBind::getDeleted, 0);
        wrapper.set(FileBusinessBind::getDeleted, 1);
        fileBusinessBindMapper.update(null, wrapper);
    }

    private void insertBinding(Long fileId, String businessType, Long businessId,
                                String bindRole, int isPrimary, int sort, Long tenantId) {
        FileBusinessBind bind = new FileBusinessBind();
        bind.setFileId(fileId);
        bind.setBusinessType(businessType);
        bind.setBusinessId(businessId);
        bind.setBindRole(bindRole);
        bind.setIsPrimary(isPrimary);
        bind.setSort(sort);
        bind.setTenantId(tenantId);
        bind.setCreateBy(getCurrentUserId());
        bind.setDeleted(0);
        fileBusinessBindMapper.insert(bind);
    }

    /**
     * 同步 product.imageUrl 主图到 file_business_bind
     * 仅当 imageUrl 是纯数字 fileId 时操作；历史 URL/blob 忽略
     */
    private void syncMainImageBinding(Product product, Long tenantId) {
        String imageUrl = product.getImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) return;
        if (!imageUrl.matches("\\d+")) return; // 非纯数字 fileId，忽略

        Long fileId = Long.valueOf(imageUrl);

        // 验证文件存在
        Long fileCount = fileStorageMapper.selectCount(
                new LambdaQueryWrapper<FileStorage>()
                        .eq(FileStorage::getId, fileId)
                        .eq(FileStorage::getTenantId, tenantId)
                        .eq(FileStorage::getStatus, 1));
        if (fileCount == 0) return; // 文件不存在，忽略

        softDeleteBindings("product", product.getId(), "main", tenantId);
        insertBinding(fileId, "product", product.getId(), "main", 1, 0, tenantId);
    }

    private void saveColorRelations(Long productId, List<Long> colorIds) {
        for (Long colorId : colorIds) {
            ProductColorRel rel = new ProductColorRel();
            rel.setProductId(productId);
            rel.setColorId(colorId);
            colorRelMapper.insert(rel);
        }
    }

    private void saveSizeRelations(Long productId, List<Long> sizeIds) {
        for (Long sizeId : sizeIds) {
            ProductSizeRel rel = new ProductSizeRel();
            rel.setProductId(productId);
            rel.setSizeId(sizeId);
            sizeRelMapper.insert(rel);
        }
    }

    private void autoGenerateSkus(Long productId, List<Long> colorIds, List<Long> sizeIds, java.math.BigDecimal price) {
        for (Long colorId : colorIds) {
            for (Long sizeId : sizeIds) {
                ProductColor color = colorMapper.selectById(colorId);
                ProductSize size = sizeMapper.selectById(sizeId);
                Product product = productMapper.selectById(productId);

                ProductSku sku = new ProductSku();
                sku.setProductId(productId);
                sku.setColorId(colorId);
                sku.setSizeId(sizeId);
                sku.setSkuCode(generateSkuCode(product.getProductCode(), color.getColorCode(), size.getSizeCode()));
                sku.setPrice(defaultAmount(price));
                sku.setCostPrice(defaultAmount(product.getCostPrice()));
                sku.setStatus(1);
                sku.setTenantId(product.getTenantId());

                skuMapper.insert(sku);
            }
        }
    }

    private void syncProductSkus(Product product) {
        List<ProductColor> colors = colorRelMapper.selectByProductId(product.getId());
        List<ProductSize> sizes = sizeRelMapper.selectByProductId(product.getId());
        if (colors == null || colors.isEmpty() || sizes == null || sizes.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<ProductSku> skuWrapper = new LambdaQueryWrapper<>();
        skuWrapper.eq(ProductSku::getProductId, product.getId());
        List<ProductSku> existingSkus = skuMapper.selectList(skuWrapper);

        Map<String, ProductSku> existingByCode = new HashMap<>();
        for (ProductSku sku : existingSkus) {
            existingByCode.put(sku.getSkuCode(), sku);
        }

        Set<String> targetSkuCodes = new HashSet<>();
        BigDecimal targetPrice = defaultAmount(product.getWholesalePrice());
        BigDecimal targetCostPrice = defaultAmount(product.getCostPrice());

        for (ProductColor color : colors) {
            for (ProductSize size : sizes) {
                String skuCode = generateSkuCode(product.getProductCode(), color.getColorCode(), size.getSizeCode());
                targetSkuCodes.add(skuCode);

                ProductSku existingSku = existingByCode.get(skuCode);
                if (existingSku != null) {
                    boolean changed = false;
                    if (!Objects.equals(existingSku.getColorId(), color.getId())) {
                        existingSku.setColorId(color.getId());
                        changed = true;
                    }
                    if (!Objects.equals(existingSku.getSizeId(), size.getId())) {
                        existingSku.setSizeId(size.getId());
                        changed = true;
                    }
                    if (existingSku.getPrice() == null || existingSku.getPrice().compareTo(targetPrice) != 0) {
                        existingSku.setPrice(targetPrice);
                        changed = true;
                    }
                    if (existingSku.getCostPrice() == null || existingSku.getCostPrice().compareTo(targetCostPrice) != 0) {
                        existingSku.setCostPrice(targetCostPrice);
                        changed = true;
                    }
                    if (!Objects.equals(existingSku.getStatus(), product.getStatus())) {
                        existingSku.setStatus(product.getStatus());
                        changed = true;
                    }
                    if (changed) {
                        skuMapper.updateById(existingSku);
                    }
                    continue;
                }

                restoreOrCreateSku(product, color, size, skuCode, targetPrice, targetCostPrice);
            }
        }

        for (ProductSku existingSku : existingSkus) {
            if (!targetSkuCodes.contains(existingSku.getSkuCode())) {
                skuMapper.deleteById(existingSku.getId());
            }
        }
    }

    private void restoreOrCreateSku(Product product, ProductColor color, ProductSize size, String skuCode,
                                    BigDecimal price, BigDecimal costPrice) {
        String checkSql = "SELECT id FROM product_sku WHERE sku_code = ? AND tenant_id = ? AND deleted = 1";
        List<Long> deletedIds = jdbcTemplate.query(checkSql, (rs, rowNum) -> rs.getLong("id"), skuCode, product.getTenantId());
        if (!deletedIds.isEmpty()) {
            String restoreSql = "UPDATE product_sku SET product_id = ?, color_id = ?, size_id = ?, price = ?, cost_price = ?, status = ?, deleted = 0 WHERE id = ?";
            jdbcTemplate.update(restoreSql, product.getId(), color.getId(), size.getId(), price, costPrice, product.getStatus(), deletedIds.get(0));
            return;
        }

        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setColorId(color.getId());
        sku.setSizeId(size.getId());
        sku.setSkuCode(skuCode);
        sku.setPrice(price);
        sku.setCostPrice(costPrice);
        sku.setStatus(product.getStatus());
        sku.setTenantId(product.getTenantId());
        skuMapper.insert(sku);
    }

    private String generateSkuCode(String productCode, String colorCode, String sizeCode) {
        return productCode + "-" + colorCode + "-" + sizeCode;
    }

    private ProductVO convertToVO(Product product) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setProductCode(product.getProductCode());
        vo.setName(product.getName());
        vo.setCategoryId(product.getCategoryId());
        vo.setSupplierId(product.getSupplierId());
        vo.setUnit(product.getUnit());
        vo.setCostPrice(product.getCostPrice());
        vo.setWholesalePrice(product.getWholesalePrice());
        vo.setWeight(product.getWeight());
        vo.setDescription(product.getDescription());
        vo.setImageUrl(product.getImageUrl());
        vo.setRemark(product.getRemark());
        vo.setStatus(product.getStatus());
        vo.setCreateTime(product.getCreateTime());
        vo.setUpdateTime(product.getUpdateTime());

        if (product.getCategoryId() != null) {
            ProductCategory category = categoryMapper.selectById(product.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getCategoryName());
            }
        }

        // 供应商名称（等供应商模块开发后完善）
        // if (product.getSupplierId() != null) {
        //     Supplier supplier = supplierMapper.selectById(product.getSupplierId());
        //     if (supplier != null) {
        //         vo.setSupplierName(supplier.getSupplierName());
        //     }
        // }

        List<ProductColor> colors = colorRelMapper.selectByProductId(product.getId());
        if (colors != null && !colors.isEmpty()) {
            List<ProductVO.ColorVO> colorVOList = colors.stream().map(c -> {
                ProductVO.ColorVO colorVO = new ProductVO.ColorVO();
                colorVO.setId(c.getId());
                colorVO.setColorCode(c.getColorCode());
                colorVO.setColorName(c.getColorName());
                return colorVO;
            }).collect(Collectors.toList());
            vo.setColors(colorVOList);
        }

        List<ProductSize> sizes = sizeRelMapper.selectByProductId(product.getId());
        if (sizes != null && !sizes.isEmpty()) {
            List<ProductVO.SizeVO> sizeVOList = sizes.stream().map(s -> {
                ProductVO.SizeVO sizeVO = new ProductVO.SizeVO();
                sizeVO.setId(s.getId());
                sizeVO.setSizeCode(s.getSizeCode());
                sizeVO.setSort(s.getSort());
                return sizeVO;
            }).collect(Collectors.toList());
            vo.setSizes(sizeVOList);
        }

        // 填充SKU列表
        LambdaQueryWrapper<ProductSku> skuWrapper = new LambdaQueryWrapper<>();
        skuWrapper.eq(ProductSku::getProductId, product.getId());
        skuWrapper.eq(ProductSku::getStatus, 1);
        List<ProductSku> skus = skuMapper.selectList(skuWrapper);
        if (skus != null && !skus.isEmpty()) {
            List<ProductVO.SkuVO> skuVOList = skus.stream().map(sku -> {
                ProductVO.SkuVO skuVO = new ProductVO.SkuVO();
                skuVO.setId(sku.getId());
                skuVO.setSkuCode(sku.getSkuCode());
                skuVO.setColorId(sku.getColorId());
                skuVO.setSizeId(sku.getSizeId());
                skuVO.setPrice(sku.getPrice());
                skuVO.setCostPrice(hasPositiveAmount(sku.getCostPrice()) ? sku.getCostPrice() : product.getCostPrice());
                skuVO.setBarCode(sku.getBarCode());
                skuVO.setStatus(sku.getStatus());
                // 查询颜色名称
                if (sku.getColorId() != null) {
                    ProductColor color = colorMapper.selectById(sku.getColorId());
                    if (color != null) {
                        skuVO.setColorName(color.getColorName());
                    }
                }
                // 查询尺码名称
                if (sku.getSizeId() != null) {
                    ProductSize size = sizeMapper.selectById(sku.getSizeId());
                    if (size != null) {
                        skuVO.setSizeName(size.getSizeCode());
                    }
                }
                return skuVO;
            }).collect(Collectors.toList());
            vo.setSkus(skuVOList);
        }

        return vo;
    }

    private boolean hasPositiveAmount(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return 1L;
    }
}

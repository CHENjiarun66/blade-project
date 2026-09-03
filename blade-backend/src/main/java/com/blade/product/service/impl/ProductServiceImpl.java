package com.blade.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileService;
import com.blade.inventory.entity.Inventory;
import com.blade.inventory.mapper.InventoryMapper;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.product.dto.*;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductCategory;
import com.blade.product.entity.ProductColor;
import com.blade.product.entity.ProductColorRel;
import com.blade.product.entity.ProductSize;
import com.blade.product.entity.ProductSizeRel;
import com.blade.product.entity.ProductSku;
import com.blade.product.enums.ProductSkuType;
import com.blade.product.mapper.*;
import com.blade.product.service.ProductService;
import com.blade.product.service.ProductSkuSemantics;
import com.blade.system.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private static final String COLOR_UNSPECIFIED = "UNSPECIFIED";
    private static final String SIZE_UNSPECIFIED = "UNSPEC";
    private static final String ATTRIBUTE_NOT_APPLICABLE = "NA";

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
    private final OrderItemMapper orderItemMapper;
    private final InventoryMapper inventoryMapper;

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
                             FileStorageMapper fileStorageMapper,
                             OrderItemMapper orderItemMapper,
                             InventoryMapper inventoryMapper) {
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
        this.orderItemMapper = orderItemMapper;
        this.inventoryMapper = inventoryMapper;
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
        }

        syncProductSkus(product);

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

        boolean clearedOnlyOneDimension = (dto.getColorIds() != null && dto.getColorIds().isEmpty() && dto.getSizeIds() == null)
                || (dto.getSizeIds() != null && dto.getSizeIds().isEmpty() && dto.getColorIds() == null);
        if (clearedOnlyOneDimension) {
            disableActiveSkus(product.getId(), product.getTenantId());
        } else if (dto.getColorIds() != null || dto.getSizeIds() != null
                || dto.getWholesalePrice() != null || dto.getCostPrice() != null || dto.getStatus() != null) {
            syncProductSkus(product);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        // tenant-aware + deleted=0 查找商品
        LambdaQueryWrapper<Product> productWrapper = new LambdaQueryWrapper<>();
        productWrapper.eq(Product::getId, id);
        productWrapper.eq(Product::getTenantId, tenantId);
        productWrapper.eq(Product::getDeleted, 0);
        Product product = productMapper.selectOne(productWrapper);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }

        // BE-1014: 删除引用保护
        // 1. 获取商品的所有未删除 SKU（租户+productId+deleted=0）
        LambdaQueryWrapper<ProductSku> skuWrapper = new LambdaQueryWrapper<>();
        skuWrapper.eq(ProductSku::getProductId, id);
        skuWrapper.eq(ProductSku::getTenantId, tenantId);
        skuWrapper.eq(ProductSku::getDeleted, 0);
        List<ProductSku> skus = skuMapper.selectList(skuWrapper);
        List<Long> skuIds = skus.stream().map(ProductSku::getId).collect(Collectors.toList());

        // 2. 检查订单明细引用（含租户过滤）
        if (!skuIds.isEmpty()) {
            Long orderItemCount = orderItemMapper.selectCount(
                    new LambdaQueryWrapper<OrderItem>()
                            .in(OrderItem::getSkuId, skuIds)
                            .eq(OrderItem::getTenantId, tenantId));
            if (orderItemCount > 0) {
                throw new RuntimeException("该商品下有 " + orderItemCount + " 条订单明细记录，无法删除。建议改为禁用。");
            }
        }

        // 3. 检查库存记录（含租户过滤）
        if (!skuIds.isEmpty()) {
            Long inventoryCount = inventoryMapper.selectCount(
                    new QueryWrapper<Inventory>()
                            .in("sku_id", skuIds)
                            .eq("tenant_id", tenantId));
            if (inventoryCount > 0) {
                throw new RuntimeException("该商品下有 " + inventoryCount + " 条库存记录，无法删除。建议改为禁用。");
            }
        }

        // 4. 检查有效文件绑定：按 businessType 分离 product 和 sku 级绑定
        LambdaQueryWrapper<FileBusinessBind> bindingWrapper = new LambdaQueryWrapper<>();
        bindingWrapper.eq(FileBusinessBind::getTenantId, tenantId)
                .eq(FileBusinessBind::getDeleted, 0);
        if (!skuIds.isEmpty()) {
            bindingWrapper.and(w -> w
                    .and(wp -> wp.eq(FileBusinessBind::getBusinessType, "product")
                            .eq(FileBusinessBind::getBusinessId, id))
                    .or(ws -> ws.eq(FileBusinessBind::getBusinessType, "sku")
                            .in(FileBusinessBind::getBusinessId, skuIds)));
        } else {
            bindingWrapper.eq(FileBusinessBind::getBusinessType, "product")
                    .eq(FileBusinessBind::getBusinessId, id);
        }
        Long bindingCount = fileBusinessBindMapper.selectCount(bindingWrapper);
        if (bindingCount > 0) {
            throw new RuntimeException("该商品下有 " + bindingCount + " 条有效文件绑定，无法删除。建议改为禁用。");
        }

        // 无引用：软删除（禁用 SKU，软删除商品）
        if (!skuIds.isEmpty()) {
            LambdaUpdateWrapper<ProductSku> disableWrapper = new LambdaUpdateWrapper<>();
            disableWrapper.in(ProductSku::getId, skuIds)
                    .eq(ProductSku::getTenantId, tenantId)
                    .eq(ProductSku::getDeleted, 0)
                    .set(ProductSku::getStatus, 0);
            skuMapper.update(null, disableWrapper);
        }

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
    @Transactional
    public void deleteColor(Long id) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        // 获取当前租户下所有未删除的商品 ID（ProductColorRel 无 tenantId，通过 Product 关联防跨租户）
        LambdaQueryWrapper<Product> productWrapper = new LambdaQueryWrapper<>();
        productWrapper.select(Product::getId);
        productWrapper.eq(Product::getTenantId, tenantId);
        productWrapper.eq(Product::getDeleted, 0);
        List<Product> activeProducts = productMapper.selectList(productWrapper);
        List<Long> activeProductIds = activeProducts.stream().map(Product::getId).collect(Collectors.toList());

        if (!activeProductIds.isEmpty()) {
            LambdaQueryWrapper<ProductColorRel> relWrapper = new LambdaQueryWrapper<>();
            relWrapper.eq(ProductColorRel::getColorId, id);
            relWrapper.in(ProductColorRel::getProductId, activeProductIds);
            Long relCount = colorRelMapper.selectCount(relWrapper);
            if (relCount > 0) {
                throw new RuntimeException("该颜色被 " + relCount + " 个商品使用，无法删除。建议改为禁用。");
            }
        }
        // 无活跃商品 → 计数为 0，允许删除

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
    @Transactional
    public void deleteSize(Long id) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        // 获取当前租户下所有未删除的商品 ID（ProductSizeRel 无 tenantId，通过 Product 关联防跨租户）
        LambdaQueryWrapper<Product> productWrapper = new LambdaQueryWrapper<>();
        productWrapper.select(Product::getId);
        productWrapper.eq(Product::getTenantId, tenantId);
        productWrapper.eq(Product::getDeleted, 0);
        List<Product> activeProducts = productMapper.selectList(productWrapper);
        List<Long> activeProductIds = activeProducts.stream().map(Product::getId).collect(Collectors.toList());

        if (!activeProductIds.isEmpty()) {
            LambdaQueryWrapper<ProductSizeRel> relWrapper = new LambdaQueryWrapper<>();
            relWrapper.eq(ProductSizeRel::getSizeId, id);
            relWrapper.in(ProductSizeRel::getProductId, activeProductIds);
            Long relCount = sizeRelMapper.selectCount(relWrapper);
            if (relCount > 0) {
                throw new RuntimeException("该尺码被 " + relCount + " 个商品使用，无法删除。建议改为禁用。");
            }
        }
        // 无活跃商品 → 计数为 0，允许删除

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

    // ==================== BE-1013: 商品素材查询 ====================

    @Override
    public ProductFileBindingsVO getFileBindings(Long productId) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        // 验证商品存在且属于当前租户
        LambdaQueryWrapper<Product> productWrapper = new LambdaQueryWrapper<>();
        productWrapper.eq(Product::getId, productId);
        productWrapper.eq(Product::getTenantId, tenantId);
        productWrapper.eq(Product::getDeleted, 0);
        Product product = productMapper.selectOne(productWrapper);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }

        // 获取商品的所有 SKU ID
        LambdaQueryWrapper<ProductSku> skuWrapper = new LambdaQueryWrapper<>();
        skuWrapper.eq(ProductSku::getProductId, productId);
        skuWrapper.eq(ProductSku::getTenantId, tenantId);
        skuWrapper.eq(ProductSku::getDeleted, 0);
        List<ProductSku> skus = skuMapper.selectList(skuWrapper);
        List<Long> skuIds = skus.stream().map(ProductSku::getId).collect(Collectors.toList());

        // 构建 SKU ID -> (colorName, sizeName, skuCode) 映射
        Map<Long, ProductSku> skuMap = skus.stream()
                .collect(Collectors.toMap(ProductSku::getId, s -> s));
        // 填充颜色名和尺码名
        Map<Long, String> colorNameMap = new HashMap<>();
        Map<Long, String> sizeNameMap = new HashMap<>();
        for (ProductSku sku : skus) {
            if (sku.getColorId() != null && !colorNameMap.containsKey(sku.getColorId())) {
                ProductColor color = colorMapper.selectById(sku.getColorId());
                colorNameMap.put(sku.getColorId(), color != null ? color.getColorName() : null);
            }
            if (sku.getSizeId() != null && !sizeNameMap.containsKey(sku.getSizeId())) {
                ProductSize size = sizeMapper.selectById(sku.getSizeId());
                sizeNameMap.put(sku.getSizeId(), size != null ? size.getSizeCode() : null);
            }
        }

        // 查询所有相关绑定：按 businessType 分离，避免业务 ID 碰撞
        LambdaQueryWrapper<FileBusinessBind> bindWrapper = new LambdaQueryWrapper<>();
        bindWrapper.eq(FileBusinessBind::getTenantId, tenantId)
                .eq(FileBusinessBind::getDeleted, 0);
        if (!skuIds.isEmpty()) {
            bindWrapper.and(w -> w
                    .and(wp -> wp.eq(FileBusinessBind::getBusinessType, "product")
                            .eq(FileBusinessBind::getBusinessId, productId))
                    .or(ws -> ws.eq(FileBusinessBind::getBusinessType, "sku")
                            .in(FileBusinessBind::getBusinessId, skuIds)));
        } else {
            bindWrapper.eq(FileBusinessBind::getBusinessType, "product")
                    .eq(FileBusinessBind::getBusinessId, productId);
        }
        bindWrapper.orderByAsc(FileBusinessBind::getSort);
        List<FileBusinessBind> allBinds = fileBusinessBindMapper.selectList(bindWrapper);

        // 收集所有 fileId，查询有效文件
        Set<Long> fileIds = allBinds.stream().map(FileBusinessBind::getFileId).collect(Collectors.toSet());
        final Map<Long, FileStorage> fileMap;
        if (!fileIds.isEmpty()) {
            LambdaQueryWrapper<FileStorage> fileWrapper = new LambdaQueryWrapper<>();
            fileWrapper.in(FileStorage::getId, fileIds)
                    .eq(FileStorage::getTenantId, tenantId)
                    .eq(FileStorage::getStatus, 1);
            List<FileStorage> files = fileStorageMapper.selectList(fileWrapper);
            fileMap = files.stream().collect(Collectors.toMap(FileStorage::getId, f -> f));
        } else {
            fileMap = Collections.emptyMap();
        }

        ProductFileBindingsVO vo = new ProductFileBindingsVO();

        // 分组：product 绑定
        List<FileBusinessBind> productBinds = allBinds.stream()
                .filter(b -> "product".equals(b.getBusinessType()) && b.getBusinessId().equals(productId))
                .collect(Collectors.toList());

        // 主图（fileId 不在 fileMap 的脏绑定会被过滤）
        List<FileBusinessBind> mainBinds = productBinds.stream()
                .filter(b -> "main".equals(b.getBindRole()))
                .collect(Collectors.toList());
        if (!mainBinds.isEmpty()) {
            ProductFileBindingsVO.FileBindingItem mainItem = toBindingItem(mainBinds.get(0), fileMap);
            if (mainItem != null) {
                vo.setMain(mainItem);
            }
        }

        // 图集 — 始终设为列表（前端期望 [] 而非 null），过滤脏 fileId
        List<FileBusinessBind> galleryBinds = productBinds.stream()
                .filter(b -> "gallery".equals(b.getBindRole()))
                .collect(Collectors.toList());
        vo.setGallery(galleryBinds.stream()
                .map(b -> toBindingItem(b, fileMap))
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));

        // SKU 图片分组 — 始终设为列表，过滤脏 fileId
        List<FileBusinessBind> skuBinds = allBinds.stream()
                .filter(b -> "sku".equals(b.getBusinessType()) && "sku_image".equals(b.getBindRole()))
                .collect(Collectors.toList());
        Map<Long, List<FileBusinessBind>> bindsBySkuId = skuBinds.stream()
                .collect(Collectors.groupingBy(FileBusinessBind::getBusinessId));
        List<ProductFileBindingsVO.SkuImageGroup> skuGroups = new ArrayList<>();
        for (Map.Entry<Long, List<FileBusinessBind>> entry : bindsBySkuId.entrySet()) {
            Long skuId = entry.getKey();
            ProductFileBindingsVO.SkuImageGroup group = new ProductFileBindingsVO.SkuImageGroup();
            group.setSkuId(skuId);
            ProductSku sku = skuMap.get(skuId);
            if (sku != null) {
                group.setSkuCode(sku.getSkuCode());
                group.setColorName(colorNameMap.getOrDefault(sku.getColorId(), null));
                group.setSizeName(sizeNameMap.getOrDefault(sku.getSizeId(), null));
            }
            group.setFiles(entry.getValue().stream()
                    .map(b -> toBindingItem(b, fileMap))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            skuGroups.add(group);
        }
        vo.setSkuImages(skuGroups);

        return vo;
    }

    private ProductFileBindingsVO.FileBindingItem toBindingItem(FileBusinessBind bind, Map<Long, FileStorage> fileMap) {
        // 过滤脏 fileId：文件不存在或 status != 1 的绑定不返回
        if (!fileMap.containsKey(bind.getFileId())) {
            return null;
        }
        ProductFileBindingsVO.FileBindingItem item = new ProductFileBindingsVO.FileBindingItem();
        item.setFileId(bind.getFileId());
        item.setSort(bind.getSort());
        item.setIsPrimary(bind.getIsPrimary());
        // 构建预览 URL：统一使用 /api/files/{fileId}/preview
        item.setPreviewUrl("/api/files/" + bind.getFileId() + "/preview");
        return item;
    }

    // ==================== BE-1014: 单个 SKU 更新 ====================

    @Override
    @Transactional
    public void updateSku(SkuUpdateDTO dto) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        // 查询 SKU，验证租户归属和未删除
        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductSku::getId, dto.getId());
        wrapper.eq(ProductSku::getTenantId, tenantId);
        wrapper.eq(ProductSku::getDeleted, 0);
        ProductSku sku = skuMapper.selectOne(wrapper);
        if (sku == null) {
            throw new RuntimeException("SKU 不存在");
        }
        boolean historicalDefault = ProductSkuSemantics.isDefault(sku)
                && ProductSkuSemantics.findProductsWithActiveVariants(skuMapper, List.of(sku))
                        .contains(sku.getProductId());
        if (ProductSkuSemantics.isPlaceholder(sku) || historicalDefault) {
            throw BusinessException.of(400, "整款占位或历史无规格 SKU 由系统维护，不能直接修改");
        }

        boolean changed = false;
        if (dto.getPrice() != null) {
            sku.setPrice(dto.getPrice());
            changed = true;
        }
        if (dto.getCostPrice() != null) {
            sku.setCostPrice(dto.getCostPrice());
            changed = true;
        }
        if (dto.getBarCode() != null) {
            sku.setBarCode(dto.getBarCode());
            changed = true;
        }
        if (dto.getStatus() != null) {
            sku.setStatus(dto.getStatus());
            changed = true;
        }

        if (!changed) {
            throw new RuntimeException("没有需要更新的字段");
        }

        skuMapper.updateById(sku);
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

    private void syncProductSkus(Product product) {
        Long tenantId = product.getTenantId();

        List<ProductColor> selectedColors = colorRelMapper.selectByProductId(product.getId());
        List<ProductSize> selectedSizes = sizeRelMapper.selectByProductId(product.getId());
        boolean hasColors = selectedColors != null && !selectedColors.isEmpty();
        boolean hasSizes = selectedSizes != null && !selectedSizes.isEmpty();
        if (hasColors != hasSizes) {
            disableActiveSkus(product.getId(), tenantId);
            return;
        }
        List<ProductColor> colors = hasColors
                ? selectedColors
                : List.of(requireReservedColor(tenantId, ATTRIBUTE_NOT_APPLICABLE, "不分颜色"));
        List<ProductSize> sizes = hasSizes
                ? selectedSizes
                : List.of(requireReservedSize(tenantId, ATTRIBUTE_NOT_APPLICABLE));
        String generatedType = !hasColors && !hasSizes
                ? ProductSkuType.DEFAULT.name()
                : ProductSkuType.NORMAL.name();

        // 查询当前商品下未删除的 SKU（租户隔离 + 未删除）
        LambdaQueryWrapper<ProductSku> skuWrapper = new LambdaQueryWrapper<>();
        skuWrapper.eq(ProductSku::getProductId, product.getId());
        skuWrapper.eq(ProductSku::getTenantId, tenantId);
        skuWrapper.eq(ProductSku::getDeleted, 0);
        List<ProductSku> existingSkus = skuMapper.selectList(skuWrapper);

        Map<String, ProductSku> existingByCode = new HashMap<>();
        for (ProductSku sku : existingSkus) {
            existingByCode.put(sku.getSkuCode(), sku);
        }

        Set<String> targetSkuCodes = new HashSet<>();
        BigDecimal defaultPrice = defaultAmount(product.getWholesalePrice());
        BigDecimal defaultCostPrice = defaultAmount(product.getCostPrice());

        for (ProductColor color : colors) {
            for (ProductSize size : sizes) {
                String skuCode = generateSkuCode(product.getProductCode(), color.getColorCode(), size.getSizeCode());
                targetSkuCodes.add(skuCode);

                ProductSku existingSku = existingByCode.get(skuCode);
                if (existingSku != null) {
                    // 已存在的 SKU：保留其 price/costPrice/barCode/status，只同步 colorId/sizeId
                    // 不自动重新启用被手动禁用的 SKU
                    boolean changed = false;
                    if (!Objects.equals(existingSku.getColorId(), color.getId())) {
                        existingSku.setColorId(color.getId());
                        changed = true;
                    }
                    if (!Objects.equals(existingSku.getSizeId(), size.getId())) {
                        existingSku.setSizeId(size.getId());
                        changed = true;
                    }
                    if (!Objects.equals(normalizeSkuType(existingSku), generatedType)) {
                        existingSku.setSkuType(generatedType);
                        changed = true;
                    }
                    if (changed) {
                        skuMapper.updateById(existingSku);
                    }
                    continue;
                }

                // 不存在的 SKU：新建或恢复
                restoreOrCreateSku(product, color, size, skuCode, generatedType, defaultPrice, defaultCostPrice);
            }
        }

        // 不在目标组合中的 SKU：禁用而非物理删除（租户+未删除过滤）
        for (ProductSku existingSku : existingSkus) {
            if (!isPlaceholder(existingSku) && !targetSkuCodes.contains(existingSku.getSkuCode())) {
                if (!Objects.equals(existingSku.getStatus(), 0)) {
                    LambdaUpdateWrapper<ProductSku> disableWrapper = new LambdaUpdateWrapper<>();
                    disableWrapper.eq(ProductSku::getId, existingSku.getId())
                            .eq(ProductSku::getTenantId, tenantId)
                            .eq(ProductSku::getDeleted, 0)
                            .set(ProductSku::getStatus, 0);
                    skuMapper.update(null, disableWrapper);
                }
            }
        }

        maintainPlaceholderSku(product);
    }

    private void disableActiveSkus(Long productId, Long tenantId) {
        LambdaUpdateWrapper<ProductSku> disableAllWrapper = new LambdaUpdateWrapper<>();
        disableAllWrapper.eq(ProductSku::getProductId, productId)
                .eq(ProductSku::getTenantId, tenantId)
                .eq(ProductSku::getDeleted, 0)
                .ne(ProductSku::getStatus, 0)
                .set(ProductSku::getStatus, 0);
        skuMapper.update(null, disableAllWrapper);
    }

    private void restoreOrCreateSku(Product product, ProductColor color, ProductSize size, String skuCode,
                                    String skuType,
                                    BigDecimal price, BigDecimal costPrice) {
        String checkSql = "SELECT id FROM product_sku WHERE sku_code = ? AND tenant_id = ? AND deleted = 1";
        List<Long> deletedIds = jdbcTemplate.query(checkSql, (rs, rowNum) -> rs.getLong("id"), skuCode, product.getTenantId());
        if (!deletedIds.isEmpty()) {
            String restoreSql = "UPDATE product_sku SET product_id = ?, color_id = ?, size_id = ?, sku_type = ?, price = ?, cost_price = ?, status = ?, deleted = 0 WHERE id = ?";
            jdbcTemplate.update(restoreSql, product.getId(), color.getId(), size.getId(), skuType,
                    price, costPrice, product.getStatus(), deletedIds.get(0));
            return;
        }

        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setColorId(color.getId());
        sku.setSizeId(size.getId());
        sku.setSkuCode(skuCode);
        sku.setSkuType(skuType);
        sku.setPrice(price);
        sku.setCostPrice(costPrice);
        sku.setStatus(product.getStatus());
        sku.setTenantId(product.getTenantId());
        skuMapper.insert(sku);
    }

    private void maintainPlaceholderSku(Product product) {
        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductSku::getProductId, product.getId())
                .eq(ProductSku::getTenantId, product.getTenantId())
                .eq(ProductSku::getDeleted, 0);
        List<ProductSku> skus = skuMapper.selectList(wrapper);
        boolean hasActiveNormalSku = skus.stream()
                .filter(sku -> ProductSkuType.NORMAL.name().equals(normalizeSkuType(sku)))
                .anyMatch(sku -> Objects.equals(sku.getStatus(), 1));
        ProductSku placeholder = skus.stream().filter(this::isPlaceholder).findFirst().orElse(null);

        // 只要商品选择了显式颜色/尺码并产生 NORMAL SKU，就必须保留整款录入入口。
        // 完全无规格商品只有 DEFAULT/NA-NA，不创建 PLACEHOLDER。
        if (!hasActiveNormalSku) {
            if (placeholder != null && !Objects.equals(placeholder.getStatus(), 0)) {
                placeholder.setStatus(0);
                skuMapper.updateById(placeholder);
            }
            return;
        }

        ProductColor color = requireReservedColor(product.getTenantId(), COLOR_UNSPECIFIED, "未指定颜色");
        ProductSize size = requireReservedSize(product.getTenantId(), SIZE_UNSPECIFIED);
        String skuCode = generateSkuCode(product.getProductCode(), COLOR_UNSPECIFIED, SIZE_UNSPECIFIED);
        if (placeholder == null) {
            restoreOrCreateSku(product, color, size, skuCode, ProductSkuType.PLACEHOLDER.name(),
                    defaultAmount(product.getWholesalePrice()), defaultAmount(product.getCostPrice()));
            return;
        }

        placeholder.setColorId(color.getId());
        placeholder.setSizeId(size.getId());
        placeholder.setSkuCode(skuCode);
        placeholder.setSkuType(ProductSkuType.PLACEHOLDER.name());
        placeholder.setPrice(defaultAmount(product.getWholesalePrice()));
        placeholder.setCostPrice(defaultAmount(product.getCostPrice()));
        placeholder.setStatus(product.getStatus());
        skuMapper.updateById(placeholder);
    }

    private ProductColor requireReservedColor(Long tenantId, String code, String name) {
        ProductColor color = colorMapper.selectOne(new LambdaQueryWrapper<ProductColor>()
                .eq(ProductColor::getTenantId, tenantId)
                .eq(ProductColor::getColorCode, code));
        if (color == null) {
            color = new ProductColor();
            color.setColorCode(code);
            color.setColorName(name);
            color.setStatus(0);
            color.setTenantId(tenantId);
            color.setDeleted(0);
            colorMapper.insert(color);
        }
        return color;
    }

    private ProductSize requireReservedSize(Long tenantId, String code) {
        ProductSize size = sizeMapper.selectOne(new LambdaQueryWrapper<ProductSize>()
                .eq(ProductSize::getTenantId, tenantId)
                .eq(ProductSize::getSizeCode, code));
        if (size == null) {
            size = new ProductSize();
            size.setSizeCode(code);
            size.setSort(SIZE_UNSPECIFIED.equals(code) ? 9999 : 9998);
            size.setStatus(0);
            size.setTenantId(tenantId);
            size.setDeleted(0);
            sizeMapper.insert(size);
        }
        return size;
    }

    private String normalizeSkuType(ProductSku sku) {
        return sku.getSkuType() == null || sku.getSkuType().isBlank()
                ? ProductSkuType.NORMAL.name()
                : sku.getSkuType();
    }

    private boolean isPlaceholder(ProductSku sku) {
        return ProductSkuType.PLACEHOLDER.name().equals(normalizeSkuType(sku));
    }

    private String generateSkuCode(String productCode, String colorCode, String sizeCode) {
        return productCode + "-" + colorCode + "-" + sizeCode;
    }

    private ProductVO convertToVO(Product product) {
        Set<String> authorities = currentAuthorities();
        boolean canViewCost = authorities.contains("field:cost_price");
        boolean canViewSale = authorities.contains("field:sale_price");
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setProductCode(product.getProductCode());
        vo.setName(product.getName());
        vo.setCategoryId(product.getCategoryId());
        vo.setSupplierId(product.getSupplierId());
        vo.setUnit(product.getUnit());
        vo.setCostPrice(canViewCost ? product.getCostPrice() : null);
        vo.setWholesalePrice(canViewSale ? product.getWholesalePrice() : null);
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
                skuVO.setSkuType(normalizeSkuType(sku));
                skuVO.setPlaceholder(isPlaceholder(sku));
                skuVO.setColorId(sku.getColorId());
                skuVO.setSizeId(sku.getSizeId());
                skuVO.setPrice(canViewSale ? sku.getPrice() : null);
                skuVO.setCostPrice(canViewCost
                        ? (hasPositiveAmount(sku.getCostPrice()) ? sku.getCostPrice() : product.getCostPrice())
                        : null);
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

    private Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
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

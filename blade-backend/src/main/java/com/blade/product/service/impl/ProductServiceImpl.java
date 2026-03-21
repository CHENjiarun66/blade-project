package com.blade.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.product.dto.ProductCreateDTO;
import com.blade.product.dto.ProductPageDTO;
import com.blade.product.dto.ProductUpdateDTO;
import com.blade.product.dto.ProductVO;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductCategory;
import com.blade.product.entity.ProductColor;
import com.blade.product.entity.ProductColorRel;
import com.blade.product.entity.ProductSize;
import com.blade.product.entity.ProductSizeRel;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.*;
import com.blade.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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

    @Autowired
    public ProductServiceImpl(ProductMapper productMapper,
                             ProductCategoryMapper categoryMapper,
                             ProductColorMapper colorMapper,
                             ProductSizeMapper sizeMapper,
                             ProductSkuMapper skuMapper,
                             ProductColorRelMapper colorRelMapper,
                             ProductSizeRelMapper sizeRelMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.colorMapper = colorMapper;
        this.sizeMapper = sizeMapper;
        this.skuMapper = skuMapper;
        this.colorRelMapper = colorRelMapper;
        this.sizeRelMapper = sizeRelMapper;
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
        product.setUnit(dto.getUnit() != null ? dto.getUnit() : "件");
        product.setDescription(dto.getDescription());
        product.setImageUrl(dto.getImageUrl());
        product.setPrice(dto.getPrice());
        product.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        product.setTenantId(TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L);

        productMapper.insert(product);

        if (dto.getColorIds() != null && !dto.getColorIds().isEmpty()) {
            saveColorRelations(product.getId(), dto.getColorIds());
        }

        if (dto.getSizeIds() != null && !dto.getSizeIds().isEmpty()) {
            saveSizeRelations(product.getId(), dto.getSizeIds());
            if (dto.getColorIds() != null && !dto.getColorIds().isEmpty()) {
                autoGenerateSkus(product.getId(), dto.getColorIds(), dto.getSizeIds(), dto.getPrice());
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
        if (dto.getUnit() != null) {
            product.setUnit(dto.getUnit());
        }
        if (dto.getDescription() != null) {
            product.setDescription(dto.getDescription());
        }
        if (dto.getImageUrl() != null) {
            product.setImageUrl(dto.getImageUrl());
        }
        if (dto.getPrice() != null) {
            product.setPrice(dto.getPrice());
        }
        if (dto.getStatus() != null) {
            product.setStatus(dto.getStatus());
        }

        productMapper.updateById(product);

        if (dto.getColorIds() != null) {
            colorRelMapper.deleteByProductId(dto.getId());
            saveColorRelations(dto.getId(), dto.getColorIds());
        }

        if (dto.getSizeIds() != null) {
            sizeRelMapper.deleteByProductId(dto.getId());
            saveSizeRelations(dto.getId(), dto.getSizeIds());
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
    public List<ProductVO.SizeVO> listAllSizes() {
        LambdaQueryWrapper<ProductSize> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ProductSize::getSort);
        List<ProductSize> sizes = sizeMapper.selectList(wrapper);

        return sizes.stream().map(size -> {
            ProductVO.SizeVO vo = new ProductVO.SizeVO();
            vo.setId(size.getId());
            vo.setSizeCode(size.getSizeCode());
            vo.setSort(size.getSort());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<ProductVO.ColorVO> listAllColors() {
        LambdaQueryWrapper<ProductColor> wrapper = new LambdaQueryWrapper<>();
        List<ProductColor> colors = colorMapper.selectList(wrapper);

        return colors.stream().map(color -> {
            ProductVO.ColorVO vo = new ProductVO.ColorVO();
            vo.setId(color.getId());
            vo.setColorCode(color.getColorCode());
            vo.setColorName(color.getColorName());
            return vo;
        }).collect(Collectors.toList());
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
                sku.setPrice(price);
                sku.setStatus(1);
                sku.setTenantId(product.getTenantId());

                skuMapper.insert(sku);
            }
        }
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
        vo.setUnit(product.getUnit());
        vo.setDescription(product.getDescription());
        vo.setImageUrl(product.getImageUrl());
        vo.setPrice(product.getPrice());
        vo.setStatus(product.getStatus());
        vo.setCreateTime(product.getCreateTime());
        vo.setUpdateTime(product.getUpdateTime());

        if (product.getCategoryId() != null) {
            ProductCategory category = categoryMapper.selectById(product.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getCategoryName());
            }
        }

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

        return vo;
    }
}

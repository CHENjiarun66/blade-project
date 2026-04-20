package com.blade.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blade.common.tenant.TenantContext;
import com.blade.product.dto.CategoryCreateDTO;
import com.blade.product.dto.CategoryUpdateDTO;
import com.blade.product.dto.ProductCategoryVO;
import com.blade.product.entity.ProductCategory;
import com.blade.product.mapper.ProductCategoryMapper;
import com.blade.product.service.ProductCategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductCategoryServiceImpl extends ServiceImpl<ProductCategoryMapper, ProductCategory> implements ProductCategoryService {

    @Override
    public List<ProductCategoryVO> listAll() {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<ProductCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductCategory::getTenantId, tenantId)
               .eq(ProductCategory::getStatus, 1)
               .orderByAsc(ProductCategory::getSort);
        return list(wrapper).stream()
            .map(category -> {
                ProductCategoryVO vo = new ProductCategoryVO();
                BeanUtils.copyProperties(category, vo);
                return vo;
            })
            .collect(Collectors.toList());
    }

    @Override
    public Long create(CategoryCreateDTO dto) {
        Long tenantId = TenantContext.getTenantId();

        // Get max sort if not provided
        Integer sort = dto.getSort();
        if (sort == null) {
            Long count = baseMapper.selectCount(null);
            sort = count != null ? count.intValue() + 1 : 1;
        }

        ProductCategory category = new ProductCategory();
        category.setCategoryName(dto.getCategoryName());
        category.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        category.setSort(sort);
        category.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        category.setTenantId(tenantId);
        category.setDeleted(0);
        baseMapper.insert(category);
        return category.getId();
    }

    @Override
    public void update(CategoryUpdateDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        ProductCategory category = lambdaQuery()
                .eq(ProductCategory::getId, dto.getId())
                .eq(ProductCategory::getTenantId, tenantId)
                .one();
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }
        category.setCategoryName(dto.getCategoryName());
        if (dto.getParentId() != null) {
            category.setParentId(dto.getParentId());
        }
        if (dto.getSort() != null) {
            category.setSort(dto.getSort());
        }
        if (dto.getStatus() != null) {
            category.setStatus(dto.getStatus());
        }
        baseMapper.updateById(category);
    }

    @Override
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();
        ProductCategory category = lambdaQuery()
                .eq(ProductCategory::getId, id)
                .eq(ProductCategory::getTenantId, tenantId)
                .one();
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }
        baseMapper.deleteById(id);
    }
}

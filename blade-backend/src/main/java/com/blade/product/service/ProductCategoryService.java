package com.blade.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.blade.product.dto.CategoryCreateDTO;
import com.blade.product.dto.CategoryUpdateDTO;
import com.blade.product.dto.ProductCategoryVO;
import com.blade.product.entity.ProductCategory;
import java.util.List;

public interface ProductCategoryService extends IService<ProductCategory> {
    List<ProductCategoryVO> listAll();

    Long create(CategoryCreateDTO dto);

    void update(CategoryUpdateDTO dto);

    void delete(Long id);
}

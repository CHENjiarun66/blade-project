package com.blade.product.service;

import com.blade.common.result.PageResult;
import com.blade.product.dto.ProductCreateDTO;
import com.blade.product.dto.ProductPageDTO;
import com.blade.product.dto.ProductUpdateDTO;
import com.blade.product.dto.ProductVO;

import java.util.List;

public interface ProductService {

    PageResult<ProductVO> pageList(ProductPageDTO dto);

    ProductVO getById(Long id);

    Long create(ProductCreateDTO dto);

    void update(ProductUpdateDTO dto);

    void delete(Long id);

    List<ProductVO.SizeVO> listAllSizes();

    List<ProductVO.ColorVO> listAllColors();
}

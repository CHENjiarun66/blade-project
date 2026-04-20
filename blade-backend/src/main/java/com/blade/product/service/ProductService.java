package com.blade.product.service;

import com.blade.common.result.PageResult;
import com.blade.product.dto.*;

import java.util.List;

public interface ProductService {

    PageResult<ProductVO> pageList(ProductPageDTO dto);

    ProductVO getById(Long id);

    Long create(ProductCreateDTO dto);

    void update(ProductUpdateDTO dto);

    void delete(Long id);

    // 颜色管理
    List<ProductVO.ColorVO> listAllColors();
    Long createColor(ColorCreateDTO dto);
    void updateColor(ColorUpdateDTO dto);
    void deleteColor(Long id);

    // 尺码管理
    List<ProductVO.SizeVO> listAllSizes();
    Long createSize(SizeCreateDTO dto);
    void updateSize(SizeUpdateDTO dto);
    void deleteSize(Long id);

    List<SkuVO> listAllSkus();
}

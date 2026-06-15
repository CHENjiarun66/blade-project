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

    /**
     * BE-1005: 商品/SKU 图片绑定服务 — PUT /api/products/{id}/file-bindings
     */
    void bindFiles(Long productId, ProductFileBindingDTO dto);

    /**
     * BE-1013: 商品素材查询 — GET /api/products/{id}/file-bindings
     * 返回商品主图、图集和按 SKU 分组的 SKU 图片
     */
    ProductFileBindingsVO getFileBindings(Long productId);

    /**
     * BE-1014: 单个 SKU 更新 — PUT /api/products/skus
     * 用于 SKU 明细精细维护：单独编辑售价、成本价、条码、状态
     */
    void updateSku(SkuUpdateDTO dto);
}

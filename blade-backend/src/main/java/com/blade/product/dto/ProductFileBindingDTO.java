package com.blade.product.dto;

import java.util.List;

/**
 * 商品文件绑定请求 DTO — PUT /api/products/{id}/file-bindings
 */
public class ProductFileBindingDTO {

    /** 商品主图 fileId（可选）。设置时同时更新 product.image_url 和绑定表 */
    private Long mainFileId;

    /** 商品图集 fileId 列表（可选）。null 表示不操作，空列表表示清空图集 */
    private List<Long> galleryFileIds;

    /** SKU 图片绑定列表（可选）。null 表示不操作，空列表表示不操作任何 SKU */
    private List<SkuImageBindingDTO> skuImageBindings;

    public Long getMainFileId() { return mainFileId; }
    public void setMainFileId(Long mainFileId) { this.mainFileId = mainFileId; }
    public List<Long> getGalleryFileIds() { return galleryFileIds; }
    public void setGalleryFileIds(List<Long> galleryFileIds) { this.galleryFileIds = galleryFileIds; }
    public List<SkuImageBindingDTO> getSkuImageBindings() { return skuImageBindings; }
    public void setSkuImageBindings(List<SkuImageBindingDTO> skuImageBindings) { this.skuImageBindings = skuImageBindings; }
}

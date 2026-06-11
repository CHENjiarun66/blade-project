package com.blade.product.dto;

import java.util.List;

/**
 * SKU 图片绑定项 — 请求 DTO 内嵌使用
 */
public class SkuImageBindingDTO {

    private Long skuId;
    private List<Long> fileIds;

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public List<Long> getFileIds() { return fileIds; }
    public void setFileIds(List<Long> fileIds) { this.fileIds = fileIds; }
}

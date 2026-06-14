package com.blade.product.dto;

import java.util.List;

/**
 * 商品素材查询响应 VO — GET /api/products/{id}/file-bindings
 * 按主图、图集、SKU 图片分组返回
 */
public class ProductFileBindingsVO {

    /** 商品主图绑定 */
    private FileBindingItem main;

    /** 商品图集绑定列表，按 sort 排序 */
    private List<FileBindingItem> gallery;

    /** SKU 图片分组，按 skuId 分组 */
    private List<SkuImageGroup> skuImages;

    public FileBindingItem getMain() { return main; }
    public void setMain(FileBindingItem main) { this.main = main; }
    public List<FileBindingItem> getGallery() { return gallery; }
    public void setGallery(List<FileBindingItem> gallery) { this.gallery = gallery; }
    public List<SkuImageGroup> getSkuImages() { return skuImages; }
    public void setSkuImages(List<SkuImageGroup> skuImages) { this.skuImages = skuImages; }

    /** 单个文件绑定项 */
    public static class FileBindingItem {
        private Long fileId;
        private String previewUrl;
        private Integer sort;
        private Integer isPrimary;

        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        public String getPreviewUrl() { return previewUrl; }
        public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }
        public Integer getSort() { return sort; }
        public void setSort(Integer sort) { this.sort = sort; }
        public Integer getIsPrimary() { return isPrimary; }
        public void setIsPrimary(Integer isPrimary) { this.isPrimary = isPrimary; }
    }

    /** SKU 图片分组 */
    public static class SkuImageGroup {
        private Long skuId;
        private String skuCode;
        private String colorName;
        private String sizeName;
        private List<FileBindingItem> files;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public String getColorName() { return colorName; }
        public void setColorName(String colorName) { this.colorName = colorName; }
        public String getSizeName() { return sizeName; }
        public void setSizeName(String sizeName) { this.sizeName = sizeName; }
        public List<FileBindingItem> getFiles() { return files; }
        public void setFiles(List<FileBindingItem> files) { this.files = files; }
    }
}

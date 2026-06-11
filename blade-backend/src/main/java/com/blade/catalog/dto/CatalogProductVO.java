package com.blade.catalog.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Product info for catalog display.
 * No costPrice, no wholesalePrice, no supplierId, no raw inventory quantities.
 */
public class CatalogProductVO {

    private Long id;
    private String productCode;
    private String name;
    private Long categoryId;
    private String categoryName;
    private String mainImageUrl;
    private List<String> imageUrls;
    private boolean hasImage;
    private boolean hasStock;
    private String stockStatus;
    private List<String> tags;
    private List<ColorSizeEntry> colors;
    private List<ColorSizeEntry> sizes;
    private List<CatalogSkuVO> skus;
    private LocalDateTime createTime;

    // --- inner types ---

    public static class ColorSizeEntry {
        private Long id;
        private String name;
        private String code;  // colorCode for colors; sizeCode for sizes

        public ColorSizeEntry() {}
        public ColorSizeEntry(Long id, String name, String code) {
            this.id = id;
            this.name = name;
            this.code = code;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    // --- getters & setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getMainImageUrl() { return mainImageUrl; }
    public void setMainImageUrl(String mainImageUrl) { this.mainImageUrl = mainImageUrl; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public boolean isHasImage() { return hasImage; }
    public void setHasImage(boolean hasImage) { this.hasImage = hasImage; }

    public boolean isHasStock() { return hasStock; }
    public void setHasStock(boolean hasStock) { this.hasStock = hasStock; }

    public String getStockStatus() { return stockStatus; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<ColorSizeEntry> getColors() { return colors; }
    public void setColors(List<ColorSizeEntry> colors) { this.colors = colors; }

    public List<ColorSizeEntry> getSizes() { return sizes; }
    public void setSizes(List<ColorSizeEntry> sizes) { this.sizes = sizes; }

    public List<CatalogSkuVO> getSkus() { return skus; }
    public void setSkus(List<CatalogSkuVO> skus) { this.skus = skus; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}

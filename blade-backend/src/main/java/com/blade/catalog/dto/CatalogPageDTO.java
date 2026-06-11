package com.blade.catalog.dto;

/**
 * Catalog product list filter + pagination DTO.
 * Accepts both "current" and "page" for page number.
 */
public class CatalogPageDTO {

    private String keyword;
    private Long categoryId;
    private Long colorId;
    private Long sizeId;
    private String stockMode;   // all | in_stock
    private Boolean hasImage;

    private Long current = 1L;
    private Long size = 20L;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public Long getColorId() { return colorId; }
    public void setColorId(Long colorId) { this.colorId = colorId; }

    public Long getSizeId() { return sizeId; }
    public void setSizeId(Long sizeId) { this.sizeId = sizeId; }

    public String getStockMode() { return stockMode; }
    public void setStockMode(String stockMode) { this.stockMode = stockMode; }

    public Boolean getHasImage() { return hasImage; }
    public void setHasImage(Boolean hasImage) { this.hasImage = hasImage; }

    public Long getCurrent() { return current; }
    public void setCurrent(Long current) { this.current = current; }

    // "page" alias — Spring maps both; we prefer current, but if current is default and page is set, use page.
    public void setPage(Long page) {
        if (page != null) {
            this.current = page;
        }
    }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public void normalize() {
        if (current == null || current < 1) {
            current = 1L;
        }
        if (size == null || size < 1) {
            size = 20L;
        }
        if (size > 100) {
            size = 100L;
        }
        if (stockMode == null || stockMode.isBlank()) {
            stockMode = "all";
        }
    }

    public long offset() {
        return (current - 1) * size;
    }
}

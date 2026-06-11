package com.blade.catalog.dto;

import java.util.List;

/**
 * SKU info for catalog display.
 * No costPrice, no raw inventory quantities.
 */
public class CatalogSkuVO {

    private Long id;
    private String skuCode;
    private Long colorId;
    private String colorName;
    private Long sizeId;
    private String sizeCode;
    private List<String> imageUrls;
    private boolean hasStock;
    private String stockStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public Long getColorId() { return colorId; }
    public void setColorId(Long colorId) { this.colorId = colorId; }

    public String getColorName() { return colorName; }
    public void setColorName(String colorName) { this.colorName = colorName; }

    public Long getSizeId() { return sizeId; }
    public void setSizeId(Long sizeId) { this.sizeId = sizeId; }

    public String getSizeCode() { return sizeCode; }
    public void setSizeCode(String sizeCode) { this.sizeCode = sizeCode; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public boolean isHasStock() { return hasStock; }
    public void setHasStock(boolean hasStock) { this.hasStock = hasStock; }

    public String getStockStatus() { return stockStatus; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
}

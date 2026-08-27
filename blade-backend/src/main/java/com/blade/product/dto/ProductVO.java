package com.blade.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "商品VO")
public class ProductVO {

    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "商品编码")
    private String productCode;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "供应商名称")
    private String supplierName;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "进货价（成本参考）")
    private BigDecimal costPrice;

    @Schema(description = "批发价")
    private BigDecimal wholesalePrice;

    @Schema(description = "重量")
    private BigDecimal weight;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "商品图片")
    private String imageUrl;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "可选颜色列表")
    private List<ColorVO> colors;

    @Schema(description = "可选尺码列表")
    private List<SizeVO> sizes;

    @Schema(description = "SKU列表")
    private List<SkuVO> skus;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

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
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public BigDecimal getWholesalePrice() { return wholesalePrice; }
    public void setWholesalePrice(BigDecimal wholesalePrice) { this.wholesalePrice = wholesalePrice; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public List<ColorVO> getColors() { return colors; }
    public void setColors(List<ColorVO> colors) { this.colors = colors; }
    public List<SizeVO> getSizes() { return sizes; }
    public void setSizes(List<SizeVO> sizes) { this.sizes = sizes; }
    public List<SkuVO> getSkus() { return skus; }
    public void setSkus(List<SkuVO> skus) { this.skus = skus; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Schema(description = "颜色VO")
    public static class ColorVO {
        private Long id;
        private String colorCode;
        private String colorName;
        private Integer status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getColorCode() { return colorCode; }
        public void setColorCode(String colorCode) { this.colorCode = colorCode; }
        public String getColorName() { return colorName; }
        public void setColorName(String colorName) { this.colorName = colorName; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    @Schema(description = "尺码VO")
    public static class SizeVO {
        private Long id;
        private String sizeCode;
        private Integer sort;
        private Integer status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getSizeCode() { return sizeCode; }
        public void setSizeCode(String sizeCode) { this.sizeCode = sizeCode; }
        public Integer getSort() { return sort; }
        public void setSort(Integer sort) { this.sort = sort; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    @Schema(description = "SKU VO")
    public static class SkuVO {
        private Long id;
        private String skuCode;
        private String skuType;
        private boolean placeholder;
        private Long colorId;
        private String colorName;
        private Long sizeId;
        private String sizeName;
        private java.math.BigDecimal price;
        private java.math.BigDecimal costPrice;
        private String barCode;
        private Integer status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public String getSkuType() { return skuType; }
        public void setSkuType(String skuType) { this.skuType = skuType; }
        public boolean isPlaceholder() { return placeholder; }
        public void setPlaceholder(boolean placeholder) { this.placeholder = placeholder; }
        public Long getColorId() { return colorId; }
        public void setColorId(Long colorId) { this.colorId = colorId; }
        public String getColorName() { return colorName; }
        public void setColorName(String colorName) { this.colorName = colorName; }
        public Long getSizeId() { return sizeId; }
        public void setSizeId(Long sizeId) { this.sizeId = sizeId; }
        public String getSizeName() { return sizeName; }
        public void setSizeName(String sizeName) { this.sizeName = sizeName; }
        public java.math.BigDecimal getPrice() { return price; }
        public void setPrice(java.math.BigDecimal price) { this.price = price; }
        public java.math.BigDecimal getCostPrice() { return costPrice; }
        public void setCostPrice(java.math.BigDecimal costPrice) { this.costPrice = costPrice; }
        public String getBarCode() { return barCode; }
        public void setBarCode(String barCode) { this.barCode = barCode; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }
}

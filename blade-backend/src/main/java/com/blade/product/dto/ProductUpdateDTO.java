package com.blade.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "更新商品DTO")
public class ProductUpdateDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID")
    private Long id;

    @Size(max = 100, message = "商品名称最多100位")
    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Size(max = 10, message = "单位最多10位")
    @Schema(description = "单位")
    private String unit;

    @Schema(description = "进货价（成本参考）")
    private BigDecimal costPrice;

    @Schema(description = "批发价")
    private BigDecimal wholesalePrice;

    @Schema(description = "重量（用于物流/运费计算）")
    private BigDecimal weight;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "商品图片URL")
    private String imageUrl;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态: 1启用 0禁用")
    private Integer status;

    @Schema(description = "颜色ID列表")
    private List<Long> colorIds;

    @Schema(description = "尺码ID列表")
    private List<Long> sizeIds;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
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
    public List<Long> getColorIds() { return colorIds; }
    public void setColorIds(List<Long> colorIds) { this.colorIds = colorIds; }
    public List<Long> getSizeIds() { return sizeIds; }
    public void setSizeIds(List<Long> sizeIds) { this.sizeIds = sizeIds; }
}

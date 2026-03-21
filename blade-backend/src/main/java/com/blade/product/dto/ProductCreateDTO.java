package com.blade.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "创建商品DTO")
public class ProductCreateDTO {

    @NotBlank(message = "商品编码不能为空")
    @Size(max = 30, message = "商品编码最多30位")
    @Schema(description = "商品编码")
    private String productCode;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100, message = "商品名称最多100位")
    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Size(max = 10, message = "单位最多10位")
    @Schema(description = "单位")
    private String unit = "件";

    @Schema(description = "描述")
    private String description;

    @Schema(description = "商品图片URL")
    private String imageUrl;

    @Schema(description = "单价")
    private BigDecimal price;

    @Schema(description = "状态: 1启用 0禁用")
    private Integer status = 1;

    @Schema(description = "颜色ID列表")
    private List<Long> colorIds;

    @Schema(description = "尺码ID列表")
    private List<Long> sizeIds;

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public List<Long> getColorIds() { return colorIds; }
    public void setColorIds(List<Long> colorIds) { this.colorIds = colorIds; }
    public List<Long> getSizeIds() { return sizeIds; }
    public void setSizeIds(List<Long> sizeIds) { this.sizeIds = sizeIds; }
}

package com.blade.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("product_sku")
public class ProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("product_id")
    private Long productId;
    @TableField("color_id")
    private Long colorId;
    @TableField("size_id")
    private Long sizeId;
    @TableField("sku_code")
    private String skuCode;
    @TableField("sku_type")
    private String skuType;
    private BigDecimal price;
    @TableField("cost_price")
    private BigDecimal costPrice;
    @TableField("bar_code")
    private String barCode;
    private Integer status;
    @TableField("tenant_id")
    private Long tenantId;
    private Integer deleted;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getColorId() { return colorId; }
    public void setColorId(Long colorId) { this.colorId = colorId; }
    public Long getSizeId() { return sizeId; }
    public void setSizeId(Long sizeId) { this.sizeId = sizeId; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getSkuType() { return skuType; }
    public void setSkuType(String skuType) { this.skuType = skuType; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public String getBarCode() { return barCode; }
    public void setBarCode(String barCode) { this.barCode = barCode; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}

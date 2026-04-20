package com.blade.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("sale_order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("sku_id")
    private Long skuId;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("sku_code")
    private String skuCode;

    @TableField("product_name")
    private String productName;

    @TableField("color_name")
    private String colorName;

    @TableField("size_name")
    private String sizeName;

    private BigDecimal price;

    private Integer quantity;

    /**
     * 计划数量（原订单数量）
     */
    @TableField("planned_quantity")
    private Integer plannedQuantity;

    /**
     * 配货数量（调整后数量）
     */
    @TableField("allocated_quantity")
    private Integer allocatedQuantity;

    /**
     * 已出库数量
     */
    @TableField("out_quantity")
    private Integer outQuantity;

    /**
     * 调整说明
     */
    @TableField("adjustment_remark")
    private String adjustmentRemark;

    private BigDecimal subtotal;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getColorName() { return colorName; }
    public void setColorName(String colorName) { this.colorName = colorName; }
    public String getSizeName() { return sizeName; }
    public void setSizeName(String sizeName) { this.sizeName = sizeName; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Integer getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(Integer plannedQuantity) { this.plannedQuantity = plannedQuantity; }
    public Integer getAllocatedQuantity() { return allocatedQuantity; }
    public void setAllocatedQuantity(Integer allocatedQuantity) { this.allocatedQuantity = allocatedQuantity; }
    public Integer getOutQuantity() { return outQuantity; }
    public void setOutQuantity(Integer outQuantity) { this.outQuantity = outQuantity; }
    public String getAdjustmentRemark() { return adjustmentRemark; }
    public void setAdjustmentRemark(String adjustmentRemark) { this.adjustmentRemark = adjustmentRemark; }
}

package com.blade.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("sale_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("order_date")
    private LocalDate orderDate;

    @TableField("source_doc_no")
    private String sourceDocNo;

    @TableField("source_shop")
    private String sourceShop;

    @TableField("order_type")
    private String orderType;

    @TableField("customer_id")
    private Long customerId;

    @TableField("customer_name")
    private String customerName;

    @TableField("customer_phone")
    private String customerPhone;

    @TableField("customer_address")
    private String customerAddress;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    /**
     * 原始订单金额（调整前）
     */
    @TableField("original_amount")
    private BigDecimal originalAmount;

    /**
     * 已退款金额
     */
    @TableField("refund_amount")
    private BigDecimal refundAmount;

    /**
     * 调整状态：NONE无调整/PENDING待确认/APPROVED已确认/COMPLETED已完成
     */
    @TableField("adjustment_status")
    private String adjustmentStatus;

    @TableField("paid_amount")
    private BigDecimal paidAmount;

    /**
     * 抹零/短款结清金额，客户少付但业务确认不再追收
     */
    @TableField("write_off_amount")
    private BigDecimal writeOffAmount;

    /**
     * 抹零/短款结清原因
     */
    @TableField("write_off_reason")
    private String writeOffReason;

    // 支付状态: 0未付款 1部分收款 2已结清
    @TableField("payment_status")
    private Integer paymentStatus;

    // 定金金额
    @TableField("deposit_amount")
    private BigDecimal depositAmount;

    @TableField("freight_amount")
    private BigDecimal freightAmount;

    @TableField("freight_cost")
    private BigDecimal freightCost;

    @TableField("total_cost_amount")
    private BigDecimal totalCostAmount;

    @TableField("gross_profit")
    private BigDecimal grossProfit;

    // 是否需要送货: 0否 1是
    @TableField("need_delivery")
    private Integer needDelivery;

    // 送货地址
    @TableField("delivery_address")
    private String deliveryAddress;

    // 是否已送货: 0否 1是
    @TableField("is_delivered")
    private Integer isDelivered;

    // 送货时间
    @TableField("delivered_at")
    private LocalDateTime deliveredAt;

    @TableField("warehouse_id")
    private Long warehouseId;

    // 开单销售人员ID，关联 sys_user.id
    @TableField("salesman_id")
    private Long salesmanId;

    // 开单销售人员名称（冗余字段，避免跨租户查询）
    @TableField("salesman_name")
    private String salesmanName;

    private Integer status;

    private String remark;

    // 订单图片，JSON数组格式，最多9张
    private String images;

    @TableField("tenant_id")
    private Long tenantId;

    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField("pay_time")
    private LocalDateTime payTime;

    @TableField("confirm_time")
    private LocalDateTime confirmTime;

    @TableField("deliver_time")
    private LocalDateTime deliverTime;

    @TableField("complete_time")
    private LocalDateTime completeTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public String getSourceDocNo() { return sourceDocNo; }
    public void setSourceDocNo(String sourceDocNo) { this.sourceDocNo = sourceDocNo; }
    public String getSourceShop() { return sourceShop; }
    public void setSourceShop(String sourceShop) { this.sourceShop = sourceShop; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getWriteOffAmount() { return writeOffAmount; }
    public void setWriteOffAmount(BigDecimal writeOffAmount) { this.writeOffAmount = writeOffAmount; }
    public String getWriteOffReason() { return writeOffReason; }
    public void setWriteOffReason(String writeOffReason) { this.writeOffReason = writeOffReason; }
    public Integer getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(Integer paymentStatus) { this.paymentStatus = paymentStatus; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public BigDecimal getFreightAmount() { return freightAmount; }
    public void setFreightAmount(BigDecimal freightAmount) { this.freightAmount = freightAmount; }
    public BigDecimal getFreightCost() { return freightCost; }
    public void setFreightCost(BigDecimal freightCost) { this.freightCost = freightCost; }
    public BigDecimal getTotalCostAmount() { return totalCostAmount; }
    public void setTotalCostAmount(BigDecimal totalCostAmount) { this.totalCostAmount = totalCostAmount; }
    public BigDecimal getGrossProfit() { return grossProfit; }
    public void setGrossProfit(BigDecimal grossProfit) { this.grossProfit = grossProfit; }
    public Integer getNeedDelivery() { return needDelivery; }
    public void setNeedDelivery(Integer needDelivery) { this.needDelivery = needDelivery; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public Integer getIsDelivered() { return isDelivered; }
    public void setIsDelivered(Integer isDelivered) { this.isDelivered = isDelivered; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getSalesmanId() { return salesmanId; }
    public void setSalesmanId(Long salesmanId) { this.salesmanId = salesmanId; }
    public String getSalesmanName() { return salesmanName; }
    public void setSalesmanName(String salesmanName) { this.salesmanName = salesmanName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }
    public LocalDateTime getConfirmTime() { return confirmTime; }
    public void setConfirmTime(LocalDateTime confirmTime) { this.confirmTime = confirmTime; }
    public LocalDateTime getDeliverTime() { return deliverTime; }
    public void setDeliverTime(LocalDateTime deliverTime) { this.deliverTime = deliverTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getAdjustmentStatus() { return adjustmentStatus; }
    public void setAdjustmentStatus(String adjustmentStatus) { this.adjustmentStatus = adjustmentStatus; }

    /**
     * 调整状态枚举
     */
    public static class AdjustmentStatus {
        public static final String NONE = "NONE";           // 无调整
        public static final String PENDING = "PENDING";     // 待确认
        public static final String APPROVED = "APPROVED";   // 已确认
        public static final String COMPLETED = "COMPLETED"; // 已完成
    }
}

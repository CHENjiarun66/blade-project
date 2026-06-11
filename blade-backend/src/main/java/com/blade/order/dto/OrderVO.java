package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "订单VO")
public class OrderVO {

    private Long id;
    private String orderNo;
    private LocalDate orderDate;
    private String sourceDocNo;
    private String sourceShop;
    private String orderType;
    private String orderTypeName;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private BigDecimal totalAmount;
    private BigDecimal originalAmount;
    private BigDecimal refundAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    // 支付状态: 0未付款 1已付定金 2已付全款
    private Integer paymentStatus;
    private String paymentStatusName;
    private String adjustmentStatus;
    // 定金金额
    private BigDecimal depositAmount;
    private BigDecimal freightAmount;
    private BigDecimal freightCost;
    private BigDecimal totalCostAmount;
    private BigDecimal grossProfit;
    // 是否需要送货: 0否 1是
    private Integer needDelivery;
    // 送货地址
    private String deliveryAddress;
    // 是否已送货: 0否 1是
    private Integer isDelivered;
    // 送货时间
    private LocalDateTime deliveredAt;
    private Long warehouseId;
    private String warehouseName;
    private Long salesmanId;
    private String salesmanName;
    private Integer status;
    private String statusName;
    private String remark;
    // 订单图片，JSON数组格式
    private String images;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime payTime;
    private LocalDateTime confirmTime;
    private LocalDateTime deliverTime;
    private LocalDateTime completeTime;
    private List<OrderItemVO> items;

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
    public String getOrderTypeName() { return orderTypeName; }
    public void setOrderTypeName(String orderTypeName) { this.orderTypeName = orderTypeName; }
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
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public void setBalanceAmount(BigDecimal balanceAmount) { this.balanceAmount = balanceAmount; }
    public Integer getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(Integer paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getPaymentStatusName() { return paymentStatusName; }
    public void setPaymentStatusName(String paymentStatusName) { this.paymentStatusName = paymentStatusName; }
    public String getAdjustmentStatus() { return adjustmentStatus; }
    public void setAdjustmentStatus(String adjustmentStatus) { this.adjustmentStatus = adjustmentStatus; }
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
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public Long getSalesmanId() { return salesmanId; }
    public void setSalesmanId(Long salesmanId) { this.salesmanId = salesmanId; }
    public String getSalesmanName() { return salesmanName; }
    public void setSalesmanName(String salesmanName) { this.salesmanName = salesmanName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
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
    public List<OrderItemVO> getItems() { return items; }
    public void setItems(List<OrderItemVO> items) { this.items = items; }

    @Schema(description = "订单明细VO")
    public static class OrderItemVO {
        private Long id;
        private Long skuId;
        private Long warehouseId;
        private String warehouseName;
        private String skuCode;
        private String productName;
        private String colorName;
        private String sizeName;
        private BigDecimal price;
        private BigDecimal costPrice;
        private Integer quantity;
        private Integer plannedQuantity;
        private Integer allocatedQuantity;
        private Integer outQuantity;
        private String adjustmentRemark;
        private BigDecimal subtotal;
        private BigDecimal costAmount;
        private BigDecimal grossProfit;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Long getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
        public String getWarehouseName() { return warehouseName; }
        public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
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
        public BigDecimal getCostPrice() { return costPrice; }
        public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Integer getPlannedQuantity() { return plannedQuantity; }
        public void setPlannedQuantity(Integer plannedQuantity) { this.plannedQuantity = plannedQuantity; }
        public Integer getAllocatedQuantity() { return allocatedQuantity; }
        public void setAllocatedQuantity(Integer allocatedQuantity) { this.allocatedQuantity = allocatedQuantity; }
        public Integer getOutQuantity() { return outQuantity; }
        public void setOutQuantity(Integer outQuantity) { this.outQuantity = outQuantity; }
        public String getAdjustmentRemark() { return adjustmentRemark; }
        public void setAdjustmentRemark(String adjustmentRemark) { this.adjustmentRemark = adjustmentRemark; }
        public BigDecimal getSubtotal() { return subtotal; }
        public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
        public BigDecimal getCostAmount() { return costAmount; }
        public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }
        public BigDecimal getGrossProfit() { return grossProfit; }
        public void setGrossProfit(BigDecimal grossProfit) { this.grossProfit = grossProfit; }
    }
}

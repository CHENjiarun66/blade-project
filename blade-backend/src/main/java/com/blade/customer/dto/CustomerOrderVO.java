package com.blade.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "客户订单记录VO（精简版）")
public class CustomerOrderVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "订单状态：0创建 1已付款 2配货中 3待发货 4已发货 5已完成 6已取消")
    private Integer status;

    @Schema(description = "订单状态名称")
    private String statusName;

    @Schema(description = "支付状态：0未付款 1部分收款 2已结清")
    private Integer paymentStatus;

    @Schema(description = "总金额")
    private BigDecimal totalAmount;

    @Schema(description = "已付金额")
    private BigDecimal paidAmount;

    @Schema(description = "订单总额（含货币符号）")
    private String totalAmountText;

    @Schema(description = "已付金额文本")
    private String paidAmountText;

    @Schema(description = "订单项摘要")
    private List<OrderItemVO> items;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "商品数量")
    private Integer totalQuantity;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }
    public Integer getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(Integer paymentStatus) { this.paymentStatus = paymentStatus; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public String getTotalAmountText() { return totalAmountText; }
    public void setTotalAmountText(String totalAmountText) { this.totalAmountText = totalAmountText; }
    public String getPaidAmountText() { return paidAmountText; }
    public void setPaidAmountText(String paidAmountText) { this.paidAmountText = paidAmountText; }
    public List<OrderItemVO> getItems() { return items; }
    public void setItems(List<OrderItemVO> items) { this.items = items; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }

    @Schema(description = "订单项VO")
    public static class OrderItemVO {
        @Schema(description = "商品名称")
        private String productName;
        @Schema(description = "SKU描述")
        private String skuDesc;
        @Schema(description = "数量")
        private Integer quantity;
        @Schema(description = "单价")
        private BigDecimal price;

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getSkuDesc() { return skuDesc; }
        public void setSkuDesc(String skuDesc) { this.skuDesc = skuDesc; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }
}

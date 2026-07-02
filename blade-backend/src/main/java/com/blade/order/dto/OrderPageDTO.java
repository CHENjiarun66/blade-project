package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "订单分页查询DTO")
public class OrderPageDTO {

    @Min(value = 1, message = "页码最小为1")
    @Schema(description = "页码")
    private Long current = 1L;

    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    @Schema(description = "每页数量")
    private Long size = 20L;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "订单状态: 0创建/1已付款/2配货中/3待发货/4已发货/5已完成/6已取消/7退货中/8已退货")
    private Integer status;

    @Schema(description = "支付状态: 0未付款/1部分收款/2已结清")
    private Integer paymentStatus;

    @Schema(description = "订单类型：SPOT现货/PREORDER订货")
    private String orderType;

    @Schema(description = "是否欠款：true=paid_amount < max(total_amount - refund_amount - write_off_amount, 0)")
    private Boolean hasBalance;

    public Long getCurrent() { return current; }
    public void setCurrent(Long current) { this.current = current; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(Integer paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public Boolean getHasBalance() { return hasBalance; }
    public void setHasBalance(Boolean hasBalance) { this.hasBalance = hasBalance; }
}

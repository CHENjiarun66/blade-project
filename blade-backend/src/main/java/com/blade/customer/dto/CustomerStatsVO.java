package com.blade.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "客户统计VO")
public class CustomerStatsVO {

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "历史订单总数")
    private Integer totalOrders;

    @Schema(description = "已完成订单数")
    private Integer completedOrders;

    @Schema(description = "总消费金额")
    private BigDecimal totalSpending;

    @Schema(description = "最近下单时间")
    private String lastOrderTime;

    @Schema(description = "最早下单时间")
    private String firstOrderTime;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Integer getTotalOrders() { return totalOrders; }
    public void setTotalOrders(Integer totalOrders) { this.totalOrders = totalOrders; }
    public Integer getCompletedOrders() { return completedOrders; }
    public void setCompletedOrders(Integer completedOrders) { this.completedOrders = completedOrders; }
    public BigDecimal getTotalSpending() { return totalSpending; }
    public void setTotalSpending(BigDecimal totalSpending) { this.totalSpending = totalSpending; }
    public String getLastOrderTime() { return lastOrderTime; }
    public void setLastOrderTime(String lastOrderTime) { this.lastOrderTime = lastOrderTime; }
    public String getFirstOrderTime() { return firstOrderTime; }
    public void setFirstOrderTime(String firstOrderTime) { this.firstOrderTime = firstOrderTime; }
}

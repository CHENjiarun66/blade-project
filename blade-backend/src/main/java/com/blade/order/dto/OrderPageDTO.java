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

    @Schema(description = "订单状态: 0待处理/1已确认/2进行中/3已完成/4已取消")
    private Integer status;

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
}

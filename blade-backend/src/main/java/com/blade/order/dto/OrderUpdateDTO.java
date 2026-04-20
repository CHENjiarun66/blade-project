package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "更新订单DTO（基础信息，不含订单明细和状态）")
public class OrderUpdateDTO {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long id;

    @Size(max = 50, message = "客户名称最多50位")
    @Schema(description = "客户名称")
    private String customerName;

    @Size(max = 20, message = "手机号最多20位")
    @Schema(description = "客户电话")
    private String customerPhone;

    @Size(max = 255, message = "地址最多255位")
    @Schema(description = "客户地址")
    private String customerAddress;

    @Schema(description = "是否需要送货: 0否 1是")
    private Integer needDelivery;

    @Size(max = 255, message = "送货地址最多255位")
    @Schema(description = "送货地址")
    private String deliveryAddress;

    @Size(max = 500, message = "备注最多500位")
    @Schema(description = "备注")
    private String remark;

    @Schema(description = "订单图片，JSON数组格式")
    private String images;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
    public Integer getNeedDelivery() { return needDelivery; }
    public void setNeedDelivery(Integer needDelivery) { this.needDelivery = needDelivery; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
}

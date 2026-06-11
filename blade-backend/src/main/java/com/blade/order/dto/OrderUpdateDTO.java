package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "更新订单DTO（基础信息，不含订单明细和状态）")
public class OrderUpdateDTO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单日期（纸质单据日期）")
    private LocalDate orderDate;

    @Size(max = 50, message = "纸质单据号最多50位")
    @Schema(description = "纸质单据号/外部单号")
    private String sourceDocNo;

    @Size(max = 100, message = "来源档口最多100位")
    @Schema(description = "订单来源档口/店铺")
    private String sourceShop;

    @Size(max = 20, message = "订单类型最多20位")
    @Schema(description = "订单类型：SPOT现货/PREORDER订货")
    private String orderType;

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

    @Schema(description = "客户运费收入")
    private BigDecimal freightAmount;

    @Schema(description = "实际运费成本")
    private BigDecimal freightCost;

    @Schema(description = "订单明细（未发货前可整体替换）")
    private List<OrderCreateDTO.OrderItemDTO> items;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public String getSourceDocNo() { return sourceDocNo; }
    public void setSourceDocNo(String sourceDocNo) { this.sourceDocNo = sourceDocNo; }
    public String getSourceShop() { return sourceShop; }
    public void setSourceShop(String sourceShop) { this.sourceShop = sourceShop; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
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
    public BigDecimal getFreightAmount() { return freightAmount; }
    public void setFreightAmount(BigDecimal freightAmount) { this.freightAmount = freightAmount; }
    public BigDecimal getFreightCost() { return freightCost; }
    public void setFreightCost(BigDecimal freightCost) { this.freightCost = freightCost; }
    public List<OrderCreateDTO.OrderItemDTO> getItems() { return items; }
    public void setItems(List<OrderCreateDTO.OrderItemDTO> items) { this.items = items; }
}

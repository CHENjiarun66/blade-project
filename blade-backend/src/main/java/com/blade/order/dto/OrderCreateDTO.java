package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "创建订单DTO")
public class OrderCreateDTO {

    @Schema(description = "客户ID（可选，传此字段表示关联已有客户）")
    private Long customerId;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 50, message = "客户名称最多50位")
    @Schema(description = "客户名称")
    private String customerName;

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

    @Size(max = 20, message = "手机号最多20位")
    @Schema(description = "客户电话")
    private String customerPhone;

    @Size(max = 255, message = "地址最多255位")
    @Schema(description = "客户地址")
    private String customerAddress;

    // 支付状态: 0未付款 1已付定金 2已付全款
    @NotNull(message = "支付状态不能为空")
    @Schema(description = "支付状态: 0未付款 1已付定金 2已付全款")
    private Integer paymentStatus;

    @Schema(description = "定金金额（当 paymentStatus=1 时必填）")
    private BigDecimal depositAmount;

    @Schema(description = "初始实收金额")
    private BigDecimal paidAmount;

    @Schema(description = "客户运费收入")
    private BigDecimal freightAmount;

    @Schema(description = "实际运费成本")
    private BigDecimal freightCost;

    @Schema(description = "是否需要送货: 0否 1是")
    private Integer needDelivery;

    @Size(max = 255, message = "送货地址最多255位")
    @Schema(description = "送货地址（needDelivery=1 时必填）")
    private String deliveryAddress;

    @Schema(description = "发货仓库ID（可选，录单阶段不需要选择）")
    private Long warehouseId;

    // salesmanId 由后端自动从当前登录用户获取，不需要前端传递
    @Schema(description = "开单销售人员ID（后端自动设置）")
    private Long salesmanId;

    @Size(max = 255, message = "备注最多255位")
    @Schema(description = "备注")
    private String remark;

    @Schema(description = "订单图片，JSON数组格式，最多9张")
    private String images;

    @NotEmpty(message = "订单明细不能为空")
    @Valid
    @Schema(description = "订单明细")
    private List<OrderItemDTO> items;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public String getSourceDocNo() { return sourceDocNo; }
    public void setSourceDocNo(String sourceDocNo) { this.sourceDocNo = sourceDocNo; }
    public String getSourceShop() { return sourceShop; }
    public void setSourceShop(String sourceShop) { this.sourceShop = sourceShop; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
    public Integer getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(Integer paymentStatus) { this.paymentStatus = paymentStatus; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getFreightAmount() { return freightAmount; }
    public void setFreightAmount(BigDecimal freightAmount) { this.freightAmount = freightAmount; }
    public BigDecimal getFreightCost() { return freightCost; }
    public void setFreightCost(BigDecimal freightCost) { this.freightCost = freightCost; }
    public Integer getNeedDelivery() { return needDelivery; }
    public void setNeedDelivery(Integer needDelivery) { this.needDelivery = needDelivery; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getSalesmanId() { return salesmanId; }
    public void setSalesmanId(Long salesmanId) { this.salesmanId = salesmanId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
    public List<OrderItemDTO> getItems() { return items; }
    public void setItems(List<OrderItemDTO> items) { this.items = items; }

    @Schema(description = "订单明细DTO")
    public static class OrderItemDTO {

        @NotNull(message = "SKU ID不能为空")
        @Schema(description = "SKU ID")
        private Long skuId;

        @Schema(description = "仓库ID（可选，不填则使用订单级别的warehouseId）")
        private Long warehouseId;

        @NotNull(message = "数量不能为空")
        @Schema(description = "数量")
        private Integer quantity;

        @Schema(description = "单价（可选，不填则取SKU价格）")
        private BigDecimal price;

        @Schema(description = "成本价快照（可选，不填则取SKU/商品成本价）")
        private BigDecimal costPrice;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Long getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public BigDecimal getCostPrice() { return costPrice; }
        public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    }
}

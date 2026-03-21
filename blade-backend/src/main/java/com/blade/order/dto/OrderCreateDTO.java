package com.blade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "创建订单DTO")
public class OrderCreateDTO {

    @Schema(description = "客户ID（可选）")
    private Long customerId;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 50, message = "客户名称最多50位")
    @Schema(description = "客户名称")
    private String customerName;

    @Size(max = 11, message = "手机号最多11位")
    @Schema(description = "客户电话")
    private String customerPhone;

    @Size(max = 255, message = "地址最多255位")
    @Schema(description = "客户地址")
    private String customerAddress;

    @NotNull(message = "仓库ID不能为空")
    @Schema(description = "发货仓库ID")
    private Long warehouseId;

    @Size(max = 255, message = "备注最多255位")
    @Schema(description = "备注")
    private String remark;

    @NotEmpty(message = "订单明细不能为空")
    @Valid
    @Schema(description = "订单明细")
    private List<OrderItemDTO> items;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<OrderItemDTO> getItems() { return items; }
    public void setItems(List<OrderItemDTO> items) { this.items = items; }

    @Schema(description = "订单明细DTO")
    public static class OrderItemDTO {

        @NotNull(message = "SKU ID不能为空")
        @Schema(description = "SKU ID")
        private Long skuId;

        @NotNull(message = "数量不能为空")
        @Schema(description = "数量")
        private Integer quantity;

        @Schema(description = "单价（可选，不填则取SKU价格）")
        private BigDecimal price;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }
}

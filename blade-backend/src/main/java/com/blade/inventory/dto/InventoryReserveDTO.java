package com.blade.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "库存预留DTO")
public class InventoryReserveDTO {

    @NotNull(message = "仓库ID不能为空")
    @Schema(description = "仓库ID")
    private Long warehouseId;

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotEmpty(message = "预留明细不能为空")
    @Schema(description = "预留明细")
    private List<ReserveItemDTO> items;

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public List<ReserveItemDTO> getItems() { return items; }
    public void setItems(List<ReserveItemDTO> items) { this.items = items; }

    @Schema(description = "预留明细项")
    public static class ReserveItemDTO {
        @NotNull(message = "SKU ID不能为空")
        private Long skuId;
        @NotNull(message = "数量不能为空")
        private Integer quantity;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}

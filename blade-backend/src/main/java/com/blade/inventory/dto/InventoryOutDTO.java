package com.blade.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "出库DTO")
public class InventoryOutDTO {

    @NotNull(message = "仓库ID不能为空")
    @Schema(description = "仓库ID")
    private Long warehouseId;

    @NotNull(message = "来源不能为空")
    @Schema(description = "来源：ORDER-订单，OTHER-其他")
    private String source;

    @Schema(description = "订单ID（source=ORDER时必填）")
    private Long orderId;

    @NotEmpty(message = "出库明细不能为空")
    @Schema(description = "出库明细")
    private List<InventoryOutItemDTO> items;

    @Schema(description = "备注")
    private String remark;

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public List<InventoryOutItemDTO> getItems() { return items; }
    public void setItems(List<InventoryOutItemDTO> items) { this.items = items; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

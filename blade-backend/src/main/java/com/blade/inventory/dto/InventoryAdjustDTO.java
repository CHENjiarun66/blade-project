package com.blade.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "库存调整DTO")
public class InventoryAdjustDTO {

    @NotNull(message = "仓库ID不能为空")
    @Schema(description = "仓库ID")
    private Long warehouseId;

    @NotEmpty(message = "调整明细不能为空")
    @Schema(description = "调整明细")
    private List<InventoryAdjustItemDTO> items;

    @NotNull(message = "调整原因不能为空")
    @Schema(description = "调整原因")
    private String reason;

    @Schema(description = "备注")
    private String remark;

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public List<InventoryAdjustItemDTO> getItems() { return items; }
    public void setItems(List<InventoryAdjustItemDTO> items) { this.items = items; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

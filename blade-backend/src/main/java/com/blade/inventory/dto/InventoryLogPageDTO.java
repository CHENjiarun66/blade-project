package com.blade.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "库存记录查询DTO")
public class InventoryLogPageDTO {

    private Long current;
    private Long size;
    private Long skuId;
    private Long warehouseId;
    private String changeType;

    public Long getCurrent() { return current; }
    public void setCurrent(Long current) { this.current = current; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
}

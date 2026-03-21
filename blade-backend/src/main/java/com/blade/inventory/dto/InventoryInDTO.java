package com.blade.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "入库DTO")
public class InventoryInDTO {

    @NotNull(message = "仓库ID不能为空")
    @Schema(description = "仓库ID")
    private Long warehouseId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "供应商名称")
    private String supplierName;

    @NotEmpty(message = "入库明细不能为空")
    @Schema(description = "入库明细")
    private List<InventoryInItemDTO> items;

    @Schema(description = "图片URLs，最多5张")
    private List<String> images;

    @Schema(description = "备注")
    private String remark;

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public List<InventoryInItemDTO> getItems() { return items; }
    public void setItems(List<InventoryInItemDTO> items) { this.items = items; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

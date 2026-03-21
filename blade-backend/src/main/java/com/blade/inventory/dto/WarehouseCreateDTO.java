package com.blade.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "仓库创建DTO")
public class WarehouseCreateDTO {

    @NotBlank(message = "仓库名称不能为空")
    @Size(max = 50, message = "仓库名称最多50位")
    @Schema(description = "仓库名称")
    private String warehouseName;

    @Size(max = 200, message = "地址最多200位")
    @Schema(description = "地址")
    private String address;

    @Size(max = 30, message = "联系人最多30位")
    @Schema(description = "联系人")
    private String contact;

    @Size(max = 20, message = "电话最多20位")
    @Schema(description = "电话")
    private String phone;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status = 1;

    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}

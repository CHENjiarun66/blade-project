package com.blade.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "批量绑定DTO")
public class FileBindingCreateDTO {

    @NotEmpty(message = "fileIds不能为空")
    @Schema(description = "文件ID列表")
    private List<Long> fileIds;

    @NotBlank(message = "businessType不能为空")
    @Schema(description = "业务类型: product/sku/order/inventory_log")
    private String businessType;

    @NotNull(message = "businessId不能为空")
    @Schema(description = "业务对象ID")
    private Long businessId;

    @Schema(description = "绑定角色: main/gallery/sku_image/receipt/source/attachment")
    private String bindRole;

    @Schema(description = "是否主图: 0否 1是")
    private Integer isPrimary;

    public List<Long> getFileIds() { return fileIds; }
    public void setFileIds(List<Long> fileIds) { this.fileIds = fileIds; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getBindRole() { return bindRole; }
    public void setBindRole(String bindRole) { this.bindRole = bindRole; }
    public Integer getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Integer isPrimary) { this.isPrimary = isPrimary; }
}

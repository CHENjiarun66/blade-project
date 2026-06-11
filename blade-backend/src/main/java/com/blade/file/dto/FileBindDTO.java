package com.blade.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class FileBindDTO {

    @NotBlank(message = "业务类型不能为空")
    private String businessType;

    @NotNull(message = "业务ID不能为空")
    private Long businessId;

    @NotEmpty(message = "文件ID不能为空")
    private List<Long> fileIds;

    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public List<Long> getFileIds() { return fileIds; }
    public void setFileIds(List<Long> fileIds) { this.fileIds = fileIds; }
}

package com.blade.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "文件绑定关系视图")
public class FileBindingVO {

    @Schema(description = "绑定ID")
    private Long id;

    @Schema(description = "文件ID")
    private Long fileId;

    @Schema(description = "业务类型")
    private String businessType;

    @Schema(description = "业务对象ID")
    private Long businessId;

    @Schema(description = "绑定角色")
    private String bindRole;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "是否主图")
    private Integer isPrimary;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getBindRole() { return bindRole; }
    public void setBindRole(String bindRole) { this.bindRole = bindRole; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Integer isPrimary) { this.isPrimary = isPrimary; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}

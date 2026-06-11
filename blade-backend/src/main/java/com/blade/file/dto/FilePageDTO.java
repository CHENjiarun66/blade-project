package com.blade.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "文件分页查询DTO")
public class FilePageDTO {

    @Min(value = 1, message = "页码最小为1")
    @Schema(description = "页码")
    private Long current = 1L;

    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    @Schema(description = "每页数量")
    private Long size = 20L;

    @Schema(description = "关键字搜索（文件名、ID）")
    private String keyword;

    @Schema(description = "文件夹ID")
    private Long folderId;

    @Schema(description = "文件类型: IMAGE/VIDEO/DOCUMENT/ARCHIVE/OTHER")
    private String fileType;

    @Schema(description = "业务类型: product/sku/order/inventory_log")
    private String businessType;

    @Schema(description = "绑定状态: true=有业务绑定, false=无绑定")
    private Boolean bound;

    @Schema(description = "文件用途: product/sku/order/inventory/temp")
    private String purpose;

    @Schema(description = "上传人ID")
    private Long createBy;

    @Schema(description = "上传开始时间(yyyy-MM-dd)")
    private String startDate;

    @Schema(description = "上传结束时间(yyyy-MM-dd)")
    private String endDate;

    @Schema(description = "文件状态: 1正常 0禁用")
    private Integer status;

    public Long getCurrent() { return current; }
    public void setCurrent(Long current) { this.current = current; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Boolean getBound() { return bound; }
    public void setBound(Boolean bound) { this.bound = bound; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}

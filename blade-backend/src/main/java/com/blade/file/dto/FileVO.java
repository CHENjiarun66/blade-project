package com.blade.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "文件视图对象")
public class FileVO {

    @Schema(description = "文件ID")
    private Long id;

    @Schema(description = "逻辑文件key")
    private String fileKey;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "存储文件名")
    private String fileName;

    @Schema(description = "MIME类型")
    private String contentType;

    @Schema(description = "文件大小(byte)")
    private Long fileSize;

    @Schema(description = "访问地址")
    private String accessUrl;

    @Schema(description = "文件夹ID")
    private Long folderId;

    @Schema(description = "文件类型: IMAGE/VIDEO/DOCUMENT/ARCHIVE/OTHER")
    private String fileType;

    @Schema(description = "文件扩展名")
    private String fileExt;

    @Schema(description = "上传来源: admin/mobile/ocr/import")
    private String source;

    @Schema(description = "文件用途: product/sku/order/inventory/temp")
    private String purpose;

    @Schema(description = "业务类型")
    private String businessType;

    @Schema(description = "业务ID")
    private Long businessId;

    @Schema(description = "有效绑定数量")
    private Integer bindCount;

    @Schema(description = "可见性: PUBLIC/PRIVATE")
    private String visibility;

    @Schema(description = "图片宽度(px)")
    private Integer imageWidth;

    @Schema(description = "图片高度(px)")
    private Integer imageHeight;

    @Schema(description = "视频时长(秒)")
    private Integer durationSeconds;

    @Schema(description = "视频封面fileId")
    private Long coverFileId;

    @Schema(description = "状态: 1正常 0禁用")
    private Integer status;

    @Schema(description = "是否有业务绑定")
    private Boolean bound;

    @Schema(description = "上传人ID")
    private Long createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "软删除时间")
    private LocalDateTime deletedTime;

    // getters & setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFileKey() { return fileKey; }
    public void setFileKey(String fileKey) { this.fileKey = fileKey; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getAccessUrl() { return accessUrl; }
    public void setAccessUrl(String accessUrl) { this.accessUrl = accessUrl; }
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getFileExt() { return fileExt; }
    public void setFileExt(String fileExt) { this.fileExt = fileExt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public Integer getBindCount() { return bindCount; }
    public void setBindCount(Integer bindCount) { this.bindCount = bindCount; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Integer getImageWidth() { return imageWidth; }
    public void setImageWidth(Integer imageWidth) { this.imageWidth = imageWidth; }
    public Integer getImageHeight() { return imageHeight; }
    public void setImageHeight(Integer imageHeight) { this.imageHeight = imageHeight; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public Long getCoverFileId() { return coverFileId; }
    public void setCoverFileId(Long coverFileId) { this.coverFileId = coverFileId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Boolean getBound() { return bound; }
    public void setBound(Boolean bound) { this.bound = bound; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public LocalDateTime getDeletedTime() { return deletedTime; }
    public void setDeletedTime(LocalDateTime deletedTime) { this.deletedTime = deletedTime; }
}

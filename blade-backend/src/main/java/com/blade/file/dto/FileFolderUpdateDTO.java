package com.blade.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "更新文件夹DTO")
public class FileFolderUpdateDTO {

    @Size(max = 128, message = "文件夹名称不能超过128个字符")
    @Schema(description = "文件夹名称（不传则不更新）")
    private String folderName;

    @Schema(description = "父文件夹ID")
    private Long parentId;

    @Schema(description = "排序")
    private Integer sort;

    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}

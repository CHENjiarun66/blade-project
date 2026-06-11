package com.blade.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "文件夹视图对象（树形）")
public class FileFolderVO {

    @Schema(description = "文件夹ID")
    private Long id;

    @Schema(description = "父文件夹ID")
    private Long parentId;

    @Schema(description = "文件夹名称")
    private String folderName;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "子文件夹列表")
    private List<FileFolderVO> children;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public List<FileFolderVO> getChildren() { return children; }
    public void setChildren(List<FileFolderVO> children) { this.children = children; }
}

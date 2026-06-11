package com.blade.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "批量移动DTO")
public class FileBatchMoveDTO {

    @NotEmpty(message = "fileIds不能为空")
    @Schema(description = "文件ID列表")
    private List<Long> fileIds;

    @Schema(description = "目标文件夹ID（null=移出到未归档）")
    private Long folderId;

    public List<Long> getFileIds() { return fileIds; }
    public void setFileIds(List<Long> fileIds) { this.fileIds = fileIds; }
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
}

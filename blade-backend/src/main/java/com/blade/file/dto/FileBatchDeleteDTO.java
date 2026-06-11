package com.blade.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "批量删除DTO")
public class FileBatchDeleteDTO {

    @NotEmpty(message = "fileIds不能为空")
    @Schema(description = "文件ID列表")
    private List<Long> fileIds;

    public List<Long> getFileIds() { return fileIds; }
    public void setFileIds(List<Long> fileIds) { this.fileIds = fileIds; }
}

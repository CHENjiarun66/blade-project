package com.blade.outlet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "档口分页查询DTO")
public class OutletPageDTO {

    @Schema(description = "当前页")
    private long current = 1;

    @Schema(description = "每页大小")
    private long size = 20;

    @Schema(description = "关键字（档口编码/名称）")
    private String keyword;

    @Schema(description = "状态: 1启用 0禁用")
    private Integer status;

    public long getCurrent() { return current; }
    public void setCurrent(long current) { this.current = current; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}

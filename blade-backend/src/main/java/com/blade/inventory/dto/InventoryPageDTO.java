package com.blade.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "库存分页查询DTO")
public class InventoryPageDTO {

    @Schema(description = "当前页")
    private Long current = 1L;

    @Schema(description = "每页条数")
    private Long size = 20L;

    @Schema(description = "搜索关键词（SKU/商品名称）")
    private String keyword;

    @Schema(description = "仓库ID")
    private Long warehouseId;

    @Schema(description = "商品分类ID")
    private Long categoryId;

    @Schema(description = "预警状态：below-低于阈值，normal-正常")
    private String alertStatus;

    public Long getCurrent() { return current; }
    public void setCurrent(Long current) { this.current = current; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getAlertStatus() { return alertStatus; }
    public void setAlertStatus(String alertStatus) { this.alertStatus = alertStatus; }
}

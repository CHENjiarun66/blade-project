package com.blade.dashboard.dto;

import lombok.Data;

/**
 * 库存预警
 */
@Data
public class InventoryAlertDTO {
    private Long skuId;
    private String skuCode;
    private String productName;
    private String warehouseName;
    private Integer quantity;
    private Integer alertThreshold;
}

package com.blade.dashboard.dto;

import lombok.Data;

/**
 * 订单状态分布
 */
@Data
public class OrderStatusDTO {
    private Integer status;
    private String label;
    private Long count;
}

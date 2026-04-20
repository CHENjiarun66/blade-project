package com.blade.dashboard.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 热销商品数据
 */
@Data
public class TopProductDTO {
    private Long productId;
    private String productName;
    private Long totalQuantity;
    private BigDecimal totalAmount;
}

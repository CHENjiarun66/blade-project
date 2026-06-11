package com.blade.analytics.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AnalyticsRankingDTO {
    private String key;
    private String label;
    private String productName;
    private String skuCode;
    private String colorName;
    private String sizeName;
    private Long orderCount;
    private Long salesQuantity;
    private BigDecimal salesAmount;
    private BigDecimal costAmount;
    private BigDecimal grossProfit;
    private BigDecimal grossProfitRate;
}

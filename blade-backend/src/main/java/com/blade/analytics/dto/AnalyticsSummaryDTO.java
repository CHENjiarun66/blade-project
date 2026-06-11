package com.blade.analytics.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AnalyticsSummaryDTO {
    private Long orderCount;
    private BigDecimal salesAmount;
    private Long salesQuantity;
    private BigDecimal grossProfit;
    private BigDecimal grossProfitRate;
    private BigDecimal refundAmount;
    private BigDecimal avgOrderValue;
    private BigDecimal avgItemPrice;
    private Boolean profitVisible;
}

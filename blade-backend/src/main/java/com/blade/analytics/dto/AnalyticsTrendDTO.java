package com.blade.analytics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AnalyticsTrendDTO {
    private List<String> dates;
    private List<Long> orderCounts;
    private List<BigDecimal> salesAmounts;
    private List<Long> salesQuantities;
    private List<BigDecimal> grossProfits;
    private Boolean profitVisible;
}

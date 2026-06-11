package com.blade.analytics.dto;

import lombok.Data;

import java.util.List;

@Data
public class AnalyticsProductDetailDTO {
    private String productName;
    private List<AnalyticsRankingDTO> skus;
    private List<AnalyticsRankingDTO> colors;
    private List<AnalyticsRankingDTO> sizes;
    private Boolean profitVisible;
}

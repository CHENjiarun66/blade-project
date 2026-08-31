package com.blade.analytics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AnalyticsProductDetailDTO {
    private String productName;
    private List<AnalyticsRankingDTO> skus;
    private List<AnalyticsRankingDTO> colors;
    private List<AnalyticsRankingDTO> sizes;
    private AnalyticsRankingDTO unspecified;
    /** 商品增加规格前产生的 DEFAULT/NA-NA 历史销量。 */
    private AnalyticsRankingDTO historicalNoVariant;
    private Long totalSalesQuantity;
    private Long specifiedSalesQuantity;
    private BigDecimal variantCoverageRate;
    private String variantDataQuality;
    private Boolean profitVisible;
}

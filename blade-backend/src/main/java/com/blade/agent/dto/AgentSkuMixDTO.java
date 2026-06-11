package com.blade.agent.dto;

import com.blade.analytics.dto.AnalyticsRankingDTO;
import com.blade.dashboard.enums.PeriodType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentSkuMixDTO {

    private String productName;
    private PeriodType periodType;
    private List<MixRow> skus;
    private List<MixRow> colors;
    private List<MixRow> sizes;
    private List<String> reasons;

    @Data
    public static class MixRow {
        private String key;
        private String label;
        private String skuCode;
        private String colorName;
        private String sizeName;
        private Long orderCount;
        private Long salesQuantity;
        private BigDecimal salesAmount;
        private String signal;

        public static MixRow from(AnalyticsRankingDTO ranking, String signal) {
            MixRow row = new MixRow();
            row.setKey(ranking.getKey());
            row.setLabel(ranking.getLabel());
            row.setSkuCode(ranking.getSkuCode());
            row.setColorName(ranking.getColorName());
            row.setSizeName(ranking.getSizeName());
            row.setOrderCount(ranking.getOrderCount());
            row.setSalesQuantity(ranking.getSalesQuantity());
            row.setSalesAmount(ranking.getSalesAmount());
            row.setSignal(signal);
            return row;
        }
    }
}

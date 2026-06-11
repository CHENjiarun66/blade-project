package com.blade.agent.dto;

import com.blade.analytics.dto.AnalyticsRankingDTO;
import com.blade.dashboard.enums.PeriodType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class AgentStyleTrendDTO {

    private String dimension;
    private String sortBy;
    private PeriodType periodType;
    private Integer comparePeriods;
    private List<Row> rows;

    public static AgentStyleTrendDTO productSales(PeriodType periodType, List<AnalyticsRankingDTO> rankings) {
        AgentStyleTrendDTO dto = new AgentStyleTrendDTO();
        dto.setDimension("PRODUCT");
        dto.setSortBy("SALES");
        dto.setPeriodType(periodType);
        dto.setComparePeriods(1);
        dto.setRows(rankings.stream().map(Row::from).collect(Collectors.toList()));
        return dto;
    }

    public static AgentStyleTrendDTO productSales(PeriodType periodType, Integer comparePeriods, List<Row> rows) {
        AgentStyleTrendDTO dto = new AgentStyleTrendDTO();
        dto.setDimension("PRODUCT");
        dto.setSortBy("SALES");
        dto.setPeriodType(periodType);
        dto.setComparePeriods(comparePeriods);
        dto.setRows(rows);
        return dto;
    }

    @Data
    public static class Row {
        private String key;
        private String label;
        private String productName;
        private Long orderCount;
        private Long salesQuantity;
        private BigDecimal salesAmount;
        private String trend;
        private String recommendation;
        private List<PeriodPoint> periodSeries;
        private List<String> reasons;

        private static Row from(AnalyticsRankingDTO ranking) {
            Row row = new Row();
            row.setKey(ranking.getKey());
            row.setLabel(ranking.getLabel());
            row.setProductName(ranking.getProductName());
            row.setOrderCount(ranking.getOrderCount());
            row.setSalesQuantity(ranking.getSalesQuantity());
            row.setSalesAmount(ranking.getSalesAmount());
            return row;
        }
    }

    @Data
    public static class PeriodPoint {
        private String periodLabel;
        private LocalDate startDate;
        private LocalDate endDate;
        private Long orderCount;
        private Long salesQuantity;
        private BigDecimal salesAmount;
    }
}

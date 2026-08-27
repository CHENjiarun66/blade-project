package com.blade.agent.service;

import com.blade.agent.dto.AgentSkuMixDTO;
import com.blade.analytics.dto.AnalyticsProductDetailDTO;
import com.blade.analytics.dto.AnalyticsRankingDTO;
import com.blade.analytics.service.AnalyticsService;
import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.dashboard.enums.PeriodType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentSkuMixService {

    private final AnalyticsService analyticsService;

    public AgentSkuMixDTO getSkuMix(DashboardQueryDTO query, String productName, Integer limit) {
        DashboardQueryDTO effectiveQuery = query != null ? query : new DashboardQueryDTO();
        int maxRows = Math.min(Math.max(limit != null ? limit : 20, 1), 100);
        AnalyticsProductDetailDTO detail = analyticsService.getProductDetail(effectiveQuery, productName);

        AgentSkuMixDTO dto = new AgentSkuMixDTO();
        dto.setProductName(detail.getProductName());
        dto.setPeriodType(effectiveQuery.getPeriodType() != null ? effectiveQuery.getPeriodType() : PeriodType.WEEK);
        List<AnalyticsRankingDTO> allSkus = detail.getSkus() != null ? detail.getSkus() : List.of();
        List<AnalyticsRankingDTO> placeholderRows = allSkus.stream().filter(this::isPlaceholder).toList();
        List<AnalyticsRankingDTO> specifiedSkus = allSkus.stream().filter(row -> !isPlaceholder(row)).toList();
        dto.setSkus(toRows(specifiedSkus, maxRows));
        dto.setColors(toRows(filterUnspecified(detail.getColors()), maxRows));
        dto.setSizes(toRows(filterUnspecified(detail.getSizes()), maxRows));
        dto.setUnspecified(detail.getUnspecified() != null
                ? AgentSkuMixDTO.MixRow.from(detail.getUnspecified(), "UNSPECIFIED")
                : aggregateUnspecified(placeholderRows));
        long fallbackUnspecified = dto.getUnspecified() != null ? dto.getUnspecified().getSalesQuantity() : 0L;
        long specifiedQuantity = detail.getSpecifiedSalesQuantity() != null
                ? detail.getSpecifiedSalesQuantity()
                : specifiedSkus.stream().mapToLong(this::safeQuantity).sum();
        long totalQuantity = detail.getTotalSalesQuantity() != null
                ? detail.getTotalSalesQuantity()
                : specifiedQuantity + fallbackUnspecified;
        dto.setTotalSalesQuantity(totalQuantity);
        dto.setSpecifiedSalesQuantity(specifiedQuantity);
        BigDecimal coverage = detail.getVariantCoverageRate() != null
                ? detail.getVariantCoverageRate()
                : totalQuantity > 0
                ? BigDecimal.valueOf(specifiedQuantity)
                        .divide(BigDecimal.valueOf(totalQuantity), 4, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        dto.setVariantCoverageRate(coverage);
        dto.setVariantDataQuality(detail.getVariantDataQuality() != null
                ? detail.getVariantDataQuality()
                : coverage.compareTo(new BigDecimal("0.80")) >= 0
                ? "HIGH"
                : coverage.compareTo(new BigDecimal("0.50")) >= 0 ? "MEDIUM" : "LOW");
        dto.setReasons(buildReasons(dto));
        return dto;
    }

    private List<AnalyticsRankingDTO> filterUnspecified(List<AnalyticsRankingDTO> rankings) {
        return rankings == null ? List.of() : rankings.stream().filter(row -> !isUnspecifiedDimension(row)).toList();
    }

    private boolean isPlaceholder(AnalyticsRankingDTO row) {
        String skuCode = row.getSkuCode() != null ? row.getSkuCode() : row.getKey();
        return skuCode != null && skuCode.endsWith("-UNSPEC-UNSPEC");
    }

    private boolean isUnspecifiedDimension(AnalyticsRankingDTO row) {
        return "未指定颜色".equals(row.getKey()) || "未指定颜色".equals(row.getLabel())
                || "UNSPEC".equalsIgnoreCase(row.getKey()) || "UNSPEC".equalsIgnoreCase(row.getLabel());
    }

    private AgentSkuMixDTO.MixRow aggregateUnspecified(List<AnalyticsRankingDTO> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        AnalyticsRankingDTO aggregate = new AnalyticsRankingDTO();
        aggregate.setKey("UNSPECIFIED");
        aggregate.setLabel("未指定颜色 / 未指定尺码");
        aggregate.setSkuCode("SPU_PLACEHOLDER");
        aggregate.setColorName("未指定颜色");
        aggregate.setSizeName("未指定尺码");
        aggregate.setOrderCount(rows.stream().map(this::safeOrderCount).reduce(0L, Long::sum));
        aggregate.setSalesQuantity(rows.stream().map(this::safeQuantity).reduce(0L, Long::sum));
        aggregate.setSalesAmount(rows.stream().map(this::safeAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return AgentSkuMixDTO.MixRow.from(aggregate, "UNSPECIFIED");
    }

    private List<AgentSkuMixDTO.MixRow> toRows(List<AnalyticsRankingDTO> rankings, int limit) {
        List<AnalyticsRankingDTO> sorted = rankings == null
                ? List.of()
                : rankings.stream()
                        .sorted(Comparator.comparing(this::safeQuantity).reversed())
                        .limit(limit)
                        .toList();
        Long maxQuantity = sorted.stream().map(this::safeQuantity).max(Long::compareTo).orElse(0L);
        Long minQuantity = sorted.stream().map(this::safeQuantity).min(Long::compareTo).orElse(0L);
        List<AgentSkuMixDTO.MixRow> rows = new ArrayList<>();
        for (AnalyticsRankingDTO ranking : sorted) {
            rows.add(AgentSkuMixDTO.MixRow.from(ranking, signal(safeQuantity(ranking), maxQuantity, minQuantity)));
        }
        return rows;
    }

    private String signal(Long quantity, Long maxQuantity, Long minQuantity) {
        if (quantity != null && quantity.equals(maxQuantity) && maxQuantity > 0) {
            return "HOT";
        }
        if (quantity != null && quantity.equals(minQuantity) && maxQuantity > minQuantity) {
            return "LOW";
        }
        return "NORMAL";
    }

    private List<String> buildReasons(AgentSkuMixDTO dto) {
        List<String> reasons = new ArrayList<>();
        if (dto.getSkus() != null && !dto.getSkus().isEmpty()) {
            AgentSkuMixDTO.MixRow topSku = dto.getSkus().get(0);
            reasons.add("SKU " + topSku.getSkuCode() + " 销量最高，销售 " + topSku.getSalesQuantity() + " 件");
        }
        if (dto.getColors() != null && !dto.getColors().isEmpty()) {
            AgentSkuMixDTO.MixRow topColor = dto.getColors().get(0);
            reasons.add("颜色 " + topColor.getLabel() + " 销量最高，销售 " + topColor.getSalesQuantity() + " 件");
        }
        if (dto.getSizes() != null && !dto.getSizes().isEmpty()) {
            AgentSkuMixDTO.MixRow topSize = dto.getSizes().get(0);
            reasons.add("尺码 " + topSize.getLabel() + " 销量最高，销售 " + topSize.getSalesQuantity() + " 件");
        }
        if (dto.getUnspecified() != null && dto.getUnspecified().getSalesQuantity() > 0) {
            reasons.add("有 " + dto.getUnspecified().getSalesQuantity()
                    + " 件仅记录到款号，颜色/尺码分析覆盖率为 "
                    + dto.getVariantCoverageRate().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                    + "%");
        }
        return reasons;
    }

    private Long safeQuantity(AnalyticsRankingDTO ranking) {
        return ranking.getSalesQuantity() != null ? ranking.getSalesQuantity() : 0L;
    }

    private Long safeOrderCount(AnalyticsRankingDTO ranking) {
        return ranking.getOrderCount() != null ? ranking.getOrderCount() : 0L;
    }

    private BigDecimal safeAmount(AnalyticsRankingDTO ranking) {
        return ranking.getSalesAmount() != null ? ranking.getSalesAmount() : BigDecimal.ZERO;
    }
}

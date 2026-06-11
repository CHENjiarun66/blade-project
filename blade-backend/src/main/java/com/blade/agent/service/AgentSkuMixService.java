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
        dto.setSkus(toRows(detail.getSkus(), maxRows));
        dto.setColors(toRows(detail.getColors(), maxRows));
        dto.setSizes(toRows(detail.getSizes(), maxRows));
        dto.setReasons(buildReasons(dto));
        return dto;
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
        return reasons;
    }

    private Long safeQuantity(AnalyticsRankingDTO ranking) {
        return ranking.getSalesQuantity() != null ? ranking.getSalesQuantity() : 0L;
    }
}

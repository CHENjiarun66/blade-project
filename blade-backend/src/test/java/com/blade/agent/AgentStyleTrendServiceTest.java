package com.blade.agent;

import com.blade.agent.dto.AgentStyleTrendDTO;
import com.blade.agent.service.AgentStyleTrendService;
import com.blade.analytics.dto.AnalyticsRankingDTO;
import com.blade.analytics.enums.AnalyticsDimension;
import com.blade.analytics.enums.AnalyticsSortBy;
import com.blade.analytics.service.AnalyticsService;
import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.dashboard.enums.PeriodType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStyleTrendServiceTest {

    private final CapturingAnalyticsService analyticsService = new CapturingAnalyticsService();
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-05-31T00:00:00Z"),
            ZoneId.of("Asia/Shanghai"));
    private final AgentStyleTrendService service = new AgentStyleTrendService(analyticsService, clock);

    @Test
    void getStyleTrends_returnsMultiPeriodTrendFactsWithoutProfitFields() throws Exception {
        DashboardQueryDTO query = new DashboardQueryDTO();
        query.setPeriodType(PeriodType.MONTH);
        analyticsService.thenRankings(
                List.of(ranking("624-1#", "1200.00", 16L, 5L)),
                List.of(ranking("624-1#", "800.00", 10L, 3L)),
                List.of(ranking("624-1#", "300.00", 4L, 1L))
        );

        AgentStyleTrendDTO result = service.getStyleTrends(query, 20, 3);

        assertEquals("PRODUCT", result.getDimension());
        assertEquals("SALES", result.getSortBy());
        assertEquals(PeriodType.MONTH, result.getPeriodType());
        assertEquals(3, result.getComparePeriods());
        assertEquals("624-1#", result.getRows().get(0).getProductName());
        assertEquals(new BigDecimal("1200.00"), result.getRows().get(0).getSalesAmount());
        assertEquals(16L, result.getRows().get(0).getSalesQuantity());
        assertEquals("GROWING", result.getRows().get(0).getTrend());
        assertEquals("KEEP", result.getRows().get(0).getRecommendation());
        assertEquals(3, result.getRows().get(0).getPeriodSeries().size());
        assertEquals("2026-05", result.getRows().get(0).getPeriodSeries().get(0).getPeriodLabel());
        assertEquals(16L, result.getRows().get(0).getPeriodSeries().get(0).getSalesQuantity());
        assertTrue(result.getRows().get(0).getReasons().contains("连续 3 个周期销量增长"));
        assertFalse(Arrays.stream(AgentStyleTrendDTO.Row.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("grossProfit")));
        assertEquals(3, analyticsService.queries.size());
        assertQuery(analyticsService.queries.get(0), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        assertQuery(analyticsService.queries.get(1), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        assertQuery(analyticsService.queries.get(2), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
    }

    @Test
    void getStyleTrends_marksDecliningStylesAsReduce() {
        DashboardQueryDTO query = new DashboardQueryDTO();
        query.setPeriodType(PeriodType.MONTH);
        analyticsService.thenRankings(
                List.of(ranking("70018#01", "240.00", 3L, 1L)),
                List.of(ranking("70018#01", "800.00", 10L, 3L)),
                List.of(ranking("70018#01", "1200.00", 16L, 5L))
        );

        AgentStyleTrendDTO result = service.getStyleTrends(query, 20, 3);

        assertEquals("DECLINING", result.getRows().get(0).getTrend());
        assertEquals("REDUCE", result.getRows().get(0).getRecommendation());
        assertTrue(result.getRows().get(0).getReasons().contains("连续 3 个周期销量下降"));
    }

    private void assertQuery(DashboardQueryDTO query, LocalDate startDate, LocalDate endDate) {
        assertEquals(PeriodType.CUSTOM, query.getPeriodType());
        assertEquals(startDate, query.getStartDate());
        assertEquals(endDate, query.getEndDate());
    }

    private AnalyticsRankingDTO ranking(String productName, String amount, Long quantity, Long orderCount) {
        AnalyticsRankingDTO ranking = new AnalyticsRankingDTO();
        ranking.setKey(productName);
        ranking.setLabel(productName);
        ranking.setProductName(productName);
        ranking.setOrderCount(orderCount);
        ranking.setSalesQuantity(quantity);
        ranking.setSalesAmount(new BigDecimal(amount));
        ranking.setGrossProfit(new BigDecimal("480.00"));
        return ranking;
    }

    private static class CapturingAnalyticsService implements AnalyticsService {
        private DashboardQueryDTO query;
        private AnalyticsDimension dimension;
        private AnalyticsSortBy sortBy;
        private Integer limit;
        private final List<DashboardQueryDTO> queries = new ArrayList<>();
        private final Queue<List<AnalyticsRankingDTO>> rankingResults = new ArrayDeque<>();

        @SafeVarargs
        final void thenRankings(List<AnalyticsRankingDTO>... results) {
            rankingResults.addAll(List.of(results));
        }

        @Override
        public com.blade.analytics.dto.AnalyticsSummaryDTO getSummary(DashboardQueryDTO query) {
            return null;
        }

        @Override
        public com.blade.analytics.dto.AnalyticsTrendDTO getTrend(DashboardQueryDTO query) {
            return null;
        }

        @Override
        public List<AnalyticsRankingDTO> getProductRanking(DashboardQueryDTO query,
                                                           AnalyticsDimension dimension,
                                                           AnalyticsSortBy sortBy,
                                                           Integer limit) {
            this.query = query;
            this.dimension = dimension;
            this.sortBy = sortBy;
            this.limit = limit;
            this.queries.add(query);
            return rankingResults.isEmpty() ? List.of() : rankingResults.remove();
        }

        @Override
        public com.blade.analytics.dto.AnalyticsProductDetailDTO getProductDetail(DashboardQueryDTO query,
                                                                                   String productName) {
            return null;
        }
    }
}

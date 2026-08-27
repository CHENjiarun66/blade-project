package com.blade.agent;

import com.blade.agent.dto.AgentSkuMixDTO;
import com.blade.agent.service.AgentSkuMixService;
import com.blade.analytics.dto.AnalyticsProductDetailDTO;
import com.blade.analytics.dto.AnalyticsRankingDTO;
import com.blade.analytics.enums.AnalyticsDimension;
import com.blade.analytics.enums.AnalyticsSortBy;
import com.blade.analytics.service.AnalyticsService;
import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.dashboard.enums.PeriodType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSkuMixServiceTest {

    private final CapturingAnalyticsService analyticsService = new CapturingAnalyticsService();
    private final AgentSkuMixService service = new AgentSkuMixService(analyticsService);

    @Test
    void getSkuMix_returnsSkuColorSizeFactsWithoutProfitFields() {
        DashboardQueryDTO query = new DashboardQueryDTO();
        query.setPeriodType(PeriodType.MONTH);
        analyticsService.detail = productDetail();

        AgentSkuMixDTO result = service.getSkuMix(query, "624-1#", 10);

        assertEquals("624-1#", result.getProductName());
        assertEquals(PeriodType.MONTH, result.getPeriodType());
        assertEquals("624-1# / 黑 / L", result.getSkus().get(0).getLabel());
        assertEquals("HOT", result.getSkus().get(0).getSignal());
        assertEquals(16L, result.getSkus().get(0).getSalesQuantity());
        assertEquals("黑", result.getColors().get(0).getLabel());
        assertEquals("L", result.getSizes().get(0).getLabel());
        assertTrue(result.getReasons().contains("SKU 624-1#-BLK-L 销量最高，销售 16 件"));
        assertFalse(Arrays.stream(AgentSkuMixDTO.MixRow.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("grossProfit")));
        assertEquals(query, analyticsService.query);
        assertEquals("624-1#", analyticsService.productName);
    }

    @Test
    void getSkuMixSeparatesUnspecifiedSalesAndReportsCoverage() {
        DashboardQueryDTO query = new DashboardQueryDTO();
        AnalyticsProductDetailDTO detail = productDetail();
        detail.setSkus(List.of(
                ranking("624-1#-BLK-L", "624-1# / 黑 / L", "黑", "L", 50L, "3000.00"),
                ranking("624-1#-UNSPEC-UNSPEC", "624-1# / 未指定颜色 / UNSPEC", "未指定颜色", "UNSPEC", 100L, "6000.00")
        ));
        detail.setColors(List.of(
                ranking("黑", "黑", "黑", null, 50L, "3000.00"),
                ranking("未指定颜色", "未指定颜色", "未指定颜色", null, 100L, "6000.00")
        ));
        detail.setSizes(List.of(
                ranking("L", "L", null, "L", 50L, "3000.00"),
                ranking("UNSPEC", "UNSPEC", null, "UNSPEC", 100L, "6000.00")
        ));
        analyticsService.detail = detail;

        AgentSkuMixDTO result = service.getSkuMix(query, "624-1#", 10);

        assertEquals(1, result.getSkus().size());
        assertEquals(100L, result.getUnspecified().getSalesQuantity());
        assertEquals(150L, result.getTotalSalesQuantity());
        assertEquals(50L, result.getSpecifiedSalesQuantity());
        assertEquals(new BigDecimal("0.3333"), result.getVariantCoverageRate());
        assertEquals("LOW", result.getVariantDataQuality());
        assertEquals(List.of("黑"), result.getColors().stream().map(AgentSkuMixDTO.MixRow::getLabel).toList());
    }

    private AnalyticsProductDetailDTO productDetail() {
        AnalyticsProductDetailDTO detail = new AnalyticsProductDetailDTO();
        detail.setProductName("624-1#");
        detail.setSkus(List.of(
                ranking("624-1#-BLK-L", "624-1# / 黑 / L", "黑", "L", 16L, "1200.00"),
                ranking("624-1#-WHT-M", "624-1# / 白 / M", "白", "M", 2L, "120.00")
        ));
        detail.setColors(List.of(
                ranking("黑", "黑", "黑", null, 16L, "1200.00"),
                ranking("白", "白", "白", null, 2L, "120.00")
        ));
        detail.setSizes(List.of(
                ranking("L", "L", null, "L", 16L, "1200.00"),
                ranking("M", "M", null, "M", 2L, "120.00")
        ));
        detail.setProfitVisible(false);
        return detail;
    }

    private AnalyticsRankingDTO ranking(String key,
                                        String label,
                                        String color,
                                        String size,
                                        Long quantity,
                                        String amount) {
        AnalyticsRankingDTO ranking = new AnalyticsRankingDTO();
        ranking.setKey(key);
        ranking.setLabel(label);
        ranking.setProductName("624-1#");
        ranking.setSkuCode(key);
        ranking.setColorName(color);
        ranking.setSizeName(size);
        ranking.setOrderCount(3L);
        ranking.setSalesQuantity(quantity);
        ranking.setSalesAmount(new BigDecimal(amount));
        ranking.setGrossProfit(new BigDecimal("100.00"));
        return ranking;
    }

    private static class CapturingAnalyticsService implements AnalyticsService {
        private DashboardQueryDTO query;
        private String productName;
        private AnalyticsProductDetailDTO detail;

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
            return List.of();
        }

        @Override
        public AnalyticsProductDetailDTO getProductDetail(DashboardQueryDTO query, String productName) {
            this.query = query;
            this.productName = productName;
            return detail;
        }
    }
}

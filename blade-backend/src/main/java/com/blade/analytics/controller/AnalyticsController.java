package com.blade.analytics.controller;

import com.blade.analytics.dto.AnalyticsProductDetailDTO;
import com.blade.analytics.dto.AnalyticsRankingDTO;
import com.blade.analytics.dto.AnalyticsSummaryDTO;
import com.blade.analytics.dto.AnalyticsTrendDTO;
import com.blade.analytics.enums.AnalyticsDimension;
import com.blade.analytics.enums.AnalyticsSortBy;
import com.blade.analytics.service.AnalyticsService;
import com.blade.common.result.R;
import com.blade.dashboard.dto.DashboardQueryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "数据分析")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @Operation(summary = "获取经营分析汇总")
    public R<AnalyticsSummaryDTO> getSummary(@ModelAttribute DashboardQueryDTO query) {
        return R.ok(analyticsService.getSummary(query));
    }

    @GetMapping("/trend")
    @Operation(summary = "获取经营分析趋势")
    public R<AnalyticsTrendDTO> getTrend(@ModelAttribute DashboardQueryDTO query) {
        return R.ok(analyticsService.getTrend(query));
    }

    @GetMapping("/product-ranking")
    @Operation(summary = "获取商品维度排行")
    public R<List<AnalyticsRankingDTO>> getProductRanking(@ModelAttribute DashboardQueryDTO query,
                                                          @RequestParam(defaultValue = "PRODUCT") AnalyticsDimension dimension,
                                                          @RequestParam(defaultValue = "SALES") AnalyticsSortBy sortBy,
                                                          @RequestParam(defaultValue = "20") Integer limit) {
        return R.ok(analyticsService.getProductRanking(query, dimension, sortBy, limit));
    }

    @GetMapping("/product-detail")
    @Operation(summary = "获取商品分析详情")
    public R<AnalyticsProductDetailDTO> getProductDetail(@ModelAttribute DashboardQueryDTO query,
                                                         @RequestParam String productName) {
        return R.ok(analyticsService.getProductDetail(query, productName));
    }
}

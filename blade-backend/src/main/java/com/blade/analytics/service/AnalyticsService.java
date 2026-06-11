package com.blade.analytics.service;

import com.blade.analytics.dto.AnalyticsProductDetailDTO;
import com.blade.analytics.dto.AnalyticsRankingDTO;
import com.blade.analytics.dto.AnalyticsSummaryDTO;
import com.blade.analytics.dto.AnalyticsTrendDTO;
import com.blade.analytics.enums.AnalyticsDimension;
import com.blade.analytics.enums.AnalyticsSortBy;
import com.blade.dashboard.dto.DashboardQueryDTO;

import java.util.List;

public interface AnalyticsService {
    AnalyticsSummaryDTO getSummary(DashboardQueryDTO query);

    AnalyticsTrendDTO getTrend(DashboardQueryDTO query);

    List<AnalyticsRankingDTO> getProductRanking(DashboardQueryDTO query,
                                                AnalyticsDimension dimension,
                                                AnalyticsSortBy sortBy,
                                                Integer limit);

    AnalyticsProductDetailDTO getProductDetail(DashboardQueryDTO query, String productName);
}

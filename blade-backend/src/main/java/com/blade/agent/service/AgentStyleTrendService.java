package com.blade.agent.service;

import com.blade.agent.dto.AgentStyleTrendDTO;
import com.blade.analytics.dto.AnalyticsRankingDTO;
import com.blade.analytics.enums.AnalyticsDimension;
import com.blade.analytics.enums.AnalyticsSortBy;
import com.blade.analytics.service.AnalyticsService;
import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.dashboard.enums.PeriodType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentStyleTrendService {

    private final AnalyticsService analyticsService;
    private final Clock clock;

    @Autowired
    public AgentStyleTrendService(AnalyticsService analyticsService) {
        this(analyticsService, Clock.systemDefaultZone());
    }

    public AgentStyleTrendService(AnalyticsService analyticsService, Clock clock) {
        this.analyticsService = analyticsService;
        this.clock = clock;
    }

    public AgentStyleTrendDTO getStyleTrends(DashboardQueryDTO query, Integer limit) {
        return getStyleTrends(query, limit, 1);
    }

    public AgentStyleTrendDTO getStyleTrends(DashboardQueryDTO query, Integer limit, Integer comparePeriods) {
        DashboardQueryDTO effectiveQuery = query != null ? query : new DashboardQueryDTO();
        PeriodType periodType = effectiveQuery.getPeriodType() != null
                ? effectiveQuery.getPeriodType()
                : PeriodType.WEEK;
        int periods = Math.min(Math.max(comparePeriods != null ? comparePeriods : 3, 1), 6);
        int maxRows = Math.min(Math.max(limit != null ? limit : 20, 1), 100);

        List<PeriodWindow> windows = buildPeriodWindows(effectiveQuery, periodType, periods);
        Map<String, RowBuilder> rowsByKey = new LinkedHashMap<>();
        for (PeriodWindow window : windows) {
            List<AnalyticsRankingDTO> rankings = analyticsService.getProductRanking(
                    toCustomQuery(window), AnalyticsDimension.PRODUCT, AnalyticsSortBy.SALES, maxRows);
            for (AnalyticsRankingDTO ranking : rankings) {
                String key = ranking.getKey() != null ? ranking.getKey() : ranking.getProductName();
                RowBuilder builder = rowsByKey.computeIfAbsent(key, ignored -> new RowBuilder(ranking));
                builder.add(window, ranking);
            }
        }

        List<AgentStyleTrendDTO.Row> rows = rowsByKey.values().stream()
                .map(builder -> builder.toRow(periods))
                .sorted((left, right) -> safeAmount(right.getSalesAmount()).compareTo(safeAmount(left.getSalesAmount())))
                .limit(maxRows)
                .toList();
        return AgentStyleTrendDTO.productSales(periodType, periods, rows);
    }

    private List<PeriodWindow> buildPeriodWindows(DashboardQueryDTO query, PeriodType periodType, int periods) {
        LocalDate today = LocalDate.now(clock);
        List<PeriodWindow> windows = new ArrayList<>();
        for (int offset = 0; offset < periods; offset++) {
            windows.add(buildPeriodWindow(query, periodType, today, offset));
        }
        return windows;
    }

    private PeriodWindow buildPeriodWindow(DashboardQueryDTO query,
                                           PeriodType periodType,
                                           LocalDate today,
                                           int offset) {
        return switch (periodType) {
            case TODAY -> dayWindow(today.minusDays(offset));
            case MONTH -> monthWindow(today, offset);
            case QUARTER -> quarterWindow(today, offset);
            case YEAR -> yearWindow(today, offset);
            case CUSTOM -> customWindow(query, today, offset);
            case WEEK -> weekWindow(today, offset);
        };
    }

    private PeriodWindow dayWindow(LocalDate date) {
        return new PeriodWindow(date.toString(), date, date);
    }

    private PeriodWindow weekWindow(LocalDate today, int offset) {
        LocalDate currentStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate start = currentStart.minusWeeks(offset);
        LocalDate end = offset == 0 ? today : start.plusDays(6);
        return new PeriodWindow(start + "~" + end, start, end);
    }

    private PeriodWindow monthWindow(LocalDate today, int offset) {
        YearMonth month = YearMonth.from(today).minusMonths(offset);
        LocalDate start = month.atDay(1);
        LocalDate end = offset == 0 ? today : month.atEndOfMonth();
        return new PeriodWindow(month.toString(), start, end);
    }

    private PeriodWindow quarterWindow(LocalDate today, int offset) {
        int currentQuarter = ((today.getMonthValue() - 1) / 3) + 1;
        int quarterIndex = currentQuarter - offset;
        int year = today.getYear();
        while (quarterIndex <= 0) {
            quarterIndex += 4;
            year -= 1;
        }
        int startMonth = (quarterIndex - 1) * 3 + 1;
        LocalDate start = LocalDate.of(year, startMonth, 1);
        LocalDate end = offset == 0
                ? today
                : start.plusMonths(3).minusDays(1);
        return new PeriodWindow(year + "-Q" + quarterIndex, start, end);
    }

    private PeriodWindow yearWindow(LocalDate today, int offset) {
        int year = today.getYear() - offset;
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = offset == 0 ? today : LocalDate.of(year, 12, 31);
        return new PeriodWindow(String.valueOf(year), start, end);
    }

    private PeriodWindow customWindow(DashboardQueryDTO query, LocalDate today, int offset) {
        LocalDate end = query.getEndDate() != null ? query.getEndDate() : today;
        LocalDate start = query.getStartDate() != null
                ? query.getStartDate()
                : end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate windowEnd = end.minusDays(days * offset);
        LocalDate windowStart = windowEnd.minusDays(days - 1);
        return new PeriodWindow(windowStart + "~" + windowEnd, windowStart, windowEnd);
    }

    private DashboardQueryDTO toCustomQuery(PeriodWindow window) {
        DashboardQueryDTO query = new DashboardQueryDTO();
        query.setPeriodType(PeriodType.CUSTOM);
        query.setStartDate(window.startDate());
        query.setEndDate(window.endDate());
        return query;
    }

    private static BigDecimal safeAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private record PeriodWindow(String label, LocalDate startDate, LocalDate endDate) {
    }

    private static class RowBuilder {
        private final String key;
        private final String label;
        private final String productName;
        private final List<AgentStyleTrendDTO.PeriodPoint> periodSeries = new ArrayList<>();

        private RowBuilder(AnalyticsRankingDTO ranking) {
            this.key = ranking.getKey();
            this.label = ranking.getLabel();
            this.productName = ranking.getProductName();
        }

        private void add(PeriodWindow window, AnalyticsRankingDTO ranking) {
            AgentStyleTrendDTO.PeriodPoint point = new AgentStyleTrendDTO.PeriodPoint();
            point.setPeriodLabel(window.label());
            point.setStartDate(window.startDate());
            point.setEndDate(window.endDate());
            point.setOrderCount(ranking.getOrderCount() != null ? ranking.getOrderCount() : 0L);
            point.setSalesQuantity(ranking.getSalesQuantity() != null ? ranking.getSalesQuantity() : 0L);
            point.setSalesAmount(safeAmount(ranking.getSalesAmount()));
            periodSeries.add(point);
        }

        private AgentStyleTrendDTO.Row toRow(int comparePeriods) {
            AgentStyleTrendDTO.PeriodPoint latest = periodSeries.isEmpty()
                    ? emptyPoint()
                    : periodSeries.get(0);
            AgentStyleTrendDTO.Row row = new AgentStyleTrendDTO.Row();
            row.setKey(key);
            row.setLabel(label);
            row.setProductName(productName);
            row.setOrderCount(latest.getOrderCount());
            row.setSalesQuantity(latest.getSalesQuantity());
            row.setSalesAmount(latest.getSalesAmount());
            row.setPeriodSeries(periodSeries);
            TrendDecision decision = decideTrend(periodSeries, comparePeriods);
            row.setTrend(decision.trend());
            row.setRecommendation(decision.recommendation());
            row.setReasons(decision.reasons());
            return row;
        }

        private AgentStyleTrendDTO.PeriodPoint emptyPoint() {
            AgentStyleTrendDTO.PeriodPoint point = new AgentStyleTrendDTO.PeriodPoint();
            point.setOrderCount(0L);
            point.setSalesQuantity(0L);
            point.setSalesAmount(BigDecimal.ZERO);
            return point;
        }

        private TrendDecision decideTrend(List<AgentStyleTrendDTO.PeriodPoint> series, int comparePeriods) {
            long nonZeroPeriods = series.stream()
                    .filter(point -> point.getSalesQuantity() != null && point.getSalesQuantity() > 0)
                    .count();
            if (series.size() < 2 || nonZeroPeriods < 2) {
                return new TrendDecision("INSUFFICIENT_DATA", "WATCH", List.of("可用周期数据不足"));
            }
            boolean growing = true;
            boolean declining = true;
            for (int i = 0; i < series.size() - 1; i++) {
                long current = series.get(i).getSalesQuantity();
                long previous = series.get(i + 1).getSalesQuantity();
                growing = growing && current > previous;
                declining = declining && current < previous;
            }
            if (growing) {
                return new TrendDecision("GROWING", "KEEP", List.of("连续 " + comparePeriods + " 个周期销量增长"));
            }
            if (declining) {
                return new TrendDecision("DECLINING", "REDUCE", List.of("连续 " + comparePeriods + " 个周期销量下降"));
            }
            return new TrendDecision("STABLE", "WATCH", List.of("多周期销量未形成连续增长或下降"));
        }
    }

    private record TrendDecision(String trend, String recommendation, List<String> reasons) {
    }
}

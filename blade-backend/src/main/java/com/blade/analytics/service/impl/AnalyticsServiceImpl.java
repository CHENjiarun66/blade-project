package com.blade.analytics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.analytics.dto.AnalyticsProductDetailDTO;
import com.blade.analytics.dto.AnalyticsRankingDTO;
import com.blade.analytics.dto.AnalyticsSummaryDTO;
import com.blade.analytics.dto.AnalyticsTrendDTO;
import com.blade.analytics.enums.AnalyticsDimension;
import com.blade.analytics.enums.AnalyticsSortBy;
import com.blade.analytics.service.AnalyticsService;
import com.blade.common.tenant.TenantContext;
import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.dashboard.enums.PeriodType;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final List<Integer> PAID_PAYMENT_STATUSES = List.of(1, 2);
    private static final String PROFIT_PERMISSION = "data:analytics:profit";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public AnalyticsServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    public AnalyticsSummaryDTO getSummary(DashboardQueryDTO query) {
        boolean profitVisible = hasProfitPermission();
        List<Order> orders = selectPaidOrdersInCurrentPeriod(query);
        List<OrderItem> items = selectItems(orders);

        long orderCount = orders.size();
        long salesQuantity = sumQuantity(items);
        BigDecimal salesAmount = sumNetSales(orders);
        BigDecimal refundAmount = orders.stream()
                .map(order -> safeAmount(order.getRefundAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossProfit = sumNetGrossProfit(orders);

        AnalyticsSummaryDTO dto = new AnalyticsSummaryDTO();
        dto.setOrderCount(orderCount);
        dto.setSalesAmount(salesAmount);
        dto.setSalesQuantity(salesQuantity);
        dto.setRefundAmount(refundAmount);
        dto.setAvgOrderValue(orderCount > 0
                ? salesAmount.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        dto.setAvgItemPrice(salesQuantity > 0
                ? salesAmount.divide(BigDecimal.valueOf(salesQuantity), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        dto.setProfitVisible(profitVisible);
        if (profitVisible) {
            dto.setGrossProfit(grossProfit);
            dto.setGrossProfitRate(rate(grossProfit, salesAmount));
        }
        return dto;
    }

    @Override
    public AnalyticsTrendDTO getTrend(DashboardQueryDTO query) {
        boolean profitVisible = hasProfitPermission();
        LocalDate[] period = getCurrentPeriod(LocalDate.now(), query);
        long days = java.time.temporal.ChronoUnit.DAYS.between(period[0], period[1]) + 1;
        int daysToShow = (int) Math.min(Math.max(days, 1), 365);
        LocalDate firstDate = period[1].minusDays(daysToShow - 1L);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        List<String> dates = new ArrayList<>();
        List<Long> orderCounts = new ArrayList<>();
        List<BigDecimal> salesAmounts = new ArrayList<>();
        List<Long> salesQuantities = new ArrayList<>();
        List<BigDecimal> grossProfits = new ArrayList<>();

        for (int i = 0; i < daysToShow; i++) {
            LocalDate date = firstDate.plusDays(i);
            List<Order> orders = selectPaidOrdersInPeriod(TenantContext.getTenantId(), date, date);
            List<OrderItem> items = selectItems(orders);
            dates.add(date.format(formatter));
            orderCounts.add((long) orders.size());
            salesAmounts.add(sumNetSales(orders));
            salesQuantities.add(sumQuantity(items));
            if (profitVisible) {
                grossProfits.add(sumNetGrossProfit(orders));
            }
        }

        AnalyticsTrendDTO dto = new AnalyticsTrendDTO();
        dto.setDates(dates);
        dto.setOrderCounts(orderCounts);
        dto.setSalesAmounts(salesAmounts);
        dto.setSalesQuantities(salesQuantities);
        dto.setGrossProfits(profitVisible ? grossProfits : null);
        dto.setProfitVisible(profitVisible);
        return dto;
    }

    @Override
    public List<AnalyticsRankingDTO> getProductRanking(DashboardQueryDTO query,
                                                       AnalyticsDimension dimension,
                                                       AnalyticsSortBy sortBy,
                                                       Integer limit) {
        boolean profitVisible = hasProfitPermission();
        List<Order> orders = selectPaidOrdersInCurrentPeriod(query);
        List<OrderItem> items = selectItems(orders);
        return rankItems(items, dimension, sortBy, limit, profitVisible);
    }

    @Override
    public AnalyticsProductDetailDTO getProductDetail(DashboardQueryDTO query, String productName) {
        boolean profitVisible = hasProfitPermission();
        List<Order> orders = selectPaidOrdersInCurrentPeriod(query);
        List<OrderItem> items = selectItems(orders).stream()
                .filter(item -> Objects.equals(safeText(item.getProductName(), "未知商品"), productName))
                .collect(Collectors.toList());

        AnalyticsProductDetailDTO dto = new AnalyticsProductDetailDTO();
        dto.setProductName(productName);
        dto.setSkus(rankItems(items, AnalyticsDimension.SKU, AnalyticsSortBy.SALES, 50, profitVisible));
        dto.setColors(rankItems(items, AnalyticsDimension.COLOR, AnalyticsSortBy.SALES, 50, profitVisible));
        dto.setSizes(rankItems(items, AnalyticsDimension.SIZE, AnalyticsSortBy.SALES, 50, profitVisible));
        dto.setProfitVisible(profitVisible);
        return dto;
    }

    private List<AnalyticsRankingDTO> rankItems(List<OrderItem> items,
                                               AnalyticsDimension dimension,
                                               AnalyticsSortBy sortBy,
                                               Integer limit,
                                               boolean profitVisible) {
        Map<String, AnalyticsRankingDTO> map = new LinkedHashMap<>();
        Map<String, Set<Long>> orderIdsByKey = new HashMap<>();

        for (OrderItem item : items) {
            String key = dimensionKey(item, dimension);
            AnalyticsRankingDTO row = map.computeIfAbsent(key, ignored -> initRanking(item, dimension, key));
            row.setSalesQuantity(row.getSalesQuantity() + safeQuantity(item.getQuantity()));
            row.setSalesAmount(row.getSalesAmount().add(safeAmount(item.getSubtotal())));
            row.setCostAmount(safeAmount(row.getCostAmount()).add(safeAmount(item.getCostAmount())));
            row.setGrossProfit(safeAmount(row.getGrossProfit()).add(safeAmount(item.getGrossProfit())));
            if (item.getOrderId() != null) {
                orderIdsByKey.computeIfAbsent(key, ignored -> new HashSet<>()).add(item.getOrderId());
            }
        }

        for (Map.Entry<String, AnalyticsRankingDTO> entry : map.entrySet()) {
            AnalyticsRankingDTO row = entry.getValue();
            row.setOrderCount((long) orderIdsByKey.getOrDefault(entry.getKey(), Set.of()).size());
            if (profitVisible) {
                row.setGrossProfitRate(rate(safeAmount(row.getGrossProfit()), safeAmount(row.getSalesAmount())));
            } else {
                row.setCostAmount(null);
                row.setGrossProfit(null);
                row.setGrossProfitRate(null);
            }
        }

        AnalyticsSortBy effectiveSort = (!profitVisible && sortBy == AnalyticsSortBy.GROSS_PROFIT)
                ? AnalyticsSortBy.SALES
                : sortBy;
        int maxRows = Math.min(Math.max(limit != null ? limit : 20, 1), 100);

        return map.values().stream()
                .sorted(comparator(effectiveSort).reversed())
                .limit(maxRows)
                .collect(Collectors.toList());
    }

    private AnalyticsRankingDTO initRanking(OrderItem item, AnalyticsDimension dimension, String key) {
        AnalyticsRankingDTO dto = new AnalyticsRankingDTO();
        dto.setKey(key);
        dto.setLabel(key);
        dto.setProductName(safeText(item.getProductName(), "未知商品"));
        dto.setSkuCode(safeText(item.getSkuCode(), "未记录SKU"));
        dto.setColorName(safeText(item.getColorName(), "未记录颜色"));
        dto.setSizeName(safeText(item.getSizeName(), "未记录尺码"));
        if (dimension == AnalyticsDimension.SKU) {
            dto.setLabel(dto.getProductName() + " / " + dto.getColorName() + " / " + dto.getSizeName());
        }
        dto.setOrderCount(0L);
        dto.setSalesQuantity(0L);
        dto.setSalesAmount(BigDecimal.ZERO);
        dto.setCostAmount(BigDecimal.ZERO);
        dto.setGrossProfit(BigDecimal.ZERO);
        return dto;
    }

    private Comparator<AnalyticsRankingDTO> comparator(AnalyticsSortBy sortBy) {
        if (sortBy == AnalyticsSortBy.QUANTITY) {
            return Comparator.comparing(row -> row.getSalesQuantity() != null ? row.getSalesQuantity() : 0L);
        }
        if (sortBy == AnalyticsSortBy.GROSS_PROFIT) {
            return Comparator.comparing(row -> safeAmount(row.getGrossProfit()));
        }
        return Comparator.comparing(row -> safeAmount(row.getSalesAmount()));
    }

    private String dimensionKey(OrderItem item, AnalyticsDimension dimension) {
        return switch (dimension) {
            case SKU -> safeText(item.getSkuCode(), "未记录SKU");
            case COLOR -> safeText(item.getColorName(), "未记录颜色");
            case SIZE -> safeText(item.getSizeName(), "未记录尺码");
            case PRODUCT -> safeText(item.getProductName(), "未知商品");
        };
    }

    private List<Order> selectPaidOrdersInCurrentPeriod(DashboardQueryDTO query) {
        LocalDate[] period = getCurrentPeriod(LocalDate.now(), query);
        return selectPaidOrdersInPeriod(TenantContext.getTenantId(), period[0], period[1]);
    }

    private List<Order> selectPaidOrdersInPeriod(Long tenantId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTenantId, tenantId);
        wrapper.eq(Order::getDeleted, 0);
        wrapper.apply("COALESCE(order_date, DATE(create_time)) BETWEEN {0} AND {1}", startDate, endDate);
        wrapper.and(w -> w.gt(Order::getPaidAmount, BigDecimal.ZERO)
                .or()
                .in(Order::getPaymentStatus, PAID_PAYMENT_STATUSES));
        return orderMapper.selectList(wrapper);
    }

    private List<OrderItem> selectItems(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (orderIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(OrderItem::getOrderId, orderIds);
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            wrapper.eq(OrderItem::getTenantId, tenantId);
        }
        return orderItemMapper.selectList(wrapper);
    }

    private LocalDate[] getCurrentPeriod(LocalDate today, DashboardQueryDTO query) {
        PeriodType periodType = query.getPeriodType() != null ? query.getPeriodType() : PeriodType.WEEK;
        LocalDate start;
        LocalDate end = today;
        switch (periodType) {
            case TODAY:
                start = today;
                break;
            case WEEK:
                start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                break;
            case MONTH:
                start = today.with(TemporalAdjusters.firstDayOfMonth());
                break;
            case QUARTER:
                int month = today.getMonthValue();
                int quarterStartMonth = ((month - 1) / 3) * 3 + 1;
                start = today.withMonth(quarterStartMonth).with(TemporalAdjusters.firstDayOfMonth());
                break;
            case YEAR:
                start = today.with(TemporalAdjusters.firstDayOfYear());
                break;
            case CUSTOM:
            default:
                start = query.getStartDate() != null ? query.getStartDate() : today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                end = query.getEndDate() != null ? query.getEndDate() : today;
                break;
        }
        return new LocalDate[] { start, end };
    }

    private BigDecimal sumNetSales(List<Order> orders) {
        return orders.stream()
                .map(this::netSalesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal netSalesAmount(Order order) {
        BigDecimal netAmount = safeAmount(order.getTotalAmount()).subtract(safeAmount(order.getRefundAmount()));
        return netAmount.compareTo(BigDecimal.ZERO) > 0 ? netAmount : BigDecimal.ZERO;
    }

    private BigDecimal sumNetGrossProfit(List<Order> orders) {
        return orders.stream()
                .map(this::netGrossProfitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal netGrossProfitAmount(Order order) {
        BigDecimal netAmount = safeAmount(order.getGrossProfit()).subtract(safeAmount(order.getRefundAmount()));
        return netAmount.compareTo(BigDecimal.ZERO) > 0 ? netAmount : BigDecimal.ZERO;
    }

    private long sumQuantity(List<OrderItem> items) {
        return items.stream()
                .mapToLong(item -> safeQuantity(item.getQuantity()))
                .sum();
    }

    private long safeQuantity(Integer quantity) {
        return quantity != null ? quantity : 0L;
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return safeAmount(numerator)
                .multiply(new BigDecimal("100"))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private boolean hasProfitPermission() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(authority -> {
            String code = authority.getAuthority();
            return PROFIT_PERMISSION.equals(code)
                    || "ROLE_ROLE_OWNER".equals(code)
                    || "ROLE_ROLE_ADMIN".equals(code)
                    || "ROLE_OWNER".equals(code)
                    || "ROLE_ADMIN".equals(code);
        });
    }
}

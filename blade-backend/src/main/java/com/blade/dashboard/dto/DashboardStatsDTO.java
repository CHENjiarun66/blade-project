package com.blade.dashboard.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 看板统计数据
 */
@Data
public class DashboardStatsDTO {
    /** 周期内订单数（受筛选器影响） */
    private Long periodOrders;
    private Long periodOrdersTrend;
    /** 周期内销售额（受筛选器影响） */
    private BigDecimal periodSales;
    private Long periodSalesTrend;
    private Long totalProducts;
    private Long pendingOrders;
    private Long pendingOrdersTrend;

    // 新增字段
    private Long lowStockAlerts;
    private Long weekOrders;
    private Long weekOrdersTrend;
    private BigDecimal weekSales;
    private Long weekSalesTrend;
    private BigDecimal avgOrderValue;
}

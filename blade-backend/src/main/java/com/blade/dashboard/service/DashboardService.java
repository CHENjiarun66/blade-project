package com.blade.dashboard.service;

import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.dashboard.dto.DashboardStatsDTO;
import com.blade.dashboard.dto.InventoryAlertDTO;
import com.blade.dashboard.dto.OrderStatusDTO;
import com.blade.dashboard.dto.OrderTrendDTO;
import com.blade.dashboard.dto.TopProductDTO;
import java.util.List;

/**
 * 看板统计服务
 */
public interface DashboardService {

    /**
     * 获取看板统计数据
     * @param query 查询参数（周期类型和日期范围）
     */
    DashboardStatsDTO getStats(DashboardQueryDTO query);

    /**
     * 获取订单趋势数据
     * @param query 查询参数（周期类型和日期范围）
     */
    OrderTrendDTO getOrderTrend(DashboardQueryDTO query);

    /**
     * 获取热销商品排行（Top 5）
     * @param query 查询参数（周期类型和日期范围）
     */
    List<TopProductDTO> getTopProducts(DashboardQueryDTO query);

    /**
     * 获取订单状态分布
     * @param query 查询参数（周期类型和日期范围）
     */
    List<OrderStatusDTO> getOrderStatusDistribution(DashboardQueryDTO query);

    /**
     * 获取库存预警列表（不受日期筛选影响）
     */
    List<InventoryAlertDTO> getInventoryAlerts();
}

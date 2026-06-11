package com.blade.dashboard.controller;

import com.blade.common.result.R;
import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.dashboard.dto.InventoryAlertDTO;
import com.blade.dashboard.dto.InventoryStatsVO;
import com.blade.dashboard.dto.OrderStatusDTO;
import com.blade.dashboard.dto.OrderTrendDTO;
import com.blade.dashboard.dto.SilentCustomerResultDTO;
import com.blade.dashboard.dto.TopProductDTO;
import com.blade.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 看板统计控制器
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "看板统计")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @Operation(summary = "获取看板统计数据")
    public R<com.blade.dashboard.dto.DashboardStatsDTO> getStats(@ModelAttribute DashboardQueryDTO query) {
        return R.ok(dashboardService.getStats(query));
    }

    @GetMapping("/trend")
    @Operation(summary = "获取订单趋势数据")
    public R<OrderTrendDTO> getOrderTrend(@ModelAttribute DashboardQueryDTO query) {
        return R.ok(dashboardService.getOrderTrend(query));
    }

    @GetMapping("/top-products")
    @Operation(summary = "获取热销商品排行")
    public R<List<TopProductDTO>> getTopProducts(@ModelAttribute DashboardQueryDTO query) {
        return R.ok(dashboardService.getTopProducts(query));
    }

    @GetMapping("/order-status")
    @Operation(summary = "获取订单状态分布")
    public R<List<OrderStatusDTO>> getOrderStatusDistribution(@ModelAttribute DashboardQueryDTO query) {
        return R.ok(dashboardService.getOrderStatusDistribution(query));
    }

    @GetMapping("/inventory-alerts")
    @Operation(summary = "获取库存预警列表")
    public R<List<InventoryAlertDTO>> getInventoryAlerts() {
        return R.ok(dashboardService.getInventoryAlerts());
    }

    @GetMapping("/silent-customers")
    @Operation(summary = "获取沉默客户列表")
    public R<SilentCustomerResultDTO> getSilentCustomers(@RequestParam(required = false, defaultValue = "90") Integer days) {
        return R.ok(dashboardService.getSilentCustomers(days));
    }

    @GetMapping("/inventory-stats")
    @Operation(summary = "获取库存统计数据（周转分析）")
    public R<InventoryStatsVO> getInventoryStats() {
        return R.ok(dashboardService.getInventoryStats());
    }
}

package com.blade.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.dashboard.dto.*;
import com.blade.dashboard.enums.PeriodType;
import com.blade.dashboard.service.DashboardService;
import com.blade.inventory.entity.Inventory;
import com.blade.inventory.entity.Warehouse;
import com.blade.inventory.mapper.InventoryMapper;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSkuMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final InventoryMapper inventoryMapper;
    private final WarehouseMapper warehouseMapper;

    @Autowired
    public DashboardServiceImpl(OrderMapper orderMapper,
                               OrderItemMapper orderItemMapper,
                               ProductMapper productMapper,
                               ProductSkuMapper productSkuMapper,
                               InventoryMapper inventoryMapper,
                               WarehouseMapper warehouseMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.inventoryMapper = inventoryMapper;
        this.warehouseMapper = warehouseMapper;
    }

    @Override
    public DashboardStatsDTO getStats(DashboardQueryDTO query) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        // 根据周期类型计算日期范围
        LocalDate[] currentPeriod = getCurrentPeriod(today, query);
        LocalDate[] previousPeriod = getPreviousPeriod(query, currentPeriod);

        LocalDateTime startOfPeriod = currentPeriod[0].atStartOfDay();
        LocalDateTime endOfPeriod = currentPeriod[1].atTime(LocalTime.MAX);
        LocalDateTime startOfPrevPeriod = previousPeriod[0].atStartOfDay();
        LocalDateTime endOfPrevPeriod = previousPeriod[1].atTime(LocalTime.MAX);

        // 当前周期订单数
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getTenantId, tenantId)
                .between(Order::getCreateTime, startOfPeriod, endOfPeriod);
        long periodOrders = orderMapper.selectCount(orderWrapper);

        // 上周期订单数（用于计算趋势）
        LambdaQueryWrapper<Order> prevOrderWrapper = new LambdaQueryWrapper<>();
        prevOrderWrapper.eq(Order::getTenantId, tenantId)
                .between(Order::getCreateTime, startOfPrevPeriod, endOfPrevPeriod);
        long previousOrders = orderMapper.selectCount(prevOrderWrapper);
        long ordersTrend = calculateTrend(periodOrders, previousOrders);

        // 当前周期销售额
        LambdaQueryWrapper<Order> salesWrapper = new LambdaQueryWrapper<>();
        salesWrapper.eq(Order::getTenantId, tenantId)
                .between(Order::getCreateTime, startOfPeriod, endOfPeriod)
                .ge(Order::getStatus, 1);
        List<Order> paidOrders = orderMapper.selectList(salesWrapper);
        BigDecimal periodSales = paidOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 上周期销售额
        LambdaQueryWrapper<Order> prevSalesWrapper = new LambdaQueryWrapper<>();
        prevSalesWrapper.eq(Order::getTenantId, tenantId)
                .between(Order::getCreateTime, startOfPrevPeriod, endOfPrevPeriod)
                .ge(Order::getStatus, 1);
        List<Order> prevPaidOrders = orderMapper.selectList(prevSalesWrapper);
        BigDecimal previousSales = prevPaidOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long salesTrend = calculateTrend(periodSales, previousSales);

        // 商品总数
        LambdaQueryWrapper<Product> productWrapper = new LambdaQueryWrapper<>();
        productWrapper.eq(Product::getTenantId, tenantId)
                .eq(Product::getDeleted, 0);
        long totalProducts = productMapper.selectCount(productWrapper);

        // 待处理订单数（状态=0创建的订单）
        LambdaQueryWrapper<Order> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(Order::getTenantId, tenantId)
                .eq(Order::getStatus, 0);
        long pendingOrders = orderMapper.selectCount(pendingWrapper);

        // 待处理订单趋势（与昨天对比）
        LambdaQueryWrapper<Order> yesterdayPendingWrapper = new LambdaQueryWrapper<>();
        yesterdayPendingWrapper.eq(Order::getTenantId, tenantId)
                .eq(Order::getStatus, 0);
        long yesterdayPendingOrders = orderMapper.selectCount(yesterdayPendingWrapper);
        long pendingOrdersTrend = calculateTrend(pendingOrders, yesterdayPendingOrders);

        // 低库存预警数
        LambdaQueryWrapper<Inventory> lowStockWrapper = new LambdaQueryWrapper<>();
        lowStockWrapper.eq(Inventory::getTenantId, tenantId)
                .lt(Inventory::getQuantity, 10);
        long lowStockAlerts = inventoryMapper.selectCount(lowStockWrapper);

        // 本周订单数（周一至今）- 固定统计，不受筛选影响
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LambdaQueryWrapper<Order> weekOrderWrapper = new LambdaQueryWrapper<>();
        weekOrderWrapper.eq(Order::getTenantId, tenantId)
                .between(Order::getCreateTime, weekStart.atStartOfDay(), today.atTime(LocalTime.MAX));
        long weekOrders = orderMapper.selectCount(weekOrderWrapper);

        // 上周同期订单数
        LocalDate lastWeekStart = weekStart.minusDays(7);
        LambdaQueryWrapper<Order> lastWeekOrderWrapper = new LambdaQueryWrapper<>();
        lastWeekOrderWrapper.eq(Order::getTenantId, tenantId)
                .between(Order::getCreateTime, lastWeekStart.atStartOfDay(), weekStart.atStartOfDay().minusSeconds(1));
        long lastWeekOrders = orderMapper.selectCount(lastWeekOrderWrapper);
        long weekOrdersTrend = calculateTrend(weekOrders, lastWeekOrders);

        // 本周销售额
        LambdaQueryWrapper<Order> weekSalesWrapper = new LambdaQueryWrapper<>();
        weekSalesWrapper.eq(Order::getTenantId, tenantId)
                .between(Order::getCreateTime, weekStart.atStartOfDay(), today.atTime(LocalTime.MAX))
                .ge(Order::getStatus, 1);
        List<Order> weekPaidOrders = orderMapper.selectList(weekSalesWrapper);
        BigDecimal weekSales = weekPaidOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 上周同期销售额
        LambdaQueryWrapper<Order> lastWeekSalesWrapper = new LambdaQueryWrapper<>();
        lastWeekSalesWrapper.eq(Order::getTenantId, tenantId)
                .between(Order::getCreateTime, lastWeekStart.atStartOfDay(), weekStart.atStartOfDay().minusSeconds(1))
                .ge(Order::getStatus, 1);
        List<Order> lastWeekPaidOrders = orderMapper.selectList(lastWeekSalesWrapper);
        BigDecimal lastWeekSales = lastWeekPaidOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long weekSalesTrend = calculateTrend(weekSales, lastWeekSales);

        // 平均客单价（使用当前周期数据）
        BigDecimal avgOrderValue = BigDecimal.ZERO;
        if (periodOrders > 0) {
            avgOrderValue = periodSales.divide(new BigDecimal(periodOrders), 2, RoundingMode.HALF_UP);
        }

        DashboardStatsDTO stats = new DashboardStatsDTO();
        stats.setPeriodOrders(periodOrders);
        stats.setPeriodOrdersTrend(ordersTrend);
        stats.setPeriodSales(periodSales);
        stats.setPeriodSalesTrend(salesTrend);
        stats.setTotalProducts(totalProducts);
        stats.setPendingOrders(pendingOrders);
        stats.setPendingOrdersTrend(pendingOrdersTrend);
        stats.setLowStockAlerts(lowStockAlerts);
        stats.setWeekOrders(weekOrders);
        stats.setWeekOrdersTrend(weekOrdersTrend);
        stats.setWeekSales(weekSales);
        stats.setWeekSalesTrend(weekSalesTrend);
        stats.setAvgOrderValue(avgOrderValue);

        return stats;
    }

    @Override
    public OrderTrendDTO getOrderTrend(DashboardQueryDTO query) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        List<String> dates = new ArrayList<>();
        List<Long> orderCounts = new ArrayList<>();
        List<BigDecimal> salesAmounts = new ArrayList<>();

        PeriodType periodType = query.getPeriodType() != null ? query.getPeriodType() : PeriodType.WEEK;

        int daysToShow;
        if (periodType == PeriodType.TODAY) {
            daysToShow = 1;
        } else if (periodType == PeriodType.WEEK) {
            daysToShow = 7;
        } else if (periodType == PeriodType.MONTH) {
            daysToShow = 30;
        } else if (periodType == PeriodType.QUARTER) {
            daysToShow = 90;
        } else if (periodType == PeriodType.YEAR) {
            daysToShow = 365;
        } else {
            // CUSTOM - 显示整个范围内的数据
            LocalDate[] currentPeriod = getCurrentPeriod(today, query);
            long days = java.time.temporal.ChronoUnit.DAYS.between(currentPeriod[0], currentPeriod[1]) + 1;
            daysToShow = (int) Math.min(days, 365);
        }

        for (int i = daysToShow - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

            dates.add(date.format(formatter));

            // 当日订单数
            LambdaQueryWrapper<Order> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(Order::getTenantId, tenantId)
                    .between(Order::getCreateTime, startOfDay, endOfDay);
            long count = orderMapper.selectCount(countWrapper);
            orderCounts.add(count);

            // 当日销售额
            LambdaQueryWrapper<Order> salesWrapper = new LambdaQueryWrapper<>();
            salesWrapper.eq(Order::getTenantId, tenantId)
                    .between(Order::getCreateTime, startOfDay, endOfDay)
                    .ge(Order::getStatus, 1);
            List<Order> orders = orderMapper.selectList(salesWrapper);
            BigDecimal sales = orders.stream()
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            salesAmounts.add(sales);
        }

        OrderTrendDTO trend = new OrderTrendDTO();
        trend.setDates(dates);
        trend.setOrderCounts(orderCounts);
        trend.setSalesAmounts(salesAmounts);
        return trend;
    }

    @Override
    public List<TopProductDTO> getTopProducts(DashboardQueryDTO query) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        // 计算当前周期
        LocalDate[] currentPeriod = getCurrentPeriod(today, query);
        LocalDateTime startOfPeriod = currentPeriod[0].atStartOfDay();
        LocalDateTime endOfPeriod = currentPeriod[1].atTime(LocalTime.MAX);

        // 获取当前周期内已支付的订单IDs
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getTenantId, tenantId)
                .between(Order::getCreateTime, startOfPeriod, endOfPeriod)
                .ge(Order::getStatus, 1);
        List<Order> paidOrders = orderMapper.selectList(orderWrapper);

        if (paidOrders.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> orderIds = paidOrders.stream().map(Order::getId).collect(Collectors.toList());

        // 获取这些订单的明细
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.in(OrderItem::getOrderId, orderIds);
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);

        // 按商品名称分组统计
        Map<String, TopProductDTO> productMap = new HashMap<>();
        for (OrderItem item : items) {
            final String productName = (item.getProductName() == null || item.getProductName().isEmpty()) ? "未知商品" : item.getProductName();

            TopProductDTO dto = productMap.computeIfAbsent(productName, k -> {
                TopProductDTO newDto = new TopProductDTO();
                newDto.setProductName(productName);
                newDto.setTotalQuantity(0L);
                newDto.setTotalAmount(BigDecimal.ZERO);
                return newDto;
            });

            dto.setTotalQuantity(dto.getTotalQuantity() + (item.getQuantity() != null ? item.getQuantity() : 0));
            dto.setTotalAmount(dto.getTotalAmount().add(item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO));
        }

        // 排序并返回Top 5
        return productMap.values().stream()
                .sorted((a, b) -> Long.compare(b.getTotalQuantity(), a.getTotalQuantity()))
                .limit(5)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderStatusDTO> getOrderStatusDistribution(DashboardQueryDTO query) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        // 计算当前周期
        LocalDate[] currentPeriod = getCurrentPeriod(today, query);
        LocalDateTime startOfPeriod = currentPeriod[0].atStartOfDay();
        LocalDateTime endOfPeriod = currentPeriod[1].atTime(LocalTime.MAX);

        // 订单状态映射
        Map<Integer, String> statusLabels = new HashMap<>();
        statusLabels.put(0, "待付款");
        statusLabels.put(1, "已付款");
        statusLabels.put(2, "配货中");
        statusLabels.put(3, "待发货");
        statusLabels.put(4, "已发货");
        statusLabels.put(5, "已完成");
        statusLabels.put(6, "已取消");

        List<OrderStatusDTO> result = new ArrayList<>();

        for (Map.Entry<Integer, String> entry : statusLabels.entrySet()) {
            Integer status = entry.getKey();
            LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Order::getTenantId, tenantId)
                    .between(Order::getCreateTime, startOfPeriod, endOfPeriod)
                    .eq(Order::getStatus, status);
            long count = orderMapper.selectCount(wrapper);

            OrderStatusDTO dto = new OrderStatusDTO();
            dto.setStatus(status);
            dto.setLabel(entry.getValue());
            dto.setCount(count);
            result.add(dto);
        }

        return result;
    }

    @Override
    public List<InventoryAlertDTO> getInventoryAlerts() {
        Long tenantId = TenantContext.getTenantId();

        // 查询低库存（库存低于10）
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getTenantId, tenantId)
                .lt(Inventory::getQuantity, 10)
                .orderByAsc(Inventory::getQuantity);
        List<Inventory> lowStockInventories = inventoryMapper.selectList(wrapper);

        if (lowStockInventories.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取所有仓库Map
        Map<Long, Warehouse> warehouseMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w));

        // 获取SKU的productName
        List<Long> skuIds = lowStockInventories.stream().map(Inventory::getSkuId).collect(Collectors.toList());
        LambdaQueryWrapper<ProductSku> skuWrapper = new LambdaQueryWrapper<>();
        skuWrapper.in(ProductSku::getId, skuIds);
        Map<Long, ProductSku> skuMap = productSkuMapper.selectList(skuWrapper).stream()
                .collect(Collectors.toMap(ProductSku::getId, sku -> sku));

        // 获取productName
        Set<Long> productIds = skuMap.values().stream()
                .map(ProductSku::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        LambdaQueryWrapper<Product> productWrapper = new LambdaQueryWrapper<>();
        productWrapper.eq(Product::getTenantId, tenantId)
                .in(productIds.size() > 0, Product::getId, productIds);
        Map<Long, Product> productMap = productMapper.selectList(productWrapper).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<InventoryAlertDTO> alerts = new ArrayList<>();
        for (Inventory inv : lowStockInventories) {
            InventoryAlertDTO dto = new InventoryAlertDTO();
            dto.setSkuId(inv.getSkuId());
            dto.setQuantity(inv.getQuantity());
            dto.setAlertThreshold(inv.getAlertThreshold() != null ? inv.getAlertThreshold() : 10);

            ProductSku sku = skuMap.get(inv.getSkuId());
            if (sku != null) {
                dto.setSkuCode(sku.getSkuCode());
                Product product = productMap.get(sku.getProductId());
                if (product != null) {
                    dto.setProductName(product.getName());
                }
            }

            Warehouse warehouse = warehouseMap.get(inv.getWarehouseId());
            if (warehouse != null) {
                dto.setWarehouseName(warehouse.getWarehouseName());
            }

            alerts.add(dto);
        }

        return alerts;
    }

    /**
     * 根据周期类型计算当前周期日期范围
     */
    private LocalDate[] getCurrentPeriod(LocalDate today, DashboardQueryDTO query) {
        PeriodType periodType = query.getPeriodType() != null ? query.getPeriodType() : PeriodType.WEEK;
        LocalDate start, end = today;

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

    /**
     * 计算上一个同等周期的日期范围
     */
    private LocalDate[] getPreviousPeriod(DashboardQueryDTO query, LocalDate[] currentPeriod) {
        PeriodType periodType = query.getPeriodType() != null ? query.getPeriodType() : PeriodType.WEEK;
        LocalDate currentStart = currentPeriod[0];
        LocalDate currentEnd = currentPeriod[1];
        LocalDate prevStart, prevEnd;

        switch (periodType) {
            case TODAY:
                prevStart = currentStart.minusDays(1);
                prevEnd = currentEnd.minusDays(1);
                break;
            case WEEK:
                prevStart = currentStart.minusWeeks(1);
                prevEnd = currentEnd.minusWeeks(1);
                break;
            case MONTH:
                prevStart = currentStart.minusMonths(1);
                prevEnd = currentEnd.minusMonths(1);
                break;
            case QUARTER:
                prevStart = currentStart.minusMonths(3);
                prevEnd = currentEnd.minusMonths(3);
                break;
            case YEAR:
                prevStart = currentStart.minusYears(1);
                prevEnd = currentEnd.minusYears(1);
                break;
            case CUSTOM:
            default:
                // 自定义周期：往前平移同样的天数
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(currentStart, currentEnd);
                prevStart = currentStart.minusDays(daysBetween);
                prevEnd = currentEnd.minusDays(daysBetween);
                break;
        }

        return new LocalDate[] { prevStart, prevEnd };
    }

    /**
     * 计算趋势百分比
     */
    private long calculateTrend(long current, long previous) {
        if (previous > 0) {
            return ((current - previous) * 100) / previous;
        }
        return current > 0 ? 100 : 0;
    }

    /**
     * 计算销售额趋势
     */
    private long calculateTrend(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) > 0) {
            return current.subtract(previous).multiply(new BigDecimal("100"))
                    .divide(previous, 0, RoundingMode.HALF_UP).longValue();
        }
        return current.compareTo(BigDecimal.ZERO) > 0 ? 100 : 0;
    }
}

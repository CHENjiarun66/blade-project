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

    private static final List<Integer> PAID_PAYMENT_STATUSES = Arrays.asList(1, 2);

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

        // 当前周期已产生收款的订单数与应收净额
        List<Order> paidOrders = selectPaidOrdersInPeriod(tenantId, currentPeriod[0], currentPeriod[1]);
        long periodOrders = paidOrders.size();
        BigDecimal periodSales = sumNetSales(paidOrders);
        BigDecimal periodGrossProfit = sumNetGrossProfit(paidOrders);
        long periodSalesQuantity = sumSalesQuantity(paidOrders);

        // 上周期已产生收款的订单数与应收净额（用于计算趋势）
        List<Order> prevPaidOrders = selectPaidOrdersInPeriod(tenantId, previousPeriod[0], previousPeriod[1]);
        long previousOrders = prevPaidOrders.size();
        BigDecimal previousSales = sumNetSales(prevPaidOrders);
        BigDecimal previousGrossProfit = sumNetGrossProfit(prevPaidOrders);
        long previousSalesQuantity = sumSalesQuantity(prevPaidOrders);
        long ordersTrend = calculateTrend(periodOrders, previousOrders);
        long salesTrend = calculateTrend(periodSales, previousSales);
        long grossProfitTrend = calculateTrend(periodGrossProfit, previousGrossProfit);
        long salesQuantityTrend = calculateTrend(periodSalesQuantity, previousSalesQuantity);

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
        List<Order> weekPaidOrders = selectPaidOrdersInPeriod(tenantId, weekStart, today);
        long weekOrders = weekPaidOrders.size();

        // 上周同期订单数
        LocalDate lastWeekStart = weekStart.minusDays(7);
        LocalDate lastWeekEnd = today.minusDays(7);
        List<Order> lastWeekPaidOrders = selectPaidOrdersInPeriod(tenantId, lastWeekStart, lastWeekEnd);
        long lastWeekOrders = lastWeekPaidOrders.size();
        long weekOrdersTrend = calculateTrend(weekOrders, lastWeekOrders);

        // 本周销售额
        BigDecimal weekSales = sumNetSales(weekPaidOrders);
        BigDecimal weekGrossProfit = sumNetGrossProfit(weekPaidOrders);

        // 上周同期销售额
        BigDecimal lastWeekSales = sumNetSales(lastWeekPaidOrders);
        BigDecimal lastWeekGrossProfit = sumNetGrossProfit(lastWeekPaidOrders);
        long weekSalesTrend = calculateTrend(weekSales, lastWeekSales);
        long weekGrossProfitTrend = calculateTrend(weekGrossProfit, lastWeekGrossProfit);

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
        stats.setPeriodGrossProfit(periodGrossProfit);
        stats.setPeriodGrossProfitTrend(grossProfitTrend);
        stats.setPeriodSalesQuantity(periodSalesQuantity);
        stats.setPeriodSalesQuantityTrend(salesQuantityTrend);
        stats.setTotalProducts(totalProducts);
        stats.setPendingOrders(pendingOrders);
        stats.setPendingOrdersTrend(pendingOrdersTrend);
        stats.setLowStockAlerts(lowStockAlerts);
        stats.setWeekOrders(weekOrders);
        stats.setWeekOrdersTrend(weekOrdersTrend);
        stats.setWeekSales(weekSales);
        stats.setWeekSalesTrend(weekSalesTrend);
        stats.setWeekGrossProfit(weekGrossProfit);
        stats.setWeekGrossProfitTrend(weekGrossProfitTrend);
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

        LocalDate[] currentPeriod = getCurrentPeriod(today, query);
        long days = java.time.temporal.ChronoUnit.DAYS.between(currentPeriod[0], currentPeriod[1]) + 1;
        int daysToShow = (int) Math.min(Math.max(days, 1), 365);
        LocalDate firstDate = currentPeriod[1].minusDays(daysToShow - 1L);

        for (int i = 0; i < daysToShow; i++) {
            LocalDate date = firstDate.plusDays(i);

            dates.add(date.format(formatter));

            // 当日订单数
            List<Order> orders = selectPaidOrdersInPeriod(tenantId, date, date);
            orderCounts.add((long) orders.size());
            salesAmounts.add(sumNetSales(orders));
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
        // 获取当前周期内已产生收款的订单IDs
        List<Order> paidOrders = selectPaidOrdersInPeriod(tenantId, currentPeriod[0], currentPeriod[1]);

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

        // 订单状态映射
        Map<Integer, String> statusLabels = new LinkedHashMap<>();
        statusLabels.put(0, "待付款");
        statusLabels.put(1, "已付款");
        statusLabels.put(2, "配货中");
        statusLabels.put(3, "待发货");
        statusLabels.put(4, "已发货");
        statusLabels.put(5, "已完成");
        statusLabels.put(6, "已取消");
        statusLabels.put(7, "退货中");
        statusLabels.put(8, "已退货");

        List<OrderStatusDTO> result = new ArrayList<>();

        for (Map.Entry<Integer, String> entry : statusLabels.entrySet()) {
            Integer status = entry.getKey();
            LambdaQueryWrapper<Order> wrapper = buildPaidOrderPeriodWrapper(tenantId, currentPeriod[0], currentPeriod[1])
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

    private List<Order> selectPaidOrdersInPeriod(Long tenantId, LocalDate startDate, LocalDate endDate) {
        return orderMapper.selectList(buildPaidOrderPeriodWrapper(tenantId, startDate, endDate));
    }

    private LambdaQueryWrapper<Order> buildPaidOrderPeriodWrapper(Long tenantId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTenantId, tenantId);
        applyOrderDateRange(wrapper, startDate, endDate);
        applyPaidOrderCondition(wrapper);
        return wrapper;
    }

    private void applyOrderDateRange(LambdaQueryWrapper<Order> wrapper, LocalDate startDate, LocalDate endDate) {
        wrapper.apply("COALESCE(order_date, DATE(create_time)) BETWEEN {0} AND {1}", startDate, endDate);
    }

    private void applyPaidOrderCondition(LambdaQueryWrapper<Order> wrapper) {
        wrapper.and(w -> w.gt(Order::getPaidAmount, BigDecimal.ZERO)
                .or()
                .in(Order::getPaymentStatus, PAID_PAYMENT_STATUSES));
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

    private long sumSalesQuantity(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return 0L;
        }
        List<Long> orderIds = orders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (orderIds.isEmpty()) {
            return 0L;
        }

        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.in(OrderItem::getOrderId, orderIds);
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);
        return items.stream()
                .mapToLong(item -> item.getQuantity() != null ? item.getQuantity() : 0L)
                .sum();
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
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

    @Override
    public SilentCustomerResultDTO getSilentCustomers(Integer days) {
        Long tenantId = TenantContext.getTenantId();
        int silentDays = days != null ? days : 90;
        LocalDate cutoffDate = LocalDate.now().minusDays(silentDays);

        // 找出有已完成订单但最后订单距今 > silentDays 的客户
        // 首先获取有已完成订单的客户及其最后订单日期
        LambdaQueryWrapper<Order> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.eq(Order::getTenantId, tenantId)
                .ge(Order::getStatus, 4); // 已发货或已完成
        List<Order> completedOrders = orderMapper.selectList(completedWrapper);

        if (completedOrders.isEmpty()) {
            SilentCustomerResultDTO result = new SilentCustomerResultDTO();
            result.setTotal(0);
            result.setCustomers(new ArrayList<>());
            return result;
        }

        // 按客户分组，找出每个客户的最后订单日期
        Map<Long, LocalDate> customerLastOrderDate = new HashMap<>();
        for (Order order : completedOrders) {
            LocalDate orderDate = order.getCreateTime().toLocalDate();
            customerLastOrderDate.merge(order.getCustomerId(), orderDate, (d1, d2) -> d1.isAfter(d2) ? d1 : d2);
        }

        // 过滤出最后订单日期早于截止日期的客户
        List<Long> silentCustomerIds = customerLastOrderDate.entrySet().stream()
                .filter(e -> e.getValue().isBefore(cutoffDate))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (silentCustomerIds.isEmpty()) {
            SilentCustomerResultDTO result = new SilentCustomerResultDTO();
            result.setTotal(0);
            result.setCustomers(new ArrayList<>());
            return result;
        }

        // 查询沉默客户的详细信息
        // 这里需要通过客户手机表获取电话，需要关联查询
        // 简化处理：直接从customer表查询（假设有customer表）
        List<SilentCustomerDTO> silentCustomers = new ArrayList<>();
        for (Long customerId : silentCustomerIds) {
            // 获取客户信息
            LambdaQueryWrapper<Order> customerOrderWrapper = new LambdaQueryWrapper<>();
            customerOrderWrapper.eq(Order::getTenantId, tenantId)
                    .eq(Order::getCustomerId, customerId)
                    .orderByDesc(Order::getCreateTime)
                    .last("LIMIT 1");
            Order lastOrder = orderMapper.selectOne(customerOrderWrapper);

            if (lastOrder != null) {
                SilentCustomerDTO dto = new SilentCustomerDTO();
                dto.setId(customerId);
                dto.setLastOrderDate(lastOrder.getCreateTime().toLocalDate().toString());
                dto.setDaysSinceLastOrder((int) java.time.temporal.ChronoUnit.DAYS.between(lastOrder.getCreateTime().toLocalDate(), LocalDate.now()));
                silentCustomers.add(dto);
            }
        }

        SilentCustomerResultDTO result = new SilentCustomerResultDTO();
        result.setTotal(silentCustomers.size());
        result.setCustomers(silentCustomers);
        return result;
    }

    @Override
    public InventoryStatsVO getInventoryStats() {
        Long tenantId = TenantContext.getTenantId();

        // 1. 当前库存总量
        LambdaQueryWrapper<Inventory> totalWrapper = new LambdaQueryWrapper<>();
        totalWrapper.eq(Inventory::getTenantId, tenantId);
        List<Inventory> allInventory = inventoryMapper.selectList(totalWrapper);
        long totalQuantity = allInventory.stream()
                .mapToLong(inv -> inv.getQuantity() != null ? inv.getQuantity() : 0L)
                .sum();
        long totalSkuCount = allInventory.stream()
                .map(Inventory::getSkuId)
                .distinct()
                .count();

        // 2. 低库存预警数（< 10）
        LambdaQueryWrapper<Inventory> lowStockWrapper = new LambdaQueryWrapper<>();
        lowStockWrapper.eq(Inventory::getTenantId, tenantId)
                .lt(Inventory::getQuantity, 10);
        long lowStockCount = inventoryMapper.selectCount(lowStockWrapper);

        // 3. 高库存积压数（> 100）
        LambdaQueryWrapper<Inventory> overstockWrapper = new LambdaQueryWrapper<>();
        overstockWrapper.eq(Inventory::getTenantId, tenantId)
                .gt(Inventory::getQuantity, 100);
        long overstockCount = inventoryMapper.selectCount(overstockWrapper);

        // 4. 计算库存周转率（基于过去90天已发货订单的销售量 / 平均库存）
        LocalDate today = LocalDate.now();
        LocalDate ninetyDaysAgo = today.minusDays(90);
        LocalDateTime startDate = ninetyDaysAgo.atStartOfDay();
        LocalDateTime endDate = today.atTime(LocalTime.MAX);

        // 过去90天已发货/已完成的订单
        LambdaQueryWrapper<Order> shippedWrapper = new LambdaQueryWrapper<>();
        shippedWrapper.eq(Order::getTenantId, tenantId)
                .between(Order::getCreateTime, startDate, endDate)
                .ge(Order::getStatus, 4);
        List<Order> shippedOrders = orderMapper.selectList(shippedWrapper);

        long totalSoldQuantity = 0L;
        if (!shippedOrders.isEmpty()) {
            List<Long> orderIds = shippedOrders.stream().map(Order::getId).collect(Collectors.toList());
            LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.in(OrderItem::getOrderId, orderIds);
            List<OrderItem> items = orderItemMapper.selectList(itemWrapper);
            totalSoldQuantity = items.stream()
                    .mapToLong(item -> item.getQuantity() != null ? item.getQuantity() : 0L)
                    .sum();
        }

        // 平均库存 = (期初库存 + 期末库存) / 2，这里简化为当前库存
        // 周转率 = 销售数量 / 平均库存
        BigDecimal turnoverRate = BigDecimal.ZERO;
        if (totalQuantity > 0) {
            // 周转率 = 90天内销售量 / 当前库存量（简化计算）
            turnoverRate = new BigDecimal(totalSoldQuantity)
                    .divide(new BigDecimal(totalQuantity), 2, RoundingMode.HALF_UP);
        }

        InventoryStatsVO vo = new InventoryStatsVO();
        vo.setTurnoverRate(turnoverRate);
        vo.setTotalQuantity(totalQuantity);
        vo.setTotalSkuCount(totalSkuCount);
        vo.setLowStockCount(lowStockCount);
        vo.setOverstockCount(overstockCount);
        return vo;
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

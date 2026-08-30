package com.blade.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.inventory.entity.Warehouse;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.order.dto.AdjustmentLogDTO;
import com.blade.order.dto.DeliveryPlanDTO;
import com.blade.order.dto.DeliveryPlanVO;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderAdjustmentLog;
import com.blade.order.entity.OrderDeliveryPlan;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.service.OrderActionService;
import com.blade.order.service.OrderDeliveryPlanService;
import com.blade.product.entity.ProductColor;
import com.blade.product.entity.ProductSku;
import com.blade.product.entity.ProductSize;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配货计划服务实现
 */
@Service
public class OrderDeliveryPlanServiceImpl implements OrderDeliveryPlanService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderDeliveryPlanMapper deliveryPlanMapper;
    private final OrderAdjustmentLogMapper adjustmentLogMapper;
    private final OrderActionService actionService;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final ProductColorMapper colorMapper;
    private final ProductSizeMapper sizeMapper;
    private final WarehouseMapper warehouseMapper;
    private final UserMapper userMapper;

    @Autowired
    public OrderDeliveryPlanServiceImpl(OrderMapper orderMapper,
                                       OrderItemMapper orderItemMapper,
                                       OrderDeliveryPlanMapper deliveryPlanMapper,
                                       OrderAdjustmentLogMapper adjustmentLogMapper,
                                       OrderActionService actionService,
                                       ProductSkuMapper productSkuMapper,
                                       ProductMapper productMapper,
                                       ProductColorMapper colorMapper,
                                       ProductSizeMapper sizeMapper,
                                       WarehouseMapper warehouseMapper,
                                       UserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.deliveryPlanMapper = deliveryPlanMapper;
        this.adjustmentLogMapper = adjustmentLogMapper;
        this.actionService = actionService;
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
        this.colorMapper = colorMapper;
        this.sizeMapper = sizeMapper;
        this.warehouseMapper = warehouseMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public List<DeliveryPlanVO> createDeliveryPlan(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        // 状态前置：经统一动作服务校验（STOCK_LINKED + 待配货）并进入配货中；
        // 失败（含历史未迁移订单）整体回滚
        actionService.startAllocation(orderId, "PC");

        // 检查是否已有配货计划
        LambdaQueryWrapper<OrderDeliveryPlan> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(OrderDeliveryPlan::getOrderId, orderId);
        long count = deliveryPlanMapper.selectCount(existingWrapper);
        if (count > 0) {
            throw new RuntimeException("该订单已有配货计划，请先删除或更新");
        }

        // 获取订单明细
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> orderItems = orderItemMapper.selectList(itemWrapper);

        // 获取SKU信息Map
        Map<Long, ProductSku> skuMap = productSkuMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProductSku::getId, sku -> sku));

        // 获取仓库Map
        Map<Long, Warehouse> warehouseMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w));

        List<OrderDeliveryPlan> plans = new ArrayList<>();
        for (OrderItem item : orderItems) {
            OrderDeliveryPlan plan = new OrderDeliveryPlan();
            plan.setOrderId(orderId);
            plan.setOrderItemId(item.getId());
            plan.setSkuId(item.getSkuId());
            plan.setPlannedQty(item.getQuantity());
            plan.setAllocatedQty(item.getQuantity()); // 默认配货数量等于订单数量
            plan.setOutQty(0);
            plan.setStatus(OrderDeliveryPlan.Status.PENDING);
            plan.setTenantId(TenantContext.getTenantId());
            plans.add(plan);
        }

        // 批量保存
        for (OrderDeliveryPlan plan : plans) {
            deliveryPlanMapper.insert(plan);
        }

        // 订单状态已由 startAllocation 动作推进到 ALLOCATING（调整状态由动作维护）

        return getDeliveryPlanByOrderId(orderId);
    }

    @Override
    @Transactional
    public List<DeliveryPlanVO> updateDeliveryPlan(Long orderId, DeliveryPlanDTO dto) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        // 状态前置：只有配货中的订单可以调整方案
        requireAllocating(order);

        // 删除旧的配货计划
        LambdaQueryWrapper<OrderDeliveryPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDeliveryPlan::getOrderId, orderId);
        deliveryPlanMapper.delete(wrapper);

        // 获取SKU信息Map
        Map<Long, ProductSku> skuMap = productSkuMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProductSku::getId, sku -> sku));

        // 获取仓库Map
        Map<Long, Warehouse> warehouseMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w));

        // 创建新的配货计划
        List<OrderDeliveryPlan> plans = new ArrayList<>();
        for (DeliveryPlanDTO.PlanItemDTO itemDto : dto.getItems()) {
            OrderDeliveryPlan plan = new OrderDeliveryPlan();
            plan.setOrderId(orderId);
            plan.setOrderItemId(itemDto.getOrderItemId());
            plan.setSkuId(itemDto.getSkuId());
            plan.setWarehouseId(itemDto.getWarehouseId());
            plan.setPlannedQty(itemDto.getPlannedQty());
            plan.setAllocatedQty(itemDto.getAllocatedQty());
            plan.setOutQty(0);
            plan.setStatus(OrderDeliveryPlan.Status.PENDING);
            plan.setRemark(itemDto.getRemark());
            plan.setTenantId(TenantContext.getTenantId());
            plans.add(plan);
        }

        // 批量保存
        for (OrderDeliveryPlan plan : plans) {
            deliveryPlanMapper.insert(plan);
        }

        return getDeliveryPlanByOrderId(orderId);
    }

    @Override
    public List<DeliveryPlanVO> getDeliveryPlanByOrderId(Long orderId) {
        LambdaQueryWrapper<OrderDeliveryPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDeliveryPlan::getOrderId, orderId);
        List<OrderDeliveryPlan> plans = deliveryPlanMapper.selectList(wrapper);

        // 获取SKU信息
        Map<Long, ProductSku> skuMap = productSkuMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProductSku::getId, sku -> sku));

        // 获取商品信息Map
        Map<Long, com.blade.product.entity.Product> productMap = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(com.blade.product.entity.Product::getId, p -> p));

        // 获取颜色信息Map
        Map<Long, ProductColor> colorMap = colorMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProductColor::getId, c -> c));

        // 获取尺码信息Map
        Map<Long, ProductSize> sizeMap = sizeMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProductSize::getId, s -> s));

        // 获取仓库Map
        Map<Long, Warehouse> warehouseMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w));

        // 获取订单信息
        Order order = orderMapper.selectById(orderId);

        return plans.stream().map(plan -> {
            DeliveryPlanVO vo = new DeliveryPlanVO();
            BeanUtils.copyProperties(plan, vo);

            // 填充SKU信息
            ProductSku sku = skuMap.get(plan.getSkuId());
            if (sku != null) {
                vo.setSkuCode(sku.getSkuCode());

                // 填充商品名称
                if (sku.getProductId() != null) {
                    com.blade.product.entity.Product product = productMap.get(sku.getProductId());
                    if (product != null) {
                        vo.setProductName(product.getName());
                    }
                }

                // 填充颜色名称
                if (sku.getColorId() != null) {
                    ProductColor color = colorMap.get(sku.getColorId());
                    if (color != null) {
                        vo.setColorName(color.getColorName());
                    }
                }

                // 填充尺码名称
                if (sku.getSizeId() != null) {
                    ProductSize size = sizeMap.get(sku.getSizeId());
                    if (size != null) {
                        vo.setSizeName(size.getSizeCode());
                    }
                }
            }

            // 填充仓库名称
            if (plan.getWarehouseId() != null) {
                Warehouse warehouse = warehouseMap.get(plan.getWarehouseId());
                if (warehouse != null) {
                    vo.setWarehouseName(warehouse.getWarehouseName());
                }
            }

            // 填充订单号
            if (order != null) {
                vo.setOrderNo(order.getOrderNo());
            }

            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteDeliveryPlan(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        // 删除配货计划
        LambdaQueryWrapper<OrderDeliveryPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDeliveryPlan::getOrderId, orderId);
        deliveryPlanMapper.delete(wrapper);

        // 新行：只有配货中的订单可以删除计划，经动作服务回到待配货；
        // 不会把已发货/已完成订单拉回旧状态
        if (order.getFulfillmentStatus() != null) {
            actionService.revertAllocationToWaiting(orderId, "PC");
        } else {
            // 历史未迁移行保持旧语义（带前置校验）
            requireLegacyAllocating(order);
            LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Order::getId, orderId)
                    .set(Order::getStatus, 1)  // PAID
                    .set(Order::getAdjustmentStatus, Order.AdjustmentStatus.NONE);
            orderMapper.update(null, updateWrapper);
        }
    }

    @Override
    @Transactional
    public void recordAdjustment(AdjustmentLogDTO dto) {
        Order order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        // 获取当前用户
        User currentUser = getCurrentUser();

        OrderAdjustmentLog log = new OrderAdjustmentLog();
        log.setOrderId(dto.getOrderId());
        log.setAdjustmentType(dto.getAdjustmentType());
        log.setOriginalSkuId(dto.getOriginalSkuId());
        log.setOriginalQuantity(dto.getOriginalQuantity());
        log.setNewSkuId(dto.getNewSkuId());
        log.setNewQuantity(dto.getNewQuantity());
        log.setReason(dto.getReason());
        log.setOperatorId(currentUser != null ? currentUser.getId() : null);
        log.setOperatorName(currentUser != null ? currentUser.getNickname() : null);
        log.setTenantId(TenantContext.getTenantId());

        adjustmentLogMapper.insert(log);
    }

    @Override
    @Transactional
    public void confirmAdjustment(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        // 获取配货计划
        LambdaQueryWrapper<OrderDeliveryPlan> planQueryWrapper = new LambdaQueryWrapper<>();
        planQueryWrapper.eq(OrderDeliveryPlan::getOrderId, orderId);
        List<OrderDeliveryPlan> plans = deliveryPlanMapper.selectList(planQueryWrapper);

        // 确定默认仓库：优先使用订单的仓库，其次使用第一个可用仓库
        Long defaultWarehouseId = order.getWarehouseId();
        if (defaultWarehouseId == null) {
            List<Warehouse> warehouses = warehouseMapper.selectList(null);
            if (!warehouses.isEmpty()) {
                defaultWarehouseId = warehouses.get(0).getId();
            }
        }

        // 将配货计划的仓库信息同步到 order_items，并为没有仓库的计划设置默认仓库
        for (OrderDeliveryPlan plan : plans) {
            Long warehouseIdToUse = plan.getWarehouseId() != null ? plan.getWarehouseId() : defaultWarehouseId;

            if (warehouseIdToUse != null) {
                // 更新配货计划的仓库（如果尚未设置）
                if (plan.getWarehouseId() == null) {
                    LambdaUpdateWrapper<OrderDeliveryPlan> planUpdateWrapper = new LambdaUpdateWrapper<>();
                    planUpdateWrapper.eq(OrderDeliveryPlan::getId, plan.getId())
                            .set(OrderDeliveryPlan::getWarehouseId, warehouseIdToUse);
                    deliveryPlanMapper.update(null, planUpdateWrapper);
                    // 更新内存对象，避免后续循环重复更新
                    plan.setWarehouseId(warehouseIdToUse);
                }

                // 同步到 order_items
                if (plan.getOrderItemId() != null) {
                    OrderItem orderItem = orderItemMapper.selectById(plan.getOrderItemId());
                    if (orderItem != null && orderItem.getWarehouseId() == null) {
                        LambdaUpdateWrapper<OrderItem> itemUpdateWrapper = new LambdaUpdateWrapper<>();
                        itemUpdateWrapper.eq(OrderItem::getId, plan.getOrderItemId())
                                .set(OrderItem::getWarehouseId, warehouseIdToUse);
                        orderItemMapper.update(null, itemUpdateWrapper);
                    }
                }
            }
        }

        // 新行：经统一动作服务推进 配货中 → 待发货；历史行带前置校验保持旧语义
        if (order.getFulfillmentStatus() != null) {
            actionService.confirmAllocation(orderId, "PC");
        } else {
            requireLegacyAllocating(order);
            LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Order::getId, orderId)
                    .set(Order::getStatus, 3)  // READY_TO_SHIP
                    .set(Order::getAdjustmentStatus, Order.AdjustmentStatus.APPROVED);
            orderMapper.update(null, wrapper);
        }

        // 更新配货计划状态为 ALLOCATED
        LambdaUpdateWrapper<OrderDeliveryPlan> planWrapper = new LambdaUpdateWrapper<>();
        planWrapper.eq(OrderDeliveryPlan::getOrderId, orderId)
                .set(OrderDeliveryPlan::getStatus, OrderDeliveryPlan.Status.ALLOCATED);
        deliveryPlanMapper.update(null, planWrapper);
    }

    @Override
    @Transactional
    public void cancelAdjustment(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        // 删除配货计划
        LambdaQueryWrapper<OrderDeliveryPlan> planWrapper = new LambdaQueryWrapper<>();
        planWrapper.eq(OrderDeliveryPlan::getOrderId, orderId);
        deliveryPlanMapper.delete(planWrapper);

        // 删除调整记录
        LambdaQueryWrapper<OrderAdjustmentLog> logWrapper = new LambdaQueryWrapper<>();
        logWrapper.eq(OrderAdjustmentLog::getOrderId, orderId);
        adjustmentLogMapper.delete(logWrapper);

        // 新行：经动作服务回到待配货；历史行带前置校验保持旧语义
        if (order.getFulfillmentStatus() != null) {
            actionService.revertAllocationToWaiting(orderId, "PC");
        } else {
            requireLegacyAllocating(order);
            LambdaUpdateWrapper<Order> orderUpdateWrapper = new LambdaUpdateWrapper<>();
            orderUpdateWrapper.eq(Order::getId, orderId)
                    .set(Order::getStatus, 1)  // PAID
                    .set(Order::getAdjustmentStatus, Order.AdjustmentStatus.NONE);
            orderMapper.update(null, orderUpdateWrapper);
        }
    }

    /** 新行状态前置：只有配货中的订单可以调整/删除方案。 */
    private void requireAllocating(Order order) {
        if (order.getFulfillmentStatus() == null
                || !FulfillmentStatus.ALLOCATING.name().equals(order.getFulfillmentStatus())) {
            throw new RuntimeException("只有配货中的订单可以调整配货方案");
        }
    }

    /** 历史行旧语义前置：只有配货中（status=2）的订单可以确认/取消调整。 */
    private void requireLegacyAllocating(Order order) {
        if (order.getStatus() == null || order.getStatus() != 2) {
            throw new RuntimeException("只有配货中的订单可以执行该操作");
        }
    }

    @Override
    public List<AdjustmentLogDTO> getAdjustmentLogs(Long orderId) {
        LambdaQueryWrapper<OrderAdjustmentLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderAdjustmentLog::getOrderId, orderId)
                .orderByDesc(OrderAdjustmentLog::getCreateTime);
        List<OrderAdjustmentLog> logs = adjustmentLogMapper.selectList(wrapper);

        return logs.stream().map(log -> {
            AdjustmentLogDTO dto = new AdjustmentLogDTO();
            dto.setOrderId(log.getOrderId());
            dto.setAdjustmentType(log.getAdjustmentType());
            dto.setOriginalSkuId(log.getOriginalSkuId());
            dto.setOriginalQuantity(log.getOriginalQuantity());
            dto.setNewSkuId(log.getNewSkuId());
            dto.setNewQuantity(log.getNewQuantity());
            dto.setReason(log.getReason());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 获取当前登录用户
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            if (username != null && !username.equals("anonymousUser")) {
                LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(User::getUsername, username);
                return userMapper.selectOne(wrapper);
            }
        }
        return null;
    }
}

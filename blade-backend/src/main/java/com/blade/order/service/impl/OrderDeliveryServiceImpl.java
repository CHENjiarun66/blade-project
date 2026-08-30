package com.blade.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.dto.OrderDeliveryDTO;
import com.blade.order.dto.OrderDeliveryVO;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderDelivery;
import com.blade.order.entity.OrderDeliveryItem;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderDeliveryItemMapper;
import com.blade.order.mapper.OrderDeliveryMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderDeliveryService;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductColor;
import com.blade.product.entity.ProductSku;
import com.blade.product.entity.ProductSize;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.inventory.entity.Warehouse;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.order.service.OrderService;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderDeliveryServiceImpl implements OrderDeliveryService {

    private final OrderDeliveryMapper deliveryMapper;
    private final OrderDeliveryItemMapper deliveryItemMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final WarehouseMapper warehouseMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductColorMapper productColorMapper;
    private final ProductSizeMapper productSizeMapper;
    private final ProductMapper productMapper;
    private final OrderService orderService;
    private final org.redisson.api.RedissonClient redissonClient;

    @Autowired
    public OrderDeliveryServiceImpl(OrderDeliveryMapper deliveryMapper,
                                   OrderDeliveryItemMapper deliveryItemMapper,
                                   OrderMapper orderMapper,
                                   OrderItemMapper orderItemMapper,
                                   WarehouseMapper warehouseMapper,
                                   ProductSkuMapper productSkuMapper,
                                   ProductColorMapper productColorMapper,
                                   ProductSizeMapper productSizeMapper,
                                   ProductMapper productMapper,
                                   OrderService orderService,
                                   org.redisson.api.RedissonClient redissonClient) {
        this.deliveryMapper = deliveryMapper;
        this.deliveryItemMapper = deliveryItemMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.warehouseMapper = warehouseMapper;
        this.productSkuMapper = productSkuMapper;
        this.productColorMapper = productColorMapper;
        this.productSizeMapper = productSizeMapper;
        this.productMapper = productMapper;
        this.orderService = orderService;
        this.redissonClient = redissonClient;
    }

    @Override
    @Transactional
    public Long create(OrderDeliveryDTO dto) {
        // 空租户显式拒绝（终审 P0-2）
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw BusinessException.of(403, "缺少租户上下文");
        }

        // 数据范围：订单必须属于当前租户
        Order order = orderMapper.selectByIdForUpdate(dto.getOrderId(), tenantId);
        if (order == null) {
            throw BusinessException.of(404, "订单不存在");
        }
        // 履约边界：历史未迁移行不得创建出库单；仅关联库存订单且处于配货中/待发货阶段
        if (order.getFulfillmentStatus() == null) {
            throw BusinessException.of(400, "历史订单尚未迁移，不能创建出库单");
        }
        if (!"STOCK_LINKED".equals(order.getFulfillmentMode())) {
            throw BusinessException.of(400, "仅记录订单不能创建出库单");
        }
        String status = order.getFulfillmentStatus();
        if (!"ALLOCATING".equals(status) && !"READY_TO_SHIP".equals(status)) {
            throw BusinessException.of(400, "订单当前状态不能创建出库单");
        }

        // 查询仓库
        Warehouse warehouse = warehouseMapper.selectById(dto.getWarehouseId());
        if (warehouse == null) {
            throw new RuntimeException("仓库不存在");
        }

        // 创建出库单
        OrderDelivery delivery = new OrderDelivery();
        delivery.setDeliveryNo(generateDeliveryNo());
        delivery.setOrderId(dto.getOrderId());
        delivery.setWarehouseId(dto.getWarehouseId());
        delivery.setWarehouseName(warehouse.getWarehouseName());
        delivery.setStatus(0); // 待出库
        delivery.setRemark(dto.getRemark());
        delivery.setTenantId(tenantId);
        deliveryMapper.insert(delivery);

        // 计算总数量并插入明细
        int totalQuantity = 0;
        List<OrderDeliveryItem> items = new ArrayList<>();

        // 终审三轮 P1-1：按 orderItemId 聚合后校验，重复行合并数量，不绕过可发校验
        java.util.Map<Long, Integer> aggregatedQty = new java.util.LinkedHashMap<>();
        java.util.Map<Long, OrderDeliveryDTO.OrderDeliveryItemDTO> aggregatedDto = new java.util.LinkedHashMap<>();
        for (OrderDeliveryDTO.OrderDeliveryItemDTO itemDTO : dto.getItems()) {
            if (itemDTO.getQuantity() == null || itemDTO.getQuantity() <= 0) {
                throw BusinessException.of(400, "出库数量必须大于0");
            }
            aggregatedQty.merge(itemDTO.getOrderItemId(), itemDTO.getQuantity(), Integer::sum);
            aggregatedDto.putIfAbsent(itemDTO.getOrderItemId(), itemDTO);
        }
        for (var entry : aggregatedQty.entrySet()) {
            OrderDeliveryDTO.OrderDeliveryItemDTO itemDTO = aggregatedDto.get(entry.getKey());
            // 终审 P1-1：明细完整性（归属、SKU 一致、不超可发）
            OrderItem orderItem = orderItemMapper.selectOne(
                    new LambdaQueryWrapper<OrderItem>()
                            .eq(OrderItem::getId, itemDTO.getOrderItemId())
                            .eq(OrderItem::getOrderId, dto.getOrderId())
                            .eq(OrderItem::getTenantId, tenantId));
            if (orderItem == null) {
                throw BusinessException.of(400, "出库明细不属于当前订单: " + itemDTO.getOrderItemId());
            }
            if (orderItem.getSkuId() != null && !orderItem.getSkuId().equals(itemDTO.getSkuId())) {
                throw BusinessException.of(400, "出库 SKU 与订单明细不一致");
            }
            int shippable = orderItem.getQuantity() - (orderItem.getOutQuantity() == null ? 0 : orderItem.getOutQuantity());
            if (entry.getValue() > shippable) {
                throw BusinessException.of(400, "出库数量超过可发数量（剩余 " + shippable + "）");
            }
        }
        for (OrderDeliveryDTO.OrderDeliveryItemDTO itemDTO : dto.getItems()) {
            OrderItem orderItem = orderItemMapper.selectOne(
                    new LambdaQueryWrapper<OrderItem>()
                            .eq(OrderItem::getId, itemDTO.getOrderItemId())
                            .eq(OrderItem::getOrderId, dto.getOrderId())
                            .eq(OrderItem::getTenantId, tenantId));
            if (orderItem == null) {
                throw BusinessException.of(400, "出库明细不属于当前订单: " + itemDTO.getOrderItemId());
            }
            if (orderItem.getSkuId() != null && !orderItem.getSkuId().equals(itemDTO.getSkuId())) {
                throw BusinessException.of(400, "出库 SKU 与订单明细不一致");
            }
            int shippable = orderItem.getQuantity() - (orderItem.getOutQuantity() == null ? 0 : orderItem.getOutQuantity());
            if (itemDTO.getQuantity() > shippable) {
                throw BusinessException.of(400, "出库数量超过可发数量（剩余 " + shippable + "）");
            }

            // 查询SKU信息
            ProductSku sku = productSkuMapper.selectById(itemDTO.getSkuId());

            // 查询颜色、尺码、商品名称
            String colorName = null;
            String sizeName = null;
            String productName = null;
            if (sku != null) {
                if (sku.getColorId() != null) {
                    ProductColor color = productColorMapper.selectById(sku.getColorId());
                    if (color != null) colorName = color.getColorName();
                }
                if (sku.getSizeId() != null) {
                    ProductSize size = productSizeMapper.selectById(sku.getSizeId());
                    if (size != null) sizeName = size.getSizeCode();
                }
                if (sku.getProductId() != null) {
                    Product product = productMapper.selectById(sku.getProductId());
                    if (product != null) productName = product.getName();
                }
            }

            // 创建出库明细
            OrderDeliveryItem item = new OrderDeliveryItem();
            item.setDeliveryId(delivery.getId());
            item.setOrderItemId(itemDTO.getOrderItemId());
            item.setSkuId(itemDTO.getSkuId());
            item.setQuantity(itemDTO.getQuantity());
            if (sku != null) {
                item.setSkuCode(sku.getSkuCode());
                item.setProductName(productName);
                item.setColorName(colorName);
                item.setSizeName(sizeName);
            }
            deliveryItemMapper.insert(item);

            totalQuantity += itemDTO.getQuantity();
        }

        delivery.setTotalQuantity(totalQuantity);
        deliveryMapper.updateById(delivery);

        return delivery.getId();
    }

    @Override
    public List<OrderDeliveryVO> getByOrderId(Long orderId) {
        LambdaQueryWrapper<OrderDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDelivery::getOrderId, orderId);
        wrapper.orderByAsc(OrderDelivery::getCreateTime);

        List<OrderDelivery> deliveries = deliveryMapper.selectList(wrapper);

        return deliveries.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void confirmDelivery(Long deliveryId) {
        Long tenantId = TenantContext.getTenantId();

        // Load tenant-owned delivery
        LambdaQueryWrapper<OrderDelivery> qw = new LambdaQueryWrapper<>();
        qw.eq(OrderDelivery::getId, deliveryId);
        qw.eq(OrderDelivery::getTenantId, tenantId);
        OrderDelivery delivery = deliveryMapper.selectOne(qw);
        if (delivery == null) {
            throw new RuntimeException("出库单不存在");
        }

        // Status 2: idempotent success (already shipped)
        if (delivery.getStatus() == 2) {
            return; // no-op
        }

        // Status 3: rejected
        if (delivery.getStatus() == 3) {
            throw new RuntimeException("该出库单已取消");
        }

        // Delegate to the canonical shipment transaction (same REQUIRED txn)
        orderService.deliverOrder(delivery.getOrderId());

        // Mark legacy delivery status 2 only after canonical shipment succeeds
        delivery.setStatus(2);
        delivery.setDeliverTime(LocalDateTime.now());
        deliveryMapper.updateById(delivery);
    }

    private String generateDeliveryNo() {
        // Redis 计数器生成连续单号，避免 Math.random 碰撞；跨天自动过期清零
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "OUT" + date;
        RAtomicLong counter = redissonClient.getAtomicLong("delivery:no:" + TenantContext.getTenantId() + ":" + date);
        Long dbMax = deliveryMapper.selectMaxDeliveryNoSeq(prefix, TenantContext.getTenantId());
        if (dbMax != null && counter.get() < dbMax) {
            counter.set(dbMax);
        }
        if (counter.get() == 0) {
            counter.expire(2, TimeUnit.DAYS);
        }
        long seq = counter.incrementAndGet();
        return prefix + String.format("%04d", seq);
    }

    private OrderDeliveryVO convertToVO(OrderDelivery delivery) {
        OrderDeliveryVO vo = new OrderDeliveryVO();
        vo.setId(delivery.getId());
        vo.setDeliveryNo(delivery.getDeliveryNo());
        vo.setOrderId(delivery.getOrderId());
        vo.setWarehouseId(delivery.getWarehouseId());
        vo.setWarehouseName(delivery.getWarehouseName());
        vo.setStatus(delivery.getStatus());
        vo.setStatusName(getStatusName(delivery.getStatus()));
        vo.setTotalQuantity(delivery.getTotalQuantity());
        vo.setDeliverer(delivery.getDeliverer());
        vo.setDeliverTime(delivery.getDeliverTime());
        vo.setRemark(delivery.getRemark());
        vo.setCreateTime(delivery.getCreateTime());

        // 查询订单信息
        Order order = orderMapper.selectById(delivery.getOrderId());
        if (order != null) {
            vo.setOrderNo(order.getOrderNo());
        }

        // 查询出库明细
        LambdaQueryWrapper<OrderDeliveryItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderDeliveryItem::getDeliveryId, delivery.getId());
        List<OrderDeliveryItem> items = deliveryItemMapper.selectList(itemWrapper);

        vo.setItems(items.stream().map(item -> {
            OrderDeliveryVO.OrderDeliveryItemVO itemVO = new OrderDeliveryVO.OrderDeliveryItemVO();
            itemVO.setId(item.getId());
            itemVO.setOrderItemId(item.getOrderItemId());
            itemVO.setSkuId(item.getSkuId());
            itemVO.setSkuCode(item.getSkuCode());
            itemVO.setProductName(item.getProductName());
            itemVO.setColorName(item.getColorName());
            itemVO.setSizeName(item.getSizeName());
            itemVO.setQuantity(item.getQuantity());
            return itemVO;
        }).collect(Collectors.toList()));

        return vo;
    }

    private String getStatusName(Integer status) {
        switch (status) {
            case 0: return "待出库";
            case 1: return "部分出库";
            case 2: return "已出库";
            case 3: return "已取消";
            default: return "未知";
        }
    }
}

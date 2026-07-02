package com.blade.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
                                   OrderService orderService) {
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
    }

    @Override
    @Transactional
    public Long create(OrderDeliveryDTO dto) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        // 查询订单
        Order order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new RuntimeException("订单不存在");
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

        for (OrderDeliveryDTO.OrderDeliveryItemDTO itemDTO : dto.getItems()) {
            // 查询订单明细
            OrderItem orderItem = orderItemMapper.selectById(itemDTO.getOrderItemId());
            if (orderItem == null) {
                throw new RuntimeException("订单明细不存在: " + itemDTO.getOrderItemId());
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
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seq = String.format("%04d", (int) (Math.random() * 10000));
        return "OUT" + date + seq;
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

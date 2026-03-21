package com.blade.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.inventory.dto.InventoryOutDTO;
import com.blade.inventory.dto.InventoryOutItemDTO;
import com.blade.inventory.dto.InventoryReserveDTO;
import com.blade.inventory.service.InventoryService;
import com.blade.order.dto.OrderCreateDTO;
import com.blade.order.dto.OrderPageDTO;
import com.blade.order.dto.OrderVO;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderService;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.ProductSkuMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductSkuMapper productSkuMapper;
    private final InventoryService inventoryService;

    @Autowired
    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                           ProductSkuMapper productSkuMapper, InventoryService inventoryService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productSkuMapper = productSkuMapper;
        this.inventoryService = inventoryService;
    }

    // 订单状态常量
    private static final int STATUS_PENDING = 0;       // 待处理
    private static final int STATUS_CONFIRMED = 1;     // 已确认
    private static final int STATUS_DELIVERING = 2;    // 货中
    private static final int STATUS_COMPLETED = 3;    // 已完成
    private static final int STATUS_CANCELLED = 4;    // 已取消
    private static final int STATUS_RETURNING = 5;    // 退货中
    private static final int STATUS_RETURNED = 6;     // 已退货

    @Override
    public PageResult<OrderVO> pageList(OrderPageDTO dto) {
        Page<Order> page = new Page<>(dto.getCurrent(), dto.getSize());
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTenantId, TenantContext.getTenantId());

        if (dto.getOrderNo() != null && !dto.getOrderNo().isEmpty()) {
            wrapper.eq(Order::getOrderNo, dto.getOrderNo());
        }
        if (dto.getCustomerName() != null && !dto.getCustomerName().isEmpty()) {
            wrapper.like(Order::getCustomerName, dto.getCustomerName());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(Order::getStatus, dto.getStatus());
        }

        wrapper.orderByDesc(Order::getCreateTime);

        IPage<Order> result = orderMapper.selectPage(page, wrapper);

        List<OrderVO> voList = result.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());

        PageResult<OrderVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(result.getTotal());
        pageResult.setSize(result.getSize());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    @Override
    public OrderVO getById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        return convertToVO(order);
    }

    @Override
    @Transactional
    public Long create(OrderCreateDTO dto) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setCustomerId(dto.getCustomerId());
        order.setCustomerName(dto.getCustomerName());
        order.setCustomerPhone(dto.getCustomerPhone());
        order.setCustomerAddress(dto.getCustomerAddress());
        order.setWarehouseId(dto.getWarehouseId());
        order.setRemark(dto.getRemark());
        order.setStatus(STATUS_PENDING);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setTenantId(tenantId);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderCreateDTO.OrderItemDTO itemDTO : dto.getItems()) {
            // 获取 SKU 信息
            ProductSku sku = productSkuMapper.selectById(itemDTO.getSkuId());
            BigDecimal price = itemDTO.getPrice() != null ? itemDTO.getPrice() : sku.getPrice();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(itemDTO.getQuantity()));
            totalAmount = totalAmount.add(subtotal);
        }
        order.setTotalAmount(totalAmount);

        orderMapper.insert(order);

        // 保存订单明细
        for (OrderCreateDTO.OrderItemDTO itemDTO : dto.getItems()) {
            ProductSku sku = productSkuMapper.selectById(itemDTO.getSkuId());
            BigDecimal price = itemDTO.getPrice() != null ? itemDTO.getPrice() : sku.getPrice();

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setSkuId(itemDTO.getSkuId());
            item.setSkuCode(sku.getSkuCode());
            item.setProductName(""); // 冗余字段，可通过 SKU 关联查询
            item.setColorName("");
            item.setSizeName("");
            item.setPrice(price);
            item.setQuantity(itemDTO.getQuantity());
            item.setSubtotal(price.multiply(BigDecimal.valueOf(itemDTO.getQuantity())));
            item.setTenantId(tenantId);
            orderItemMapper.insert(item);
        }

        return order.getId();
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        Integer oldStatus = order.getStatus();
        order.setStatus(status);
        order.setUpdateTime(LocalDateTime.now());

        // 设置各状态时间
        LocalDateTime now = LocalDateTime.now();
        switch (status) {
            case STATUS_CONFIRMED:
                order.setConfirmTime(now);
                break;
            case STATUS_DELIVERING:
                order.setDeliverTime(now);
                break;
            case STATUS_COMPLETED:
                order.setCompleteTime(now);
                break;
            case STATUS_CANCELLED:
                // 取消订单：释放预留库存
                releaseInventory(order);
                break;
        }

        orderMapper.updateById(order);
    }

    /**
     * 订单付款确认：锁定库存
     */
    @Transactional
    public void confirmPayment(Long orderId, BigDecimal paidAmount) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != STATUS_PENDING) {
            throw new RuntimeException("订单状态不是待处理，无法确认付款");
        }

        // 锁定库存
        reserveInventory(order);

        order.setStatus(STATUS_CONFIRMED);
        order.setPaidAmount(paidAmount);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 订单发货：预留转正式出库
     */
    @Transactional
    public void deliverOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != STATUS_CONFIRMED) {
            throw new RuntimeException("订单状态不是已确认，无法发货");
        }

        // 预留转出库
        outInventory(order);

        order.setStatus(STATUS_DELIVERING);
        order.setDeliverTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 订单完成
     */
    @Transactional
    public void completeOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != STATUS_DELIVERING) {
            throw new RuntimeException("订单状态不是货中，无法完成");
        }

        order.setStatus(STATUS_COMPLETED);
        order.setCompleteTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 订单取消：释放预留库存
     */
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() == STATUS_COMPLETED || order.getStatus() == STATUS_DELIVERING) {
            throw new RuntimeException("订单已发货或完成，无法取消");
        }

        // 释放预留库存
        releaseInventory(order);

        order.setStatus(STATUS_CANCELLED);
        order.setRemark(order.getRemark() + " [取消原因：" + reason + "]");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 预留库存（付款确认时调用）
     */
    private void reserveInventory(Order order) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(wrapper);

        List<InventoryReserveDTO.ReserveItemDTO> reserveItems = items.stream()
                .map(item -> {
                    InventoryReserveDTO.ReserveItemDTO dto = new InventoryReserveDTO.ReserveItemDTO();
                    dto.setSkuId(item.getSkuId());
                    dto.setQuantity(item.getQuantity());
                    return dto;
                })
                .collect(Collectors.toList());

        InventoryReserveDTO reserveDTO = new InventoryReserveDTO();
        reserveDTO.setOrderId(order.getId());
        reserveDTO.setWarehouseId(order.getWarehouseId());
        reserveDTO.setItems(reserveItems);

        inventoryService.reserve(reserveDTO, 1L); // operatorId 默认1
    }

    /**
     * 释放预留库存（取消订单时调用）
     */
    private void releaseInventory(Order order) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(wrapper);

        List<InventoryReserveDTO.ReserveItemDTO> releaseItems = items.stream()
                .map(item -> {
                    InventoryReserveDTO.ReserveItemDTO dto = new InventoryReserveDTO.ReserveItemDTO();
                    dto.setSkuId(item.getSkuId());
                    dto.setQuantity(item.getQuantity());
                    return dto;
                })
                .collect(Collectors.toList());

        InventoryReserveDTO releaseDTO = new InventoryReserveDTO();
        releaseDTO.setOrderId(order.getId());
        releaseDTO.setWarehouseId(order.getWarehouseId());
        releaseDTO.setItems(releaseItems);

        inventoryService.release(releaseDTO, 1L);
    }

    /**
     * 正式出库（发货时调用）
     */
    private void outInventory(Order order) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(wrapper);

        List<InventoryOutItemDTO> outItems = items.stream()
                .map(item -> {
                    InventoryOutItemDTO dto = new InventoryOutItemDTO();
                    dto.setSkuId(item.getSkuId());
                    dto.setQuantity(item.getQuantity());
                    return dto;
                })
                .collect(Collectors.toList());

        InventoryOutDTO outDTO = new InventoryOutDTO();
        outDTO.setOrderId(order.getId());
        outDTO.setWarehouseId(order.getWarehouseId());
        outDTO.setSource("ORDER");
        outDTO.setItems(outItems);

        inventoryService.out(outDTO, 1L);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Order order = orderMapper.selectById(id);
        if (order != null && order.getStatus() != STATUS_PENDING) {
            throw new RuntimeException("只有待处理状态的订单可以删除");
        }
        orderMapper.deleteById(id);
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, id);
        orderItemMapper.delete(wrapper);
    }

    private OrderVO convertToVO(Order order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setStatusName(getStatusName(order.getStatus()));

        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(wrapper);

        if (items != null && !items.isEmpty()) {
            List<OrderVO.OrderItemVO> itemVOList = items.stream().map(item -> {
                OrderVO.OrderItemVO itemVO = new OrderVO.OrderItemVO();
                itemVO.setId(item.getId());
                itemVO.setSkuId(item.getSkuId());
                itemVO.setSkuCode(item.getSkuCode());
                itemVO.setProductName(item.getProductName());
                itemVO.setColorName(item.getColorName());
                itemVO.setSizeName(item.getSizeName());
                itemVO.setPrice(item.getPrice());
                itemVO.setQuantity(item.getQuantity());
                itemVO.setSubtotal(item.getSubtotal());
                return itemVO;
            }).collect(Collectors.toList());
            vo.setItems(itemVOList);
        }

        return vo;
    }

    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = String.format("%04d", (int) (Math.random() * 10000));
        return "ORD" + date + random;
    }

    private String getStatusName(Integer status) {
        if (status == null) return "";
        switch (status) {
            case 0: return "待处理";
            case 1: return "已确认";
            case 2: return "货中";
            case 3: return "已完成";
            case 4: return "已取消";
            case 5: return "退货中";
            case 6: return "已退货";
            default: return "未知";
        }
    }
}

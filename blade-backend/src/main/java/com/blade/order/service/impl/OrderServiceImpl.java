package com.blade.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.file.service.FileService;
import com.blade.inventory.dto.InventoryReserveDTO;
import com.blade.inventory.entity.Warehouse;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.inventory.service.InventoryService;
import com.blade.order.dto.OrderCreateDTO;
import com.blade.order.dto.OrderExportDTO;
import com.blade.order.dto.OrderPageDTO;
import com.blade.order.dto.OrderUpdateDTO;
import com.blade.order.dto.OrderVO;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderDeliveryPlan;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderService;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductColor;
import com.blade.product.entity.ProductSku;
import com.blade.product.entity.ProductSize;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderDeliveryPlanMapper deliveryPlanMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductColorMapper productColorMapper;
    private final ProductSizeMapper productSizeMapper;
    private final ProductMapper productMapper;
    private final InventoryService inventoryService;
    private final UserMapper userMapper;
    private final WarehouseMapper warehouseMapper;
    private final RedissonClient redissonClient;
    private final FileService fileService;

    @Autowired
    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                           OrderDeliveryPlanMapper deliveryPlanMapper,
                           ProductSkuMapper productSkuMapper, ProductColorMapper productColorMapper,
                           ProductSizeMapper productSizeMapper, ProductMapper productMapper,
                           InventoryService inventoryService, UserMapper userMapper,
                           WarehouseMapper warehouseMapper, RedissonClient redissonClient,
                           FileService fileService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.deliveryPlanMapper = deliveryPlanMapper;
        this.productSkuMapper = productSkuMapper;
        this.productColorMapper = productColorMapper;
        this.productSizeMapper = productSizeMapper;
        this.productMapper = productMapper;
        this.inventoryService = inventoryService;
        this.userMapper = userMapper;
        this.warehouseMapper = warehouseMapper;
        this.redissonClient = redissonClient;
        this.fileService = fileService;
    }

    // 订单状态常量（9状态设计）
    private static final int STATUS_CREATED = 0;           // 创建
    private static final int STATUS_PAID = 1;              // 已付款
    private static final int STATUS_ADJUSTMENT_PENDING = 2; // 配货中-待确认
    private static final int STATUS_READY_TO_SHIP = 3;     // 待发货
    private static final int STATUS_DELIVERED = 4;         // 已发货
    private static final int STATUS_COMPLETED = 5;         // 已完成
    private static final int STATUS_CANCELLED = 6;         // 已取消
    private static final int STATUS_RETURNING = 7;        // 退货中
    private static final int STATUS_RETURNED = 8;         // 已退货

    // 支付状态常量
    private static final int PAYMENT_UNPAID = 0;      // 未付款
    private static final int PAYMENT_DEPOSIT = 1;      // 已付定金
    private static final int PAYMENT_FULL = 2;         // 已付全款
    private static final String ORDER_TYPE_SPOT = "SPOT";
    private static final String ORDER_TYPE_PREORDER = "PREORDER";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

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
        if (dto.getPaymentStatus() != null) {
            wrapper.eq(Order::getPaymentStatus, dto.getPaymentStatus());
        }
        if (dto.getOrderType() != null && !dto.getOrderType().isEmpty()) {
            wrapper.eq(Order::getOrderType, dto.getOrderType());
        }
        if (Boolean.TRUE.equals(dto.getHasBalance())) {
            wrapper.apply("paid_amount < total_amount");
        } else if (Boolean.FALSE.equals(dto.getHasBalance())) {
            wrapper.apply("paid_amount >= total_amount");
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
        order.setOrderDate(dto.getOrderDate() != null ? dto.getOrderDate() : LocalDate.now());
        order.setSourceDocNo(dto.getSourceDocNo());
        order.setSourceShop(dto.getSourceShop());
        order.setOrderType(normalizeOrderType(dto.getOrderType()));
        order.setCustomerId(dto.getCustomerId());
        order.setCustomerName(dto.getCustomerName());
        order.setCustomerPhone(dto.getCustomerPhone());
        order.setCustomerAddress(dto.getCustomerAddress());
        order.setWarehouseId(dto.getWarehouseId());
        // 设置开单销售人员（从当前登录用户获取）
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            order.setSalesmanId(currentUser.getId());
            order.setSalesmanName(currentUser.getNickname() != null ? currentUser.getNickname() : currentUser.getUsername());
        } else {
            order.setSalesmanId(getCurrentUserId());
        }
        order.setRemark(dto.getRemark());
        order.setImages(dto.getImages());
        order.setStatus(STATUS_CREATED);
        order.setPaidAmount(ZERO);
        order.setAdjustmentStatus(Order.AdjustmentStatus.NONE);
        order.setTenantId(tenantId);
        order.setFreightAmount(safeAmount(dto.getFreightAmount()));
        order.setFreightCost(safeAmount(dto.getFreightCost()));
        order.setNeedDelivery(dto.getNeedDelivery());
        order.setDeliveryAddress(dto.getDeliveryAddress());
        order.setIsDelivered(0);
        order.setDeliveredAt(null);

        OrderTotals totals = calculateTotals(dto.getItems(), order.getFreightAmount(), order.getFreightCost());
        applyTotals(order, totals);
        applyPaymentSnapshot(order, resolveInitialPaidAmount(dto, order.getTotalAmount()));

        orderMapper.insert(order);
        insertOrderItems(order, dto.getItems(), tenantId);
        fileService.bindFilesFromJson("order", order.getId(), order.getImages());

        return order.getId();
    }

    private void insertOrderItems(Order order, List<OrderCreateDTO.OrderItemDTO> itemDTOs, Long tenantId) {
        for (OrderCreateDTO.OrderItemDTO itemDTO : itemDTOs) {
            OrderItem item = buildOrderItem(order, itemDTO, tenantId);
            orderItemMapper.insert(item);
        }
    }

    private OrderItem buildOrderItem(Order order, OrderCreateDTO.OrderItemDTO itemDTO, Long tenantId) {
        ProductSku sku = productSkuMapper.selectById(itemDTO.getSkuId());
        if (sku == null) {
            throw new RuntimeException("SKU不存在：" + itemDTO.getSkuId());
        }
        Product product = sku.getProductId() != null ? productMapper.selectById(sku.getProductId()) : null;
        BigDecimal price = itemDTO.getPrice() != null ? itemDTO.getPrice() : safeAmount(sku.getPrice());
        BigDecimal costPrice = itemDTO.getCostPrice() != null ? itemDTO.getCostPrice() : resolveCostPrice(sku, product);
        Integer quantity = itemDTO.getQuantity() != null ? itemDTO.getQuantity() : 0;
        if (quantity <= 0) {
            throw new RuntimeException("商品数量必须大于0");
        }
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal costAmount = costPrice.multiply(BigDecimal.valueOf(quantity));

        String colorName = "";
        String sizeName = "";
        if (sku.getColorId() != null) {
            ProductColor color = productColorMapper.selectById(sku.getColorId());
            if (color != null) {
                colorName = color.getColorName();
            }
        }
        if (sku.getSizeId() != null) {
            ProductSize size = productSizeMapper.selectById(sku.getSizeId());
            if (size != null) {
                sizeName = size.getSizeCode();
            }
        }

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setSkuId(itemDTO.getSkuId());
        item.setWarehouseId(itemDTO.getWarehouseId() != null ? itemDTO.getWarehouseId() : order.getWarehouseId());
        item.setSkuCode(sku.getSkuCode() != null ? sku.getSkuCode() : "");
        item.setProductName(product != null ? product.getName() : "");
        item.setColorName(colorName);
        item.setSizeName(sizeName);
        item.setPrice(price);
        item.setCostPrice(costPrice);
        item.setQuantity(quantity);
        item.setSubtotal(subtotal);
        item.setCostAmount(costAmount);
        item.setGrossProfit(subtotal.subtract(costAmount));
        item.setTenantId(tenantId);
        return item;
    }

    private OrderTotals calculateTotals(List<OrderCreateDTO.OrderItemDTO> items, BigDecimal freightAmount, BigDecimal freightCost) {
        BigDecimal salesAmount = ZERO;
        BigDecimal productCostAmount = ZERO;
        for (OrderCreateDTO.OrderItemDTO itemDTO : items) {
            ProductSku sku = productSkuMapper.selectById(itemDTO.getSkuId());
            if (sku == null) {
                throw new RuntimeException("SKU不存在：" + itemDTO.getSkuId());
            }
            Product product = sku.getProductId() != null ? productMapper.selectById(sku.getProductId()) : null;
            BigDecimal price = itemDTO.getPrice() != null ? itemDTO.getPrice() : safeAmount(sku.getPrice());
            BigDecimal costPrice = itemDTO.getCostPrice() != null ? itemDTO.getCostPrice() : resolveCostPrice(sku, product);
            Integer quantity = itemDTO.getQuantity() != null ? itemDTO.getQuantity() : 0;
            if (quantity <= 0) {
                throw new RuntimeException("商品数量必须大于0");
            }
            salesAmount = salesAmount.add(price.multiply(BigDecimal.valueOf(quantity)));
            productCostAmount = productCostAmount.add(costPrice.multiply(BigDecimal.valueOf(quantity)));
        }
        BigDecimal freightIncome = safeAmount(freightAmount);
        BigDecimal freightExpense = safeAmount(freightCost);
        BigDecimal totalAmount = salesAmount.add(freightIncome);
        BigDecimal totalCost = productCostAmount.add(freightExpense);
        return new OrderTotals(totalAmount, totalCost, totalAmount.subtract(totalCost));
    }

    private OrderTotals calculateTotalsFromExistingItems(Long orderId, BigDecimal freightAmount, BigDecimal freightCost) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(wrapper);
        BigDecimal salesAmount = items.stream()
                .map(item -> safeAmount(item.getSubtotal()))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal productCostAmount = items.stream()
                .map(item -> safeAmount(item.getCostAmount()))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalAmount = salesAmount.add(safeAmount(freightAmount));
        BigDecimal totalCost = productCostAmount.add(safeAmount(freightCost));
        return new OrderTotals(totalAmount, totalCost, totalAmount.subtract(totalCost));
    }

    private void applyTotals(Order order, OrderTotals totals) {
        order.setTotalAmount(totals.totalAmount());
        order.setTotalCostAmount(totals.totalCostAmount());
        order.setGrossProfit(totals.grossProfit());
    }

    private BigDecimal resolveInitialPaidAmount(OrderCreateDTO dto, BigDecimal totalAmount) {
        if (dto.getPaidAmount() != null) {
            return dto.getPaidAmount();
        }
        if (dto.getPaymentStatus() != null && dto.getPaymentStatus() == PAYMENT_FULL) {
            return totalAmount;
        }
        if (dto.getPaymentStatus() != null && dto.getPaymentStatus() == PAYMENT_DEPOSIT) {
            return safeAmount(dto.getDepositAmount());
        }
        return ZERO;
    }

    private void applyPaymentSnapshot(Order order, BigDecimal paidAmount) {
        BigDecimal paid = safeAmount(paidAmount);
        if (paid.compareTo(ZERO) < 0) {
            throw new RuntimeException("实收金额不能小于0");
        }
        if (paid.compareTo(order.getTotalAmount()) > 0) {
            throw new RuntimeException("实收金额不能超过订单应收总额");
        }
        order.setPaidAmount(paid);
        if (paid.compareTo(order.getTotalAmount()) >= 0 && order.getTotalAmount().compareTo(ZERO) > 0) {
            order.setPaymentStatus(PAYMENT_FULL);
            order.setDepositAmount(ZERO);
        } else if (paid.compareTo(ZERO) > 0) {
            order.setPaymentStatus(PAYMENT_DEPOSIT);
            order.setDepositAmount(paid);
        } else {
            order.setPaymentStatus(PAYMENT_UNPAID);
            order.setDepositAmount(ZERO);
        }
    }

    private BigDecimal resolveCostPrice(ProductSku sku, Product product) {
        if (sku != null && sku.getCostPrice() != null && sku.getCostPrice().compareTo(ZERO) > 0) {
            return sku.getCostPrice();
        }
        if (product != null && product.getCostPrice() != null) {
            return product.getCostPrice();
        }
        return ZERO;
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    private String normalizeOrderType(String orderType) {
        if (ORDER_TYPE_PREORDER.equals(orderType)) {
            return ORDER_TYPE_PREORDER;
        }
        return ORDER_TYPE_SPOT;
    }

    private String getOrderTypeName(String orderType) {
        if (ORDER_TYPE_PREORDER.equals(orderType)) {
            return "订货订单";
        }
        return "现货订单";
    }

    private record OrderTotals(BigDecimal totalAmount, BigDecimal totalCostAmount, BigDecimal grossProfit) {}

    @Override
    @Transactional
    public void update(OrderUpdateDTO dto) {
        Order order = orderMapper.selectById(dto.getId());
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        // 已发货后只允许补充备注/图片，金额结构和明细不再修改
        if (order.getStatus() >= STATUS_DELIVERED) {
            if (dto.getRemark() != null) order.setRemark(dto.getRemark());
            if (dto.getImages() != null) order.setImages(dto.getImages());
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(order);
            fileService.bindFilesFromJson("order", order.getId(), order.getImages());
            return;
        }
        boolean hasFinancialChange = dto.getFreightAmount() != null || dto.getFreightCost() != null
                || (dto.getItems() != null && !dto.getItems().isEmpty());
        if (order.getStatus() != STATUS_CREATED && hasFinancialChange) {
            throw new RuntimeException("已收款或配货订单不允许直接修改金额和明细，请先取消订单或调整配货计划");
        }
        if (dto.getOrderDate() != null) order.setOrderDate(dto.getOrderDate());
        if (dto.getSourceDocNo() != null) order.setSourceDocNo(dto.getSourceDocNo());
        if (dto.getSourceShop() != null) order.setSourceShop(dto.getSourceShop());
        if (dto.getOrderType() != null) order.setOrderType(normalizeOrderType(dto.getOrderType()));
        if (dto.getCustomerName() != null) order.setCustomerName(dto.getCustomerName());
        if (dto.getCustomerPhone() != null) order.setCustomerPhone(dto.getCustomerPhone());
        if (dto.getCustomerAddress() != null) order.setCustomerAddress(dto.getCustomerAddress());
        if (dto.getNeedDelivery() != null) order.setNeedDelivery(dto.getNeedDelivery());
        if (dto.getDeliveryAddress() != null) order.setDeliveryAddress(dto.getDeliveryAddress());
        if (dto.getRemark() != null) order.setRemark(dto.getRemark());
        if (dto.getImages() != null) order.setImages(dto.getImages());
        if (dto.getFreightAmount() != null) order.setFreightAmount(safeAmount(dto.getFreightAmount()));
        if (dto.getFreightCost() != null) order.setFreightCost(safeAmount(dto.getFreightCost()));
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(OrderItem::getOrderId, order.getId());
            orderItemMapper.delete(wrapper);
            insertOrderItems(order, dto.getItems(), order.getTenantId());
        }
        if (hasFinancialChange) {
            OrderTotals totals = dto.getItems() != null && !dto.getItems().isEmpty()
                    ? calculateTotals(dto.getItems(), order.getFreightAmount(), order.getFreightCost())
                    : calculateTotalsFromExistingItems(order.getId(), order.getFreightAmount(), order.getFreightCost());
            applyTotals(order, totals);
            applyPaymentSnapshot(order, order.getPaidAmount());
        }
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        fileService.bindFilesFromJson("order", order.getId(), order.getImages());
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
            case STATUS_PAID:
                order.setConfirmTime(now);
                break;
            case STATUS_DELIVERED:
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
        if (order.getStatus() != STATUS_CREATED) {
            throw new RuntimeException("订单状态不是待处理，无法确认付款");
        }

        // 锁定库存（跨仓总量预留）
        reserveInventoryGlobal(order);

        order.setStatus(STATUS_PAID);
        order.setPaidAmount(paidAmount);
        // 根据实收金额同步支付状态
        if (paidAmount.compareTo(order.getTotalAmount()) >= 0) {
            order.setPaymentStatus(PAYMENT_FULL);
        } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(PAYMENT_DEPOSIT);
        }
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 追加收款：在已付款基础上记录额外收款，不改变订单状态
     */
    @Override
    @Transactional
    public void addPayment(Long orderId, BigDecimal additionalAmount) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() == STATUS_COMPLETED || order.getStatus() == STATUS_CANCELLED
                || order.getStatus() == STATUS_RETURNING || order.getStatus() == STATUS_RETURNED) {
            throw new RuntimeException("该订单当前状态不支持追加收款");
        }
        if (order.getPaymentStatus() == PAYMENT_FULL) {
            throw new RuntimeException("订单已付全款，无需追加");
        }
        BigDecimal currentPaid = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaidAmount = currentPaid.add(additionalAmount);
        if (newPaidAmount.compareTo(order.getTotalAmount()) > 0) {
            throw new RuntimeException("追加后金额不能超过订单总额");
        }
        applyPaymentSnapshot(order, newPaidAmount);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    public List<OrderExportDTO> exportOrders(OrderPageDTO dto) {
        Long tenantId = TenantContext.getTenantId();

        // 查询所有符合筛选条件的订单（不分页）
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTenantId, tenantId);

        if (dto.getOrderNo() != null && !dto.getOrderNo().isEmpty()) {
            wrapper.eq(Order::getOrderNo, dto.getOrderNo());
        }
        if (dto.getCustomerName() != null && !dto.getCustomerName().isEmpty()) {
            wrapper.like(Order::getCustomerName, dto.getCustomerName());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(Order::getStatus, dto.getStatus());
        }
        if (dto.getPaymentStatus() != null) {
            wrapper.eq(Order::getPaymentStatus, dto.getPaymentStatus());
        }
        if (dto.getOrderType() != null && !dto.getOrderType().isEmpty()) {
            wrapper.eq(Order::getOrderType, dto.getOrderType());
        }
        if (Boolean.TRUE.equals(dto.getHasBalance())) {
            wrapper.apply("paid_amount < total_amount");
        } else if (Boolean.FALSE.equals(dto.getHasBalance())) {
            wrapper.apply("paid_amount >= total_amount");
        }

        wrapper.orderByDesc(Order::getCreateTime);

        // 查询订单（设置一个很大的分页大小）
        Page<Order> page = new Page<>(1, 10000);
        IPage<Order> orderPage = orderMapper.selectPage(page, wrapper);
        List<Order> orders = orderPage.getRecords();

        if (orders.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询所有订单项
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.in(OrderItem::getOrderId, orderIds);
        List<OrderItem> allItems = orderItemMapper.selectList(itemWrapper);

        // 按订单ID分组
        java.util.Map<Long, List<OrderItem>> itemsByOrderId = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        // 构建导出数据（每行 = 1个订单明细）
        List<OrderExportDTO> result = new ArrayList<>();
        for (Order order : orders) {
            List<OrderItem> items = itemsByOrderId.getOrDefault(order.getId(), new ArrayList<>());
            if (items.isEmpty()) {
                // 订单没有明细，也导出一行
                OrderExportDTO exportDto = new OrderExportDTO();
                fillOrderFields(exportDto, order);
                result.add(exportDto);
            } else {
                for (OrderItem item : items) {
                    OrderExportDTO exportDto = new OrderExportDTO();
                    fillOrderFields(exportDto, order);
                    exportDto.setProductName(item.getProductName());
                    exportDto.setSkuCode(item.getSkuCode());
                    exportDto.setColorName(item.getColorName());
                    exportDto.setSizeName(item.getSizeName());
                    exportDto.setQuantity(item.getQuantity());
                    exportDto.setPrice(item.getPrice());
                    exportDto.setCostPrice(item.getCostPrice());
                    exportDto.setSubtotal(item.getSubtotal());
                    exportDto.setCostAmount(item.getCostAmount());
                    exportDto.setItemGrossProfit(item.getGrossProfit());
                    result.add(exportDto);
                }
            }
        }
        return result;
    }

    private void fillOrderFields(OrderExportDTO exportDto, Order order) {
        exportDto.setOrderNo(order.getOrderNo());
        exportDto.setSourceDocNo(order.getSourceDocNo());
        exportDto.setSourceShop(order.getSourceShop());
        exportDto.setOrderDate(order.getOrderDate() != null ? order.getOrderDate().toString() : "");
        exportDto.setOrderTypeName(getOrderTypeName(order.getOrderType()));
        exportDto.setStatusName(getStatusName(order.getStatus()));
        exportDto.setPaymentStatusName(getPaymentStatusName(order.getPaymentStatus()));
        exportDto.setCustomerName(order.getCustomerName());
        exportDto.setCustomerPhone(order.getCustomerPhone());
        exportDto.setTotalAmount(order.getTotalAmount());
        exportDto.setPaidAmount(order.getPaidAmount());
        exportDto.setBalanceAmount(safeAmount(order.getTotalAmount()).subtract(safeAmount(order.getPaidAmount())));
        exportDto.setFreightAmount(order.getFreightAmount());
        exportDto.setFreightCost(order.getFreightCost());
        exportDto.setTotalCostAmount(order.getTotalCostAmount());
        exportDto.setGrossProfit(order.getGrossProfit());
        exportDto.setSalesmanName(order.getSalesmanName());
        exportDto.setCreateTime(order.getCreateTime() != null ? order.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
        exportDto.setRemark(order.getRemark());
    }

    /**
     * 订单发货：预留转正式出库
     * 状态流转：READY_TO_SHIP → DELIVERED
     */
    @Transactional
    public void deliverOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != STATUS_READY_TO_SHIP) {
            throw new RuntimeException("订单状态不是待发货，无法发货");
        }

        // 使用配货计划出库
        outInventory(orderId);

        order.setStatus(STATUS_DELIVERED);
        order.setIsDelivered(1);
        order.setDeliveredAt(LocalDateTime.now());
        order.setDeliverTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 订单完成
     * 状态流转：DELIVERED → COMPLETED
     */
    @Transactional
    public void completeOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != STATUS_DELIVERED) {
            throw new RuntimeException("订单状态不是已发货，无法完成");
        }

        order.setStatus(STATUS_COMPLETED);
        order.setCompleteTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 订单取消：释放预留库存
     * 可取消状态：CREATED, PAID, ADJUSTMENT_PENDING
     */
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        // 白名单：只有这些状态可以取消
        if (order.getStatus() != STATUS_CREATED &&
                order.getStatus() != STATUS_PAID &&
                order.getStatus() != STATUS_ADJUSTMENT_PENDING) {
            throw new RuntimeException("该订单当前状态不支持取消");
        }

        // 仅付款后才有库存预留，创建状态取消无需释放
        if (order.getStatus() >= STATUS_PAID) {
            releaseInventoryGlobal(order);
        }

        order.setStatus(STATUS_CANCELLED);
        order.setRemark(order.getRemark() + " [取消原因：" + reason + "]");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 跨仓总量预留（付款确认时调用）
     * 不分仓库，直接按SKU总量预留
     */
    private void reserveInventoryGlobal(Order order) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(wrapper);

        // 按SKU分组，汇总数量
        java.util.Map<Long, Integer> itemsBySku = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        OrderItem::getSkuId,
                        java.util.stream.Collectors.summingInt(OrderItem::getQuantity)));

        List<InventoryReserveDTO.ReserveItemDTO> reserveItems = itemsBySku.entrySet().stream()
                .map(entry -> {
                    InventoryReserveDTO.ReserveItemDTO dto = new InventoryReserveDTO.ReserveItemDTO();
                    dto.setSkuId(entry.getKey());
                    dto.setQuantity(entry.getValue());
                    return dto;
                })
                .collect(Collectors.toList());

        InventoryReserveDTO reserveDTO = new InventoryReserveDTO();
        reserveDTO.setOrderId(order.getId());
        reserveDTO.setWarehouseId(order.getWarehouseId()); // 用订单的仓库ID（实际不走仓库）
        reserveDTO.setItems(reserveItems);

        inventoryService.globalReserve(reserveDTO, 1L); // operatorId 默认1
    }

    /**
     * 跨仓总量释放（取消订单时调用）
     */
    private void releaseInventoryGlobal(Order order) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(wrapper);

        // 按SKU分组，汇总数量
        java.util.Map<Long, Integer> itemsBySku = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        OrderItem::getSkuId,
                        java.util.stream.Collectors.summingInt(OrderItem::getQuantity)));

        List<InventoryReserveDTO.ReserveItemDTO> releaseItems = itemsBySku.entrySet().stream()
                .map(entry -> {
                    InventoryReserveDTO.ReserveItemDTO dto = new InventoryReserveDTO.ReserveItemDTO();
                    dto.setSkuId(entry.getKey());
                    dto.setQuantity(entry.getValue());
                    return dto;
                })
                .collect(Collectors.toList());

        InventoryReserveDTO releaseDTO = new InventoryReserveDTO();
        releaseDTO.setOrderId(order.getId());
        releaseDTO.setWarehouseId(order.getWarehouseId());
        releaseDTO.setItems(releaseItems);

        inventoryService.globalRelease(releaseDTO, 1L);
    }

    /**
     * 预留库存（付款确认时调用）
     * 按仓库分组，每个仓库单独调用库存服务
     * @deprecated 使用 {@link #reserveInventoryGlobal(Order)} 跨仓总量预留
     */
    private void reserveInventory(Order order) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(wrapper);

        // 按仓库ID分组
        java.util.Map<Long, List<OrderItem>> itemsByWarehouse = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(item ->
                        item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId()));

        // 每个仓库单独处理
        for (java.util.Map.Entry<Long, List<OrderItem>> entry : itemsByWarehouse.entrySet()) {
            Long warehouseId = entry.getKey();
            List<OrderItem> warehouseItems = entry.getValue();

            List<InventoryReserveDTO.ReserveItemDTO> reserveItems = warehouseItems.stream()
                    .map(item -> {
                        InventoryReserveDTO.ReserveItemDTO dto = new InventoryReserveDTO.ReserveItemDTO();
                        dto.setSkuId(item.getSkuId());
                        dto.setQuantity(item.getQuantity());
                        return dto;
                    })
                    .collect(Collectors.toList());

            InventoryReserveDTO reserveDTO = new InventoryReserveDTO();
            reserveDTO.setOrderId(order.getId());
            reserveDTO.setWarehouseId(warehouseId);
            reserveDTO.setItems(reserveItems);

            inventoryService.reserve(reserveDTO, 1L); // operatorId 默认1
        }
    }

    /**
     * 释放预留库存（取消订单时调用）
     * 按仓库分组，每个仓库单独调用库存服务
     */
    private void releaseInventory(Order order) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(wrapper);

        // 按仓库ID分组
        java.util.Map<Long, List<OrderItem>> itemsByWarehouse = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(item ->
                        item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId()));

        // 每个仓库单独处理
        for (java.util.Map.Entry<Long, List<OrderItem>> entry : itemsByWarehouse.entrySet()) {
            Long warehouseId = entry.getKey();
            List<OrderItem> warehouseItems = entry.getValue();

            List<InventoryReserveDTO.ReserveItemDTO> releaseItems = warehouseItems.stream()
                    .map(item -> {
                        InventoryReserveDTO.ReserveItemDTO dto = new InventoryReserveDTO.ReserveItemDTO();
                        dto.setSkuId(item.getSkuId());
                        dto.setQuantity(item.getQuantity());
                        return dto;
                    })
                    .collect(Collectors.toList());

            InventoryReserveDTO releaseDTO = new InventoryReserveDTO();
            releaseDTO.setOrderId(order.getId());
            releaseDTO.setWarehouseId(warehouseId);
            releaseDTO.setItems(releaseItems);

            inventoryService.release(releaseDTO, 1L);
        }
    }

    /**
     * 正式出库（发货时调用）
     * 使用配货计划按序出库
     */
    private void outInventory(Long orderId) {
        // 获取配货计划
        LambdaQueryWrapper<OrderDeliveryPlan> planWrapper = new LambdaQueryWrapper<>();
        planWrapper.eq(OrderDeliveryPlan::getOrderId, orderId);
        List<OrderDeliveryPlan> plans = deliveryPlanMapper.selectList(planWrapper);

        if (plans.isEmpty()) {
            throw new RuntimeException("订单没有配货计划，无法发货");
        }

        // 按配货计划逐一出库
        for (OrderDeliveryPlan plan : plans) {
            if (plan.getStatus() == OrderDeliveryPlan.Status.OUT) {
                continue; // 已出库，跳过
            }
            // 校验：已分配数量 - 已出库数量 = 本次应出库数量
            int toOutQty = plan.getAllocatedQty() - plan.getOutQty();
            if (toOutQty > 0) {
                inventoryService.outByPlan(plan.getId(), toOutQty, getCurrentUserId());
            }
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Order order = orderMapper.selectById(id);
        if (order != null && order.getStatus() != STATUS_CREATED) {
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
        vo.setPaymentStatusName(getPaymentStatusName(order.getPaymentStatus()));
        vo.setOrderTypeName(getOrderTypeName(order.getOrderType()));
        vo.setBalanceAmount(safeAmount(order.getTotalAmount()).subtract(safeAmount(order.getPaidAmount())));

        // 设置销售人员名称（优先使用存储的冗余字段，避免跨租户查询）
        if (order.getSalesmanName() != null && !order.getSalesmanName().isEmpty()) {
            vo.setSalesmanName(order.getSalesmanName());
        } else if (order.getSalesmanId() != null) {
            // 兼容旧数据：尝试从用户表查询
            User user = userMapper.selectById(order.getSalesmanId());
            if (user != null) {
                vo.setSalesmanName(user.getNickname() != null ? user.getNickname() : user.getUsername());
            }
        }

        // 设置仓库名称
        if (order.getWarehouseId() != null) {
            Warehouse warehouse = warehouseMapper.selectById(order.getWarehouseId());
            if (warehouse != null) {
                vo.setWarehouseName(warehouse.getWarehouseName());
            }
        }

        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(wrapper);

        if (items != null && !items.isEmpty()) {
            List<OrderVO.OrderItemVO> itemVOList = items.stream().map(item -> {
                OrderVO.OrderItemVO itemVO = new OrderVO.OrderItemVO();
                itemVO.setId(item.getId());
                itemVO.setSkuId(item.getSkuId());
                itemVO.setWarehouseId(item.getWarehouseId());
                // 查询仓库名称
                if (item.getWarehouseId() != null) {
                    Warehouse wh = warehouseMapper.selectById(item.getWarehouseId());
                    if (wh != null) {
                        itemVO.setWarehouseName(wh.getWarehouseName());
                    }
                }
                itemVO.setSkuCode(item.getSkuCode());
                itemVO.setProductName(item.getProductName());
                itemVO.setColorName(item.getColorName());
                itemVO.setSizeName(item.getSizeName());
                itemVO.setPrice(item.getPrice());
                itemVO.setCostPrice(item.getCostPrice());
                itemVO.setQuantity(item.getQuantity());
                itemVO.setPlannedQuantity(item.getPlannedQuantity());
                itemVO.setAllocatedQuantity(item.getAllocatedQuantity());
                itemVO.setOutQuantity(item.getOutQuantity());
                itemVO.setAdjustmentRemark(item.getAdjustmentRemark());
                itemVO.setSubtotal(item.getSubtotal());
                itemVO.setCostAmount(item.getCostAmount());
                itemVO.setGrossProfit(item.getGrossProfit());
                return itemVO;
            }).collect(Collectors.toList());
            vo.setItems(itemVOList);
        }

        return vo;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return 1L; // 默认管理员
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long tenantId = TenantContext.getTenantId();
        String key = "order:no:" + tenantId + ":" + date;
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        // 当天首次使用时设置过期时间为2天（跨天清零）
        if (counter.get() == 0) {
            counter.expire(2, TimeUnit.DAYS);
        }
        long seq = counter.incrementAndGet();
        return "ORD" + date + String.format("%04d", seq);
    }

    private String getStatusName(Integer status) {
        if (status == null) return "";
        switch (status) {
            case 0: return "创建";
            case 1: return "已付款";
            case 2: return "配货中";
            case 3: return "待发货";
            case 4: return "已发货";
            case 5: return "已完成";
            case 6: return "已取消";
            case 7: return "退货中";
            case 8: return "已退货";
            default: return "未知";
        }
    }

    private String getPaymentStatusName(Integer paymentStatus) {
        if (paymentStatus == null) return "";
        switch (paymentStatus) {
            case 0: return "未付款";
            case 1: return "已付定金";
            case 2: return "已付全款";
            default: return "未知";
        }
    }
}

package com.blade.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.file.service.FileService;
import com.blade.inventory.entity.Warehouse;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.inventory.service.InventoryService;
import com.blade.order.dto.AddPaymentDTO;
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
    private static final int PAYMENT_DEPOSIT = 1;     // 部分收款
    private static final int PAYMENT_FULL = 2;        // 已结清
    private static final String ORDER_TYPE_SPOT = "SPOT";
    private static final String ORDER_TYPE_PREORDER = "PREORDER";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String NET_RECEIVABLE_SQL =
            "GREATEST(COALESCE(total_amount, 0) - COALESCE(refund_amount, 0) - COALESCE(write_off_amount, 0), 0)";

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
            wrapper.apply("COALESCE(paid_amount, 0) < " + NET_RECEIVABLE_SQL);
        } else if (Boolean.FALSE.equals(dto.getHasBalance())) {
            wrapper.apply("COALESCE(paid_amount, 0) >= " + NET_RECEIVABLE_SQL);
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
        BigDecimal netRec = netReceivable(order);
        if (paid.compareTo(netRec) > 0) {
            throw new RuntimeException("实收金额不能超过订单应收净额");
        }
        order.setPaidAmount(paid);
        if (paid.compareTo(netRec) >= 0 && netRec.compareTo(ZERO) > 0) {
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

    private boolean amountChanged(BigDecimal currentValue, BigDecimal newValue) {
        return newValue != null && safeAmount(currentValue).compareTo(safeAmount(newValue)) != 0;
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

    /**
     * 应收净额 = max(totalAmount - refundAmount - writeOffAmount, 0)
     */
    private BigDecimal netReceivable(Order order) {
        BigDecimal total = safeAmount(order.getTotalAmount());
        BigDecimal refund = safeAmount(order.getRefundAmount());
        BigDecimal writeOff = safeAmount(order.getWriteOffAmount());
        BigDecimal net = total.subtract(refund).subtract(writeOff);
        return net.compareTo(ZERO) > 0 ? net : ZERO;
    }

    /**
     * 尾款 = max(netReceivable - paidAmount, 0)
     */
    private BigDecimal balance(Order order) {
        BigDecimal netRec = netReceivable(order);
        BigDecimal paid = safeAmount(order.getPaidAmount());
        BigDecimal bal = netRec.subtract(paid);
        return bal.compareTo(ZERO) > 0 ? bal : ZERO;
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
        boolean hasFinancialChange = amountChanged(order.getFreightAmount(), dto.getFreightAmount())
                || amountChanged(order.getFreightCost(), dto.getFreightCost())
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
                // 取消订单：不涉及库存操作
                break;
        }

        orderMapper.updateById(order);
    }

    /**
     * 订单付款确认：只更新订单状态和支付信息，不涉及库存。
     */
    @Transactional
    public void confirmPayment(Long orderId, BigDecimal paidAmount) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("无法获取当前租户");
        }
        Order order = orderMapper.selectByIdForUpdate(orderId, tenantId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != STATUS_CREATED) {
            throw new RuntimeException("订单状态不是待处理，无法确认付款");
        }

        applyPaymentSnapshot(order, paidAmount);
        order.setStatus(STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 追加收款（委托到 DTO 重载）。
     */
    @Override
    @Deprecated
    @Transactional
    public void addPayment(Long orderId, BigDecimal additionalAmount) {
        AddPaymentDTO dto = new AddPaymentDTO();
        dto.setAdditionalAmount(additionalAmount);
        addPayment(orderId, dto);
    }

    /**
     * 追加收款 / 标记结清。
     * <p>
     * 使用 tenant-scoped FOR UPDATE 行锁防止并发覆盖。
     * markAsSettled=true 时：先追加 additionalAmount（可为 0），
     * 再将当前尾款写入 write_off_amount，reason 必填。
     * 重复标记结清（尾款已为 0）会拒绝。
     */
    @Override
    @Transactional
    public void addPayment(Long orderId, AddPaymentDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("无法获取当前租户");
        }

        // Row-lock the order within the current tenant
        Order order = orderMapper.selectByIdForUpdate(orderId, tenantId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() == STATUS_COMPLETED || order.getStatus() == STATUS_CANCELLED
                || order.getStatus() == STATUS_RETURNING || order.getStatus() == STATUS_RETURNED) {
            throw new RuntimeException("该订单当前状态不支持追加收款");
        }

        BigDecimal additionalAmount = safeAmount(dto.getAdditionalAmount());
        boolean markAsSettled = Boolean.TRUE.equals(dto.getMarkAsSettled());

        // Validation: normal payment must be > 0 unless markAsSettled
        if (!markAsSettled && additionalAmount.compareTo(ZERO) <= 0) {
            throw new RuntimeException("追加金额必须大于0");
        }

        // Validation: markAsSettled requires reason
        if (markAsSettled && (dto.getWriteOffReason() == null || dto.getWriteOffReason().isBlank())) {
            throw new RuntimeException("标记结清必须填写原因");
        }

        // Validation: order must not already be settled (payment_status=2)
        if (order.getPaymentStatus() != null && order.getPaymentStatus() == PAYMENT_FULL) {
            throw new RuntimeException("订单已结清，无需追加");
        }

        BigDecimal netRec = netReceivable(order);
        BigDecimal currentPaid = safeAmount(order.getPaidAmount());
        BigDecimal currentBalance = netRec.subtract(currentPaid);
        if (currentBalance.compareTo(ZERO) <= 0) {
            throw new RuntimeException("订单已结清，无需追加");
        }

        // Validation: payment must not exceed net receivable
        if (additionalAmount.compareTo(currentBalance) > 0) {
            throw new RuntimeException("追加后金额不能超过应收净额，当前尾款：" + currentBalance);
        }

        // Apply the additional payment
        BigDecimal newPaid = currentPaid.add(additionalAmount);
        order.setPaidAmount(newPaid);

        if (markAsSettled) {
            // Write remaining balance into write_off_amount
            BigDecimal remaining = netRec.subtract(newPaid);
            if (remaining.compareTo(ZERO) <= 0) {
                throw new RuntimeException("当前已无尾款，无需标记结清");
            }
            // Preserve existing write-off and add new remainder
            BigDecimal existingWriteOff = safeAmount(order.getWriteOffAmount());
            order.setWriteOffAmount(existingWriteOff.add(remaining));
            order.setWriteOffReason(dto.getWriteOffReason().trim());
        }

        // Preserve the existing payment snapshot behavior while applying the unified formula.
        applyPaymentSnapshot(order, newPaid);

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
            wrapper.apply("COALESCE(paid_amount, 0) < " + NET_RECEIVABLE_SQL);
        } else if (Boolean.FALSE.equals(dto.getHasBalance())) {
            wrapper.apply("COALESCE(paid_amount, 0) >= " + NET_RECEIVABLE_SQL);
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
        exportDto.setWriteOffAmount(order.getWriteOffAmount());
        exportDto.setWriteOffReason(order.getWriteOffReason());
        exportDto.setBalanceAmount(balance(order));
        exportDto.setFreightAmount(order.getFreightAmount());
        exportDto.setFreightCost(order.getFreightCost());
        exportDto.setTotalCostAmount(order.getTotalCostAmount());
        exportDto.setGrossProfit(order.getGrossProfit());
        exportDto.setSalesmanName(order.getSalesmanName());
        exportDto.setCreateTime(order.getCreateTime() != null ? order.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
        exportDto.setRemark(order.getRemark());
    }

    /**
     * Canonical order shipment transaction — the single path that deducts
     * inventory and advances order status to DELIVERED.
     * <p>
     * Row-locks the order with {@code SELECT ... FOR UPDATE} scoped to the
     * current tenant so concurrent {@code deliverOrder} / {@code confirmDelivery}
     * calls serialise until commit.
     * <p>
     * Idempotent: already DELIVERED or COMPLETED returns success without
     * touching inventory.  Only READY_TO_SHIP may proceed.
     */
    @Override
    @Transactional
    public void deliverOrder(Long orderId) {
        Long tenantId = TenantContext.getTenantId();

        // Row-lock the order within the current tenant
        Order order = orderMapper.selectByIdForUpdate(orderId, tenantId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        // Idempotency: already delivered or completed
        if (order.getStatus() == STATUS_DELIVERED || order.getStatus() == STATUS_COMPLETED) {
            return; // no-op success
        }

        // Only READY_TO_SHIP may proceed
        if (order.getStatus() != STATUS_READY_TO_SHIP) {
            throw new RuntimeException("订单状态不是待发货，无法发货");
        }

        // Query delivery plans with explicit tenant filter
        LambdaQueryWrapper<OrderDeliveryPlan> planWrapper = new LambdaQueryWrapper<>();
        planWrapper.eq(OrderDeliveryPlan::getOrderId, orderId);
        planWrapper.eq(OrderDeliveryPlan::getTenantId, tenantId);
        List<OrderDeliveryPlan> plans = deliveryPlanMapper.selectList(planWrapper);

        if (plans.isEmpty()) {
            throw new RuntimeException("订单没有配货计划，无法发货");
        }

        // Plans must be in ALLOCATED or OUT state (this version ships whole order)
        for (OrderDeliveryPlan plan : plans) {
            if (!OrderDeliveryPlan.Status.ALLOCATED.equals(plan.getStatus())
                    && !OrderDeliveryPlan.Status.OUT.equals(plan.getStatus())) {
                throw new RuntimeException("配货计划状态异常，无法发货");
            }
        }

        // Deduct inventory for each non-OUT plan; any failure rolls back the
        // entire transaction (prior inventory/plan/log updates + order state)
        for (OrderDeliveryPlan plan : plans) {
            if (OrderDeliveryPlan.Status.OUT.equals(plan.getStatus())) {
                continue; // already shipped
            }
            // null-safe: allocatedQty/outQty must both be present
            Integer allocatedQty = plan.getAllocatedQty();
            Integer outQty = plan.getOutQty();
            if (allocatedQty == null || outQty == null) {
                throw new RuntimeException(String.format(
                        "配货计划数据异常: 配货数量或已出库数量为空, planId=%d", plan.getId()));
            }
            int toOutQty = allocatedQty - outQty;
            if (toOutQty <= 0) {
                throw new RuntimeException(String.format(
                        "配货计划无待出库数量, planId=%d, allocatedQty=%d, outQty=%d",
                        plan.getId(), allocatedQty, outQty));
            }
            inventoryService.outByPlan(plan.getId(), toOutQty, getCurrentUserId());
        }

        // Advance order to DELIVERED
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
     * 订单取消：不涉及库存释放。
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

        order.setStatus(STATUS_CANCELLED);
        order.setRemark(order.getRemark() + " [取消原因：" + reason + "]");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
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
        vo.setWriteOffAmount(order.getWriteOffAmount());
        vo.setWriteOffReason(order.getWriteOffReason());
        vo.setBalanceAmount(balance(order));

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
        String prefix = "ORD" + date;
        String key = "order:no:" + tenantId + ":" + date;
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        long dbMaxSeq = resolveMaxOrderSeq(tenantId, prefix);
        if (counter.get() < dbMaxSeq) {
            counter.set(dbMaxSeq);
        }
        // 当天首次使用时设置过期时间为2天（跨天清零）
        if (counter.get() == 0) {
            counter.expire(2, TimeUnit.DAYS);
        }
        long seq = counter.incrementAndGet();
        return prefix + String.format("%04d", seq);
    }

    private long resolveMaxOrderSeq(Long tenantId, String prefix) {
        String maxOrderNo = orderMapper.selectMaxOrderNoByPrefix(tenantId, prefix);
        if (maxOrderNo == null || maxOrderNo.length() <= prefix.length()) {
            return 0L;
        }
        try {
            return Long.parseLong(maxOrderNo.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
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
            case 1: return "部分收款";
            case 2: return "已结清";
            default: return "未知";
        }
    }
}

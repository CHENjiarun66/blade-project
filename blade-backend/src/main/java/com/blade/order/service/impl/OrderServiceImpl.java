package com.blade.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.file.service.FileService;
import com.blade.inventory.entity.Warehouse;
import com.blade.inventory.mapper.WarehouseMapper;
import com.blade.order.dto.AddPaymentDTO;
import com.blade.order.dto.OrderCreateDTO;
import com.blade.order.dto.OrderExportDTO;
import com.blade.order.dto.OrderPageDTO;
import com.blade.order.dto.OrderUpdateDTO;
import com.blade.order.dto.OrderVO;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderDeliveryPlan;
import com.blade.order.entity.OrderFinancialRecord;
import com.blade.order.entity.OrderItem;
import com.blade.order.enums.FulfillmentMode;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.enums.FinancialRecordType;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.customer.service.CustomerStatsCacheService;
import com.blade.order.service.OrderAccessPolicy;
import com.blade.order.service.OrderActionService;
import com.blade.order.service.OrderCompatAdapter;
import com.blade.order.service.OrderFinanceSnapshotService;
import com.blade.order.service.OrderService;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductColor;
import com.blade.product.entity.ProductSku;
import com.blade.product.entity.ProductSize;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.product.service.ProductSkuSemantics;
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
    private final OrderFinancialRecordMapper financialRecordMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductColorMapper productColorMapper;
    private final ProductSizeMapper productSizeMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final WarehouseMapper warehouseMapper;
    private final RedissonClient redissonClient;
    private final FileService fileService;
    private final OrderActionService actionService;
    private final OrderFinanceSnapshotService snapshotService;
    private final OrderCompatAdapter compatAdapter;
    private final CustomerStatsCacheService customerStatsCacheService;
    private final OrderAccessPolicy accessPolicy;

    private static final String ORDER_TYPE_SPOT = "SPOT";
    private static final String ORDER_TYPE_PREORDER = "PREORDER";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    /**
     * 过渡期列表筛选口径（读侧兼容）：paid_amount 已由快照服务维护为累计实收，
     * 新旧行统一可用。系列 E 统一事实服务上线后由版本化口径替换。
     */
    private static final String NET_RECEIVABLE_SQL =
            "GREATEST(COALESCE(total_amount, 0) - COALESCE(refund_amount, 0) - COALESCE(write_off_amount, 0), 0)";

    @Autowired
    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                            OrderDeliveryPlanMapper deliveryPlanMapper,
                            OrderFinancialRecordMapper financialRecordMapper,
                            ProductSkuMapper productSkuMapper, ProductColorMapper productColorMapper,
                            ProductSizeMapper productSizeMapper, ProductMapper productMapper,
                            UserMapper userMapper,
                            WarehouseMapper warehouseMapper, RedissonClient redissonClient,
                            FileService fileService,
                            OrderActionService actionService,
                            OrderFinanceSnapshotService snapshotService,
                            OrderCompatAdapter compatAdapter,
                            CustomerStatsCacheService customerStatsCacheService,
                            OrderAccessPolicy accessPolicy) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.deliveryPlanMapper = deliveryPlanMapper;
        this.financialRecordMapper = financialRecordMapper;
        this.productSkuMapper = productSkuMapper;
        this.productColorMapper = productColorMapper;
        this.productSizeMapper = productSizeMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.warehouseMapper = warehouseMapper;
        this.redissonClient = redissonClient;
        this.fileService = fileService;
        this.actionService = actionService;
        this.snapshotService = snapshotService;
        this.compatAdapter = compatAdapter;
        this.customerStatsCacheService = customerStatsCacheService;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public PageResult<OrderVO> pageList(OrderPageDTO dto) {
        Page<Order> page = new Page<>(dto.getCurrent(), dto.getSize());
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTenantId, TenantContext.getTenantId());
        wrapper.eq(Order::getDeleted, 0);
        // 数据范围：无 viewAll 的用户只能看到本人开单
        if (!accessPolicy.hasViewAllScope()) {
            Long currentUserId = accessPolicy.currentUserId();
            wrapper.eq(Order::getSalesmanId, currentUserId != null ? currentUserId : -1L);
        }
        applyOrderPageFilters(wrapper, dto);

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

    private void applyOrderPageFilters(LambdaQueryWrapper<Order> wrapper, OrderPageDTO dto) {
        String orderNo = trimToNull(dto.getOrderNo());
        String customerName = trimToNull(dto.getCustomerName());

        if (orderNo != null && orderNo.equals(customerName)) {
            wrapper.and(query -> query.like(Order::getOrderNo, orderNo)
                    .or()
                    .like(Order::getCustomerName, customerName));
        } else {
            if (orderNo != null) {
                wrapper.eq(Order::getOrderNo, orderNo);
            }
            if (customerName != null) {
                wrapper.like(Order::getCustomerName, customerName);
            }
        }
        if (dto.getStatus() != null) {
            wrapper.eq(Order::getStatus, dto.getStatus());
        }
        if (dto.getPaymentStatus() != null) {
            wrapper.eq(Order::getPaymentStatus, dto.getPaymentStatus());
        }
        if (trimToNull(dto.getFulfillmentStatus()) != null) {
            wrapper.eq(Order::getFulfillmentStatus, dto.getFulfillmentStatus().trim());
        }
        if (trimToNull(dto.getCollectionStatus()) != null) {
            wrapper.eq(Order::getCollectionStatus, dto.getCollectionStatus().trim());
        }
        if (trimToNull(dto.getOrderType()) != null) {
            wrapper.eq(Order::getOrderType, dto.getOrderType().trim());
        }
        if (Boolean.TRUE.equals(dto.getHasBalance())) {
            wrapper.apply("COALESCE(paid_amount, 0) < " + NET_RECEIVABLE_SQL);
        } else if (Boolean.FALSE.equals(dto.getHasBalance())) {
            wrapper.apply("COALESCE(paid_amount, 0) >= " + NET_RECEIVABLE_SQL);
        }
        if (dto.getStartDate() != null) {
            wrapper.apply("COALESCE(order_date, DATE(create_time)) >= {0}", dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            wrapper.apply("COALESCE(order_date, DATE(create_time)) <= {0}", dto.getEndDate());
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public OrderVO getById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null || Integer.valueOf(1).equals(order.getDeleted())) {
            throw new RuntimeException("订单不存在");
        }
        // 订单所有权/数据范围（终审三轮 P0-2）
        accessPolicy.requireAccess(order);
        OrderVO vo = convertToVO(order);
        // 详情页财务流水仅对持有财务查看权限的用户返回（终审 P0-1：前端隐藏不等于权限控制）
        if (!currentAuthorities().contains("btn:order:viewFinance")) {
            return vo;
        }
        vo.setFinancialRecords(snapshotService.records(id, order.getTenantId()).stream()
                .map(r -> {
                    OrderVO.FinancialRecordVO recordVO = new OrderVO.FinancialRecordVO();
                    recordVO.setId(r.getId());
                    recordVO.setOrderId(r.getOrderId());
                    recordVO.setRecordType(r.getRecordType());
                    recordVO.setAmount(r.getAmount());
                    recordVO.setPaymentMethod(r.getPaymentMethod());
                    recordVO.setOccurredAt(r.getOccurredAt());
                    recordVO.setOperatorName(r.getOperatorName());
                    recordVO.setReason(r.getReason());
                    recordVO.setSource(r.getSource());
                    recordVO.setReversedRecordId(r.getReversedRecordId());
                    return recordVO;
                })
                .toList());
        return vo;
    }

    @Override
    @Transactional
    public Long create(OrderCreateDTO dto) {
        // 空租户显式拒绝（终审 P0-2）：业务写路径不允许默认租户回退
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw BusinessException.of(403, "缺少租户上下文");
        }

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
        order.setTenantId(tenantId);
        order.setFreightAmount(safeAmount(dto.getFreightAmount()));
        order.setFreightCost(safeAmount(dto.getFreightCost()));
        order.setNeedDelivery(dto.getNeedDelivery());
        order.setDeliveryAddress(dto.getDeliveryAddress());
        order.setIsDelivered(0);
        order.setDeliveredAt(null);
        order.setFulfillmentMode(FulfillmentMode.UNDECIDED.name());
        order.setSalesReturnAmount(ZERO);
        order.setDeleted(0);

        OrderTotals totals = calculateTotals(dto.getItems(), order.getFreightAmount(), order.getFreightCost());
        applyTotals(order, totals);

        // 新模型初始事实：CONFIRMED + UNPAID，旧字段由适配器投影
        order.setFulfillmentStatus(FulfillmentStatus.CONFIRMED.name());
        order.setStatus(compatAdapter.projectLegacyStatus(FulfillmentStatus.CONFIRMED));
        snapshotService.initializeForNewOrder(order);
        BigDecimal initialPaid = safeAmount(resolveInitialPaidAmount(dto, order.getTotalAmount()));
        if (initialPaid.compareTo(order.getTotalAmount()) > 0) {
            throw new RuntimeException("实收金额不能超过订单应收净额");
        }

        orderMapper.insert(order);
        insertOrderItems(order, dto.getItems(), tenantId);
        fileService.bindFilesFromJson("order", order.getId(), order.getImages());

        // 创建即收款：首笔 RECEIPT 走统一财务事实，快照重算
        if (initialPaid.compareTo(ZERO) > 0) {
            insertReceipt(order, initialPaid);
            snapshotService.recalculateAndApply(order);
        }

        customerStatsCacheService.evictPreferenceCache(order.getCustomerId());
        return order.getId();
    }

    private void insertReceipt(Order order, BigDecimal amount) {
        OrderFinancialRecord record = new OrderFinancialRecord();
        record.setTenantId(order.getTenantId());
        record.setOrderId(order.getId());
        record.setRecordType(FinancialRecordType.RECEIPT.name());
        record.setAmount(amount);
        record.setOccurredAt(LocalDateTime.now());
        record.setSource("PC");
        User user = getCurrentUser();
        record.setOperatorId(user != null ? user.getId() : null);
        record.setOperatorName(user != null ? user.getNickname() : null);
        record.setDeleted(0);
        financialRecordMapper.insert(record);
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
        if (dto.getPaymentStatus() != null && dto.getPaymentStatus() == 2) {
            return totalAmount;
        }
        if (dto.getPaymentStatus() != null && dto.getPaymentStatus() == 1) {
            return safeAmount(dto.getDepositAmount());
        }
        return ZERO;
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

    private record OrderTotals(BigDecimal totalAmount, BigDecimal totalCostAmount, BigDecimal grossProfit) {}

    @Override
    @Transactional
    public void update(OrderUpdateDTO dto) {
        Order order = orderMapper.selectById(dto.getId());
        if (order == null || Integer.valueOf(1).equals(order.getDeleted())) {
            throw new RuntimeException("订单不存在");
        }
        accessPolicy.requireAccess(order);
        // 终审 P0-7：可编辑性按新生命周期与财务事实判断，不再依赖旧数字状态
        boolean hasFinancialFacts = financialRecordMapper.selectCount(new LambdaQueryWrapper<OrderFinancialRecord>()
                .eq(OrderFinancialRecord::getOrderId, order.getId())
                .eq(OrderFinancialRecord::getTenantId, order.getTenantId())) > 0;
        boolean afterShipped = order.getFulfillmentStatus() != null
                && (com.blade.order.enums.FulfillmentStatus.SHIPPED.name().equals(order.getFulfillmentStatus())
                    || com.blade.order.enums.FulfillmentStatus.COMPLETED.name().equals(order.getFulfillmentStatus()));
        // 已发货（或已完成）后只允许补充备注/图片，金额结构和明细不再修改
        if (afterShipped) {
            if (dto.getRemark() != null) order.setRemark(dto.getRemark());
            if (dto.getImages() != null) order.setImages(dto.getImages());
            order.setUpdateTime(LocalDateTime.now());
            int rows = orderMapper.updateById(order);
            if (rows == 0) {
                throw BusinessException.of(409, "订单已被其他操作更新，请刷新后重试");
            }
            fileService.bindFilesFromJson("order", order.getId(), order.getImages());
            return;
        }
        boolean hasFinancialChange = amountChanged(order.getFreightAmount(), dto.getFreightAmount())
                || amountChanged(order.getFreightCost(), dto.getFreightCost())
                || (dto.getItems() != null && !dto.getItems().isEmpty());
        if (hasFinancialChange && (hasFinancialFacts
                || (order.getFulfillmentStatus() != null
                    && !com.blade.order.enums.FulfillmentStatus.CONFIRMED.name().equals(order.getFulfillmentStatus()))
                || order.getStatus() != 0)) {
            throw BusinessException.of(400, "订单已产生收款、履约或配货事实，不允许直接修改金额和明细；请使用取消、冲销或调整流程");
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
            // 订单价值变化后由统一快照服务重算收款快照
            snapshotService.recalculateAndApply(order);
        }
        order.setUpdateTime(LocalDateTime.now());
        int rows = orderMapper.updateById(order);
        if (rows == 0) {
            throw BusinessException.of(409, "订单已被其他操作更新，请刷新后重试");
        }
        fileService.bindFilesFromJson("order", order.getId(), order.getImages());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        accessPolicy.requireAccess(order);
        // 只允许删除尚未产生任何事实的确认订单（软删除，可恢复）
        if (order.getStatus() != null && order.getStatus() != 0) {
            throw new RuntimeException("只有待处理状态的订单可以删除");
        }
        if (order.getFulfillmentStatus() != null && !FulfillmentStatus.CONFIRMED.name().equals(order.getFulfillmentStatus())) {
            throw new RuntimeException("只有待处理状态的订单可以删除");
        }
        Long recordCount = financialRecordMapper.selectCount(new LambdaQueryWrapper<OrderFinancialRecord>()
                .eq(OrderFinancialRecord::getOrderId, id)
                .eq(OrderFinancialRecord::getTenantId, order.getTenantId()));
        if (recordCount > 0) {
            throw new RuntimeException("订单已产生财务流水，不能删除，请使用取消或冲销");
        }
        Long planCount = deliveryPlanMapper.selectCount(new LambdaQueryWrapper<OrderDeliveryPlan>()
                .eq(OrderDeliveryPlan::getOrderId, id)
                .eq(OrderDeliveryPlan::getTenantId, order.getTenantId()));
        if (planCount > 0) {
            throw new RuntimeException("订单已创建配货计划，不能删除，请先取消配货");
        }
        // 全局逻辑删除配置：deleteById 即软删除（UPDATE deleted=1），可恢复
        orderMapper.deleteById(id);
    }

    private OrderVO convertToVO(Order order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        // 新行展示新履约状态标签；历史行保持旧标签
        boolean migratedVo = order.getCollectionStatus() != null;
        vo.setStatusName(migratedVo ? fulfillmentStatusLabel(order.getFulfillmentStatus())
                : getStatusName(order.getStatus()));
        vo.setPaymentStatusName(getPaymentStatusName(order.getPaymentStatus()));
        vo.setOrderTypeName(getOrderTypeName(order.getOrderType()));
        vo.setWriteOffAmount(order.getWriteOffAmount());
        vo.setWriteOffReason(order.getWriteOffReason());
        // 尾款展示：新行用快照，历史行按旧公式回退（只读展示）
        boolean migrated = order.getCollectionStatus() != null;
        vo.setBalanceAmount(migrated ? order.getBalanceAmount() : legacyBalanceForDisplay(order));
        vo.setFulfillmentStatus(order.getFulfillmentStatus());
        vo.setCollectionStatus(order.getCollectionStatus());
        vo.setFulfillmentMode(order.getFulfillmentMode());
        vo.setSettledAt(order.getSettledAt());
        vo.setSettlementMethod(order.getSettlementMethod());
        vo.setGrossReceivedAmount(order.getGrossReceivedAmount());
        vo.setCashRefundAmount(order.getCashRefundAmount());
        vo.setSalesReturnAmount(order.getSalesReturnAmount());
        vo.setNetReceivedAmount(order.getNetReceivedAmount());
        vo.setLegacyUnmigrated(!migrated);
        vo.setAllowedActions(actionService.computeAllowedActions(order));

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
            // 明细 SKU 类型（占位标记供前端引导拆分）
            List<Long> itemSkuIds = items.stream().map(OrderItem::getSkuId).filter(java.util.Objects::nonNull).distinct().toList();
            java.util.Map<Long, ProductSku> itemSkuMap = itemSkuIds.isEmpty() ? java.util.Map.of()
                    : productSkuMapper.selectBatchIds(itemSkuIds).stream()
                            .collect(Collectors.toMap(ProductSku::getId, sk -> sk));
            java.util.Set<Long> variantProductIds = ProductSkuSemantics.findProductsWithActiveVariants(
                    productSkuMapper, itemSkuMap.values());
            List<OrderVO.OrderItemVO> itemVOList = items.stream().map(item -> {
                OrderVO.OrderItemVO itemVO = new OrderVO.OrderItemVO();
                itemVO.setId(item.getId());
                itemVO.setSkuId(item.getSkuId());
                ProductSku itemSku = item.getSkuId() == null ? null : itemSkuMap.get(item.getSkuId());
                itemVO.setSkuType(itemSku != null ? itemSku.getSkuType() : null);
                itemVO.setVariantUnresolved(ProductSkuSemantics.requiresVariantResolution(
                        itemSku, variantProductIds));
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

    private java.util.Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return java.util.Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    /** 历史未迁移行的尾款展示回退（只读，不落库）。 */
    private BigDecimal legacyBalanceForDisplay(Order order) {
        BigDecimal legacy = safeAmount(order.getTotalAmount())
                .subtract(safeAmount(order.getRefundAmount()))
                .subtract(safeAmount(order.getWriteOffAmount()))
                .subtract(safeAmount(order.getPaidAmount()));
        return legacy.max(ZERO);
    }

    private Long getCurrentUserId() {
        // 无默认用户（终审 P0-2）：无登录上下文时操作人为空，不得伪造 admin
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    private User getCurrentUser() {
        return accessPolicy.currentUser();
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

    private String fulfillmentStatusLabel(String status) {
        if (status == null) return "";
        switch (status) {
            case "CONFIRMED": return "已确认";
            case "WAITING_ALLOCATION": return "待配货";
            case "ALLOCATING": return "配货中";
            case "READY_TO_SHIP": return "待发货";
            case "SHIPPED": return "已发货";
            case "COMPLETED": return "已完成";
            case "CANCELLED": return "已取消";
            default: return status;
        }
    }

    private String collectionStatusLabel(String status) {
        if (status == null) return "";
        switch (status) {
            case "UNPAID": return "未收款";
            case "PARTIAL": return "部分收款";
            case "SETTLED": return "已结清";
            default: return status;
        }
    }

    private String fulfillmentModeLabel(String mode) {
        if (mode == null) return "";
        switch (mode) {
            case "UNDECIDED": return "尚未选择";
            case "STOCK_LINKED": return "关联库存";
            case "RECORD_ONLY": return "仅记录订单";
            default: return mode;
        }
    }

    private String settlementMethodLabel(String method) {
        if (method == null) return "";
        switch (method) {
            case "FULL_RECEIPT": return "足额收款";
            case "WRITE_OFF": return "短款结清";
            case "MIGRATION_CONFIRMED": return "迁移确认";
            default: return method;
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

    // ==================== 旧接口委托统一动作服务 ====================

    /**
     * 订单付款确认（旧接口）：委托统一收款动作。
     */
    @Transactional
    public void confirmPayment(Long orderId, BigDecimal paidAmount) {
        actionService.recordPayment(orderId, paidAmount, null, null, "PC");
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
     * 追加收款 / 标记结清（旧接口）：委托统一动作服务。
     */
    @Override
    @Transactional
    public void addPayment(Long orderId, AddPaymentDTO dto) {
        actionService.addPaymentCompat(orderId, dto);
    }

    /**
     * 订单发货（旧接口）：委托统一发货事务。
     */
    @Override
    @Transactional
    public void deliverOrder(Long orderId) {
        actionService.shipOrder(orderId, "PC");
    }

    /**
     * 订单完成（旧接口）：委托统一动作服务。
     */
    @Transactional
    public void completeOrder(Long orderId) {
        actionService.completeOrder(orderId, "PC");
    }

    /**
     * 订单取消（旧接口）：委托统一动作服务。
     */
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        actionService.cancelOrder(orderId, reason, "PC");
    }

    @Override
    public List<OrderExportDTO> exportOrders(OrderPageDTO dto) {
        Long tenantId = TenantContext.getTenantId();

        // 查询所有符合筛选条件的订单（不分页）
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTenantId, tenantId);
        wrapper.eq(Order::getDeleted, 0);
        applyOrderPageFilters(wrapper, dto);

        wrapper.orderByDesc(Order::getCreateTime);

        // 导出上限显式化：超过上限要求缩小筛选范围，不允许静默丢数据
        long matchTotal = orderMapper.selectCount(wrapper);
        if (matchTotal > 10000) {
            throw new RuntimeException("符合筛选条件的订单共 " + matchTotal + " 条，超过单次导出上限 10000 条，请缩小筛选范围");
        }
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
        boolean migratedRow = order.getCollectionStatus() != null;
        exportDto.setFulfillmentStatusName(migratedRow ? fulfillmentStatusLabel(order.getFulfillmentStatus()) : null);
        exportDto.setCollectionStatusName(migratedRow ? collectionStatusLabel(order.getCollectionStatus()) : null);
        exportDto.setFulfillmentModeName(migratedRow ? fulfillmentModeLabel(order.getFulfillmentMode()) : null);
        exportDto.setGrossReceivedAmount(order.getGrossReceivedAmount());
        exportDto.setCashRefundAmount(order.getCashRefundAmount());
        exportDto.setNetReceivedAmount(order.getNetReceivedAmount());
        exportDto.setSettlementMethodName(migratedRow ? settlementMethodLabel(order.getSettlementMethod()) : null);
        exportDto.setCustomerName(order.getCustomerName());
        exportDto.setCustomerPhone(order.getCustomerPhone());
        exportDto.setTotalAmount(order.getTotalAmount());
        exportDto.setPaidAmount(order.getPaidAmount());
        exportDto.setWriteOffAmount(order.getWriteOffAmount());
        exportDto.setWriteOffReason(order.getWriteOffReason());
        boolean migrated = order.getCollectionStatus() != null;
        exportDto.setBalanceAmount(migrated ? order.getBalanceAmount() : legacyBalanceForDisplay(order));
        exportDto.setFreightAmount(order.getFreightAmount());
        exportDto.setFreightCost(order.getFreightCost());
        exportDto.setTotalCostAmount(order.getTotalCostAmount());
        exportDto.setGrossProfit(order.getGrossProfit());
        exportDto.setSalesmanName(order.getSalesmanName());
        exportDto.setCreateTime(order.getCreateTime() != null ? order.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
        exportDto.setRemark(order.getRemark());
    }
}

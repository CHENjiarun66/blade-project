package com.blade.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.customer.service.CustomerStatsCacheService;
import com.blade.inventory.service.InventoryService;
import com.blade.order.dto.AddPaymentDTO;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderAdjustmentLog;
import com.blade.order.entity.OrderDeliveryPlan;
import com.blade.order.entity.OrderFinancialRecord;
import com.blade.order.entity.OrderStateTransitionLog;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentMode;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.enums.FinancialRecordType;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.mapper.OrderStateTransitionLogMapper;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一订单动作服务：订单状态、履约状态和财务快照的唯一写入口。
 * <p>
 * 每个动作在同一事务内完成：校验租户/权限/状态/参数/幂等键 → 行锁订单
 * → 写财务流水（如有）→ 重算快照 → 写新状态 → 写状态日志 → 由
 * OrderCompatAdapter 投影旧字段 → 单次落库。Controller、草稿、库存和前端
 * 不得绕过本服务直接写状态或快照。
 * <p>
 * 历史未迁移行（fulfillment_status 为 NULL）：允许财务动作（自动固化期初流水），
 * 履约动作一律拒绝，等待 SOW-7 迁移工具按证据写入。
 */
@Service
public class OrderActionService {

    public static final String CONFIRM_DRAFT = "confirmDraft";
    public static final String RECORD_PAYMENT = "recordPayment";
    public static final String SETTLE_WITH_WRITE_OFF = "settleWithWriteOff";
    public static final String REFUND_PAYMENT = "refundPayment";
    public static final String REVERSE_FINANCIAL_RECORD = "reverseFinancialRecord";
    public static final String CHOOSE_FULFILLMENT_MODE = "chooseFulfillmentMode";
    public static final String START_ALLOCATION = "startAllocation";
    public static final String CONFIRM_ALLOCATION = "confirmAllocation";
    public static final String SHIP_ORDER = "shipOrder";
    public static final String COMPLETE_ORDER = "completeOrder";
    public static final String CANCEL_ORDER = "cancelOrder";

    private final OrderMapper orderMapper;
    private final OrderFinancialRecordMapper recordMapper;
    private final OrderStateTransitionLogMapper transitionLogMapper;
    private final OrderDeliveryPlanMapper deliveryPlanMapper;
    private final OrderAdjustmentLogMapper adjustmentLogMapper;
    private final OrderFinanceSnapshotService snapshotService;
    private final OrderCompatAdapter compatAdapter;
    private final InventoryService inventoryService;
    private final OrderPlaceholderSplitService placeholderSplitService;
    private final CustomerStatsCacheService customerStatsCacheService;

    public OrderActionService(OrderMapper orderMapper,
                              OrderFinancialRecordMapper recordMapper,
                              OrderStateTransitionLogMapper transitionLogMapper,
                              OrderDeliveryPlanMapper deliveryPlanMapper,
                              OrderAdjustmentLogMapper adjustmentLogMapper,
                              OrderFinanceSnapshotService snapshotService,
                              OrderCompatAdapter compatAdapter,
                              InventoryService inventoryService,
                              OrderPlaceholderSplitService placeholderSplitService,
                              CustomerStatsCacheService customerStatsCacheService) {
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
        this.transitionLogMapper = transitionLogMapper;
        this.deliveryPlanMapper = deliveryPlanMapper;
        this.adjustmentLogMapper = adjustmentLogMapper;
        this.snapshotService = snapshotService;
        this.compatAdapter = compatAdapter;
        this.inventoryService = inventoryService;
        this.placeholderSplitService = placeholderSplitService;
        this.customerStatsCacheService = customerStatsCacheService;
    }

    // ==================== 财务动作 ====================

    /**
     * 正常收款：amount 必须大于 0 且不超过当前尾款。幂等键命中直接成功返回。
     */
    @Transactional
    public void recordPayment(Long orderId, BigDecimal amount, String paymentMethod,
                              String idempotencyKey, String source) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.of(400, "收款金额必须大于0");
        }
        Order order = lockForFinancialAction(orderId, idempotencyKey);
        if (order == null) return; // 幂等重放
        requireMutableCollection(order);
        BigDecimal balance = balanceForValidation(order);
        if (balance.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(balance) > 0) {
            throw BusinessException.of(400, "收款金额不能超过当前尾款：" + balance);
        }
        String fromCollection = order.getCollectionStatus();
        insertRecord(order, FinancialRecordType.RECEIPT, amount, paymentMethod, null, idempotencyKey, source);
        snapshotService.recalculate(order);
        writeTransitionLog(order, RECORD_PAYMENT, order.getFulfillmentStatus(), fromCollection,
                order.getFulfillmentMode(), source, null, idempotencyKey);
        persist(order);
    }

    /**
     * 短款核销结清：先收 receiptAmount（可为 0，但订单需已有正数实收），剩余尾款写入 WRITE_OFF。
     */
    @Transactional
    public void settleWithWriteOff(Long orderId, BigDecimal receiptAmount, String reason,
                                   String idempotencyKey, String source) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.of(400, "标记结清必须填写原因");
        }
        if (receiptAmount == null || receiptAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw BusinessException.of(400, "收款金额不能为负数");
        }
        Order order = lockForFinancialAction(orderId, idempotencyKey);
        if (order == null) return; // 幂等重放
        if (CollectionStatus.SETTLED.name().equals(order.getCollectionStatus())) {
            throw BusinessException.of(400, "订单已结清，无需核销");
        }
        if (netReceivedForValidation(order).compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.of(400, "订单还没有正数实收，不能整单核销");
        }
        BigDecimal balance = balanceForValidation(order);
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.of(400, "当前已无尾款，无需标记结清");
        }
        if (receiptAmount.compareTo(balance) > 0) {
            throw BusinessException.of(400, "收款金额不能超过当前尾款：" + balance);
        }
        String fromCollection = order.getCollectionStatus();
        if (receiptAmount.compareTo(BigDecimal.ZERO) > 0) {
            insertRecord(order, FinancialRecordType.RECEIPT, receiptAmount, null, null, idempotencyKey, source);
        }
        insertRecord(order, FinancialRecordType.WRITE_OFF, balance.subtract(receiptAmount), null,
                reason, null, source);
        snapshotService.recalculate(order);
        order.setWriteOffReason(reason);
        writeTransitionLog(order, SETTLE_WITH_WRITE_OFF, order.getFulfillmentStatus(), fromCollection,
                order.getFulfillmentMode(), source, reason, idempotencyKey);
        persist(order);
    }

    /**
     * 现金退款：只表示现金流出，与销售退货无关；不能超过累计实收。
     */
    @Transactional
    public void refundPayment(Long orderId, BigDecimal amount, String reason,
                              String idempotencyKey, String source) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.of(400, "退款金额必须大于0");
        }
        if (reason == null || reason.isBlank()) {
            throw BusinessException.of(400, "退款必须填写原因");
        }
        Order order = lockForFinancialAction(orderId, idempotencyKey);
        if (order == null) return; // 幂等重放
        // 终审 P0-3：额度 = 有效累计实收 − 有效累计现金退款；连续多次退款不得超过剩余额度
        BigDecimal refundable = refundableForValidation(order);
        if (refundable.compareTo(amount) < 0) {
            throw BusinessException.of(400, "退款金额超过剩余可退额度：" + refundable);
        }
        String fromCollection = order.getCollectionStatus();
        insertRecord(order, FinancialRecordType.REFUND, amount, null, reason, idempotencyKey, source);
        snapshotService.recalculate(order);
        writeTransitionLog(order, REFUND_PAYMENT, order.getFulfillmentStatus(), fromCollection,
                order.getFulfillmentMode(), source, reason, idempotencyKey);
        persist(order);
    }

    /**
     * 冲销财务流水：追加 REVERSAL，不修改原记录。禁止冲销 REVERSAL；
     * 同一原流水的并发双冲销由 uk_ofr_reversal 唯一键兜底，只能成功一次。
     */
    @Transactional
    public void reverseFinancialRecord(Long pathOrderId, Long recordId, String reason, String idempotencyKey, String source) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.of(400, "冲销必须填写原因");
        }
        Long tenantId = currentTenant();
        OrderFinancialRecord target = recordMapper.selectOne(new LambdaQueryWrapper<OrderFinancialRecord>()
                .eq(OrderFinancialRecord::getId, recordId)
                .eq(OrderFinancialRecord::getTenantId, tenantId));
        if (target == null) {
            throw BusinessException.of(404, "财务流水不存在");
        }
        // 终审 P1-6：资源边界——流水必须属于路径中的订单
        if (!target.getOrderId().equals(pathOrderId)) {
            throw BusinessException.of(400, "财务流水不属于当前订单");
        }
        if (FinancialRecordType.REVERSAL.name().equals(target.getRecordType())) {
            throw BusinessException.of(400, "冲销记录不能再被冲销");
        }
        Long reversedCount = recordMapper.selectCount(new LambdaQueryWrapper<OrderFinancialRecord>()
                .eq(OrderFinancialRecord::getReversedRecordId, recordId)
                .eq(OrderFinancialRecord::getTenantId, tenantId));
        if (reversedCount > 0) {
            throw BusinessException.of(400, "该流水已被冲销");
        }
        Order order = orderMapper.selectByIdForUpdate(target.getOrderId(), tenantId);
        if (order == null) {
            throw BusinessException.of(404, "订单不存在");
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            OrderFinancialRecord replay = recordMapper.selectOne(new LambdaQueryWrapper<OrderFinancialRecord>()
                    .eq(OrderFinancialRecord::getTenantId, tenantId)
                    .eq(OrderFinancialRecord::getIdempotencyKey, idempotencyKey)
                    .last("LIMIT 1"));
            if (replay != null) return; // 幂等重放
        }
        String fromCollection = order.getCollectionStatus();
        OrderFinancialRecord reversal = new OrderFinancialRecord();
        reversal.setTenantId(tenantId);
        reversal.setOrderId(order.getId());
        reversal.setRecordType(FinancialRecordType.REVERSAL.name());
        reversal.setAmount(target.getAmount());
        reversal.setOccurredAt(LocalDateTime.now());
        reversal.setReason(reason);
        reversal.setSource(source);
        reversal.setIdempotencyKey(idempotencyKey);
        reversal.setReversedRecordId(recordId);
        reversal.setOperatorId(currentUserId());
        reversal.setOperatorName(currentUserName());
        reversal.setDeleted(0);
        recordMapper.insert(reversal);
        snapshotService.recalculate(order);
        writeTransitionLog(order, REVERSE_FINANCIAL_RECORD, order.getFulfillmentStatus(), fromCollection,
                order.getFulfillmentMode(), source, reason, idempotencyKey);
        persist(order);
    }

    // ==================== 履约动作 ====================

    /**
     * 选择履约方式：已结清且未选择。RECORD_ONLY 直接完成，STOCK_LINKED 进入待配货。
     */
    @Transactional
    public void chooseFulfillmentMode(Long orderId, FulfillmentMode mode, String source) {
        if (mode == null || mode == FulfillmentMode.UNDECIDED) {
            throw BusinessException.of(400, "必须选择关联库存或仅记录订单");
        }
        Order order = lockOrder(orderId);
        requireMigrated(order);
        if (!CollectionStatus.SETTLED.name().equals(order.getCollectionStatus())) {
            throw BusinessException.of(400, "订单结清后才能选择履约方式");
        }
        requireStatus(order, FulfillmentStatus.CONFIRMED);
        if (!FulfillmentMode.UNDECIDED.name().equals(order.getFulfillmentMode())) {
            throw BusinessException.of(400, "履约方式已选择，不能重复选择");
        }
        order.setFulfillmentMode(mode.name());
        order.setFulfillmentDecidedAt(LocalDateTime.now());
        order.setFulfillmentDecidedBy(currentUserId());
        if (mode == FulfillmentMode.RECORD_ONLY) {
            transition(order, FulfillmentStatus.COMPLETED);
            order.setCompleteTime(LocalDateTime.now());
        } else {
            transition(order, FulfillmentStatus.WAITING_ALLOCATION);
        }
        writeTransitionLog(order, CHOOSE_FULFILLMENT_MODE, order.getFulfillmentStatus(),
                order.getCollectionStatus(), FulfillmentMode.UNDECIDED.name(), source, mode.name(), null);
        persist(order);
    }

    /**
     * 创建配货计划：仅 STOCK_LINKED 且待配货（系列 C 在此接入占位 SKU 阻断与计划创建）。
     */
    @Transactional
    public void startAllocation(Long orderId, String source) {
        Order order = lockOrder(orderId);
        requireMigrated(order);
        if (!FulfillmentMode.STOCK_LINKED.name().equals(order.getFulfillmentMode())) {
            throw BusinessException.of(400, "仅记录订单或未选择履约方式的订单不能配货");
        }
        requireStatus(order, FulfillmentStatus.WAITING_ALLOCATION);
        // 占位履约保护：含占位明细的订单必须先拆分到真实 SKU
        if (placeholderSplitService.hasPlaceholderItems(orderId, currentTenant())) {
            throw BusinessException.of(400, "订单仍包含未指定颜色/尺码的占位明细，请先完成拆分");
        }
        transition(order, FulfillmentStatus.ALLOCATING);
        order.setAdjustmentStatus(Order.AdjustmentStatus.PENDING);
        writeTransitionLog(order, START_ALLOCATION, FulfillmentStatus.WAITING_ALLOCATION.name(),
                order.getCollectionStatus(), FulfillmentMode.STOCK_LINKED.name(), source, null, null);
        persist(order);
    }

    /**
     * 确认配货方案：配货中 → 待发货（配货计划状态与仓库同步由配货服务完成后调用本动作）。
     */
    @Transactional
    public void confirmAllocation(Long orderId, String source) {
        Order order = lockOrder(orderId);
        requireMigrated(order);
        requireStatus(order, FulfillmentStatus.ALLOCATING);
        transition(order, FulfillmentStatus.READY_TO_SHIP);
        order.setAdjustmentStatus(Order.AdjustmentStatus.APPROVED);
        writeTransitionLog(order, CONFIRM_ALLOCATION, FulfillmentStatus.ALLOCATING.name(),
                order.getCollectionStatus(), order.getFulfillmentMode(), source, null, null);
        persist(order);
    }

    /**
     * 内部状态机辅助：删除/取消配货后回到待配货。
     * 仅供配货服务在"订单仍处于配货中"前置校验后调用；已发货/已完成订单不可达此处。
     */
    @Transactional
    public void revertAllocationToWaiting(Long orderId, String source) {
        Order order = lockOrder(orderId);
        requireMigrated(order);
        requireStatus(order, FulfillmentStatus.ALLOCATING);
        transition(order, FulfillmentStatus.WAITING_ALLOCATION);
        order.setAdjustmentStatus(Order.AdjustmentStatus.NONE);
        writeTransitionLog(order, "revertAllocation", FulfillmentStatus.ALLOCATING.name(),
                order.getCollectionStatus(), order.getFulfillmentMode(), source, "配货方案删除/取消", null);
        persist(order);
    }

    /**
     * 订单发货（唯一出库扣库存事务入口）：待发货 → 已发货。
     * 幂等：已发货/已完成直接成功返回。
     */
    @Transactional
    public void shipOrder(Long orderId, String source) {
        Long tenantId = currentTenant();
        Order order = orderMapper.selectByIdForUpdate(orderId, tenantId);
        if (order == null) {
            throw BusinessException.of(404, "订单不存在");
        }
        FulfillmentStatus current = currentStatus(order);
        if (current == FulfillmentStatus.SHIPPED || current == FulfillmentStatus.COMPLETED) {
            return; // 幂等成功
        }
        requireMigrated(order);
        requireStatus(order, FulfillmentStatus.READY_TO_SHIP);
        // 占位履约保护（纵深防御）：出库前再次确认没有占位明细
        if (placeholderSplitService.hasPlaceholderItems(orderId, tenantId)) {
            throw BusinessException.of(400, "订单仍包含未指定颜色/尺码的占位明细，请先完成拆分");
        }

        List<OrderDeliveryPlan> plans = deliveryPlanMapper.selectList(new LambdaQueryWrapper<OrderDeliveryPlan>()
                .eq(OrderDeliveryPlan::getOrderId, orderId)
                .eq(OrderDeliveryPlan::getTenantId, tenantId));
        if (plans.isEmpty()) {
            throw BusinessException.of(400, "订单没有配货计划，无法发货");
        }
        for (OrderDeliveryPlan plan : plans) {
            if (!OrderDeliveryPlan.Status.ALLOCATED.equals(plan.getStatus())
                    && !OrderDeliveryPlan.Status.OUT.equals(plan.getStatus())) {
                throw BusinessException.of(400, "配货计划状态异常，无法发货");
            }
        }
        for (OrderDeliveryPlan plan : plans) {
            if (OrderDeliveryPlan.Status.OUT.equals(plan.getStatus())) {
                continue;
            }
            Integer allocatedQty = plan.getAllocatedQty();
            Integer outQty = plan.getOutQty();
            if (allocatedQty == null || outQty == null) {
                throw BusinessException.of(400, String.format(
                        "配货计划数据异常: 配货数量或已出库数量为空, planId=%d", plan.getId()));
            }
            int toOutQty = allocatedQty - outQty;
            if (toOutQty <= 0) {
                throw BusinessException.of(400, String.format(
                        "配货计划无待出库数量, planId=%d", plan.getId()));
            }
            inventoryService.outByPlan(plan.getId(), toOutQty, currentUserId());
        }
        transition(order, FulfillmentStatus.SHIPPED);
        order.setIsDelivered(1);
        order.setDeliveredAt(LocalDateTime.now());
        order.setDeliverTime(LocalDateTime.now());
        writeTransitionLog(order, SHIP_ORDER, FulfillmentStatus.READY_TO_SHIP.name(),
                order.getCollectionStatus(), order.getFulfillmentMode(), source, null, null);
        persist(order);
    }

    /**
     * 完成订单：已发货 → 已完成。
     */
    @Transactional
    public void completeOrder(Long orderId, String source) {
        Order order = lockOrder(orderId);
        requireMigrated(order);
        requireStatus(order, FulfillmentStatus.SHIPPED);
        transition(order, FulfillmentStatus.COMPLETED);
        order.setCompleteTime(LocalDateTime.now());
        writeTransitionLog(order, COMPLETE_ORDER, FulfillmentStatus.SHIPPED.name(),
                order.getCollectionStatus(), order.getFulfillmentMode(), source, null, null);
        persist(order);
    }

    /**
     * 取消订单：确认/待配货/配货中可取消；清理未履约计划；已出库订单不可取消。
     */
    @Transactional
    public void cancelOrder(Long orderId, String reason, String source) {
        Order order = lockOrder(orderId);
        requireMigrated(order);
        FulfillmentStatus current = currentStatus(order);
        if (current != FulfillmentStatus.CONFIRMED
                && current != FulfillmentStatus.WAITING_ALLOCATION
                && current != FulfillmentStatus.ALLOCATING) {
            throw BusinessException.of(400, "该订单当前状态不支持取消");
        }
        deliveryPlanMapper.delete(new LambdaQueryWrapper<OrderDeliveryPlan>()
                .eq(OrderDeliveryPlan::getOrderId, orderId)
                .eq(OrderDeliveryPlan::getTenantId, currentTenant()));
        adjustmentLogMapper.delete(new LambdaQueryWrapper<OrderAdjustmentLog>()
                .eq(OrderAdjustmentLog::getOrderId, orderId)
                .eq(OrderAdjustmentLog::getTenantId, currentTenant()));
        transition(order, FulfillmentStatus.CANCELLED);
        order.setAdjustmentStatus(Order.AdjustmentStatus.NONE);
        order.setRemark((order.getRemark() == null ? "" : order.getRemark()) + " [取消原因：" + reason + "]");
        writeTransitionLog(order, CANCEL_ORDER, current.name(),
                order.getCollectionStatus(), order.getFulfillmentMode(), source, reason, null);
        persist(order);
    }

    // ==================== allowedActions ====================

    /**
     * 按当前状态与登录用户权限计算可用动作。历史未迁移行只开放财务动作。
     */
    public List<String> computeAllowedActions(Order order) {
        List<String> actions = new ArrayList<>();
        Set<String> authorities = currentAuthorities();
        boolean migrated = order.getFulfillmentStatus() != null;
        FulfillmentStatus status = currentStatus(order);
        boolean settled = CollectionStatus.SETTLED.name().equals(order.getCollectionStatus());
        boolean hasReceipts = grossReceivedForValidation(order).compareTo(BigDecimal.ZERO) > 0;
        boolean hasRecords = hasReceipts
                || safe(order.getWriteOffAmount()).compareTo(BigDecimal.ZERO) > 0
                || safe(order.getCashRefundAmount()).compareTo(BigDecimal.ZERO) > 0;
        boolean terminal = status == FulfillmentStatus.COMPLETED || status == FulfillmentStatus.CANCELLED;

        if (authorities.contains("btn:order:recordPayment") && !terminal && !settled) {
            actions.add(RECORD_PAYMENT);
        }
        if (authorities.contains("btn:order:writeOff") && !terminal && !settled) {
            actions.add(SETTLE_WITH_WRITE_OFF);
        }
        if (authorities.contains("btn:order:refund") && hasReceipts) {
            actions.add(REFUND_PAYMENT);
        }
        if (authorities.contains("btn:order:reverse") && hasRecords) {
            actions.add(REVERSE_FINANCIAL_RECORD);
        }
        if (migrated
                && authorities.contains("btn:order:chooseFulfillment")
                && settled
                && FulfillmentMode.UNDECIDED.name().equals(order.getFulfillmentMode())
                && status == FulfillmentStatus.CONFIRMED) {
            actions.add(CHOOSE_FULFILLMENT_MODE);
        }
        if (migrated
                && authorities.contains("btn:order:allocate")
                && FulfillmentMode.STOCK_LINKED.name().equals(order.getFulfillmentMode())
                && status == FulfillmentStatus.WAITING_ALLOCATION) {
            actions.add(START_ALLOCATION);
        }
        if (migrated
                && authorities.contains("btn:order:allocate")
                && status == FulfillmentStatus.ALLOCATING) {
            actions.add(CONFIRM_ALLOCATION);
        }
        if (migrated
                && authorities.contains("btn:order:deliver")
                && status == FulfillmentStatus.READY_TO_SHIP) {
            actions.add(SHIP_ORDER);
        }
        if (migrated
                && authorities.contains("btn:order:deliver")
                && status == FulfillmentStatus.SHIPPED) {
            actions.add(COMPLETE_ORDER);
        }
        if (migrated
                && authorities.contains("btn:order:cancel")
                && (status == FulfillmentStatus.CONFIRMED
                    || status == FulfillmentStatus.WAITING_ALLOCATION
                    || status == FulfillmentStatus.ALLOCATING)) {
            actions.add(CANCEL_ORDER);
        }
        return actions;
    }

    // ==================== 内部工具 ====================

    /**
     * 财务动作前置：幂等键命中返回 null（调用方静默成功），否则行锁订单。
     */
    private Order lockForFinancialAction(Long orderId, String idempotencyKey) {
        Long tenantId = currentTenant();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            OrderFinancialRecord existing = recordMapper.selectOne(
                    new LambdaQueryWrapper<OrderFinancialRecord>()
                            .eq(OrderFinancialRecord::getTenantId, tenantId)
                            .eq(OrderFinancialRecord::getIdempotencyKey, idempotencyKey)
                            .last("LIMIT 1"));
            if (existing != null) {
                if (!existing.getOrderId().equals(orderId)) {
                    throw BusinessException.of(400, "幂等键已被其他订单使用");
                }
                return null;
            }
        }
        Order order = orderMapper.selectByIdForUpdate(orderId, tenantId);
        if (order == null) {
            throw BusinessException.of(404, "订单不存在");
        }
        return order;
    }

    private Order lockOrder(Long orderId) {
        Order order = orderMapper.selectByIdForUpdate(orderId, currentTenant());
        if (order == null) {
            throw BusinessException.of(404, "订单不存在");
        }
        return order;
    }

    private void requireTenant() {
        if (TenantContext.getTenantId() == null) {
            throw BusinessException.of(403, "缺少租户上下文");
        }
    }

    private void requireMigrated(Order order) {
        if (order.getFulfillmentStatus() == null) {
            throw BusinessException.of(400, "历史订单尚未迁移到新状态模型，请先完成历史迁移");
        }
    }

    private void requireMutableCollection(Order order) {
        FulfillmentStatus status = currentStatus(order);
        if (status == FulfillmentStatus.COMPLETED || status == FulfillmentStatus.CANCELLED) {
            throw BusinessException.of(400, "该订单当前状态不支持收款");
        }
        if (CollectionStatus.SETTLED.name().equals(order.getCollectionStatus())) {
            throw BusinessException.of(400, "订单已结清，无需追加");
        }
    }

    private void requireStatus(Order order, FulfillmentStatus expected) {
        FulfillmentStatus current = currentStatus(order);
        if (current != expected) {
            throw BusinessException.of(400, "订单状态不是" + expected.getLabel() + "，无法执行该操作");
        }
    }

    /** 当前履约状态：新行取新字段，历史行按旧字段回退（仅用于状态校验，不写回）。 */
    private FulfillmentStatus currentStatus(Order order) {
        if (order.getFulfillmentStatus() != null) {
            return FulfillmentStatus.valueOf(order.getFulfillmentStatus());
        }
        Integer legacy = order.getStatus();
        if (legacy == null) {
            return null;
        }
        switch (legacy) {
            case 0: return FulfillmentStatus.CONFIRMED;
            case 1: return FulfillmentStatus.WAITING_ALLOCATION;
            case 2: return FulfillmentStatus.ALLOCATING;
            case 3: return FulfillmentStatus.READY_TO_SHIP;
            case 4: return FulfillmentStatus.SHIPPED;
            case 5: return FulfillmentStatus.COMPLETED;
            case 6: return FulfillmentStatus.CANCELLED;
            default: return null;
        }
    }

    /**
     * 写入新履约状态并在同一事务投影旧 status（唯一投影入口）。
     */
    private void transition(Order order, FulfillmentStatus next) {
        order.setFulfillmentStatus(next.name());
        order.setStatus(compatAdapter.projectLegacyStatus(next));
    }

    /**
     * 统一落库：动作内所有变更（含快照与乐观版本）一次 updateById。
     * 订单/财务变化后失效相关客户统计缓存（系列 E 口径一致性）。
     */
    private void persist(Order order) {
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        customerStatsCacheService.evictPreferenceCache(order.getCustomerId());
    }

    private void insertRecord(Order order, FinancialRecordType type, BigDecimal amount,
                              String paymentMethod, String reason, String idempotencyKey, String source) {
        OrderFinancialRecord record = new OrderFinancialRecord();
        record.setTenantId(order.getTenantId());
        record.setOrderId(order.getId());
        record.setRecordType(type.name());
        record.setAmount(amount);
        record.setPaymentMethod(paymentMethod);
        record.setOccurredAt(LocalDateTime.now());
        record.setReason(reason);
        record.setSource(source);
        record.setIdempotencyKey(idempotencyKey);
        record.setOperatorId(currentUserId());
        record.setOperatorName(currentUserName());
        record.setDeleted(0);
        recordMapper.insert(record);
    }

    private void writeTransitionLog(Order order, String action, String fromFulfillment,
                                    String fromCollection, String fromMode, String source,
                                    String reason, String idempotencyKey) {
        OrderStateTransitionLog log = new OrderStateTransitionLog();
        log.setTenantId(order.getTenantId());
        log.setOrderId(order.getId());
        log.setAction(action);
        log.setFromFulfillmentStatus(fromFulfillment);
        log.setToFulfillmentStatus(order.getFulfillmentStatus());
        log.setFromCollectionStatus(fromCollection);
        log.setToCollectionStatus(order.getCollectionStatus());
        log.setFromFulfillmentMode(fromMode);
        log.setToFulfillmentMode(order.getFulfillmentMode());
        log.setOperatorId(currentUserId());
        log.setOperatorName(currentUserName());
        log.setSource(source);
        log.setReason(reason);
        log.setIdempotencyKey(idempotencyKey);
        log.setOccurredAt(LocalDateTime.now());
        transitionLogMapper.insert(log);
    }

    /**
     * 尾款校验口径：新行用快照，历史行按旧公式（max(total-refund-write_off-paid, 0)）。
     */
    private BigDecimal balanceForValidation(Order order) {
        if (order.getCollectionStatus() != null) {
            return safe(order.getBalanceAmount());
        }
        return safe(order.getTotalAmount())
                .subtract(safe(order.getRefundAmount()))
                .subtract(safe(order.getWriteOffAmount()))
                .subtract(safe(order.getPaidAmount()))
                .max(BigDecimal.ZERO);
    }

    /** 净实收校验口径：新行用快照，历史行用 paid_amount。 */
    private BigDecimal netReceivedForValidation(Order order) {
        if (order.getCollectionStatus() != null) {
            return safe(order.getNetReceivedAmount());
        }
        return safe(order.getPaidAmount());
    }

    /** 累计实收校验口径：新行用快照，历史行用 paid_amount。 */
    private BigDecimal grossReceivedForValidation(Order order) {
        if (order.getCollectionStatus() != null) {
            return safe(order.getGrossReceivedAmount());
        }
        return safe(order.getPaidAmount());
    }

    /**
     * 剩余可退额度 = 有效累计实收 − 有效累计现金退款（终审 P0-3）。
     * 行锁保证并发退款串行校验；退款被冲销后额度自动恢复。
     */
    private BigDecimal refundableForValidation(Order order) {
        BigDecimal gross = grossReceivedForValidation(order);
        BigDecimal refunded = order.getCollectionStatus() != null
                ? safe(order.getCashRefundAmount())
                : BigDecimal.ZERO; // 历史行没有退款流水（R2 起历史行整体拒绝退款）
        return gross.subtract(refunded).max(BigDecimal.ZERO);
    }

    private Long currentTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw BusinessException.of(403, "缺少租户上下文");
        }
        return tenantId;
    }

    private Long currentUserId() {
        User user = currentUser();
        return user != null ? user.getId() : null;
    }

    private String currentUserName() {
        User user = currentUser();
        return user != null ? user.getNickname() : null;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /** 旧接口 DTO 适配：markAsSettled 分流到核销动作。 */
    @Transactional
    public void addPaymentCompat(Long orderId, AddPaymentDTO dto) {
        requireTenant();
        if (Boolean.TRUE.equals(dto.getMarkAsSettled())) {
            settleWithWriteOff(orderId, safe(dto.getAdditionalAmount()), dto.getWriteOffReason(), null, "PC");
        } else {
            recordPayment(orderId, dto.getAdditionalAmount(), null, null, "PC");
        }
    }
}

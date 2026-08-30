package com.blade.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.common.exception.BusinessException;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderFinancialRecord;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FinancialRecordType;
import com.blade.order.enums.SettlementMethod;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一财务快照服务：订单金额快照与收款状态的唯一写入口。
 * <p>
 * 快照公式（15-ORDER_FINANCE_ANALYTICS_DESIGN §3）：
 * adjusted = max(total - sales_return, 0)
 * net_received = max(gross_received - cash_refund, 0)
 * settlement_required = max(adjusted - write_off, 0)
 * balance = max(settlement_required - net_received, 0)
 * <p>
 * 调用方必须在同一事务内已持有订单行锁（selectByIdForUpdate）。每次重算在单条
 * UPDATE 中同时写出全部快照列并自增乐观版本（配合 chk_so_snapshots_nonnegative）。
 * 历史未迁移行（collection_status IS NULL）首次财务动作时先落 MIGRATION_OPENING
 * / 迁移 WRITE_OFF 流水，使快照始终可由流水复算；legacy refund_amount 语义不可拆分，
 * 不生成流水、不并入新快照（15 号文档 §5.3，人工核对通道处理）。
 */
@Service
public class OrderFinanceSnapshotService {

    private final OrderMapper orderMapper;
    private final OrderFinancialRecordMapper recordMapper;
    private final OrderCompatAdapter compatAdapter;

    public OrderFinanceSnapshotService(OrderMapper orderMapper,
                                       OrderFinancialRecordMapper recordMapper,
                                       OrderCompatAdapter compatAdapter) {
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
        this.compatAdapter = compatAdapter;
    }

    /**
     * 重算并只修改实体字段（含 payment_status 投影），不落库、不推进版本。
     * 供统一动作服务在同一事务内合并其他字段后单次落库。
     */
    @Transactional
    public void recalculate(Order order) {
        seedLegacyOpeningIfUnmigrated(order);
        List<OrderFinancialRecord> records = effectiveRecords(order.getId(), order.getTenantId());

        BigDecimal gross = sum(records, FinancialRecordType.RECEIPT)
                .add(sum(records, FinancialRecordType.MIGRATION_OPENING));
        BigDecimal cashRefund = sum(records, FinancialRecordType.REFUND);
        BigDecimal writeOff = sum(records, FinancialRecordType.WRITE_OFF);

        BigDecimal total = safe(order.getTotalAmount());
        BigDecimal salesReturn = safe(order.getSalesReturnAmount());
        BigDecimal adjusted = total.subtract(salesReturn).max(BigDecimal.ZERO);
        BigDecimal netReceived = gross.subtract(cashRefund).max(BigDecimal.ZERO);
        BigDecimal settlementRequired = adjusted.subtract(writeOff).max(BigDecimal.ZERO);
        BigDecimal balance = settlementRequired.subtract(netReceived).max(BigDecimal.ZERO);

        CollectionStatus previous = order.getCollectionStatus() != null
                ? CollectionStatus.valueOf(order.getCollectionStatus()) : null;
        CollectionStatus next;
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            // 零金额订单必须人工确认才能结清，快照重算不得自动 SETTLED
            next = previous == CollectionStatus.SETTLED ? CollectionStatus.SETTLED : CollectionStatus.UNPAID;
        } else if (balance.compareTo(BigDecimal.ZERO) > 0) {
            next = netReceived.compareTo(BigDecimal.ZERO) > 0
                    ? CollectionStatus.PARTIAL : CollectionStatus.UNPAID;
        } else {
            next = CollectionStatus.SETTLED;
        }

        order.setGrossReceivedAmount(gross);
        order.setCashRefundAmount(cashRefund);
        order.setWriteOffAmount(writeOff);
        order.setNetReceivedAmount(netReceived);
        order.setBalanceAmount(balance);
        order.setCollectionStatus(next.name());

        if (next == CollectionStatus.SETTLED) {
            if (previous != CollectionStatus.SETTLED) {
                order.setSettledAt(LocalDateTime.now());
            }
            order.setSettlementMethod((writeOff.compareTo(BigDecimal.ZERO) > 0
                    ? SettlementMethod.WRITE_OFF : SettlementMethod.FULL_RECEIPT).name());
        } else {
            order.setSettlementMethod(null);
        }

        // paid_amount 兼容映射累计实收（15 号文档 §5.3），payment_status 由适配器投影
        order.setPaidAmount(gross);
        order.setPaymentStatus(compatAdapter.projectLegacyPaymentStatus(next));
        // 旧接口兼容：首次产生实收时补 pay_time
        if (netReceived.compareTo(BigDecimal.ZERO) > 0 && order.getPayTime() == null) {
            order.setPayTime(LocalDateTime.now());
        }
    }

    /**
     * 重算并立即落库（快照是唯一变更时使用）。
     */
    @Transactional
    public void recalculateAndApply(Order order) {
        recalculate(order);
        order.setVersion(order.getVersion() == null ? 1 : order.getVersion() + 1);
        orderMapper.updateById(order);
    }

    /**
     * 仅初始创建快照（创建订单时还没有任何流水）。collection_status 由快照公式决定。
     */
    @Transactional
    public void initializeForNewOrder(Order order) {
        BigDecimal zero = BigDecimal.ZERO;
        order.setGrossReceivedAmount(zero);
        order.setCashRefundAmount(zero);
        order.setWriteOffAmount(zero);
        order.setNetReceivedAmount(zero);
        order.setSalesReturnAmount(safe(order.getSalesReturnAmount()));
        BigDecimal total = safe(order.getTotalAmount());
        BigDecimal balance = total.subtract(safe(order.getSalesReturnAmount())).max(BigDecimal.ZERO);
        order.setBalanceAmount(balance);
        // 新订单一律 UNPAID；零金额订单必须经 markZeroAmountSettled 人工确认结清
        order.setCollectionStatus(CollectionStatus.UNPAID.name());
        order.setPaidAmount(zero);
        order.setPaymentStatus(compatAdapter.projectLegacyPaymentStatus(CollectionStatus.UNPAID));
        order.setVersion(0);
    }

    /**
     * 零金额订单必须人工确认才能结清：直接把空订单标记 SETTLED。
     */
    @Transactional
    public void markZeroAmountSettled(Order order, Long operatorId, String operatorName) {
        if (safe(order.getTotalAmount()).compareTo(BigDecimal.ZERO) != 0) {
            throw BusinessException.of(400, "仅零金额订单可以人工确认结清");
        }
        order.setCollectionStatus(CollectionStatus.SETTLED.name());
        order.setSettledAt(LocalDateTime.now());
        order.setSettlementMethod(SettlementMethod.FULL_RECEIPT.name());
        order.setPaymentStatus(compatAdapter.projectLegacyPaymentStatus(CollectionStatus.SETTLED));
        bumpVersion(order);
        orderMapper.updateById(order);
    }

    /**
     * 历史未迁移行首次财务动作：把旧 paid_amount / write_off_amount 固化为期初流水。
     * refund_amount 语义不可拆分，不落流水（人工核对通道处理）。
     */
    private void seedLegacyOpeningIfUnmigrated(Order order) {
        if (order.getCollectionStatus() != null) {
            return;
        }
        LocalDateTime occurredAt = order.getPayTime() != null ? order.getPayTime()
                : (order.getCreateTime() != null ? order.getCreateTime() : LocalDateTime.now());
        BigDecimal legacyPaid = safe(order.getPaidAmount());
        if (legacyPaid.compareTo(BigDecimal.ZERO) > 0) {
            insertRecord(order, FinancialRecordType.MIGRATION_OPENING, legacyPaid, occurredAt,
                    "历史实收期初（旧 paid_amount 快照）", null);
        }
        BigDecimal legacyWriteOff = safe(order.getWriteOffAmount());
        if (legacyWriteOff.compareTo(BigDecimal.ZERO) > 0) {
            insertRecord(order, FinancialRecordType.WRITE_OFF, legacyWriteOff, occurredAt,
                    "历史短款核销期初（旧 write_off_amount 快照）", null);
        }
        if (legacyPaid.compareTo(BigDecimal.ZERO) > 0 || legacyWriteOff.compareTo(BigDecimal.ZERO) > 0) {
            order.setCollectionStatus(CollectionStatus.UNPAID.name()); // 占位，随后由快照公式重算覆盖
        }
    }

    private void insertRecord(Order order, FinancialRecordType type, BigDecimal amount,
                              LocalDateTime occurredAt, String reason, String idempotencyKey) {
        OrderFinancialRecord record = new OrderFinancialRecord();
        record.setTenantId(order.getTenantId());
        record.setOrderId(order.getId());
        record.setRecordType(type.name());
        record.setAmount(amount);
        record.setOccurredAt(occurredAt);
        record.setReason(reason);
        record.setSource("MIGRATION");
        record.setIdempotencyKey(idempotencyKey);
        record.setDeleted(0);
        recordMapper.insert(record);
    }

    public List<OrderFinancialRecord> records(Long orderId, Long tenantId) {
        return recordMapper.selectList(new LambdaQueryWrapper<OrderFinancialRecord>()
                .eq(OrderFinancialRecord::getOrderId, orderId)
                .eq(OrderFinancialRecord::getTenantId, tenantId)
                .orderByAsc(OrderFinancialRecord::getOccurredAt)
                .orderByAsc(OrderFinancialRecord::getId));
    }

    /**
     * 有效流水 = 全部流水减去已被 REVERSAL 冲销的记录（冲销记录本身不参与聚合）。
     */
    private List<OrderFinancialRecord> effectiveRecords(Long orderId, Long tenantId) {
        List<OrderFinancialRecord> all = records(orderId, tenantId);
        java.util.Set<Long> reversedIds = all.stream()
                .filter(r -> FinancialRecordType.REVERSAL.name().equals(r.getRecordType()))
                .map(OrderFinancialRecord::getReversedRecordId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return all.stream()
                .filter(r -> !FinancialRecordType.REVERSAL.name().equals(r.getRecordType()))
                .filter(r -> !reversedIds.contains(r.getId()))
                .toList();
    }

    private BigDecimal sum(List<OrderFinancialRecord> records, FinancialRecordType type) {
        return records.stream()
                .filter(r -> type.name().equals(r.getRecordType()))
                .map(OrderFinancialRecord::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void bumpVersion(Order order) {
        order.setVersion(order.getVersion() == null ? 1 : order.getVersion() + 1);
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}

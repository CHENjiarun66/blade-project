package com.blade.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.order.entity.Order;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.mapper.OrderMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 统一、版本化订单事实服务（系列 E）。
 * <p>
 * 所有统计消费者（Dashboard、Analytics、Customer、Agent、WhatsApp、导出）必须通过本服务
 * 获取订单口径与金额公式，禁止各自复制状态条件或公式：
 * <ul>
 *   <li>经营订单：非取消的正式订单（草稿在独立表，天然排除）</li>
 *   <li>订单额：经营订单 total_amount（按 order_date 归属）</li>
 *   <li>净销售额：max(total - sales_return - write_off, 0)（15 号文档 §7.2）</li>
 *   <li>已产生收款：gross_received &gt; 0 或 collection_status=SETTLED（历史行按旧字段回退）</li>
 *   <li>现金流：按 order_financial_record.occurred_at 统计（RECEIPT+MIGRATION_OPENING/REFUND/WRITE_OFF）</li>
 *   <li>取消订单不进经营订单额，但其财务流水仍进现金流</li>
 *   <li>RECORD_ONLY 计入销售，不计入库存出库（库存出库只能来自实际库存流水）</li>
 * </ul>
 */
@Service
public class OrderFactsService {

    /** 口径版本：消费者响应应携带，便于排障与后续演进 */
    public static final String FACTS_VERSION = "order-facts-v1";

    private final OrderMapper orderMapper;

    public OrderFactsService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    // ==================== 口径判定 ====================

    /**
     * 经营订单：非取消的已迁移正式订单。
     * 终审三轮 P0-3：历史未迁移行不得参与新事实统计（只允许 VO 展示回退），
     * 迁移完成后再进入新统计。
     */
    public boolean isBusinessOrder(Order order) {
        if (order.getDeleted() != null && order.getDeleted() == 1) {
            return false;
        }
        if (order.getCollectionStatus() == null) {
            return false; // 历史未迁移行排除
        }
        return !FulfillmentStatus.CANCELLED.name().equals(order.getFulfillmentStatus());
    }

    /** 已完成（履约完成，仅已迁移行） */
    public boolean isFulfilled(Order order) {
        if (!isMigrated(order)) return false;
        return FulfillmentStatus.COMPLETED.name().equals(order.getFulfillmentStatus());
    }

    /** 已发货或更晚（仅已迁移行） */
    public boolean isShippedOrBeyond(Order order) {
        if (!isMigrated(order)) return false;
        return FulfillmentStatus.SHIPPED.name().equals(order.getFulfillmentStatus())
                || FulfillmentStatus.COMPLETED.name().equals(order.getFulfillmentStatus());
    }

    /** 已产生收款（仅已迁移行，按快照） */
    public boolean hasReceivedMoney(Order order) {
        if (!isMigrated(order)) return false;
        return gross(order).compareTo(BigDecimal.ZERO) > 0
                || CollectionStatus.SETTLED.name().equals(order.getCollectionStatus());
    }

    /** 已结清（仅已迁移行） */
    public boolean isSettled(Order order) {
        return isMigrated(order) && CollectionStatus.SETTLED.name().equals(order.getCollectionStatus());
    }

    // ==================== 金额公式 ====================

    /** 净销售额 = max(total - sales_return - write_off, 0) */
    public BigDecimal netSalesAmount(Order order) {
        return total(order)
                .subtract(salesReturn(order))
                .subtract(writeOff(order))
                .max(BigDecimal.ZERO);
    }

    /** 净毛利 = max(gross_profit - sales_return - write_off, 0)（本轮退货成本不冲回，报表已标注口径限制） */
    public BigDecimal netGrossProfitAmount(Order order) {
        BigDecimal grossProfit = order.getGrossProfit() == null ? BigDecimal.ZERO : order.getGrossProfit();
        return grossProfit
                .subtract(salesReturn(order))
                .subtract(writeOff(order))
                .max(BigDecimal.ZERO);
    }

    /**
     * 累计实收。数据代际按 collection_status 判定（终审 P1-3）：
     * V51 中新快照列 NOT NULL DEFAULT 0，不能用非空判断区分代际；
     * 历史未迁移行回退旧 paid_amount，避免迁移前统计成 0。
     */
    public BigDecimal gross(Order order) {
        return nz(order.getGrossReceivedAmount());
    }

    /** 已迁移（新模型行）：collection_status 非空即代表已进入新状态机 */
    public boolean isMigrated(Order order) {
        return order.getCollectionStatus() != null;
    }

    private BigDecimal total(Order order) {
        return nz(order.getTotalAmount());
    }

    private BigDecimal salesReturn(Order order) {
        return nz(order.getSalesReturnAmount());
    }

    private BigDecimal writeOff(Order order) {
        return nz(order.getWriteOffAmount());
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ==================== 查询口径 ====================

    /** 经营订单（非取消），按订单业务日期区间 */
    public List<Order> businessOrdersByOrderDate(Long tenantId, LocalDate start, LocalDate end) {
        List<Order> orders = ordersByOrderDate(tenantId, start, end);
        return orders.stream().filter(this::isBusinessOrder).toList();
    }

    /** 已产生收款的经营订单，按订单业务日期区间 */
    public List<Order> paidBusinessOrdersByOrderDate(Long tenantId, LocalDate start, LocalDate end) {
        return businessOrdersByOrderDate(tenantId, start, end).stream()
                .filter(this::hasReceivedMoney)
                .toList();
    }

    public List<Order> ordersByOrderDate(Long tenantId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTenantId, tenantId);
        wrapper.eq(Order::getDeleted, 0);
        wrapper.apply("COALESCE(order_date, DATE(create_time)) BETWEEN {0} AND {1}", start, end);
        return orderMapper.selectList(wrapper);
    }

    /** 客户的经营订单（非取消）——WhatsApp orderFacts / 客户统计使用 */
    public List<Order> customerBusinessOrders(Long tenantId, Long customerId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTenantId, tenantId);
        wrapper.eq(Order::getCustomerId, customerId);
        wrapper.eq(Order::getDeleted, 0);
        return orderMapper.selectList(wrapper).stream().filter(this::isBusinessOrder).toList();
    }
}

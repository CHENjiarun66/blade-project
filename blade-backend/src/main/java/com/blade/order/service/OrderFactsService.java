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

    /** 经营订单：非取消的正式订单（历史行按旧 status 判断；新行按 fulfillment_status） */
    public boolean isBusinessOrder(Order order) {
        if (order.getDeleted() != null && order.getDeleted() == 1) {
            return false;
        }
        if (order.getFulfillmentStatus() != null) {
            return !FulfillmentStatus.CANCELLED.name().equals(order.getFulfillmentStatus());
        }
        Integer legacy = order.getStatus();
        return legacy == null || (legacy != 6 && legacy != 8);
    }

    /** 已完成（履约完成） */
    public boolean isFulfilled(Order order) {
        if (order.getFulfillmentStatus() != null) {
            return FulfillmentStatus.COMPLETED.name().equals(order.getFulfillmentStatus());
        }
        Integer legacy = order.getStatus();
        return legacy != null && legacy == 5;
    }

    /** 已发货或更晚（完成/发货履约事实） */
    public boolean isShippedOrBeyond(Order order) {
        if (order.getFulfillmentStatus() != null) {
            return FulfillmentStatus.SHIPPED.name().equals(order.getFulfillmentStatus())
                    || FulfillmentStatus.COMPLETED.name().equals(order.getFulfillmentStatus());
        }
        Integer legacy = order.getStatus();
        return legacy != null && (legacy == 4 || legacy == 5);
    }

    /** 已产生收款：新行按累计实收/结清状态；历史行按旧字段 */
    public boolean hasReceivedMoney(Order order) {
        if (order.getCollectionStatus() != null) {
            return gross(order).compareTo(BigDecimal.ZERO) > 0
                    || CollectionStatus.SETTLED.name().equals(order.getCollectionStatus());
        }
        return nz(order.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0
                || order.getPaymentStatus() != null && (order.getPaymentStatus() == 1 || order.getPaymentStatus() == 2);
    }

    /** 已结清 */
    public boolean isSettled(Order order) {
        if (order.getCollectionStatus() != null) {
            return CollectionStatus.SETTLED.name().equals(order.getCollectionStatus());
        }
        return order.getPaymentStatus() != null && order.getPaymentStatus() == 2;
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

    /** 累计实收（新行快照；历史行 paid_amount） */
    public BigDecimal gross(Order order) {
        return order.getGrossReceivedAmount() != null ? order.getGrossReceivedAmount()
                : nz(order.getPaidAmount());
    }

    private BigDecimal total(Order order) {
        return nz(order.getTotalAmount());
    }

    private BigDecimal salesReturn(Order order) {
        if (order.getSalesReturnAmount() != null) {
            return order.getSalesReturnAmount();
        }
        // 历史未迁移行：refund_amount 语义不可拆分，销售口径保守按"价值减少"处理，
        // 与旧行为保持一致（15 号文档 §5.3，人工核对通道负责最终拆分）
        if (order.getCollectionStatus() == null) {
            return nz(order.getRefundAmount());
        }
        return BigDecimal.ZERO;
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

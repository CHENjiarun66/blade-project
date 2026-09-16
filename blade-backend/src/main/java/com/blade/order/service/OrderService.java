package com.blade.order.service;

import com.blade.common.result.PageResult;
import com.blade.order.dto.AddPaymentDTO;
import com.blade.order.dto.OrderCreateDTO;
import com.blade.order.dto.OrderExportDTO;
import com.blade.order.dto.OrderPageDTO;
import com.blade.order.dto.OrderUpdateDTO;
import com.blade.order.dto.OrderVO;
import java.util.List;

public interface OrderService {

    PageResult<OrderVO> pageList(OrderPageDTO dto);

    OrderVO getById(Long id);

    Long create(OrderCreateDTO dto);

    void update(OrderUpdateDTO dto);

    void delete(Long id);

    /**
     * 追加收款（兼容旧接口，委托到 DTO 重载）。
     * @deprecated 请使用 {@link #addPayment(Long, AddPaymentDTO)}
     */
    @Deprecated
    void addPayment(Long orderId, java.math.BigDecimal additionalAmount);

    /**
     * 追加收款 / 标记结清。
     * 支持普通追加和抹零/短款结清两种模式。
     */
    void addPayment(Long orderId, AddPaymentDTO dto);

    /**
     * 导出订单列表（不分页，返回所有符合筛选条件的数据）
     * @param dto 筛选条件
     * @return 导出数据列表
     */
    List<OrderExportDTO> exportOrders(OrderPageDTO dto);

    /**
     * Canonical order shipment transaction — the single path that deducts
     * inventory and advances order status to DELIVERED.
     * Called by both {@code POST /api/orders/{id}/deliver} and
     * {@code POST /api/order-deliveries/{id}/confirm}.
     */
    void deliverOrder(Long orderId);
}

package com.blade.order.service;

import com.blade.order.dto.OrderDeliveryDTO;
import com.blade.order.dto.OrderDeliveryVO;
import java.util.List;

public interface OrderDeliveryService {

    /**
     * 创建出库单
     */
    Long create(OrderDeliveryDTO dto);

    /**
     * 根据订单ID查询出库单列表
     */
    List<OrderDeliveryVO> getByOrderId(Long orderId);

    /**
     * 确认发货
     */
    void confirmDelivery(Long deliveryId);
}

package com.blade.order.service;

import com.blade.common.result.PageResult;
import com.blade.order.dto.OrderCreateDTO;
import com.blade.order.dto.OrderPageDTO;
import com.blade.order.dto.OrderUpdateDTO;
import com.blade.order.dto.OrderVO;

public interface OrderService {

    PageResult<OrderVO> pageList(OrderPageDTO dto);

    OrderVO getById(Long id);

    Long create(OrderCreateDTO dto);

    void update(OrderUpdateDTO dto);

    void updateStatus(Long id, Integer status);

    void delete(Long id);

    void addPayment(Long orderId, java.math.BigDecimal additionalAmount);
}

package com.blade.order.service;

import com.blade.common.result.PageResult;
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

    void updateStatus(Long id, Integer status);

    void delete(Long id);

    void addPayment(Long orderId, java.math.BigDecimal additionalAmount);

    /**
     * 导出订单列表（不分页，返回所有符合筛选条件的数据）
     * @param dto 筛选条件
     * @return 导出数据列表
     */
    List<OrderExportDTO> exportOrders(OrderPageDTO dto);
}

package com.blade.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.order.entity.OrderAdjustmentLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单调整记录 Mapper
 */
@Mapper
public interface OrderAdjustmentLogMapper extends BaseMapper<OrderAdjustmentLog> {
}

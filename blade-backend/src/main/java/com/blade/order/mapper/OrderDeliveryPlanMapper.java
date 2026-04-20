package com.blade.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.order.entity.OrderDeliveryPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单发货计划 Mapper
 */
@Mapper
public interface OrderDeliveryPlanMapper extends BaseMapper<OrderDeliveryPlan> {
}

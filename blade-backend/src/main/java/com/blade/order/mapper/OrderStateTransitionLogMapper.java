package com.blade.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.order.entity.OrderStateTransitionLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderStateTransitionLogMapper extends BaseMapper<OrderStateTransitionLog> {
}

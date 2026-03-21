package com.blade.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}

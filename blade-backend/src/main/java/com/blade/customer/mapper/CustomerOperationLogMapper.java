package com.blade.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.customer.entity.CustomerOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerOperationLogMapper extends BaseMapper<CustomerOperationLog> {
}

package com.blade.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.order.entity.OrderDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderDeliveryMapper extends BaseMapper<OrderDelivery> {

    /**
     * 取指定前缀（按天）出库单号的最大序号，用于 Redis 计数器初始化，避免 Redis 重启后单号回退。
     */
    @Select("""
            SELECT MAX(CAST(SUBSTRING(delivery_no, LENGTH(#{prefix}) + 1) AS UNSIGNED))
            FROM order_delivery
            WHERE tenant_id = #{tenantId}
              AND delivery_no LIKE CONCAT(#{prefix}, '%')
            """)
    Long selectMaxDeliveryNoSeq(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);
}

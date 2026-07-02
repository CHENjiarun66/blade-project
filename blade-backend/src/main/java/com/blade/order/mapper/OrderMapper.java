package com.blade.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    @Select("""
            SELECT MAX(order_no)
            FROM sale_order
            WHERE tenant_id = #{tenantId}
              AND deleted = 0
              AND order_no LIKE CONCAT(#{prefix}, '%')
            """)
    String selectMaxOrderNoByPrefix(@Param("tenantId") Long tenantId, @Param("prefix") String prefix);

    /**
     * Row-lock an order by id within the current tenant.
     * Used by the canonical shipment transaction to serialise concurrent
     * deliverOrder / confirmDelivery calls until commit.
     */
    @Select("SELECT * FROM sale_order WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0 FOR UPDATE")
    Order selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}

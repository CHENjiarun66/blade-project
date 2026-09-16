package com.blade.order.draft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.order.draft.entity.OrderDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderDraftMapper extends BaseMapper<OrderDraft> {
    @Select("SELECT * FROM order_draft WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    OrderDraft selectForUpdate(@Param("id") Long id);
}

package com.blade.outlet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.outlet.entity.SalesOutlet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SalesOutletMapper extends BaseMapper<SalesOutlet> {

    /** 清除当前租户除 selfId 外的全部默认标记（租户由拦截器约束）。 */
    @Update("UPDATE sales_outlet SET is_tenant_default = 0 WHERE is_tenant_default = 1 AND id <> #{selfId}")
    int clearOtherTenantDefaults(@Param("selfId") Long selfId);
}

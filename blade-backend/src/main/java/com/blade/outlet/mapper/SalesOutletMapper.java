package com.blade.outlet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.outlet.entity.SalesOutlet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SalesOutletMapper extends BaseMapper<SalesOutlet> {

    /**
     * 租户级串行锁：锁 sys_tenant 对应行（sys_tenant 无 tenant_id，由 ignore-tables 排除拦截器）。
     * 必须显式传 tenantId，避免依赖 TenantContext。
     */
    @Select("SELECT id FROM sys_tenant WHERE id = #{tenantId} FOR UPDATE")
    Long lockTenantRow(@Param("tenantId") Long tenantId);

    /** 清除该租户除 selfId 外的全部未删除默认标记；显式 tenant_id + deleted。 */
    @Update("UPDATE sales_outlet SET is_tenant_default = 0 "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND is_tenant_default = 1 AND id <> #{selfId}")
    int clearOtherTenantDefaults(@Param("tenantId") Long tenantId, @Param("selfId") Long selfId);

    /** 将目标档口标记为租户默认；仅允许启用且未删除，显式 tenant_id + deleted。 */
    @Update("UPDATE sales_outlet SET is_tenant_default = 1 "
            + "WHERE tenant_id = #{tenantId} AND id = #{selfId} AND deleted = 0 AND status = 1")
    int markTenantDefault(@Param("tenantId") Long tenantId, @Param("selfId") Long selfId);

    /** 启停并同步默认标记；显式 tenant_id + deleted，禁用默认档口即清除默认。 */
    @Update("UPDATE sales_outlet SET status = #{status}, is_tenant_default = #{isTenantDefault}, update_by = #{updateBy} "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    int updateStatusAndDefault(@Param("tenantId") Long tenantId, @Param("id") Long id,
                               @Param("status") Integer status,
                               @Param("isTenantDefault") Integer isTenantDefault,
                               @Param("updateBy") Long updateBy);
}

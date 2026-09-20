package com.blade.system.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.system.permission.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RolePermissionMapper extends BaseMapper<SysRolePermission> {

    /**
     * 在给定角色集合中返回授予指定权限编码（启用且未删）的角色ID。
     * 角色集合来自已按租户过滤的 sys_role，避免跨租户；单次查询，非 N+1。
     */
    @Select("""
            <script>
            SELECT DISTINCT rp.role_id
            FROM sys_role_permission rp
            JOIN sys_permission p ON p.id = rp.permission_id
            WHERE p.code = #{code}
              AND rp.deleted = 0 AND p.deleted = 0 AND p.status = 1
              AND rp.role_id IN
              <foreach collection="roleIds" item="rid" open="(" separator="," close=")">#{rid}</foreach>
            </script>
            """)
    List<Long> selectRoleIdsByPermissionCodeAndRoleIds(@Param("code") String code,
                                                       @Param("roleIds") List<Long> roleIds);

    /**
     * 根据角色ID删除角色权限关联
     */
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据权限ID删除权限角色关联
     */
    void deleteByPermissionId(@Param("permissionId") Long permissionId);

    /**
     * 批量插入角色权限关联
     */
    void batchInsert(@Param("list") java.util.List<SysRolePermission> list);
}

package com.blade.system.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.system.permission.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RolePermissionMapper extends BaseMapper<SysRolePermission> {

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

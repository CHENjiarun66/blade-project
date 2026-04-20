package com.blade.system.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.system.permission.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 根据角色ID列表查询所有权限编码
     */
    List<String> selectCodesByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 根据用户ID查询所有权限编码（查询用户所有角色的权限）
     */
    List<String> selectCodesByUserId(@Param("userId") Long userId);

    /**
     * 根据角色ID查询所有权限
     */
    List<SysPermission> selectByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据角色ID列表查询所有权限
     */
    List<SysPermission> selectByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 根据权限类型查询
     */
    List<SysPermission> selectByType(@Param("type") Integer type);

    /**
     * 查询用户的菜单权限（用于前端菜单渲染）
     */
    List<SysPermission> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的按钮权限
     */
    List<SysPermission> selectButtonsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的字段权限
     */
    List<SysPermission> selectFieldsByUserId(@Param("userId") Long userId);
}

package com.blade.system.permission.service;

import com.blade.common.result.PageResult;
import com.blade.system.permission.dto.PermissionCreateDTO;
import com.blade.system.permission.dto.PermissionUpdateDTO;
import com.blade.system.permission.dto.PermissionVO;
import com.blade.system.permission.dto.RolePermissionDTO;

import java.util.List;

public interface PermissionService {

    /**
     * 权限分页列表
     */
    PageResult<PermissionVO> pageList(int current, int size, Integer type, String module);

    /**
     * 获取权限详情
     */
    PermissionVO getById(Long id);

    /**
     * 创建权限
     */
    Long create(PermissionCreateDTO dto);

    /**
     * 更新权限
     */
    void update(PermissionUpdateDTO dto);

    /**
     * 删除权限
     */
    void delete(Long id);

    /**
     * 获取所有权限（树形结构）
     */
    List<PermissionVO> getAllTree();

    /**
     * 获取角色已分配的权限ID列表
     */
    List<Long> getPermissionIdsByRoleId(Long roleId);

    /**
     * 分配角色权限
     */
    void assignPermissions(RolePermissionDTO dto);

    /**
     * 判断用户是否有指定权限
     */
    boolean hasPermission(Long userId, String permissionCode);

    /**
     * 获取用户的所有权限编码
     */
    List<String> getUserPermissionCodes(Long userId);

    /**
     * 获取用户可见的菜单
     */
    List<PermissionVO> getVisibleMenus(Long userId);

    /**
     * 获取用户可见的按钮权限
     */
    List<String> getUserButtonPermissions(Long userId);

    /**
     * 获取用户可见的字段权限
     */
    List<PermissionVO> getUserFieldPermissions(Long userId);

    /**
     * 根据权限码列表获取字段权限信息
     */
    List<PermissionVO> getFieldPermissionsByCodes(List<String> codes);
}

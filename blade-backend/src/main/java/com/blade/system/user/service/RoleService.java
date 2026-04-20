package com.blade.system.user.service;

import com.blade.common.result.PageResult;
import com.blade.system.user.dto.RoleCreateDTO;
import com.blade.system.user.dto.RoleUpdateDTO;
import com.blade.system.user.dto.RoleVO;

import java.util.List;

public interface RoleService {

    /**
     * 角色分页列表
     */
    PageResult<RoleVO> pageList(int current, int size, String keyword);

    /**
     * 获取角色详情
     */
    RoleVO getById(Long id);

    /**
     * 创建角色
     */
    Long create(RoleCreateDTO dto);

    /**
     * 更新角色
     */
    void update(RoleUpdateDTO dto);

    /**
     * 删除角色
     */
    void delete(Long id);

    /**
     * 获取所有角色（下拉框用）
     */
    List<RoleVO> getAll();

    /**
     * 获取角色关联的菜单ID列表（用于前端回显）
     */
    List<Long> getMenuIdsByRoleId(Long roleId);

    /**
     * 获取角色关联的权限ID列表（用于前端回显）
     */
    List<Long> getPermissionIdsByRoleId(Long roleId);
}

package com.blade.system.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.system.permission.service.PermissionService;
import com.blade.system.user.dto.RoleCreateDTO;
import com.blade.system.user.dto.RoleUpdateDTO;
import com.blade.system.user.dto.RoleVO;
import com.blade.system.user.entity.Role;
import com.blade.system.user.mapper.RoleMapper;
import com.blade.system.user.service.RoleService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final PermissionService permissionService;

    @Autowired
    public RoleServiceImpl(RoleMapper roleMapper, PermissionService permissionService) {
        this.roleMapper = roleMapper;
        this.permissionService = permissionService;
    }

    @Override
    public PageResult<RoleVO> pageList(int current, int size, String keyword) {
        Page<Role> page = new Page<>(current, size);
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getDeleted, 0);

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Role::getRoleName, keyword)
                    .or().like(Role::getRoleCode, keyword));
        }
        wrapper.orderByDesc(Role::getId);

        IPage<Role> result = roleMapper.selectPage(page, wrapper);
        List<RoleVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult<RoleVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(result.getTotal());
        pageResult.setSize(result.getSize());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    @Override
    public RoleVO getById(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }
        RoleVO vo = convertToVO(role);
        // 填充权限ID列表
        List<Long> permissionIds = permissionService.getPermissionIdsByRoleId(id);
        vo.setPermissionIds(permissionIds);
        return vo;
    }

    @Override
    @Transactional
    public Long create(RoleCreateDTO dto) {
        // 检查编码唯一性
        LambdaQueryWrapper<Role> codeWrapper = new LambdaQueryWrapper<>();
        codeWrapper.eq(Role::getRoleCode, dto.getRoleCode())
                   .eq(Role::getDeleted, 0);
        if (roleMapper.selectCount(codeWrapper) > 0) {
            throw new RuntimeException("角色编码已存在");
        }

        Role role = new Role();
        role.setRoleName(dto.getRoleName());
        role.setRoleCode(dto.getRoleCode());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        role.setTenantId(tenantId);
        role.setDeleted(0);

        roleMapper.insert(role);

        // 分配权限
        if (dto.getPermissionIds() != null && dto.getPermissionIds().length > 0) {
            assignPermissions(role.getId(), Arrays.asList(dto.getPermissionIds()));
        }

        return role.getId();
    }

    @Override
    @Transactional
    public void update(RoleUpdateDTO dto) {
        Role role = roleMapper.selectById(dto.getId());
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }

        // 如果修改了编码，检查唯一性
        if (dto.getRoleCode() != null && !dto.getRoleCode().equals(role.getRoleCode())) {
            LambdaQueryWrapper<Role> codeWrapper = new LambdaQueryWrapper<>();
            codeWrapper.eq(Role::getRoleCode, dto.getRoleCode())
                       .eq(Role::getDeleted, 0)
                       .ne(Role::getId, dto.getId());
            if (roleMapper.selectCount(codeWrapper) > 0) {
                throw new RuntimeException("角色编码已存在");
            }
        }

        if (dto.getRoleName() != null) role.setRoleName(dto.getRoleName());
        if (dto.getRoleCode() != null) role.setRoleCode(dto.getRoleCode());
        if (dto.getDescription() != null) role.setDescription(dto.getDescription());
        if (dto.getStatus() != null) role.setStatus(dto.getStatus());

        roleMapper.updateById(role);

        // 更新权限
        if (dto.getPermissionIds() != null) {
            assignPermissions(dto.getId(), Arrays.asList(dto.getPermissionIds()));
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }
        Long activeUsers = roleMapper.countActiveUsersByRoleId(id);
        if (activeUsers != null && activeUsers > 0) {
            throw new RuntimeException("该角色已分配给 " + activeUsers + " 个用户，请先移除用户与角色的关联后再删除");
        }
        role.setDeleted(1);
        roleMapper.updateById(role);
    }

    @Override
    public List<RoleVO> getAll() {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getDeleted, 0)
               .eq(Role::getStatus, 1)
               .orderByDesc(Role::getId);
        List<Role> roles = roleMapper.selectList(wrapper);
        return roles.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        // 获取角色所有菜单权限
        List<Long> allPermissionIds = permissionService.getPermissionIdsByRoleId(roleId);
        // 这里只返回菜单类型的权限ID
        // 由于权限系统刚建立，先返回所有权限ID
        return allPermissionIds;
    }

    @Override
    public List<Long> getPermissionIdsByRoleId(Long roleId) {
        return permissionService.getPermissionIdsByRoleId(roleId);
    }

    private void assignPermissions(Long roleId, List<Long> permissionIds) {
        com.blade.system.permission.dto.RolePermissionDTO dto =
            new com.blade.system.permission.dto.RolePermissionDTO();
        dto.setRoleId(roleId);
        dto.setPermissionIds(permissionIds);
        permissionService.assignPermissions(dto);
    }

    private RoleVO convertToVO(Role role) {
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(role, vo);
        return vo;
    }
}

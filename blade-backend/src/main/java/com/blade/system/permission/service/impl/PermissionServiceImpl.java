package com.blade.system.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.system.permission.dto.PermissionCreateDTO;
import com.blade.system.permission.dto.PermissionUpdateDTO;
import com.blade.system.permission.dto.PermissionVO;
import com.blade.system.permission.dto.RolePermissionDTO;
import com.blade.system.permission.entity.SysPermission;
import com.blade.system.permission.entity.SysRolePermission;
import com.blade.system.permission.mapper.PermissionMapper;
import com.blade.system.permission.mapper.RolePermissionMapper;
import com.blade.system.permission.service.PermissionService;
import com.blade.system.user.mapper.RoleMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RoleMapper roleMapper;

    @Autowired
    public PermissionServiceImpl(PermissionMapper permissionMapper,
                                 RolePermissionMapper rolePermissionMapper,
                                 RoleMapper roleMapper) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public PageResult<PermissionVO> pageList(int current, int size, Integer type, String module) {
        Page<SysPermission> page = new Page<>(current, size);
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermission::getDeleted, 0);

        if (type != null) {
            wrapper.eq(SysPermission::getType, type);
        }
        if (module != null && !module.isEmpty()) {
            wrapper.eq(SysPermission::getModule, module);
        }
        wrapper.orderByAsc(SysPermission::getSort);

        IPage<SysPermission> result = permissionMapper.selectPage(page, wrapper);

        List<PermissionVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult<PermissionVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(result.getTotal());
        pageResult.setSize(result.getSize());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    @Override
    public PermissionVO getById(Long id) {
        SysPermission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new RuntimeException("权限不存在");
        }
        return convertToVO(permission);
    }

    @Override
    @Transactional
    public Long create(PermissionCreateDTO dto) {
        // 检查编码唯一性
        LambdaQueryWrapper<SysPermission> codeWrapper = new LambdaQueryWrapper<>();
        codeWrapper.eq(SysPermission::getCode, dto.getCode())
                   .eq(SysPermission::getDeleted, 0);
        if (permissionMapper.selectCount(codeWrapper) > 0) {
            throw new RuntimeException("权限编码已存在");
        }

        SysPermission permission = new SysPermission();
        BeanUtils.copyProperties(dto, permission);
        if (permission.getParentId() == null) {
            permission.setParentId(0L);
        }
        if (permission.getSort() == null) {
            permission.setSort(0);
        }
        if (permission.getStatus() == null) {
            permission.setStatus(1);
        }
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
        permission.setTenantId(tenantId);
        permission.setDeleted(0);

        permissionMapper.insert(permission);
        return permission.getId();
    }

    @Override
    @Transactional
    public void update(PermissionUpdateDTO dto) {
        SysPermission permission = permissionMapper.selectById(dto.getId());
        if (permission == null) {
            throw new RuntimeException("权限不存在");
        }

        // 如果修改了编码，检查唯一性
        if (dto.getCode() != null && !dto.getCode().equals(permission.getCode())) {
            LambdaQueryWrapper<SysPermission> codeWrapper = new LambdaQueryWrapper<>();
            codeWrapper.eq(SysPermission::getCode, dto.getCode())
                       .eq(SysPermission::getDeleted, 0)
                       .ne(SysPermission::getId, dto.getId());
            if (permissionMapper.selectCount(codeWrapper) > 0) {
                throw new RuntimeException("权限编码已存在");
            }
        }

        if (dto.getName() != null) permission.setName(dto.getName());
        if (dto.getCode() != null) permission.setCode(dto.getCode());
        if (dto.getType() != null) permission.setType(dto.getType());
        if (dto.getModule() != null) permission.setModule(dto.getModule());
        if (dto.getParentId() != null) permission.setParentId(dto.getParentId());
        if (dto.getPath() != null) permission.setPath(dto.getPath());
        if (dto.getMethod() != null) permission.setMethod(dto.getMethod());
        if (dto.getIcon() != null) permission.setIcon(dto.getIcon());
        if (dto.getSort() != null) permission.setSort(dto.getSort());
        if (dto.getStatus() != null) permission.setStatus(dto.getStatus());
        if (dto.getMaskType() != null) permission.setMaskType(dto.getMaskType());
        if (dto.getMaskValue() != null) permission.setMaskValue(dto.getMaskValue());
        if (dto.getDescription() != null) permission.setDescription(dto.getDescription());

        permissionMapper.updateById(permission);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysPermission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new RuntimeException("权限不存在");
        }
        // 存在子权限时禁止删除，避免权限树出现悬空子节点
        LambdaQueryWrapper<SysPermission> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(SysPermission::getParentId, id)
                    .eq(SysPermission::getDeleted, 0);
        Long childCount = permissionMapper.selectCount(childWrapper);
        if (childCount != null && childCount > 0) {
            throw new RuntimeException("该权限下存在 " + childCount + " 个子权限，请先删除或移动子权限");
        }
        permission.setDeleted(1);
        permissionMapper.updateById(permission);

        // 同时删除角色权限关联
        rolePermissionMapper.deleteByPermissionId(id);
    }

    @Override
    public List<PermissionVO> getAllTree() {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermission::getDeleted, 0)
               .orderByAsc(SysPermission::getSort, SysPermission::getId);
        List<SysPermission> all = permissionMapper.selectList(wrapper);

        // 构建树形结构
        List<PermissionVO> rootList = new ArrayList<>();
        Map<Long, List<SysPermission>> childrenMap = new HashMap<>();

        for (SysPermission p : all) {
            Long parentId = p.getParentId() == null ? 0L : p.getParentId();
            childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(p);
        }

        for (SysPermission p : all) {
            Long parentId = p.getParentId() == null ? 0L : p.getParentId();
            if (parentId == 0) {
                rootList.add(convertToVO(p));
            }
        }

        // 递归设置子节点
        for (PermissionVO vo : rootList) {
            setChildren(vo, childrenMap);
        }

        return rootList;
    }

    private void setChildren(PermissionVO parent, Map<Long, List<SysPermission>> childrenMap) {
        List<SysPermission> children = childrenMap.get(parent.getId());
        if (children != null && !children.isEmpty()) {
            List<PermissionVO> childVOList = children.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());
            parent.setChildren(childVOList);
            for (PermissionVO child : childVOList) {
                setChildren(child, childrenMap);
            }
        }
    }

    @Override
    public List<Long> getPermissionIdsByRoleId(Long roleId) {
        List<SysPermission> permissions = permissionMapper.selectByRoleId(roleId);
        return permissions.stream().map(SysPermission::getId).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void assignPermissions(RolePermissionDTO dto) {
        // 先删除该角色的所有权限关联
        rolePermissionMapper.deleteByRoleId(dto.getRoleId());

        // 插入新的权限关联
        if (dto.getPermissionIds() != null && !dto.getPermissionIds().isEmpty()) {
            Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;
            List<SysRolePermission> list = dto.getPermissionIds().stream()
                    .map(permissionId -> {
                        SysRolePermission rp = new SysRolePermission();
                        rp.setRoleId(dto.getRoleId());
                        rp.setPermissionId(permissionId);
                        rp.setTenantId(tenantId);
                        rp.setDeleted(0);
                        return rp;
                    })
                    .collect(Collectors.toList());

            rolePermissionMapper.batchInsert(list);
        }
    }

    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        List<String> codes = permissionMapper.selectCodesByUserId(userId);
        return codes.contains(permissionCode);
    }

    @Override
    public List<String> getUserPermissionCodes(Long userId) {
        return permissionMapper.selectCodesByUserId(userId);
    }

    @Override
    public List<PermissionVO> getVisibleMenus(Long userId) {
        List<SysPermission> menus = permissionMapper.selectMenusByUserId(userId);
        return buildMenuTree(menus);
    }

    private List<PermissionVO> buildMenuTree(List<SysPermission> allMenus) {
        Map<Long, List<SysPermission>> childrenMap = new HashMap<>();
        List<PermissionVO> roots = new ArrayList<>();

        for (SysPermission p : allMenus) {
            Long parentId = p.getParentId() == null ? 0L : p.getParentId();
            childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(p);
        }

        for (SysPermission p : allMenus) {
            Long parentId = p.getParentId() == null ? 0L : p.getParentId();
            if (parentId == 0) {
                roots.add(convertToVO(p));
            }
        }

        for (PermissionVO vo : roots) {
            buildChildren(vo, childrenMap);
        }

        return roots;
    }

    private void buildChildren(PermissionVO parent, Map<Long, List<SysPermission>> childrenMap) {
        List<SysPermission> children = childrenMap.get(parent.getId());
        if (children != null && !children.isEmpty()) {
            List<PermissionVO> childVOList = children.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());
            parent.setChildren(childVOList);
            for (PermissionVO child : childVOList) {
                buildChildren(child, childrenMap);
            }
        }
    }

    @Override
    public List<String> getUserButtonPermissions(Long userId) {
        List<SysPermission> buttons = permissionMapper.selectButtonsByUserId(userId);
        return buttons.stream().map(SysPermission::getCode).collect(Collectors.toList());
    }

    @Override
    public List<PermissionVO> getUserFieldPermissions(Long userId) {
        List<SysPermission> fields = permissionMapper.selectFieldsByUserId(userId);
        return fields.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<PermissionVO> getFieldPermissionsByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysPermission::getCode, codes)
                .eq(SysPermission::getType, 3)
                .eq(SysPermission::getDeleted, 0);
        List<SysPermission> fields = permissionMapper.selectList(wrapper);
        return fields.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    private PermissionVO convertToVO(SysPermission permission) {
        PermissionVO vo = new PermissionVO();
        BeanUtils.copyProperties(permission, vo);
        vo.setTypeName(getTypeName(permission.getType()));
        return vo;
    }

    private String getTypeName(Integer type) {
        if (type == null) return "";
        switch (type) {
            case 1: return "菜单";
            case 2: return "按钮";
            case 3: return "字段";
            case 4: return "API";
            default: return "未知";
        }
    }
}

package com.blade.system.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.entity.SysUserOutlet;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.system.permission.mapper.PermissionMapper;
import com.blade.system.user.dto.UserCreateDTO;
import com.blade.system.user.dto.UserPageDTO;
import com.blade.system.user.dto.UserUpdateDTO;
import com.blade.system.user.dto.UserVO;
import com.blade.system.user.entity.Role;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.RoleMapper;
import com.blade.system.user.mapper.UserMapper;
import com.blade.system.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private static final String SALES_ROLE_CODE = "ROLE_SALES";
    private static final String PERM_OUTLET_ALL = "data:outlet:all";
    private static final String PERM_PEOPLE_ALL = "data:order:peopleAll";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final SysUserOutletMapper sysUserOutletMapper;
    private final SalesOutletMapper salesOutletMapper;
    private final PermissionMapper permissionMapper;

    public UserServiceImpl(UserMapper userMapper, RoleMapper roleMapper, PasswordEncoder passwordEncoder,
                           SysUserOutletMapper sysUserOutletMapper, SalesOutletMapper salesOutletMapper,
                           PermissionMapper permissionMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.sysUserOutletMapper = sysUserOutletMapper;
        this.salesOutletMapper = salesOutletMapper;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public PageResult<UserVO> pageList(UserPageDTO dto) {
        Page<User> page = new Page<>(dto.getCurrent(), dto.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (dto.getUsername() != null && !dto.getUsername().isEmpty()) {
            wrapper.like(User::getUsername, dto.getUsername());
        }
        if (dto.getNickname() != null && !dto.getNickname().isEmpty()) {
            wrapper.like(User::getNickname, dto.getNickname());
        }
        if (dto.getKeyword() != null && !dto.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(User::getUsername, dto.getKeyword())
                    .or().like(User::getNickname, dto.getKeyword()));
        }
        if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            wrapper.eq(User::getPhone, dto.getPhone());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(User::getStatus, dto.getStatus());
        }

        wrapper.orderByDesc(User::getCreateTime);

        IPage<User> result = userMapper.selectPage(page, wrapper);
        List<UserVO> voList = result.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());
        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public UserVO getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return convertToVO(user);
    }

    @Override
    public User getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public List<Role> getRolesByUserId(Long userId) {
        return roleMapper.selectByUserId(userId);
    }

    @Override
    @Transactional
    public Long create(UserCreateDTO dto) {
        LambdaQueryWrapper<User> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(User::getUsername, dto.getUsername());
        if (userMapper.selectCount(checkWrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 先解析并校验角色（跨租户/不存在/禁用一律拒绝），再写任何关系。
        List<Role> roles = validateAndResolveRoles(dto.getRoleIds());

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setAvatar(dto.getAvatar());
        user.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        user.setTenantId(TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L);

        userMapper.insert(user);

        for (Role role : roles) {
            roleMapper.insertUserRole(user.getId(), role.getId(), user.getTenantId());
        }

        validateAndSaveOutletBindings(roles, user.getId(), user.getTenantId(),
                dto.getOutletIds(), dto.getDefaultOutletId());

        return user.getId();
    }

    @Override
    @Transactional
    public void update(UserUpdateDTO dto) {
        User user = userMapper.selectById(dto.getId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }

        userMapper.updateById(user);

        // 先解析并校验角色，再删除/重建关系。
        List<Role> resolvedRoles = null;
        if (dto.getRoleIds() != null) {
            resolvedRoles = validateAndResolveRoles(dto.getRoleIds());
            roleMapper.deleteUserRoles(dto.getId());
            for (Role role : resolvedRoles) {
                roleMapper.insertUserRole(dto.getId(), role.getId(), user.getTenantId());
            }
        }

        if (dto.getOutletIds() != null) {
            List<Role> roles = resolvedRoles != null
                    ? resolvedRoles
                    : roleMapper.selectByUserId(dto.getId());
            validateAndSaveOutletBindings(roles, dto.getId(), user.getTenantId(),
                    dto.getOutletIds(), dto.getDefaultOutletId());
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (id == 1L) {
            throw new RuntimeException("不能删除超级管理员");
        }
        userMapper.deleteById(id);
        roleMapper.deleteUserRoles(id);
        sysUserOutletMapper.deleteByUserId(id);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    // ==================== 角色解析与档口绑定 ====================

    /**
     * 规范化并校验 roleIds：去重、数量一致、角色启用且未删（同租户由租户拦截器保证）。
     * 任一项失败抛 404/400，调用方不得写入任何角色/档口关系。
     */
    private List<Role> validateAndResolveRoles(Long[] roleIds) {
        if (roleIds == null || roleIds.length == 0) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(Arrays.asList(roleIds));
        List<Role> roles = roleMapper.selectBatchIds(new ArrayList<>(unique));
        if (roles == null || roles.size() != unique.size()) {
            throw BusinessException.of(404, "角色不存在或不属于当前租户");
        }
        for (Role role : roles) {
            if (role.getId() == null || !Integer.valueOf(1).equals(role.getStatus())) {
                throw BusinessException.of(400, "角色不可用");
            }
        }
        return roles;
    }

    private Set<String> permissionCodesForRoles(List<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> roleIds = roles.stream().map(Role::getId).filter(id -> id != null).toList();
        if (roleIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<String> codes = permissionMapper.selectCodesByRoleIds(roleIds);
        return codes == null ? Collections.emptySet() : Set.copyOf(codes);
    }

    private void validateAndSaveOutletBindings(List<Role> roles, Long userId, Long tenantId,
                                               Long[] outletIds, Long defaultOutletId) {
        Set<String> codes = permissionCodesForRoles(roles);
        boolean hasOutletAll = codes.contains(PERM_OUTLET_ALL);
        boolean isSales = roles.stream().anyMatch(r -> SALES_ROLE_CODE.equals(r.getRoleCode()));

        if (defaultOutletId != null && !contains(outletIds, defaultOutletId)) {
            throw BusinessException.of(400, "默认档口必须属于已选档口");
        }

        boolean empty = outletIds == null || outletIds.length == 0;
        if (empty) {
            if (isSales && !hasOutletAll) {
                throw BusinessException.of(400, "销售员至少绑定一个档口");
            }
            sysUserOutletMapper.deleteByUserId(userId);
            return;
        }

        for (Long outletId : outletIds) {
            SalesOutlet outlet = salesOutletMapper.selectById(outletId);
            if (outlet == null) {
                throw BusinessException.of(404, "档口不存在");
            }
            if (!Integer.valueOf(1).equals(outlet.getStatus())) {
                throw BusinessException.of(400, "档口未启用");
            }
        }

        sysUserOutletMapper.deleteByUserId(userId);
        for (Long outletId : outletIds) {
            SysUserOutlet binding = new SysUserOutlet();
            binding.setTenantId(tenantId);
            binding.setUserId(userId);
            binding.setOutletId(outletId);
            binding.setIsDefault(outletId.equals(defaultOutletId) ? 1 : 0);
            binding.setStatus(1);
            binding.setDeleted(0);
            sysUserOutletMapper.insert(binding);
        }
    }

    private static boolean contains(Long[] arr, Long value) {
        if (arr == null) {
            return false;
        }
        return Arrays.asList(arr).contains(value);
    }

    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());

        List<Role> roles = roleMapper.selectByUserId(user.getId());
        if (roles != null && !roles.isEmpty()) {
            List<UserVO.RoleVO> roleVOList = new ArrayList<>();
            for (Role role : roles) {
                UserVO.RoleVO roleVO = new UserVO.RoleVO();
                roleVO.setId(role.getId());
                roleVO.setRoleName(role.getRoleName());
                roleVO.setRoleCode(role.getRoleCode());
                roleVOList.add(roleVO);
            }
            vo.setRoles(roleVOList);
        }

        List<Long> outletIds = sysUserOutletMapper.selectOutletIdsByUserId(user.getId());
        Long defaultOutletId = sysUserOutletMapper.selectDefaultOutletIdByUserId(user.getId());
        vo.setOutletIds(outletIds == null ? Collections.emptyList() : outletIds);
        vo.setDefaultOutletId(defaultOutletId);

        // 二维权限独立计算：档口范围由 data:outlet:all，人员范围由 data:order:peopleAll。
        Set<String> codes = permissionCodesForRoles(roles);
        vo.setOutletScope(codes.contains(PERM_OUTLET_ALL)
                ? "ALL" : (vo.getOutletIds().isEmpty() ? "NONE" : "ASSIGNED"));
        vo.setPeopleScope(codes.contains(PERM_PEOPLE_ALL) ? "ALL_USERS" : "SELF");

        return vo;
    }
}

package com.blade.system.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.system.user.dto.UserCreateDTO;
import com.blade.system.user.dto.UserPageDTO;
import com.blade.system.user.dto.UserUpdateDTO;
import com.blade.system.user.dto.UserVO;
import com.blade.system.user.entity.Role;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.RoleMapper;
import com.blade.system.user.mapper.UserMapper;
import com.blade.system.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(UserMapper userMapper, RoleMapper roleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
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

        if (dto.getRoleIds() != null && dto.getRoleIds().length > 0) {
            for (Long roleId : dto.getRoleIds()) {
                roleMapper.insertUserRole(user.getId(), roleId);
            }
        }

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

        if (dto.getRoleIds() != null) {
            roleMapper.deleteUserRoles(dto.getId());
            for (Long roleId : dto.getRoleIds()) {
                roleMapper.insertUserRole(dto.getId(), roleId);
            }
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

        return vo;
    }
}

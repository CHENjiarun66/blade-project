package com.blade.outlet;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.entity.SysUserOutlet;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.system.permission.mapper.PermissionMapper;
import com.blade.system.user.dto.UserCreateDTO;
import com.blade.system.user.dto.UserUpdateDTO;
import com.blade.system.user.dto.UserVO;
import com.blade.system.user.entity.Role;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.RoleMapper;
import com.blade.system.user.mapper.UserMapper;
import com.blade.system.user.service.UserService;
import com.blade.system.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserOutletBindingTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SysUserOutletMapper sysUserOutletMapper = mock(SysUserOutletMapper.class);
    private final SalesOutletMapper salesOutletMapper = mock(SalesOutletMapper.class);
    private final PermissionMapper permissionMapper = mock(PermissionMapper.class);
    private final UserService service = new UserServiceImpl(
            userMapper, roleMapper, passwordEncoder, sysUserOutletMapper, salesOutletMapper, permissionMapper);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(7L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            inv.getArgument(0, User.class).setId(50L);
            return 1;
        });
        when(userMapper.selectById(any())).thenAnswer(inv -> {
            User u = new User();
            u.setId(inv.getArgument(0));
            u.setTenantId(7L);
            return u;
        });
        when(permissionMapper.selectCodesByRoleIds(anyList())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Role role(String code) {
        Role r = new Role();
        r.setId(1L);
        r.setRoleCode(code);
        r.setStatus(1);
        return r;
    }

    private UserCreateDTO createDto(Long[] roleIds, Long[] outletIds, Long defaultOutletId) {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("u1");
        dto.setPassword("123456");
        dto.setRoleIds(roleIds);
        dto.setOutletIds(outletIds);
        dto.setDefaultOutletId(defaultOutletId);
        return dto;
    }

    @Test
    void salesUserWithoutOutletAllPermissionRequiresAtLeastOneOutlet() {
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of(role("ROLE_SALES")));
        assertThrows(BusinessException.class, () -> service.create(createDto(new Long[]{1L}, null, null)));
        verify(sysUserOutletMapper, never()).insert(any(SysUserOutlet.class));
    }

    @Test
    void roleWithOutletAllPermissionCanBeUnboundEvenWithoutBinding() {
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of(role("ROLE_SALES")));
        when(permissionMapper.selectCodesByRoleIds(anyList())).thenReturn(List.of("data:outlet:all"));
        service.create(createDto(new Long[]{1L}, null, null));
        verify(sysUserOutletMapper, never()).insert(any(SysUserOutlet.class));
    }

    @Test
    void defaultOutletMustBelongToSelectedOutlets() {
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of(role("ROLE_SALES")));
        SalesOutlet outlet = new SalesOutlet();
        outlet.setId(11L);
        outlet.setStatus(1);
        when(salesOutletMapper.selectById(11L)).thenReturn(outlet);

        assertThrows(BusinessException.class,
                () -> service.create(createDto(new Long[]{1L}, new Long[]{11L}, 999L)));
    }

    @Test
    void crossTenantOutletIsRejectedAsNotFound() {
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of(role("ROLE_SALES")));
        when(salesOutletMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.create(createDto(new Long[]{1L}, new Long[]{12L}, 12L)));
    }

    @Test
    void crossTenantRoleRejectedBeforeAnyRoleWrite() {
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of()); // 跨租户/不存在：查不到
        assertThrows(BusinessException.class, () -> service.create(createDto(new Long[]{99L}, null, null)));
        verify(roleMapper, never()).insertUserRole(any(), any(), any());
        verify(roleMapper, never()).deleteUserRoles(any());
        verify(sysUserOutletMapper, never()).insert(any(SysUserOutlet.class));
    }

    @Test
    void disabledRoleRejected() {
        Role disabled = role("ROLE_SALES");
        disabled.setStatus(0);
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of(disabled));
        assertThrows(BusinessException.class, () -> service.create(createDto(new Long[]{1L}, null, null)));
        verify(roleMapper, never()).insertUserRole(any(), any(), any());
    }

    @Test
    void duplicateRoleIdsAreNormalized() {
        Role r = role("ROLE_OWNER");
        r.setId(9L);
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of(r));
        when(permissionMapper.selectCodesByRoleIds(anyList())).thenReturn(List.of("data:outlet:all"));
        service.create(createDto(new Long[]{9L, 9L}, null, null));
        // 只插入一次角色关系（去重）
        verify(roleMapper, times(1)).insertUserRole(any(), any(), any());
    }

    @Test
    void updateWithoutOutletFieldDoesNotClearExistingBindings() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setId(50L);
        dto.setNickname("改昵称");
        dto.setOutletIds(null);

        service.update(dto);

        verify(sysUserOutletMapper, never()).deleteByUserId(any());
        verify(sysUserOutletMapper, never()).insert(any(SysUserOutlet.class));
    }

    @Test
    void updateWithBindingsSavesAndPreservesDefaultFlag() {
        when(roleMapper.selectByUserId(50L)).thenReturn(List.of(role("ROLE_SALES")));
        SalesOutlet a = new SalesOutlet();
        a.setId(11L); a.setStatus(1);
        SalesOutlet b = new SalesOutlet();
        b.setId(12L); b.setStatus(1);
        when(salesOutletMapper.selectById(11L)).thenReturn(a);
        when(salesOutletMapper.selectById(12L)).thenReturn(b);

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setId(50L);
        dto.setOutletIds(new Long[]{11L, 12L});
        dto.setDefaultOutletId(12L);

        service.update(dto);

        ArgumentCaptor<SysUserOutlet> captor = ArgumentCaptor.forClass(SysUserOutlet.class);
        verify(sysUserOutletMapper, times(2)).insert(captor.capture());
        List<SysUserOutlet> saved = captor.getAllValues();
        assertEquals(7L, saved.get(0).getTenantId());
        assertEquals(11L, saved.get(0).getOutletId());
        assertEquals(0, saved.get(0).getIsDefault());
        assertEquals(12L, saved.get(1).getOutletId());
        assertEquals(1, saved.get(1).getIsDefault());
    }

    @Test
    void onlyPeopleAllScopeYieldsAssignedAndAllUsers() {
        Role r = role("ROLE_LEADER");
        r.setId(5L);
        when(roleMapper.selectByUserId(50L)).thenReturn(List.of(r));
        when(permissionMapper.selectCodesByRoleIds(anyList())).thenReturn(List.of("data:order:peopleAll"));
        when(sysUserOutletMapper.selectOutletIdsByUserId(50L)).thenReturn(List.of(11L));
        when(sysUserOutletMapper.selectDefaultOutletIdByUserId(50L)).thenReturn(null);

        UserVO vo = service.getById(50L);

        assertEquals("ASSIGNED", vo.getOutletScope());
        assertEquals("ALL_USERS", vo.getPeopleScope());
    }

    @Test
    void onlyOutletAllScopeYieldsAllAndSelf() {
        Role r = role("ROLE_X");
        r.setId(6L);
        when(roleMapper.selectByUserId(50L)).thenReturn(List.of(r));
        when(permissionMapper.selectCodesByRoleIds(anyList())).thenReturn(List.of("data:outlet:all"));
        when(sysUserOutletMapper.selectOutletIdsByUserId(50L)).thenReturn(List.of());
        when(sysUserOutletMapper.selectDefaultOutletIdByUserId(50L)).thenReturn(null);

        UserVO vo = service.getById(50L);

        assertEquals("ALL", vo.getOutletScope());
        assertEquals("SELF", vo.getPeopleScope());
    }
}

package com.blade.outlet;

import com.blade.system.permission.mapper.RolePermissionMapper;
import com.blade.system.permission.service.PermissionService;
import com.blade.system.user.dto.RoleVO;
import com.blade.system.user.entity.Role;
import com.blade.system.user.mapper.RoleMapper;
import com.blade.system.user.service.impl.RoleServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色列表 data:outlet:all 契约：一次性计算每角色是否授予全部档口。
 */
class RoleOutletScopeContractTest {

    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final RolePermissionMapper rolePermissionMapper = mock(RolePermissionMapper.class);
    private final RoleServiceImpl service =
            new RoleServiceImpl(roleMapper, permissionService, rolePermissionMapper);

    private Role role(long id, String code) {
        Role r = new Role();
        r.setId(id);
        r.setRoleCode(code);
        r.setStatus(1);
        r.setDeleted(0);
        return r;
    }

    @Test
    void getAllFlagsRolesGrantingOutletAllInSingleQuery() {
        Role owner = role(1L, "ROLE_OWNER");
        Role sales = role(4L, "ROLE_SALES");
        Role custom = role(9L, "ROLE_CUSTOM");
        when(roleMapper.selectList(any())).thenReturn(List.of(owner, sales, custom));
        when(rolePermissionMapper.selectRoleIdsByPermissionCodeAndRoleIds(eq("data:outlet:all"), anyList()))
                .thenReturn(List.of(1L, 9L));

        List<RoleVO> result = service.getAll();
        Map<Long, Boolean> flags = result.stream()
                .collect(Collectors.toMap(RoleVO::getId, RoleVO::getGrantsOutletAll));

        assertTrue(flags.get(1L), "ROLE_OWNER 应授予 data:outlet:all");
        assertFalse(flags.get(4L), "ROLE_SALES 不应授予 data:outlet:all");
        assertTrue(flags.get(9L), "自定义授权角色应授予 data:outlet:all");

        // 单次查询（非 N+1）
        verify(rolePermissionMapper, times(1))
                .selectRoleIdsByPermissionCodeAndRoleIds(eq("data:outlet:all"), anyList());
    }
}

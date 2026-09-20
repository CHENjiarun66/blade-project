package com.blade.outlet;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.outlet.dto.OutletCreateDTO;
import com.blade.outlet.dto.OutletUpdateDTO;
import com.blade.outlet.dto.OutletOptionsVO;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.service.OutletService;
import com.blade.outlet.service.impl.OutletServiceImpl;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutletServiceImplTest {

    private final SalesOutletMapper outletMapper = mock(SalesOutletMapper.class);
    private final SysUserOutletMapper sysUserOutletMapper = mock(SysUserOutletMapper.class);
    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderDraftMapper orderDraftMapper = mock(OrderDraftMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final OutletAccessPolicy outletAccessPolicy = mock(OutletAccessPolicy.class);
    private final OutletService service = new OutletServiceImpl(
            outletMapper, sysUserOutletMapper, orderMapper, orderDraftMapper, userMapper, outletAccessPolicy);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(7L);
        User user = new User();
        user.setId(23L);
        user.setUsername("owner");
        when(userMapper.selectByUsername("owner")).thenReturn(user);
        when(outletMapper.selectCount(any())).thenReturn(0L);
        when(outletMapper.insert(any(SalesOutlet.class))).thenAnswer(inv -> {
            inv.getArgument(0, SalesOutlet.class).setId(101L);
            return 1;
        });
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderDraftMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authAs(String... authorities) {
        List<SimpleGrantedAuthority> list = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner", "n/a", list));
    }

    private SalesOutlet outlet(long id, String code) {
        SalesOutlet o = new SalesOutlet();
        o.setId(id);
        o.setOutletCode(code);
        o.setStatus(1);
        return o;
    }

    @Test
    void createPersistsOutletForCurrentTenantAndRejectsDuplicateCode() {
        authAs("btn:outlet:create");
        OutletCreateDTO dto = new OutletCreateDTO();
        dto.setOutletCode("YL");
        dto.setOutletName("御龙");

        Long id = service.create(dto);

        ArgumentCaptor<SalesOutlet> captor = ArgumentCaptor.forClass(SalesOutlet.class);
        verify(outletMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getTenantId());
        assertEquals(101L, id);
        assertEquals(1, captor.getValue().getStatus());

        when(outletMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.create(dto));
    }

    @Test
    void settingTenantDefaultBatchClearsOtherDefaults() {
        authAs("btn:outlet:create");
        OutletCreateDTO dto = new OutletCreateDTO();
        dto.setOutletCode("NEW");
        dto.setOutletName("新档口");
        dto.setIsTenantDefault(1);

        service.create(dto);

        // 批量清除其它默认（不再 selectOne 单条）
        verify(outletMapper).clearOtherTenantDefaults(101L);
    }

    @Test
    void disablingDefaultOutletClearsDefaultFlag() {
        authAs("btn:outlet:disable");
        SalesOutlet outlet = outlet(9L, "YL");
        outlet.setIsTenantDefault(1);
        when(outletMapper.selectById(9L)).thenReturn(outlet);

        service.updateStatus(9L, 0);

        assertEquals(0, outlet.getStatus());
        assertEquals(0, outlet.getIsTenantDefault());
        verify(outletMapper).updateById(outlet);
    }

    @Test
    void updateRejectsChangingOutletCode() {
        authAs("btn:outlet:edit");
        when(outletMapper.selectById(9L)).thenReturn(outlet(9L, "YL"));

        OutletUpdateDTO dto = new OutletUpdateDTO();
        dto.setId(9L);
        dto.setOutletCode("Y2");
        dto.setOutletName("御龙");

        assertThrows(BusinessException.class, () -> service.update(dto));
        verify(outletMapper, never()).updateById(any(SalesOutlet.class));
    }

    @Test
    void updateWithNullIsTenantDefaultPreservesExistingValue() {
        authAs("btn:outlet:edit");
        SalesOutlet outlet = outlet(9L, "YL");
        outlet.setIsTenantDefault(1);
        when(outletMapper.selectById(9L)).thenReturn(outlet);

        OutletUpdateDTO dto = new OutletUpdateDTO();
        dto.setId(9L);
        dto.setOutletCode("YL");
        dto.setOutletName("御龙");
        dto.setIsTenantDefault(null);

        service.update(dto);

        assertEquals(1, outlet.getIsTenantDefault());
        verify(outletMapper).updateById(outlet);
    }

    @Test
    void optionsDelegatesToAccessPolicy() {
        // 选项裁剪由 OutletAccessPolicy 统一负责
        OutletOptionsVO vo = new OutletOptionsVO("ASSIGNED", "SELF", true, 1L, List.of());
        when(outletAccessPolicy.listAvailableOptions()).thenReturn(vo);

        assertSame(vo, service.options());
        verify(outletAccessPolicy).listAvailableOptions();
    }

    @Test
    void getByIdReturns404ForMissingOrCrossTenant() {
        authAs("menu:outlet");
        when(outletMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getById(999L));
    }
}

package com.blade.outlet;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.outlet.dto.OutletCreateDTO;
import com.blade.outlet.dto.OutletOptionVO;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
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
    private final OutletService service = new OutletServiceImpl(
            outletMapper, sysUserOutletMapper, orderMapper, orderDraftMapper, userMapper);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(7L);
        User user = new User();
        user.setId(23L);
        user.setUsername("owner");
        when(userMapper.selectByUsername("owner")).thenReturn(user);
        when(outletMapper.selectCount(any())).thenReturn(0L);
        when(outletMapper.selectOne(any())).thenReturn(null);
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
    void settingTenantDefaultClearsExistingDefault() {
        authAs("btn:outlet:create");
        SalesOutlet existingDefault = new SalesOutlet();
        existingDefault.setId(5L);
        existingDefault.setIsTenantDefault(1);
        when(outletMapper.selectOne(any())).thenReturn(existingDefault);

        OutletCreateDTO dto = new OutletCreateDTO();
        dto.setOutletCode("NEW");
        dto.setOutletName("新档口");
        dto.setIsTenantDefault(1);

        service.create(dto);

        verify(outletMapper).updateById(existingDefault);
        assertEquals(0, existingDefault.getIsTenantDefault());
    }

    @Test
    void disablingDefaultOutletClearsDefaultFlag() {
        authAs("btn:outlet:disable");
        SalesOutlet outlet = new SalesOutlet();
        outlet.setId(9L);
        outlet.setIsTenantDefault(1);
        outlet.setStatus(1);
        when(outletMapper.selectById(9L)).thenReturn(outlet);

        service.updateStatus(9L, 0);

        assertEquals(0, outlet.getStatus());
        assertEquals(0, outlet.getIsTenantDefault());
        verify(outletMapper).updateById(outlet);
    }

    @Test
    void optionsReturnsAllEnabledWhenAllAuthorityPresent() {
        authAs("data:outlet:all");
        SalesOutlet a = new SalesOutlet();
        a.setId(1L); a.setOutletCode("A"); a.setOutletName("甲"); a.setStatus(1);
        SalesOutlet b = new SalesOutlet();
        b.setId(2L); b.setOutletCode("B"); b.setOutletName("乙"); b.setStatus(1);
        when(outletMapper.selectList(any())).thenReturn(List.of(a, b));

        List<OutletOptionVO> options = service.options();

        assertEquals(2, options.size());
        verify(sysUserOutletMapper, never()).selectOutletIdsByUserId(any());
    }

    @Test
    void optionsReturnsOnlyBoundOutletsAndEmptyWhenNoBinding() {
        authAs("menu:outlet");
        SalesOutlet a = new SalesOutlet();
        a.setId(1L); a.setOutletCode("A"); a.setOutletName("甲"); a.setStatus(1);
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of(1L));
        when(outletMapper.selectList(any())).thenReturn(List.of(a));

        List<OutletOptionVO> options = service.options();
        assertEquals(1, options.size());
        assertEquals("A", options.get(0).getOutletCode());

        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of());
        assertTrue(service.options().isEmpty());
    }

    @Test
    void getByIdReturns404ForMissingOrCrossTenant() {
        authAs("menu:outlet");
        when(outletMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getById(999L));
    }
}

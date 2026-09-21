package com.blade.order;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderAdjustmentLog;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderAccessPolicy;
import com.blade.order.service.OrderPlaceholderSplitService;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-2 审计整改：占位拆分必须在锁单后、读取/删除/插入明细前先 requireAccess。
 * 真实 {@link OrderAccessPolicy} + {@link OutletAccessPolicy}（mock 数据层），NONE 范围。
 */
class OrderPlaceholderSplitScopeGuardTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderItemMapper orderItemMapper = mock(OrderItemMapper.class);
    private final OrderAdjustmentLogMapper adjustmentLogMapper = mock(OrderAdjustmentLogMapper.class);
    private final SalesOutletMapper salesOutletMapper = mock(SalesOutletMapper.class);
    private final SysUserOutletMapper sysUserOutletMapper = mock(SysUserOutletMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    private OrderPlaceholderSplitService splitService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        User principal = new User();
        principal.setId(23L);
        principal.setUsername("warehouse");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("btn:order:allocate"))));

        OutletAccessPolicy outletPolicy = new OutletAccessPolicy(salesOutletMapper, sysUserOutletMapper,
                mock(com.blade.agent.mapper.AgentKeyMapper.class), mock(AgentKeyOutletMapper.class), userMapper);
        when(salesOutletMapper.selectList(any())).thenReturn(List.of());
        when(sysUserOutletMapper.selectOutletIdsByUserId(23L)).thenReturn(List.of());
        OrderAccessPolicy accessPolicy = new OrderAccessPolicy(userMapper, outletPolicy);

        splitService = new OrderPlaceholderSplitService(orderMapper, orderItemMapper, adjustmentLogMapper,
                mock(ProductSkuMapper.class), mock(ProductMapper.class), mock(ProductColorMapper.class),
                mock(ProductSizeMapper.class), accessPolicy);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void noneScopeCannotSplitOtherOutletOrderAndNoWriteHappens() {
        Order order = new Order();
        order.setId(5L);
        order.setTenantId(1L);
        order.setSourceOutletId(99L); // B 档口
        order.setFulfillmentStatus(FulfillmentStatus.WAITING_ALLOCATION.name());
        when(orderMapper.selectByIdForUpdate(5L, 1L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> splitService.splitPlaceholderItem(5L, 900L, List.of(target()), "越权拆分"));

        assertEquals(403, ex.getCode());
        verify(orderItemMapper, never()).selectOne(any());
        verify(orderItemMapper, never()).delete(any());
        verify(adjustmentLogMapper, never()).insert(any(OrderAdjustmentLog.class));
    }

    private OrderPlaceholderSplitService.SplitTarget target() {
        OrderPlaceholderSplitService.SplitTarget target = new OrderPlaceholderSplitService.SplitTarget();
        target.setSkuId(1L);
        target.setQuantity(1);
        return target;
    }
}

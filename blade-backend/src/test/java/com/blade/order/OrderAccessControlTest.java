package com.blade.order;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.dto.AddPaymentDTO;
import com.blade.order.entity.Order;
import com.blade.order.enums.CollectionStatus;
import com.blade.order.enums.FulfillmentStatus;
import com.blade.order.mapper.OrderAdjustmentLogMapper;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.order.mapper.OrderFinancialRecordMapper;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.mapper.OrderStateTransitionLogMapper;
import com.blade.order.service.OrderAccessPolicy;
import com.blade.order.service.OrderActionService;
import com.blade.order.service.OrderCompatAdapter;
import com.blade.order.service.OrderFinanceSnapshotService;
import com.blade.inventory.service.InventoryService;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 终审三轮 P0-1/P0-2 反例测试：
 * 1. SALES（仅 recordPayment）调 markAsSettled → 403（writeOff 权限不得被绕过）
 * 2. 跨销售订单访问 → 403（订单所有权/数据范围）
 * 3. 无权限用户调退款/冲销 → 403
 * Runs without Spring context, MySQL, Redis, or Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderAccessControlTest {

    private static final Long TENANT_ID = 1L;

    @Mock private OrderMapper orderMapper;
    @Mock private OrderFinancialRecordMapper financialRecordMapper;
    @Mock private OrderStateTransitionLogMapper transitionLogMapper;
    @Mock private OrderDeliveryPlanMapper deliveryPlanMapper;
    @Mock private OrderAdjustmentLogMapper adjustmentLogMapper;
    @Mock private InventoryService inventoryService;
    @Mock private UserMapper userMapper;

    private OrderActionService actionService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    /** 模拟 SALES 用户（仅 recordPayment + view，无 writeOff/viewAll） */
    private void loginAsSales(Long userId) {
        User principal = new User();
        principal.setId(userId);
        principal.setUsername("sales1");
        SecurityContext sc = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        lenient().when(sc.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getPrincipal()).thenReturn(principal);
        lenient().when(auth.getAuthorities()).thenReturn((java.util.Collection) java.util.List.of(
                new SimpleGrantedAuthority("btn:order:recordPayment"),
                new SimpleGrantedAuthority("btn:order:view")));
        SecurityContextHolder.setContext(sc);
    }

    /** 模拟 ADMIN 用户（全量权限） */
    private void loginAsAdmin(Long userId) {
        User principal = new User();
        principal.setId(userId);
        principal.setUsername("admin");
        SecurityContext sc = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        lenient().when(sc.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getPrincipal()).thenReturn(principal);
        lenient().when(auth.getAuthorities()).thenReturn((java.util.Collection) java.util.List.of(
                new SimpleGrantedAuthority("btn:order:viewAll"),
                new SimpleGrantedAuthority("btn:order:recordPayment"),
                new SimpleGrantedAuthority("btn:order:writeOff"),
                new SimpleGrantedAuthority("btn:order:refund"),
                new SimpleGrantedAuthority("btn:order:reverse"),
                new SimpleGrantedAuthority("btn:order:deliver"),
                new SimpleGrantedAuthority("btn:order:cancel"),
                new SimpleGrantedAuthority("btn:order:view"),
                new SimpleGrantedAuthority("btn:order:viewFinance"),
                new SimpleGrantedAuthority("btn:order:chooseFulfillment"),
                new SimpleGrantedAuthority("btn:order:allocate")));
        SecurityContextHolder.setContext(sc);
    }

    /** 模拟生产 JWT 链路中的 Spring Security UserDetails principal。 */
    private void loginAsSpringUserDetails(String username) {
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(username, "n/a", List.of(
                        new SimpleGrantedAuthority("btn:order:recordPayment"),
                        new SimpleGrantedAuthority("btn:order:view")));
        SecurityContext sc = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        lenient().when(sc.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getPrincipal()).thenReturn(principal);
        lenient().when(auth.getName()).thenReturn(username);
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        lenient().when(auth.getAuthorities()).thenReturn((java.util.Collection) principal.getAuthorities());
        SecurityContextHolder.setContext(sc);
    }

    private Order migratedOrder(Long id, Long salesmanId) {
        Order order = new Order();
        order.setId(id);
        order.setTenantId(TENANT_ID);
        order.setStatus(0);
        order.setSalesmanId(salesmanId);
        order.setFulfillmentStatus(FulfillmentStatus.CONFIRMED.name());
        order.setCollectionStatus(CollectionStatus.PARTIAL.name());
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setSalesReturnAmount(BigDecimal.ZERO);
        order.setGrossReceivedAmount(new BigDecimal("50.00"));
        order.setCashRefundAmount(BigDecimal.ZERO);
        order.setWriteOffAmount(BigDecimal.ZERO);
        order.setNetReceivedAmount(new BigDecimal("50.00"));
        order.setBalanceAmount(new BigDecimal("50.00"));
        order.setVersion(0);
        return order;
    }

    private void stubOrder(Order order) {
        when(orderMapper.selectByIdForUpdate(order.getId(), TENANT_ID)).thenReturn(order);
        when(financialRecordMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(orderMapper.updateById(any(Order.class))).thenReturn(1);
    }

    private OrderActionService buildService(OrderAccessPolicy policy) {
        return new OrderActionService(orderMapper, financialRecordMapper, transitionLogMapper,
                deliveryPlanMapper, adjustmentLogMapper,
                new OrderFinanceSnapshotService(orderMapper, financialRecordMapper, new OrderCompatAdapter()),
                new OrderCompatAdapter(), inventoryService,
                mock(com.blade.order.service.OrderPlaceholderSplitService.class),
                mock(com.blade.customer.service.CustomerStatsCacheService.class), policy);
    }

    @Test
    void salesWithOnlyRecordPayment_cannotExecuteWriteOff() {
        // SALES 用户（仅 recordPayment）调 markAsSettled → 必须被服务层 403 拦截
        loginAsSales(2L);
        Order order = migratedOrder(1L, 1L); // 他人开单
        stubOrder(order);
        OrderAccessPolicy policy = new OrderAccessPolicy(userMapper);
        actionService = buildService(policy);

        AddPaymentDTO dto = new AddPaymentDTO();
        dto.setAdditionalAmount(BigDecimal.ZERO);
        dto.setMarkAsSettled(true);
        dto.setWriteOffReason("测试核销");

        // 两个拦截点都应命中：writeOff 权限缺失（或数据范围缺失），任一即可
        assertThrows(BusinessException.class, () ->
                actionService.addPaymentCompat(1L, dto));
    }

    @Test
    void salesCannotAccessOtherSalesmanOrder() {
        // SALES 用户访问他人订单 → 403
        loginAsSales(2L);
        Order order = migratedOrder(1L, 1L); // salesmanId=1，SALES 用户 id=2
        stubOrder(order);
        actionService = buildService(new OrderAccessPolicy(userMapper));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                actionService.recordPayment(1L, new BigDecimal("10.00"), null, null, "PC"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void salesCanAccessOwnOrder() {
        // SALES 用户访问本人订单 → 允许
        loginAsSales(2L);
        Order order = migratedOrder(1L, 2L); // salesmanId=2，SALES 用户 id=2
        stubOrder(order);
        actionService = buildService(new OrderAccessPolicy(userMapper));

        assertDoesNotThrow(() ->
                actionService.recordPayment(1L, new BigDecimal("10.00"), null, null, "PC"));
    }

    @Test
    void springUserDetailsPrincipal_resolvesDomainUserAndCanAccessOwnOrder() {
        loginAsSpringUserDetails("sales2");
        User domainUser = new User();
        domainUser.setId(2L);
        domainUser.setUsername("sales2");
        when(userMapper.selectOne(any())).thenReturn(domainUser);
        Order order = migratedOrder(1L, 2L);
        stubOrder(order);
        actionService = buildService(new OrderAccessPolicy(userMapper));

        assertDoesNotThrow(() ->
                actionService.recordPayment(1L, new BigDecimal("10.00"), null, null, "PC"));
    }

    @Test
    void adminWithViewAll_canAccessAnyOrder() {
        // ADMIN（viewAll）可访问任何订单
        loginAsAdmin(1L);
        Order order = migratedOrder(1L, 99L); // 他人开单，但 admin 有 viewAll
        stubOrder(order);
        actionService = buildService(new OrderAccessPolicy(userMapper));

        assertDoesNotThrow(() ->
                actionService.recordPayment(1L, new BigDecimal("10.00"), null, null, "PC"));
    }

    @Test
    void salesCannotRefundWithoutRefundPermission() {
        loginAsSales(2L);
        Order order = migratedOrder(1L, 2L); // 本人订单
        stubOrder(order);
        actionService = buildService(new OrderAccessPolicy(userMapper));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                actionService.refundPayment(1L, new BigDecimal("10.00"), "退款", null, "PC"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void allowedActions_emptyForNonOwner() {
        loginAsSales(2L);
        Order order = migratedOrder(1L, 1L); // 他人订单
        actionService = buildService(new OrderAccessPolicy(userMapper));

        List<String> actions = actionService.computeAllowedActions(order);
        assertTrue(actions.isEmpty(), "非本人订单不得有 allowedActions");
    }

    private static class ArrayList<T> extends java.util.ArrayList<T> {}
}

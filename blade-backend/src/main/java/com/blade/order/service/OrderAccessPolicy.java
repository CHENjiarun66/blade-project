package com.blade.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.common.exception.BusinessException;
import com.blade.order.entity.Order;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 订单访问策略（Series C）：档口范围 × 人员范围二维判定，唯一入口。
 *
 * <p>{@code btn:order:viewAll} 仅保留兼容语义，不再独立绕过档口/人员范围。
 * {@code agent:orders:read} 仍用于端点授权，数据范围由 Agent 档口 scope 决定。</p>
 */
@Service
public class OrderAccessPolicy {

    public static final String AUTH_AGENT_ORDERS_READ = "agent:orders:read";
    public static final String AUTH_VIEW_ALL_COMPAT = "btn:order:viewAll";

    private final UserMapper userMapper;
    private final OutletAccessPolicy outletAccessPolicy;

    public OrderAccessPolicy(UserMapper userMapper, OutletAccessPolicy outletAccessPolicy) {
        this.userMapper = userMapper;
        this.outletAccessPolicy = outletAccessPolicy;
    }

    /** 列表 SQL 谓词：在分页之前应用档口维度 + 人员维度（租户由调用方/拦截器保证）。 */
    public void applyReadPredicate(LambdaQueryWrapper<Order> wrapper) {
        applyScopePredicate(wrapper, outletAccessPolicy.resolveCurrentScope());
    }

    private void applyScopePredicate(LambdaQueryWrapper<Order> wrapper, OutletAccessScope scope) {
        if (scope.isNone()) {
            wrapper.apply("1 = 0");
            return;
        }
        if (scope.isAssigned()) {
            if (scope.readableOutletIds().isEmpty()) {
                wrapper.apply("1 = 0");
                return;
            }
            wrapper.in(Order::getSourceOutletId, scope.readableOutletIds());
        }
        // ALL 省略 IN，但仍受人员维度与待归档约束
        if (!scope.unassignedAllowed()) {
            wrapper.isNotNull(Order::getSourceOutletId);
        }
        if (!scope.isPeopleAll()) {
            Long actorId = scope.actorId();
            wrapper.eq(Order::getSalesmanId, actorId != null ? actorId : -1L);
        }
    }

    /** 详情/动作/配货/发货/文件等统一走此判定；不可访问抛 403。 */
    public void requireAccess(Order order) {
        if (order == null) {
            throw BusinessException.of(404, "订单不存在");
        }
        OutletAccessScope scope = outletAccessPolicy.resolveCurrentScope();

        if (order.getTenantId() != null && scope.tenantId() != null
                && !order.getTenantId().equals(scope.tenantId())
                && !Long.valueOf(0L).equals(scope.tenantId())) {
            throw BusinessException.of(403, "无权访问该订单");
        }

        if (order.getSourceOutletId() == null) {
            if (!scope.unassignedAllowed()) {
                throw BusinessException.of(403, "无权访问该订单");
            }
        } else if (!scope.canReadOutlet(order.getSourceOutletId())) {
            throw BusinessException.of(403, "无权访问该订单");
        }

        if (!scope.isPeopleAll()) {
            Long actorId = scope.actorId();
            if (actorId == null || order.getSalesmanId() == null
                    || !order.getSalesmanId().equals(actorId)) {
                throw BusinessException.of(403, "无权访问该订单");
            }
        }
    }

    public boolean canAccess(Order order) {
        try {
            requireAccess(order);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    public Long currentUserId() {
        User user = currentUser();
        return user != null ? user.getId() : null;
    }

    public User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof User user) {
            return user;
        }
        if (!authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }
}

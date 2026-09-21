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
    public static final String AUTH_ORDER_CHANGE_OUTLET = "btn:order:changeOutlet";

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
        // 档口维度：与详情/动作完全一致的过滤形态
        switch (scope.outletFilter()) {
            case NO_FILTER -> { }
            case NOT_NULL -> wrapper.isNotNull(Order::getSourceOutletId);
            case IN -> wrapper.in(Order::getSourceOutletId, scope.readableOutletIds());
            case IN_OR_NULL -> wrapper.and(w -> w.in(Order::getSourceOutletId, scope.readableOutletIds())
                    .or().isNull(Order::getSourceOutletId));
            case ONLY_NULL -> wrapper.isNull(Order::getSourceOutletId);
            case DENY -> wrapper.apply("1 = 0");
        }
        // 人员维度独立叠加
        if (!scope.isPeopleAll()) {
            Long actorId = scope.actorId();
            wrapper.eq(Order::getSalesmanId, actorId != null ? actorId : -1L);
        }
    }

    /**
     * 订单列表显式档口筛选：只允许当前 readable 集合内；待归档仅 unassigned 可用。
     * 伪造/越权 ID 直接 403，不静默返回空结果，便于识别越权探测。
     */
    public void applyExplicitOutletFilter(LambdaQueryWrapper<Order> wrapper,
                                          Long sourceOutletId,
                                          Boolean unassignedOnly) {
        boolean pendingArchive = Boolean.TRUE.equals(unassignedOnly);
        if (sourceOutletId != null && pendingArchive) {
            throw BusinessException.of(400, "档口筛选与待归档筛选互斥");
        }
        OutletAccessScope scope = outletAccessPolicy.resolveCurrentScope();
        if (sourceOutletId != null) {
            if (!scope.canReadOutlet(sourceOutletId)) {
                throw BusinessException.of(403, "无权按该档口筛选");
            }
            wrapper.eq(Order::getSourceOutletId, sourceOutletId);
        } else if (pendingArchive) {
            if (!scope.unassignedAllowed()) {
                throw BusinessException.of(403, "无权查看待归档档口数据");
            }
            wrapper.isNull(Order::getSourceOutletId);
        }
    }

    /** 修改正式订单档口必须拥有独立高权限 btn:order:changeOutlet。 */
    public void requireChangeOutletPermission() {
        if (!currentAuthorities().contains(AUTH_ORDER_CHANGE_OUTLET)) {
            throw BusinessException.of(403, "缺少修改订单档口权限");
        }
    }

    /** 历史 NULL 档口归档需要 data:outlet:unassigned。 */
    public void requireUnassignedAccess() {
        if (!outletAccessPolicy.resolveCurrentScope().unassignedAllowed()) {
            throw BusinessException.of(403, "无权归档待归档档口数据");
        }
    }

    /** 详情/动作/配货/发货/文件等统一走此判定；不可访问抛 403。 */
    public void requireAccess(Order order) {
        if (order == null) {
            throw BusinessException.of(404, "订单不存在");
        }
        OutletAccessScope scope = outletAccessPolicy.resolveCurrentScope();

        // 严格租户隔离：实体或范围租户为空/不相等一律 fail closed（不存在仍 404）
        if (order.getTenantId() == null || scope.tenantId() == null
                || !order.getTenantId().equals(scope.tenantId())) {
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

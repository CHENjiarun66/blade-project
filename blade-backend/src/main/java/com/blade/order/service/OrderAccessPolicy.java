package com.blade.order.service;

import com.blade.common.exception.BusinessException;
import com.blade.order.entity.Order;
import com.blade.system.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 订单访问策略（终审第二轮 P0-2）：订单所有权与数据范围的唯一判定入口。
 * <ul>
 *   <li>持有 {@code btn:order:viewAll}（老板/管理员/财务等）可访问本租户全部订单</li>
 *   <li>其他用户（如销售）只能访问本人开单（salesman_id = 当前用户）的订单</li>
 *   <li>查询、动作与 allowedActions 共用本策略，前端隐藏不作为权限控制</li>
 * </ul>
 * 空租户在动作服务入口已显式拒绝，本服务不重复处理。
 */
@Service
public class OrderAccessPolicy {

    public static final String AUTH_VIEW_ALL = "btn:order:viewAll";

    /**
     * 当前用户是否可访问指定订单。不可访问抛 403。
     */
    public void requireAccess(Order order) {
        Long userId = currentUserId();
        Set<String> authorities = currentAuthorities();
        if (authorities.contains(AUTH_VIEW_ALL)) {
            return;
        }
        // 无 viewAll：仅本人开单
        if (userId != null && order.getSalesmanId() != null && order.getSalesmanId().equals(userId)) {
            return;
        }
        throw BusinessException.of(403, "无权访问该订单");
    }

    /**
     * 查询/allowedActions 用：可访问返回 true（不抛异常）。
     */
    public boolean canAccess(Order order) {
        try {
            requireAccess(order);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    /**
     * 当前用户是否可查看全租户订单（查询过滤用）。
     */
    public boolean hasViewAllScope() {
        return currentAuthorities().contains(AUTH_VIEW_ALL);
    }

    public Long currentUserId() {
        User user = currentUser();
        return user != null ? user.getId() : null;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof User user ? user : null;
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

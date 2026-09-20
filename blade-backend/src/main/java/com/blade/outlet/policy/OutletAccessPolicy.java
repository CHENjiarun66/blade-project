package com.blade.outlet.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.outlet.dto.OutletOptionVO;
import com.blade.outlet.dto.OutletOptionsVO;
import com.blade.outlet.entity.AgentKeyOutlet;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 档口与人员数据范围的唯一判定入口（JWT 用户或 Agent Key）。
 * 读写分离：readable 含禁用档口历史，usable 仅启用档口；NONE 一律 fail-closed。
 */
@Service
public class OutletAccessPolicy {

    public static final String AUTH_OUTLET_ALL = "data:outlet:all";
    public static final String AUTH_PEOPLE_ALL = "data:order:peopleAll";
    public static final String AUTH_OUTLET_UNASSIGNED = "data:outlet:unassigned";

    private final SalesOutletMapper salesOutletMapper;
    private final SysUserOutletMapper sysUserOutletMapper;
    private final AgentKeyMapper agentKeyMapper;
    private final AgentKeyOutletMapper agentKeyOutletMapper;
    private final UserMapper userMapper;

    public OutletAccessPolicy(SalesOutletMapper salesOutletMapper,
                              SysUserOutletMapper sysUserOutletMapper,
                              AgentKeyMapper agentKeyMapper,
                              AgentKeyOutletMapper agentKeyOutletMapper,
                              UserMapper userMapper) {
        this.salesOutletMapper = salesOutletMapper;
        this.sysUserOutletMapper = sysUserOutletMapper;
        this.agentKeyMapper = agentKeyMapper;
        this.agentKeyOutletMapper = agentKeyOutletMapper;
        this.userMapper = userMapper;
    }

    /** 解析当前调用主体（用户或 Agent）的不可变范围快照。 */
    public OutletAccessScope resolveCurrentScope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return emptyScope(TenantContext.getTenantId(), OutletAccessScope.ActorType.USER, null);
        }
        if (auth.getPrincipal() instanceof AgentPrincipal agent) {
            return resolveAgentScope(agent);
        }
        return resolveUserScope(auth);
    }

    public List<Long> allowedOutletIds() {
        return resolveCurrentScope().readableOutletIds();
    }

    public boolean canAccessOutlet(Long outletId) {
        return resolveCurrentScope().canReadOutlet(outletId);
    }

    public void requireAccessOutlet(Long outletId) {
        if (!canAccessOutlet(outletId)) {
            throw BusinessException.of(403, "无权访问该档口数据");
        }
    }

    public boolean canUseOutlet(Long outletId) {
        return resolveCurrentScope().canUseOutlet(outletId);
    }

    public void requireUseOutlet(Long outletId) {
        if (!canUseOutlet(outletId)) {
            throw BusinessException.of(403, "无权使用该档口");
        }
    }

    public Long resolveDefaultOutletId() {
        return resolveCurrentScope().defaultOutletId();
    }

    /** 结构化选项契约。 */
    public OutletOptionsVO listAvailableOptions() {
        OutletAccessScope scope = resolveCurrentScope();
        List<OutletOptionVO> items = new ArrayList<>();
        if (!scope.usableOutletIds().isEmpty()) {
            List<SalesOutlet> outlets = salesOutletMapper.selectList(
                    new LambdaQueryWrapper<SalesOutlet>()
                            .in(SalesOutlet::getId, scope.usableOutletIds())
                            .eq(SalesOutlet::getStatus, 1)
                            .orderByAsc(SalesOutlet::getSort)
                            .orderByAsc(SalesOutlet::getId));
            for (SalesOutlet o : outlets) {
                items.add(new OutletOptionVO(o.getId(), o.getOutletCode(), o.getOutletName(), o.getStatus()));
            }
        }
        return new OutletOptionsVO(scope.outletScopeType(), scope.peopleScopeType(),
                scope.locked(), scope.defaultOutletId(), items);
    }

    // ==================== 用户 ====================

    private OutletAccessScope resolveUserScope(Authentication auth) {
        Set<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        boolean allAuthority = authorities.contains(AUTH_OUTLET_ALL);
        boolean peopleAll = authorities.contains(AUTH_PEOPLE_ALL);
        boolean unassigned = authorities.contains(AUTH_OUTLET_UNASSIGNED);

        Long tenantId = TenantContext.getTenantId();
        User user = currentUser(auth);
        Long userId = user != null ? user.getId() : null;

        List<SalesOutlet> allOutlets = salesOutletMapper.selectList(null);
        List<Long> enabledIds = allOutlets.stream()
                .filter(o -> Integer.valueOf(1).equals(o.getStatus()))
                .map(SalesOutlet::getId)
                .toList();

        if (allAuthority) {
            List<Long> allIds = allOutlets.stream().map(SalesOutlet::getId).toList();
            Long def = resolveDefault(enabledIds, tenantDefaultId(enabledIds), null);
            return new OutletAccessScope(tenantId, OutletAccessScope.ActorType.USER, userId,
                    OutletAccessScope.ALL, peopleAll, unassigned, allIds, enabledIds, def);
        }

        if (userId == null) {
            return emptyScope(tenantId, OutletAccessScope.ActorType.USER, null);
        }
        List<Long> boundIds = sysUserOutletMapper.selectOutletIdsByUserId(userId);
        List<Long> readable = boundIds == null ? List.of() : boundIds;
        if (readable.isEmpty()) {
            return emptyScope(tenantId, OutletAccessScope.ActorType.USER, userId);
        }
        List<Long> usable = readable.stream().filter(enabledIds::contains).toList();
        Long personalDefault = sysUserOutletMapper.selectDefaultOutletIdByUserId(userId);
        Long def = resolveDefault(usable, tenantDefaultId(usable), personalDefault);
        return new OutletAccessScope(tenantId, OutletAccessScope.ActorType.USER, userId,
                OutletAccessScope.ASSIGNED, peopleAll, unassigned, readable, usable, def);
    }

    // ==================== Agent ====================

    private OutletAccessScope resolveAgentScope(AgentPrincipal agent) {
        Long tenantId = agent.getTenantId() != null ? agent.getTenantId() : TenantContext.getTenantId();
        AgentKey key = agentKeyMapper.selectById(agent.getKeyId());
        String scopeType = key != null && key.getOutletScopeType() != null
                ? key.getOutletScopeType() : OutletAccessScope.NONE;

        List<SalesOutlet> allOutlets = salesOutletMapper.selectList(null);
        List<Long> enabledIds = allOutlets.stream()
                .filter(o -> Integer.valueOf(1).equals(o.getStatus()))
                .map(SalesOutlet::getId)
                .toList();

        if (OutletAccessScope.ALL.equals(scopeType)) {
            List<Long> allIds = allOutlets.stream().map(SalesOutlet::getId).toList();
            Long def = resolveDefault(enabledIds, tenantDefaultId(enabledIds), null);
            return new OutletAccessScope(tenantId, OutletAccessScope.ActorType.AGENT, agent.getKeyId(),
                    OutletAccessScope.ALL, true, false, allIds, enabledIds, def);
        }
        if (!OutletAccessScope.ASSIGNED.equals(scopeType)) {
            return emptyScope(tenantId, OutletAccessScope.ActorType.AGENT, agent.getKeyId());
        }
        List<Long> boundIds = agentKeyOutletMapper.selectOutletIdsByKeyId(agent.getKeyId());
        List<Long> readable = boundIds == null ? List.of() : boundIds;
        if (readable.isEmpty()) {
            return emptyScope(tenantId, OutletAccessScope.ActorType.AGENT, agent.getKeyId());
        }
        List<Long> usable = readable.stream().filter(enabledIds::contains).toList();
        Long keyDefault = agentKeyOutletMapper.selectDefaultOutletIdByKeyId(agent.getKeyId());
        Long def = resolveDefault(usable, tenantDefaultId(usable), keyDefault);
        return new OutletAccessScope(tenantId, OutletAccessScope.ActorType.AGENT, agent.getKeyId(),
                OutletAccessScope.ASSIGNED, true, false, readable, usable, def);
    }

    // ==================== 草稿 ====================

    /** 草稿读取 SQL 谓词：档口维度 + 人员维度（手工草稿按 created_by_user_id）。 */
    public void applyDraftReadScope(LambdaQueryWrapper<OrderDraft> query) {
        OutletAccessScope scope = resolveCurrentScope();
        if (scope.isNone()) {
            query.apply("1 = 0");
            return;
        }
        if (scope.isAssigned()) {
            if (scope.readableOutletIds().isEmpty()) {
                query.apply("1 = 0");
                return;
            }
            query.in(OrderDraft::getSourceOutletId, scope.readableOutletIds());
        }
        if (!scope.unassignedAllowed()) {
            query.isNotNull(OrderDraft::getSourceOutletId);
        }
        if (!scope.isPeopleAll()) {
            Long actorId = scope.actorId();
            query.eq(OrderDraft::getCreatedByUserId, actorId != null ? actorId : -1L);
        }
    }

    public boolean canAccessDraft(OrderDraft draft) {
        try {
            requireDraftAccess(draft);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    public void requireDraftAccess(OrderDraft draft) {
        if (draft == null) {
            throw BusinessException.of(404, "草稿不存在");
        }
        OutletAccessScope scope = resolveCurrentScope();
        if (draft.getTenantId() != null && scope.tenantId() != null
                && !draft.getTenantId().equals(scope.tenantId())
                && !Long.valueOf(0L).equals(scope.tenantId())) {
            throw BusinessException.of(403, "无权访问该草稿");
        }
        if (draft.getSourceOutletId() == null) {
            if (!scope.unassignedAllowed()) {
                throw BusinessException.of(403, "无权访问该草稿");
            }
        } else if (!scope.canReadOutlet(draft.getSourceOutletId())) {
            throw BusinessException.of(403, "无权访问该草稿");
        }
        if (!scope.isPeopleAll()) {
            Long actorId = scope.actorId();
            if (actorId == null || draft.getCreatedByUserId() == null
                    || !draft.getCreatedByUserId().equals(actorId)) {
                throw BusinessException.of(403, "无权访问该草稿");
            }
        }
    }

    // ==================== 辅助 ====================


    private OutletAccessScope emptyScope(Long tenantId, OutletAccessScope.ActorType type, Long actorId) {
        return new OutletAccessScope(tenantId, type, actorId,
                OutletAccessScope.NONE, false, false, List.of(), List.of(), null);
    }

    private Long tenantDefaultId(List<Long> usable) {
        if (usable.isEmpty()) {
            return null;
        }
        SalesOutlet def = salesOutletMapper.selectOne(new LambdaQueryWrapper<SalesOutlet>()
                .eq(SalesOutlet::getIsTenantDefault, 1)
                .eq(SalesOutlet::getStatus, 1)
                .last("LIMIT 1"));
        return def != null && usable.contains(def.getId()) ? def.getId() : null;
    }

    private Long resolveDefault(List<Long> usable, Long tenantDefault, Long personalDefault) {
        if (usable.isEmpty()) {
            return null;
        }
        if (personalDefault != null && usable.contains(personalDefault)) {
            return personalDefault;
        }
        if (tenantDefault != null && usable.contains(tenantDefault)) {
            return tenantDefault;
        }
        if (usable.size() == 1) {
            return usable.get(0);
        }
        return null;
    }

    private User currentUser(Authentication auth) {
        if (auth.getPrincipal() instanceof User user) {
            return user;
        }
        String username = auth.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return null;
        }
        return userMapper.selectByUsername(username);
    }
}

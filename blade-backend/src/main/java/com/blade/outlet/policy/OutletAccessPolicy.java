package com.blade.outlet.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.mapper.AgentKeyMapper;
import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.outlet.dto.OutletOptionVO;
import com.blade.outlet.dto.OutletOptionsVO;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.mapper.AgentKeyOutletMapper;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
            return emptyScope(TenantContext.getTenantId(), OutletAccessScope.ActorType.USER,
                    null, false, false);
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

    /**
     * 解析并校验“可用于新写”的档口主数据（按 ID）：
     * 当前范围 canUseOutlet + 本租户 + 未删除 + 启用。writer/OrderService 统一复用。
     */
    public com.blade.outlet.entity.SalesOutlet requireUsableOutlet(Long outletId) {
        OutletAccessScope scope = resolveCurrentScope();
        if (outletId == null || !scope.canUseOutlet(outletId)) {
            throw BusinessException.of(403, "无权使用该档口");
        }
        com.blade.outlet.entity.SalesOutlet outlet = salesOutletMapper.selectById(outletId);
        if (outlet == null || !Integer.valueOf(0).equals(outlet.getDeleted())
                || !Integer.valueOf(1).equals(outlet.getStatus())
                || scope.tenantId() == null || !scope.tenantId().equals(outlet.getTenantId())) {
            throw BusinessException.of(400, "档口不存在或未启用");
        }
        return outlet;
    }

    /**
     * 解析并校验“可用于新写”的档口主数据（按稳定编码，Agent）：
     * 仅当前可用集合内按编码解析，越权/禁用/不存在一律 403。
     */
    public com.blade.outlet.entity.SalesOutlet requireUsableOutletByCode(String outletCode) {
        OutletAccessScope scope = resolveCurrentScope();
        String code = outletCode == null ? null : outletCode.trim();
        if (code == null || code.isEmpty() || scope.usableOutletIds().isEmpty() || scope.tenantId() == null) {
            throw BusinessException.of(403, "无权使用该档口");
        }
        com.blade.outlet.entity.SalesOutlet outlet = salesOutletMapper.selectOne(
                new LambdaQueryWrapper<com.blade.outlet.entity.SalesOutlet>()
                        .eq(com.blade.outlet.entity.SalesOutlet::getOutletCode, code)
                        .eq(com.blade.outlet.entity.SalesOutlet::getTenantId, scope.tenantId())
                        .in(com.blade.outlet.entity.SalesOutlet::getId, scope.usableOutletIds())
                        .eq(com.blade.outlet.entity.SalesOutlet::getDeleted, 0)
                        .eq(com.blade.outlet.entity.SalesOutlet::getStatus, 1)
                        .last("LIMIT 1"));
        if (outlet == null) {
            throw BusinessException.of(403, "无权使用该档口");
        }
        return outlet;
    }

    public com.blade.outlet.entity.SalesOutlet findOutlet(Long outletId) {
        return outletId == null ? null : salesOutletMapper.selectById(outletId);
    }

    /**
     * 草稿列表显式档口筛选：只允许当前 readable 集合内；待归档仅 unassigned 可用。
     * 伪造/越权 ID 直接 403，不静默返回空结果，便于识别越权探测。
     */
    public void applyExplicitDraftOutletFilter(LambdaQueryWrapper<OrderDraft> query,
                                               Long sourceOutletId,
                                               Boolean unassignedOnly) {
        boolean pendingArchive = Boolean.TRUE.equals(unassignedOnly);
        if (sourceOutletId != null && pendingArchive) {
            throw BusinessException.of(400, "档口筛选与待归档筛选互斥");
        }
        OutletAccessScope scope = resolveCurrentScope();
        if (sourceOutletId != null) {
            if (!scope.canReadOutlet(sourceOutletId)) {
                throw BusinessException.of(403, "无权按该档口筛选");
            }
            query.eq(OrderDraft::getSourceOutletId, sourceOutletId);
        } else if (pendingArchive) {
            if (!scope.unassignedAllowed()) {
                throw BusinessException.of(403, "无权查看待归档档口数据");
            }
            query.isNull(OrderDraft::getSourceOutletId);
        }
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

        List<SalesOutlet> allOutlets = loadTenantOutlets(tenantId);
        List<Long> allIds = allOutlets.stream().map(SalesOutlet::getId).toList();
        List<Long> enabledIds = allOutlets.stream()
                .filter(o -> Integer.valueOf(1).equals(o.getStatus()))
                .map(SalesOutlet::getId)
                .toList();

        if (allAuthority) {
            Long personalDefault = userId == null ? null
                    : sysUserOutletMapper.selectDefaultOutletIdByUserId(userId);
            Long def = resolveDefault(enabledIds, tenantDefaultId(enabledIds), personalDefault);
            return new OutletAccessScope(tenantId, OutletAccessScope.ActorType.USER, userId,
                    OutletAccessScope.ALL, peopleAll, unassigned, allIds, enabledIds, def);
        }

        if (userId == null) {
            return emptyScope(tenantId, OutletAccessScope.ActorType.USER, null, peopleAll, unassigned);
        }
        List<Long> boundIds = sysUserOutletMapper.selectOutletIdsByUserId(userId);
        // 绑定必须与本租户真实未删档口取交集，剔除伪造/跨租户/已删除 ID
        List<Long> readable = boundIds == null ? List.of()
                : boundIds.stream().filter(allIds::contains).distinct().toList();
        if (readable.isEmpty()) {
            return emptyScope(tenantId, OutletAccessScope.ActorType.USER, userId, peopleAll, unassigned);
        }
        List<Long> usable = readable.stream().filter(enabledIds::contains).toList();
        Long personalDefault = sysUserOutletMapper.selectDefaultOutletIdByUserId(userId);
        Long def = resolveDefault(usable, tenantDefaultId(usable), personalDefault);
        return new OutletAccessScope(tenantId, OutletAccessScope.ActorType.USER, userId,
                OutletAccessScope.ASSIGNED, peopleAll, unassigned, readable, usable, def);
    }

    // ==================== Agent ====================

    private OutletAccessScope resolveAgentScope(AgentPrincipal agent) {
        Long principalTenant = agent.getTenantId();
        Long contextTenant = TenantContext.getTenantId();
        AgentKey key = agentKeyMapper.selectById(agent.getKeyId());
        boolean invalid = principalTenant == null || contextTenant == null
                || key == null || key.getTenantId() == null
                || !Integer.valueOf(AgentKey.STATUS_ACTIVE).equals(key.getStatus())
                || (key.getExpiresTime() != null && key.getExpiresTime().isBefore(LocalDateTime.now()))
                || !principalTenant.equals(contextTenant)
                || !principalTenant.equals(key.getTenantId());
        if (invalid) {
            // 不存在/停用/过期/租户不一致：fail-closed，不得产生可访问范围
            return emptyScope(contextTenant, OutletAccessScope.ActorType.AGENT, agent.getKeyId(), true, false);
        }
        Long tenantId = principalTenant;
        String scopeType = key.getOutletScopeType() != null
                ? key.getOutletScopeType() : OutletAccessScope.NONE;

        List<SalesOutlet> allOutlets = loadTenantOutlets(tenantId);
        List<Long> allIds = allOutlets.stream().map(SalesOutlet::getId).toList();
        List<Long> enabledIds = allOutlets.stream()
                .filter(o -> Integer.valueOf(1).equals(o.getStatus()))
                .map(SalesOutlet::getId)
                .toList();

        if (OutletAccessScope.ALL.equals(scopeType)) {
            Long keyDefault = agentKeyOutletMapper.selectDefaultOutletIdByKeyId(agent.getKeyId());
            Long def = resolveDefault(enabledIds, tenantDefaultId(enabledIds), keyDefault);
            return new OutletAccessScope(tenantId, OutletAccessScope.ActorType.AGENT, agent.getKeyId(),
                    OutletAccessScope.ALL, true, false, allIds, enabledIds, def);
        }
        if (!OutletAccessScope.ASSIGNED.equals(scopeType)) {
            return emptyScope(tenantId, OutletAccessScope.ActorType.AGENT, agent.getKeyId(), true, false);
        }
        List<Long> boundIds = agentKeyOutletMapper.selectOutletIdsByKeyId(agent.getKeyId());
        List<Long> readable = boundIds == null ? List.of()
                : boundIds.stream().filter(allIds::contains).distinct().toList();
        if (readable.isEmpty()) {
            return emptyScope(tenantId, OutletAccessScope.ActorType.AGENT, agent.getKeyId(), true, false);
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
        applyDraftOutletFilter(query, scope);
        if (!scope.isPeopleAll()) {
            Long actorId = scope.actorId();
            query.eq(OrderDraft::getCreatedByUserId, actorId != null ? actorId : -1L);
        }
    }

    private void applyDraftOutletFilter(LambdaQueryWrapper<OrderDraft> query, OutletAccessScope scope) {
        switch (scope.outletFilter()) {
            case NO_FILTER -> { }
            case NOT_NULL -> query.isNotNull(OrderDraft::getSourceOutletId);
            case IN -> query.in(OrderDraft::getSourceOutletId, scope.readableOutletIds());
            case IN_OR_NULL -> query.and(w -> w.in(OrderDraft::getSourceOutletId, scope.readableOutletIds())
                    .or().isNull(OrderDraft::getSourceOutletId));
            case ONLY_NULL -> query.isNull(OrderDraft::getSourceOutletId);
            case DENY -> query.apply("1 = 0");
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
        // 严格租户隔离：实体或范围租户为空/不相等一律 fail closed
        if (draft.getTenantId() == null || scope.tenantId() == null
                || !draft.getTenantId().equals(scope.tenantId())) {
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

    private List<SalesOutlet> loadTenantOutlets(Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        // 显式租户 + 未删除，即使租户拦截器存在也写清条件
        return salesOutletMapper.selectList(new LambdaQueryWrapper<SalesOutlet>()
                .eq(SalesOutlet::getTenantId, tenantId)
                .eq(SalesOutlet::getDeleted, 0));
    }

    private OutletAccessScope emptyScope(Long tenantId, OutletAccessScope.ActorType type, Long actorId,
                                         boolean peopleAll, boolean unassigned) {
        return new OutletAccessScope(tenantId, type, actorId,
                OutletAccessScope.NONE, peopleAll, unassigned, List.of(), List.of(), null);
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

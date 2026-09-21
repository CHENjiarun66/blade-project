package com.blade.file.policy;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.service.OrderAccessPolicy;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import com.blade.system.user.entity.User;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文件业务访问策略（Series E2）：文件中心所有出口的唯一授权入口。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>order / order_draft 敏感绑定：分别复用 {@link OrderAccessPolicy#requireAccess} 与
 *       {@link OutletAccessPolicy#requireDraftAccess}，并强制同租户。</li>
 *   <li>多敏感绑定采用 ALL 规则：任一绑定不可访问即拒绝，避免命中一个可访问绑定就放行。</li>
 *   <li>权威来源 {@code file_business_bind}；当权威绑定为空且 legacy {@code file_storage.business_*}
 *       为 order/order_draft 时仍按 legacy 目标校验。</li>
 *   <li>未绑定临时文件仅可靠创建者本人或 {@code btn:file:viewAll} 可读/管理。</li>
 *   <li>无法解析 tenant/actor 时 fail closed，不 fallback tenant=1。</li>
 *   <li>{@code btn:file:viewAll} 不绕过 order/order_draft 的业务档口范围。</li>
 * </ul>
 */
@Service
public class FileBusinessAccessPolicy {

    public static final String AUTH_FILE_VIEW_ALL = "btn:file:viewAll";
    private static final List<String> SENSITIVE_TYPES = List.of("order", "order_draft");
    private static final Map<String, String> BUSINESS_PERMISSION_MAP = Map.of(
            "product", "menu:product",
            "sku", "menu:product",
            "inventory_log", "btn:inventory:viewLog",
            "ocr_document", "menu:file",
            "whatsapp_message", "menu:whatsapp"
    );

    private final FileBusinessBindMapper fileBusinessBindMapper;
    private final OrderMapper orderMapper;
    private final OrderDraftMapper orderDraftMapper;
    private final OrderAccessPolicy orderAccessPolicy;
    private final OutletAccessPolicy outletAccessPolicy;

    public FileBusinessAccessPolicy(FileBusinessBindMapper fileBusinessBindMapper,
                                    OrderMapper orderMapper,
                                    OrderDraftMapper orderDraftMapper,
                                    OrderAccessPolicy orderAccessPolicy,
                                    OutletAccessPolicy outletAccessPolicy) {
        this.fileBusinessBindMapper = fileBusinessBindMapper;
        this.orderMapper = orderMapper;
        this.orderDraftMapper = orderDraftMapper;
        this.orderAccessPolicy = orderAccessPolicy;
        this.outletAccessPolicy = outletAccessPolicy;
    }

    private record SensitiveTarget(String type, Long id) {
    }

    // ==================== 读 ====================

    /** 文件读取授权：敏感绑定按业务范围，非敏感/未绑定按权限与创建者。 */
    public void requireFileRead(FileStorage file) {
        if (file == null) {
            throw BusinessException.of(404, "文件不存在");
        }
        Long tenantId = requiredTenantId();
        if (file.getTenantId() == null || !tenantId.equals(file.getTenantId())) {
            throw BusinessException.of(403, "无权访问该文件");
        }

        List<SensitiveTarget> sensitive = sensitiveTargets(file, tenantId);
        if (!sensitive.isEmpty()) {
            // ALL 规则：所有敏感绑定都必须可访问
            for (SensitiveTarget target : sensitive) {
                requireTargetAccess(target.type(), target.id());
            }
            return;
        }

        List<FileBusinessBind> binds = activeBindings(file.getId(), tenantId);
        if (!binds.isEmpty()) {
            // 非敏感业务绑定：沿用既有权限映射（product/sku 等）
            requireMappedBindingPermission(binds);
            return;
        }

        // 未绑定/临时文件：仅创建者本人或 viewAll
        if (hasViewAll()) {
            return;
        }
        Long actorId = currentUserId();
        if (actorId != null && actorId.equals(file.getCreateBy())) {
            return;
        }
        throw BusinessException.of(403, "无权访问该文件");
    }

    /** 文件是否存在 order/order_draft 敏感绑定（含 legacy-only）。用于 PUBLIC 强制业务校验。 */
    public boolean hasSensitiveTargets(FileStorage file) {
        if (file == null || file.getTenantId() == null) {
            return false;
        }
        return !sensitiveTargets(file, file.getTenantId()).isEmpty();
    }

    /** 业务目标授权：order/draft 走统一订单/草稿策略，其他走权限映射。 */
    public void requireTargetAccess(String businessType, Long businessId) {
        if (businessType == null || businessType.isBlank() || businessId == null) {
            throw BusinessException.of(400, "业务对象不能为空");
        }
        Long tenantId = requiredTenantId();
        if ("order".equals(businessType)) {
            Order order = orderMapper.selectById(businessId);
            if (order == null) {
                throw BusinessException.of(404, "订单不存在");
            }
            if (order.getTenantId() == null || !tenantId.equals(order.getTenantId())) {
                throw BusinessException.of(403, "无权访问该订单");
            }
            orderAccessPolicy.requireAccess(order);
            return;
        }
        if ("order_draft".equals(businessType)) {
            OrderDraft draft = orderDraftMapper.selectById(businessId);
            if (draft == null) {
                throw BusinessException.of(404, "草稿不存在");
            }
            if (draft.getTenantId() == null || !tenantId.equals(draft.getTenantId())) {
                throw BusinessException.of(403, "无权访问该草稿");
            }
            outletAccessPolicy.requireDraftAccess(draft);
            return;
        }
        requireMappedTypePermission(businessType);
    }

    /** 校验一批文件都可读；任一不可读整体拒绝（用于 bind/delete/move 等变更前）。 */
    public void requireFilesRead(List<FileStorage> files) {
        if (files == null) {
            return;
        }
        for (FileStorage file : files) {
            requireFileRead(file);
        }
    }

    // ==================== 列表可见性（SQL 预分页） ====================

    /**
     * 文件中心分页可见性 SQL（作用于 {@code file_storage} 外层查询，分页/计数之前）。
     *
     * <p>敏感文件 = 所有 order/order_draft 绑定均可访问；非敏感绑定可见；未绑定仅创建者或
     * viewAll。NONE 档口范围为 {@code 1=0}。</p>
     */
    public String buildVisibilityCondition() {
        Long tenantId = requiredTenantId();
        OutletAccessScope scope = outletAccessPolicy.resolveCurrentScope();
        Long actorId = scope.actorId();
        boolean peopleAll = scope.peopleAll();
        boolean viewAll = hasViewAll();

        if (!peopleAll && actorId == null) {
            return "1 = 0";
        }

        String readable = scope.readableOutletIds().isEmpty() ? null
                : scope.readableOutletIds().stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(","));
        boolean unassigned = scope.unassignedAllowed();

        String orderOutlet = outletPredicate("o", readable, unassigned);
        String draftOutlet = outletPredicate("d", readable, unassigned);
        String orderPeople = peopleAll ? "1=1" : ("o.salesman_id = " + actorId);
        String draftPeople = peopleAll ? "1=1" : ("d.created_by_user_id = " + actorId);

        String orderAccessible = "EXISTS (SELECT 1 FROM sale_order o"
                + " WHERE o.id = b.business_id AND o.tenant_id = file_storage.tenant_id AND o.deleted = 0"
                + " AND " + orderOutlet + " AND " + orderPeople + ")";
        String draftAccessible = "EXISTS (SELECT 1 FROM order_draft d"
                + " WHERE d.id = b.business_id AND d.tenant_id = file_storage.tenant_id AND d.deleted = 0"
                + " AND " + draftOutlet + " AND " + draftPeople + ")";
        String accessibleForB = "((b.business_type = 'order' AND " + orderAccessible + ")"
                + " OR (b.business_type = 'order_draft' AND " + draftAccessible + "))";

        String legacyOrderAccessible = "EXISTS (SELECT 1 FROM sale_order o"
                + " WHERE o.id = file_storage.business_id AND o.tenant_id = file_storage.tenant_id AND o.deleted = 0"
                + " AND " + orderOutlet + " AND " + orderPeople + ")";
        String legacyDraftAccessible = "EXISTS (SELECT 1 FROM order_draft d"
                + " WHERE d.id = file_storage.business_id AND d.tenant_id = file_storage.tenant_id AND d.deleted = 0"
                + " AND " + draftOutlet + " AND " + draftPeople + ")";

        String hasSensitive = "EXISTS (SELECT 1 FROM file_business_bind b WHERE b.file_id = file_storage.id"
                + " AND b.deleted = 0 AND b.business_type IN ('order','order_draft'))";
        String hasNonSensitive = "EXISTS (SELECT 1 FROM file_business_bind b WHERE b.file_id = file_storage.id"
                + " AND b.deleted = 0 AND b.business_type NOT IN ('order','order_draft'))";
        String hasAny = "EXISTS (SELECT 1 FROM file_business_bind b WHERE b.file_id = file_storage.id AND b.deleted = 0)";
        String allSensitiveAccessible = "NOT EXISTS (SELECT 1 FROM file_business_bind b"
                + " WHERE b.file_id = file_storage.id AND b.deleted = 0"
                + " AND b.business_type IN ('order','order_draft') AND NOT " + accessibleForB + ")";

        String unboundVisible = viewAll ? "1=1"
                : (actorId != null ? "file_storage.create_by = " + actorId : "1=0");

        return "("
                + "(" + hasSensitive + " AND " + allSensitiveAccessible + ")"
                + " OR (NOT " + hasSensitive + " AND " + hasNonSensitive + ")"
                + " OR (NOT " + hasAny + " AND ("
                +     "(file_storage.business_type = 'order' AND " + legacyOrderAccessible + ")"
                +     " OR (file_storage.business_type = 'order_draft' AND " + legacyDraftAccessible + ")"
                +     " OR ((file_storage.business_type IS NULL"
                +          " OR file_storage.business_type NOT IN ('order','order_draft')) AND " + unboundVisible + ")"
                + "))"
                + ")";
    }

    // ==================== 内部 ====================

    private String outletPredicate(String alias, String readable, boolean unassigned) {
        StringBuilder sb = new StringBuilder("(");
        if (readable == null) {
            sb.append("1=0");
        } else {
            sb.append(alias).append(".source_outlet_id IN (").append(readable).append(")");
        }
        if (unassigned) {
            sb.append(" OR ").append(alias).append(".source_outlet_id IS NULL");
        }
        return sb.append(")").toString();
    }

    private List<FileBusinessBind> activeBindings(Long fileId, Long tenantId) {
        if (fileId == null || tenantId == null) {
            return List.of();
        }
        return fileBusinessBindMapper.selectActiveByFileAndTenant(fileId, tenantId);
    }

    /**
     * 敏感目标集合：权威绑定中的 order/order_draft；权威绑定为空时回退 legacy
     * {@code file_storage.business_type/business_id}。
     */
    private List<SensitiveTarget> sensitiveTargets(FileStorage file, Long tenantId) {
        List<FileBusinessBind> binds = activeBindings(file.getId(), tenantId);
        List<SensitiveTarget> targets = binds.stream()
                .filter(bind -> bind.getBusinessType() != null && bind.getBusinessId() != null)
                .filter(bind -> SENSITIVE_TYPES.contains(bind.getBusinessType()))
                .map(bind -> new SensitiveTarget(bind.getBusinessType(), bind.getBusinessId()))
                .distinct()
                .toList();
        if (targets.isEmpty() && binds.isEmpty()
                && file.getBusinessType() != null && SENSITIVE_TYPES.contains(file.getBusinessType())
                && file.getBusinessId() != null) {
            return List.of(new SensitiveTarget(file.getBusinessType(), file.getBusinessId()));
        }
        return targets;
    }

    private void requireMappedBindingPermission(List<FileBusinessBind> binds) {
        if (hasViewAll()) {
            return;
        }
        boolean anyMapped = binds.stream()
                .map(FileBusinessBind::getBusinessType)
                .filter(Objects::nonNull)
                .anyMatch(type -> BUSINESS_PERMISSION_MAP.containsKey(type));
        if (!anyMapped) {
            // 未知/其他非敏感绑定：按文件中心权限处理，viewAll 已在上面放行
            throw BusinessException.of(403, "无权访问该业务文件");
        }
        for (FileBusinessBind bind : binds) {
            String permission = BUSINESS_PERMISSION_MAP.get(bind.getBusinessType());
            if (permission != null && hasAuthority(permission)) {
                return;
            }
        }
        throw BusinessException.of(403, "无权访问该业务文件");
    }

    private void requireMappedTypePermission(String businessType) {
        if (hasViewAll()) {
            return;
        }
        String permission = BUSINESS_PERMISSION_MAP.get(businessType);
        if (permission == null || !hasAuthority(permission)) {
            throw BusinessException.of(403, "无权访问该业务文件");
        }
    }

    private Long requiredTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw BusinessException.of(403, "缺少租户上下文");
        }
        return tenantId;
    }

    private boolean hasViewAll() {
        return hasAuthority(AUTH_FILE_VIEW_ALL);
    }

    private boolean hasAuthority(String code) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(code::equals);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }
}

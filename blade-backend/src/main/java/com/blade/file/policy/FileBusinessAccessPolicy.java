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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文件业务访问策略（Series E2）：文件中心所有出口的唯一授权入口。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>order / order_draft 敏感绑定：分别复用 {@link OrderAccessPolicy#requireAccess} 与
 *       {@link OutletAccessPolicy#requireDraftAccess}，并强制同租户。</li>
 *   <li>多敏感绑定采用 ALL 规则：任一绑定不可访问即拒绝。</li>
 *   <li>权威来源 {@code file_business_bind}；当权威绑定为空且 legacy {@code file_storage.business_*}
 *       为 order/order_draft 时仍按 legacy 目标校验。</li>
 *   <li>非敏感映射（product/sku/inventory_log/ocr_document/whatsapp_message）按当前 authorities
 *       判定；直接读取与列表 SQL 使用同一“任一映射权限满足”语义。</li>
 *   <li>未绑定 / 仅 temp 等未知绑定：仅可靠创建者本人或 {@code btn:file:viewAll}。</li>
 *   <li>无法解析 tenant/actor 时 fail closed，不 fallback tenant=1；{@code btn:file:viewAll}
 *       不绕过 order/order_draft 的业务档口范围。</li>
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

    /** 预分页可见性：参数化 SQL 模板 + 绑定参数，供 wrapper.apply(sql, params)。 */
    public record VisibilityCondition(String sql, Object[] params) {
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
        boolean hasMappedBinding = binds.stream()
                .map(FileBusinessBind::getBusinessType)
                .filter(Objects::nonNull)
                .anyMatch(BUSINESS_PERMISSION_MAP::containsKey);
        if (hasMappedBinding) {
            requireMappedBindingPermission(binds);
            return;
        }

        // 未绑定 / 仅 temp 等未知绑定：仅创建者本人或 viewAll
        if (hasViewAll()) {
            return;
        }
        Long actorId = currentUserId();
        if (actorId != null && actorId.equals(file.getCreateBy())) {
            return;
        }
        throw BusinessException.of(403, "无权访问该文件");
    }

    /** 文件是否存在 order/order_draft 敏感绑定（含 legacy-only）。仅用文件自带 tenant，不依赖 TenantContext。 */
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

    /** 校验一批文件都可读；任一不可读整体拒绝。 */
    public void requireFilesRead(List<FileStorage> files) {
        if (files == null) {
            return;
        }
        for (FileStorage file : files) {
            requireFileRead(file);
        }
    }

    // ==================== 列表可见性（SQL 预分页，参数化） ====================

    /**
     * 文件中心分页可见性条件（作用于 {@code file_storage} 外层查询，分页/计数之前）。
     *
     * <p>所有子查询严格 {@code b.tenant_id = ?} 且与直接读取权限一致：非敏感映射只在调用者
     * 拥有对应权限时可见；temp/未绑定仅创建者或 viewAll；NONE 档口范围为 {@code 1=0}。</p>
     */
    public VisibilityCondition buildVisibilityCondition() {
        Long tenantId = requiredTenantId();
        OutletAccessScope scope = outletAccessPolicy.resolveCurrentScope();
        Long actorId = scope.actorId();
        boolean peopleAll = scope.peopleAll();
        boolean viewAll = hasViewAll();

        if (!peopleAll && actorId == null) {
            return new VisibilityCondition("1 = 0", new Object[0]);
        }

        List<Object> params = new ArrayList<>();
        String tenantPh = placeholder(params, tenantId);
        String readableIn = null;
        if (!scope.readableOutletIds().isEmpty()) {
            readableIn = scope.readableOutletIds().stream()
                    .filter(Objects::nonNull)
                    .map(id -> placeholder(params, id))
                    .collect(Collectors.joining(",", "(", ")"));
        }
        String actorPh = actorId != null ? placeholder(params, actorId) : null;

        boolean unassigned = scope.unassignedAllowed();
        String orderOutlet = outletPredicate("o", readableIn, unassigned);
        String draftOutlet = outletPredicate("d", readableIn, unassigned);
        String orderPeople = peopleAll ? "1=1" : ("o.salesman_id = " + actorPh);
        String draftPeople = peopleAll ? "1=1" : ("d.created_by_user_id = " + actorPh);

        String orderAccessible = "EXISTS (SELECT 1 FROM sale_order o"
                + " WHERE o.id = b.business_id AND o.tenant_id = " + tenantPh + " AND o.deleted = 0"
                + " AND " + orderOutlet + " AND " + orderPeople + ")";
        String draftAccessible = "EXISTS (SELECT 1 FROM order_draft d"
                + " WHERE d.id = b.business_id AND d.tenant_id = " + tenantPh + " AND d.deleted = 0"
                + " AND " + draftOutlet + " AND " + draftPeople + ")";
        String accessibleForB = "((b.business_type = 'order' AND " + orderAccessible + ")"
                + " OR (b.business_type = 'order_draft' AND " + draftAccessible + "))";

        String legacyOrderAccessible = "EXISTS (SELECT 1 FROM sale_order o"
                + " WHERE o.id = file_storage.business_id AND o.tenant_id = " + tenantPh + " AND o.deleted = 0"
                + " AND " + orderOutlet + " AND " + orderPeople + ")";
        String legacyDraftAccessible = "EXISTS (SELECT 1 FROM order_draft d"
                + " WHERE d.id = file_storage.business_id AND d.tenant_id = " + tenantPh + " AND d.deleted = 0"
                + " AND " + draftOutlet + " AND " + draftPeople + ")";

        String bindTenant = "b.tenant_id = " + tenantPh;
        String hasSensitive = "EXISTS (SELECT 1 FROM file_business_bind b WHERE b.file_id = file_storage.id"
                + " AND " + bindTenant + " AND b.deleted = 0 AND b.business_type IN ('order','order_draft'))";
        String allSensitiveAccessible = "NOT EXISTS (SELECT 1 FROM file_business_bind b"
                + " WHERE b.file_id = file_storage.id AND " + bindTenant + " AND b.deleted = 0"
                + " AND b.business_type IN ('order','order_draft') AND NOT " + accessibleForB + ")";

        List<String> permittedTypes = permittedNonSensitiveTypes(viewAll);
        String hasPermittedNonSensitive = permittedTypes.isEmpty()
                ? "1=0"
                : "EXISTS (SELECT 1 FROM file_business_bind b WHERE b.file_id = file_storage.id"
                    + " AND " + bindTenant + " AND b.deleted = 0"
                    + " AND b.business_type IN (" + literalList(permittedTypes) + "))";
        // 任意映射类型（不看调用者权限）：存在时不得回落“未绑定”创建者规则
        String hasAnyMappedNonSensitive = "EXISTS (SELECT 1 FROM file_business_bind b WHERE b.file_id = file_storage.id"
                + " AND " + bindTenant + " AND b.deleted = 0"
                + " AND b.business_type IN (" + literalList(allMappedTypes()) + "))";

        String unboundVisible = viewAll ? "1=1"
                : (actorId != null ? "file_storage.create_by = " + actorPh : "1=0");

        String sql = "("
                + "(" + hasSensitive + " AND " + allSensitiveAccessible + ")"
                + " OR (NOT " + hasSensitive + " AND " + hasPermittedNonSensitive + ")"
                + " OR (NOT " + hasSensitive + " AND NOT " + hasAnyMappedNonSensitive + " AND ("
                +     "(file_storage.business_type = 'order' AND " + legacyOrderAccessible + ")"
                +     " OR (file_storage.business_type = 'order_draft' AND " + legacyDraftAccessible + ")"
                +     " OR ((file_storage.business_type IS NULL"
                +          " OR file_storage.business_type NOT IN ('order','order_draft')) AND " + unboundVisible + ")"
                + "))"
                + ")";
        return new VisibilityCondition(sql, params.toArray());
    }

    // ==================== 内部 ====================

    private static String placeholder(List<Object> params, Object value) {
        params.add(value);
        return "{" + (params.size() - 1) + "}";
    }

    private String outletPredicate(String alias, String readableIn, boolean unassigned) {
        StringBuilder sb = new StringBuilder("(");
        if (readableIn == null) {
            sb.append("1=0");
        } else {
            sb.append(alias).append(".source_outlet_id IN ").append(readableIn);
        }
        if (unassigned) {
            sb.append(" OR ").append(alias).append(".source_outlet_id IS NULL");
        }
        return sb.append(")").toString();
    }

    private static String literalList(List<String> values) {
        return values.stream()
                .map(value -> "'" + value.replace("'", "''") + "'")
                .collect(Collectors.joining(","));
    }

    private List<String> permittedNonSensitiveTypes(boolean viewAll) {
        if (viewAll) {
            return allMappedTypes();
        }
        return BUSINESS_PERMISSION_MAP.entrySet().stream()
                .filter(entry -> hasAuthority(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private static List<String> allMappedTypes() {
        return BUSINESS_PERMISSION_MAP.keySet().stream().sorted().toList();
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

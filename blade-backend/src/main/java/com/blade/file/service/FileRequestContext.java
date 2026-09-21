package com.blade.file.service;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.system.user.entity.User;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 文件模块请求上下文 helper（Series E2 第二轮整改）。
 *
 * <p>说明：{@code /api/files}、{@code /api/file-folders}、{@code /api/files/cleanup} 是 JWT 用户路径，
 * 不接受 Agent principal。缺少 TenantContext 或无法解析可靠 User 一律 403 fail closed，
 * 绝不回退 tenant=1 / user=1；系统任务（清理定时器）显式遍历租户并自行设置 TenantContext。</p>
 */
public final class FileRequestContext {

    private FileRequestContext() {
    }

    /** 请求租户：必须已由认证过滤器写入，否则 403。 */
    public static Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw BusinessException.of(403, "缺少租户上下文");
        }
        return tenantId;
    }

    /** 可靠操作者：仅接受 User principal 且 id 非空，否则 403（不 fallback user 1）。 */
    public static Long requireOperatorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof User user
                && user.getId() != null) {
            return user.getId();
        }
        throw BusinessException.of(403, "无法解析当前用户");
    }
}

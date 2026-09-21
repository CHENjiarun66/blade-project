package com.blade.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class AgentKeyManagementDTO {
    private AgentKeyManagementDTO() {
    }

    public record CreateRequest(
            @NotBlank(message = "Key名称不能为空") @Size(max = 100) String name,
            @NotEmpty(message = "至少选择一个scope") List<String> scopes,
            @Min(value = 1, message = "有效期至少1天") @Max(value = 365, message = "有效期不能超过365天") Integer expiresInDays,
            String outletScopeType,
            List<Long> outletIds,
            Long defaultOutletId) {
    }

    public record RotateRequest(
            @Size(min = 1, message = "至少选择一个scope") List<String> scopes,
            @Min(value = 1, message = "有效期至少1天") @Max(value = 365, message = "有效期不能超过365天") Integer expiresInDays,
            String outletScopeType,
            List<Long> outletIds,
            Long defaultOutletId) {
    }

    /** Key 已绑定档口摘要；对 ALL 范围只会出现被标记为默认档口的那一条。 */
    public record OutletSummary(
            Long id,
            String outletCode,
            String outletName,
            Integer status,
            boolean isDefault) {
    }

    /** 管理者可配置档口选项：仅受租户约束，不受当前管理账号自身 ASSIGNED 范围限制。 */
    public record AdminOutletOption(
            Long id,
            String outletCode,
            String outletName,
            Integer status,
            boolean tenantDefault) {
    }

    public record View(
            Long id,
            String name,
            String keyPrefix,
            List<String> scopes,
            String outletScopeType,
            Long defaultOutletId,
            List<OutletSummary> outlets,
            Integer status,
            LocalDateTime expiresTime,
            boolean expired,
            LocalDateTime lastUsedTime,
            String lastUsedIp,
            Long createdByUserId,
            LocalDateTime disabledTime,
            Long rotatedFromKeyId,
            LocalDateTime createTime) {
    }

    public record Credential(
            Long id,
            String name,
            String agentKey,
            String keyPrefix,
            List<String> scopes,
            String outletScopeType,
            Long defaultOutletId,
            List<OutletSummary> outlets,
            LocalDateTime expiresTime,
            Long rotatedFromKeyId) {
    }
}

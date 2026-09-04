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
            @Min(value = 1, message = "有效期至少1天") @Max(value = 365, message = "有效期不能超过365天") Integer expiresInDays) {
    }

    public record RotateRequest(
            @Min(value = 1, message = "有效期至少1天") @Max(value = 365, message = "有效期不能超过365天") Integer expiresInDays) {
    }

    public record View(
            Long id,
            String name,
            String keyPrefix,
            List<String> scopes,
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
            LocalDateTime expiresTime,
            Long rotatedFromKeyId) {
    }
}

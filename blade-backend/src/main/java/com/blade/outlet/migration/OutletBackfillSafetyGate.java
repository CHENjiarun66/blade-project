package com.blade.outlet.migration;

import com.blade.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 回填写库安全闸门（Series F）：
 * 必须显式开启 apply、提供映射文件与租户、确认在生产副本操作，且数据库连接不含生产/NAS 特征。
 */
@Component
public class OutletBackfillSafetyGate {

    private static final List<String> PRODUCTION_MARKERS = List.of("prod", "production", "nas");

    public void requireApplyAllowed(OutletBackfillProperties properties, String jdbcUrl, String databaseName) {
        if (!properties.isApply()) {
            throw BusinessException.of(400, "apply 未显式开启（默认 dry-run）");
        }
        if (properties.getMappingFile() == null || properties.getMappingFile().isBlank()) {
            throw BusinessException.of(400, "apply 必须显式提供映射文件");
        }
        if (properties.getTenantId() == null) {
            throw BusinessException.of(400, "apply 必须显式指定租户");
        }
        if (!properties.isCopyEnvironmentAck()) {
            throw BusinessException.of(400, "apply 必须确认连接的是生产副本（blade.outlet.backfill.copy-environment-ack=true）");
        }
        String probe = ((jdbcUrl == null ? "" : jdbcUrl) + " " + (databaseName == null ? "" : databaseName))
                .toLowerCase(Locale.ROOT);
        for (String marker : PRODUCTION_MARKERS) {
            if (probe.contains(marker)) {
                throw BusinessException.of(403, "拒绝疑似生产/NAS 数据库连接（命中特征: " + marker + "）");
            }
        }
    }
}

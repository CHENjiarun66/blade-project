package com.blade.outlet.migration;

import com.blade.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Series F 回填命令行入口：仅当显式传入 {@code --blade.outlet.backfill.mapping-file=...} 时激活。
 *
 * <p>默认 dry-run 并输出 JSON 报告；apply 需同时传
 * {@code --blade.outlet.backfill.apply=true --blade.outlet.backfill.tenant-id=N
 * --blade.outlet.backfill.copy-environment-ack=true}，并通过安全闸门的生产特征拒绝。
 * 只应在生产库副本上执行。</p>
 */
@Component
@ConditionalOnProperty(name = "blade.outlet.backfill.mapping-file")
@RequiredArgsConstructor
public class OutletBackfillCli implements ApplicationRunner {

    private final OutletBackfillProperties properties;
    private final OutletBackfillService service;
    private final OutletBackfillSafetyGate safetyGate;
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String mappingFile = properties.getMappingFile();
        if (mappingFile == null || mappingFile.isBlank()) {
            return;
        }
        Long tenantId = properties.getTenantId();
        if (tenantId == null) {
            throw BusinessException.of(400, "必须显式指定 blade.outlet.backfill.tenant-id");
        }
        List<OutletBackfillMappingRow> rows = OutletBackfillMapping.parseFile(mappingFile);
        OutletBackfillReport report;
        if (properties.isApply()) {
            safetyGate.requireApplyAllowed(properties,
                    environment.getProperty("spring.datasource.url"), currentDatabase());
            report = service.apply(tenantId, rows);
        } else {
            report = service.preview(tenantId, rows);
        }
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }

    private String currentDatabase() {
        return jdbc.queryForObject("SELECT DATABASE()", String.class);
    }
}

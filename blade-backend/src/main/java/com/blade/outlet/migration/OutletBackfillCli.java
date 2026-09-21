package com.blade.outlet.migration;

import com.blade.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Series F 回填命令行入口：仅当显式传入 {@code --blade.outlet.backfill.mapping-file=...} 时激活。
 *
 * <p>默认 dry-run 并输出 JSON +（可选 report-dir 下的 JSON/Markdown）报告；apply 必须经
 * {@link OutletBackfillSafetyGate} 的 fail-closed 正向副本身份验证后才可写入。建议一次性运行：
 * {@code --spring.main.web-application-type=none}。只应在生产库副本上执行。</p>
 */
@Component
@ConditionalOnProperty(name = "blade.outlet.backfill.mapping-file")
@RequiredArgsConstructor
public class OutletBackfillCli implements ApplicationRunner {

    private final OutletBackfillProperties properties;
    private final OutletBackfillSafetyGate safetyGate;
    private final OutletBackfillService service;
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
            report = service.apply(safetyGate.approve(properties), rows);
        } else {
            Path reportDir = properties.getReportDir() == null || properties.getReportDir().isBlank()
                    ? null : Path.of(properties.getReportDir());
            report = service.preview(tenantId, reportDir, rows);
        }
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}

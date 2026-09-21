package com.blade.outlet.migration;

import com.blade.common.exception.BusinessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 显式历史档口映射 CSV 解析与静态校验（Series F / DATA-OUTLET-002）。
 *
 * <p>列固定为 {@code tenant_id,legacy_source_shop,outlet_code,decision,reason}。
 * decision 取值：{@code MAP}（回填，必须给 outlet_code）、{@code SKIP}（人工明确不回填）、
 * {@code REVIEW}（需人工复核）。纯数字 legacy_source_shop 一律拒绝自动映射；
 * 同租户同 legacy 值出现冲突决策直接报错；只有 MAP 行会进入写库候选。</p>
 */
public final class OutletBackfillMapping {

    public static final String DECISION_MAP = "MAP";
    public static final String DECISION_SKIP = "SKIP";
    public static final String DECISION_REVIEW = "REVIEW";

    private static final List<String> HEADER = List.of(
            "tenant_id", "legacy_source_shop", "outlet_code", "decision", "reason");
    private static final Set<String> DECISIONS = Set.of(DECISION_MAP, DECISION_SKIP, DECISION_REVIEW);

    private OutletBackfillMapping() {
    }

    public static List<OutletBackfillMappingRow> parseFile(String path) {
        try {
            return parse(Files.readString(Path.of(path)));
        } catch (IOException e) {
            throw BusinessException.of(400, "无法读取映射文件: " + path);
        }
    }

    public static List<OutletBackfillMappingRow> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            throw BusinessException.of(400, "映射文件为空");
        }
        List<String> lines = csv.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .toList();
        if (lines.isEmpty()) {
            throw BusinessException.of(400, "映射文件没有有效行");
        }
        List<String> header = parseLine(lines.get(0)).stream().map(String::trim).toList();
        if (!header.equals(HEADER)) {
            throw BusinessException.of(400, "映射表头必须为 tenant_id,legacy_source_shop,outlet_code,decision,reason");
        }
        List<OutletBackfillMappingRow> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = parseLine(lines.get(i));
            int lineNo = i + 1;
            if (cells.size() != HEADER.size()) {
                errors.add("第 " + lineNo + " 行列数应为 " + HEADER.size());
                continue;
            }
            String tenantRaw = cells.get(0).trim();
            String legacy = cells.get(1).trim();
            String outletCode = cells.get(2).trim();
            String decision = cells.get(3).trim().toUpperCase(Locale.ROOT);
            String reason = cells.get(4).trim();

            long tenantId;
            try {
                tenantId = Long.parseLong(tenantRaw);
                if (tenantId <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                errors.add("第 " + lineNo + " 行 tenant_id 非法: " + tenantRaw);
                continue;
            }
            if (legacy.isEmpty()) {
                errors.add("第 " + lineNo + " 行 legacy_source_shop 不能为空");
                continue;
            }
            if (!DECISIONS.contains(decision)) {
                errors.add("第 " + lineNo + " 行 decision 非法: " + cells.get(3));
                continue;
            }
            if (DECISION_MAP.equals(decision)) {
                if (outletCode.isEmpty()) {
                    errors.add("第 " + lineNo + " 行 MAP 必须提供 outlet_code");
                    continue;
                }
                if (legacy.matches("^[0-9]+$")) {
                    errors.add("第 " + lineNo + " 行纯数字 legacy_source_shop 不得自动映射: " + legacy);
                    continue;
                }
            } else {
                if (!outletCode.isEmpty()) {
                    errors.add("第 " + lineNo + " 行非 MAP 不应提供 outlet_code");
                    continue;
                }
                if (reason.isEmpty()) {
                    errors.add("第 " + lineNo + " 行 SKIP/REVIEW 必须提供 reason");
                    continue;
                }
            }
            String key = tenantId + "\u0000" + legacy;
            if (!seen.add(key)) {
                errors.add("第 " + lineNo + " 行同租户 legacy_source_shop 重复: " + legacy);
                continue;
            }
            rows.add(new OutletBackfillMappingRow(tenantId, legacy, outletCode, decision, reason));
        }
        if (!errors.isEmpty()) {
            throw BusinessException.of(400, "映射文件校验失败: " + String.join("; ", errors));
        }
        if (rows.isEmpty()) {
            throw BusinessException.of(400, "映射文件没有有效映射行");
        }
        return List.copyOf(rows);
    }

    /** 最小 CSV 解析：支持双引号包裹与转义双引号。 */
    static List<String> parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    static List<String> header() {
        return HEADER;
    }

    static List<String> decisions() {
        return Arrays.asList(DECISION_MAP, DECISION_SKIP, DECISION_REVIEW);
    }
}

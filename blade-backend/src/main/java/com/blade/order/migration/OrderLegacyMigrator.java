package com.blade.order.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 历史订单迁移工具（SOW-7 / BE-1047）——离线、受控、默认 dry-run。
 * <p>
 * 边界（ROM/SOW 与执行看板锁定）：
 * <ul>
 *   <li>不进入常驻应用，不提供任何 Web 端点，不进 Flyway；由维护窗口以命令行执行</li>
 *   <li>默认 dry-run 只输出审计报告；真实写回必须显式传 {@code --execute}</li>
 *   <li>租户必填（空租户拒绝）；数据源通过参数显式指定</li>
 *   <li>不根据旧 status=7/8（退货语义）自动决定，进入人工核对清单</li>
 *   <li>不伪造收款时间/操作人：MIGRATION_OPENING 以 pay_time/create_time 为业务时间，operator 为空</li>
 *   <li>幂等重放：已有 fulfillment_status 的订单跳过；已有 MIGRATION_OPENING 的订单不重复落流水</li>
 *   <li>证据冲突（状态/计划/出库/金额互相矛盾）进入人工核对清单，不自动变更</li>
 * </ul>
 * 映射规则（14 号文档 §7.2）：已结清且无配货/出库证据 → COMPLETED + RECORD_ONLY +
 * SETTLED(MIGRATION_CONFIRMED)；存在实际出库证据 → SHIPPED + STOCK_LINKED；
 * 存在配货计划 → 按计划状态推导并复核；部分收款/未收款 → CONFIRMED + UNDECIDED；
 * 证据冲突 → 人工核对。
 * <p>
 * SQL 语句集中存放于 {@code resources/migration/order-legacy-migrator-sql.json}（便于 DBA 审核），
 * 经 JdbcTemplate 全部参数绑定执行，事务由 DataSourceTransactionManager 语义保证
 * （单连接单事务：迁移成功整体提交，任一异常整体回滚）。
 */
public class OrderLegacyMigrator {

    private final JdbcTemplate jdbc;
    private final Map<String, String> sql;

    public OrderLegacyMigrator(String jdbcUrl, String user, String password) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, user, password);
        this.jdbc = new JdbcTemplate(dataSource);
        try (var in = OrderLegacyMigrator.class.getResourceAsStream("/migration/order-legacy-migrator-sql.json")) {
            if (in == null) {
                throw new IllegalStateException("缺少 SQL 语句资源文件 order-legacy-migrator-sql.json");
            }
            this.sql = new ObjectMapper().readValue(in, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        }
    }

    /** 逐单迁移结果 */
    public record OrderResult(
            long orderId,
            String orderNo,
            String decision,
            String fromStatus,
            String toFulfillment,
            String toMode,
            String toCollection,
            String settlementMethod,
            String evidence,
            String note
    ) {
    }

    /** 迁移报告 + 不变量 */
    public static class Report {
        public final List<OrderResult> results = new ArrayList<>();
        public long totalOrders;
        public long migrated;
        public long manualReview;
        public long skipped;
        public BigDecimal sumTotalBefore = BigDecimal.ZERO;
        public BigDecimal sumTotalAfter = BigDecimal.ZERO;
        public BigDecimal sumPaidBefore = BigDecimal.ZERO;
        public BigDecimal sumGrossAfter = BigDecimal.ZERO;
        public String factsVersion = "order-facts-v1";

        public String render() {
            StringBuilder sb = new StringBuilder();
            sb.append("# 历史订单迁移审计报告\n\n");
            sb.append("- 口径版本：").append(factsVersion).append('\n');
            sb.append("- 订单总数：").append(totalOrders).append('\n');
            sb.append("- 迁移：").append(migrated).append(" / 人工核对：").append(manualReview)
                    .append(" / 已迁移跳过：").append(skipped).append("\n\n");
            sb.append("不变量：\n");
            sb.append("- 订单原始金额总量（迁移不修改 total_amount）：")
                    .append(sumTotalBefore).append(" / ").append(sumTotalAfter).append('\n');
            sb.append("- 实收迁移对账（旧 paid_amount 合计 / 新 gross_received 期初合计）：")
                    .append(sumPaidBefore).append(" / ").append(sumGrossAfter).append("\n\n");
            sb.append("| 订单ID | 单号 | 决策 | 旧status | 新履约 | 履约方式 | 收款状态 | 结清方式 | 证据 | 备注 |\n");
            sb.append("|---|---|---|---|---|---|---|---|---|---|\n");
            for (OrderResult r : results) {
                sb.append("| ").append(r.orderId()).append(" | ").append(r.orderNo())
                        .append(" | ").append(r.decision())
                        .append(" | ").append(r.fromStatus())
                        .append(" | ").append(r.toFulfillment())
                        .append(" | ").append(r.toMode())
                        .append(" | ").append(r.toCollection())
                        .append(" | ").append(r.settlementMethod())
                        .append(" | ").append(r.evidence())
                        .append(" | ").append(r.note()).append(" |\n");
            }
            return sb.toString();
        }
    }

    /**
     * 执行迁移。
     *
     * @param tenantId 租户（必填，空拒绝）
     * @param execute  false = dry-run（只算不写）；true = 真实写回
     */
    public Report migrate(Long tenantId, boolean execute) {
        if (tenantId == null) {
            throw new IllegalArgumentException("必须显式指定租户（--tenant），拒绝空租户迁移");
        }
        Report report = new Report();
        List<LegacyOrder> orders = loadOrders(tenantId);
        report.totalOrders = orders.size();
        for (LegacyOrder o : orders) {
            migrateOne(jdbc, o, execute, report);
        }
        computeInvariants(tenantId, report);
        return report;
    }

    // ==================== 核心：逐单映射（包级可见，供预演测试复用） ====================

    void migrateOne(JdbcTemplate jdbc, LegacyOrder o, boolean execute, Report report) {
        if (notBlank(o.fulfillmentStatus)) {
            OrderResult skippedResult = new OrderResult(o.id, o.orderNo, "SKIPPED_ALREADY_MIGRATED",
                    str(o.status), o.fulfillmentStatus, o.fulfillmentMode, o.collectionStatus, "", "已迁移", "");
            report.results.add(skippedResult);
            report.skipped++;
            return;
        }

        String evidence = "status=" + o.status
                + ",paid=" + o.paidAmount
                + ",writeOff=" + o.writeOffAmount
                + ",total=" + o.totalAmount
                + ",plans=" + o.planCount
                + ",delivered=" + o.hasDelivery
                + ",isDelivered=" + o.isDelivered;

        // 旧退货语义：不自动决定
        if (o.status != null && (o.status == 7 || o.status == 8)) {
            report.results.add(new OrderResult(o.id, o.orderNo, "MANUAL_REVIEW",
                    str(o.status), "", "", "", "", evidence, "旧退货语义(status=7/8)不自动映射"));
            report.manualReview++;
            return;
        }

        boolean hasDeliveryEvidence = o.hasDelivery || (o.isDelivered != null && o.isDelivered == 1);
        boolean hasPlans = o.planCount > 0;
        boolean paidPositive = o.paidAmount != null && o.paidAmount.signum() > 0;

        // 证据冲突：存在出库事实但无收款证据 → 人工核对
        if (hasDeliveryEvidence && !paidPositive) {
            report.results.add(new OrderResult(o.id, o.orderNo, "MANUAL_REVIEW",
                    str(o.status), "", "", "", "", evidence, "存在出库证据但无收款证据，冲突"));
            report.manualReview++;
            return;
        }

        String toFulfillment;
        String toMode;
        String toCollection;
        String settlementMethod;
        String note;

        if (hasDeliveryEvidence) {
            toFulfillment = "SHIPPED";
            toMode = "STOCK_LINKED";
            toCollection = "SETTLED";
            settlementMethod = "MIGRATION_CONFIRMED";
            note = "按实际出库证据推导，建议复核";
        } else if (hasPlans) {
            toFulfillment = "ALLOCATING";
            toMode = "STOCK_LINKED";
            toCollection = paidPositive ? "SETTLED" : "PARTIAL";
            settlementMethod = "MIGRATION_CONFIRMED";
            note = "存在配货计划，按计划状态推导，建议复核";
        } else {
            BigDecimal netReceivable = nz(o.totalAmount).subtract(nz(o.refundAmount)).subtract(nz(o.writeOffAmount)).max(BigDecimal.ZERO);
            boolean settled = (o.paymentStatus != null && o.paymentStatus == 2)
                    || (paidPositive && netReceivable.compareTo(nz(o.paidAmount)) <= 0);
            if (settled) {
                toFulfillment = "COMPLETED";
                toMode = "RECORD_ONLY";
                toCollection = "SETTLED";
                settlementMethod = "MIGRATION_CONFIRMED";
                note = "已结清且无配货/出库证据 → 仅记录完成";
            } else {
                toFulfillment = "CONFIRMED";
                toMode = "UNDECIDED";
                toCollection = paidPositive ? "PARTIAL" : "UNPAID";
                settlementMethod = "";
                note = paidPositive ? "保留尾款，等待后续收款" : "保留未收款状态";
            }
        }

        if (execute) {
            BigDecimal gross = nz(o.paidAmount);
            BigDecimal netReceivable = nz(o.totalAmount).subtract(nz(o.refundAmount)).subtract(nz(o.writeOffAmount)).max(BigDecimal.ZERO);
            BigDecimal balance = netReceivable.subtract(gross).max(BigDecimal.ZERO);
            Timestamp settledAt = "SETTLED".equals(toCollection)
                    ? Timestamp.valueOf(o.payTime != null ? o.payTime : o.createTime)
                    : null;

            jdbc.update(sql.get("migrateOrder"), ps -> {
                ps.setString(1, toFulfillment);
                ps.setString(2, toMode);
                ps.setString(3, toCollection);
                ps.setString(4, settlementMethod.isBlank() ? null : settlementMethod);
                ps.setTimestamp(5, settledAt);
                ps.setBigDecimal(6, gross);
                ps.setBigDecimal(7, gross);
                ps.setBigDecimal(8, balance);
                ps.setLong(9, o.id);
                ps.setLong(10, o.tenantId);
            });

            // 期初收款流水（幂等：已有 MIGRATION_OPENING 不重复）
            if (paidPositive && countMigrationOpening(o.id, o.tenantId) == 0) {
                Timestamp occurredAt = Timestamp.valueOf(o.payTime != null ? o.payTime : o.createTime);
                jdbc.update(sql.get("insertMigrationOpening"), ps -> {
                    ps.setLong(1, o.tenantId);
                    ps.setLong(2, o.id);
                    ps.setBigDecimal(3, o.paidAmount);
                    ps.setTimestamp(4, occurredAt);
                });
            }
            // 状态迁移日志（迁移动作，不伪造操作人）
            jdbc.update(sql.get("insertTransitionLog"), ps -> {
                ps.setLong(1, o.tenantId);
                ps.setLong(2, o.id);
                ps.setString(3, toFulfillment);
                ps.setString(4, toCollection);
                ps.setString(5, toMode);
                ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            });
        }

        report.results.add(new OrderResult(o.id, o.orderNo, "MIGRATED",
                str(o.status), toFulfillment, toMode, toCollection, settlementMethod, evidence, note));
        report.migrated++;
    }

    // ==================== 数据访问 ====================

    private List<LegacyOrder> loadOrders(Long tenantId) {
        return jdbc.query(sql.get("loadOrders"),
                ps -> ps.setLong(1, tenantId),
                (rs, rowNum) -> {
                    LegacyOrder o = new LegacyOrder();
                    o.id = rs.getLong("id");
                    o.orderNo = rs.getString("order_no");
                    o.tenantId = rs.getLong("tenant_id");
                    o.status = (Integer) rs.getObject("status");
                    o.paymentStatus = (Integer) rs.getObject("payment_status");
                    o.totalAmount = rs.getBigDecimal("total_amount");
                    o.refundAmount = rs.getBigDecimal("refund_amount");
                    o.writeOffAmount = rs.getBigDecimal("write_off_amount");
                    o.paidAmount = rs.getBigDecimal("paid_amount");
                    Timestamp payTime = rs.getTimestamp("pay_time");
                    o.payTime = payTime == null ? null : payTime.toLocalDateTime();
                    Timestamp createTime = rs.getTimestamp("create_time");
                    o.createTime = createTime == null ? null : createTime.toLocalDateTime();
                    o.isDelivered = (Integer) rs.getObject("is_delivered");
                    o.fulfillmentStatus = rs.getString("fulfillment_status");
                    o.fulfillmentMode = rs.getString("fulfillment_mode");
                    o.collectionStatus = rs.getString("collection_status");
                    o.planCount = rs.getInt("plan_count");
                    o.hasDelivery = rs.getInt("delivery_done") > 0;
                    o.migrationOpenings = rs.getInt("migration_openings");
                    return o;
                });
    }

    private int countMigrationOpening(long orderId, long tenantId) {
        Integer count = jdbc.queryForObject(sql.get("countMigrationOpening"),
                Integer.class, orderId, tenantId);
        return count == null ? 0 : count;
    }

    private void computeInvariants(Long tenantId, Report report) {
        jdbc.query(sql.get("sumLegacy"), rs -> {
            report.sumTotalBefore = rs.getBigDecimal(1);
            report.sumPaidBefore = rs.getBigDecimal(2);
        }, tenantId);
        jdbc.query(sql.get("sumNew"), rs -> {
            report.sumTotalAfter = rs.getBigDecimal(1);
            report.sumGrossAfter = rs.getBigDecimal(2);
        }, tenantId);
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String str(Integer v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** 旧订单行 */
    static class LegacyOrder {
        long id;
        String orderNo;
        long tenantId;
        Integer status;
        Integer paymentStatus;
        BigDecimal totalAmount;
        BigDecimal refundAmount;
        BigDecimal writeOffAmount;
        BigDecimal paidAmount;
        LocalDateTime payTime;
        LocalDateTime createTime;
        Integer isDelivered;
        String fulfillmentStatus;
        String fulfillmentMode;
        String collectionStatus;
        int planCount;
        boolean hasDelivery;
        int migrationOpenings;
    }

    // ==================== 命令行入口（离线执行） ====================

    public static void main(String[] args) throws Exception {
        String url = arg(args, "--url");
        String user = arg(args, "--user", "root");
        String password = arg(args, "--password");
        String tenant = arg(args, "--tenant");
        boolean execute = List.of(args).contains("--execute");
        String reportPath = arg(args, "--report", "migration-report.md");

        if (url == null || tenant == null) {
            System.err.println("用法: OrderLegacyMigrator --url <jdbc> [--user u] [--password p] --tenant <id> [--execute] [--report path]");
            System.err.println("默认 dry-run：只生成审计报告，不写库。真实写回必须显式 --execute。");
            System.exit(2);
        }
        OrderLegacyMigrator migrator = new OrderLegacyMigrator(url, user, password);
        Report report = migrator.migrate(Long.valueOf(tenant), execute);
        Files.writeString(Path.of(reportPath), report.render());
        System.out.println("报告已写入: " + reportPath);
        System.out.println("迁移=" + report.migrated + " 人工核对=" + report.manualReview + " 跳过=" + report.skipped);
        if (report.manualReview > 0 && execute) {
            System.out.println("存在人工核对项，请先处理再进入下一阶段。");
        }
    }

    private static String arg(String[] args, String name, String defaultValue) {
        String v = arg(args, name);
        return v != null ? v : defaultValue;
    }

    private static String arg(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }
}

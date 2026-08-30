package com.blade.order.migration;

import com.blade.order.service.OrderCompatAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
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
 * 终审 P0-5 整改要点：
 * <ul>
 *   <li>execute 使用 TransactionTemplate 单事务：任一订单写失败整体回滚，不允许部分提交</li>
 *   <li>dry-run 与 execute 走同一套决策（decideOne），报告与写库结果一致</li>
   * <li>refund_amount &gt; 0：语义不可拆分（销售退回 vs 现金退款），进入人工核对，不自动决定</li>
   * <li>write_off_amount &gt; 0：生成可复算的 WRITE_OFF 期初流水（金额+订单+来源 MIGRATION）</li>
   * <li>paid_amount &gt; 0：生成 MIGRATION_OPENING 期初流水</li>
   * <li>写库后经 OrderCompatAdapter 投影旧 status/payment_status，新旧字段始终一致</li>
   * <li>旧取消订单（status=6）映射为 CANCELLED，不再被当成已完成</li>
   * <li>出库证据订单的收款状态按金额公式推导，不盲目标记 SETTLED</li>
 * </ul>
 * 全部 SQL 集中在 {@code resources/migration/order-legacy-migrator-sql.json}，JdbcTemplate 参数绑定。
 */
public class OrderLegacyMigrator {

    private final DriverManagerDataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate txTemplate;
    private final OrderCompatAdapter compatAdapter = new OrderCompatAdapter();
    private final Map<String, String> sql;

    /** 故障注入点（仅供测试）：在事务内第 N 次写操作前抛异常，验证回滚 */
    private Runnable faultInjector;

    public OrderLegacyMigrator(String jdbcUrl, String user, String password) throws Exception {
        this.dataSource = new DriverManagerDataSource(jdbcUrl, user, password);
        this.jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        this.txTemplate = new TransactionTemplate(txManager);
        try (InputStream in = OrderLegacyMigrator.class.getResourceAsStream("/migration/order-legacy-migrator-sql.json")) {
            if (in == null) {
                throw new IllegalStateException("缺少 SQL 语句资源文件 order-legacy-migrator-sql.json");
            }
            this.sql = new ObjectMapper().readValue(in, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        }
    }

    /** 仅供测试注入故障 */
    void setFaultInjector(Runnable faultInjector) {
        this.faultInjector = faultInjector;
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

    /** 迁移决策（dry-run 与 execute 共用） */
    record Decision(String decision, String toFulfillment, String toMode, String toCollection,
                    String settlementMethod, String evidence, String note) {
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
     * @param execute  false = dry-run（只算不写）；true = 真实写回（单事务，任一失败整体回滚）
     */
    public Report migrate(Long tenantId, boolean execute) {
        if (tenantId == null) {
            throw new IllegalArgumentException("必须显式指定租户（--tenant），拒绝空租户迁移");
        }
        Report report = new Report();
        List<LegacyOrder> orders = loadOrders(tenantId);
        report.totalOrders = orders.size();

        // dry-run 与 execute 使用同一决策
        List<LegacyOrder> toMigrate = new ArrayList<>();
        for (LegacyOrder o : orders) {
            Decision d = decideOne(o);
            applyDecisionToReport(o, d, report);
            if ("MIGRATED".equals(d.decision()) && execute) {
                toMigrate.add(o);
            }
        }

        if (execute && !toMigrate.isEmpty()) {
            // 单事务：任一订单写失败整体回滚，无部分提交
            txTemplate.executeWithoutResult(status -> {
                for (LegacyOrder o : toMigrate) {
                    if (faultInjector != null) {
                        faultInjector.run();
                    }
                    Decision d = decideOne(o);
                    writeOne(o);
                }
                // 事务内重算不变量；不平则抛异常触发回滚
                computeInvariants(tenantId, report);
                if (report.sumTotalAfter.compareTo(report.sumTotalBefore) != 0) {
                    throw new IllegalStateException("金额不变量被破坏（total_amount 总量变化），整体回滚");
                }
            });
        } else {
            computeInvariants(tenantId, report);
        }
        return report;
    }

    // ==================== 决策（dry-run 与 execute 共用） ====================

    Decision decideOne(LegacyOrder o) {
        if (notBlank(o.fulfillmentStatus)) {
            return new Decision("SKIPPED_ALREADY_MIGRATED", o.fulfillmentStatus, o.fulfillmentMode,
                    o.collectionStatus, "", "已迁移", "");
        }

        String evidence = "status=" + o.status
                + ",paid=" + o.paidAmount
                + ",writeOff=" + o.writeOffAmount
                + ",refund=" + o.refundAmount
                + ",total=" + o.totalAmount
                + ",plans=" + o.planCount
                + ",delivered=" + o.hasDelivery
                + ",isDelivered=" + o.isDelivered;

        // 旧退货语义：不自动决定
        if (o.status != null && (o.status == 7 || o.status == 8)) {
            return new Decision("MANUAL_REVIEW", "", "", "", "", evidence, "旧退货语义(status=7/8)不自动映射");
        }

        // refund_amount 语义不可拆分（销售退回 vs 现金退款）：进人工核对
        if (o.refundAmount != null && o.refundAmount.signum() > 0) {
            return new Decision("MANUAL_REVIEW", "", "", "", "", evidence,
                    "refund_amount>0 且无法拆分销售退回/现金退款，需人工核对");
        }

        boolean hasDeliveryEvidence = o.hasDelivery || (o.isDelivered != null && o.isDelivered == 1);
        boolean hasPlans = o.planCount > 0;
        boolean paidPositive = o.paidAmount != null && o.paidAmount.signum() > 0;

        // 证据冲突：存在出库事实但无收款证据
        if (hasDeliveryEvidence && !paidPositive) {
            return new Decision("MANUAL_REVIEW", "", "", "", "", evidence, "存在出库证据但无收款证据，冲突");
        }

        // 收款状态按金额公式推导（不盲目标记 SETTLED）
        BigDecimal netReceivable = nz(o.totalAmount).subtract(nz(o.refundAmount)).subtract(nz(o.writeOffAmount)).max(BigDecimal.ZERO);
        boolean settled = (o.paymentStatus != null && o.paymentStatus == 2)
                || (paidPositive && netReceivable.compareTo(nz(o.paidAmount)) <= 0);
        String toCollection = settled ? "SETTLED" : (paidPositive ? "PARTIAL" : "UNPAID");

        // 旧取消订单：明确映射 CANCELLED
        if (o.status != null && o.status == 6) {
            return new Decision("MIGRATED", "CANCELLED", "UNDECIDED", toCollection, "", evidence,
                    "旧取消订单映射 CANCELLED，收款状态按金额公式推导");
        }

        if (hasDeliveryEvidence) {
            return new Decision("MIGRATED", "SHIPPED", "STOCK_LINKED", toCollection,
                    settled ? "MIGRATION_CONFIRMED" : "", evidence,
                    "按实际出库证据推导，收款状态按金额公式推导");
        }
        if (hasPlans) {
            return new Decision("MIGRATED", "ALLOCATING", "STOCK_LINKED", toCollection,
                    settled ? "MIGRATION_CONFIRMED" : "", evidence, "存在配货计划，按计划状态推导");
        }
        if (settled) {
            return new Decision("MIGRATED", "COMPLETED", "RECORD_ONLY", "SETTLED", "MIGRATION_CONFIRMED",
                    evidence, "已结清且无配货/出库证据 → 仅记录完成");
        }
        return new Decision("MIGRATED", "CONFIRMED", "UNDECIDED", toCollection, "", evidence,
                paidPositive ? "保留尾款，等待后续收款" : "保留未收款状态");
    }

    private void applyDecisionToReport(LegacyOrder o, Decision d, Report report) {
        report.results.add(new OrderResult(o.id, o.orderNo, d.decision(),
                str(o.status), d.toFulfillment(), d.toMode(), d.toCollection(), d.settlementMethod(),
                d.evidence(), d.note()));
        switch (d.decision()) {
            case "MIGRATED" -> report.migrated++;
            case "MANUAL_REVIEW" -> report.manualReview++;
            case "SKIPPED_ALREADY_MIGRATED" -> report.skipped++;
            default -> throw new IllegalStateException("未知决策: " + d.decision());
        }
    }

    // ==================== 事务内写库（经适配器投影旧字段） ====================

    private void writeOne(LegacyOrder o) {
        Decision d = decideOne(o);
        if (!"MIGRATED".equals(d.decision())) {
            return; // 人工核对/跳过不写库
        }
        BigDecimal gross = nz(o.paidAmount);
        BigDecimal netReceivable = nz(o.totalAmount).subtract(nz(o.refundAmount)).subtract(nz(o.writeOffAmount)).max(BigDecimal.ZERO);
        BigDecimal balance = netReceivable.subtract(gross).max(BigDecimal.ZERO);
        Timestamp settledAt = "SETTLED".equals(d.toCollection())
                ? Timestamp.valueOf(o.payTime != null ? o.payTime : o.createTime)
                : null;

        // 1. 写新字段
        jdbc.update(sql.get("migrateOrder"), ps -> {
            ps.setString(1, d.toFulfillment());
            ps.setString(2, d.toMode());
            ps.setString(3, d.toCollection());
            ps.setString(4, d.settlementMethod().isBlank() ? null : d.settlementMethod());
            ps.setTimestamp(5, settledAt);
            ps.setBigDecimal(6, gross);
            ps.setBigDecimal(7, gross);
            ps.setBigDecimal(8, balance);
            ps.setLong(9, o.id);
            ps.setLong(10, o.tenantId);
        });

        // 2. 期初流水：MIGRATION_OPENING（实收）+ WRITE_OFF（短款核销），幂等不重复
        if (gross.signum() > 0 && countRecord(o.id, o.tenantId, "MIGRATION_OPENING") == 0) {
            Timestamp occurredAt = Timestamp.valueOf(o.payTime != null ? o.payTime : o.createTime);
            jdbc.update(sql.get("insertMigrationOpening"), ps -> {
                ps.setLong(1, o.tenantId);
                ps.setLong(2, o.id);
                ps.setBigDecimal(3, o.paidAmount);
                ps.setTimestamp(4, occurredAt);
            });
        }
        if (nz(o.writeOffAmount).signum() > 0 && countRecord(o.id, o.tenantId, "WRITE_OFF") == 0) {
            Timestamp occurredAt = Timestamp.valueOf(o.payTime != null ? o.payTime : o.createTime);
            jdbc.update(sql.get("insertWriteOffOpening"), ps -> {
                ps.setLong(1, o.tenantId);
                ps.setLong(2, o.id);
                ps.setBigDecimal(3, o.writeOffAmount);
                ps.setTimestamp(4, occurredAt);
            });
        }

        // 3. 状态迁移日志
        jdbc.update(sql.get("insertTransitionLog"), ps -> {
            ps.setLong(1, o.tenantId);
            ps.setLong(2, o.id);
            ps.setString(3, d.toFulfillment());
            ps.setString(4, d.toCollection());
            ps.setString(5, d.toMode());
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
        });

        // 4. 旧字段投影：经唯一适配器，新旧一致
        Integer legacyStatus = compatAdapter.projectLegacyStatus(
                com.blade.order.enums.FulfillmentStatus.valueOf(d.toFulfillment()));
        Integer legacyPayment = compatAdapter.projectLegacyPaymentStatus(
                com.blade.order.enums.CollectionStatus.valueOf(d.toCollection()));
        jdbc.update(sql.get("projectLegacy"), ps -> {
            ps.setInt(1, legacyStatus);
            ps.setInt(2, legacyPayment);
            ps.setLong(3, o.id);
            ps.setLong(4, o.tenantId);
        });
    }

    private int countRecord(long orderId, long tenantId, String recordType) {
        Integer n = jdbc.queryForObject(sql.get("countRecord"), Integer.class, orderId, tenantId, recordType);
        return n == null ? 0 : n;
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
                    return o;
                });
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

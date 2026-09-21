# Series F 本地交付报告：历史档口回填预演、初始授权建议包与本地回归/性能预演

> 分支：`feature/outlet-access-control`　基线：`d26d80e`（E3 整改完成）
> 执行：DeepSeek（`[dsh]`）
> 范围：仅 Series F **本地可执行**的发布准备；不 push/deploy/NAS/生产，不连接生产库。
> 依据：[20-OUTLET_ACCESS_CONTROL_DESIGN.md](../../20-OUTLET_ACCESS_CONTROL_DESIGN.md)、ROM/SOW、[生产副本 checklist](./2026-09-21-outlet-production-release-checklist.md)。

---

## 0. 提交

| commit | 内容 |
|---|---|
| `8213dea` | `feat(outlet): add Series F historical outlet backfill dry-run tooling [dsh]`（回填工具 + 授权建议包 + EXPLAIN 脚本） |
| `8189078` | `test(outlet): add Series F backfill and scope matrix audit tests [dsh]`（18 例测试） |
| 本文档所在 commit | `docs(outlet): record Series F local release preparation [dsh]` |

## 1. 明确范围（本地 vs 外部）

本地完成：
- 历史 `source_shop → source_outlet_id` 回填工具（dry-run/apply、显式映射、校验、报告、对账、幂等、自动测试）。
- 初始用户-档口授权建议/确认包（只读 SQL + CSV 模板，恒 `NEEDS_REVIEW`）。
- TEST-OUTLET-004 本地矩阵审计 + 索引核对 + 查询计划预演脚本。
- 文档收口与生产副本/备份/灰度/回滚 checklist（只写不执行）。

**未做（待外部执行）**：
- DATA-OUTLET-002 真实生产副本预演与数据对账。
- DATA-OUTLET-003 人工确认与实际授权写入。
- DEPLOY-OUTLET-001 备份/灰度/切写/回滚。
- 生产规模性能结论、`btn:order:viewAll` 下线、`source_outlet_id NOT NULL`（Series G）。

## 2. 历史档口回填工具（DATA-OUTLET-002 本地）

### 2.1 契约

| 组件 | 说明 |
|---|---|
| `OutletBackfillMapping` | 显式 CSV 解析与静态校验；表头固定 `tenant_id,legacy_source_shop,outlet_code,decision,reason` |
| `OutletBackfillMappingRow` | 单条决策：`MAP` / `SKIP` / `REVIEW` |
| `OutletBackfillService` | `preview`（dry-run，默认）与 `apply`（`@Transactional`） |
| `OutletBackfillSafetyGate` | apply 四重闸门 + 生产/NAS 特征拒绝 |
| `OutletBackfillCli` | `ApplicationRunner`，仅当显式传入映射文件时运行并输出 JSON 报告 |
| `OutletBackfillReport` | 计数、前后对账快照、warnings/errors |

### 2.2 严格校验

- 纯数字 `legacy_source_shop` 一律拒绝自动映射；`MAP` 必须有 `outlet_code`；`SKIP`/`REVIEW` 必须有 `reason` 且不得带 `outlet_code`。
- 同租户同 `legacy_source_shop` 重复即报错；CSV 行租户必须与请求租户一致。
- 档口必须同租户、未删除且启用；未知/跨租户/已删除/禁用档口在写入前整体拒绝。
- 疑似批次/编号（`source_shop` 等于 `source_doc_no` 批次前缀或 `source_batch_no`）在行级再次排除，即使被人工 `MAP` 也不会更新。

### 2.3 只更新 `source_outlet_id`

- 订单：`sale_order.source_outlet_id`；草稿：`order_draft.source_outlet_id`。
- 仅 `deleted=0`、`source_outlet_id IS NULL` 的行；确认草稿（`confirmed_order_id` 非空）跳过并计数。
- 已有 `source_outlet_id` 且与映射不同 → 冲突，不覆盖。
- `source_shop` 与其它列绝不改写。

### 2.4 对账与幂等

- apply 在同一事务内取前后快照：行数、`total_amount`、`gross_received_amount`、`net_received_amount`、
  `cash_refund_amount`、`sales_return_amount`、`write_off_amount`、状态分布、`source_shop` 分布、
  订单/草稿明细数、文件绑定数；除 `source_outlet_id` 空/非空计数外必须完全一致，否则抛错回滚。
- 重复执行：第二次 `ordersUpdated=0/draftsUpdated=0`，`alreadyApplied` 增加；`reconciliationConsistent=true`。

### 2.5 安全闸门（只在生产副本）

- 默认 `blade.outlet.backfill.apply=false`（dry-run）。
- apply 需同时：显式映射文件、显式租户、`copy-environment-ack=true`、`apply=true`。
- 连接串/库名命中 `prod`/`production`/`nas` 直接 403。
- 命令示例（**不在本分支执行**）：

```bash
# dry-run
java -jar blade-backend.jar \
  --blade.outlet.backfill.mapping-file=/secure/path/outlet-mapping.csv \
  --blade.outlet.backfill.tenant-id=<tenant>

# apply（仅生产副本）
java -jar blade-backend.jar \
  --blade.outlet.backfill.mapping-file=/secure/path/outlet-mapping.csv \
  --blade.outlet.backfill.tenant-id=<tenant> \
  --blade.outlet.backfill.apply=true \
  --blade.outlet.backfill.copy-environment-ack=true
```

模板：`scripts/outlet-backfill-plan-template.csv`。

### 2.6 自动测试

- `OutletBackfillMappingTest`（7）：解析/注释/表头/纯数字/MAP 缺码/非 MAP 带码/重复/非法租户与 decision。
- `OutletBackfillSafetyGateTest`（3）：四重闸门缺失、生产/NAS 特征拒绝、合法副本放行。
- `OutletBackfillServiceIntegrationTest`（5，真实库事务回滚）：dry-run 不写库、apply 只改 ID 且幂等、
  冲突不覆盖、确认草稿跳过、疑似批次跳过、未知/禁用/删除/跨租户整体拒绝、对账一致。

## 3. 初始用户-档口授权建议/确认包（DATA-OUTLET-003 本地）

- 只读报告：`scripts/outlet-user-outlet-authorization-suggestions.sql`（只 SELECT）输出
  `tenant_id,user_id,username,nickname,role_codes,has_outlet_all,has_people_all,has_unassigned,
  existing_outlet_codes,existing_default_outlet_code,suggested_outlet_scope,suggested_people_scope,
  suggested_default_outlet_code,decision,reason`。
- 确认模板：`scripts/outlet-user-outlet-authorization-template.csv`
  （`outlet_scope`/`people_scope`/`default_outlet_code`/`outlet_codes`/`decision`/`reason`）。
- `decision` 恒为 `NEEDS_REVIEW`；无法从角色/既有绑定推断一律 `NEEDS_REVIEW`。
- **绝不自动授权**：建议包只读，实际授权由人工在控制台逐项确认；DATA-OUTLET-003 保持待人工确认。

## 4. TEST-OUTLET-004 本地矩阵与性能预演

- `OutletScopeCrossResourceAuditTest`（2）：档口 × 人员 × user/agent × 订单谓词/草稿谓词/统计读范围/可用档口选项，
  覆盖 single/multi/all/none/unassigned 与 Agent ASSIGNED/ALL/NONE。
- `OutletIndexCoverageTest`（1）：核对 `sale_order`、`order_draft`、`sales_outlet`、`sys_user_outlet`、
  `agent_key_outlet` 的档口范围索引存在。
- `scripts/outlet-scope-explain.sql`：single/multi/all/none/unassigned 的 EXPLAIN 预演 + 索引清单，只读、不做脆弱计划断言。
- 索引核对发现：`sale_order.salesman_id` 无独立索引，`peopleScope=SELF` 的超大规模过滤可能范围扫描；
  记录为 Series G 评估项，本分支**未改 schema**。
- 生产规模性能结论**保持 pending**。

## 5. 验证结果

- 全量后端 `mvn test`：**769/769**，Failures 0 / Errors 0 / Skipped 0
  （E3 基线 751，Series F 本地新增 18 例）。
- PC 构建 `npm run build`：通过（仅既有 chunk 体积告警）。
- 关键 E2E（本地后端 + Vite）：见 §5.1；环境限制如实记录。
- `git diff --check`：无输出；workspace clean（工具隐藏目录已本地排除，未提交）。

### 5.1 E2E

| spec | 结果 |
|---|---|
| `e2e/e2e-agent-key-outlet-scope.spec.ts`（mock） | 2 passed |
| `e2e-outlet.spec.ts`、`e2e-order-outlet.spec.ts`、`e2e-analytics-outlet.spec.ts`、`e2e-file-outlet-filter.spec.ts`、`e2e-quick-order-draft.spec.ts`、`e2e-manual-draft-confirm.spec.ts` | 14 passed |

说明：`e2e-file-upload.spec.ts` 依赖本地不存在的 `super_admin` 租户（历史环境限制），本机未运行；不在本分支修改。

## 6. 生产副本 checklist

见 [2026-09-21-outlet-production-release-checklist.md](./2026-09-21-outlet-production-release-checklist.md)：备份 → 事实审计 → dry-run → 副本 apply → 对账 → 用户授权 → 灰度（先双读后切写）→ 回滚。**全部只写不执行。**

## 7. 剩余外部事项

- DATA-OUTLET-002：真实生产副本预演、异常清单确认、对账签字。
- DATA-OUTLET-003：人工确认并授权。
- DEPLOY-OUTLET-001：备份、迁移预演、灰度、验收、观察、回滚。
- TEST-OUTLET-004：生产规模查询计划与性能结论。
- Series G：`btn:order:viewAll` 下线评估、`source_outlet_id NOT NULL` 评估、`salesman_id` 索引评估。
- 本分支不删除 `btn:order:viewAll`、不加 NOT NULL、不虚报任何生产执行。

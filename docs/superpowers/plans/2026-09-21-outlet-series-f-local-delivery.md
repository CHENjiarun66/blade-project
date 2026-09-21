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
| `2948ab7` | `docs(outlet): record Series F local release preparation [dsh]` |
| `35ebcc0` | `fix(outlet): fail-closed copy identity gate and complete backfill reports [dsh]`（Codex 终审整改 P0-1~P0-4/P1） |
| `ecac2e5` | `test(outlet): add Series F remediation counterexamples [dsh]`（8 例反例/契约测试） |
| 本文档所在 commit | `docs(outlet): record Series F Codex remediation [dsh]` |

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
- 另按 `id + source_shop` 生成稳定 SHA-256 摘要（`orderIdShopDigest`/`draftIdShopDigest`），
  即使分布相同但值被交换也会被识别。
- 重复执行：第二次 `ordersUpdated=0/draftsUpdated=0`，`alreadyApplied` 增加；`reconciliationConsistent=true`。
- UPDATE 同时带 `tenant_id=?`、`source_outlet_id IS NULL`、分块 `id IN (...)`；若候选选取后被并发填入，
  更新行数少于候选数，差额计入并发冲突/跳过并告警，绝不覆盖。

### 2.5 安全闸门（fail-closed 正向副本身份，只在生产副本）

- 默认 `blade.outlet.backfill.apply=false`（dry-run）。
- apply 必须同时满足（正向证明，不能只靠 ack）：
  1. 显式映射文件、显式租户、`apply=true`；
  2. 显式 `expected-database-name`，且与实际 `SELECT DATABASE()` **完全一致**；
  3. 实际库名匹配副本命名模式 `*_copy` / `*_rehearsal` / `*_staging` / `*_test`（正则 `(?i)^[a-z0-9_]+_(copy|rehearsal|staging|test)$`）；`blade` 等生产常用名直接拒绝；
  4. 显式 `copy-environment-ack=true`；
  5. 显式 `report-dir` 且目录可创建/可写；
  6. 第二层黑名单：连接串或库名命中 `prod`/`production`/`nas` 直接 403。
- 写入入口 `OutletBackfillService.apply(OutletBackfillApproval, rows)`：`OutletBackfillApproval` 构造器包内可见，
  只有 `OutletBackfillSafetyGate.approve(...)` 能签发，外部包无法绕过闸门调用写入。
- 命令示例（**不在本分支执行**，一次性任务加 `--spring.main.web-application-type=none`）：

```bash
# dry-run（可选 report-dir，产出 JSON + Markdown）
java -jar blade-backend.jar \
  --spring.main.web-application-type=none \
  --blade.outlet.backfill.mapping-file=/secure/path/outlet-mapping.csv \
  --blade.outlet.backfill.tenant-id=<tenant> \
  --blade.outlet.backfill.report-dir=/secure/reports

# apply（仅生产副本）
java -jar blade-backend.jar \
  --spring.main.web-application-type=none \
  --blade.outlet.backfill.mapping-file=/secure/path/outlet-mapping.csv \
  --blade.outlet.backfill.tenant-id=<tenant> \
  --blade.outlet.backfill.report-dir=/secure/reports \
  --blade.outlet.backfill.apply=true \
  --blade.outlet.backfill.expected-database-name=blade_rehearsal \
  --blade.outlet.backfill.copy-environment-ack=true
```

模板：`scripts/outlet-backfill-plan-template.csv`。

### 2.6 报告

- `OutletBackfillReport` 同时含计数与结构化 `groups`（按 `sale_order`/`order_draft` 分开）：
  `BLANK_NULL`、`UNMAPPED`、`MAP_CANDIDATE`、`SKIP`、`REVIEW`、`SUSPECT`、`ALREADY_APPLIED`、
  `CONFLICT`、`CONFIRMED_DRAFT_SKIPPED`，每条含 value/count/sampleRefs（订单号或草稿 externalRefNo，最多 5 条）。
- 样例严格限定 `tenant_id`；报告不含 JDBC/凭据/密码。
- 指定 `report-dir` 时同时输出稳定命名（含模式+租户+时间戳，已存在则追加序号，不静默覆盖）的 JSON 与 Markdown。

### 2.7 自动测试

- `OutletBackfillMappingTest`（7）：解析/注释/表头/纯数字/MAP 缺码/非 MAP 带码/重复/非法租户与 decision。
- `OutletBackfillSafetyGateTest`（6）：基础闸门缺失、`jdbc:mysql://mysql:3306/blade`+`blade`+ack 反例、expected 名称不匹配、非 copy 命名、生产/NAS 黑名单第二层、合法 rehearsal 放行。
- `OutletBackfillServiceIntegrationTest`（6，真实库事务回滚）：dry-run 分类/样例不写库、apply 只改 ID 且幂等、冲突不覆盖、确认草稿跳过、疑似批次跳过、未知/禁用/删除/跨租户整体拒绝、UPDATE tenant+NULL 谓词、JSON+Markdown 输出。
- `OutletBackfillServiceUpdateTest`（2）：UPDATE 带 tenant/null 条件 + 1200 ID 分 3 块；空列表不触库。
- `OutletOpsSqlContractTest`（2）：运维 SQL 无写/DDL 关键字；`@tenant_id`/`:tenant_id` 闸门与关键语句 tenant 条件。

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

- 全量后端 `mvn test`：**777/777**，Failures 0 / Errors 0 / Skipped 0
  （E3 基线 751，Series F 本地新增 26 例，其中 Codex 终审整改新增 8 例）。
- PC 构建 `npm run build`：沿用上一轮通过（本轮纯后端工具/脚本/文档，无前端改动，未重复构建）。
- 关键 E2E（本地后端 + Vite）：沿用上一轮 16 passed（本轮无前端改动）；环境限制如实记录。
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

## 8. Codex 终审整改（P0-1 ~ P0-4 / P1）

| 编号 | 问题 | 修复 | 测试 |
|---|---|---|---|
| P0-1 | 安全闸门只靠 ack + 黑名单，真实生产 `jdbc:mysql://mysql:3306/blade` 不命中 | fail-closed 正向副本身份：显式 `expected-database-name` 与实际 `SELECT DATABASE()` 完全一致；实际库名匹配 `*_copy/_rehearsal/_staging/_test`；显式可写 `report-dir`；黑名单降为第二层；`OutletBackfillApproval` 构造器包内可见，仅 gate 可签发，写入入口 `apply(approval, rows)`，事务代理保持生效 | `OutletBackfillSafetyGateTest`（含 `jdbc:mysql://mysql:3306/blade`+`blade`+ack 必须拒绝、expected 不匹配拒绝、合法 rehearsal 放行）、`OutletBackfillServiceIntegrationTest.apply*` |
| P0-2 | 报告只有计数，无空值/未映射/疑似/冲突/样例；无 JSON/Markdown | `OutletBackfillReport.groups` 按 `sale_order`/`order_draft` 分类（`BLANK_NULL/UNMAPPED/MAP_CANDIDATE/SKIP/REVIEW/SUSPECT/ALREADY_APPLIED/CONFLICT/CONFIRMED_DRAFT_SKIPPED`），含 value/count/sampleRefs（≤5，严格 tenant）；显式 `report-dir` 输出时间戳 JSON+Markdown，不静默覆盖；apply 强制 report-dir 可写 | `OutletBackfillServiceIntegrationTest.previewReportsCandidatesGroupsAndSamplesWithoutWriting`、`applyUpdatesOnlySourceOutletIdWritesReportsAndIsIdempotent`；`OutletBackfillMappingTest` |
| P0-3 | UPDATE 未含 tenant/null、无分块；冲突告警用累计计数；对账仅分布 | UPDATE `SET source_outlet_id=? WHERE tenant_id=? AND source_outlet_id IS NULL AND id IN (...)`，500/块；候选后并发填入按差额计入 conflict/concurrent 并告警；冲突判定改当前行局部计数；新增 `id + source_shop` SHA-256 对账摘要 | `OutletBackfillServiceUpdateTest`、`OutletBackfillServiceIntegrationTest.updateHelperEnforcesTenantAndNullPredicate` |
| P0-4 | 运维 SQL 可返回全租户 | `outlet-source-shop-audit.sql`/`outlet-user-outlet-authorization-suggestions.sql` 改为 `SET @tenant_id = NULL` fail-closed，所有语句/CTE/JOIN 按同 tenant；`outlet-scope-explain.sql` 保持显式 `:tenant_id` | `OutletOpsSqlContractTest`（只读关键字 + tenant 闸门 + 关键语句 tenant 条件） |
| P1 | CLI 一次性任务退出、文档口径 | CLI 命令示范加 `--spring.main.web-application-type=none`；本报告新增本章节；16/17/19 Agent 手册已核对为当前版本（本轮无 diff）；STATUS 以 03-TASKS/本报告为权威，`🚧 本地完成/待外部` 不代表生产完成 | 文档复核 |

验证（整改）：全量后端 **777/777**；`git diff --check` 无输出；workspace clean。前端本轮无改动，沿用上一轮构建/E2E 结果。

## 9. 第二批A：回填报告审计证据增强（代码完成、生产副本演练与人工确认未完成）

状态口径：**代码与自动化测试完成；真实生产副本 apply、异常清单确认、对账签字均未执行，待外部人工。**

| 审计字段 | 实现 | 测试 |
|---|---|---|
| 逐条 mapping | `OutletBackfillReport.MappingDecision`：legacy_source_shop/outlet_code/decision/reason + 该值 orders/drafts 候选数与实际更新数，可逐条重建结果 | `OutletBackfillServiceIntegrationTest.applyUpdatesOnlySourceOutletIdWritesReportsAndIsIdempotent` |
| operator | `blade.outlet.backfill.operator`；apply 在安全闸门强制非空，preview 可缺省并在报告中明确记为 `PREVIEW` | `OutletBackfillSafetyGateTest.rejectsWhenAnyBaseGateMissing`（operator 空拒绝）、`...previewReportsAuditEvidenceAndMarksPreviewOperator` |
| 运行时间 | `startedAt`/`finishedAt`（ISO 本地时间） | 同上 |
| 预期/实际副本库名 | `expectedDatabaseName`（apply 即闸门校验值）/`actualDatabaseName`（`SELECT DATABASE()`）；报告不含 JDBC/密码/凭据 | 同上（断言 JSON 不含 `jdbc:`/`password`） |
| 映射文件 SHA-256 | 对实际映射文件字节做 SHA-256；无文件（程序化 preview）为 null，不伪造 | 同上 |
| apply 前后 source_outlet_id 赋值摘要 | 新增 `orderIdOutletDigest`/`draftIdOutletDigest`（`id + source_outlet_id` SHA-256），与 `orderIdShopDigest`/`draftIdShopDigest` 并存；对账仍严格校验 source_shop/金额/状态/明细不变，仅排除按设计变化的 outlet 赋值指标 | `applyUpdatesOnlySourceOutletIdWritesReportsAndIsIdempotent` |

安全不变量保持：dry-run 默认、apply 正向副本库名门（`*_copy/_rehearsal/_staging/_test` + 期望库名完全一致 + 可写 report-dir + operator）、只改 `source_outlet_id`、不改 `source_shop`、租户隔离、对账、幂等。

验证：`mvn -Dtest='OutletBackfill*Test' test` 22/22 通过（Mapping 7、SafetyGate 6、ServiceIntegration 7、ServiceUpdate 2）。

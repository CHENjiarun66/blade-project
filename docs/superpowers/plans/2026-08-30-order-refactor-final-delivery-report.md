# 订单大重构连续实施 — 最终交付报告

> 日期：2026-08-30　|　执行 Agent：ZCode　|　最终审核：Codex
>
> **最终状态：`WAITING_CODEX_FINAL_REVIEW`**
>
> 交付范围：`docs/superpowers/plans/2026-08-30-order-refactor-zcode-long-run-task.md` 系列 A~G 全部内容。

## 一、基线与最终提交

| 项 | 值 |
|---|---|
| 任务基线 | `1594a8f`（launch commit；实施基线 `d800ec4`） |
| 最终 tip | `28bf93f`（`feature/order-lifecycle-finance-refactor`，与 origin 同步 0/0） |
| 系列提交 | A=`b7e5f05`　B=`10fde95`　C=`e4e2c93`　D=`618c501`+`ce2afdc`　E=`47551ff`　F=`2abef36`　G=`8770f2b`+`28bf93f` |
| 变更规模 | 80 文件，+6730 / −1575 行 |
| 工作区 | `git status` 无未提交变更；`git diff --check` 无输出 |

## 二、按系列交付内容

| 系列 | commit | 完成内容 |
|---|---|---|
| A | `b7e5f05` | V51（生命周期/收款/履约/结清/快照/版本列 + `order_financial_record` + `order_state_transition_log` + 幂等/冲销唯一键 + CHECK + 租户索引）、V52（8 个动作权限 + OWNER/ADMIN 全量 + 同租户 JOIN 幂等赋权）、实体/枚举/Mapper、schema 测试；隔离库空库 Flyway V1→V52 连续升级成功 |
| B | `10fde95` | `OrderActionService` 11 动作、`OrderFinanceSnapshotService`（期初固化/流水聚合/排除已冲销/单语句快照）、`OrderCompatAdapter` 唯一映射点；旧接口全部委托、删除裸 `updateStatus` 与第二套收款公式；草稿定金写为首笔 RECEIPT；软删除；`allowedActions`；`legacyUnmigrated` 展示回退 |
| C | `e4e2c93` | 占位拆分服务（守恒+`PLACEHOLDER_SPLIT` 审计）+ 拆分端点；`startAllocation`/`shipOrder` 占位阻断；出库单前置（STOCK_LINKED）；`/api/inventory/out-by-plan` 410 收口；出库单号 Redis 计数器；配货全表加载改批量 |
| D | `618c501`+`ce2afdc` | `packages/types` + admin API 新契约；详情金额/结清区 + 财务流水表 + 履约选择 + 现金退款 + 占位拆分引导；`new.vue` 不再提交数字状态；导出新列 + 10000 行显式拒绝；客户订单 VO 新字段 |
| E | `47551ff` | `OrderFactsService`（版本化口径）；Dashboard/Analytics/Customer/WhatsApp orderFacts+contextStamp 全部切换（取消订单不进经营订单额；RECORD_ONLY 计销售不计库存周转；历史行 refund_amount 保守回退保持旧数字）；`CustomerStatsCacheService` 订单/财务动作后失效偏好缓存 |
| F | `2abef36` | 移动端按履约状态字符串筛选、详情收款/结清/履约 Chip、按钮按 `allowedActions` 展示、旧响应兼容回退、不提交数字状态；构建通过 + 手工冒烟清单（`docs/testing/2026-08-30-mobile-order-lifecycle-smoke.md`） |
| G | `8770f2b`+`28bf93f` | 离线迁移工具 `OrderLegacyMigrator`（dry-run 默认、`--execute` 显式、空租户拒绝、7/8 与证据冲突进人工核对、幂等重放、SQL 独立资源文件）；迁移预演测试；TASKS/CHANGELOG/SESSION_CONTEXT/STATUS 同步 |

关键源文件：`order/service/OrderActionService.java`、`OrderFinanceSnapshotService.java`、`OrderCompatAdapter.java`、`OrderFactsService.java`、`OrderPlaceholderSplitService.java`、`order/migration/OrderLegacyMigrator.java`、`customer/service/CustomerStatsCacheService.java`、`db/migration/V51__*.sql`、`V52__*.sql`。

## 三、V51/V52 DDL、兼容与回滚影响

- **V51**（§13 契约落地）：`sale_order` 增列 `fulfillment_status`(NULL)/`collection_status`(NULL)/`fulfillment_mode`(默认 UNDECIDED)/决策人时间/`settled_at`/`settlement_method`/五个金额快照/`version`；`chk_so_snapshots_nonnegative`；3 个租户优先索引；两张新表含 `uk_ofr_idempotency(tenant,idem)`、`uk_ofr_reversal(tenant,reversed_id)`、`chk_ofr_amount_positive`、`chk_ofr_reversal_shape`。**不改 V1-V50、不自动推断历史状态**。
- **V52**：`btn:order:recordPayment/writeOff/refund/reverse/chooseFulfillment/allocate/export/viewFinance`；OWNER+ADMIN 全量，FINANCE=收款/核销/退款/冲销/导出/财务查看，SALES=收款/导出（viewFinance 待数据范围过滤落地后另发迁移），WAREHOUSE=履约/配货/发货；全部同租户 JOIN。**无迁移权限/端点**。
- **兼容**：旧 `status/payment_status` 仅由适配器事务内投影；旧接口路径/请求/响应字段全部保留；历史未迁移行开放财务动作（自动固化期初流水），履约动作拒绝；移动端旧版本读取不崩溃（后端契约测试守护）。
- **回滚**：加法迁移，应用可整体回退到 V50 基线镜像；迁移工具 dry-run 不写库；`--execute` 单事务（成功提交/异常回滚），配合发布前备份可恢复。

## 四、11 动作摘要（入口 → 权限 → 事务 → 幂等 → 转移）

| 动作 | 入口 | 权限 | 转移 |
|---|---|---|---|
| confirmDraft | `POST /order-drafts/{id}/confirm` | JWT（`btn:order:create` 语义） | 草稿→订单 CONFIRMED+UNPAID；定金→首笔 RECEIPT；幂等返回原单 |
| recordPayment | `POST /orders/confirm-payment`、`/orders/{id}/add-payment` | `btn:order:recordPayment` | 金额>0≤尾款→RECEIPT→快照重算 |
| settleWithWriteOff | `add-payment(markAsSettled)` | `btn:order:writeOff` | 需正数实收+原因→WRITE_OFF 余款→SETTLED |
| refundPayment | `POST /orders/{id}/refund` | `btn:order:refund` | 现金流出≤累计实收→REFUND→快照重算（可回退 SETTLED） |
| reverseFinancialRecord | `POST /orders/{id}/reverse-record` | `btn:order:reverse` | 禁冲销 REVERSAL；并发双冲销由 `uk_ofr_reversal` 只成功一次 |
| chooseFulfillmentMode | `POST /orders/{id}/fulfillment-mode` | `btn:order:chooseFulfillment` | 仅 SETTLED+UNDECIDED；RECORD_ONLY→COMPLETED，STOCK_LINKED→WAITING_ALLOCATION |
| startAllocation | `POST /orders/{id}/delivery-plan` | `btn:order:allocate` | 仅 STOCK_LINKED+待配货+无占位→ALLOCATING |
| confirmAllocation | `POST /orders/{id}/confirm-adjustment` | `btn:order:allocate` | ALLOCATING→READY_TO_SHIP |
| shipOrder | `POST /orders/{id}/deliver`、`/order-deliveries/{id}/confirm` | `btn:order:deliver` | READY_TO_SHIP→SHIPPED；唯一扣库存事务；已发货幂等 |
| completeOrder | `POST /orders/{id}/complete` | `btn:order:deliver` | SHIPPED→COMPLETED |
| cancelOrder | `POST /orders/{id}/cancel` | `btn:order:cancel` | CONFIRMED/WAITING_ALLOCATION/ALLOCATING→CANCELLED；清理未履约计划 |

每个动作同一事务：行锁订单 → 财务流水 → 快照重算 → 新状态 → 状态日志 → 适配器投影旧字段 → 单次落库。幂等键由 `uk_ofr_idempotency`/`uk_ostl_idempotency` 数据库唯一约束承接。

## 五、不变量与测试结果（逐条命令）

| 命令 | 结果 |
|---|---|
| `BLADE_DB_URL=…3307… mvn test`（blade-backend，含空库 V1→V52 Flyway） | **432 通过 / 0 失败 / 0 错误**（基线 400 → +32） |
| `cd packages/types && npm run build` | 通过（tsc 无错误） |
| `cd blade-admin && npm run build` | 通过（vite built） |
| `cd blade-mobile && npm run build` | 通过（PWA 产物生成） |
| `npx playwright install chromium` | **环境不支持**（mac13-arm64，见下节），改用系统 Chrome（config `channel:'chrome'`） |
| `E2E_API_BASE=http://127.0.0.1:8081/api E2E_PASSWORD=… npx playwright test -g "订单生命周期"` | **3/3 通过**（STOCK_LINKED 全链路、RECORD_ONLY 零库存、短款结清） |
| `git diff --check d800ec4..HEAD`、`git status --short --branch`、`git rev-list --left-right --count` | 无空白错误；无未提交变更；0/0 |

不变量断言（测试佐证）：
- **金额**：快照可由有效流水复算；超收拒绝；零金额订单必须人工结清；冲销后快照排除被冲销流水（`OrderServiceImplWriteOffTest` 20 项）。
- **库存**：`RECORD_ONLY` 全链路零库存流水（E2E 断言库存前后相等）；占位未拆分阻断配货/出库；出库仅经 `shipOrder`，out-by-plan 410（`InventoryOutByPlanSoftCouplingTest`）。
- **并发**：并发双冲销只能成功一次、并发收款不超尾款（`OrderFinancialConcurrencyTest`，真实隔离库）。
- **租户**：空租户显式拒绝 403；跨租户 404（`OrderTenantIsolationTest`）；V52 同租户 JOIN（`OrderV52PermissionSchemaTest`）。
- **统计一致性**：同一筛选范围 Dashboard=事实服务；客户消费额=经营订单实收和；WhatsApp SQL 同口径（`OrderFactConsistencyTest`，真实隔离库，9 类样本）。

## 六、尚未完成 / 环境受限事项（不阻塞代码审核，进入发布前必须补齐）

1. **V42 生产副本迁移预演**：本机无 V42 备份且禁止连接 NAS。迁移工具已用合成数据在隔离库完成 dry-run/execute/重放验证（`OrderMigrationRehearsalTest`）；生产副本预演归 BE-1052/Codex 发布阶段。
2. **`e2e-order-draft.spec.ts` 未在隔离库执行**：依赖开发库中的 Agent 草稿+文件中心夹具（不可移植）。替代验证：`OrderDraftConfirmFinanceTest`（草稿定金→首笔 RECEIPT→幂等确认）+ 2026-08-27 开发库同名规范 1/1 通过记录。
3. **`order-fullflow.spec.ts` 失败（预先存在，非本次引入）**：其 UI 步骤针对 Phase 2 快速录单改版前的旧页面（提交按钮/客户名占位/履约步骤均已过期）。本次新增的 `e2e-order-lifecycle.spec.ts` 以 API 关键路径覆盖同一生命周期并全部通过；fullflow 的页面改版重写建议单独立项。
4. **Playwright 浏览器**：仓库锁定的 Playwright 版本不支持 mac13-arm64 捆绑 Chromium；config 已加 `channel:'chrome'`（用系统 Chrome），不影响 CI。
5. 本机 Docker 未运行导致首轮 73 个上下文错误——已通过隔离测试库（`blade-mysql-test`，端口 3307 + `scripts/test-db-seed.sql`）解决；`application-test.yml` 增加 `BLADE_DB_URL` 环境变量覆盖（默认值不变）。

## 七、Git 状态

```text
$ git status --short --branch
## feature/order-lifecycle-finance-refactor...origin/feature/order-lifecycle-finance-refactor
（无未提交变更）

$ git rev-list --left-right --count HEAD...@{upstream}
0	0

最终 tip：28bf93f
```

---

> 本报告提交 Codex 做最终一次性审核（`1594a8f..28bf93f` 完整 Diff：代码、migration、测试、安全）。未经 Codex 最终通过与用户批准，不进入 release、NAS 发布或生产迁移。

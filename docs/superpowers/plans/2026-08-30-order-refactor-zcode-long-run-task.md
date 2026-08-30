# Z Code 长任务：订单生命周期、财务、履约与统计重构连续实施

> 日期：2026-08-30
>
> 执行 Agent：Z Code
>
> 架构与最终代码审核：Codex
>
> 实施基线：`feature/order-lifecycle-finance-refactor@d800ec4`
>
> 执行模式：连续实施、自测、分系列提交；全部完成后一次性交给 Codex 审核

## 一、任务目标与授权边界

Z Code 从基线 `d800ec4` 开始，连续完成原 `ORDER-SOW-1`～`ORDER-SOW-7` 的开发范围。中间不再等待 Codex 做文档确认或逐阶段放行；每个系列完成后自行测试、修复并提交，然后直接进入下一系列。

本任务授权：修改功能分支内的代码、测试、V51/V52 migration、接口文档和必要项目文档；在本地或隔离测试环境运行 migration 和测试；提交并推送 `origin/feature/order-lifecycle-finance-refactor`。

本任务不授权：连接 NAS/生产执行写操作、修改或删除生产数据、部署生产、修改 V1～V50、合并 `master/develop`、创建 release、改写 Git 历史、跳过失败测试。

只有以下情况才暂停并询问：发现 V51/V52 已被其他提交占用；完成需求必须破坏生产数据或旧 migration；业务结果存在两种会显著改变金额/库存的解释且现有设计无法裁定；测试所需的生产副本或凭证不存在。普通实现选择由 Z Code按本文和设计文档自行完成，不要为命名、文件组织或小型重构反复请求确认。

## 二、唯一实施契约

实施优先级从高到低：

1. 本长任务文档
2. `2026-08-30-order-refactor-sow0-baseline-audit.md` 第 13～17 节的整改后契约
3. `2026-08-30-order-refactor-agent-execution-board.md` 的字段、兼容和生产安全规则
4. `14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md`
5. `15-ORDER_FINANCE_ANALYTICS_DESIGN.md`
6. `06-ORDER_INVENTORY_DESIGN.md`

旧 CR-0 的 `CHANGES_REQUESTED` 已被 Codex 最终批准结论取代，不再阻塞编码。若旧章节与本任务冲突，以本任务为准。

全程必须遵守：

- 新状态和金额只有统一领域动作服务可写；Controller、草稿、库存、前端不得直接写状态或快照。
- 旧 `status/payment_status` 只由 `OrderCompatAdapter` 在同一事务投影。
- 财务流水只追加；纠错只能新增 `REVERSAL`，不得更新、隐藏或删除历史流水。
- 现金退款与销售退货分离；本轮 `REFUND` 只表示现金退款。
- 新订单、财务和迁移服务遇到空租户必须拒绝。
- 所有角色权限关联必须同租户；后端必须强制权限，前端隐藏不算安全控制。
- `RECORD_ONLY` 永不产生库存流水；占位 SKU 未拆分时永不扣库存。
- 统计消费者统一使用版本化订单事实服务，不复制状态条件和金额公式。
- 所有 SQL 使用参数绑定；所有金额使用 `BigDecimal`；事务失败必须整体回滚。

## 三、连续实施系列

### 系列 A：V51/V52、实体与基础契约

完成以下内容后自测并提交，但不等待 Codex：

1. 新增 `V51__order_lifecycle_finance.sql`：
   - `sale_order` 新生命周期、收款、履约方式、结清、金额快照和乐观锁字段。
   - `order_financial_record`、`order_state_transition_log`。
   - 租户优先索引、幂等唯一键、并发冲销唯一键、正金额与冲销形态 CHECK、快照非负 CHECK。
   - 不修改 V1～V50，不用 Flyway 自动推断历史订单新状态。
2. 新增 `V52__order_action_permission.sql`：
   - 收款、核销、现金退款、冲销、履约选择、配货、导出、财务查看权限。
   - `ROLE_OWNER/ROLE_ADMIN` 获得全部正常订单动作；其余角色按审计报告矩阵。
   - 角色与权限按同租户 JOIN，幂等写入；不增加迁移端点或迁移权限。
3. 新增/更新实体、Mapper、枚举、基础 DTO/VO；新表不可暴露 update/delete 业务能力。
4. 增加空库连续升级、V50→V52、字段/索引/CHECK/唯一键、跨租户权限关联测试。

系列提交建议：`feat(order): add lifecycle finance schema and models [zcode]`

### 系列 B：统一状态机、财务事实与草稿交接

实现单一 `OrderActionService`、财务快照服务和唯一 `OrderCompatAdapter`，至少覆盖 11 个动作：

1. `confirmDraft`
2. `recordPayment`
3. `settleWithWriteOff`
4. `refundPayment`
5. `reverseFinancialRecord`
6. `chooseFulfillmentMode`
7. `startAllocation`
8. `confirmAllocation`
9. `shipOrder`
10. `completeOrder`
11. `cancelOrder`

具体要求：

- 每个动作校验租户、权限、所有权、当前状态、参数、乐观版本和幂等键。
- 同一事务内：锁定订单 → 写财务流水（如有）→ 重算全部快照 → 写新状态 → 写状态日志 → 投影旧状态。
- 同一原流水并发双冲销只能成功一次；禁止冲销 `REVERSAL`。
- 草稿确认幂等，并把纸单定金写为首笔 `RECEIPT`；纸单售价、数量和金额继续以人工识别/确认结果为准。
- 删除裸 `updateStatus` 和旧的第二套收款公式；旧接口改为委托新动作服务。
- `allowedActions` 由后端根据状态、权限、数据范围统一计算。
- 历史未迁移行仅允许 VO 展示回退并返回 `legacyUnmigrated=true`；不得参与动作、统计或写回。
- 订单删除改为符合项目约定的可恢复行为；已产生正式流水或履约事实的订单不得删除。

必须增加：状态白名单、金额公式、零金额、超收、草稿首款、重复请求、并发收款、并发冲销、跨租户、空租户、权限、事务回滚、旧接口兼容测试。

系列提交建议：`feat(order): implement lifecycle and finance actions [zcode]`

### 系列 C：库存履约与占位 SKU 闭环

1. 仅 `STOCK_LINKED` 可创建配货、调整和出库；`RECORD_ONLY` 选择后按设计完成且不写库存流水。
2. 占位 SKU 必须先拆到真实 SKU；数量、销售额和成本快照守恒，并保留拆分来源审计。
3. 配货计划全部写入口增加状态前置条件，删除/取消不能把已发货或已完成订单拉回旧状态。
4. 配货、出库单确认和订单发货收敛到一个底层事务入口；并发和重复调用只扣一次库存。
5. `/api/inventory/out-by-plan` 外部路由移除或返回明确业务拒绝，不得以 500 RuntimeException 响应，也不得绕过订单动作。
6. 取消订单正确释放尚未履约的预留/计划；已出库订单不得直接取消。
7. 同系列处理出库单号碰撞和配货全表加载问题，不扩大到其他模块。

必须增加：占位阻断、拆分守恒、RECORD_ONLY 零库存、库存不足、部分失败回滚、双入口并发、重复发货、取消释放和跨租户测试。

系列提交建议：`feat(order): close fulfillment and inventory workflow [zcode]`

### 系列 D：PC、公共 API、共享类型、权限与导出

1. 改造订单列表、详情、快速录单、新建订单和草稿工作台，界面操作方式参考现有快速录单。
2. 展示履约状态、收款状态、实收、现金退款、核销、尾款、结清方式、财务流水和履约方式。
3. 新建/编辑提交不再携带业务最终状态数字；按钮只根据后端 `allowedActions` 与权限展示。
4. 增加收款、短款结清、现金退款、冲销、履约选择和配货操作；所有失败展示明确业务错误。
5. 更新 `blade-admin/src/api/**`、`packages/types/**`、客户订单类型和接口文档；保留旧响应字段兼容。
6. SALES 财务查看必须同时满足本人数据范围与字段权限；条件不满足由后端 403。
7. 导出增加新金额和履约字段，并处理原 10000 行静默截断问题，不能无提示丢数据。
8. 更新/新增 Playwright 关键路径用例和前端构建校验。

系列提交建议：`feat(order): update admin contracts permissions and export [zcode]`

### 系列 E：统一统计事实与全部消费者

1. 建立唯一、版本化订单事实服务，集中定义经营订单额、净销售额、现金流、结清、履约、销量和毛利口径。
2. Dashboard、Analytics、Customer、Agent、WhatsApp `orderFacts/contextStamp` 和导出全部改为调用统一事实服务。
3. 取消订单不进入经营订单额，但已有现金流水仍进入对应现金流统计。
4. 足额结清和短款核销都计为已结清，但实收与核销分开统计。
5. 占位 SKU 销量计入 SPU 总销量；SKU 维度单列“未指定”，不得污染真实颜色/尺码排名。
6. 订单、状态和财务动作后正确失效客户偏好与相关分析缓存。
7. 删除消费者中旧数字比较、复制金额公式和无状态过滤的直接 SQL。

使用同一组样本覆盖：取消、未收款、部分收款、足额结清、短款结清、现金退款、RECORD_ONLY、未发货、已出库。断言六类消费者在同筛选范围得到一致结果。

系列提交建议：`feat(order): unify order facts and analytics consumers [zcode]`

### 系列 F：移动端兼容与切换

1. 移动端读取新字符串状态、金额快照、履约方式和 `allowedActions`。
2. 新移动端不得提交旧数字状态；旧版本响应兼容由后端契约测试保护。
3. 更新共享类型的唯一来源，避免 PC 与移动端各自复制枚举。
4. 完成移动端构建并编写简短手工冒烟记录；本系列不要求引入新测试框架。

系列提交建议：`feat(order): migrate mobile order lifecycle contract [zcode]`

### 系列 G：离线迁移工具、文档同步与总回归

1. 实现独立、离线、默认 dry-run 的历史迁移工具；不进入常驻 Controller，不提供生产 Web 端点。
2. 工具显式接收租户和数据源，空租户拒绝；真实写回必须显式 `--execute`。
3. 输出逐单旧值、新值、判断证据、金额不变量、异常原因和人工核对清单；旧 `status=7/8`、不明确 `refund_amount` 不自动决定。
4. 工具可幂等重放；第二次执行不重复创建流水或改变已确认结果。
5. 在可用的隔离测试库完成空库/V50 连续升级和合成历史数据迁移演练。若没有 V42 生产副本，只完成工具与合成数据测试，并在最终交付中明确列为 Codex/发布阶段待执行项；不得自行连接 NAS 获取。
6. 同步 `03-TASKS.md`、`STATUS.md`、`05-CHANGELOG.md`、`SESSION_CONTEXT.md` 和相关 API/设计文档，使状态与实际代码一致。
7. 运行全部最终测试，修复所有由本次变更导致的失败。

系列提交建议：`test(order): add migration rehearsal and full regression [zcode]`

## 四、自测与提交规则

每个系列必须执行相关定向测试和 `git diff --check`。测试失败必须先修复，不允许把已知失败带入下一系列。每个系列至少一个独立提交，提交信息带 `[zcode]`；允许为修复测试增加后续小提交，但禁止把所有工作压成一个无法审核的巨型提交。

最终至少运行并记录：

```bash
cd blade-backend && mvn test
cd blade-admin && npm run build
cd blade-admin && npx playwright test e2e-order-lifecycle.spec.ts e2e-order-draft.spec.ts order-fullflow.spec.ts
cd blade-mobile && npm run build
git diff --check d800ec4..HEAD
git status --short --branch
git rev-list --left-right --count HEAD...@{upstream}
```

若 Playwright 需要本地服务或固定测试数据，Z Code 应自行启动隔离开发环境并记录命令；不能运行的用例必须写清环境阻断和已完成的替代验证，不能写成“通过”。

## 五、最终一次性交付格式

全部系列完成、自测和推送后，只提交一份最终交付报告，不再逐 SOW 请求 Codex 确认。报告必须包含：

- 基线 `d800ec4` 与最终 tip commit。
- 按系列列出提交、修改文件和完成内容。
- V51/V52 的实际 DDL、兼容与回滚影响。
- 11 个动作的入口、权限、事务、幂等与状态转移摘要。
- 金额、库存、租户、权限、统计一致性不变量结果。
- 每条测试命令、退出码、通过/失败数量；不得只写“已测试”。
- 尚未完成或因环境无法执行的事项。
- `git status` 和远端 ahead/behind。

最终状态写 `WAITING_CODEX_FINAL_REVIEW`。之后停止继续重构，由 Codex 对 `d800ec4..最终 tip` 做一次完整代码、migration、测试和安全审核。未经 Codex 最终通过与用户批准，不得进入 NAS 发布或生产迁移。

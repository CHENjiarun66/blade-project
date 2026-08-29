# 如何由实现 Agent 完成订单大重构

> 日期：2026-08-30
>
> 内容类型：实施与审核清单
>
> 实现负责人：其他 Agent，认领时填写名称
>
> 架构与最终审核：Codex
>
> 生产批准：用户
>
> 目标分支：`feature/order-lifecycle-finance-refactor`

本文把订单大重构拆成可领取的工作包。实现 Agent 一次只执行一个工作包。Codex 在每个审核门禁检查代码、测试、数据安全和范围，不能把最终验收委托给实现 Agent。

## 一、角色和权限

| 角色 | 可以做 | 不可以做 |
|------|------|------|
| 实现 Agent | 只读审计、认领任务、编写测试和代码、更新任务记录、提交并推送自己的 feature 分支 | 修改已执行 migration、绕过审核进入下一阶段、合并 `master`、生产写操作、NAS 发布 |
| Codex | 锁定契约、审查计划和 Diff、复跑测试、检查迁移与统计不变量、给出通过或整改结论 | 代替用户批准生产发布 |
| 用户 | 确认业务例外、批准 release 合并和 NAS 维护窗口 | 无需处理实现细节 |

实现 Agent 提交的“测试通过”只是交付说明。Codex 复核后，工作包才算通过。

## 二、开工前置条件

开始编码前必须满足：

1. 当前设计文档已经提交并推送，工作区没有来源不明的未提交修改
2. 从确认后的 V50 基线创建 `feature/order-lifecycle-finance-refactor`
3. 为实现 Agent 创建独立 worktree，不与 Codex 或其他 Agent 共用工作目录
4. 实现 Agent完整阅读本文件、主 ROM/SOW、生命周期设计、财务设计、任务清单和协作规范
5. 实现 Agent先完成 `ORDER-SOW-0` 只读审计，Codex 审核后才能修改代码

如果 V43-V50 在开工前已经通过独立 release 合入 `master`，从最新 `master` 创建分支；否则从当前 V50 功能基线创建。Agent 必须在审计报告中记录实际基线 commit。

## 三、字段与兼容契约

正式订单采用“两旧两新”：

| 角色 | 字段 | 类型 | 用途 |
|------|------|------|------|
| 旧兼容 | `status` | `tinyint` | 旧订单履约状态，只由兼容适配器维护 |
| 旧兼容 | `payment_status` | `tinyint` | 旧收款状态 `0/1/2`，只由兼容适配器维护 |
| 新事实 | `fulfillment_status` | `varchar(32)` | `CONFIRMED` 至 `COMPLETED/CANCELLED` 的履约状态 |
| 新事实 | `collection_status` | `varchar(16)` | `UNPAID`、`PARTIAL`、`SETTLED` |

辅助维度：

- `fulfillment_mode`：`UNDECIDED`、`STOCK_LINKED`、`RECORD_ONLY`
- 草稿状态：继续存放在独立 `order_draft` 表
- 本次不新增 `order_lifecycle_status`，避免与 `fulfillment_status` 重复

写入规则：

1. 新状态机和财务快照服务先写新字段
2. 同一事务调用兼容适配器生成旧字段投影
3. 旧 Controller 和旧客户端不能继续直接写旧字段
4. 读取接口同时返回新字段和旧兼容字段
5. 新 PC、移动端、统计和 Agent 只按新字段判断
6. 首发不删除旧字段，至少稳定一个发布周期后单独评估

旧字段映射必须集中在一个适配器。任何模块自行维护映射都属于审核阻断项。

兼容投影固定为：

| 新字段值 | 旧字段投影 |
|------|------|
| `collection_status=UNPAID` | `payment_status=0` |
| `collection_status=PARTIAL` | `payment_status=1` |
| `collection_status=SETTLED` | `payment_status=2` |
| `fulfillment_status=CONFIRMED` | `status=0` |
| `fulfillment_status=WAITING_ALLOCATION` | `status=1` |
| `fulfillment_status=ALLOCATING` | `status=2` |
| `fulfillment_status=READY_TO_SHIP` | `status=3` |
| `fulfillment_status=SHIPPED` | `status=4` |
| `fulfillment_status=COMPLETED` | `status=5` |
| `fulfillment_status=CANCELLED` | `status=6` |

旧 `status=7/8` 属于尚未重构的退货语义。历史迁移把它们列入人工核对，首发不能强制映射为新的履约状态。

## 四、任务状态

每个工作包使用以下状态：

```text
TODO → IMPLEMENTING → WAITING_CODEX_REVIEW
     → CHANGES_REQUESTED → WAITING_CODEX_REVIEW
     → CODEX_APPROVED → MERGED_TO_INTEGRATION
```

实现 Agent只能把任务改为 `WAITING_CODEX_REVIEW`，不能自行填写 `CODEX_APPROVED`。Codex 复核后更新审核结论。

## 五、工作包与审核门禁

### ORDER-SOW-0：只读基线审计

对应范围：全部订单关联模块。状态：`TODO`。执行人：待认领。

实现 Agent只读输出：

- 当前 branch、commit、ahead/behind 和 migration 最高版本
- 所有旧 `status`、`payment_status` 写入点和数字比较点
- 所有订单金额公式、直接统计 SQL 和缓存消费者
- 草稿确认、收款、配货、出库、取消、导出和 Agent 调用链
- 拟修改文件、测试文件、migration 方案和风险

禁止修改文件、数据库和生产环境。

Codex 门禁 `CR-0`：确认扫描没有遗漏，锁定 migration 编号、文件边界、旧字段映射和测试矩阵。

### ORDER-SOW-1：加法迁移与兼容模型

对应任务：`BE-1040`、`BE-1041`。状态：`BLOCKED_BY_CR-0`。

允许范围：

- `blade-backend/src/main/resources/db/migration/` 中新增 migration
- `blade-backend/src/main/java/com/blade/order/entity/**`
- 新增订单状态、财务类型枚举和基础 DTO/VO
- 对应 schema、Mapper 和实体测试

锁定要求：

- 预留 V51 给订单生命周期和财务 schema
- 预留 V52 给订单动作与财务权限
- 不修改 V1-V50
- 新字段可空或带兼容默认值，旧应用仍能启动和读取
- 新增 `order_financial_record`、`order_state_transition_log`
- 新增 `collection_status`、`fulfillment_status`、`fulfillment_mode` 和并发版本
- 不在 Flyway 中凭旧 `status` 自动判定 81 张历史订单最终状态

Codex 门禁 `CR-1`：审查 DDL、索引、租户隔离、默认值、Flyway 连续升级、旧应用兼容和 schema 测试。

### ORDER-SOW-2：状态机、财务事实与草稿交接

对应任务：`BE-1042`、`BE-1043`、`BE-1044`、`BE-1049`。状态：`BLOCKED_BY_CR-1`。

允许范围：

- `blade-backend/src/main/java/com/blade/order/**`
- `blade-backend/src/main/java/com/blade/order/draft/**`
- 订单动作直接需要的权限配置和对应测试

必须实现：

- 统一订单动作服务和旧字段兼容适配器
- 收款、短款核销、退款、冲销和金额快照重算
- 草稿确认事务和首笔 `RECEIPT` 流水
- `allowedActions` 后端计算
- 幂等键、乐观锁、状态日志、租户和所有权过滤
- 零金额、重复请求、并发收款、跨租户和事务回滚测试

禁止让 Controller、草稿、库存或前端直接更新状态字段和金额快照。

Codex 门禁 `CR-2`：逐个检查写入口、事务、幂等、租户过滤、金额不变量、权限和兼容投影。

### ORDER-SOW-3：库存履约与占位 SKU

对应任务：`BE-610`～`BE-612`、`BE-1045`、`BA-805`、`BA-1123`。状态：`BLOCKED_BY_CR-2`。

允许范围：

- `blade-backend/src/main/java/com/blade/order/**`
- `blade-backend/src/main/java/com/blade/inventory/**`
- PC 占位拆分与配货相关页面
- 对应测试

必须实现：

- 占位 SKU 拆分审计，数量和销售额守恒
- `STOCK_LINKED` 才能创建配货和出库
- `RECORD_ONLY` 完成不产生库存流水
- 配货、出库和订单发货共用一个底层事务入口
- 重复发货、双入口并发、库存不足和部分失败全部回滚

Codex 门禁 `CR-3`：核对库存只扣一次、占位不可出库、金额不重复统计、事务原子性和跨租户隔离。

### ORDER-SOW-4：PC、公共 API、类型与导出

对应任务：`BA-1120`～`BA-1123`、`BE-1051`。状态：`BLOCKED_BY_CR-2`，可与 SOW-3 并行但必须使用独立 worktree。

允许范围：

- `blade-admin/src/views/orders/**`
- `blade-admin/src/api/**` 中订单直接接口
- `packages/types/**`
- 订单导出 DTO/服务和接口文档
- 对应单元测试与 Playwright 测试

必须实现：

- 列表、详情、快速录单和草稿页展示新状态
- 收款流水、实收、退款、核销、尾款和结清方式
- 履约方式选择和占位拆分引导
- 所有按钮只根据后端 `allowedActions` 和权限展示
- 兼容旧响应，但新提交不携带最终状态数字

Codex 门禁 `CR-4`：审查 API 契约、旧客户端兼容、权限隐藏与后端拒绝、金额展示和关键路径 E2E。

### ORDER-SOW-5：统计事实和全部消费者

对应任务：`BE-1046`、`BE-1050`。状态：`BLOCKED_BY_CR-2`。

允许范围：

- `blade-backend/src/main/java/com/blade/dashboard/**`
- `blade-backend/src/main/java/com/blade/analytics/**`
- `blade-backend/src/main/java/com/blade/customer/**`
- `blade-backend/src/main/java/com/blade/agent/**`
- `blade-backend/src/main/java/com/blade/whatsapp/**` 中订单事实消费代码
- 订单导出和直接相关测试

必须先建立统一、版本化订单事实服务，再切换消费者。禁止每个模块复制状态条件和金额公式。

验收数据必须覆盖：取消订单、未收款、部分收款、足额结清、短款结清、现金退款、`RECORD_ONLY`、未发货和已出库订单。

Codex 门禁 `CR-5`：同一筛选范围下，仪表盘、Analytics、客户、Agent、WhatsApp 和导出结果一致；缓存按订单和财务动作失效。

### ORDER-SOW-6：移动端兼容与切换

对应任务：`FE-110`、`FE-111`。状态：`BLOCKED_BY_CR-2`。

允许范围：`blade-mobile/**`、`packages/types/**` 中移动端直接依赖部分和对应测试。

必须实现新状态读取、动作白名单、收款与履约展示。旧移动端版本在过渡期不能因为新增字段崩溃，新版本不能提交旧数字状态。

Codex 门禁 `CR-6`：检查共享类型一致、旧响应兼容、动作权限、构建和移动端关键路径。

### ORDER-SOW-7：历史迁移与全链路回归

对应任务：`BE-1047`、`TEST-ORDER-LIFECYCLE-001`。状态：`BLOCKED_BY_CR-3/4/5/6`。

实现 Agent必须：

1. 编写只读审计和受控迁移工具，不把不确定判定塞进 Flyway
2. 在 V42 生产副本执行 V43-V50 和新 migration
3. 输出 81 张历史订单逐单映射、证据和异常清单
4. 重复执行迁移预演，证明结果确定且幂等
5. 运行后端全量测试、PC/移动端构建、E2E、权限和跨租户测试
6. 输出金额、订单、库存和文件不变量报告

Codex 门禁 `CR-7`：独立恢复副本、复跑 migration 和测试，逐项核对不变量。Codex 只给出 release 建议，不批准生产发布。

### ORDER-SOW-8：release 与 NAS 发布准备

对应任务：`BE-1052`。状态：`BLOCKED_BY_CR-7`。

实现 Agent只准备 release 制品和 dry run：

- 创建 release 候选并记录准确 commit
- 构建不可变 `linux/amd64` 镜像并记录 digest
- 升级备份脚本、恢复演练、SHA-256、NAS 外副本和 uploads 清单
- 生成维护窗口、迁移、验证和回滚命令
- 不执行 NAS 写操作，不合并 `master`

Codex 门禁 `CR-8`：检查 release Diff、构建、镜像、备份恢复、migration 和回滚可执行性。用户确认后才能合入 `master` 和进入生产维护窗口。

### ORDER-SOW-9：生产切换与观察

状态：`REQUIRES_USER_APPROVAL`。

生产流程固定为：

1. 用户批准维护窗口和 release commit
2. 停止 PC、移动端、Agent、Collector 和后台任务写入
3. 停止 backend，显示维护页
4. 生成最终数据库/uploads 备份集、SHA-256 和 NAS 外副本
5. 记录 V42 最终业务基线
6. 部署 release 镜像，执行 Flyway 和受控历史迁移
7. 运行不变量、登录、订单、收款、仅记录和库存履约冒烟
8. Codex 给出上线校验结论
9. 全部通过后解除停写
10. 观察一个发布周期，保留旧字段、旧镜像和备份

任一步失败都保持停写。开放写入前可以恢复最终备份；开放写入后禁止直接覆盖旧 SQL，应切回兼容读取或执行前向修复。

### ORDER-SOW-10：旧字段下线评估

对应任务：`BE-1048`。状态：`DEFERRED_ONE_RELEASE_CYCLE`。

首发不删除 `status`、`payment_status`、兼容 DTO 或旧接口。稳定一个发布周期并确认访问日志无旧消费者后，再单独规划下线 release。

## 六、每次交付格式

实现 Agent完成工作包后必须提交：

```text
工作包：ORDER-SOW-n
执行 Agent：agent_name
基线 commit：commit_sha
交付 commit：commit_sha
阅读文档：文件清单
修改文件：文件清单
数据库影响：migration 和数据影响
测试命令：逐条命令与结果
租户/权限检查：查询和写入如何隔离
兼容性：旧字段、旧接口和旧客户端结果
未决风险：没有则写“无”
建议状态：WAITING_CODEX_REVIEW
```

缺少 commit、实际测试输出、租户说明或数据库影响时，Codex 直接退回，不进入代码审核。

## 七、Codex 审核清单

Codex 对每个门禁执行：

1. 核对 `git status --short`、分支、commit 和实际文件范围
2. 阅读目标 Diff，确认没有夹带无关重构
3. 运行 `git diff --check`
4. 复跑实现 Agent 声称通过的定向测试
5. 检查查询和写入的租户、所有权、软删除和权限过滤
6. 检查状态机、事务、幂等、并发和兼容投影
7. 检查金额、库存和统计不变量
8. 更新审核结论：`CODEX_APPROVED` 或 `CHANGES_REQUESTED`

最终审核额外执行后端全量测试、PC/移动端构建、E2E、生产副本迁移和恢复演练。Codex 不根据实现 Agent 的报告直接放行。

## 八、实现 Agent 启动指令

把以下内容交给实现 Agent，先执行 `ORDER-SOW-0`：

```text
你负责 BladeProject 订单大重构的实现，Codex 负责架构和最终审核。

先阅读：
1. docs/superpowers/plans/2026-08-30-order-refactor-agent-execution-board.md
2. docs/superpowers/plans/2026-08-30-order-lifecycle-finance-refactor-rom-sow.md
3. docs/14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md
4. docs/15-ORDER_FINANCE_ANALYTICS_DESIGN.md
5. docs/03-TASKS.md
6. docs/reference/AGENT_COLLABORATION.md
7. docs/reference/GIT_BRANCH_WORKFLOW.md

本轮只执行 ORDER-SOW-0，只读审计，不修改文件，不运行数据库 migration，不连接生产执行写操作。
输出当前基线、旧状态写入点、金额公式、直接 SQL 消费者、调用链、拟改文件、测试方案和风险。完成后等待 Codex 审核，不得自行开始 SOW-1。
```

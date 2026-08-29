# 如何重构订单状态、收款与库存履约流程

> 内容类型：概念设计与迁移方案。更新时间：2026-08-29。

> 实现状态：业务方案已确认，代码、数据库和生产数据迁移尚未实施。当前运行行为仍以 [06-ORDER_INVENTORY_DESIGN.md](./06-ORDER_INVENTORY_DESIGN.md) 的“当前实现”说明和代码为准。

本文定义订单草稿、正式订单、收款、短款结清、库存履约和仅记录订单之间的目标关系。开发、迁移、测试和验收必须以本文为主设计，不得直接重解释旧 `sale_order.status` 数值。

## 一、设计目标

本次重构解决以下问题：

- 旧订单状态把“已付款”和“履约进度”混在同一个字段中
- 正式订单无法区分“需要扣库存”和“只记录历史数据”
- 多次收款只有累计金额，没有逐笔流水
- 草稿中的纸单定金与正式收款缺少明确交接点
- 状态数字分散在后端、PC、移动端、统计、客户和测试代码中
- 旧订单状态缺少可信业务含义，不能直接按旧数字迁移

本次重构遵循以下原则：

- 草稿状态、收款状态、履约方式和履约状态分开存储
- 只有正式订单参与收款和经营统计
- 只有 `STOCK_LINKED` 订单可以创建配货计划和扣减库存
- `RECORD_ONLY` 订单不创建库存计划，不产生库存流水
- 支付状态由金额和核销流水计算，前端不能直接指定最终状态
- 所有关键动作通过统一状态机服务执行
- 旧字段保留兼容期，新字段稳定后再下线旧字段

## 二、目标业务流程

订单从纸单草稿进入正式订单后，先处理收款，再决定是否进入库存履约：

```text
草稿订单 EDITING
    → 人工确认
正式订单 CONFIRMED
    ├─ UNPAID 未收款
    ├─ PARTIAL 部分收款
    └─ SETTLED 已结清
         ├─ RECORD_ONLY → COMPLETED
         └─ STOCK_LINKED
              → WAITING_ALLOCATION
              → ALLOCATING
              → READY_TO_SHIP
              → SHIPPED
              → COMPLETED
```

已结清包括客户实收达到应收净额，以及操作人员通过“标记结清”确认不再追收尾款。订单结清后，界面允许选择“关联库存”或“仅记录订单”。界面可以默认选中“仅记录订单”，数据库仍默认 `UNDECIDED`，并要求操作人员确认。

## 三、四个独立状态维度

### 3.1 草稿状态

草稿继续使用独立的 `order_draft` 表：

| 值 | 业务名称 | 说明 |
|---|---|---|
| `EDITING` | 待确认 | Agent 或人工可以继续修改 |
| `CONFIRMED` | 已确认 | 已生成正式订单，重复确认返回同一订单 |
| `VOID` | 已作废 | 预留状态，实施作废功能后启用 |

草稿中的 `deposit` 是纸单识别值或人工确认值。系统在草稿转正式订单时才把该金额写入正式收款流水。

### 3.2 收款状态

正式订单新增字符串字段 `collection_status`。旧整数 `payment_status` 在兼容期保留，只接收新状态服务生成的兼容投影。

| 值 | 业务名称 | 判定规则 |
|---|---|---|
| `UNPAID` | 未收款 | `paid_amount = 0` 且尚未结清 |
| `PARTIAL` | 部分收款 | `0 < paid_amount < receivable_net_amount` |
| `SETTLED` | 已结清 | 实收达到应收净额，或剩余尾款已核销 |

订单生命周期只根据财务快照判断是否结清。金额、退款和统计的完整口径见 [15-ORDER_FINANCE_ANALYTICS_DESIGN.md](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md)。目标快照使用：

```text
adjusted_order_amount = total_amount - sales_return_amount
net_received_amount = gross_received_amount - cash_refund_amount
balance_amount = adjusted_order_amount - write_off_amount - net_received_amount
```

`SETTLED` 不增加更多子状态。系统通过收款流水类型区分足额收款、短款核销和历史迁移。

### 3.3 履约方式

新增 `fulfillment_mode`：

| 值 | 业务名称 | 库存影响 |
|---|---|---|
| `UNDECIDED` | 尚未选择 | 不允许创建配货计划或完成订单 |
| `STOCK_LINKED` | 关联库存 | 必须经过配货、确认和出库 |
| `RECORD_ONLY` | 仅记录订单 | 不创建库存计划，不扣减库存 |

`need_delivery` 只表示是否需要物流送货，不能用于推断 `fulfillment_mode`。历史订单即使填写送货地址，也可能是仅记录订单。

### 3.4 履约状态

新增字符串字段 `fulfillment_status`：

| 值 | 业务名称 | 允许进入条件 |
|---|---|---|
| `CONFIRMED` | 已确认 | 草稿确认或人工创建正式订单 |
| `WAITING_ALLOCATION` | 待配货 | 已结清并选择 `STOCK_LINKED` |
| `ALLOCATING` | 配货中 | 已创建配货计划 |
| `READY_TO_SHIP` | 待发货 | 配货方案已确认 |
| `SHIPPED` | 已发货 | 已按配货计划完成库存出库 |
| `COMPLETED` | 已完成 | `RECORD_ONLY` 直接完成，或发货后完成 |
| `CANCELLED` | 已取消 | 满足取消规则并完成必要清理 |

旧的“退货中”和“已退货”不继续扩充到履约状态。后续实现退货时新增独立 `after_sales_status` 和退货单。

兼容期由一个适配器维护旧字段投影：`UNPAID/PARTIAL/SETTLED` 对应旧 `payment_status=0/1/2`；`CONFIRMED/WAITING_ALLOCATION/ALLOCATING/READY_TO_SHIP/SHIPPED/COMPLETED/CANCELLED` 对应旧 `status=0/1/2/3/4/5/6`。旧退货状态 `7/8` 不自动映射，迁移时进入人工核对。

## 四、关键业务规则

### 4.1 草稿确认

草稿确认必须在一个事务中完成：

1. 锁定草稿并校验幂等状态
2. 校验客户、SKU、数量、纸单售价和警告确认
3. 创建 `fulfillment_status=CONFIRMED` 的正式订单
4. 把草稿定金写为正式订单的首笔 `RECEIPT` 流水
5. 重新计算正式订单收款快照
6. 回写 `confirmed_order_id` 和草稿状态

草稿确认不得自动选择履约方式，不得创建配货计划，也不得扣减库存。

### 4.2 正常收款与短款结清

每次收款都新增不可变的收款流水。订单表保留累计金额和状态快照，便于列表与统计查询。

短款结清执行以下动作：

1. 写入本次正常收款金额，允许为 `0`
2. 把剩余尾款写入 `WRITE_OFF` 流水
3. 保存核销原因、操作人和时间
4. 更新核销快照和 `collection_status=SETTLED`

系统不能把短款核销伪装成客户实收金额。

### 4.3 两种履约方式

订单达到 `SETTLED` 后才能确认履约方式：

- 选择 `STOCK_LINKED` 后进入 `WAITING_ALLOCATION`。包含 `PLACEHOLDER` SKU 的订单必须先拆到真实 SKU，才能创建配货计划或出库
- 选择 `RECORD_ONLY` 后直接进入 `COMPLETED`。该订单不影响库存，但仍计入销售额、款号销量和客户贡献

### 4.4 取消、退款与退货

取消订单必须走状态动作接口，不能直接更新状态字段：

- `CONFIRMED` 订单可以取消
- `WAITING_ALLOCATION` 或 `ALLOCATING` 订单取消前必须清理未出库计划和预留
- `READY_TO_SHIP` 是否允许取消，需要根据库存动作判断
- `SHIPPED` 之后不使用取消，后续走独立退货流程
- 已收款订单取消后仍需退款，取消状态不能代替退款记录

本轮保留现有退款金额口径，不把完整退货业务夹带进状态重构。

## 五、数据模型调整

### 5.1 正式订单新增字段

| 字段 | 类型建议 | 说明 |
|---|---|---|
| `collection_status` | `varchar(16)` | 新收款状态：`UNPAID`、`PARTIAL`、`SETTLED` |
| `fulfillment_status` | `varchar(32)` | 新履约状态 |
| `fulfillment_mode` | `varchar(24)` | 履约方式 |
| `fulfillment_decided_at` | `datetime` | 履约方式确认时间 |
| `fulfillment_decided_by` | `bigint` | 履约方式确认人 |
| `settled_at` | `datetime` | 首次达到已结清的时间 |
| `version` | `int` | 乐观并发版本 |

旧 `status` 字段在兼容期继续保留。代码不能在同一个迁移中直接把旧数字重新解释成新含义。

### 5.2 收款流水

新增 `order_financial_record`。完整字段和金额影响见 [15-ORDER_FINANCE_ANALYTICS_DESIGN.md](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md)：

| 字段 | 说明 |
|---|---|
| `order_id` | 正式订单标识 |
| `record_type` | `RECEIPT`、`WRITE_OFF`、`REFUND`、`REVERSAL`、`MIGRATION_OPENING` |
| `amount` | 本次金额 |
| `payment_method` | 可空，现金、转账等方式 |
| `occurred_at` | 业务发生时间 |
| `operator_id` | 操作人 |
| `reason` | 核销、退款或冲销原因 |
| `idempotency_key` | Agent 或外部请求幂等键 |
| `tenant_id` | 租户标识 |

订单金额字段继续作为汇总快照。服务端只允许统一的财务快照服务更新这些字段。

### 5.3 状态流转日志

新增 `order_state_transition_log`，记录订单和租户、动作名称、变更前后履约状态、履约方式、收款状态、操作人、来源、原因、幂等键和发生时间。历史迁移也写入迁移动作日志，但不得伪造不存在的操作人和收款时间。

### 5.4 兼容字段

本轮不立即删除：

- `status`：旧履约状态兼容字段
- `payment_status`：旧整数收款状态兼容字段
- `deposit_amount`：首笔部分收款历史快照
- `pay_time`：旧接口兼容时间
- `adjustment_status`：配货计划兼容字段
- `is_delivered`、`delivered_at`：物流展示兼容字段

## 六、统一状态机与接口

后端新增统一订单动作服务。Controller、配货服务、出库服务和 Agent 服务不得直接调用 `setStatus()` 写状态。

| 动作 | 作用 |
|---|---|
| `confirmDraft` | 草稿转正式订单 |
| `recordPayment` | 新增正常收款 |
| `settleWithWriteOff` | 收款并核销尾款 |
| `chooseFulfillmentMode` | 选择库存履约或仅记录 |
| `startAllocation` | 创建配货计划 |
| `confirmAllocation` | 确认计划并进入待发货 |
| `shipOrder` | 按实际计划扣库存 |
| `completeOrder` | 完成已发货订单 |
| `cancelOrder` | 按来源状态取消和清理 |

系统废弃或收紧任意数字状态更新、收款后强制进入“已付款”、配货服务直接写订单状态，以及绕过订单动作服务的出库入口。所有写接口使用租户范围行锁或版本条件更新，并支持幂等重试。

## 七、旧订单迁移

### 7.1 已核对的旧备份

只读扫描 `tmp/nas-migration/blade_project_prod_for_nas_20260605_dwy.sql` 得到：

| 项目 | 数量 |
|---|---:|
| 订单总数 | 81 |
| 旧 `status=0` | 81 |
| 已结清 | 74 |
| 部分收款 | 6 |
| 未收款 | 1 |
| 标记需要送货 | 8 |
| 配货计划 | 0 |
| 已送货记录 | 0 |

该备份证明旧 `status=0` 不能直接映射为新“待确认”。正式迁移仍必须读取生产环境实时数据。

### 7.2 自动迁移建议

| 旧数据证据 | 新履约状态 | 新履约方式 | 处理方式 |
|---|---|---|---|
| 已结清且没有配货或出库证据 | `COMPLETED` | `RECORD_ONLY` | 生成候选清单，确认后批量迁移 |
| 部分收款 | `CONFIRMED` | `UNDECIDED` | 保留尾款，等待后续收款 |
| 未收款 | `CONFIRMED` | `UNDECIDED` | 保留未收款状态 |
| 存在配货计划或实际出库证据 | 按证据推导 | `STOCK_LINKED` | 自动推导后复核 |
| 状态、计划、金额或库存证据冲突 | 不自动变更 | 不自动变更 | 进入人工核对清单 |

迁移不能根据 `need_delivery` 推断库存履约方式。部分收款订单的历史差额也不能自动核销。

### 7.3 迁移前审计

生产迁移前生成只读报告：

- 按旧状态、收款状态和组合状态统计数量
- 校验负数、超收、核销和退款金额
- 查找有配货状态但没有计划的订单
- 查找已发货但没有出库证据的订单
- 查找物流标记与履约证据冲突的订单
- 查找草稿已确认但缺少正式订单的记录
- 查找准备进入库存履约但仍含占位 SKU 的订单

报告必须包含每张异常订单的订单号、判定证据和建议结果。

## 八、分阶段实施与回滚

1. **加法迁移**：新增履约字段、收款流水和状态日志，保留旧字段
2. **统一后端动作**：改造草稿确认、收款、核销、履约选择、配货、发货、完成和取消
3. **切换消费者**：改造 PC、移动端、共享类型、统计、客户、导出和 Agent
4. **迁移预演**：在测试环境导入生产副本，输出迁移对照和异常清单
5. **生产迁移**：备份后执行同一脚本，核对订单、金额和库存流水不变量
6. **清理兼容**：稳定运行一个发布周期后，再评估旧字段和旧接口下线

应用异常时先切回旧字段读取。加法迁移不删除旧数据，可以回滚应用版本。生产数据迁移必须保留逐单映射快照，禁止通过覆盖式脚本恢复。

## 九、影响范围与测试

后端涉及订单、草稿、配货、出库、仪表盘、分析、客户、导出、Flyway、Mapper 和权限。前端涉及 PC 订单全流程、草稿工作台、仪表盘、分析、客户详情、移动端订单页面和 `packages/types`。

测试至少覆盖：

- 草稿无定金、部分定金和足额定金确认
- 多次收款、足额结清和短款结清
- 已结清后选择两种履约方式
- 占位 SKU 的库存履约阻断
- 仅记录订单不产生库存流水
- 配货、确认、发货、完成和取消白名单
- 双入口并发发货与重复 Agent 请求
- 旧订单迁移、异常清单和回滚读取
- 仪表盘、分析、客户和导出统计口径

## 十、当前决策与待实施项

以下决策已经确认：

- 草稿与正式订单分表
- 正式订单收款状态为未收款、部分收款、已结清
- 短款通过明确核销动作结清
- 已结清后选择关联库存或仅记录订单
- 仅记录订单直接完成且不影响库存
- 关联库存订单必须经过真实 SKU、配货和出库
- 旧状态不原地重解释，采用新增字段和兼容迁移
- 新增收款流水、状态日志和并发控制

数据库、统一状态机、跨端交互、统计改造、生产审计和历史数据迁移均尚未实施。

具体关联系统、Agent 工作包、分支策略、V42 至新版本迁移顺序和 NAS 发布门禁，以[订单大重构 ROM/SOW](./superpowers/plans/2026-08-30-order-lifecycle-finance-refactor-rom-sow.md)为准。任何 Agent 不得只改订单主表后单独上线。

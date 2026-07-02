# 订单系统与库存系统解耦设计方案

> 讨论时间：2026-03-23
> 参与角色：销售人员、仓库人员、开发
> 生产口径更新：2026-06-17

## 0. 当前生产口径：软解耦优先

经过生产录单流程验证，订单系统和库存系统第一版采用“软连接”：

- 订单创建、快速录单、确认收款、追加收款、抹零/短款结清均不得因库存不足、未建库存记录或仓库未配置失败。
- 收款不锁库存，只让订单进入待配货/优先配货队列。
- 库存只在配货/发货阶段作为提示、复核和实际扣减依据。
- 发货时按实际发货 SKU、仓库、数量扣减库存；如果发生替换 SKU、减配、补发或退款，必须记录在配货方案或调整说明中。
- 第一版确认发货仍是库存强校验节点：缺库存记录或库存不足会阻止发货；部分发货、分批发货和缺货退款不在本版范围。
- `inventory_global_reserve` 和 `global_reserved_qty` 作为历史兼容结构暂不删除，但第一版生产订单流程不再依赖硬预留。

对应流程图文件：[`architecture/order-inventory-soft-coupling-flow.drawio`](./architecture/order-inventory-soft-coupling-flow.drawio)。

---

## 一、问题背景

### 1.1 业务痛点

当前系统设计是"销售开单时必须选择仓库"，但实际业务中：

- **销售开单**：不关心商品从哪个仓库出，只关心有没有货
- **仓库分配**：由仓库人员后续根据实际库存分配
- **可能出现的情况**：库存不够需要换款、数量不足需要沟通

### 1.2 核心矛盾

| 环节 | 当前设计 | 实际需求 |
|------|----------|----------|
| 销售开单 | 必须选仓库 | 不选仓库，不因库存不足阻断 |
| 收款确认 | 按仓库或跨仓硬预留 | 只更新收款状态，不锁库存 |
| 配货 | 无或强库存校验 | 查看库存并记录实际配货方案 |
| 出库 | 按原订单出 | 按实际发货明细扣减库存，可能和原订单有差异 |

---

## 二、实际业务场景

### 2.1 场景1：仓库库存不够，需要换款

**客户订单**：
- 6000款 × 30件

**实际情况**：
- 6000款只有28件

**处理流程**：
```
1. 仓库发现6000只有28件
2. 通知销售人员（线下沟通）
3. 销售人员联系客户确认
4. 客户同意换款6001
5. 销售人员通知仓库执行

实际发货：6000款×28件 + 6001款×2件
```

### 2.2 场景2：客户不同意换款，按实际库存发货

**客户订单**：
- 6000款 × 30件

**实际情况**：
- 6000款只有28件

**处理流程**：
```
1. 仓库发现6000只有28件
2. 通知销售人员（线下沟通）
3. 销售人员联系客户确认
4. 客户不同意换款，要求有多少发多少
5. 销售人员通知仓库执行

实际发货：6000款×28件
后续：欠2件退款
```

### 2.3 沟通机制

- 仓库发现库存问题 → 通知销售人员（线下沟通）
- 销售人员确认方案 → 通知仓库执行
- 系统只记录最终结果，不做系统内消息通知
- **预留**：后期可扩展系统内通知功能

---

## 三、业务流程设计

### 3.1 完整订单生命周期

```
┌─────────────────────────────────────────────────────────────────┐
│                         订单生命周期                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  【1. 销售开单 / 快速录单】                                      │
│  ├─ 输入：客户信息 + 商品明细（无仓库）                           │
│  ├─ 校验：SKU状态、金额、客户等业务字段；不校验库存                │
│  └─ 输出：订单创建成功，status=CREATED                           │
│                                                                  │
│  【2. 收款确认 / 追加收款】                                      │
│  ├─ 触发条件：财务确认定金或全款                                 │
│  ├─ 操作：更新 paid_amount / payment_status / pay_time           │
│  ├─ 不做：库存检查、库存锁定、跨仓预留                            │
│  └─ 输出：status=PAID，进入待配货/优先配货队列                    │
│                                                                  │
│  【3. 配货分配】                                                │
│  ├─ 操作：仓库人员查看待配货订单和当前库存                        │
│  ├─ 配货状态：ADJUSTMENT_PENDING（待调整）                      │
│  ├─ 调整方式：                                                  │
│  │   • 换款：用其他SKU补欠件                                   │
│  │   • 减数量：按实际库存发货                                   │
│  │   • 备注说明：调整原因                                       │
│  └─ 输出：配货确认，status=READY_TO_SHIP                         │
│                                                                  │
│  【4. 发货出库】                                                │
│  ├─ 操作：仓库人员按配货结果执行出库                             │
│  ├─ 库存：按实际SKU/仓库/数量扣减 quantity                       │
│  └─ 输出：status=DELIVERED, delivered_at 更新                  │
│                                                                  │
│  【5. 订单完成】                                                │
│  └─ 输出：status=COMPLETED, complete_time 更新                   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 配货调整流程

```
┌─────────────────────────────────────────────────────────────────┐
│                       配货调整流程                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  【待配货】→ 仓库人员开始配货                                    │
│       ↓                                                          │
│  【配货中-待确认】                                               │
│       ↓                                                          │
│  库存足够？ ──否──→ 库存不足提醒（不阻断）                        │
│       ↓是                                                         │
│  配货完成                                                         │
│       ↓                                                          │
│  销售人员确认方案（线下沟通）                                     │
│       ↓                                                          │
│  销售人员系统内记录调整结果                                       │
│       ↓                                                          │
│  仓库执行调整后的配货方案                                         │
│       ↓                                                          │
│  【待发货】                                                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 四、数据模型设计

> 若与 [architecture/DATABASE.md](./architecture/DATABASE.md) 或 Flyway 迁移脚本冲突，以迁移脚本累计结果为准。本节主要记录订单库存解耦相关的结构增量和实现要点。

### 4.1 新增表（当前已落地）

#### 4.1.1 订单发货计划表（order_delivery_plan）

```sql
CREATE TABLE order_delivery_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    order_item_id BIGINT COMMENT '原订单明细ID（可空，用于追踪原商品）',
    sku_id BIGINT NOT NULL COMMENT 'SKU ID',
    warehouse_id BIGINT COMMENT '仓库ID（配货时填写）',
    planned_qty INT NOT NULL DEFAULT 0 COMMENT '计划数量（原订单数量）',
    allocated_qty INT NOT NULL DEFAULT 0 COMMENT '配货数量（调整后数量）',
    out_qty INT NOT NULL DEFAULT 0 COMMENT '已出库数量',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING待配/ALLOCATED已配/OUT已完成',
    remark VARCHAR(255) COMMENT '备注（如调整原因）',
    tenant_id BIGINT COMMENT '租户ID',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    INDEX idx_order_id (order_id),
    INDEX idx_sku_warehouse (sku_id, warehouse_id),
    KEY idx_tenant_id (tenant_id)
) COMMENT '订单发货计划表';
```

#### 4.1.2 订单调整记录表（order_adjustment_log）

```sql
CREATE TABLE order_adjustment_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(50) COMMENT '操作人名称',
    adjustment_type VARCHAR(20) NOT NULL COMMENT '调整类型：REDUCE减数量/REPLACE替换/REFUND退款',
    original_sku_id BIGINT COMMENT '原SKU ID',
    original_quantity INT COMMENT '原数量',
    new_sku_id BIGINT COMMENT '新SKU ID（替换时使用）',
    new_quantity INT COMMENT '新数量',
    reason VARCHAR(255) COMMENT '调整原因',
    confirmed_time DATETIME COMMENT '确认时间',
    create_time DATETIME COMMENT '创建时间',
    tenant_id BIGINT COMMENT '租户ID',
    INDEX idx_order_id (order_id),
    KEY idx_tenant_id (tenant_id)
) COMMENT '订单调整记录表';
```

#### 4.1.3 库存总量预留表（inventory_global_reserve）

> 生产口径：该表作为历史兼容和后续软预留能力预留，第一版订单收款流程不再写入硬预留记录。

```sql
CREATE TABLE inventory_global_reserve (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    sku_id BIGINT NOT NULL COMMENT 'SKU ID',
    reserve_qty INT NOT NULL COMMENT '预留数量',
    released_qty INT NOT NULL DEFAULT 0 COMMENT '已释放数量',
    tenant_id BIGINT COMMENT '租户ID',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    INDEX idx_order_sku (order_id, sku_id),
    INDEX idx_sku (sku_id)
) COMMENT '库存总量预留表（跨仓）';
```

### 4.2 修改表（当前生产口径已落地）

#### 4.2.1 订单表（sale_order）

```sql
-- 新增字段
ALTER TABLE sale_order ADD COLUMN original_amount DECIMAL(12,2) COMMENT '原始订单金额' AFTER total_amount;
ALTER TABLE sale_order ADD COLUMN refund_amount DECIMAL(12,2) DEFAULT 0 COMMENT '已退款金额' AFTER original_amount;
ALTER TABLE sale_order ADD COLUMN adjustment_status VARCHAR(20) DEFAULT 'NONE' COMMENT '调整状态：NONE无调整/PENDING待确认/APPROVED已确认/COMPLETED已完成' AFTER refund_amount;

-- warehouse_id 改为可选（移除必填约束）
```

**现状**：
- 上述字段已由 `V21__order_delivery_plan.sql` 落地。
- `V29__order_quick_entry_finance.sql` 已补齐快速录单日期、类型、运费、成本和毛利字段。
- `V39__order_write_off.sql` 新增 `write_off_amount`、`write_off_reason`；`BE-124` 所需生产字段已收口。
- `V40__order_delivery_display_columns.sql` 补齐历史出库单表展示冗余列，避免出库单详情查询实体字段时报 unknown column。

#### 4.2.2 订单明细表（sale_order_item）

```sql
-- 新增字段
ALTER TABLE sale_order_item ADD COLUMN planned_quantity INT DEFAULT 0 COMMENT '计划数量（原订单数量）';
ALTER TABLE sale_order_item ADD COLUMN allocated_quantity INT DEFAULT 0 COMMENT '配货数量（调整后数量）';
ALTER TABLE sale_order_item ADD COLUMN out_quantity INT DEFAULT 0 COMMENT '已出库数量';
ALTER TABLE sale_order_item ADD COLUMN adjustment_remark VARCHAR(255) COMMENT '调整说明';
```

**现状**：
- 上述字段已由 `V21__order_delivery_plan.sql` 落地。
- 当前库中还保留 `product_id` 作为历史兼容字段。

#### 4.2.3 库存表（inventory）

```sql
-- 新增字段
ALTER TABLE inventory ADD COLUMN global_reserved_qty INT DEFAULT 0 COMMENT '全局预留数量（跨仓预留总量）';
```

**现状**：
- `global_reserved_qty` 已由 `V20__inventory_global_reserve.sql` 落地。
- `version` 已由 `V11__inventory_add_version.sql` 落地。
- `inventory_log.warehouse_id` 已由 `V22__inventory_log_warehouse_nullable.sql` 改为允许 `NULL`。

### 4.3 订单状态枚举

> 订单处理状态用于表达发货/履约进度；`payment_status` 用于表达收款状态。两者独立变化：追加收款和抹零/短款结清只改变收款状态，不自动改变发货状态；配货、发货、完成只改变履约状态，不自动改变收款金额。

```java
// 订单状态
public class OrderStatus {
    public static final int CREATED = 0;           // 创建
    public static final int PAID = 1;              // 已付款
    public static final int ADJUSTMENT_PENDING = 2; // 配货中-待确认
    public static final int READY_TO_SHIP = 3;     // 待发货
    public static final int DELIVERED = 4;         // 已发货
    public static final int COMPLETED = 5;        // 已完成
    public static final int CANCELLED = 6;         // 已取消
    public static final int RETURNING = 7;         // 退货中
    public static final int RETURNED = 8;          // 已退货
}

// 调整状态
public class AdjustmentStatus {
    public static final String NONE = "NONE";           // 无调整
    public static final String PENDING = "PENDING";      // 待确认
    public static final String APPROVED = "APPROVED";    // 已确认
    public static final String COMPLETED = "COMPLETED";  // 已完成
}

// 调整类型
public class AdjustmentType {
    public static final String REDUCE = "REDUCE";        // 减数量
    public static final String REPLACE = "REPLACE";      // 替换
    public static final String REFUND = "REFUND";          // 退款
}
```

### 4.4 发货状态与收款状态关系

| 业务动作 | 发货/履约状态 `status` | 收款状态 `payment_status` | 库存影响 |
|----------|------------------------|----------------------------|----------|
| 创建订单/快速录单 | 0 创建 | 按初始实收金额和抹零金额计算：0未付款 / 1部分收款 / 2已结清 | 无，不检查库存 |
| 确认收款 | 0 → 1 已付款/待配货 | 根据累计实收重新计算 | 无，不锁库存 |
| 追加收款 | 不变 | 根据累计实收重新计算 | 无，不锁库存 |
| 抹零/短款结清 | 不变 | 写入 `write_off_amount` 后重新计算，通常变为 2已结清 | 无，不锁库存 |
| 创建配货方案 | 1 → 2 配货中待确认 | 不变 | 只提示库存，不扣减 |
| 确认配货方案 | 2 → 3 待发货 | 不变 | 只保存实际发货方案 |
| 确认发货 | 3 → 4 已发货 | 不变 | 按实际发货明细扣减库存 |
| 完成订单 | 4 → 5 已完成 | 不变 | 无 |
| 未发货取消 | 0/1/2/3 → 6 已取消 | 不变；如已收款需走退款记录 | 无 |
| 退货入库 | 4/5 → 7 → 8 | 不变；退款单独记录 | 按退货明细增加库存 |

**收款状态计算规则**：
- 应收净额：`receivable_net_amount = max(total_amount - refund_amount - write_off_amount, 0)`。
- 尾款：`balance_amount = max(receivable_net_amount - paid_amount, 0)`。
- `paid_amount = 0`：`payment_status = 0` 未付款。
- `0 < paid_amount < receivable_net_amount`：`payment_status = 1` 部分收款。
- `paid_amount >= receivable_net_amount`：`payment_status = 2` 已结清。
- 例：订单应收 312，客户实付 310，业务确认 2 元不再追收时，记录 `write_off_amount=2`，应收净额为 310，订单显示已结清。
- 收款状态仅用于展示、筛选、统计和欠款判断，不代表库存已锁定。

---

## 五、接口设计

### 5.1 订单接口变更

#### 5.1.1 创建订单（修改）

```java
// OrderCreateDTO 修改
@Schema(description = "默认发货仓库ID（可选，后续可在配货时指定）")
private Long warehouseId;  // 改为可选

// 新增字段
@Schema(description = "原始订单金额（自动计算）")
private BigDecimal originalAmount;
```

#### 5.1.2 新增：查询配货计划

```java
/**
 * 查询订单配货计划
 * GET /api/orders/{id}/delivery-plans
 */
@GetMapping("/{id}/delivery-plans")
public R<List<OrderDeliveryPlanVO>> getDeliveryPlans(@PathVariable Long id);
```

#### 5.1.3 新增：更新配货计划

```java
/**
 * 更新配货计划（仓库分配/调整）
 * PUT /api/orders/{id}/delivery-plans
 */
@PutMapping("/{id}/delivery-plans")
public R<Void> updateDeliveryPlans(
    @PathVariable Long id,
    @RequestBody List<DeliveryPlanDTO> plans
);
```

#### 5.1.4 新增：确认配货调整

```java
/**
 * 确认配货调整方案（销售人员确认后执行）
 * POST /api/orders/{id}/confirm-adjustment
 */
@PostMapping("/{id}/confirm-adjustment")
public R<Void> confirmAdjustment(
    @PathVariable Long id,
    @RequestBody OrderAdjustmentDTO adjustment
);
```

#### 5.1.5 新增：获取调整记录

```java
/**
 * 获取订单调整记录
 * GET /api/orders/{id}/adjustment-logs
 */
@GetMapping("/{id}/adjustment-logs")
public R<List<OrderAdjustmentLogVO>> getAdjustmentLogs(@PathVariable Long id);
```

### 5.2 库存接口新增

#### 5.2.1 跨仓总量预留（历史兼容接口）

```java
/**
 * 跨仓总量预留（历史兼容；当前付款确认不调用）
 * POST /api/inventory/global-reserve
 */
@PostMapping("/global-reserve")
public R<Void> globalReserve(@RequestBody InventoryGlobalReserveDTO dto);
```

#### 5.2.2 跨仓总量释放

```java
/**
 * 跨仓总量释放（取消订单时调用）
 * POST /api/inventory/global-release
 */
@PostMapping("/global-release")
public R<Void> globalRelease(@RequestBody InventoryGlobalReleaseDTO dto);
```

#### 5.2.3 按配货计划出库（仅由订单发货事务调用）

```java
/**
 * 按配货计划出库（发货时调用）
 * 外部 POST /api/inventory/out-by-plan 已关闭，防止绕过整单状态机形成部分发货。
 * OrderService.deliverOrder() 在事务内逐条调用 InventoryService.outByPlan()。
 */
@PostMapping("/out-by-plan")
public R<Void> outByPlan(@RequestBody List<DeliveryPlanDTO> plans);
```

**方法签名：**
```java
void outByPlan(Long planId, Integer quantity, Long operatorId);
```

**当前验证逻辑**（必须按顺序执行）：
1. 按当前租户查询配货计划，校验计划、仓库、状态和剩余可出数量。
2. 获取 Redis 分布式锁：`inventory:lock:{skuId}:{warehouseId}`。
3. 按当前租户查询 SKU/仓库库存，校验 `quantity - reserved_qty >= 本次出库量`。
4. 使用带 `tenant_id` 和可用量条件的原子 SQL 扣减 `quantity` 并递增 `version`。
5. 任一计划失败时由订单发货事务整体回滚；订单行锁保证两个发货入口不会重复扣减。

**库存变动**（注意与 out() 的区别）：
| 字段 | 变动 | 说明 |
|------|------|------|
| quantity | `quantity - quantity` | 扣减实际库存 |
| reserved_qty | **不变** | 跨仓流程不涉及单仓预占 |
| global_reserved_qty | **不变** | 当前生产发货不依赖历史全局预留 |

`outByPlan()` 是当前订单确认发货的唯一库存扣减实现；旧 `out()` 不再由订单发货路径直接调用。

**日志记录**：
- changeType = `SALE_OUT`
- 备注 = `"配货计划出库"`

**当前实现补充**：
- `inventory_log.warehouse_id` 在历史跨仓预留类日志中允许为 `NULL`。
- `outByPlan()` 只扣减 `quantity`，不修改 `reserved_qty` 或 `global_reserved_qty`。

---

## 六、页面设计

### 6.1 订单列表页 - 状态筛选更新

| 状态值 | 状态名称 | 说明 |
|--------|----------|------|
| 0 | 创建 | 刚创建，待付款 |
| 1 | 已付款 | 已付款，待配货 |
| 2 | 配货中 | 配货中（库存不足需调整） |
| 3 | 待发货 | 配货完成，待出库 |
| 4 | 已发货 | 已出库 |
| 5 | 已完成 | 客户确认收货 |
| 6 | 已取消 | 已取消 |

### 6.2 订单详情页 - 新增配货调整区块

当订单状态为"配货中"时，显示调整区块：

```
┌─────────────────────────────────────────────────┐
│  ⚠️ 库存不足提醒                                │
├─────────────────────────────────────────────────┤
│  原订单商品：                                    │
│  • 6000款 × 30件 × ¥128 = ¥3,840            │
│                                                 │
│  实际库存：                                     │
│  • 6000款 × 28件（欠2件）                     │
│                                                 │
│  调整方案：                                     │
│  ○ 保持原订单数量，欠件退款                     │
│  ● 替换为其他款号                              │
│                                                 │
│  替换商品：                                     │
│  [选择SKU ▼] × [数量2]                        │
│                                                 │
│  调整说明：___________________                   │
│                                                 │
│  销售人员确认：                                 │
│  [销售人员确认] 按钮                           │
│                                                 │
└─────────────────────────────────────────────────┘
```

### 6.3 商品选择对话框（简化版）

```
┌─────────────────────────────────────────────────┐
│  选择商品                                    ✕  │
├─────────────────────────────────────────────────┤
│  [搜索商品...]                                │
│                                                 │
│  可选商品（显示跨仓总量）：                    │
│  ┌─────┐ ┌─────┐ ┌─────┐                     │
│  │T恤  │ │裤子  │ │外套  │                     │
│  │总量28│ │总量50│ │总量10│                     │
│  └─────┘ └─────┘ └─────┘                     │
│                                                 │
│  已选商品：                                    │
│  ┌─────────────────────────────────────┐      │
│  │ T恤/6000/红色/XL × 3  ¥384    [删] │      │
│  │ 裤子/6001/蓝色/L × 2  ¥400    [删] │      │
│  └─────────────────────────────────────┘      │
│                                                 │
│         [取消]  [确定添加]                     │
└─────────────────────────────────────────────────┘
```

---

## 七、并发控制设计

### 7.1 问题场景

**并发问题示例**：

```
时间线：
T1: 销售A查询商品X，跨仓总量=100件
T2: 销售B查询商品X，跨仓总量=100件
T3: 销售A下单60件 → 系统通过
T4: 销售B下单50件 → 系统也通过
T5: 实际库存只有80件 → 超卖了30件！
```

### 7.2 并发控制方案

**组合方案：数据库乐观锁 + Redis分布式锁**

```
┌─────────────────────────────────────────────────────┐
│                    请求流程                          │
├─────────────────────────────────────────────────────┤
│                                                      │
│  1. 收到下单请求                                     │
│         ↓                                           │
│  2. Redis分布式锁（防并发）                         │
│         ↓                                           │
│  3. 乐观锁更新库存（数据库层面保证）                  │
│         ↓                                           │
│  4. 释放锁                                          │
│         ↓                                           │
│  5. 返回结果                                        │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 7.3 数据模型变更

#### 7.3.1 库存表新增字段

```sql
-- 新增字段：乐观锁版本号
ALTER TABLE inventory ADD COLUMN version INT DEFAULT 0 COMMENT '乐观锁版本号';

-- 新增字段：全局预留数量（跨仓预留总量）
ALTER TABLE inventory ADD COLUMN global_reserved_qty INT DEFAULT 0 COMMENT '全局预留数量（跨仓预留总量）';
```

### 7.4 关键代码实现

#### 7.4.1 Redis分布式锁

```java
// 库存锁Key格式
private static final String SKU_LOCK_PREFIX = "sku:lock:";

// 获取商品锁
private RLock getSkuLock(Long skuId) {
    String lockKey = SKU_LOCK_PREFIX + skuId;
    return redissonClient.getLock(lockKey);
}
```

#### 7.4.2 乐观锁扣减库存

```java
/**
 * 乐观锁扣减库存
 * @param skuId SKU ID
 * @param quantity 扣减数量
 * @param expectedVersion 期望版本号
 * @return true扣减成功，false库存不足或版本不匹配
 */
public boolean decreaseWithOptimisticLock(Long skuId, Integer quantity, Integer expectedVersion) {
    String sql = """
        UPDATE inventory
        SET quantity = quantity - ?,
            version = version + 1,
            update_time = NOW()
        WHERE sku_id = ?
          AND version = ?
          AND quantity >= ?
        """;

    int rows = jdbcTemplate.update(sql, quantity, skuId, expectedVersion, quantity);
    return rows > 0;
}
```

#### 7.4.3 跨仓总量查询

```java
/**
 * 查询SKU跨仓可用总量
 * 可用量 = Σ(inventory.quantity - inventory.reserved_qty - inventory.global_reserved_qty)
 */
public Integer getGlobalAvailableQty(Long skuId) {
    String sql = """
        SELECT COALESCE(SUM(quantity - reserved_qty - global_reserved_qty), 0)
        FROM inventory
        WHERE sku_id = ? AND tenant_id = ?
        """;

    return jdbcTemplate.queryForObject(sql, Integer.class, skuId, TenantContext.getTenantId());
}
```

#### 7.4.4 订单创建并发控制

```java
@Transactional
public Long create(OrderCreateDTO dto) {
    Long tenantId = TenantContext.getTenantId();

    // 1. 校验并锁定每个SKU
    for (OrderItemDTO item : dto.getItems()) {
        RLock lock = getSkuLock(item.getSkuId());

        try {
            // 加锁（最多等待3秒，锁定10秒自动释放）
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new RuntimeException("系统繁忙，请稍后重试");
            }

            // 2. 查询跨仓可用总量
            Integer available = getGlobalAvailableQty(item.getSkuId());

            // 3. 校验库存是否足够
            if (available < item.getQuantity()) {
                throw new RuntimeException(
                    String.format("商品[%s]库存不足，可用:%d, 需要:%d",
                        item.getSkuCode(), available, item.getQuantity())
                );
            }

            // 4. 预留库存（乐观锁扣减）
            boolean success = reserveStock(item.getSkuId(), item.getQuantity());
            if (!success) {
                throw new RuntimeException("库存预留失败，请重试");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("系统繁忙，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // 5. 创建订单...
}
```

#### 7.4.5 库存预留（付款确认时）

```java
@Transactional
public void reserveGlobal(InventoryGlobalReserveDTO dto, Long operatorId) {
    for (GlobalReserveItemDTO item : dto.getItems()) {
        RLock lock = getSkuLock(item.getSkuId());

        try {
            lock.lock(10, TimeUnit.SECONDS);

            // 1. 查询跨仓可用总量
            Integer available = getGlobalAvailableQty(item.getSkuId());

            if (available < item.getQuantity()) {
                throw new RuntimeException("库存不足，无法预留");
            }

            // 2. 更新各仓库的 global_reserved_qty
            // 按仓库可用量比例分配预留
            allocateAndReserveGlobal(item.getSkuId(), item.getQuantity(), dto.getOrderId());

        } finally {
            lock.unlock();
        }
    }
}
```

### 7.5 并发场景处理

| 场景 | 处理方式 |
|------|----------|
| 两个销售同时下单同一SKU | Redis锁保证串行，乐观锁保证数据一致性 |
| 高并发大量下单 | Redis锁 + 限流 |
| 锁超时 | 10秒自动释放，防止死锁 |
| 数据库更新失败 | 乐观锁版本不匹配时重试（最多3次） |

### 7.6 限流保护

```java
// 订单创建限流：每个租户每分钟最多100单
@RateLimiter(value = 100, timeout = 1, name = "orderCreateRateLimiter")
public Long create(OrderCreateDTO dto) {
    // 业务逻辑
}
```

### 7.7 库存模块并发控制

#### 7.7.1 问题分析

库存操作当前都是"查询 → 判断 → 更新"，不是原子操作：

```java
// 当前代码示例（有问题）
public void out(InventoryOutDTO dto, Long operatorId) {
    for (InventoryOutItemDTO item : dto.getItems()) {
        // 问题：两个线程可能同时通过这个检查
        Inventory inv = inventoryMapper.selectBySkuAndWarehouse(...);
        if (inv.getQuantity() - inv.getReservedQty() < item.getQuantity()) {
            throw new RuntimeException("库存不足");  // 但实际上可能超卖
        }
        // 问题：两个线程可能同时更新
        wrapper.setSql("quantity = quantity + " + changeQty);
        inventoryMapper.update(null, wrapper);
    }
}
```

#### 7.7.2 并发问题场景

| 操作 | 并发问题 | 后果 |
|------|----------|------|
| 入库 | 双重写 | 数据覆盖 |
| 出库 | 双重扣减 | 超卖 |
| 调整 | 双重调整 | 数量不一致 |
| 预留 | 双重预留 | 超额预留 |
| 释放 | 双重释放 | 库存为负 |

#### 7.7.3 库存操作并发控制方案

**方案：乐观锁 + Redis分布式锁**

```java
// 库存操作锁Key格式
private static final String INVENTORY_LOCK_PREFIX = "inventory:lock:";

// 获取库存锁
private RLock getInventoryLock(Long skuId, Long warehouseId) {
    String lockKey = INVENTORY_LOCK_PREFIX + skuId + ":" + warehouseId;
    return redissonClient.getLock(lockKey);
}

// 出库操作并发控制示例
@Transactional
public void out(InventoryOutDTO dto, Long operatorId) {
    for (InventoryOutItemDTO item : dto.getItems()) {
        RLock lock = getInventoryLock(item.getSkuId(), dto.getWarehouseId());

        try {
            lock.lock(10, TimeUnit.SECONDS);

            // 乐观锁更新
            int rows = inventoryMapper.updateWithOptimisticLock(
                item.getSkuId(),
                dto.getWarehouseId(),
                -item.getQuantity()  // 扣减数量，负数
            );

            if (rows == 0) {
                throw new RuntimeException("库存不足或已被其他操作修改");
            }

        } finally {
            lock.unlock();
        }
    }
}
```

```sql
-- 乐观锁SQL示例
UPDATE inventory
SET quantity = quantity - ?,
    version = version + 1,
    update_time = NOW()
WHERE sku_id = ?
  AND warehouse_id = ?
  AND version = ?
  AND (quantity - reserved_qty) >= ?
```

#### 7.7.4 库存模块并发控制清单

| 操作 | 控制方式 | 锁粒度 | 乐观锁 |
|------|----------|--------|--------|
| 入库 (in) | Redis锁 | SKU+仓库 | 是 |
| 出库 (out) | Redis锁 | SKU+仓库 | 是 |
| 调整 (adjust) | Redis锁 | SKU+仓库 | 是 |
| 预留 (reserve) | Redis锁 | SKU+仓库 | 是 |
| 释放 (release) | Redis锁 | SKU+仓库 | 是 |

### 7.8 商品模块并发控制

#### 7.8.1 问题分析

商品创建和SKU生成存在竞态条件：

```java
// 当前代码示例（有问题）
public Long create(ProductCreateDTO dto) {
    // 问题：两个线程可能同时通过这个检查
    if (productMapper.selectCount(checkWrapper) > 0) {
        throw new RuntimeException("商品编码已存在");
    }
    productMapper.insert(product);  // 但两个人都可能插入成功
}

// 自动生成SKU也有同样问题
private void autoGenerateSkus(Long productId, ...) {
    // 同一商品+颜色+尺码可能重复生成
}
```

#### 7.8.2 并发问题场景

| 操作 | 并发问题 | 后果 |
|------|----------|------|
| 创建商品 | 双重检查 | 编码重复 |
| 生成SKU | 双重生成 | SKU编码重复 |

#### 7.8.3 商品模块并发控制方案

**方案：Redis分布式锁 + 数据库唯一约束**

```java
// 商品创建锁Key
private static final String PRODUCT_LOCK_PREFIX = "product:lock:";

// 创建商品并发控制
@Transactional
public Long create(ProductCreateDTO dto) {
    // 1. 加锁防止并发创建同一编码商品
    String lockKey = PRODUCT_LOCK_PREFIX + dto.getProductCode();
    RLock lock = redissonClient.getLock(lockKey);

    try {
        lock.lock(10, TimeUnit.SECONDS);

        // 2. 再次检查编码唯一性
        LambdaQueryWrapper<Product> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(Product::getProductCode, dto.getProductCode());
        if (productMapper.selectCount(checkWrapper) > 0) {
            throw new RuntimeException("商品编码已存在");
        }

        // 3. 插入商品
        Product product = new Product();
        // ... 设置属性
        productMapper.insert(product);

        // 4. 生成SKU（也需要加锁）
        if (dto.getColorIds() != null && dto.getSizeIds() != null) {
            autoGenerateSkusWithLock(product.getId(), dto.getColorIds(), dto.getSizeIds(), dto.getPrice());
        }

        return product.getId();

    } finally {
        lock.unlock();
    }
}

// SKU生成也需要加锁（防止同一商品+颜色+尺码重复）
private void autoGenerateSkusWithLock(Long productId, List<Long> colorIds, List<Long> sizeIds, BigDecimal price) {
    for (Long colorId : colorIds) {
        for (Long sizeId : sizeIds) {
            String skuLockKey = "sku:generate:lock:" + productId + ":" + colorId + ":" + sizeId;
            RLock skuLock = redissonClient.getLock(skuLockKey);

            try {
                skuLock.lock(5, TimeUnit.SECONDS);

                // 检查是否已存在
                // 插入SKU

            } finally {
                skuLock.unlock();
            }
        }
    }
}
```

#### 7.8.4 数据库唯一约束

```sql
-- 商品编码唯一约束
ALTER TABLE product ADD CONSTRAINT uk_product_code UNIQUE (product_code, tenant_id);

-- SKU编码唯一约束
ALTER TABLE product_sku ADD CONSTRAINT uk_sku_code UNIQUE (sku_code, tenant_id);

-- 复合唯一约束（商品+颜色+尺码）
ALTER TABLE product_sku ADD CONSTRAINT uk_sku_product_color_size
    UNIQUE (product_id, color_id, size_id, tenant_id);
```

#### 7.8.5 商品模块并发控制清单

| 操作 | 控制方式 | 锁粒度 | 唯一约束 |
|------|----------|--------|----------|
| 创建商品 | Redis锁 | 商品编码 | 是 |
| 更新商品 | Redis锁 | 商品ID | - |
| 生成SKU | Redis锁 | 商品+颜色+尺码 | 是 |

---

## 八、实施步骤

### Phase 0：生产口径修正（当前优先）
1. 修改 `OrderServiceImpl.confirmPayment()`：确认收款不再调用跨仓硬预留。
2. 修改追加收款和抹零/短款结清：只重算 `paid_amount` / `write_off_amount` / `payment_status`，不改变履约状态，不调用库存。
3. 修改 `outByPlan()`：只在确认发货时按实际发货明细校验和扣减库存。
4. 前端订单详情/配货页：库存不足只提示，配货方案允许保存；确认发货前再做真实出库校验。
5. 保留 `inventory_global_reserve` / `global_reserved_qty`，不做破坏性迁移。

### Phase 1：数据模型变更（历史已落地）
1. 执行数据库迁移脚本
2. 创建新表：order_delivery_plan, order_adjustment_log, inventory_global_reserve
3. 修改现有表：sale_order, sale_order_item, inventory（含乐观锁版本号）

### Phase 2：库存服务重构（历史能力保留，第一版订单流程暂停硬预留）
1. 实现 InventoryService.globalReserve()
2. 实现 InventoryService.globalRelease()
3. 实现 InventoryService.outByPlan()
4. 修改库存查询接口，支持跨仓总量查询
5. 添加Redis分布式锁集成

### Phase 3：订单服务重构（按生产口径收尾）
1. 修改 OrderServiceImpl.create() - warehouseId改为可选，添加并发控制
2. 修改 OrderServiceImpl.confirmPayment() - 只更新收款状态和待配货状态，不调用跨仓预留
3. 新增配货计划相关方法
4. 新增调整记录相关方法

### Phase 4：接口适配
1. 修改 OrderCreateDTO - warehouseId改为可选
2. 新增配货计划相关接口
3. 新增调整记录相关接口

### Phase 5：前端适配
1. 订单列表页 - 新增配货中状态筛选
2. 订单详情页 - 新增配货调整区块
3. 配货区块 - 显示库存软提示和替换/减配说明；录单商品选择不显示、不校验库存

### Phase 6：测试验证
1. 订单创建测试（不选仓库）
2. 确认收款测试（库存不足也可确认收款，不写入硬预留）
3. 配货调整测试（换款/减数量）
4. 发货出库测试（按实际配货明细扣减库存）
5. 全流程联调测试
6. 并发测试 - 模拟多用户同时下单

---

## 九、后期扩展预留

### 8.1 系统内通知功能（后期）

预留字段和接口，但不实现具体逻辑：

```java
// OrderNotificationDTO
@Schema(description = "订单通知DTO")
public class OrderNotificationDTO {
    private Long orderId;
    private String notificationType;  // LOW_STOCK/ADJUSTMENT_CONFIRM/
    private String message;
    private Long recipientId;  // 接收人
    private String recipientType;  // SALES/WAREHOUSE
}
```

### 8.2 配货推荐（后期）

系统可根据历史数据自动推荐配货方案：
- 按仓库优先级分配
- 按最近仓库分配
- 按库存余量分配

---

## 十、历史代码问题与修复计划（2026-03-26，已被生产口径替代）

> 本章保留早期问题分析作为历史记录，不代表 2026-06-21 当前实现。当前实现以本文第 0、3、4、5 节和 ROM/SOW 为准：收款不预留库存，配货只软提示，确认发货统一调用 `deliverOrder()` → `outByPlan()` 实际扣减。

### 11.1 库存锁定流程完整梳理

#### 正常订单生命周期

```
1. 销售新建订单 (status=0)
   └── 库存无变动

2. 销售确认收款（定金或全款）(status=1)
   └── 调用 reserve() → reservedQty++
   └── 日志：changeType=SALE_OUT, orderId=xxx, changeQty=-N
   └── 仓库人员能看到此订单（显示"待付尾款"或"待发货"）

3. 客户付完尾款
   └── 无库存变动（只是更新订单的已付金额）

4. 仓库配货（按仓库分组出库）
   └── 创建出库单 OrderDelivery（无库存变动）

5. 仓库确认发货
   └── 调用 out() → quantity--, reservedQty--
   └── 日志：changeType=SALE_OUT, orderId=xxx, changeQty=-N
   └── 出库单状态：2（已发货）

6. 全部出库完成后，订单自动变为"已发货"状态 (status=2)

7. 订单完成 (status=3)
   └── 库存无变动

8. 订单取消 (status=4)
   └── 调用 release() → reservedQty--
   └── 日志：changeType=SALE_CANCEL, orderId=xxx, changeQty=+N
```

#### 库存字段含义

| 字段 | 说明 | 变动规则 |
|------|------|----------|
| quantity | 库存总量 | 入库++, 出库-- |
| reservedQty | 已锁定数量 | 预留++, 释放--, 出库-- |
| available = quantity - reservedQty | 可用数量 | 计算得出 |

**注意**：`availableQty` 数据库列是冗余的，前端应计算显示。

### 11.2 当前代码问题清单

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| 1 | OrderItem 没有 warehouseId 字段 | OrderItem.java | 无法支持多仓库分配 |
| 2 | reserveInventory 用 order.warehouseId | OrderServiceImpl:376 | 所有商品按一个仓库预留 |
| 3 | out() 没有扣减 reservedQty | InventoryServiceImpl:258 | 出库后 reservedQty 不归零，导致reservedQty累计增大 |
| 4 | confirmDelivery 没有调用库存操作 | OrderDeliveryServiceImpl | 确认发货但不扣库存 |

#### 问题1详解：OrderItem 缺少 warehouseId

当前 OrderItem 只有 skuId 和 quantity：
```java
// 当前 OrderItem
private Long skuId;
private Integer quantity;
```

但实际业务中：
- 一个订单的商品可能来自**不同仓库**
- 同一个 SKU 也可能从不同仓库出

**解决方案**：OrderItem 增加 warehouseId 字段

#### 问题2详解：预留时用错仓库ID

```java
// OrderServiceImpl.reserveInventory()
reserveDTO.setWarehouseId(order.getWarehouseId()); // 错误：所有item用同一个仓库
```

**解决方案**：按 OrderItem.warehouseId 分别调用 reserve

#### 问题3详解：出库时没有扣减 reservedQty

```java
// InventoryServiceImpl.out()
wrapper.setSql("quantity = quantity + " + changeQty);
// 缺少：reserved_qty = reserved_qty + " + changeQty
```

**后果**：
- 订单完成后，reservedQty 里还有已出库的数量
- 多个订单累计后，reservedQty 可能超过实际库存

**正确做法**：
```java
wrapper.setSql("quantity = quantity + " + changeQty + ", reserved_qty = reserved_qty + " + changeQty);
```

#### 问题4详解：确认发货没有调用库存

当前 OrderDeliveryServiceImpl.confirmDelivery() 只更新状态，没有调用库存服务。

### 11.3 修复计划

#### Phase 1：数据模型修改

1. **OrderItem 新增 warehouseId 字段**
   - 数据库迁移脚本
   - OrderItem.java Entity
   - OrderCreateDTO.OrderItemDTO
   - OrderVO.OrderItemVO

2. **OrderCreateDTO 修改**
   - 每个 OrderItem 可指定 warehouseId

#### Phase 2：库存服务修复

1. **修复 out() 方法**
   - 出库时同时扣减 quantity 和 reservedQty

2. **修改 reserveInventory() / releaseInventory()**
   - 按 OrderItem.warehouseId 分别预留/释放

#### Phase 3：出库单与库存联动

1. **OrderDeliveryService.confirmDelivery()**
   - 调用 inventoryService.out() 扣库存

#### Phase 4：前端适配

1. **新建订单页**
   - 支持每个商品选择仓库

2. **订单详情页**
   - 显示出库单列表
   - 支持创建出库单

### 11.4 出库单与库存的关系

```
Order (order_id)
   └── OrderItem (sku_id, warehouse_id, quantity)
           ↓
OrderDelivery (按仓库分组，一个仓库一个出库单)
   └── OrderDeliveryItem (order_item_id, sku_id, quantity)
           ↓
InventoryService.out(skuId, warehouseId, quantity)
           ↓
Inventory quantity--, reservedQty--
InventoryLog 记录 order_id, warehouse_id, sku_id
```

### 11.5 决策记录（续）

4. **多仓库支持**：OrderItem 增加 warehouseId 字段，按仓库分别预留/出库（2026-03-26）
5. **出库扣减 reservedQty**：out() 时同时扣减 quantity 和 reservedQty（2026-03-26）
6. **出库单与库存联动**：confirmDelivery() 调用库存服务（2026-03-26）

# 订单系统问题与风险清单

> 本文档记录订单系统相关的历史问题、当前风险和仍未完成的改进项。
> 阅读时请注意每个章节的状态标签，避免把历史问题当成当前现状。

---

## 状态说明

- `✅ 已修复`：问题已在当前代码中落地解决。
- `⏳ 进行中`：已有部分设计或实现，但仍需收尾。
- `🔴 未实现`：当前代码中仍未落地。

---

## 一、库存并发控制

**状态**：`✅ 已修复`

### 问题背景

早期库存操作缺少 Redis 分布式锁和乐观锁，存在并发超卖、双重预留、双重释放风险。

### 当前现状

- `InventoryServiceImpl` 已为入库、出库、调整、预留、释放等流程增加分布式锁。
- `inventory` 表已增加 `version` 字段。
- `MybatisPlusConfig` 已启用乐观锁插件。

### 关键落地点

- `blade-backend/src/main/java/com/blade/config/RedissonConfig.java`
- `blade-backend/src/main/java/com/blade/config/MybatisPlusConfig.java`
- `blade-backend/src/main/java/com/blade/inventory/service/impl/InventoryServiceImpl.java`
- `blade-backend/src/main/resources/db/migration/V11__inventory_add_version.sql`

---

## 二、跨仓总量预留

**状态**：`✅ 已实现`

### 问题背景

早期设计要求销售开单时选仓库，与实际“先卖货、后配仓”的业务模式不一致。

### 当前现状

- 订单确认收款时已改为走跨仓总量预留。
- `inventory_global_reserve` 已创建。
- `inventory.global_reserved_qty` 已落地。
- 已实现 `globalReserve()`、`globalRelease()`、`getGlobalAvailableQty()`。

### 关键落地点

- `blade-backend/src/main/resources/db/migration/V20__inventory_global_reserve.sql`
- `blade-backend/src/main/java/com/blade/inventory/entity/InventoryGlobalReserve.java`
- `blade-backend/src/main/java/com/blade/inventory/service/impl/InventoryServiceImpl.java`
- `blade-backend/src/main/java/com/blade/order/service/impl/OrderServiceImpl.java`

---

## 三、配货计划与调整记录

**状态**：`⏳ 进行中`

### 已完成部分

- `order_delivery_plan` 已落地。
- 配货计划 CRUD 已实现。
- 调整记录已实现。
- 订单状态已支持 `ADJUSTMENT_PENDING`、`READY_TO_SHIP`。
- `blade-admin` 订单详情页已支持创建、编辑、确认、取消配货计划和查看调整记录。

### 尚未完全收尾的点

- `03-TASKS.md` 中 `BE-124` 仍显示未完成，说明订单表/明细表与现有配货设计还有补齐空间。
- 部分发货、缺货退款等后续流程仍未完整覆盖。
- 文档体系中仍有旧描述把该能力写成“缺失”，需要继续收敛。

### 关键落地点

- `blade-backend/src/main/java/com/blade/order/service/impl/OrderDeliveryPlanServiceImpl.java`
- `blade-backend/src/main/java/com/blade/order/entity/OrderDeliveryPlan.java`
- `blade-admin/src/views/orders/detail.vue`
- `blade-admin/src/api/order.ts`

---

## 四、支付状态与订单状态同步

**状态**：`✅ 已实现`

### 当前现状

- 支付状态字段和相关校验已落地。
- 确认收款会推动订单流转并执行库存预留。
- 该问题不再属于当前阻塞，但相关业务规则应以 [02-PRD.md](../02-PRD.md) 为准。

---

## 五、当前仍需关注的未完成项

### 5.1 订单表结构收尾

**状态**：`⏳ 进行中`

- `BE-124` 仍未关闭。
- 需要继续确认 `sale_order`、`sale_order_item` 与当前配货、调整、发货状态设计完全一致。

### 5.2 按配货计划出库验收

**状态**：`⏳ 进行中`

- `InventoryService.outByPlan()` 已在代码中实现。
- `03-TASKS.md` 里的 `BE-126` 仍待与验收结果对齐。
- 当前实现规则为：配货计划出库扣减 `quantity` 和 `global_reserved_qty`，不扣减 `reserved_qty`。

### 5.3 仪表盘数据权限

**状态**：`🔴 未实现`

- 当前菜单权限过滤已完成。
- 看板统计接口尚未按权限做数据过滤。

### 5.4 部分发货/退款机制

**状态**：`🔴 未实现`

- “发 28 件，欠 2 件退款”这类流程尚未形成完整闭环。
- 相关金额、发货数量、退款字段与流程仍需后续设计和实现。

---

## 六、字段与规则提示

### inventory 关键字段

| 字段 | 当前状态 | 说明 |
|------|----------|------|
| `version` | ✅ 已添加 | 乐观锁版本号 |
| `reserved_qty` | ✅ 已存在 | 单仓预留，主要用于单仓预占场景 |
| `global_reserved_qty` | ✅ 已添加 | 跨仓总量预留 |

### 当前库存规则

- 单仓预占场景使用 `reserved_qty`。
- 跨仓总量预留场景使用 `global_reserved_qty`。
- 配货计划出库当前实现扣减 `quantity + global_reserved_qty`，不扣减 `reserved_qty`。

---

## 七、推荐阅读顺序

1. [02-PRD.md](../02-PRD.md)
2. [03-TASKS.md](../03-TASKS.md)
3. [06-ORDER_INVENTORY_DESIGN.md](../06-ORDER_INVENTORY_DESIGN.md)
4. [05-CHANGELOG.md](../05-CHANGELOG.md)

如果目标是修改订单或库存代码，以上四份文档建议一起交叉阅读。

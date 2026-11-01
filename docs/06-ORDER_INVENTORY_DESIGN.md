# 订单与库存现在如何联动

> 文档状态：现行说明。核对：2026-11-02，V69 源码。原 V50 长篇方案见 [历史设计](./archive/designs/ORDER_INVENTORY_V50.md)。

录单和收款不占库存；只有选择库存履约并完成实际发货，才扣减销售库存。现行状态与金额规则分别以 [生命周期契约](./14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md) 和 [财务契约](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md) 为准。

## 当前流程

1. 草稿只暂存识别或录入结果，不进入正式订单、库存或经营统计
2. 确认草稿或新建正式订单进入 `CONFIRMED`；金额由服务端计算
3. 收款形成流水，只更新收款状态，不自动进入配货
4. `SETTLED` 后明确选择 `RECORD_ONLY` 或 `STOCK_LINKED`
5. `RECORD_ONLY` 直接完成，零库存计划、零库存流水
6. `STOCK_LINKED` 进入待配货、配货中、待发货、已发货、已完成；占位 SKU 未拆分时阻断库存履约
7. 实际销售出库由 `OrderActionService.shipOrder` 统一执行；`/api/inventory/out-by-plan` 已返回 410

## 保留的旧字段

`status`、`payment_status` 是 `OrderCompatAdapter` 写出的兼容投影，不是新的流程入口。`global_reserved_qty` 和 `inventory_global_reserve` 仍为历史/独立预留能力，不表示现行收款自动做跨仓总量锁定。

`reserved_qty` 的仓库预留接口与正式订单销售发货是不同路径。不能从表存在或某个旧方法存在，推断它仍在当前订单主流程中执行。

## 代码与验收依据

| 边界 | 主要实现 |
|---|---|
| 财务、履约动作与幂等 | `OrderActionService` |
| 配货计划 | `OrderDeliveryPlanServiceImpl` |
| SKU 是否可进入库存 | `InventorySkuEligibilityService` |
| 库存并发与日志 | `InventoryServiceImpl` |
| 占位拆分与守恒 | `OrderPlaceholderSplitService` |

近期验收覆盖重复发货、跨档口拒绝、占位阻断、仅记录订单零库存副作用、库存日志操作者和真实并发。代码审查发现的身份/动作权限问题见 [改进清单](./CODE_REVIEW_2026-11-02.md)。不要把历史“已加锁”理解为所有边界都已经验证。

完整退货、库存逆向冲销、审批回退和期间约束仍按 [回退设计](./18-ORDER_REVERSAL_APPROVAL_DESIGN.md) 单独实施，本页不把它们写成已有能力。

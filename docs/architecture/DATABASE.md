# 数据模型与 schema 来源

> 当前结构说明，2026-11-02，代码最高 Flyway V69。原手工逐字段参考已归档，避免与累计迁移产生两份冲突结构。本文不是可执行建库脚本。

精确字段、默认值、索引和约束来自 [累计迁移索引](./SCHEMA_MIGRATIONS.md) 和源码 `blade-backend/src/main/resources/db/migration/`。必须从空库累计迁移或查看目标库的 schema history；不能只执行某个 ALTER，也不能把旧参考中的 CREATE TABLE 当成最新结构。

## 主要模型

| 领域 | 表 / 关系 | 当前边界 |
|---|---|---|
| 系统 | sys_tenant、sys_user、sys_role、sys_permission、用户角色/角色权限 | JWT、可信租户上下文；权限定义全局共享，角色归属租户 |
| 商品 | product、product_sku、颜色/尺码及关联、supplier | 实际/占位 SKU 区分；不能把占位规格当真实库存履约 |
| 库存 | warehouse、inventory、inventory_log、inventory_global_reserve | 事务、锁、version；跨仓总量预留是兼容能力，不是所有订单付款必经流程 |
| 客户 | customer、customer_phone | 国际号码归一、多号码、订单关联；不是旧 client 草案 |
| 正式订单 | sale_order、sale_order_item | 主状态和收款/履约分离；商品/客户/成本等历史快照 |
| 订单财务 | order_financial_record、order_state_transition_log | 逐笔收退款/核销及状态审计；不是通用总账 |
| 草稿 | order_draft、order_draft_item | Agent/手工来源，确认前不计正式统计与库存；幂等确认 |
| 配货 | order_delivery_plan、order_adjustment_log | 关联库存订单的履约计划与 SKU/仓库数量 |
| 文件 | file_storage、file_business_bind、文件衍生物相关表 | NAS 文件、业务绑定和访问范围；以实际 SQL 表名为准 |
| 档口 | sales_outlet、sys_user_outlet、agent_key_outlet、order_outlet_change_log | 租户×档口×人员；source_outlet_id 兼容期可空 |
| Agent / WhatsApp | agent_key、wa_*、任务/推荐等表 | scope、档口约束和工作流；具体表看迁移索引 |

## 订单的当前字段

| 字段 | 含义 |
|---|---|
| fulfillment_status | CONFIRMED / WAITING_ALLOCATION / ALLOCATING / READY_TO_SHIP / SHIPPED / COMPLETED / CANCELLED |
| collection_status | UNPAID / PARTIAL / SETTLED，由金额和流水计算 |
| fulfillment_mode | UNDECIDED / STOCK_LINKED / RECORD_ONLY，默认未决定 |
| status、payment_status | 旧数字兼容投影，不驱动新业务 |
| fulfillment_decided_at/by、settled_at | 履约选择审计和首次结清时间 |
| gross_received_amount、cash_refund_amount、sales_return_amount、write_off_amount | 收款、现金退款、退货、核销独立快照 |
| source_outlet_id、source_shop | 主数据 ID 与历史名称快照；映射不得覆盖原始名称 |
| customer_phone | V69 已扩展，不沿用旧“固定 11 位”参考 |

完整规则见 [生命周期](../14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md)、[财务](../15-ORDER_FINANCE_ANALYTICS_DESIGN.md)、[档口](../20-OUTLET_ACCESS_CONTROL_DESIGN.md)。新增字段必须通过新版本迁移，不能修改已执行历史 migration。

## 租户与生产数据

受管业务表使用 tenant_id；全局定义和无该列关系表的例外由 TenantLineHandler 明确列出。缺少租户必须拒绝，不得使用默认管理员/默认租户绕过。历史订单字段回填必须在隔离副本先预演、对账并留恢复证据。

最近生产核实为 V69，不代表任何旧脚本默认版本都正确。发布与备份按 [NAS 运维](../13-NAS_PRODUCTION_OPS.md)。本轮没有迁移或修改生产数据。原参考见 [历史快照](../archive/designs/DATABASE_REFERENCE_BEFORE_2026-11-02.md)。

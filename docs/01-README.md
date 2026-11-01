# BladeProject 文档中心

> 文档状态：现行导航。更新：2026-11-02。近期主线由用户确认：稳定现有订单、库存和真实业务验收。

先区分当前业务契约、实际交付状态、后续规划和历史证据，再选择要读的文档。设计完成、代码完成、部署完成和业务验收通过是四种不同状态。

## 先读这四份

| 你要确认什么 | 入口 |
|---|---|
| 现在做什么、哪些扩展后置 | [项目范围与主线](./PROJECT_SCOPE.md) |
| 项目成熟度、主要风险与改进顺序 | [项目评估](./PROJECT_REVIEW_2026-11-02.md) |
| 当前模块、任务和交接状态 | [会话快照](./SESSION_CONTEXT.md)、[状态板](./STATUS.md)、[任务账本](./03-TASKS.md) |
| 当前订单、收款、库存等业务规则 | [PRD](./02-PRD.md) |

## 按工作选择文档

| 工作 | 文档 |
|---|---|
| 启动和测试本地环境 | [环境配置](./00-SETUP.md) |
| 开发、合并、归档分支 | [Git 工作流](./reference/GIT_BRANCH_WORKFLOW.md)、[分支盘点](./reference/GIT_INVENTORY_2026-11-02.md) |
| 多 Agent 认领与交接 | [协作规范](./reference/AGENT_COLLABORATION.md) |
| 找需要修复或优化的代码 | [代码评估与验收清单](./CODE_REVIEW_2026-11-02.md) |
| 订单状态和编辑边界 | [生命周期契约](./14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md) |
| 金额、结清和统计口径 | [订单财务契约](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md) |
| 库存履约与旧字段 | [订单库存现行说明](./06-ORDER_INVENTORY_DESIGN.md) |
| 客户维护与统计 | [客户现行说明](./08-CUSTOMER_OPTIMIZATION.md) |
| 文件中心、预览和上传 | [文件存储](./09-FILE_STORAGE_DESIGN.md)、[数字资产](./12-FILE_CENTER_ASSET_DESIGN.md) |
| 档口、人员和 Agent 数据范围 | [档口权限](./20-OUTLET_ACCESS_CONTROL_DESIGN.md) |
| API / 数据结构 | [接口参考](./reference/API_SPEC.md)、[数据库参考](./architecture/DATABASE.md)；精确字段以 DTO/Controller/Flyway 为准 |
| NAS 检查、备份和发布 | [NAS 运维](./13-NAS_PRODUCTION_OPS.md) |
| Agent Key 与纸单批量录入 | [连接手册](./19-AGENT_CONNECTION_PLAYBOOK.md)、[Key 管理](./16-AGENT_LOCAL_KEY_MANAGER.md)、[草稿操作](./17-AGENT_ORDER_DRAFT_RUNBOOK.md) |
| 排查问题 | [故障排查](./reference/TROUBLESHOOTING.md)、[订单风险清单](./reference/ORDER_SYSTEM_ISSUES.md) |

## 后置规划和历史证据

- [订单回退与审批设计](./18-ORDER_REVERSAL_APPROVAL_DESIGN.md)：设计已确认，功能尚未实现，不是现有订单退货/冲销入口
- [旧 ERP 优化蓝图](./reference/legacy-erp-to-blade-optimization-blueprint.md)：后续接替范围，不是本期上线清单
- [库存扩展候选](./backlog/INVENTORY_EXPANSION_QUESTIONS.md)：未排期问题，不构成已确认需求
- [历史档案](./archive/README.md)：完成的 SOW、阶段审核、旧方案和交付记录
- [文档目录与处理清单](./DOCUMENT_CATALOG.md)：每份文档的状态、去向及删除理由

历史文档中的“禁止发布”“尚未实施”“待 V42 迁移”只描述当时阶段。当前判断按现行契约、代码和最近发布证据完成，不能把一个旧审核结论当成今天的总状态。

## 更新规则

业务规则改动更新 PRD 和对应契约；实施状态更新任务账本及交接快照；验证证据写入 CHANGELOG。已完成的阶段材料归档，不反复堆入会话快照。新增文档先明确它是现行契约、操作手册、规划还是历史证据。

# 文档目录与整理记录

> 2026-11-02，整理基线 `132c9e6`。按用途与生命周期分类；历史材料保留证据，重复入口移除并指向唯一现行入口。

本轮移动 27 份文档（25 份进入历史区，2 份成为候选需求），将 6 份过长/过时现行文档保留历史快照后重写，另外单独保留旧 OCR 章节；从 CHANGELOG 和 TASKS 拆出两份历史明细。删除 3 份已被现行入口覆盖的重复指南。原始 docs 完整备份的 SHA-256：`7112f36df6e3a75bf49f2d58b7f9fcbe2dc18c3506cf9584da32ab0795ec4af1`；Git 历史和本轮审计附件均可追溯。

现行摘要不宣称覆盖每一个 DTO/列：接口与字段精确来源为 Controller/DTO 和累计 Flyway。需求原始讨论、决策日志和 Git 盘点是带日期证据，不自动取代当前范围。

## 删除的重复入口

| 原路径 | 现行替代 | 理由 |
|---|---|---|
| 开发者必读文档.md | [文档中心](./01-README.md) | 重复导航与旧开发约定 |
| 给Agent读的生产环境发布指南.md | [NAS 运维](./13-NAS_PRODUCTION_OPS.md) | 重复发布入口并混有旧迁移步骤 |
| reference/DEVELOPMENT_WORKFLOW.md | [协作规范](./reference/AGENT_COLLABORATION.md) | 旧分工与新分支/交接规范重复 |

删除前已备份，没有删除业务源码、迁移或原始数据。机器处理清单为 [document-actions-2026-11-02.json](./document-actions-2026-11-02.json)。

## 现行入口、契约和手册（29）

| 文件 | 用途/标题 |
|---|---|
| [00-SETUP.md](./00-SETUP.md) | 如何启动和验证本地开发环境 |
| [01-README.md](./01-README.md) | BladeProject 文档中心 |
| [02-PRD.md](./02-PRD.md) | 产品需求文档（PRD） |
| [03-TASKS.md](./03-TASKS.md) | 开发任务清单 |
| [04-REQUISITION_LOG.md](./04-REQUISITION_LOG.md) | 需求讨论记录 |
| [05-CHANGELOG.md](./05-CHANGELOG.md) | 变更记录 |
| [06-ORDER_INVENTORY_DESIGN.md](./06-ORDER_INVENTORY_DESIGN.md) | 订单与库存现在如何联动 |
| [08-CUSTOMER_OPTIMIZATION.md](./08-CUSTOMER_OPTIMIZATION.md) | 客户管理当前有哪些能力 |
| [08-PERMISSION_SYSTEM_DESIGN.md](./08-PERMISSION_SYSTEM_DESIGN.md) | 权限系统设计文档 |
| [09-FILE_STORAGE_DESIGN.md](./09-FILE_STORAGE_DESIGN.md) | 统一文件存储设计 |
| [10-AGENT_INTEGRATION_DESIGN.md](./10-AGENT_INTEGRATION_DESIGN.md) | Agent 对接设计 |
| [11-AGENT_ACCESS_GUIDE.md](./11-AGENT_ACCESS_GUIDE.md) | 外部 Agent 接入指南 |
| [12-FILE_CENTER_ASSET_DESIGN.md](./12-FILE_CENTER_ASSET_DESIGN.md) | 文件中心与数字资产设计 |
| [13-NAS_PRODUCTION_OPS.md](./13-NAS_PRODUCTION_OPS.md) | NAS 生产环境运维 |
| [14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md](./14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md) | 如何重构订单状态、收款与库存履约流程 |
| [15-ORDER_FINANCE_ANALYTICS_DESIGN.md](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md) | 如何设计订单金额、结清与经营统计 |
| [16-AGENT_LOCAL_KEY_MANAGER.md](./16-AGENT_LOCAL_KEY_MANAGER.md) | 本机 Agent Key 管理器与授权代理 |
| [17-AGENT_ORDER_DRAFT_RUNBOOK.md](./17-AGENT_ORDER_DRAFT_RUNBOOK.md) | Agent 纸单 / Excel 批量订单草稿操作手册 |
| [19-AGENT_CONNECTION_PLAYBOOK.md](./19-AGENT_CONNECTION_PLAYBOOK.md) | 让外部 Agent 连接 BladeProject |
| [20-OUTLET_ACCESS_CONTROL_DESIGN.md](./20-OUTLET_ACCESS_CONTROL_DESIGN.md) | 档口主数据与跨档口数据权限设计 |
| [21-CATALOG_WHOLESALE_DESIGN.md](./21-CATALOG_WHOLESALE_DESIGN.md) | 客户选款、批发清单和订单草稿接入契约 |
| [CODE_REVIEW_2026-11-02.md](./CODE_REVIEW_2026-11-02.md) | 代码评估与后续修复验收 |
| [DOCUMENT_CATALOG.md](./DOCUMENT_CATALOG.md) | 文档目录与整理记录 |
| [PROJECT_REVIEW_2026-11-02.md](./PROJECT_REVIEW_2026-11-02.md) | 项目评估：先完成稳定性与真实验收 |
| [PROJECT_SCOPE.md](./PROJECT_SCOPE.md) | 当前范围与工作主线 |
| [SESSION_CONTEXT.md](./SESSION_CONTEXT.md) | 当前交接快照 |
| [STATUS.md](./STATUS.md) | 项目状态总览（自动生成） |
| [testing/2026-08-30-mobile-order-lifecycle-smoke.md](./testing/2026-08-30-mobile-order-lifecycle-smoke.md) | 移动端订单生命周期切换 — 手工冒烟清单（系列 F） |
| [testing/ORDER_FULLFLOW_TEST_CASES.md](./testing/ORDER_FULLFLOW_TEST_CASES.md) | 订单系统全流程测试用例 |

## 参考与实现说明（16）

| 文件 | 用途/标题 |
|---|---|
| [architecture/ARCHITECTURE.md](./architecture/ARCHITECTURE.md) | 当前系统架构 |
| [architecture/DATABASE.md](./architecture/DATABASE.md) | 数据模型与 schema 来源 |
| [architecture/MOBILE_PWA_PLAN.md](./architecture/MOBILE_PWA_PLAN.md) | 移动端的维护范围与后续计划 |
| [architecture/SCHEMA_MIGRATIONS.md](./architecture/SCHEMA_MIGRATIONS.md) | 累计 schema 迁移索引 |
| [reference/AGENT_COLLABORATION.md](./reference/AGENT_COLLABORATION.md) | 多 Agent 联合开发协作规范 |
| [reference/API_SPEC.md](./reference/API_SPEC.md) | API 接口规范 |
| [reference/DECISIONS_LOG.md](./reference/DECISIONS_LOG.md) | 技术决策记录 |
| [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md) | Git 分支与发布工作流 |
| [reference/GIT_INVENTORY_2026-11-02.md](./reference/GIT_INVENTORY_2026-11-02.md) | Git 分支、正式/测试版本与进度盘点（2026-11-02） |
| [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md) | 当前订单与库存风险 |
| [reference/PROJECT_STRUCTURE.md](./reference/PROJECT_STRUCTURE.md) | 项目目录结构 |
| [reference/TROUBLESHOOTING.md](./reference/TROUBLESHOOTING.md) | 问题排查指南 |
| [reference/legacy-erp-functional-audit.md](./reference/legacy-erp-functional-audit.md) | 旧 ERP 全功能审计与测试操作清单 |
| [superpowers/plans/2026-08-24-whatsapp-hybrid-agent-analysis-rom-sow.md](./superpowers/plans/2026-08-24-whatsapp-hybrid-agent-analysis-rom-sow.md) | ROM/SOW：WhatsApp 混合 Agent 分析与客户推荐 v1 |
| [superpowers/plans/2026-08-24-whatsapp-local-archive-rom-sow.md](./superpowers/plans/2026-08-24-whatsapp-local-archive-rom-sow.md) | ROM/SOW：WhatsApp Mac 本地归档与 Blade ERP 集成 v1 |
| [superpowers/plans/2026-08-26-whatsapp-nas-production-integration-plan.md](./superpowers/plans/2026-08-26-whatsapp-nas-production-integration-plan.md) | WhatsApp 生产真实联调待办 |

## 后置规划与候选需求（4）

| 文件 | 用途/标题 |
|---|---|
| [18-ORDER_REVERSAL_APPROVAL_DESIGN.md](./18-ORDER_REVERSAL_APPROVAL_DESIGN.md) | 订单全流程回退、冲销与审批设计 |
| [backlog/INVENTORY_EXPANSION_QUESTIONS.md](./backlog/INVENTORY_EXPANSION_QUESTIONS.md) | 库存管理系统扩展功能问题清单 |
| [backlog/IPAD_CUSTOMER_BEHAVIOR_COLLECTION.md](./backlog/IPAD_CUSTOMER_BEHAVIOR_COLLECTION.md) | 档口 iPad 选款系统 — 客户行为数据采集方案 |
| [reference/legacy-erp-to-blade-optimization-blueprint.md](./reference/legacy-erp-to-blade-optimization-blueprint.md) | 旧 ERP 测试审计总结与 Blade 新系统优化蓝图 |

## 历史证据（35）

| 文件 | 用途/标题 |
|---|---|
| [archive/README.md](./archive/README.md) | 历史档案 |
| [archive/architecture/BACKEND_MIGRATION.md](./archive/architecture/BACKEND_MIGRATION.md) | 后端 Spring Boot 3 迁移方案 |
| [archive/changelog/2026-09-and-earlier.md](./archive/changelog/2026-09-and-earlier.md) | 2026-09 及以前变更记录 |
| [archive/designs/BUILTIN_OCR_ORIGINAL.md](./archive/designs/BUILTIN_OCR_ORIGINAL.md) | 原内置 OCR 方案（历史） |
| [archive/designs/CUSTOMER_OPTIMIZATION_ORIGINAL.md](./archive/designs/CUSTOMER_OPTIMIZATION_ORIGINAL.md) | 客户模块优化方案 |
| [archive/designs/DATABASE_REFERENCE_BEFORE_2026-11-02.md](./archive/designs/DATABASE_REFERENCE_BEFORE_2026-11-02.md) | 历史参考快照 |
| [archive/designs/MOBILE_PWA_ORIGINAL.md](./archive/designs/MOBILE_PWA_ORIGINAL.md) | 移动端 PWA 开发计划 |
| [archive/designs/ORDER_INVENTORY_V50.md](./archive/designs/ORDER_INVENTORY_V50.md) | 订单系统与库存系统解耦设计方案 |
| [archive/execution/TASK_EXECUTION_BEFORE_2026-11-02.md](./archive/execution/TASK_EXECUTION_BEFORE_2026-11-02.md) | 早期任务执行明细 |
| [archive/operations/NAS_OPS_BEFORE_2026-11-02.md](./archive/operations/NAS_OPS_BEFORE_2026-11-02.md) | NAS 生产环境运维手册（Agent 专用） |
| [archive/plans/2026-05-22-agent-gateway-style-trends.md](./archive/plans/2026-05-22-agent-gateway-style-trends.md) | Agent Gateway Style Trends Implementation Plan |
| [archive/plans/2026-06-11-quick-order-product-batch-entry-sow.md](./archive/plans/2026-06-11-quick-order-product-batch-entry-sow.md) | SOW: PC 快速录单商品级批量 SKU 录入 |
| [archive/plans/2026-06-14-product-management-v2-rom-sow.md](./archive/plans/2026-06-14-product-management-v2-rom-sow.md) | ROM/SOW: 商品管理 v2 - SKU 精细维护与商品素材管理 |
| [archive/plans/2026-06-18-file-derivatives-production-rollout.md](./archive/plans/2026-06-18-file-derivatives-production-rollout.md) | 图片派生图生产发布与历史补生成清单 |
| [archive/plans/2026-06-18-file-derivatives-v1-sow.md](./archive/plans/2026-06-18-file-derivatives-v1-sow.md) | 图片派生图第一版开发 SOW |
| [archive/plans/2026-06-21-order-inventory-soft-coupling-v1-rom-sow.md](./archive/plans/2026-06-21-order-inventory-soft-coupling-v1-rom-sow.md) | ROM/SOW: 订单与库存软解耦生产版 v1 |
| [archive/plans/2026-08-30-order-lifecycle-finance-refactor-rom-sow.md](./archive/plans/2026-08-30-order-lifecycle-finance-refactor-rom-sow.md) | ROM/SOW：订单生命周期、财务与统计大重构 |
| [archive/plans/2026-08-30-order-refactor-agent-execution-board.md](./archive/plans/2026-08-30-order-refactor-agent-execution-board.md) | 如何由实现 Agent 完成订单大重构 |
| [archive/plans/2026-08-30-order-refactor-final-delivery-report.md](./archive/plans/2026-08-30-order-refactor-final-delivery-report.md) | 订单大重构连续实施 — 最终交付报告 |
| [archive/plans/2026-08-30-order-refactor-sow0-baseline-audit.md](./archive/plans/2026-08-30-order-refactor-sow0-baseline-audit.md) | ORDER-SOW-0 只读基线审计报告（订单生命周期、财务与统计大重构） |
| [archive/plans/2026-08-30-order-refactor-zcode-long-run-task.md](./archive/plans/2026-08-30-order-refactor-zcode-long-run-task.md) | Z Code 长任务：订单生命周期、财务、履约与统计重构连续实施 |
| [archive/plans/2026-09-20-outlet-access-control-rom-sow.md](./archive/plans/2026-09-20-outlet-access-control-rom-sow.md) | ROM/SOW：档口主数据、用户多档口与数据权限边界 |
| [archive/plans/2026-09-20-outlet-series-a-delivery.md](./archive/plans/2026-09-20-outlet-series-a-delivery.md) | Series A 交付报告：档口数据模型与兼容迁移 |
| [archive/plans/2026-09-21-outlet-integrity-audit-remediation-1.md](./archive/plans/2026-09-21-outlet-integrity-audit-remediation-1.md) | 档口完整性审计整改报告（第一批） |
| [archive/plans/2026-09-21-outlet-production-release-checklist.md](./archive/plans/2026-09-21-outlet-production-release-checklist.md) | 档口模块生产副本预演 / 备份 / 灰度 / 回滚 checklist（只写不执行） |
| [archive/plans/2026-09-21-outlet-series-b1-delivery.md](./archive/plans/2026-09-21-outlet-series-b1-delivery.md) | Series B1 交付报告：档口主数据与用户授权（后端） |
| [archive/plans/2026-09-21-outlet-series-b2-delivery.md](./archive/plans/2026-09-21-outlet-series-b2-delivery.md) | Series B2 交付报告：档口管理与用户档口授权（前端） |
| [archive/plans/2026-09-21-outlet-series-c-delivery.md](./archive/plans/2026-09-21-outlet-series-c-delivery.md) | Series C 交付报告：统一后端数据访问策略与授权矩阵 |
| [archive/plans/2026-09-21-outlet-series-d-delivery.md](./archive/plans/2026-09-21-outlet-series-d-delivery.md) | Series D 交付报告：订单与草稿档口交互 |
| [archive/plans/2026-09-21-outlet-series-e1-delivery.md](./archive/plans/2026-09-21-outlet-series-e1-delivery.md) | Series E1 交付报告：统计/仪表盘/订单导出档口隔离与统计筛选 |
| [archive/plans/2026-09-21-outlet-series-e2-delivery.md](./archive/plans/2026-09-21-outlet-series-e2-delivery.md) | Series E2 交付报告：文件中心与订单/草稿图片档口权限闭环 |
| [archive/plans/2026-09-21-outlet-series-e3-delivery.md](./archive/plans/2026-09-21-outlet-series-e3-delivery.md) | Series E3 交付报告：Agent Key 档口范围、capabilities/outlets 与全出口反泄露 |
| [archive/plans/2026-09-21-outlet-series-f-local-delivery.md](./archive/plans/2026-09-21-outlet-series-f-local-delivery.md) | Series F 本地交付报告：历史档口回填预演、初始授权建议包与本地回归/性能预演 |
| [archive/plans/2026-09-22-order-entry-customer-fixes-production-release.md](./archive/plans/2026-09-22-order-entry-customer-fixes-production-release.md) | 订单录入、订单可编辑窗口与客户统计修复生产发布记录 |
| [archive/snapshots/SESSION_CONTEXT_BEFORE_2026-11-02.md](./archive/snapshots/SESSION_CONTEXT_BEFORE_2026-11-02.md) | 当前会话上下文 |

## 后续维护规则

现行契约随业务改动更新；实施状态只在任务、方向数据和交接快照维护；验证写入 CHANGELOG。已结束阶段报告归档，候选需求保留在 backlog。删/移动文档后运行 `node scripts/check-docs.mjs`；改变任务或方向状态后运行 `node scripts/gen-status.mjs`。所有生成和检查均为本地只读业务数据操作。

## 子项目与部署入口

| 文件 | 状态 |
|---|---|
| [README.md](../README.md) | 现行入口；与文档中心同步 |
| [CLAUDE.md](../CLAUDE.md) | 现行入口；与文档中心同步 |
| [blade-backend/README.md](../blade-backend/README.md) | 现行入口；与文档中心同步 |
| [blade-backend/CLAUDE.md](../blade-backend/CLAUDE.md) | 现行入口；与文档中心同步 |
| [blade-admin/README.md](../blade-admin/README.md) | 现行入口；与文档中心同步 |
| [blade-admin/CLAUDE.md](../blade-admin/CLAUDE.md) | 现行入口；与文档中心同步 |
| [blade-mobile/README.md](../blade-mobile/README.md) | 现行入口；与文档中心同步 |
| [blade-mobile/CLAUDE.md](../blade-mobile/CLAUDE.md) | 现行入口；与文档中心同步 |
| [deploy/nas/README.md](../deploy/nas/README.md) | 现行入口；与文档中心同步 |
| [tools/blade-agent-key-manager/README.md](../tools/blade-agent-key-manager/README.md) | 现行入口；与文档中心同步 |

前端各 TEST_CASES/TEST_TEMPLATE 为手工验收模板或历史记录，保留在对应源码目录；不能当作当前代码版本已通过测试的证据。Agent skills 和 stitch 原型的上游说明不纳入业务文档生命周期。

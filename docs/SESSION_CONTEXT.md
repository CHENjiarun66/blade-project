# 当前会话上下文

> 本文件是项目的快速状态快照，用于新 AI 和新会话快速接手。
> 这里只保留摘要信息；任务明细以 `03-TASKS.md` 为准，变更历史以 `05-CHANGELOG.md` 为准。

---

## 项目基本信息

| 项目 | 值 |
|------|---|
| 项目名称 | BladeProject |
| 启动日期 | 2026-03-21 |
| 当前阶段 | 后端核心模块、PC 管理端主要业务页面、库存并发控制、跨仓总量预留、配货计划、权限基础能力、订单编辑和追加收款均已落地；移动端继续开发中 |
| 下一步 | 订单库存解耦收尾（BE-124、BE-126）、看板完善、移动端页面继续推进、个人中心与 OCR 待开发 |

---

## 项目路径

| 项目 | 路径 |
|------|------|
| BladeProject 主目录 | `./` |
| 文档中心 | `./docs/` |
| 后端 | `./blade-backend/` |
| 移动端 | `./blade-mobile/` |
| PC 管理端 | `./blade-admin/` |
| 共享类型 | `./packages/types/` |
| Stitch 原型 | `./stitch/` |

---

## 单一事实来源

| 信息类型 | 以此文档为准 |
|-----------|--------------|
| 技术栈与业务规则 | [02-PRD.md](./02-PRD.md) |
| 当前任务进度 | [03-TASKS.md](./03-TASKS.md) |
| 最近变更历史 | [05-CHANGELOG.md](./05-CHANGELOG.md) |
| 快速接手摘要 | 本文档 |

---

## 当前摘要

### 已完成的关键能力

- 后端商品、库存、订单、客户、权限、看板接口主体已完成。
- 库存并发控制已完成，包含 Redis 分布式锁和乐观锁。
- 跨仓总量预留已完成，`inventory_global_reserve` 和 `global_reserved_qty` 已落地。
- 配货计划与调整记录已落地，订单支持 `ADJUSTMENT_PENDING`、`READY_TO_SHIP` 等中间状态。
- 订单状态机 4 项缺陷已修复（paymentStatus 同步、adjustmentStatus 初始化、减配释放多余预留、取消条件校验）。
- 订单编辑功能已实现（客户信息/送货/备注/图片，支持 status>=4 禁止修改）。
- 追加收款功能已实现（创建状态且未付全款可累加 paidAmount，自动更新 paymentStatus）。
- `blade-admin` 已完成订单、库存、商品、客户、系统管理等主要页面。
- 前端菜单权限过滤已完成，系统可按权限展示菜单和路由。
- `packages/types` 已搭建并被移动端集成使用。

### 仍在进行或未完成的事项

- `BE-124`：订单相关表结构补充配货/调整字段，任务仍未完成。
- `BE-126`：按配货计划出库方法虽已在代码中实现，但 `TASKS` 仍需结合验收结果收敛状态。
- 仪表盘数据权限尚未实现。
- 移动端页面开发仍在继续。
- 个人中心、OCR 拍照录单等任务仍未完成。

---

## 当前阻塞与风险

| 问题 | 优先级 | 状态 | 说明 |
|------|--------|------|------|
| 仪表盘数据权限 | P2 | 🔴 未实现 | 后端统计接口尚未按权限过滤数据 |
| 订单表结构收尾 | P1 | ⏳ 进行中 | `BE-124` 仍需与当前配货实现完全对齐 |
| 文档状态漂移 | P1 | ⏳ 进行中 | 已发现多份入口文档与代码和任务状态不一致 |

**说明**：
- 订单与库存开发前，优先阅读 [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md) 和 [06-ORDER_INVENTORY_DESIGN.md](./06-ORDER_INVENTORY_DESIGN.md)。
- 当前代码真相优先于过时文档；若发现冲突，以 `TASKS + CHANGELOG + 代码实现` 交叉核对。

---

## 最近完成的代表性能力

### 配货计划与订单状态扩展

- 后端已实现 `OrderDeliveryPlanService`、配货计划 CRUD、调整记录、确认/取消调整。
- 订单状态已扩展到包含 `ADJUSTMENT_PENDING` 和 `READY_TO_SHIP`。
- `blade-admin` 订单详情页已支持创建、编辑、确认、取消配货计划和查看调整记录。

### 订单编辑与追加收款

- 订单列表页新增编辑按钮，弹窗顶部显示订单上下文摘要（订单号/状态/金额），支持编辑客户信息/送货方式/备注/图片链接。
- 订单详情页新增"追加收款"按钮（创建状态且未付全款时显示），弹窗显示当前已付/待付余额，输入本次收款金额后累加到 paidAmount，paymentStatus 自动更新。
- 后端 `GlobalExceptionHandler` 补充 `RuntimeException` 专项处理，业务校验错误不再返回 500。

### 跨仓总量预留

- `inventory` 表已增加 `global_reserved_qty`。
- 已实现 `globalReserve`、`globalRelease`、`getGlobalAvailableQty`。
- 订单确认收款改为走跨仓总量预留，不再绑定单仓库预占。

### 权限基础能力

- 后端权限表、角色权限关系、权限判断逻辑已完成。
- `blade-admin` 已落地系统管理页和菜单权限过滤。

---

## 快捷索引

| 你想做的事 | 看这个 |
|-----------|--------|
| 了解项目入口与阅读顺序 | [01-README.md](./01-README.md) |
| 了解业务与技术规则 | [02-PRD.md](./02-PRD.md) |
| 查看任务状态 | [03-TASKS.md](./03-TASKS.md) |
| 查看最近变更 | [05-CHANGELOG.md](./05-CHANGELOG.md) |
| 看项目目录结构 | [reference/PROJECT_STRUCTURE.md](./reference/PROJECT_STRUCTURE.md) |
| 查订单/库存设计 | [06-ORDER_INVENTORY_DESIGN.md](./06-ORDER_INVENTORY_DESIGN.md) |
| 查已知问题和历史坑 | [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md) |
| 排查常见环境问题 | [reference/TROUBLESHOOTING.md](./reference/TROUBLESHOOTING.md) |

---

## 接手建议

如果你是新接手的 AI，推荐阅读顺序：

1. [SESSION_CONTEXT.md](./SESSION_CONTEXT.md)
2. [01-README.md](./01-README.md)
3. [02-PRD.md](./02-PRD.md)
4. [03-TASKS.md](./03-TASKS.md)
5. 订单/库存相关开发再补读 [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md)

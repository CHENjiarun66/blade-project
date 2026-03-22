# 当前会话上下文

> **本文件是项目的"状态快照"。**
> 每次新 AI 来，第一时间读本文档，立刻知道项目当前状态。
> 新 AI 不需要翻完整套文档，用这份文档就能秒接。
>
> **维护规则**：每次任务状态变更、每次需求讨论后，必须同步更新本文档。

---

## 项目基本信息

| 项目 | 值 |
|------|---|
| 项目名称 | BladeProject |
| 启动日期 | 2026-03-21 |
| 当前阶段 | 后端核心模块完成，移动端骨架完成，blade-admin 骨架完成，业务页面开发中 |
| 下一步 | blade-admin 业务页面开发（订单/库存/商品/客户） |

---

## 项目路径

| 项目 | 路径 |
|------|------|
| BladeProject 主目录 | ../ |
| 后端 | ../blade-backend/ |
| 移动端 | ../blade-mobile/ |
| PC 管理端 | ../blade-admin/ |
| 共享包 | ../packages/types/ (已完成) |
| 文档中心 | ./ |

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 移动端 | Vue3 + Vite + TypeScript + PWA + Vuetify3 |
| PC 管理端 | Vue3 + Vite + TypeScript + Element Plus |
| 后端 | Spring Boot 3.2 + Spring Security + MyBatis-Plus |
| 数据库 | MySQL 8 + Redis 7 |
| AI 工具 | Claude Code |

详见：[02-PRD.md](./02-PRD.md)

---

## 最新进度（最后更新：2026-03-22）

### 总体进度

| 阶段 | 状态 | 说明 |
|------|------|------|
| 技术方案讨论 | ✅ 完成 | 多 Agent 团队讨论，决策已锁定 |
| 文档体系建设 | ✅ 完成 | 完整文档体系建立 |
| 后端骨架搭建 | ✅ 完成 | BE-001 ~ BE-009 完成 |
| 商品模块开发 | ✅ 完成 | BE-201 ~ BE-206 完成 |
| 库存模块开发 | ✅ 完成 | BE-301 ~ BE-309 完成 |
| 订单系统开发 | ✅ 完成 | BE-101 ~ BE-110 重构完成 |
| 移动端骨架搭建 | ✅ 完成 | FE-001, FE-005~FE-007 完成 |
| 移动端页面开发 | ⏳ 进行中 | FE-101~FE-103 等页面骨架完成 |
| packages/types 共享类型 | ✅ 完成 | auth/order/inventory/product 类型定义 |
| PC 管理端骨架搭建 | ✅ 完成 | blade-admin 从零搭建完成，登录/布局/仪表盘已完成 |
| PC 管理端业务开发 | ⏳ 进行中 | BA-201~BA-603 待开发 |
| 客户模块开发 | ⏳ 待开始 | BE-401 |
| 看板系统开发 | ⏳ 待开始 | BE-501 ~ BE-503 |

---

## 最近讨论（最新在前）

### [2026-03-22] 需求 #006 - PC 管理端架构方案

**结论**：采用独立项目 blade-admin（方案 B），不集成到 blade-mobile

**方案**：
- blade-admin 作为独立 Vue3 项目
- 使用 Element Plus 作为 PC 端 UI
- 通过 Monorepo 结构共享 API 类型
- blade-mobile 专注移动端，blade-admin 专注 PC 端

### [2026-03-21] 需求 #005 - AI 开发规则体系

**结论**：建立完整的需求讨论 → PRD 锁定 → 任务驱动 → 变更记录的流程

**核心机制**：
- 需求讨论在 04-REQUISITION_LOG.md
- PRD 是开发唯一依据
- AI 自主从 03-TASKS.md 领任务
- 交接必须同步文档

---

## 当前阻塞问题

（暂无）

---

## 经验教训记录（2026-03-22）

### vben-admin 模板问题

**问题**：blade-admin 使用 vben-admin 模板时，Node 22.22.0 下 sass-embedded 报错崩溃

**解决**：设置 `SASS_LOGGER=javascript` 环境变量后再运行

**教训**：
1. 推荐技术方案前必须实际测试
2. 复杂 monorepo 迁移难度高
3. Agent Teams 讨论结论必须包含验证结果

详见：[reference/TROUBLESHOOTING.md](./reference/TROUBLESHOOTING.md)

---

## 待你确认的事项

（暂无待确认事项）

---

## 快捷索引

| 你想做的事 | 看这个 |
|-----------|--------|
| 了解项目全貌 | [01-README.md](./01-README.md) |
| 了解要开发什么 | [02-PRD.md](./02-PRD.md) |
| 查看任务清单 | [03-TASKS.md](./03-TASKS.md) |
| 提出新需求 | 直接告诉我，或写 [04-REQUISITION_LOG.md](./04-REQUISITION_LOG.md) |
| 查看变更历史 | [05-CHANGELOG.md](./05-CHANGELOG.md) |
| 排查问题 | [reference/TROUBLESHOOTING.md](./reference/TROUBLESHOOTING.md) |
| 了解 API 接口 | [reference/API_SPEC.md](./reference/API_SPEC.md) |

---

## 给接手的 AI

如果你是新接手的 AI，请按以下顺序阅读：

1. **本文档（SESSION_CONTEXT.md）** — 快速了解当前状态
2. **[01-README.md](./01-README.md)** — 了解工作流程
3. **[02-PRD.md](./02-PRD.md)** — 了解要开发什么
4. **[03-TASKS.md](./03-TASKS.md)** — 领取任务，开始执行

你不需要读完所有文档才能工作。读完上述 4 份文档，你就可以开始了。

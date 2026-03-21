# BladeProject AI 开发规范

> **所有 AI 开发必须遵守本文档。**
> 新会话、新 AI 必须首先阅读本文档。

---

## 一、快速开始

**新 AI 必读顺序**：

1. 📋 [docs/01-README.md](./docs/01-README.md) — 项目结构和工作流程
2. 📝 [docs/02-PRD.md](./docs/02-PRD.md) — 产品需求文档（开发依据）
3. ✅ [docs/03-TASKS.md](./docs/03-TASKS.md) — 开发任务清单（领取任务）

---

## 二、工作流程

```
需求提出 → docs/04-REQUISITION_LOG.md（讨论）
    ↓
需求确认 → docs/02-PRD.md（锁定）
    ↓
拆解任务 → docs/03-TASKS.md（领取 + 执行）
    ↓
变更记录 → docs/05-CHANGELOG.md（同步）
```

---

## 三、核心规则

### 规则 1：PRD 是唯一依据

**所有开发必须严格按 [02-PRD.md](./docs/02-PRD.md) 执行**。不擅自添加/修改功能。

### 规则 2：任务驱动

从 [03-TASKS.md](./docs/03-TASKS.md) 领取任务，完成后更新状态。

### 规则 3：变更必须记录

需求变更、架构变更、决策变更必须记录到 [05-CHANGELOG.md](./docs/05-CHANGELOG.md)。

### 规则 4：遇到问题先查文档

1. [docs/reference/TROUBLESHOOTING.md](./docs/reference/TROUBLESHOOTING.md)
2. [docs/reference/DECISIONS_LOG.md](./docs/reference/DECISIONS_LOG.md)
3. 最后才向用户提问

### 规则 5：交接必须同步

任务完成后必须同步更新：
- 任务状态（03-TASKS.md）
- 变更记录（05-CHANGELOG.md）

---

## 四、技术栈（已锁定，禁止更改）

| 层级 | 技术 |
|------|------|
| 移动端 | Vue3 + Vite + TypeScript + PWA + Vuetify3 |
| 后端 | Spring Boot 3 + Spring Security + MyBatis-Plus |
| AI 工具 | Claude Code（主力） |

详见：[docs/02-PRD.md](./docs/02-PRD.md)

---

## 五、快捷指令

如需快速让新 AI 了解项目，发送：

```
请先阅读 ./docs/01-README.md
然后阅读 ./docs/02-PRD.md
最后查看 ./docs/03-TASKS.md
```

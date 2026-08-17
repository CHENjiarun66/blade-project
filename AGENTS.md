# BladeProject AI 开发规范

> **所有 AI 开发必须遵守本文档。**
> 新会话、新 AI 必须首先阅读本文档。

---

## 一、快速开始

**新 AI 必读顺序**：

1. 📋 [docs/SESSION_CONTEXT.md](./docs/SESSION_CONTEXT.md) — 当前状态快照
2. 📊 [docs/STATUS.md](./docs/STATUS.md) — 项目状态总览（自动生成，一眼看清做了什么/在做什么/没做什么）
3. 📋 [docs/01-README.md](./docs/01-README.md) — 项目结构和工作流程
4. 📝 [docs/02-PRD.md](./docs/02-PRD.md) — 产品需求文档（开发依据）
5. ✅ [docs/03-TASKS.md](./docs/03-TASKS.md) — 开发任务清单（领取任务）
6. 🌿 [docs/reference/GIT_BRANCH_WORKFLOW.md](./docs/reference/GIT_BRANCH_WORKFLOW.md) — 分支、集成测试、GitHub 同步与 NAS 发布规范
7. 🤝 [docs/reference/AGENT_COLLABORATION.md](./docs/reference/AGENT_COLLABORATION.md) — 双 Agent（Codex + DeepSeek）联合开发同步协议

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

### 规则 6：分支和发布必须受控

开发前必须确认当前 Git 分支和工作区状态。新功能默认使用 `feature/*` 分支，集成测试使用 `develop`，上线候选使用 `release/*`，NAS 生产环境只部署 `master`。详细规则见 [docs/reference/GIT_BRANCH_WORKFLOW.md](./docs/reference/GIT_BRANCH_WORKFLOW.md)。

### 规则 7：双 Agent 联合开发必须同步

Codex、DeepSeek（DSH）及其他 Agent 工具联合开发时，必须遵守 [docs/reference/AGENT_COLLABORATION.md](./docs/reference/AGENT_COLLABORATION.md)。默认使用独立 Git worktree；只有明确约定时才允许共享同一工作目录：

- **任务与文件范围认领**：开始任务前在 TASKS.md 标记执行人，并注明主要修改目录/文件；一个任务和一个核心文件同一时刻只允许一个主 Agent 修改。
- **隔离开发**：不同 Agent 不得依赖同一工作目录中的未提交文件；每个 Agent 使用自己的 `feature/*` 分支和 worktree。
- **单一集成人**：`develop`、`release/*`、`master` 的合并和发布由同一时间唯一指定的集成人执行。
- **提交标注**：commit message 末尾必须带执行人后缀，如 `[codex]`、`[dsh]`、`[claude]`。
- **交接同步**：收工必须提交并 push，更新 TASKS.md、05-CHANGELOG.md；里程碑或会话结束时再更新 SESSION_CONTEXT.md，并运行状态看板脚本。
- **验证结果必填**：变更记录必须包含实际执行的验证命令与结果，不能只写"已测试"。

### 规则 8：交接必须刷新状态看板

每次任务交接 / 收工时运行 `node scripts/gen-status.mjs` 刷新项目状态看板：

- 数据源：`docs/03-TASKS.md`（脚本只读，不修改业务数据）
- 产物：`docs/STATUS.md`（提交入库）、`outputs/status.html`（本地可视化看板，浏览器打开）
- 刷新后必须把 `docs/STATUS.md` 的变更一并 commit + push

---

## 四、技术栈（已锁定，禁止更改）

| 层级 | 技术 |
|------|------|
| 移动端 | Vue3 + Vite + TypeScript + PWA + Vuetify 4 |
| PC 管理端 | Vue3 + Vite + TypeScript + Element Plus |
| 后端 | Spring Boot 3 + Spring Security + MyBatis-Plus |
| AI 工具 | Codex（主力） |

详见：[docs/02-PRD.md](./docs/02-PRD.md)

---

## 五、快捷指令

如需快速让新 AI 了解项目，发送：

```
请先阅读 ./docs/01-README.md
然后阅读 ./docs/02-PRD.md
最后查看 ./docs/03-TASKS.md
```

---

## 六、Agent Teams 使用规范

### 6.1 何时使用

使用 Agent Teams 进行复杂技术方案的讨论和决策，如：
- 新技术选型（前端框架、UI 库等）
- 架构方案讨论（微服务拆分、多租户设计等）
- 需要多角度评估的决策

### 6.2 讨论规范

**必须包含验证环节**：
1. 讨论结论必须包含**实际验证结果**
2. 在目标环境测试后再给出最终推荐
3. 验证内容包括：依赖兼容性、运行稳定性、迁移难度

**禁止**：
- 只分析不验证就下结论
- 推荐未在实际环境测试过的方案
- 低估复杂模板（monorepo、大量依赖）的迁移风险

### 6.3 经验规则

| 情况 | 要求 |
|------|------|
| 推荐第三方模板/框架 | 必须在目标 Node/Java 版本下实际运行测试 |
| 复杂项目（monorepo） | 必须评估迁移难度和潜在兼容性问题 |
| 讨论结论 | 必须包含验证结果，不只是方案建议 |

详见：[docs/reference/TROUBLESHOOTING.md](./docs/reference/TROUBLESHOOTING.md)（第一节：经验教训）

---

## 七、blade-admin 前端开发模式

### 7.1 边开发边封装原则

**不预先封装组件，先做业务，在开发过程中识别重复模式并抽取。**

详见：[blade-admin/CLAUDE.md](./blade-admin/CLAUDE.md)

### 7.2 开发阶段

**阶段一**：开发第一个业务模块（订单管理），写出重复代码
**阶段二**：识别重复模式，抽取公共组件
**阶段三**：用组件重构订单模块
**阶段四**：用组件快速搭建其他模块

### 7.3 组件抽取时机

| 发现重复 | 抽取为 |
|---------|-------|
| 表格 + 筛选 + 分页 | `DataTable` |
| 状态标签显示 | `StatusTag` |
| 状态操作按钮组 | `StatusActions` |
| 弹窗表单 | `DialogForm` |
| 图片上传 | `ImageUploader` |

详见：[blade-admin/CLAUDE.md](./blade-admin/CLAUDE.md)

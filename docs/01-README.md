# BladeProject 文档中心

> 所有 AI 和开发者的主导航页。

---

## 快速开始（新 AI/新会话必读）

**第一步**：阅读 [SESSION_CONTEXT.md](./SESSION_CONTEXT.md) — 项目当前状态快照

**第二步**：阅读 [产品需求文档（PRD）](./02-PRD.md) — 业务规则和技术栈依据

**第三步**：阅读 [开发任务清单](./03-TASKS.md) — 当前进度和任务状态

**第四步**：需要启动环境时，再看 [环境配置指南](./00-SETUP.md)

**第五步**：需要开发、合并或上线时，必须阅读 [Git 分支与发布工作流](./reference/GIT_BRANCH_WORKFLOW.md)

---

## 项目结构

```text
BladeProject/
├── docs/                 # 文档中心
├── blade-backend/        # 后端
├── blade-mobile/         # 移动端 PWA
├── blade-admin/          # PC 管理端
├── packages/types/       # 共享类型
└── stitch/               # 原型工程
```

详细结构见 [reference/PROJECT_STRUCTURE.md](./reference/PROJECT_STRUCTURE.md)。

---

## 你通常会用到的文档

| 场景 | 文档 |
|------|------|
| 快速接手当前状态 | [SESSION_CONTEXT.md](./SESSION_CONTEXT.md) |
| 确认业务规则和技术栈 | [02-PRD.md](./02-PRD.md) |
| 查看任务状态 | [03-TASKS.md](./03-TASKS.md) |
| 查看变更历史 | [05-CHANGELOG.md](./05-CHANGELOG.md) |
| 查看项目结构 | [reference/PROJECT_STRUCTURE.md](./reference/PROJECT_STRUCTURE.md) |
| 开发分支、测试集成、上线发布 | [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md) |
| 查订单/库存设计 | [06-ORDER_INVENTORY_DESIGN.md](./06-ORDER_INVENTORY_DESIGN.md) |
| 查图片/附件上传与存储设计 | [09-FILE_STORAGE_DESIGN.md](./09-FILE_STORAGE_DESIGN.md) |
| 查文件中心/数字资产/客户展示页设计 | [12-FILE_CENTER_ASSET_DESIGN.md](./12-FILE_CENTER_ASSET_DESIGN.md) |
| 查 NAS 生产环境发布/运维 | [13-NAS_PRODUCTION_OPS.md](./13-NAS_PRODUCTION_OPS.md) |
| 查外部 AI Agent 对接设计 | [10-AGENT_INTEGRATION_DESIGN.md](./10-AGENT_INTEGRATION_DESIGN.md) |
| 接入外部 AI Agent | [11-AGENT_ACCESS_GUIDE.md](./11-AGENT_ACCESS_GUIDE.md) |
| 双 Agent 协作同步（Codex + DeepSeek） | [reference/AGENT_COLLABORATION.md](./reference/AGENT_COLLABORATION.md) |
| 排查问题 | [reference/TROUBLESHOOTING.md](./reference/TROUBLESHOOTING.md) |
| 查看技术决策 | [reference/DECISIONS_LOG.md](./reference/DECISIONS_LOG.md) |

---

## AI 开发规则

### 规则 1：PRD 是唯一依据

开发前必须阅读 [02-PRD.md](./02-PRD.md)。功能开发严格按 PRD 执行，不擅自添加或修改功能。

### 规则 2：任务驱动

从 [03-TASKS.md](./03-TASKS.md) 领取任务，完成后同步更新状态。

### 规则 3：变更必须记录

需求、架构和关键决策变化需要同步到 [05-CHANGELOG.md](./05-CHANGELOG.md)。

### 规则 4：遇到问题先查文档

1. [reference/TROUBLESHOOTING.md](./reference/TROUBLESHOOTING.md)
2. [reference/DECISIONS_LOG.md](./reference/DECISIONS_LOG.md)
3. 仍无法确认时再向用户提问

### 规则 5：分支与发布必须受控

开发前必须确认当前 Git 分支。新功能默认从 `master` 或 `develop` 创建 `feature/*` 分支；NAS 生产环境只部署 `master`；合并、发布和 GitHub push 规则按 [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md) 执行。

---

## 相关项目路径

| 项目 | 路径 |
|------|------|
| BladeProject 主目录 | `../` |
| 文档中心 | `./` |
| 后端 | `../blade-backend/` |
| 移动端 PWA | `../blade-mobile/` |
| PC 管理端 | `../blade-admin/` |
| 共享类型 | `../packages/types/` |
| 原 Blade 项目（参考） | `/Users/chenjiarun/Documents/Blade/` |

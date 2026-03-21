# BladeProject 文档中心

> 所有 AI 和开发者必须阅读的入口文档。

---

## 快速开始（新 AI/新会话必读）

**第一步**：阅读 [SESSION_CONTEXT.md](./SESSION_CONTEXT.md) — 项目当前状态快照

**第二步**：阅读 [环境配置指南](./00-SETUP.md) — 确保开发环境就绪

**第三步**：阅读 [产品需求文档（PRD）](./02-PRD.md)

**第四步**：阅读 [开发任务清单](./03-TASKS.md)

**第五步**：根据任务状态，领取并执行开发任务

---

## 项目结构

```
BladeProject/
├── docs/                           # 文档中心
│   ├── 开发者必读文档.md           # 开发者指南（给人类看）
│   ├── 01-README.md               # 本文件（入口）
│   ├── SESSION_CONTEXT.md          # 当前状态快照（新 AI 必读）
│   ├── 02-PRD.md                  # 产品需求文档（开发依据）
│   ├── 03-TASKS.md                # 开发任务清单
│   ├── 04-REQUISITION_LOG.md      # 需求讨论记录
│   ├── 05-CHANGELOG.md            # 变更记录
│   │
│   ├── architecture/               # 架构文档
│   │   ├── ARCHITECTURE.md
│   │   ├── BACKEND_MIGRATION.md
│   │   └── MOBILE_PWA_PLAN.md
│   │
│   └── reference/                  # 参考文档
│       ├── API_SPEC.md            # API 接口规范
│       ├── PROJECT_STRUCTURE.md
│       ├── DEVELOPMENT_WORKFLOW.md
│       ├── DECISIONS_LOG.md
│       └── TROUBLESHOOTING.md
│
├── blade-mobile/                   # 移动端项目
│   ├── CLAUDE.md
│   └── src/
│
└── blade-backend/                  # 后端项目
    ├── CLAUDE.md
    └── src/
```

---

## 工作流程

```
┌─────────────────────────────────────────────────────────┐
│  需求讨论区 (04-REQUISITION_LOG.md)                      │
│  - 用户提出需求                                          │
│  - AI 分析、拆解、给方案                                 │
│  - 用户确认                                              │
└─────────────────────────────────────────────────────────┘
                          ↓ 需求锁定
┌─────────────────────────────────────────────────────────┐
│  产品需求文档 (02-PRD.md)                                │
│  - 锁定后的功能需求                                      │
│  - AI 开发的唯一依据                                     │
└─────────────────────────────────────────────────────────┘
                          ↓ 拆解任务
┌─────────────────────────────────────────────────────────┐
│  开发任务清单 (03-TASKS.md)                              │
│  - 任务列表 + 状态 + 负责人                             │
│  - AI 自己领任务、自己做、自己更新状态                    │
└─────────────────────────────────────────────────────────┘
```

---

## AI 开发规则

### 规则 1：PRD 是唯一依据

开发前必须阅读 [02-PRD.md](./02-PRD.md)。所有功能开发必须严格按 PRD 执行，不能擅自添加/修改功能。

### 规则 2：任务驱动

从 [03-TASKS.md](./03-TASKS.md) 领取任务。完成后更新任务状态。

### 规则 3：变更必须记录

需求变更、架构变更、决策变更必须记录到 [05-CHANGELOG.md](./05-CHANGELOG.md)。

### 规则 4：遇到问题先查文档

1. [reference/TROUBLESHOOTING.md](./reference/TROUBLESHOOTING.md)
2. [reference/DECISIONS_LOG.md](./reference/DECISIONS_LOG.md)
3. 最后才向用户提问

### 规则 5：交接时必须同步文档

任务完成后必须同步更新：
- 任务状态（03-TASKS.md）
- 变更记录（05-CHANGELOG.md）
- 相关架构文档

---

## 相关项目路径

| 项目 | 路径 |
|------|------|
| BladeProject 主目录 | ../ |
| 文档中心 | ./ |
| 后端 | ../blade-backend/ |
| 移动端 PWA | ../blade-mobile/ |
| 原 Blade 项目（参考） | /Users/chenjiarun/Documents/Blade/ |

# BladeProject

> Monorepo 项目，包含后端、移动端、PC 管理端、共享类型和项目文档。

## 阅读顺序

1. [docs/SESSION_CONTEXT.md](./docs/SESSION_CONTEXT.md)
2. [docs/01-README.md](./docs/01-README.md)
3. [docs/02-PRD.md](./docs/02-PRD.md)
4. [docs/03-TASKS.md](./docs/03-TASKS.md)
5. [docs/reference/GIT_BRANCH_WORKFLOW.md](./docs/reference/GIT_BRANCH_WORKFLOW.md)（开发分支、测试集成、上线发布）

## 子项目入口

| 模块 | 路径 | 说明 |
|------|------|------|
| 文档中心 | `./docs/` | PRD、任务、变更、设计、排障 |
| 后端 | `./blade-backend/` | Spring Boot 3 API 与数据库迁移 |
| 移动端 | `./blade-mobile/` | Vue 3 PWA |
| PC 管理端 | `./blade-admin/` | Vue 3 + Element Plus 后台 |
| 共享类型 | `./packages/types/` | 前端共享 TypeScript 类型 |
| 原型 | `./stitch/` | 历史原型工程 |

## 说明

- 业务规则和技术栈以 [docs/02-PRD.md](./docs/02-PRD.md) 为准。
- 当前进度以 [docs/03-TASKS.md](./docs/03-TASKS.md) 为准。
- 最近变更以 [docs/05-CHANGELOG.md](./docs/05-CHANGELOG.md) 为准。
- Git 分支、GitHub 同步和 NAS 生产发布以 [docs/reference/GIT_BRANCH_WORKFLOW.md](./docs/reference/GIT_BRANCH_WORKFLOW.md) 为准。
- 更完整的导航见 [docs/01-README.md](./docs/01-README.md)。

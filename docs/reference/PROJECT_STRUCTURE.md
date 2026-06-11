# 项目目录结构

> 本文档只记录当前仓库的真实结构和各目录职责，不承载历史迁移设计。

---

## 一、仓库结构

```text
BladeProject/
├── AGENTS.md                  # 根级 AI 协作规范
├── README.md                  # 轻量导航页
├── docs/                      # 项目文档中心
├── blade-backend/             # Spring Boot 3 后端
├── blade-mobile/              # Vue 3 + Vite 移动端 PWA
├── blade-admin/               # Vue 3 + Element Plus PC 管理端
├── packages/
│   └── types/                 # 共享 TypeScript 类型包
└── stitch/                    # 历史原型/设计稿工程
```

---

## 二、目录职责

| 目录 | 职责 |
|------|------|
| `docs/` | PRD、任务、变更、设计、排障等文档 |
| `blade-backend/` | 后端 API、数据库迁移、权限与业务逻辑 |
| `blade-mobile/` | 员工使用的移动端 PWA |
| `blade-admin/` | 管理员使用的 PC 后台 |
| `packages/types/` | 前端共享类型定义，供移动端和其他前端复用 |
| `stitch/` | UI 原型和试验工程，不作为生产代码主入口 |

---

## 三、关键入口文件

### 文档入口

| 文件 | 说明 |
|------|------|
| `docs/01-README.md` | 文档主入口和阅读顺序 |
| `docs/02-PRD.md` | 业务规则与技术栈的单一事实来源 |
| `docs/03-TASKS.md` | 当前任务状态和领取依据 |
| `docs/05-CHANGELOG.md` | 变更记录 |
| `docs/SESSION_CONTEXT.md` | 快速接手摘要 |
| `docs/reference/GIT_BRANCH_WORKFLOW.md` | Git 分支、GitHub 同步、release 与 NAS 生产发布规范 |
| `docs/10-AGENT_INTEGRATION_DESIGN.md` | 外部 AI Agent 对接设计与第一版边界 |
| `docs/11-AGENT_ACCESS_GUIDE.md` | 外部 Agent 接入鉴权、接口调用和工具封装说明 |

### 后端入口

| 文件 | 说明 |
|------|------|
| `blade-backend/pom.xml` | Maven 依赖与 Java/Spring Boot 版本 |
| `blade-backend/src/main/resources/application.yml` | 后端运行配置 |
| `blade-backend/src/main/resources/db/migration/` | Flyway 迁移脚本 |
| `blade-backend/src/main/java/com/blade/config/` | 安全、MyBatis-Plus、Redisson 等配置 |

### PC 管理端入口

| 文件 | 说明 |
|------|------|
| `blade-admin/package.json` | 前端依赖与脚本 |
| `blade-admin/src/main.ts` | 应用入口 |
| `blade-admin/src/router/index.ts` | 路由与权限守卫 |
| `blade-admin/src/views/` | 订单、库存、商品、客户、系统管理页面 |

### 移动端入口

| 文件 | 说明 |
|------|------|
| `blade-mobile/package.json` | 移动端依赖与脚本 |
| `blade-mobile/src/main.ts` | 应用入口 |
| `blade-mobile/src/router/index.ts` | 路由配置 |
| `blade-mobile/src/plugins/vuetify.ts` | Vuetify 配置 |

### 共享类型入口

| 文件 | 说明 |
|------|------|
| `packages/types/package.json` | 共享类型包配置 |
| `packages/types/src/index.ts` | 类型导出入口 |
| `packages/types/src/*.ts` | auth/order/inventory/product 等领域类型 |

---

## 四、维护规则

- 文档中出现的路径必须是仓库内真实存在的路径。
- 新增一级模块时，优先更新本文档和 [01-README.md](../01-README.md)。
- 架构设计、历史迁移背景请写入 `docs/architecture/`，不要回灌到本文档。

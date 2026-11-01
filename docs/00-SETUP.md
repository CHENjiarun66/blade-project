# 如何启动和验证本地开发环境

> 文档状态：现行操作指南。核对基线：2026-11-02，代码 `132c9e6`。生产发布另见 [NAS 运维](./13-NAS_PRODUCTION_OPS.md)。

本页说明本地依赖、独立测试库、前后端启动和验证顺序。已有本地库的迁移版本不能由这份文档推断；不要把旧目录、默认连接串或历史密码当成当前环境事实。

## 选择正确的工作目录

日常集成基线在 `/Users/chenjiarun/Documents/BladeProject-worktrees/develop`。原 `/Users/chenjiarun/Documents/BladeProject` 为 V50 看板/规划工作区，保留未提交资料，不是最新业务基线。用 `git status --short --branch` 核对后，再从最新 develop 创建独立功能 worktree。

## 检查声明的依赖

| 组件 | 仓库声明 | 依据 |
|---|---|---|
| Java / Spring Boot | Java 17 / Spring Boot 3.2.4 | `blade-backend/pom.xml` |
| MySQL / Redis | MySQL 8 / Redis 7 | `deploy/nas/docker-compose.prod.yml` |
| Node / 包管理 | Node 22、npm，按 lockfile 安装 | 项目已有构建记录及各 package-lock.json |
| PC | Vue 3、Vite 8、Element Plus 2、Pinia 3 | `blade-admin/package.json` |
| 移动端 | Vue 3、Vite 8、Vuetify 4、Vue Router 4 | `blade-mobile/package.json` |

表中版本是项目声明，不是升级建议。实际解析版本以 lockfile/pom 为准，不能照旧文档重新选择框架。

## 使用独立的开发和测试数据库

后端配置读取 `BLADE_DB_URL`、`BLADE_DB_USERNAME`、`BLADE_DB_PASSWORD`。默认 URL 指向本机 `blade_project`，不代表该库可以重建。新验证环境应使用独立端口和独立库名，例如 3307 / `blade_project_test`，Redis 测试使用 database 1。

先在受控本地环境建立独立 MySQL 库和 Redis，再设置连接变量：

```bash
export BLADE_DB_URL='jdbc:mysql://127.0.0.1:3307/blade_project_test?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export BLADE_DB_USERNAME=root
export BLADE_DB_PASSWORD='your_local_test_password'
export SPRING_DATA_REDIS_DATABASE=1
```

这里的密码是占位符。不要对 `blade_project_prod`、NAS 数据库或来源不明的现有容器执行测试初始化、DROP、Flyway repair 或恢复导入。

当前源码最高迁移为 V69。空库需要完整 Flyway 初始化；登录类测试还需要 `scripts/test-db-seed.sql` 的 `test_tenant` 约定。可先在该独立库启动一次后端，确认迁移成功后停止，再对同一独立库执行种子 SQL。生产副本只用于指定的只读/隔离验证，通用测试夹具会与生产默认档口数据冲突。

## 启动与构建

在明确设置上述独立数据库变量后启动后端，默认端口 8080：

```bash
cd blade-backend
mvn spring-boot:run
```

PC 前端默认端口以 blade-admin/vite.config.ts 为准（当前 5777），`/api` 代理到 8080：

```bash
cd blade-admin
npm ci
npm run dev
```

移动端引用共享类型，先构建类型包，再启动移动应用：

```bash
cd packages/types
npm ci
npm run build
cd ../../blade-mobile
npm ci
npm run dev
```

移动端默认端口以 `blade-mobile/vite.config.ts` 为准。生产入口 `/catalog` 来自 PC 构建，不等于移动端已经发布。

## 验证改动

- 后端业务改动：对独立库执行 `mvn test`；涉及授权时使用实际登录/JWT 链路，不能只构造业务 `User` principal
- PC / 移动端改动：在对应目录执行 `npm run build`，再做相关真实交互验证
- 共享类型改动：先执行 `packages/types` 构建，再验证两个使用端
- 文档改动：执行 `node scripts/check-docs.mjs`、`node scripts/gen-status.mjs` 和 `git diff --check`

`mvn package -DskipTests` 只是打包，不是测试通过证据。最近一次生产发布的 880 项通过记录不替代新改动的验证。

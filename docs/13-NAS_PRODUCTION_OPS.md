# NAS 生产环境运维手册（Agent 专用）

> 本文档用于指导后续 Agent 将本地开发完成并验证通过的 BladeProject 发布到群晖 NAS 生产环境。  
> 本文档是运维流程文档，不替代 PRD。业务规则仍以 `docs/02-PRD.md` 为准。

---

## 1. 当前生产环境事实

> 2026-08-30 只读核对：NAS 生产数据库最新 Flyway 为 V42，四个生产容器均在运行；本地 Phase 2 分支已到 V50。V43-V47 包含 WhatsApp，V48-V50 包含纸单草稿和占位 SKU，均尚未部署生产。发布前仍必须重新只读确认，不得仅依赖本文快照。

### 1.0 待发布能力与前置验收

以下能力已经在本地开发，但尚未记录为 NAS 生产完成：

| 能力 | 迁移范围 | 发布前置条件 |
|------|------|------|
| WhatsApp 本地归档与客户工作区 | V43-V47 | Collector/Worker Key、Mac → NAS 网络、增量同步与回滚验收 |
| 纸单 Agent 批量草稿 | V48 | 最小 scope Agent Key、真实批次本地验收、30 单联调方案 |
| SPU 占位 SKU | V49-V50 | 占位拆分、拆分审计和履约保护完成 |

不得只发布数据库或只发布前端。每组能力需要同时部署匹配的后端、前端和迁移，再执行健康检查。

### 1.1 NAS 信息

| 项目 | 值 |
|------|----|
| NAS 地址 | `192.168.1.10` |
| WireGuard 备用地址 | `10.13.13.1` |
| SSH 用户 | `admin008` |
| 登录方式 | 已配置 SSH key，通常不需要密码 |
| 系统 | Synology / 群晖 |
| CPU 架构 | x86_64 / `linux/amd64` |
| Docker | 已安装 |
| docker-compose | `/usr/local/bin/docker-compose`，旧版 compose v1 |
| 生产端口 | `8899` |
| 访问入口 | `https://192.168.1.10:8899/catalog` |
| 外网业务入口 | `https://frp-pen.com:33294`（Agent 运行配置，可变，不写死） |

不要在文档或日志中打印 NAS 密码、`.env.prod`、JWT secret、数据库密码。

连接规则：

- 发布脚本默认先连接局域网地址 `192.168.1.10`。
- 如果本地环境无法访问 `192.168.1.10:22`，发布脚本会自动切换到 WireGuard 地址 `10.13.13.1` 继续连接 NAS。
- 如需强制使用某个地址，可传入 `NAS_HOST=<host> NAS_HOST_FIXED=1`。
- 生产入口文档仍以局域网地址 `https://192.168.1.10:8899/catalog` 记录；通过 WireGuard 验证时可访问 `https://10.13.13.1:8899/catalog`。
- 外部 Mac Agent 当前通过 `https://frp-pen.com:33294` 访问业务 API；Agent 端使用 `BLADE_AGENT_API_BASE_URL` 配置，实际接口拼接 `/api/agent/...`。该外网地址与 SSH 发布地址相互独立，变更外网入口不得影响 NAS 发布脚本的 `NAS_HOST`。
- 外网业务入口只允许转发 Nginx HTTPS，不得公开 MySQL、Redis、后端容器端口、SSH 或 DSM 管理入口。正式联调需从 Agent 所在 Mac 验证证书、限流、审计和断线幂等重试。

### 1.2 生产部署目录

生产环境部署在存储空间 2 的共享文件夹：

```text
/volume2/blade
```

目录结构：

```text
/volume2/blade
├── app/                         # 发布用应用文件 / 构建上下文
│   ├── blade-backend/
│   │   ├── Dockerfile
│   │   └── target/blade-backend-1.0.0.jar
│   ├── blade-admin/
│   │   ├── Dockerfile
│   │   └── dist/
│   └── deploy/nas/nginx/default.conf
├── mysql/                       # MySQL 数据目录，生产数据，不能随意删除
├── redis/                       # Redis 持久化目录
├── uploads/                     # 文件中心真实文件目录
├── logs/                        # 后端日志挂载目录
├── db-backups/                  # 数据库备份和迁移 SQL
├── docker-compose.prod.yml
├── .env.prod                    # 生产密钥文件，只存在 NAS，不提交
├── deploy_from_local.sh         # 发布脚本副本
└── blade-prod-images.tar        # 首次部署/离线兜底镜像包
```

历史目录 `/volume2/docker/blade` 只作为迁移前临时备份存在，不再作为生产运行目录。

### 1.3 当前容器

| 容器 | 作用 |
|------|------|
| `blade-mysql` | 生产 MySQL |
| `blade-redis` | Redis 登录态/缓存 |
| `blade-backend` | Spring Boot 后端 |
| `blade-web` | Nginx 前端与 API 反代 |

检查命令：

```bash
ssh admin008@192.168.1.10
cd /volume2/blade
/usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml ps
```

### 1.4 当前生产数据库

| 项目 | 值 |
|------|----|
| NAS 数据库名 | `blade_project_prod` |
| 来源 | 本机生产库 `blade_project_prod` 只读导出后导入 |
| NAS 主租户 code | `dwy_jiajiadress` |
| 本机生产库是否被改 | 否 |

最近一次迁移验证：

```text
product      164
product_sku  416
sale_order   81
file_storage 22
flyway       42（2026-08-30 只读复核）
```

注意：`file_storage` 记录已迁移，但真实文件是否完整存在于 `/volume2/blade/uploads` 需要单独核对。

---

## 2. 项目架构速览

### 2.1 本地项目结构

```text
BladeProject/
├── blade-backend/        # Spring Boot 3 + Spring Security + MyBatis-Plus
├── blade-admin/          # Vue 3 + Vite + TypeScript + Element Plus，PC 管理端与 /catalog
├── blade-mobile/         # Vue 3 + Vite + TypeScript + PWA + Vuetify
├── packages/types/       # 共享类型
├── deploy/nas/           # NAS 部署脚本与 compose
└── docs/                 # 项目文档
```

### 2.2 生产运行结构

```text
client/iPad/browser
        |
        v
blade-web nginx :8899
        |
        +-- static files: blade-admin/dist
        |
        +-- /api/* -> blade-backend:8080
                          |
                          +-- MySQL: blade-mysql:3306
                          +-- Redis: blade-redis:6379
                          +-- uploads: /data/uploads -> /volume2/blade/uploads
```

### 2.3 数据库边界

| 环境 | 数据库 | 用途 |
|------|--------|------|
| 本机开发 | `blade_project` | 默认开发库 |
| 本机生产/真实录入 | `blade_project_prod` | 本机保留的真实/演示数据源 |
| NAS 生产 | `blade_project_prod` | 对外生产环境 |

Agent 默认不能直接修改本机生产库。若需要迁移生产数据到 NAS，必须只读导出本机生产库，在导出的 SQL 或 NAS 目标库上做转换。

---

## 3. 运维原则

### 3.0 数据安全优先级

生产环境的最高优先级是数据安全。按风险排序：

```text
MySQL 数据 > uploads 文件 > .env.prod 密钥 > 应用镜像/前端静态资源
```

日常发布允许替换应用镜像和前端静态资源，但默认不得改动：

```text
/volume2/blade/mysql
/volume2/blade/uploads
/volume2/blade/.env.prod
```

任何涉及数据库导入、数据库回滚、数据目录迁移、uploads 覆盖的操作，都必须先说明风险并获得用户确认。

### 3.1 首次部署和日常发布分离

首次部署才需要处理：

- MySQL/Redis 容器
- 基础镜像
- `.env.prod`
- 数据目录
- 首次数据库初始化

日常发布只应该更新：

- `blade-backend:prod`
- `blade-web:prod`
- `/volume2/blade/app/blade-backend/target/blade-backend-1.0.0.jar`
- `/volume2/blade/app/blade-admin/dist`
- `/volume2/blade/app/deploy/nas/nginx/default.conf`
- `/volume2/blade/docker-compose.prod.yml`（仅限应用配置变更）

日常发布不要重建、删除或覆盖：

```text
/volume2/blade/mysql
/volume2/blade/redis
/volume2/blade/uploads
/volume2/blade/.env.prod
```

日常发布必须使用：

```bash
deploy/nas/deploy_app_from_local.sh --execute
```

禁止日常发布使用 `deploy_from_local.sh`。该脚本只用于首次部署或基础设施重建，且必须显式传入：

```bash
FIRST_DEPLOY_CONFIRM=YES deploy/nas/deploy_from_local.sh
```

### 3.2 生产发布必须可追踪

推荐发布已提交到 Git 的版本，不推荐发布未提交的临时工作区。

发布前 Agent 应记录：

- 当前分支
- 当前 commit
- 是否存在未提交变更
- 本次构建和验证命令

### 3.3 NAS 尽量少依赖外网

已知事实：

- NAS 访问 Docker Hub 可能超时。
- NAS 是 x86_64，Apple Silicon 本机默认 ARM 镜像不能直接给 NAS 使用。
- Synology SSH 可能不支持新版 `scp` 的 SFTP 子系统，需要 `scp -O`。

因此：

- 首次部署或基础镜像变更：优先本机构建 `linux/amd64` 离线镜像包，再上传 NAS。
- 日常发布：只构建并上传应用镜像，避免反复传 MySQL/Redis/基础镜像。
- NAS 从 GitHub 拉代码可作为日常流程，但不要让 NAS 每次拉 Docker Hub 基础镜像。
- 每次发布必须运行或等价执行镜像架构检查，确认 `blade-backend:prod` 和 `blade-web:prod` 均为 `linux/amd64`。

### 3.4 发布门禁

发布前必须满足：

1. 本地构建通过。
2. 与本次变更相关的测试通过。
3. Docker 镜像架构为 `linux/amd64`。
4. NAS 当前数据库已备份，备份文件非空。
5. 发布命令只更新 `backend` 和 `web`，不更新 MySQL/Redis。
6. 明确当前是否有未提交代码；有未提交代码时必须向用户说明风险。
7. 若本次版本包含 Flyway migration，必须确认 migration 文件已提交到 Git，已在本地或测试库验证通过，并在发布说明中列出数据库影响范围。

发布后必须验证：

1. `blade-mysql`、`blade-redis`、`blade-backend`、`blade-web` 均为 Up。
2. `curl -k -I https://127.0.0.1:8899/catalog` 返回 200/3xx。
3. 登录后关键 API 返回 200。
4. 涉及文件中心时，至少验证图片/视频上传、预览、列表。
5. 若验证失败，优先做应用镜像回滚，不做数据库回滚。

---

## 4. 代码来源策略

### 4.1 主推荐：GitHub 主仓库

如果 NAS 能稳定访问 GitHub，可在 NAS 的发布源码目录执行：

```bash
git fetch --all
git checkout <branch-or-tag>
git pull --ff-only
```

适用前提：

- NAS 可以稳定访问 GitHub。
- NAS 上已配置 Git 凭据或 SSH key。
- 只用于拉代码，不用于每次拉基础镜像。

### 4.2 国内备用：Gitee 镜像仓库

如果 GitHub 不稳定，可以维护 Gitee 镜像仓库。NAS 从 Gitee 拉代码：

```bash
git remote add gitee <gitee-repo-url>
git fetch gitee
git checkout <branch-or-tag>
```

注意：

- GitHub 仍建议作为主仓库。
- Gitee 作为国内镜像，需要保持同步。
- 私有仓库权限要单独配置。

### 4.3 最稳兜底：本机 Git 打包上传

不依赖 NAS 访问 GitHub/Gitee：

```bash
git archive --format=tar HEAD | gzip > /tmp/blade-source.tar.gz
scp -O /tmp/blade-source.tar.gz admin008@192.168.1.10:/volume2/blade/releases/
```

适合网络异常或 NAS Git 凭据不可用时使用。

---

## 5. 首次部署流程

首次部署或基础设施重建时使用，日常发版不要反复执行全量流程。

### 5.0 NAS 平台确认

首次部署、基础镜像升级、群晖系统升级后，必须先确认 NAS 平台：

```bash
cd /Users/chenjiarun/Documents/BladeProject
deploy/nas/check_platform.sh
```

重点确认：

- `uname -m` 为 `x86_64` 或等价 amd64。
- Docker server 输出 `linux/amd64`。
- `/usr/local/bin/docker-compose` 可用，且为群晖当前旧版 compose。
- `/volume2/blade/mysql` 和 `/volume2/blade/uploads` 存在且不会被发布脚本删除。
- 生产容器名仍为 `blade-mysql`、`blade-redis`、`blade-backend`、`blade-web`。

### 5.1 本地构建

```bash
cd /Users/chenjiarun/Documents/BladeProject

cd blade-backend
mvn clean package -DskipTests

cd ../blade-admin
PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build
```

### 5.2 构建 linux/amd64 镜像

```bash
cd /Users/chenjiarun/Documents/BladeProject

docker pull --platform linux/amd64 mysql:8.0
docker pull --platform linux/amd64 redis:7-alpine
docker pull --platform linux/amd64 nginx:alpine
docker pull --platform linux/amd64 maven:3.9.9-eclipse-temurin-17

docker build --platform linux/amd64 -t blade-backend:prod blade-backend
docker build --platform linux/amd64 -t blade-web:prod -f blade-admin/Dockerfile .

docker save mysql:8.0 redis:7-alpine blade-backend:prod blade-web:prod \
  -o /private/tmp/blade-prod-images-amd64.tar
```

### 5.3 上传到 NAS

```bash
scp -O /private/tmp/blade-prod-images-amd64.tar \
  admin008@192.168.1.10:/volume2/blade/blade-prod-images.tar
```

同步发布文件：

```bash
scp -O deploy/nas/docker-compose.prod.yml admin008@192.168.1.10:/volume2/blade/docker-compose.prod.yml
scp -O deploy/nas/README.md admin008@192.168.1.10:/volume2/blade/README.md
scp -O deploy/nas/deploy_from_local.sh admin008@192.168.1.10:/volume2/blade/deploy_from_local.sh
```

### 5.4 启动

```bash
ssh admin008@192.168.1.10
cd /volume2/blade
/usr/local/bin/docker load -i blade-prod-images.tar
/usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-build
```

首次启动 MySQL 和 Flyway 迁移可能需要 8-15 分钟。后端日志出现以下内容才算启动完成：

```text
Started BladeApplication
```

---

## 6. 日常发布流程（推荐）

日常发版只更新 `backend` 和 `web`，不动 MySQL/Redis。

推荐直接使用安全发布脚本：

```bash
cd /Users/chenjiarun/Documents/BladeProject
deploy/nas/deploy_app_from_local.sh
```

默认是 dry run，只展示流程，不会上传或修改 NAS。确认执行：

```bash
deploy/nas/deploy_app_from_local.sh --execute
```

该脚本会自动完成：

- 记录 Git 分支、commit 和未提交变更。
- 本地构建后端 jar 和前端 dist。
- 只构建 `blade-backend:prod`、`blade-web:prod` 应用镜像。
- 校验镜像架构必须为 `linux/amd64`。
- 在 NAS 上创建发布前数据库备份并校验非空。
- 上传应用文件和应用镜像。
- 执行 `docker-compose up -d --no-deps backend web`，只重启应用容器。
- 验证容器状态和 `/catalog`。

以下小节是该脚本的手工等价流程，用于排查或特殊场景。

### 6.1 发布前检查

```bash
cd /Users/chenjiarun/Documents/BladeProject
git status --short
git rev-parse --abbrev-ref HEAD
git rev-parse --short HEAD
```

若存在未提交变更，Agent 必须向用户说明风险。生产发布推荐使用已提交 commit。

### 6.2 本地构建和测试

```bash
cd blade-backend
mvn clean package -DskipTests

cd ../blade-admin
PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build
```

按变更范围补充测试：

- 后端共享逻辑：跑相关 JUnit。
- Catalog/UI：跑 Playwright 或浏览器验证。
- 文件上传/预览：至少验证上传、预览、列表。

### 6.3 构建应用镜像

```bash
cd /Users/chenjiarun/Documents/BladeProject

docker build --platform linux/amd64 -t blade-backend:prod blade-backend
docker build --platform linux/amd64 -t blade-web:prod -f blade-admin/Dockerfile .

docker image inspect blade-backend:prod blade-web:prod \
  --format '{{.RepoTags}} {{.Architecture}}/{{.Os}}'
```

必须看到：

```text
amd64/linux
```

### 6.4 只导出应用镜像

```bash
docker save blade-backend:prod blade-web:prod \
  -o /private/tmp/blade-app-images-amd64.tar
```

### 6.5 上传并重启应用容器

```bash
scp -O /private/tmp/blade-app-images-amd64.tar \
  admin008@192.168.1.10:/volume2/blade/blade-app-images.tar

ssh admin008@192.168.1.10 '
cd /volume2/blade
/usr/local/bin/docker load -i blade-app-images.tar
/usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps backend web
'
```

说明：

- `--no-deps backend web` 只更新应用容器。
- 不会重启 MySQL/Redis。
- 不会改动 `/volume2/blade/mysql`、`redis`、`uploads`。

### 6.6 发布后验证

```bash
ssh admin008@192.168.1.10
cd /volume2/blade
/usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml ps
curl -k -I https://127.0.0.1:8899/catalog
```

登录验证：

- 租户：`dwy_jiajiadress`
- 管理员账号：`admin`
- 密码以用户当前确认为准，不在文档中扩散。

不要在日志中打印 access token 或 refresh token。

---

## 7. 数据库迁移流程

### 7.1 迁移原则

数据库迁移是高风险操作，必须遵守：

- 迁移前备份 NAS 当前库。
- 日常版本中的结构变更必须通过 Flyway migration 随后端代码发布，路径为 `blade-backend/src/main/resources/db/migration/`。
- 已发布或已执行过的 Flyway migration 禁止修改；后续变更只能新增更高版本号 migration。
- 迁移 SQL 应尽量向前兼容，避免应用回滚后因数据库结构不兼容导致旧版本无法启动。
- 不直接修改本机生产库。
- 如需租户 code 转换，在导出的 SQL 或 NAS 目标库中处理。
- 导入期间停止 `backend` 和 `web`，避免应用写入。
- 导入后验证关键表数量、租户 code、登录/API。

### 7.1.1 日常发布中的 Flyway 自动迁移

当新版本包含新增字段、表、索引或初始化权限数据时，推荐流程是：

```text
push master
  ↓
GitHub Actions 或本地构建 backend/web 镜像
  ↓
NAS 发布脚本先备份生产库
  ↓
NAS 更新 backend/web 镜像并只重启应用容器
  ↓
backend 启动时 Flyway 自动执行尚未执行过的 migration
  ↓
验证页面、登录、关键 API 和数据库版本
```

注意：

- 应用镜像可以快速回滚，但已经执行的数据库 migration 默认不自动回滚。
- 因此 migration 必须按“向前兼容”设计；发布失败时优先回滚应用镜像，不默认做数据库回滚。
- 数据库回滚只在明确需要恢复数据时执行，且必须获得用户确认。

### 7.2 备份 NAS 当前库

推荐使用脚本：

```bash
cd /Users/chenjiarun/Documents/BladeProject
deploy/nas/backup_db.sh
```

脚本只执行 `mysqldump` 只读导出，并校验备份文件非空。

手工等价命令：

```bash
ssh admin008@192.168.1.10 '
mkdir -p /volume2/blade/db-backups
cd /volume2/blade
/usr/local/bin/docker exec blade-mysql sh -c '"'"'
mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --single-transaction --default-character-set=utf8mb4 \
  --routines --triggers "$MYSQL_DATABASE"
'"'"' > /volume2/blade/db-backups/nas_blade_project_prod_before_import_$(date +%Y%m%d_%H%M%S).sql
'
```

备份完成后必须确认：

- 备份文件位于 `/volume2/blade/db-backups/`。
- 文件大小非 0。
- 备份文件名包含时间戳或发布 ID。
- 不在终端输出数据库密码或 `.env.prod` 内容。

### 7.3 只读导出本机生产库

```bash
cd /Users/chenjiarun/Documents/BladeProject
mkdir -p tmp/nas-migration

docker exec blade-mysql sh -c '
mysqldump -uroot -proot123 \
  --single-transaction --default-character-set=utf8mb4 \
  --routines --triggers blade_project_prod
' > tmp/nas-migration/blade_project_prod_for_nas.sql
```

### 7.4 租户 code 转换

如果用户要求 NAS 生产登录租户为 `dwy_jiajiadress`，不要改本机生产库。只在导入 SQL 末尾追加：

```sql
UPDATE `sys_tenant`
SET `tenant_code` = 'dwy_jiajiadress'
WHERE `id` = 1;
```

当前已知：本机生产库主租户是 `tenant_id=1`，业务数据集中在 `tenant_id=1`。

### 7.5 导入 NAS

```bash
scp -O tmp/nas-migration/blade_project_prod_for_nas.sql \
  admin008@192.168.1.10:/volume2/blade/db-backups/

ssh admin008@192.168.1.10 '
set -e
cd /volume2/blade
/usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml stop backend web
/usr/local/bin/docker exec blade-mysql sh -c '"'"'
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`;
CREATE DATABASE \`$MYSQL_DATABASE\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
"
'"'"'
/usr/local/bin/docker exec -i blade-mysql sh -c '"'"'
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"
'"'"' < /volume2/blade/db-backups/blade_project_prod_for_nas.sql
/usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-build
'
```

### 7.6 导入后验证

```bash
ssh admin008@192.168.1.10 '
/usr/local/bin/docker exec blade-mysql sh -c '"'"'
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N "$MYSQL_DATABASE" -e "
SELECT id, tenant_code, status FROM sys_tenant ORDER BY id;
SELECT \"product\", COUNT(*) FROM product;
SELECT \"product_sku\", COUNT(*) FROM product_sku;
SELECT \"sale_order\", COUNT(*) FROM sale_order;
SELECT \"file_storage\", COUNT(*) FROM file_storage;
SELECT \"flyway\", COUNT(*) FROM flyway_schema_history;
"
'"'"'
'
```

### 7.7 订单大重构的备份集

普通日常发布只要求生成非空 `mysqldump`；订单生命周期和财务重构必须使用 release ID 建立完整备份集，不能只留下 NAS 同一磁盘上的一份 SQL。

每个备份集至少包含：

| 内容 | 要求 |
|------|------|
| 数据库逻辑备份 | `--single-transaction --routines --triggers`；压缩后生成 SHA-256 |
| 数据库结构备份 | 单独导出 schema，便于核对迁移前结构 |
| Flyway 历史 | 导出完整 `flyway_schema_history` |
| 业务基线报告 | 订单数、明细数、实收、退款、核销、尾款、配货计划、出库和库存汇总 |
| uploads | 群晖快照或只读文件备份，并生成文件路径、大小和数量清单 |
| 发布制品 | Git commit、migration 列表、compose/nginx、前后端不可变镜像 ID 和镜像 tar |
| 私密配置 | `.env.prod` 只做受控加密备份，不写入 Git、日志或普通 release 目录 |
| 恢复说明 | 恢复顺序、旧镜像 ID、验证命令、负责人和停止条件 |

数据库备份至少保留两份：NAS `db-backups` 一份，NAS 之外的受控介质一份。推荐遵守 3-2-1 原则，但不得为了复制备份而在日志中暴露密钥或真实业务数据。

备份完成不代表可恢复。正式发布前必须在隔离 MySQL 中实际恢复一次，确认：SQL 可导入、Flyway 版本正确、关键表数量和金额汇总一致、应用旧版本可以读取。只做 `test -s` 不足以作为大重构发布门禁。

### 7.8 分阶段无损切换

“无损替换”指业务事实、订单金额、财务流水、库存和附件不丢失，不承诺完全零停机。当前单实例架构建议使用短暂停写窗口，而不是在生产库上直接尝试一键替换。

#### 阶段 A：兼容底座

1. migration 只新增表、字段和索引，不删除、不改写旧字段含义。
2. 新服务同时返回新旧字段，旧前端仍能读取。
3. 状态、财务和履约写入集中到统一服务；过渡期按设计维护必要兼容快照。
4. 仪表盘、客户、Agent 等消费者先进行影子计算和差异报告，不立即切换展示口径。

#### 阶段 B：生产副本预演

1. 只读导出 V42 生产库，恢复到隔离测试库。
2. 连续执行 V43-V50 和订单重构的新 migration。
3. 运行历史订单迁移，输出逐单旧值、新值、判定证据和异常原因。
4. 核对订单数、金额、收款、退款、核销、尾款、配货、出库和库存不变量。
5. 使用同一份脚本至少重复预演一次，结果必须确定且幂等。

#### 阶段 C：最终切换

1. 发布维护公告并进入短暂停写；所有 PC、移动端、Agent、Collector 和后台任务停止产生订单相关写入。
2. 停止 `backend`，保留维护页；确认数据库连接中没有业务写事务。
3. 创建最终数据库与 uploads 一致性备份集，计算哈希并复制一份到 NAS 外。
4. 记录最终业务基线和 Flyway V42 状态。
5. 加载按 release ID 标记的新镜像，执行 migration 和历史数据迁移。
6. 运行自动不变量校验、登录和订单关键路径冒烟。
7. 校验全部通过后切换 web/backend 并解除停写；失败则在仍未开放写入时回滚。

#### 阶段 D：观察与清理

1. 首个观察期保留旧字段、旧镜像、备份和兼容读取。
2. 持续比对旧口径与新事实层，监控金额差异、失败动作、库存异常和缓存一致性。
3. 至少稳定一个发布周期后，才评估删除旧接口或字段；物理删除必须是单独 release。

### 7.9 回滚边界

| 时点 | 回滚方式 | 数据损失风险 |
|------|------|------|
| 尚未解除停写 | 切回旧镜像；必要时恢复最终数据库/uploads 备份 | 可做到不丢业务写入，因为窗口内没有新写入 |
| 已解除停写，但 migration 为加法且旧应用兼容 | 关闭新功能开关或切回旧读取/旧镜像，不恢复数据库 | 新写入保留，推荐方式 |
| 已解除停写且已有新模型写入 | 优先前向修复、冲销流水或兼容转换 | 直接恢复旧 SQL 会丢失切换后的新订单，禁止盲目覆盖 |
| 发生不可逆数据破坏 | 再次停写，保存事故现场和增量数据，评审后恢复备份并人工合并增量 | 必须单独获得用户确认 |

只要重新开放录单，就不能再把发布前 SQL 整库覆盖回来并声称“无损”。因此首发必须坚持加法 migration、兼容读取、不可变流水和延迟删除，使应用回滚不依赖数据库回滚。

### 7.10 发布脚本升级要求

订单大重构正式 release 前，`backup_db.sh` 和 `deploy_app_from_local.sh` 至少补齐：

- 数据库备份压缩、SHA-256 和 NAS 外副本校验。
- 隔离库恢复演练结果和业务基线报告门禁。
- `blade-backend:<release_id>`、`blade-web:<release_id>` 不可变标签，保留上一个稳定镜像。
- 明确的 maintenance/停写步骤，以及 migration 前后检查点。
- Flyway 和历史迁移失败时保持停写，不自动继续开放流量。
- 发布清单记录 Git commit、镜像 digest、migration 范围、备份文件和回滚命令。

当前脚本没有完整实现这些能力，因此在 `BE-1052` 完成前不得用于订单大重构正式发布。

---

## 8. uploads 文件迁移流程

数据库 `file_storage` 只保存元数据。真实文件在本地或 NAS 的文件目录中。

当前 NAS 文件目录：

```text
/volume2/blade/uploads
```

若 `file_storage` 有记录但页面图片无法显示，应检查：

1. `file_storage.storage_path` 或相关路径字段。
2. NAS `/volume2/blade/uploads` 是否存在对应文件。
3. 后端环境变量 `BLADE_FILE_LOCAL_BASE_PATH=/data/uploads`。
4. 容器挂载是否为 `/volume2/blade/uploads -> /data/uploads`。

迁移本机 uploads 到 NAS 时，必须先确认本机文件存储目录。不要猜路径。找到后使用：

```bash
rsync -av --progress <local-uploads-dir>/ \
  admin008@192.168.1.10:/volume2/blade/uploads/
```

如果本机不支持 rsync 或 NAS 权限限制，可用 `scp -O -r`。

### 8.1 图片派生图生产补生成

生产环境已有图片可以生成 `thumb` 和 `card`，但必须同时满足：

1. `file_storage` 中存在 `file_type='IMAGE' AND status=1` 的有效记录。
2. `storage_path` 位于容器目录 `/data/uploads` 下，且 `blade-backend` 容器内可读。
3. 后端版本包含 V38 `file_derivative` 和历史补生成接口。
4. 当前租户管理员具有 `btn:file:viewAll` 或 `btn:file:cleanup` 权限。

派生生成只读取原图，并在 `/data/uploads/derivatives/` 新增 JPEG 文件；不会覆盖或删除原图。发布或补生成前仍必须完成数据库备份，并确认 uploads 已有可恢复备份或群晖快照。

#### 发布前只读预检

在 NAS 上先检查数据库记录、路径口径、容器挂载和磁盘空间：

```bash
ssh admin008@192.168.1.10 '
cd /volume2/blade
/usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml ps
docker exec blade-backend sh -lc "test -d /data/uploads && test -w /data/uploads && df -h /data/uploads"
docker exec blade-mysql sh -lc '\''mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -N -e "
SELECT CONCAT(\"active_images=\", COUNT(*))
FROM file_storage WHERE file_type=\"IMAGE\" AND status=1;
SELECT CONCAT(\"invalid_storage_paths=\", COUNT(*))
FROM file_storage
WHERE file_type=\"IMAGE\" AND status=1
  AND storage_path NOT LIKE \"/data/uploads/%\";
"'\''
'
```

如果 `invalid_storage_paths` 不为 0，禁止执行补生成。先核对这些记录是否为历史迁移遗留的本机绝对路径；路径修复属于生产数据库写操作，必须另行备份、评审和获得用户确认。

检查每个生产原图在后端容器内是否真实可读：

```bash
ssh admin008@192.168.1.10 '
docker exec blade-mysql sh -lc '\''mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -N -e "
SELECT storage_path FROM file_storage
WHERE file_type=\"IMAGE\" AND status=1
ORDER BY tenant_id,id;
"'\'' > /tmp/blade-image-paths.txt
total=0; missing=0
while IFS= read -r path; do
  total=$((total+1))
  docker exec blade-backend test -r "$path" || { echo "MISSING $path"; missing=$((missing+1)); }
done < /tmp/blade-image-paths.txt
echo "total=$total missing=$missing"
test "$missing" -eq 0
'
```

#### 发布和补生成门禁

1. 只从已验收 release 合入 `master` 后发布，NAS 不部署 feature/develop。
2. 发布前运行 `deploy/nas/backup_db.sh`，并确认备份文件非空。
3. 确认 `/volume2/blade/uploads` 已有群晖快照或独立备份，记录文件数和 `du -sh` 结果。
4. 只更新 `backend` 和 `web`；禁止重建 MySQL/Redis，禁止覆盖 uploads。
5. 后端启动后确认 Flyway 到 V38，再验证登录、原图 `/preview` 和派生图 `/variant`。
6. 首批只处理 5 张；`failed=0` 且数据库/物理文件正常后，按 20 或 50 张逐批扩大。
7. 任一批出现 FAILED、磁盘空间异常、原图 403/404 或页面破图，立即停止后续批次并保留现场。

补生成接口：

```text
POST /api/files/derivatives/backfill?limit=5
POST /api/files/derivatives/backfill?limit=20
```

接口只处理当前登录租户，不会自动跨租户。每个租户都必须单独登录、单独执行和验收。最终必须确认：有效图片每张各有一个 `READY thumb` 和一个 `READY card`，`FAILED=0`，派生文件存在且非空；再次调用接口应返回 `processed=0`。

#### 回滚边界

- 应用异常：优先回滚 `backend/web` 镜像，V38 为新增表，不要求立即回滚数据库。
- 派生生成异常：停止批次；原图不受影响，页面会回退原图。
- 不得为了回滚派生图而删除 `/volume2/blade/uploads` 或恢复整个 uploads 目录。
- 删除派生记录或派生物理文件属于单独清理操作，第一版不自动执行，必须另行评审。

---

## 9. 回滚流程

### 9.1 应用回滚

最安全的日常回滚是回滚 `backend/web` 应用镜像，不回滚数据库：

```bash
ssh admin008@192.168.1.10 '
cd /volume2/blade
/usr/local/bin/docker load -i <previous-app-images.tar>
/usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps backend web
'
```

前提：保留上一个版本的应用镜像包。

### 9.2 数据库回滚

数据库回滚风险高。只有在明确需要恢复数据时执行：

1. 停止 `backend` 和 `web`。
2. 备份当前坏库。
3. 重建 `blade_project_prod`。
4. 导入指定备份 SQL。
5. 启动并验证。

不要在没有用户确认时执行数据库回滚。

---

## 10. 常见问题

### 10.1 `exec format error`

原因：Apple Silicon 本机导出了 ARM 镜像，NAS 是 x86_64。

解决：所有生产镜像必须按 `linux/amd64` 构建：

```bash
docker build --platform linux/amd64 ...
```

### 10.2 NAS 拉 Docker Hub 超时

原因：NAS 网络访问 Docker Hub 不稳定。

解决：

- 首次部署使用本机离线镜像包。
- 日常发布只上传应用镜像。
- 不让 NAS 每次拉 MySQL/Redis/基础镜像。

### 10.3 `scp: subsystem request failed`

原因：Synology SSH 可能没有启用新版 scp 默认使用的 SFTP 子系统。

解决：

```bash
scp -O <file> admin008@192.168.1.10:<target>
```

### 10.4 首次启动 API 502

常见原因：

- MySQL 首次初始化未完成。
- Flyway 正在跑迁移。
- 后端还没出现 `Started BladeApplication`。

检查：

```bash
/usr/local/bin/docker logs --tail=200 blade-backend
/usr/local/bin/docker logs --tail=200 blade-mysql
```

### 10.5 登录后图片不显示

检查：

- `file_storage` 记录是否存在。
- `/volume2/blade/uploads` 是否有真实文件。
- 前端是否使用 `filePreviewUrl(fileId)`。
- 新窗口或 `<img>` 是否带 `previewToken`。
- 当前用户是否有对应业务查看权限。

### 10.6 视频上传失败或 413

常见原因：

- 前端 Nginx `client_max_body_size` 小于视频大小。
- 后端 Spring multipart 上限小于视频大小。
- 业务层 `BLADE_FILE_MAX_SIZE_MB` 小于视频大小。

当前第一版默认约定：

```text
Nginx client_max_body_size = 220m
BLADE_MULTIPART_MAX_FILE_SIZE = 200MB
BLADE_MULTIPART_MAX_REQUEST_SIZE = 220MB
BLADE_FILE_MAX_SIZE_MB = 200
```

修改后必须重新构建并重启 `blade-web` 和 `blade-backend`，否则 NAS 仍会使用旧限制。

---

## 11. Agent 操作红线

禁止：

- 日常发布使用 `deploy_from_local.sh` 或任何会重建 MySQL/Redis 的全量流程。
- 未备份就 `DROP DATABASE`。
- 删除 `/volume2/blade/mysql`。
- 删除 `/volume2/blade/uploads`。
- 用本机 uploads 目录直接覆盖 NAS `/volume2/blade/uploads`，除非已有备份且用户明确确认。
- 打印 `.env.prod` 内容。
- 打印 access token、refresh token、JWT secret、数据库密码。
- 未经用户确认直接修改本机生产库 `blade_project_prod`。
- 用 ARM 镜像覆盖 NAS 生产镜像。
- 日常发版时重建 MySQL/Redis。
- 日常发版时执行 `docker-compose down -v`、`docker system prune --volumes` 或任何会删除 volume/数据目录的命令。
- 把临时目录 `/volume2/docker/blade` 当成当前生产目录。

必须：

- 发布前运行或等价执行 `deploy/nas/check_platform.sh`。
- 发布前创建 NAS 数据库备份，或使用 `deploy/nas/deploy_app_from_local.sh --execute` 自动备份。
- 发布前说明当前代码版本和是否有未提交变更。
- 发布前构建验证。
- 生产数据库操作前先备份。
- 发布后检查容器状态、前端 200、登录后 API 200。
- 更新 `docs/03-TASKS.md` 和 `docs/05-CHANGELOG.md`。

---

## 12. 推荐后续优化

- 新增 `deploy/nas/deploy_app_from_local.sh`：只构建并发布 `backend/web` 应用镜像。
- 新增 `deploy/nas/backup_db.sh`：标准化 NAS 数据库备份。
- 新增 `deploy/nas/healthcheck.sh`：自动检查容器、前端、登录、关键 API。
- 如果 NAS 访问 GitHub 不稳定，配置 Gitee 镜像仓库作为备用代码源。
- 如果后续正式公网访问，补充 HTTPS、域名、反代、安全策略和外部备份。

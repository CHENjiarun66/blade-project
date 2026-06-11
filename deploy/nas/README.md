# BladeProject 群晖 NAS 生产部署

目标 NAS：群晖，Docker 旧版 compose 兼容。

推荐部署目录：

```bash
/volume2/blade
```

外部访问端口：

```bash
http://NAS-IP:8899/catalog
```

## 目录结构

```text
/volume2/blade
├── app/                  # 上传本项目打包后的应用文件
├── mysql/                # MySQL 数据
├── redis/                # Redis 持久化
├── uploads/              # 文件中心图片/视频
├── logs/                 # 后端日志挂载预留
├── docker-compose.prod.yml
└── .env.prod
```

## 本地打包

```bash
cd /Users/chenjiarun/Documents/BladeProject/blade-backend
mvn clean package -DskipTests

cd /Users/chenjiarun/Documents/BladeProject/blade-admin
PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build
```

## 日常发布（推荐）

日常发布只允许更新应用容器 `backend` 和 `web`，不得重建或重启 MySQL/Redis。

```bash
deploy/nas/deploy_app_from_local.sh
```

默认是 dry run，只展示流程，不会上传或修改 NAS。确认发布时执行：

```bash
deploy/nas/deploy_app_from_local.sh --execute
```

脚本会：

- 本地构建后端 jar 和前端 dist
- 本机按 `linux/amd64` 构建 `blade-backend:prod` 和 `blade-web:prod`
- 校验镜像架构必须是 `linux/amd64`
- 发布前在 NAS 创建数据库备份
- 上传应用文件和应用镜像
- 只执行 `docker-compose up -d --no-deps backend web`

## 首次部署 / 基础设施重建

本机已配置 SSH key 时，可从项目根目录执行：

```bash
FIRST_DEPLOY_CONFIRM=YES deploy/nas/deploy_from_local.sh
```

脚本会：

- 本地构建后端 jar 和前端 dist
- 上传发布文件到 `/volume2/blade`
- 在本机按 `linux/amd64` 构建/导出 Docker 离线镜像包
- 上传镜像包并在 NAS 上执行 `docker load`
- 如果 NAS 上不存在 `.env.prod`，在 NAS 上生成生产密钥文件
- 使用 `/usr/local/bin/docker-compose up -d --no-build` 启动容器

不要把 `deploy_from_local.sh` 用作日常发布脚本。

说明：

- 群晖 Docker Hub 访问可能超时，默认走本机离线镜像包部署。
- 群晖 918+ 为 x86_64，必须使用 `linux/amd64` 镜像；Apple Silicon 本机不能直接导出默认 ARM 镜像给 NAS。
- Synology SSH 环境可能不支持新版 `scp` 的 SFTP 子系统，脚本固定使用 `scp -O`。

## 上传到 NAS

将以下内容上传到 `/volume2/blade/app`：

- `blade-backend/Dockerfile`
- `blade-backend/target/blade-backend-1.0.0.jar`
- `blade-admin/Dockerfile`
- `blade-admin/dist`
- `deploy/nas/nginx/default.conf`

将以下内容上传到 `/volume2/blade`：

- `deploy/nas/docker-compose.prod.yml`
- 按 `deploy/nas/.env.prod.example` 创建 `.env.prod`

## 启动

```bash
cd /volume2/blade
/usr/local/bin/docker load -i blade-prod-images.tar
/usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-build
```

## 检查

```bash
/usr/local/bin/docker ps
curl http://127.0.0.1:8899/catalog
```

部署前平台检查：

```bash
deploy/nas/check_platform.sh
```

手动备份数据库：

```bash
deploy/nas/backup_db.sh
```

首次启动注意：

- MySQL 首次初始化和 Flyway 迁移可能需要 8-15 分钟。
- 初始化期间 `/api/*` 可能短暂返回 502，等后端日志出现 `Started BladeApplication` 后再验证。

## 备份重点

必须定期备份：

- `/volume2/blade/mysql`
- `/volume2/blade/uploads`

Redis 只用于登录态和缓存，建议备份但优先级低于 MySQL 和 uploads。

生产发布前必须至少完成数据库备份：

```bash
deploy/nas/backup_db.sh
```

涉及 uploads 迁移或覆盖前，必须先单独备份 `/volume2/blade/uploads`，并确认 `file_storage` 元数据与真实文件路径一致。

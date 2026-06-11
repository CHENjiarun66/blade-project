请按 BladeProject 的 NAS 生产发布安全规范执行一次“日常应用发布”，不要执行首次部署或全量部署。

必须先阅读：
1. /Users/chenjiarun/Documents/BladeProject/AGENTS.md
2. /Users/chenjiarun/Documents/BladeProject/docs/13-NAS_PRODUCTION_OPS.md
3. /Users/chenjiarun/Documents/BladeProject/deploy/nas/README.md

发布目标：
- NAS：192.168.1.10
- 生产目录：/volume2/blade
- 入口：http://192.168.1.10:8899/catalog

严格限制：
- 只能发布 backend/web 应用容器。
- 禁止重建、重启、删除 MySQL/Redis。
- 禁止删除或覆盖 /volume2/blade/mysql。
- 禁止删除或覆盖 /volume2/blade/uploads。
- 禁止打印 .env.prod、数据库密码、JWT secret、access token、refresh token。
- 禁止执行 docker-compose down -v、docker system prune --volumes、rm -rf 数据目录。
- 禁止使用 deploy/nas/deploy_from_local.sh，除非我明确说是首次部署。
- 日常发布必须使用 deploy/nas/deploy_app_from_local.sh --execute。

执行前必须：
1. 运行或等价执行 deploy/nas/check_platform.sh，确认 NAS 是 linux/amd64，compose 可用，mysql/uploads 目录存在。
2. 说明当前 git branch、commit、git status --short。
3. 说明是否存在未提交变更；如果有，列出变更范围。
4. 本地构建后端和前端。
5. 按变更范围运行相关测试。
6. 确认 Docker 镜像是 linux/amd64。
7. 创建 NAS 数据库备份，并确认备份文件非空。

执行发布：
- 使用 deploy/nas/deploy_app_from_local.sh --execute。
- 该脚本只允许重启 backend 和 web。

发布后必须验证：
1. docker-compose ps 显示 blade-mysql、blade-redis、blade-backend、blade-web 均 Up。
2. curl -I http://127.0.0.1:8899/catalog 返回 200/3xx。
3. 登录后验证关键 API 正常。
4. 本次如涉及文件中心/视频上传，必须验证文件中心图片/视频上传、列表、预览均正常。
5. 输出发布报告：发布版本、备份文件路径、测试结果、验证结果、是否有异常、是否需要回滚。

如果任何一步失败：
- 立即停止继续发布。
- 不要做数据库回滚。
- 先报告失败点和当前状态。
- 应用发布失败时优先考虑回滚 backend/web 应用镜像，不碰数据库。
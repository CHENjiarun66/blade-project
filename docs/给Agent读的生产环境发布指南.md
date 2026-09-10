请按 BladeProject 的 NAS 生产发布安全规范执行一次“日常应用发布”，不要执行首次部署或全量部署。

必须先阅读：
1. /Users/chenjiarun/Documents/BladeProject/AGENTS.md
2. /Users/chenjiarun/Documents/BladeProject/docs/13-NAS_PRODUCTION_OPS.md
3. /Users/chenjiarun/Documents/BladeProject/deploy/nas/README.md

发布目标：
- NAS：192.168.1.10
- 生产目录：/volume2/blade
- 局域网入口：https://192.168.1.10:8899/catalog
- 外网入口：https://frp-pen.com:33294（可由 `AGENT_EXTERNAL_URL` 覆盖）

严格限制：
- 只能发布 backend/web 应用容器。
- 禁止重建、重启、删除 MySQL/Redis。
- 禁止删除或覆盖 /volume2/blade/mysql。
- 禁止删除或覆盖 /volume2/blade/uploads。
- 禁止打印 .env.prod、数据库密码、JWT secret、access token、refresh token。
- 禁止执行 docker-compose down -v、docker system prune --volumes、rm -rf 数据目录。
- 禁止使用 deploy/nas/deploy_from_local.sh，除非我明确说是首次部署。
- 日常发布必须使用带确认与预演证据的 `deploy/nas/deploy_app_from_local.sh --execute`。

执行前必须：
1. 运行或等价执行 deploy/nas/check_platform.sh，确认 NAS 是 linux/amd64，compose 可用，mysql/uploads 目录存在。
2. 说明当前 git branch、commit、git status --short。
3. 说明是否存在未提交变更；如果有，列出变更范围。
4. 本地构建后端和前端。
5. 按变更范围运行相关测试。
6. 确认 Docker 镜像是 linux/amd64。
7. 准备与当前完整 Git commit 一致、`result=PASS` 且 `manual_review=0` 的生产副本预演证据。
8. 确认 `/volume2/blade/secrets/tls/blade.crt` 和 `blade.key` 已安全配置、未过期且匹配；禁止将私钥打包进镜像或输出到日志。
9. 创建 NAS 压缩数据库/schema 备份，确认 SHA-256 与 NAS 外副本校验通过。

执行发布：
- 使用 `ORDER_RELEASE_CONFIRM=YES REHEARSAL_REPORT=<绝对路径> deploy/nas/deploy_app_from_local.sh --execute`。
- 该脚本只允许重启 backend 和 web。

发布后必须验证：
1. docker-compose ps 显示 blade-mysql、blade-redis、blade-backend、blade-web 均 Up。
2. `curl -k -I https://127.0.0.1:8899/catalog` 返回 200/3xx，且外网入口不带 `-k` 通过可信 TLS 和 200/3xx 检查。
3. 登录后验证关键 API 正常。
4. 本次如涉及文件中心/视频上传，必须验证文件中心图片/视频上传、列表、预览均正常。
5. 输出发布报告：发布版本、备份文件路径、测试结果、验证结果、是否有异常、是否需要回滚。

如果任何一步失败：
- 立即停止继续发布。
- 保持维护页与停写，不要在未知状态下自动解除维护或做数据库回滚。
- 先报告失败点和当前状态。
- 应用发布失败时优先考虑回滚 backend/web 应用镜像，不碰数据库。

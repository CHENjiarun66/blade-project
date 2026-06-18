# 图片派生图生产发布与历史补生成清单

> 日期：2026-06-18  
> 状态：待生产执行  
> 范围：V38 图片派生图、PC/Catalog 分层加载、生产历史图片补生成  
> 禁止范围：本清单不授权直接修改生产路径、不授权覆盖 uploads、不授权数据库回滚

## 1. 已完成验收

- 后端全量测试：298/298 通过。
- PC 管理端生产构建通过。
- Catalog E2E：5/5 通过。
- 本机测试环境 89 张历史图片生成 178 个派生文件，0 FAILED、0 缺失、0 空文件。
- 浏览器实测文件中心、商品列表和 Catalog 使用 `card`；Catalog 全屏使用原图。

## 2. 候选版本

- 功能提交：`05e1545 feat(files): add image derivative pipeline`
- 测试环境记录：`074bbfa docs(files): record test backfill verification`
- 数据库变更：`V38__file_derivative.sql`
- 正式生产只能部署最终合入 `master` 的 release 提交，不直接部署上述 feature 提交。

## 3. 发布前门禁

- [ ] `develop` 集成测试通过。
- [ ] 从 `master` 创建只包含本功能的 release 候选。
- [ ] release 后端测试、前端构建、Catalog E2E 通过。
- [ ] NAS 平台检查通过，镜像确认为 `linux/amd64`。
- [ ] 生产数据库备份已创建且非空。
- [ ] `/volume2/blade/uploads` 已有可恢复快照或独立备份。
- [ ] 生产 `file_storage.storage_path` 全部符合 `/data/uploads/%`。
- [ ] 每个有效生产原图在 `blade-backend` 容器内可读。
- [ ] NAS 磁盘剩余空间满足派生文件和发布临时文件需求。
- [ ] 已记录生产租户列表，并为每个租户指定补生成批次。

> 生产旧图能否补生成的最终结论以本节预检为准：路径与可读性全部通过即可补生成；存在失败记录时，只隔离失败项，不得直接批量改库或移动原图。

## 4. 发布顺序

1. 记录当前 `master` 提交、当前生产镜像和数据库 Flyway 版本。
2. 备份数据库，确认 uploads 快照/备份和原图预检结果。
3. 将 release 合入 `master`，构建 `linux/amd64` 的 backend/web 镜像。
4. 仅更新 `blade-backend` 和 `blade-web`。
5. 等待后端启动，确认 Flyway V38 成功。
6. 验证登录、文件原图、商品/订单/文件中心、Catalog。
7. 按租户执行历史补生成：5 张试跑，随后 20/50 张分批。
8. 每批检查接口 failed、数据库状态、物理文件和页面显示。
9. 全部完成后复跑一次，要求 processed=0。

## 5. 停止条件

出现以下任一情况立即停止，不继续下一批：

- `failed > 0` 或出现持续 `PENDING`。
- 原图路径不在 `/data/uploads`，或容器内不可读。
- NAS 磁盘空间异常下降。
- `/preview` 或 `/variant` 出现 403/404/500。
- 商品、订单、文件中心或 Catalog 出现新增破图。
- MySQL、Redis、uploads 挂载与发布前不一致。

## 6. 回滚原则

- 应用问题优先回滚 backend/web 镜像。
- V38 只新增表，应用回滚时默认保留，不自动执行数据库回滚。
- 派生失败不会损坏原图，页面可回退原图。
- 数据库恢复、路径批量修复、派生文件删除必须另行取得用户确认。

详细命令与 NAS 路径见 `docs/13-NAS_PRODUCTION_OPS.md` 的“8.1 图片派生图生产补生成”。

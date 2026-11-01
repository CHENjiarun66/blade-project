# 变更记录

## 2026-11-02 项目范围、文档生命周期与代码评估 [codex]

**用户主线**：先稳定现有订单、库存和真实业务验收；新增移动、经营 Agent、完整 ERP/通用财务扩展后置，存量继续维护。

**完成**：

- 新增 PROJECT_SCOPE、PROJECT_REVIEW、CODE_REVIEW、project-streams.json，按 13 个方向区分实施/部署/真实验收和下一步；STATUS/HTML 看板从任务和方向共同生成。任务百分比明确仅为计数比例。
- 27 份文档移动：25 份历史 SOW/交付/发布记录归档，库存扩展与 iPad 行为采集 2 份方案进入候选需求；6 份过时长文保留快照后重写；另保留旧 OCR 章节，拆出两份历史变更/执行明细。删除 3 份重复指南，保留替代入口与完整原 docs 备份校验值。
- 重写文档中心、环境配置、订单库存/客户/NAS/架构/schema 参考和交接快照；纠正 PRD 中旧状态机、客户未开发、内置 OCR、tenant=0 旁路等过时描述。订单 API 改为实际动作路由，schema 指向累计 Flyway；对子项目 README/规范及部署入口同步校准。
- BA-806 改为已发布：62649cc 已在生产 Web 83312c4；BE-009 按原备注同步为暂缓。保留基线全部 380 个任务 ID，新增 OPS-DOC-001 与 BE-STAB-001～007；完成后总 388 项：327 完成、0 进行中、42 待办、6 部分、13 暂缓/外部承担。
- 代码评估登记 7 项未实施改进：真实操作者、库存动作授权、SQL 日志、异常诊断、普通发布与旧迁移分离、真实 JWT 回归入口、大页面/服务拆分。没有把评估写成已修复。

**实际验证**：

- `node scripts/check-docs.mjs`：93 份文档、548 个本地链接、13 个方向通过；检查目录、任务引用、范围表和迁移索引覆盖。
- `node scripts/gen-status.mjs`：388 项，无其他未归类状态；`node --check` 两个脚本及 `git diff --check` 通过。
- 任务 ID 比较：原 380 个全部保留，新增 8 个，无删除。归档新增文件在暂存后检查发现两处历史空白格式，已规范化；最终对完整基线 diff 检查通过。
- 本机提交钩子提示 Lefthook 不在 PATH，未作为验证证据；以上脚本均已显式执行。
- `mvn -o -q -Dtest=ProjectIdentityAuditProbeTest test`：3 个隔离身份缺陷探针均复现（3 failures、0 errors）；用户 4 在库存/客户被读为 1，在拆分被读为 null。探针不连接数据库，源码/结果留审计附件，临时源码和编译类已移出正常测试树。这是缺陷证据，不是回归通过。
- 本轮没有重新执行 880 项完整业务测试；880/880 为 2026-11-01 发布证据。业务源码、依赖、迁移和运行配置未变；没有生产部署/数据库写操作；原脏工作区未修改。

具体处理与风险见 [文档目录](./DOCUMENT_CATALOG.md)、[项目评估](./PROJECT_REVIEW_2026-11-02.md)、[代码评估](./CODE_REVIEW_2026-11-02.md)。


> 所有需求变更、架构变更、重大决策变更必须记录在此。
> 新 AI 阅读本文档可以快速了解项目的演变过程。
> 格式：日期 + 变更内容 + 原因 + 影响范围

---

## 2026-11-02 变更记录

### [整理] - Git 历史归档、正式/测试基线与进度纠偏

- 远程实查 25 个分支、本地 37 个；归档后各保留 6 个。先备份全部 refs bundle、两处未提交工作区的 patch/未跟踪文件与内容 SHA-256，再发布并验证 34 个归档标签和 2 个生产源代码标签，才移出 31 个本地/20 个远程历史引用；远程删除使用预期 SHA lease 与原子推送。
- 原 `develop` 为 2026-08-18 的旧基线，落后正式版 187 个提交，已快进到正式基线；新增干净工作区 `/Users/chenjiarun/Documents/BladeProject-worktrees/develop`。原主目录保留 V50 历史代码与未提交规划，分支改名 `codex/project-cockpit`，3 个本地看板提交已补推。
- 保留真实未合入的草稿删除（1 个提交）、看板工具（3 个）、协作规范（2 个）；另保留已有未提交声明文件的订单编辑分支。8 个干净历史工作区保持原提交并保留目录，清理 2 条已不存在目录的注册信息。三个共享 Git post-* 钩子添加 helper 存在性判断，解决 master/develop 缺看板文件时误报；原钩子已备份。
- 进度纠偏：BE-506 仪表盘统一读取范围已有生产代码与提交，改为已完成；档口生产发布/映射预演已有 2026-09-22 记录，改为已部署/部分收口，不再写未执行任何备份/预演。授权、专项灰度/回滚和性能证据未补齐的部分仍保留，未把上线等同最终验收。
- 更新任务/状态板、会话快照、Git 工作流，并新增 `docs/reference/GIT_INVENTORY_2026-11-02.md` 与 CSV 清单。任务记录为 380 项：326 完成/0 进行中/36 待办/6 部分/12 暂缓，约 89% 是历史清单勾选口径，不代表完整替代旧 ERP。
- 验证：远程 heads/tags SHA、祖先与补丁等价检查、bundle verify、脏工作区前后内容指纹、共享钩子 `sh -n` 与 develop 无 helper 调用、`node scripts/gen-status.mjs`、`git diff --check`。master/develop 业务代码相同，本次只改引用和文档，不重新构建/部署应用或执行迁移；最近生产 manifest 的 880/880 是 11-01 发版记录。

## 2026-11-01 变更记录

### [修复] - 商品图片上传无法解析当前用户

- 真实线上请求复现：`POST /api/files/upload` 返回 HTTP 200/业务 403、`无法解析当前用户`，未生成文件 ID。根因是实际 JWT principal 为 Spring Security `UserDetails`，文件模块只识别业务实体 `User`；原上传测试手工构造实体身份，未覆盖登录链路。
- 认证加载器新增 `AuthenticatedUserDetails`，在保留原账号状态、密码擦除和 authorities 语义的同时携带数据库 userId/tenantId；文件请求上下文和私人未绑定文件的创建者校验统一解析可靠 ID，拒绝匿名、未认证、只有用户名或租户不符的身份，不用默认管理员代替。
- 商品主图/图集/SKU 素材绑定记录兼容该认证身份，保存真实操作者 ID；没有改变商品图片选择、绑定角色、业务权限或数据库结构。已有有效 JWT 会在后续请求加载新身份类型，无须重新登录。
- 新增真实 MVC 登录 → JWT 过滤器 → 商品图片上传 → 预览 → 主图保存回归，校验文件与主图绑定 create_by 都为实际用户；另覆盖没有商品权限的上传拒绝、私人未绑定文件本人可读/他人 403，以及 4 项请求身份反例。修复前真实登录上传测试因业务码 403 失败。
- 验证：隔离测试库执行 `scripts/test-db-seed.sql` 后，`mvn -o -q test` **880/880**（Failures 0 / Errors 0 / Skipped 0）；`mvn -o -q -DskipTests package`、linux/amd64 镜像构建与 `git diff --check` 均通过，无新增 migration 或前端文件变更。
- 生产：应用 commit `9e0470c41bbb352940cdf07be2f4adabbee91c74`，release `20261101_upload_user_hotfix`。NAS/Mac 双份备份与镜像 SHA-256 均校验，仅 compose `up -d --no-deps backend` 加 Nginx reload，保留上一个文件中心修复及回滚镜像/jar；MySQL/Redis/web 的容器 ID、启动时间一致。启动确认 V69 无需迁移，切换前后保持 176 单、758 明细、355 文件元数据。
- 线上原登录态验收：商品图片上传业务码 200，文件 356 的 create_by=4 与当前账号一致，原图 PNG/200、thumb JPEG/200。测试附件已解绑并软删除，仅新增该测试元数据及软删除的测试绑定；有效文件总数发布前后均为 331，现有商品主图未改变。外网可信 HTTPS 200、维护模式关闭，证据在 `/volume2/blade/releases/20261101_upload_user_hotfix`。

### [修复] - 文件中心管理员列表请求失败

- 线上现象：文件夹树正常，`GET /api/files?current=1&size=20&status=1` 返回 HTTP 200、业务码 400 且无 message；NAS、后端、MySQL、Redis 和磁盘状态均正常。
- 根因：同时拥有 `data:order:peopleAll` 与 `btn:file:viewAll` 时，可见性 SQL 不引用 actor，但参数数组仍包含 actor；MyBatis-Plus `apply` 渲染查询抛出 `sql not contains: "{3}"`，经 MyBatis 包装后只记录空异常消息。
- 修复：只在 SELF 订单或仅本人可见的未绑定文件需要 actor 条件时绑定 actor 参数；租户、档口、敏感绑定和普通用户可见性规则保持原语义。
- 回归：新增 4 种人员/文件权限组合的真实 MyBatis 参数渲染与租户 SQL 解析测试，以及 Owner 真实数据库分页/计数回归；修复前 Owner 参数渲染测试失败，修复后通过。
- 发布范围：基于当前生产 `83312c4`（文档基线 `1e26ce7`）的后端热修复，无新增 migration、无前端变更；生产库 V69。已完成 NAS/Mac 双份压缩数据库备份与 SHA-256 校验。
- 验证：`mvn -o -q -Dtest='File*Test' test` 在恢复生产备份的隔离库通过 **208/208**；干净隔离库执行 `scripts/test-db-seed.sql` 后，`mvn -o -q test` 全量通过 **873/873**（Failures 0 / Errors 0 / Skipped 0）；`mvn -o -q -DskipTests package` 与 linux/amd64 镜像构建通过，`git diff --check` 通过。生产副本用于文件专项检查；全量测试改用空库，避免测试默认档口与生产副本既有默认档口冲突。
- 生产：应用 commit `ce8a69f3815920a3e1dcf51d7adf0194fea6a129`，release `20261101_file_center_hotfix`；以原生产镜像为基础替换已验证 jar，仅用 compose `up -d --no-deps backend` 更新后端，保留旧镜像与 jar 回滚。此次没有迁移，未执行订单历史迁移/重放脚本。前端仅重载 Nginx 解析后端地址。
- 线上验收：当前登录账号的列表、第二页、商品素材、未绑定、回收站与文件夹树均为 HTTP 200/业务 200；有效文件 331，原图与 thumb 均返回 JPEG/200，页面 20 张图片加载完成。Flyway V69、176 张订单、758 条明细、355 条文件元数据均与发布前一致；MySQL/Redis/web 的容器 ID 和启动时间一致，维护模式关闭，外网可信 HTTPS 200。发布证据与回滚文件位于 `/volume2/blade/releases/20261101_file_center_hotfix`。


历史交付、迁移与审核记录见 [2026-09 及以前变更记录](./archive/changelog/2026-09-and-earlier.md)。

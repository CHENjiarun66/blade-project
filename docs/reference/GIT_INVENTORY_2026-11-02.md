# Git 分支、正式/测试版本与进度盘点（2026-11-02）

本报告交叉核对了 GitHub 实际分支、本地 Git 提交关系、全部 worktree、NAS 运行镜像/发布 manifest、生产 Flyway 版本、任务表与未提交文件。统计不把“分支存在”当成“功能未上线”，也不把文档勾选当成生产验收。

## 1. 整理结果

| 范围 | 整理前 | 整理后 |
|---|---:|---:|
| GitHub 实际远程分支 | 25 | 6 |
| 本地分支 | 37 | 6 |
| 当前应跟踪的独有工作 | 3 组 | 3 组（全部保留） |
| 历史分支引用 | 31 本地 / 20 远程 | 34 个归档标签保留历史 tip |

本地与远程多数是同名分支的镜像，不能将 37+25 理解为 62 个开发任务。另有 2 个明确的生产源代码标签。临时盘点分支在合并报告后移除，不计入最终 6 个。

- 已完成历史分支归档：商品、订单、缩略图、Catalog、WhatsApp、Agent Key、档口权限和两次文件热修复等。
- `release/2026-06-18-file-derivatives` 虽不是 master 的直接祖先，但两个非合并提交均由 `git cherry` 核实补丁已在 master，仅保留旧合并结构，故标签归档。
- 删除活动分支引用前，已在 GitHub 验证归档标签指向原 SHA；删除时校验远程预期 SHA，防止误删审计期间新增提交。
- 全仓库 bundle、两处脏工作区的 staged/unstaged patch、未跟踪文件压缩包、内容 SHA-256 已做本地备份。原 12 个已修改文件、23 个未跟踪文件/符号链接，以及另一工作区的 1 个生成声明文件，内容校验一致。
- 8 个已完成且干净的旧工作区只解除分支占用并停留原提交，目录、构建产物和忽略文件保留；清掉 2 条已不存在目录的 worktree 记录。
- 修复了 3 个共享 post-* Git 钩子：当前 worktree 没有 cockpit helper 时跳过，避免在 master/develop 误报脚本不存在。原钩子已备份，其余钩子保持原样。

## 2. 正式版和测试版到底有什么差别

| 对象 | 核实结果 | 含义 |
|---|---|---|
| NAS 后端 | `blade-backend:20261101_upload_user_hotfix`，源码 `9e0470c` | 已含文件列表和商品图片上传两次修复；最近发布 880/880 后端回归 |
| NAS 网页 | `blade-web:20260925_draft_customer_83312c4` | 两次热修复没有前端代码变更，因此网页镜像仍沿用该版本 |
| NAS 数据库 | 实际查询为 V69 | 与最新正式源码迁移集一致 |
| `master` | 审计前 `6cb017a` | 在运行源码之后的提交只补发布记录；此次盘点提交只改文档/状态产物，不部署新应用 |
| `develop` 整理前 | `0335c51`，2026-08-18，落后 master 187 个提交 | 是过期测试分支，并非更先进的测试版 |
| `develop` 整理后 | 与 master 同基线 | 业务代码与正式版一致；待合入分支没有被混入 |
| 原主工作目录 | `codex/project-cockpit`，`5ec9b29`，最高迁移 V50 | 保留旧代码、看板与未提交规划，不能继续当作最新 ERP 测试基线 |

已建立干净开发/测试工作区：`/Users/chenjiarun/Documents/BladeProject-worktrees/develop`。此目录的代码同步完成不等于已经启动测试服务器；本次在 5777/8080/18080 等常用端口未发现开发服务监听。

已增加生产标记：`prod/backend-2026-11-01-upload-user-hotfix` → `9e0470c`；`prod/web-2026-09-25-draft-customer` → `83312c4`。本次未操作生产数据、应用容器或数据库迁移。

## 3. 六个保留分支

| 分支 | 用途 | 当前状态 |
|---|---|---|
| `master` | 正式主线 | 与 NAS 当前应用代码一致；此次只增加盘点文档 |
| `develop` | 开发/测试集成基线 | 由落后 187 个提交快进到正式代码；新工作区已建立 |
| `codex/order-draft-delete` | 待合入业务功能 | 草稿单个/批量软删除；1 个独有提交，旧 V61 基线，需移植与回归 |
| `codex/project-cockpit` | 待合入开发工具 | 原 phase2 分支重命名；3 个看板提交已补推，工作目录仍含未提交规划 |
| `feature/agent-collaboration-hardening` | 待复核协作规范 | 2 个独有文档提交；需要适配现行工作流后选择性合入 |
| `codex/order-edit-entry-consolidation` | 已合入但工作区未净 | 功能已在正式版；尚有 auto-imports.d.ts 的本地改动，保留分支 |

`codex/order-edit-entry-consolidation` 保留原因是未提交文件，不代表订单编辑功能尚未发布。该本地改动确认处理后，可按同样归档流程关闭这条分支。

## 4. 哪些内容还没有补进正式版

### 已经写在分支里，但还没合入

1. **草稿删除**：`e7ed233`；单个/批量逻辑删除、权限检查、只允许编辑中的草稿、PC 删除入口与回归用例。生产控制器中尚无这两个删除路由，生产草稿列表也没有相应删除交互。应从最新 develop 新建移植分支，适配 V69 和档口权限后验证，不能把旧 V61 分支整体当成最新版本部署。
2. **项目全景看板**：`f2ebdf7` / `e3e8651` / `5ec9b29`；CRUD 下钻、路线图、进展快照、打开看板脚本与 Git 钩子。这是开发工具，不是尚未上线的 ERP 订单重构。原本只在本地的 3 个提交已推送到新命名分支。
3. **协作规范硬化**：`1b9bcf7` / `a55c703`；AGENTS、协作流程和旧进度同步文档。需要按现行生产基线复核，尤其不能用旧任务快照覆盖最新状态。

### 尚未开发或尚未做完业务验收

| 工作流 | 已有成果 | 真正剩余 |
|---|---|---|
| 真实纸单与 Agent | 草稿、多图、匹配、占位拆分、确认、正式订单等代码已部署 | 本地真实批次与外网 30 单联调验收 |
| WhatsApp | 归档与客户工作区代码、迁移已部署 | Mac Collector → NAS 的真实增量、媒体与恢复联调 |
| 档口权限 | 主数据、范围策略、V63–V68、历史映射工具已部署 | 用户授权清单、异常人工确认、专项灰度/回滚/性能、兼容期复盘 |
| 订单回退与审批 | `18-ORDER_REVERSAL_APPROVAL_DESIGN.md` 已设计 | BE-621/622/623、FE-620、冲销与逆向库存门禁仍待开发 |
| 经营 Agent | 部分只读事实和接入能力 | 跟进/风险/补货/周期报告/搜索、限流、毛利 scope 专项等 |
| 替代旧 ERP | 销售订单、SKU、库存与权限基础已有 | 迁移期初、历史档案、无价采购收货、完整采购/应付/资金/核销及对账仍有缺口；完整财务后置 |
| 移动端 | 骨架和部分订单/库存页面 | 搜索、扫码、库存调整、商品编辑/SKU 配置、趋势/热销等 |
| 证书续期 | 现用证书仍有效 | 实际证书到期为 2026-11-26 11:59:59（北京时间）；按现行任务要求最迟 11-19 前完成续期 |

未提交文件中还有旧 ERP 审计/优化蓝图、通用财务事实、无价库存、期初迁移、历史档案、角色价格隐私及 Excel 模板。这些是**资料和规划在途**，不能据此宣称供应链/通用财务已经实现。此次完整保留，后续应在最新基线上挑出有效增量，而不是覆盖生产文档。

## 5. 现在的进度与“正在做”

修正过期记录后，任务表共有 **380 项：326 完成、36 待办、6 部分完成、12 暂缓/外部承担，单项明确标记进行中为 0**。活跃范围勾选进度约 **89%**，PC 93%、移动端 64%；这是历史任务清单的记录口径，**不是完整替代旧 ERP 的完成率，也不是逐功能生产验收率**。

本次纠正了两类误报：

- BE-506 不能继续记为未实现：生产 DashboardController 已检查 `menu:dashboard`，DashboardServiceImpl 已使用统一 OrderReadScope，相关提交 `aad6b88` / `9c1a481` 已在 master。
- 档口发布、生产副本映射已有 2026-09-22 证据，不能再写“未执行任何备份/预演”。但专项灰度、回滚、人工授权和性能验收没有完整证据，仍按部分完成保留。

目前能确认的是三组待处理分支和两处未提交工作区，不能仅凭文件未提交或一个旧分支存在，就断言还有 Agent 正在运行开发。最近的两次线上 bug 已完成发布。

建议顺序：① 草稿删除迁移到最新基线并回归；② 30 单/WhatsApp/档口真实验收收口，同时处理证书续期；③ 单独整理旧 ERP 增量规划，再推进期初迁移和无价采购；④ 经营 Agent 与移动端剩余功能。

## 6. 恢复与证据

历史分支完整列表和 SHA/归档标签见 [归档清单 CSV](./GIT_BRANCH_ARCHIVE_2026-11-02.csv)。恢复单条历史分支示例：

```bash
git switch -c feature/product-management-v2 archive/2026-11-02/feature/product-management-v2
```

旧 develop 可从 `archive/2026-11-02/develop` 恢复。旧 phase2 本地 tip 与旧远程 tip 分别保留在 `archive/2026-11-02/codex/phase2-order-drafts` 和 `archive/2026-11-02/remote/codex/phase2-order-drafts`。

本机完整备份目录：`/Users/chenjiarun/.codex/visualizations/2026/10/31/01a19331-bd18-7490-ad70-6b9ec56f2e3d/branch-audit-2026-11-02/backup`。包含 `all-refs-before.bundle`、各工作区 patch/未跟踪文件包及 SHA256SUMS；备份含业务文件，仅保存在本机，没有把未提交 Excel/图片传到 GitHub。

```bash
git clone /absolute/path/to/all-refs-before.bundle recovered-repository
```

验证：远程原始分支与归档标签 SHA、Git 祖先关系与文档补丁等价、全部已存在工作区未提交文件的前后指纹、bundle verify、master/develop 业务代码一致性、node scripts/gen-status.mjs、git diff --check、共享钩子 shell 语法与无 helper 分支调用。最近生产发版的 880 项回归来自发布 manifest，本次仅整理引用和文档，没有重新运行业务测试。

## 7. 整理前逐分支去向

| 范围 | 原分支 | 原 SHA | 处理 |
|---|---|---|---|
| 本地 | `codex/agent-connection-docs` | `8c50c607` | 已归档，移出活动分支 |
| 本地 | `codex/agent-key-capabilities` | `19886897` | 已归档，移出活动分支 |
| 本地 | `codex/draft-customer-country-parity` | `1e26ce7e` | 已归档，移出活动分支 |
| 本地 | `codex/fix-draft-source-shop` | `48369d32` | 已归档，移出活动分支 |
| 本地 | `codex/fix-file-center-visibility` | `ce8a69f3` | 已归档，移出活动分支 |
| 本地 | `codex/fix-upload-user-principal` | `6cb017a2` | 已归档，移出活动分支 |
| 本地 | `codex/order-draft-delete` | `e7ed233f` | 保留 |
| 本地 | `codex/order-edit-entry-consolidation` | `c6fc2938` | 功能已合入，保护脏工作区而保留 |
| 本地 | `codex/order-entry-customer-fixes` | `df847370` | 已归档，移出活动分支 |
| 本地 | `codex/outlet-access-control-planning` | `191f0d82` | 已归档，移出活动分支 |
| 本地 | `codex/phase2-order-drafts` | `5ec9b297` | 已重命名并补推 3 个本地提交 |
| 本地 | `develop` | `0335c510` | 已快进到正式基线；旧 tip 已归档 |
| 本地 | `feature/agent-collaboration-hardening` | `a55c7036` | 保留 |
| 本地 | `feature/ba-701-703-acceptance` | `fedb91cf` | 已归档，移出活动分支 |
| 本地 | `feature/catalog-pinch-zoom-smooth` | `6cc04138` | 已归档，移出活动分支 |
| 本地 | `feature/file-derivatives-v1` | `47590f2d` | 已归档，移出活动分支 |
| 本地 | `feature/order-inventory-soft-coupling-v1` | `36907d75` | 已归档，移出活动分支 |
| 本地 | `feature/order-lifecycle-finance-refactor` | `65853a0d` | 已归档，移出活动分支 |
| 本地 | `feature/order-list-filter-confirm` | `dd0378e1` | 已归档，移出活动分支 |
| 本地 | `feature/order-module-dev-20260611` | `3aa02cc4` | 已归档，移出活动分支 |
| 本地 | `feature/order-soft-coupling-edit-permissions` | `359a3101` | 已归档，移出活动分支 |
| 本地 | `feature/outlet-access-control` | `8b196206` | 已归档，移出活动分支 |
| 本地 | `feature/product-management-v2` | `22312d1a` | 已归档，移出活动分支 |
| 本地 | `feature/whatsapp-local-archive` | `bdffcd28` | 已归档，移出活动分支 |
| 本地 | `fix/backend-test-baseline` | `9a2beffa` | 已归档，移出活动分支 |
| 本地 | `hotfix/order-image-preview` | `97ac9d07` | 已归档，移出活动分支 |
| 本地 | `master` | `6cb017a2` | 保留 |
| 本地 | `release/2026-06-11-order-quick` | `826d3d4e` | 已归档，移出活动分支 |
| 本地 | `release/2026-06-15-product-test-baseline` | `9a2beffa` | 已归档，移出活动分支 |
| 本地 | `release/2026-06-18-file-derivatives` | `065d5593` | 已归档，移出活动分支 |
| 本地 | `release/2026-08-17-catalog-ipad` | `898a8bdd` | 已归档，移出活动分支 |
| 本地 | `release/2026-08-17-order-filter-confirm` | `9ab7f1c5` | 已归档，移出活动分支 |
| 本地 | `release/2026-08-18-permission-acceptance` | `8ef52d23` | 已归档，移出活动分支 |
| 本地 | `release/2026-09-16-order-drafts` | `fff329b1` | 已归档，移出活动分支 |
| 本地 | `release/2026-09-22-order-entry-customer-fixes` | `df847370` | 已归档，移出活动分支 |
| 本地 | `release/2026-09-23-order-edit-consolidation` | `e09c69df` | 已归档，移出活动分支 |
| 本地 | `snapshot/current-all-work-20260611` | `984eff01` | 已归档，移出活动分支 |
| 远程 | `codex/agent-connection-docs` | `8c50c607` | 已归档，移出活动分支 |
| 远程 | `codex/agent-key-capabilities` | `19886897` | 已归档，移出活动分支 |
| 远程 | `codex/draft-customer-country-parity` | `1e26ce7e` | 已归档，移出活动分支 |
| 远程 | `codex/order-draft-delete` | `e7ed233f` | 保留 |
| 远程 | `codex/order-edit-entry-consolidation` | `c6fc2938` | 功能已合入，保护脏工作区而保留 |
| 远程 | `codex/order-entry-customer-fixes` | `df847370` | 已归档，移出活动分支 |
| 远程 | `codex/phase2-order-drafts` | `5252339f` | 旧远程归档；本地重命名为 codex/project-cockpit |
| 远程 | `develop` | `0335c510` | 已快进到正式基线；旧 tip 已归档 |
| 远程 | `feature/agent-collaboration-hardening` | `a55c7036` | 保留 |
| 远程 | `feature/ba-701-703-acceptance` | `fedb91cf` | 已归档，移出活动分支 |
| 远程 | `feature/catalog-pinch-zoom-smooth` | `6cc04138` | 已归档，移出活动分支 |
| 远程 | `feature/order-lifecycle-finance-refactor` | `65853a0d` | 已归档，移出活动分支 |
| 远程 | `feature/order-list-filter-confirm` | `dd0378e1` | 已归档，移出活动分支 |
| 远程 | `feature/order-module-dev-20260611` | `3aa02cc4` | 已归档，移出活动分支 |
| 远程 | `feature/product-management-v2` | `22312d1a` | 已归档，移出活动分支 |
| 远程 | `feature/whatsapp-local-archive` | `bdffcd28` | 已归档，移出活动分支 |
| 远程 | `master` | `6cb017a2` | 保留 |
| 远程 | `release/2026-06-15-product-test-baseline` | `9a2beffa` | 已归档，移出活动分支 |
| 远程 | `release/2026-08-17-catalog-ipad` | `898a8bdd` | 已归档，移出活动分支 |
| 远程 | `release/2026-08-17-order-filter-confirm` | `9ab7f1c5` | 已归档，移出活动分支 |
| 远程 | `release/2026-08-18-permission-acceptance` | `8ef52d23` | 已归档，移出活动分支 |
| 远程 | `release/2026-09-16-order-drafts` | `fff329b1` | 已归档，移出活动分支 |
| 远程 | `release/2026-09-22-order-entry-customer-fixes` | `df847370` | 已归档，移出活动分支 |
| 远程 | `release/2026-09-23-order-edit-consolidation` | `e09c69df` | 已归档，移出活动分支 |
| 远程 | `snapshot/current-all-work-20260611` | `984eff01` | 已归档，移出活动分支 |

# 当前会话上下文

> 本文件是项目的快速状态快照，用于新 AI 和新会话快速接手。
> 这里只保留摘要信息；任务明细以 `03-TASKS.md` 为准，变更历史以 `05-CHANGELOG.md` 为准。

---

## 项目基本信息

| 项目 | 值 |
|------|---|
| 项目名称 | BladeProject |
| 启动日期 | 2026-03-21 |
| 当前阶段 | 后端核心模块、PC 管理端主要业务页面、库存并发控制、跨仓总量预留、配货计划、权限基础能力、订单编辑和追加收款均已落地；客户模块国际化升级（国家区号选择器 + 客户详情页 3 Tab）已完成，E2E 测试 12/12 通过；客户模块优化 Phase 4.6 M1~M4 全部完成；看板系统 BA-603 库存统计（周转分析）已完成；订单导出 BA-204 已完成；统一文件上传和文件中心底座已完成；图片派生图第一版 BE-1012、BA-1007、BA-1028 已完成，PC 与 Catalog 已按 thumb/card/original 分层加载；客户 iPad Catalog 现货选款页第一版已完成并已上线 NAS 生产；移动端继续开发中 |
| 下一步 | **权限页面 BA-701~703 已完成最终验收并于 2026-08-18 上线 NAS 生产**（Release id `20260818_124459`）：`master` 已更新至 `8d19a7a`；发布前生产库备份 `/volume2/blade/db-backups/pre_app_deploy_20260818_124459.sql` 已生成；只重启 `blade-backend`/`blade-web`，MySQL/Redis/uploads 未触碰；生产库 Flyway 已从 V40 迁移到 V42（V41 ROLE_OWNER API 权限 + V42 多租户修正）；`https://10.13.13.1:8899/catalog`、`/orders`、`/system` 均返回 200，登录与权限 API 验证通过，ROLE_OWNER/ROLE_ADMIN 各 11 项 API 权限。建议用户在生产入口做人工复验（系统管理页三 Tab 操作）。开发侧下一步为仪表盘数据权限、移动端真实数据接入和 Agent Gateway 未完成能力。部分发货、分批发货和缺货退款继续排除。 |

---

## 项目路径

| 项目 | 路径 |
|------|------|
| BladeProject 主目录 | `./` |
| 文档中心 | `./docs/` |
| 后端 | `./blade-backend/` |
| 移动端 | `./blade-mobile/` |
| PC 管理端 | `./blade-admin/` |
| 共享类型 | `./packages/types/` |
| Stitch 原型 | `./stitch/` |

## 当前本地运行环境

| 项目 | 值 |
|------|------|
| MySQL 容器 | `blade-mysql` |
| Redis 容器 | `blade-redis` |
| Nacos 容器 | `blade-nacos` |
| 默认后端数据库 | `blade_project` |
| 本地生产库保留 | `blade_project_prod` |
| 数据库覆盖方式 | `BLADE_DB_URL` / `BLADE_DB_USERNAME` / `BLADE_DB_PASSWORD` |
| NAS 生产环境 | `192.168.1.10:/volume2/blade`，入口 `http://192.168.1.10:8899/catalog` |
| NAS 运维手册 | [13-NAS_PRODUCTION_OPS.md](./13-NAS_PRODUCTION_OPS.md) |
| Git 分支/发布规范 | [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md) |

> 2026-08-17 接手复验备注：本机 MySQL 已停止，Docker `blade-mysql` 已成功占用 `3306`；`blade_project` 开发库启动后由 Flyway 从 V38 迁移到 V40。`test_tenant/admin/admin123` 可登录，真实 Catalog API 返回 `code=200,total=1119`；真实前端 `/catalog` iPad 竖屏冒烟通过。

---

## 单一事实来源

| 信息类型 | 以此文档为准 |
|-----------|--------------|
| 技术栈与业务规则 | [02-PRD.md](./02-PRD.md) |
| 当前任务进度 | [03-TASKS.md](./03-TASKS.md) |
| 最近变更历史 | [05-CHANGELOG.md](./05-CHANGELOG.md) |
| 分支开发与生产发布 | [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md) |
| 快速接手摘要 | 本文档 |

---

## 当前摘要

### 当前 Git / 发布规则

- 当前已建立分支规范：[reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md)。
- `master` 定义为生产稳定分支，NAS 生产环境只部署 `master`。
- 新功能默认使用 `feature/*` 分支开发，集成测试使用 `develop`，上线候选使用 `release/*`。
- 当前大范围开发快照分支为 `snapshot/current-all-work-20260611`，用于保存 2026-06-11 分类提交后的完整开发成果；是否整体上线需先创建/验证 release，再合入 `master`。
- GitHub 远程通道为 `origin = https://github.com/CHENjiarun66/blade-project.git`；push/fetch 失败时先检查代理和认证，不要擅自更换远程仓库。

### 已完成的关键能力

- 后端商品、库存、订单、客户、权限、看板接口主体已完成。
- 库存并发控制已完成，包含 Redis 分布式锁和乐观锁。
- 跨仓总量预留已完成，`inventory_global_reserve` 和 `global_reserved_qty` 已落地。
- 配货计划与调整记录已落地，订单支持 `ADJUSTMENT_PENDING`、`READY_TO_SHIP` 等中间状态。
- 订单状态机 4 项缺陷已修复（paymentStatus 同步、adjustmentStatus 初始化、减配释放多余预留、取消条件校验）。
- 订单编辑功能已实现（客户信息/送货/备注/图片，支持 status>=4 禁止修改）。
- 追加收款与抹零/短款结清已实现：收款不锁库存，`payment_status=2` 表示已结清；尾款统一按 `max(total-refund-writeOff-paid,0)` 计算。
- `blade-admin` 已完成订单、库存、商品、客户、系统管理等主要页面。
- 前端菜单权限过滤已完成，系统可按权限展示菜单和路由。
- `packages/types` 已搭建并被移动端集成使用。
- **客户模块国际化已完成**：国家区号选择器（WhatsApp 风格可搜索下拉，约 140 个国家/地区，支持中英文+区号筛选）、客户详情页（3 个独立 Tab：基本信息/订单记录/商品偏好，支持颜色/尺码/品类偏好柱状图）。E2E 测试全部通过（12/12 测试用例）。
- **客户模块优化 Phase 4.6 M1~M4 全部完成**：M1 数据质量（BE-412~414）✅ + M2 用户体验（BE-415~417）✅ + M3 业务功能（BE-418~420）✅ + M4 架构能力（BE-421~423）✅ 已完成
- **库存周转分析已完成**：仪表盘新增库存周转率、库存总量、库存积压预警卡片（BA-603），平均在库天数已移除。
- **仪表盘订单统计口径已调整**：订单统计按 `order_date`（为空回退 `create_time`）+ 已产生收款订单（`paid_amount > 0` 或 `payment_status in (1,2)`）+ 应收净额（`total_amount - refund_amount - write_off_amount`，最低 0）统计，并新增毛利和销量统计。
- **数据分析页 v1 已完成**：新增 `/analytics` 独立页面，支持经营汇总、趋势、商品/SKU/颜色/尺码排行和商品详情拆解；新增 `menu:analytics` 与 `data:analytics:profit` 权限，毛利/成本/毛利率按权限展示。
- **保持登录 30 天已生效**：登录页 `remember` 会传到后端，勾选时 refresh token 有效期为 30 天并在续期时延续；access token 保持 1 小时，前端会在业务请求发出前发现 10 分钟内过期并主动刷新。
- **订单导出已完成**：订单列表页新增导出按钮，支持筛选条件导出 Excel（BA-204）
- **订单列表筛选确认按钮已完成（BA-214）**：订单列表筛选区新增“确认筛选”按钮，关键字回车提交；日期范围已传入后端并按 `order_date` 查询，旧数据为空时回退 `create_time`；导出复用同一筛选条件。
- **快速录单商品级批量录入已完成（BA-207）**：选择商品后展示正常状态 SKU 颜色 x 尺码矩阵，批量填写数量并一次性添加到订单明细；第一版不读取、不展示、不校验库存；重复 `skuId` 自动合并数量且不覆盖已改单价/成本价。SOW 见 [2026-06-11-quick-order-product-batch-entry-sow.md](./superpowers/plans/2026-06-11-quick-order-product-batch-entry-sow.md)。
- **个人中心已完成**：个人中心页面（用户信息展示、修改密码）、头部下拉菜单（BA-704）
- **统一文件存储第一版已完成**：新增 `file_storage` 表、统一上传/预览/软删除/绑定接口，本地存储落地；订单图片、PC/移动端入库凭证、商品主图均已改为上传后保存 fileId；浏览器原生 `<img>`/新窗口预览通过 `/api/files/{id}/preview?previewToken=...` 进入统一权限校验，后续可切七牛云/NAS。
- **图片派生图第一版已完成**：V38 新增 `file_derivative`；上传图片后生成 `thumb`（长边 320px）和 `card`（长边 800px），生成失败不影响原图；`/api/files/{id}/variant` 继承原图权限并缺失回退原图。PC 商品/订单/文件中心与 Catalog 已分层接入，Catalog IndexedDB 缓存按 `original/thumb/card` 隔离。历史图片需通过当前租户批量接口分批补生成。
- **文件中心/数字资产中心后端底座已完成 Phase 6.6**：新增 [12-FILE_CENTER_ASSET_DESIGN.md](./12-FILE_CENTER_ASSET_DESIGN.md)，明确文件中心不是单纯图片/视频相册，而是通用数字资产中心；BE-1001~BE-1011 已完成到资产表结构、分页/详情、文件夹、多业务绑定、批量操作、有效绑定删除保护、未绑定治理、第一版安全清理调度、商品/SKU 图片绑定、基础视频上传分类、私有预览权限和文件中心回归测试。清理调度默认关闭，按配置 tenant-id 处理单租户，仅软删除/标记元数据，不做真实物理删除。
- **后端测试基线已修复**：独立分支 `fix/backend-test-baseline` 已完成 BE-1030~BE-1033；`cd blade-backend && mvn test` 通过（Tests run 244, Failures 0, Errors 0, Skipped 0），修复范围包含测试认证夹具、订单状态机断言和商品/文件实体显式列映射。
- **NAS 生产环境已初步部署完成**：生产目录 `/volume2/blade`，前端入口 `http://192.168.1.10:8899/catalog`；容器为 `blade-mysql`、`blade-redis`、`blade-backend`、`blade-web`；NAS 数据库已从本机生产库 `blade_project_prod` 迁移并将主租户 code 调整为 `dwy_jiajiadress`。后续发布/备份/回滚按 [13-NAS_PRODUCTION_OPS.md](./13-NAS_PRODUCTION_OPS.md) 执行。

### 仍在进行或未完成的事项

- `TEST-ORDER-INV-001` 已完成：MySQL 8 临时库 V1-V40 累计迁移通过；后端全量 `mvn test` 383 项通过；PC `npm run build` 通过；浏览器关键路径覆盖 UI 登录、订单创建、定金、追加收款、抹零结清、配货计划、确认调整、发货和详情页渲染。
- 仪表盘数据权限尚未实现。
- 外部 Agent 对接需求已锁定为只读 Agent Gateway 第一版；安全边界、认证审计、款式趋势和颜色尺码结构已完成，凭证管理与毛利 scope 部分完成，客户跟进/风险、库存建议、周期分析、统一搜索和限流验证尚未完成。
- 文件中心/数字资产中心后端 BE-1001~BE-1011 已完成；PC `/files` 页面 BA-1001~BA-1006 已完成（路由菜单、虚拟入口文件夹树、网格列表视图筛选分页、上传移动删除、商品SKU绑定弹窗、未绑定清理管理），V36 已补齐 `menu:file` 与文件中心按钮权限；Catalog 聚合接口和 `/catalog` 展示页第一版已完成，V37 已补齐 `menu:catalog` 与 `data:catalog:view` 权限。
- 图片派生图/缩略图性能优化 `BE-1012`、`BA-1007`、`BA-1028` 已完成第一版；本机测试环境 tenant 1 的 89 张历史图片已补齐 178 个 `thumb/card` 派生文件，0 失败、0 缺失。生产环境补生成及后续异步队列、自动重试、视频封面和 NAS/七牛云/CDN Provider 尚未执行。
- 商品管理 v2 已完成：`BE-1013` 商品素材查询 API、`BE-1014` 删除引用保护与 SKU 精细更新、`BA-407~BA-410` 商品编辑页 v2/SKU 明细/商品素材/删除禁用交互均已落地；ROM/SOW 见 [2026-06-14-product-management-v2-rom-sow.md](./superpowers/plans/2026-06-14-product-management-v2-rom-sow.md)。
- 文件预览权限补强已完成：PRIVATE 文件仍由后端校验登录、租户、业务权限；前端所有 fileId 预览必须走 `filePreviewUrl(fileId)`，不要手写 `/api/files/{id}/preview`，否则浏览器 `<img>` 不会带认证信息。
- 移动端页面开发仍在继续。
- OCR 拍照录单等任务仍未完成。

---

## 当前阻塞与风险

| 问题 | 优先级 | 状态 | 说明 |
|------|--------|------|------|
| 仪表盘数据权限 | P2 | 🔴 未实现 | 后端统计接口尚未按权限过滤数据 |
| 文档状态漂移 | P1 | 🟡 持续治理 | 2026-06-18 已修正商品 v2、看板和 Agent Gateway 的已知入口状态；后续每轮交接继续以 TASKS、CHANGELOG 和代码交叉核对 |
| 文件中心边界漂移 | P1 | 🟡 已设边界 | 第一版不得漂移到视频转码、分片上传、七牛云/NAS、公开分享链接；以 `12-FILE_CENTER_ASSET_DESIGN.md` 为准 |

**说明**：
- 订单与库存开发前，优先阅读 [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md) 和 [06-ORDER_INVENTORY_DESIGN.md](./06-ORDER_INVENTORY_DESIGN.md)。
- 当前代码真相优先于过时文档；若发现冲突，以 `TASKS + CHANGELOG + 代码实现` 交叉核对。

---

## 最近完成的代表性能力

### 配货计划与订单状态扩展

- 后端已实现 `OrderDeliveryPlanService`、配货计划 CRUD、调整记录、确认/取消调整。
- 订单状态已扩展到包含 `ADJUSTMENT_PENDING` 和 `READY_TO_SHIP`。
- `blade-admin` 订单详情页已支持创建、编辑、确认、取消配货计划和查看调整记录。

### 订单编辑与追加收款

- 订单列表页新增编辑按钮，弹窗顶部显示订单上下文摘要（订单号/状态/金额），支持编辑客户信息/送货方式/备注/图片 fileId。
- 订单详情页追加收款支持普通收款与标记结清；结清原因和核销金额可追溯，已结清订单不再显示追加收款按钮。
- 后端 `GlobalExceptionHandler` 补充 `RuntimeException` 专项处理，业务校验错误不再返回 500。

### 跨仓总量预留

- `inventory` 表已增加 `global_reserved_qty`。
- 已实现 `globalReserve`、`globalRelease`、`getGlobalAvailableQty`。
- 历史跨仓总量预留结构继续保留，但当前生产订单流程的确认收款、追加收款、取消和减配均不再创建或释放硬预留。

### 权限基础能力

- 后端权限表、角色权限关系、权限判断逻辑已完成。
- `blade-admin` 已落地系统管理页和菜单权限过滤。

### 客户模块国际化与优化计划

- 国家区号选择器（WhatsApp 风格可搜索下拉，约 140 个国家/地区，支持中英文+区号筛选）
- 客户详情页（3 个独立 Tab：基本信息/订单记录/商品偏好，支持颜色/尺码/品类偏好柱状图）
- E2E 测试全部通过（12/12 测试用例）
- **Phase 4.6 M1+M2+M3+M4 全部完成 ✅**：
  - M1：电话重复检查（唯一索引+应用层校验）、删除客户订单保护（进行中订单拦截）、N+1查询优化
  - M2：订单分页（page/size参数）、常用国家置顶（localStorage）、国家选择器键盘导航（↑↓/Enter/Esc）
  - M3：客户标签功能（crm_customer_tag + crm_customer_tag_rel，完整CRUD+分配接口）、沉默客户预警（GET /api/dashboard/silent-customers?days=90）、偏好时间范围筛选（startDate/endDate参数）
  - M4：客户数据权限（create_by 字段 + mine 筛选）、操作审计日志（crm_customer_operation_log 表）、偏好数据 Redis 缓存（1小时 TTL）

### 库存统计与订单导出

- 仪表盘新增库存周转分析卡片：周转率、库存总量、库存积压预警（BA-603），第一行统计卡片随日期范围动态展示周期订单、周期销售额、周期毛利和周期销量
- 数据分析页新增销售+商品分析：经营汇总、趋势图、商品/SKU/颜色/尺码排行、商品详情抽屉
- 订单列表页新增导出按钮，支持筛选条件导出 Excel（BA-204）

### PC 文件中心 BA-1001~BA-1006

- 新增 `/files` 路由和侧边栏菜单（`menu:file` 权限），固定页面标题和优先页面映射。
- 左侧快捷入口：全部文件、未绑定、商品素材、SKU 图片、订单图片、入库凭证、视频、回收站，各入口映射到后端 FilePageDTO 查询参数。
- 集成真实文件夹树 API（`GET /api/file-folders/tree`），支持多层级缩进展示。
- 网格视图：图片卡片缩略图、视频占位、类型角标、绑定标记、多选 checkbox；列表视图：el-table 含 selection 列、预览/文件名/类型/大小/业务/绑定/来源/时间/状态列。
- 筛选栏：keyword 搜索、fileType 下拉、businessType 下拉、网格/列表切换。
- 分页（prev/pager/next）、loading 状态、空状态提示、图片预览弹窗（大图 + 元数据）。
- 上传：隐藏多文件 input，逐个调用 uploadFile(file, 'temp')，loading 态，上传后自动刷新。
- 预览：网格缩略展示使用 `fileVariantUrl(fileId, 'card')`，列表小图使用 `fileVariantUrl(fileId, 'thumb')`；预览弹窗和“打开原文件”继续使用 `filePreviewUrl(fileId)`，两类 URL 都由 `previewToken` 补齐浏览器原生资源请求的认证信息。
- 批量操作：选中后显示工具栏（移动/绑定/删除/取消选择），移动弹窗 radio-group 选文件夹或未归档。
- 删除保护：删除前并行查询 getFileBindings，展示绑定风险详情弹窗，仅未绑定文件可被 batch-delete 删除。
- 商品绑定弹窗：FileBindDialog.vue，remote 搜索商品→选角色 main/gallery/sku_image→sku_image 时显示 SKU 多选→PUT /api/products/{id}/file-bindings。
- 清理面板：FileCleanupPanel.vue，清理说明/保留天数/候选统计刷新/软删除确认/回收站快捷入口。
- 扩展 `blade-admin/src/api/file.ts`：完整 batch 操作/绑定/清理/文件夹创建 API；扩展 `blade-admin/src/api/product.ts`：ProductFileBindingDTO/SkuImageBindingDTO + setProductFileBindings()。
- `npm run build`（Node v22）通过，无 TypeScript 错误。

### 前端图标本地 fallback

- PC 管理端已移除对 Google Material Symbols 字体的强依赖。
- `blade-admin/src/utils/materialIconFallback.ts` 会把现有 `material-symbols-outlined` 图标名转换为本地内联 SVG，避免网络字体加载失败时显示 `dashboard`、`download`、`edit` 等英文。

---

## 快捷索引

| 你想做的事 | 看这个 |
|-----------|--------|
| 了解项目入口与阅读顺序 | [01-README.md](./01-README.md) |
| 了解业务与技术规则 | [02-PRD.md](./02-PRD.md) |
| 查看任务状态 | [03-TASKS.md](./03-TASKS.md) |
| 一眼看清项目进度（自动生成） | [STATUS.md](./STATUS.md)，可视化看板 `outputs/status.html` |
| 查看最近变更 | [05-CHANGELOG.md](./05-CHANGELOG.md) |
| 看项目目录结构 | [reference/PROJECT_STRUCTURE.md](./reference/PROJECT_STRUCTURE.md) |
| 查订单/库存设计 | [06-ORDER_INVENTORY_DESIGN.md](./06-ORDER_INVENTORY_DESIGN.md) |
| 查客户模块优化计划 | [08-CUSTOMER_OPTIMIZATION.md](./08-CUSTOMER_OPTIMIZATION.md) |
| 查图片/附件上传与存储设计 | [09-FILE_STORAGE_DESIGN.md](./09-FILE_STORAGE_DESIGN.md) |
| 查文件中心/数字资产/客户展示页设计 | [12-FILE_CENTER_ASSET_DESIGN.md](./12-FILE_CENTER_ASSET_DESIGN.md) |
| 查 NAS 生产运维发布 | [13-NAS_PRODUCTION_OPS.md](./13-NAS_PRODUCTION_OPS.md) |
| 查 Git 分支、GitHub 同步和上线流程 | [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md) |
| 查外部 AI Agent 对接设计 | [10-AGENT_INTEGRATION_DESIGN.md](./10-AGENT_INTEGRATION_DESIGN.md) |
| 查双 Agent 协作同步协议 | [reference/AGENT_COLLABORATION.md](./reference/AGENT_COLLABORATION.md) |
| 查已知问题和历史坑 | [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md) |
| 排查常见环境问题 | [reference/TROUBLESHOOTING.md](./reference/TROUBLESHOOTING.md) |

---

## 接手建议

如果你是新接手的 AI，推荐阅读顺序：

1. [SESSION_CONTEXT.md](./SESSION_CONTEXT.md)
2. [STATUS.md](./STATUS.md)（自动生成，一眼看清进度）
3. [01-README.md](./01-README.md)
4. [02-PRD.md](./02-PRD.md)
5. [03-TASKS.md](./03-TASKS.md)
6. 开发/合并/上线前补读 [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md)
7. 订单/库存相关开发再补读 [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md)

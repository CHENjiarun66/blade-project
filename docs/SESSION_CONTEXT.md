# 当前会话上下文

> 本文件是项目的快速状态快照，用于新 AI 和新会话快速接手。
> 这里只保留摘要信息；任务明细以 `03-TASKS.md` 为准，变更历史以 `05-CHANGELOG.md` 为准。

---

## 项目基本信息

| 项目 | 值 |
|------|---|
| 项目名称 | BladeProject |
| 启动日期 | 2026-03-21 |
| 当前阶段 | 后端核心模块、PC 管理端主要业务页面、库存并发控制、跨仓总量预留、配货计划、权限基础能力、订单编辑和追加收款均已落地；客户模块国际化升级（国家区号选择器 + 客户详情页 3 Tab）已完成，E2E 测试 12/12 通过；客户模块优化 Phase 4.6 M1~M4 全部完成；看板系统 BA-603 库存统计（周转分析）已完成；订单导出 BA-204 已完成；统一文件上传底座已完成；文件中心/数字资产中心后端 BE-1001~BE-1011 已完成到文件夹、列表、绑定、批量操作、删除保护、未绑定治理、安全清理调度、预览权限和回归测试；PC 文件中心 BA-1001~BA-1006 已完成；客户 iPad Catalog 现货选款页 BE-1020~BE-1023、BA-1020~BA-1024 已完成第一版（/catalog、横竖屏、筛选、SKU 矩阵、全屏大图）；移动端继续开发中 |
| 下一步 | 商品管理 v2（SKU 精细维护、商品图集、SKU 图片、删除引用保护）、订单库存解耦收尾（BE-124、BE-126）、看板完善（仪表盘数据权限）、外部 Agent 只读 Gateway（BE-551~BE-565）、移动端页面继续推进、OCR 识别服务与拍照录单待开发；Catalog 后续可补客户身份、行为采集、选款清单、公开分享/只读专用账号 |

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
- 追加收款功能已实现（创建状态且未付全款可累加 paidAmount，自动更新 paymentStatus）。
- `blade-admin` 已完成订单、库存、商品、客户、系统管理等主要页面。
- 前端菜单权限过滤已完成，系统可按权限展示菜单和路由。
- `packages/types` 已搭建并被移动端集成使用。
- **客户模块国际化已完成**：国家区号选择器（WhatsApp 风格可搜索下拉，约 140 个国家/地区，支持中英文+区号筛选）、客户详情页（3 个独立 Tab：基本信息/订单记录/商品偏好，支持颜色/尺码/品类偏好柱状图）。E2E 测试全部通过（12/12 测试用例）。
- **客户模块优化 Phase 4.6 M1~M4 全部完成**：M1 数据质量（BE-412~414）✅ + M2 用户体验（BE-415~417）✅ + M3 业务功能（BE-418~420）✅ + M4 架构能力（BE-421~423）✅ 已完成
- **库存周转分析已完成**：仪表盘新增库存周转率、库存总量、库存积压预警卡片（BA-603），平均在库天数已移除。
- **仪表盘订单统计口径已调整**：订单统计按 `order_date`（为空回退 `create_time`）+ 已产生收款订单（`paid_amount > 0` 或 `payment_status in (1,2)`）+ 应收净额（`total_amount - refund_amount`，最低 0）统计，并新增毛利和销量统计；卡片按“筛选周期 / 本周 / 库存”三行分组展示，第一行随日期范围动态变化。
- **数据分析页 v1 已完成**：新增 `/analytics` 独立页面，支持经营汇总、趋势、商品/SKU/颜色/尺码排行和商品详情拆解；新增 `menu:analytics` 与 `data:analytics:profit` 权限，毛利/成本/毛利率按权限展示。
- **保持登录 30 天已生效**：登录页 `remember` 会传到后端，勾选时 refresh token 有效期为 30 天并在续期时延续；access token 保持 1 小时，前端会在业务请求发出前发现 10 分钟内过期并主动刷新。
- **订单导出已完成**：订单列表页新增导出按钮，支持筛选条件导出 Excel（BA-204）
- **快速录单商品级批量录入已完成（BA-207）**：选择商品后展示正常状态 SKU 颜色 x 尺码矩阵，批量填写数量并一次性添加到订单明细；第一版不读取、不展示、不校验库存；重复 `skuId` 自动合并数量且不覆盖已改单价/成本价。SOW 见 [2026-06-11-quick-order-product-batch-entry-sow.md](./superpowers/plans/2026-06-11-quick-order-product-batch-entry-sow.md)。
- **个人中心已完成**：个人中心页面（用户信息展示、修改密码）、头部下拉菜单（BA-704）
- **统一文件存储第一版已完成**：新增 `file_storage` 表、统一上传/预览/软删除/绑定接口，本地存储落地；订单图片、PC/移动端入库凭证、商品主图均已改为上传后保存 fileId；浏览器原生 `<img>`/新窗口预览通过 `/api/files/{id}/preview?previewToken=...` 进入统一权限校验，后续可切七牛云/NAS。
- **文件中心/数字资产中心后端底座已完成 Phase 6.6**：新增 [12-FILE_CENTER_ASSET_DESIGN.md](./12-FILE_CENTER_ASSET_DESIGN.md)，明确文件中心不是单纯图片/视频相册，而是通用数字资产中心；BE-1001~BE-1011 已完成到资产表结构、分页/详情、文件夹、多业务绑定、批量操作、有效绑定删除保护、未绑定治理、第一版安全清理调度、商品/SKU 图片绑定、基础视频上传分类、私有预览权限和文件中心回归测试。清理调度默认关闭，按配置 tenant-id 处理单租户，仅软删除/标记元数据，不做真实物理删除。
- **NAS 生产环境已初步部署完成**：生产目录 `/volume2/blade`，前端入口 `http://192.168.1.10:8899/catalog`；容器为 `blade-mysql`、`blade-redis`、`blade-backend`、`blade-web`；NAS 数据库已从本机生产库 `blade_project_prod` 迁移并将主租户 code 调整为 `dwy_jiajiadress`。后续发布/备份/回滚按 [13-NAS_PRODUCTION_OPS.md](./13-NAS_PRODUCTION_OPS.md) 执行。

### 仍在进行或未完成的事项

- `BE-124`：订单相关表结构补充配货/调整字段，任务仍未完成。
- `BE-126`：按配货计划出库方法虽已在代码中实现，但 `TASKS` 仍需结合验收结果收敛状态。
- 仪表盘数据权限尚未实现。
- 外部 Agent 对接需求已锁定为只读 Agent Gateway 第一版，优先做款式趋势、客户跟进/风险、颜色尺码结构、库存建议和周期分析；订单异常、利润解释、WhatsApp 反馈和经营记忆进入后续路线，WhatsApp 先做接入方案验证；后端任务尚未开始。
- 文件中心/数字资产中心后端 BE-1001~BE-1011 已完成；PC `/files` 页面 BA-1001~BA-1006 已完成（路由菜单、虚拟入口文件夹树、网格列表视图筛选分页、上传移动删除、商品SKU绑定弹窗、未绑定清理管理），V36 已补齐 `menu:file` 与文件中心按钮权限；Catalog 聚合接口和 `/catalog` 展示页第一版已完成，V37 已补齐 `menu:catalog` 与 `data:catalog:view` 权限。
- 图片派生图/缩略图性能优化已纳入待开发：`BE-1012`、`BA-1007`、`BA-1028`。目标是图片上传后生成 `thumb`/`card` 派生图，商品列表、订单图片墙、文件中心网格、Catalog 卡片优先加载派生图，点开大图/下载仍加载原图；本轮只完成文档规划，尚未开始开发。
- 商品管理 v2 已纳入待开发：`BE-1013` 商品素材查询 API、`BE-1014` 删除引用保护验收、`BA-407~BA-410` 商品编辑页 v2/SKU 明细/商品素材/删除禁用交互；ROM/SOW 见 [2026-06-14-product-management-v2-rom-sow.md](./superpowers/plans/2026-06-14-product-management-v2-rom-sow.md)。
- 文件预览权限补强已完成：PRIVATE 文件仍由后端校验登录、租户、业务权限；前端所有 fileId 预览必须走 `filePreviewUrl(fileId)`，不要手写 `/api/files/{id}/preview`，否则浏览器 `<img>` 不会带认证信息。
- 移动端页面开发仍在继续。
- OCR 拍照录单等任务仍未完成。

---

## 当前阻塞与风险

| 问题 | 优先级 | 状态 | 说明 |
|------|--------|------|------|
| 仪表盘数据权限 | P2 | 🔴 未实现 | 后端统计接口尚未按权限过滤数据 |
| 订单表结构收尾 | P1 | ⏳ 进行中 | `BE-124` 仍需与当前配货实现完全对齐 |
| 文档状态漂移 | P1 | ⏳ 进行中 | 已发现多份入口文档与代码和任务状态不一致 |
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
- 订单详情页新增"追加收款"按钮（创建状态且未付全款时显示），弹窗显示当前已付/待付余额，输入本次收款金额后累加到 paidAmount，paymentStatus 自动更新。
- 后端 `GlobalExceptionHandler` 补充 `RuntimeException` 专项处理，业务校验错误不再返回 500。

### 跨仓总量预留

- `inventory` 表已增加 `global_reserved_qty`。
- 已实现 `globalReserve`、`globalRelease`、`getGlobalAvailableQty`。
- 订单确认收款改为走跨仓总量预留，不再绑定单仓库预占。

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
- 预览：图片缩略图和“打开原文件”统一使用 `filePreviewUrl(fileId)`，由 `previewToken` 补齐浏览器原生资源请求的认证信息。
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
| 查看最近变更 | [05-CHANGELOG.md](./05-CHANGELOG.md) |
| 看项目目录结构 | [reference/PROJECT_STRUCTURE.md](./reference/PROJECT_STRUCTURE.md) |
| 查订单/库存设计 | [06-ORDER_INVENTORY_DESIGN.md](./06-ORDER_INVENTORY_DESIGN.md) |
| 查客户模块优化计划 | [08-CUSTOMER_OPTIMIZATION.md](./08-CUSTOMER_OPTIMIZATION.md) |
| 查图片/附件上传与存储设计 | [09-FILE_STORAGE_DESIGN.md](./09-FILE_STORAGE_DESIGN.md) |
| 查文件中心/数字资产/客户展示页设计 | [12-FILE_CENTER_ASSET_DESIGN.md](./12-FILE_CENTER_ASSET_DESIGN.md) |
| 查 NAS 生产运维发布 | [13-NAS_PRODUCTION_OPS.md](./13-NAS_PRODUCTION_OPS.md) |
| 查 Git 分支、GitHub 同步和上线流程 | [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md) |
| 查外部 AI Agent 对接设计 | [10-AGENT_INTEGRATION_DESIGN.md](./10-AGENT_INTEGRATION_DESIGN.md) |
| 查已知问题和历史坑 | [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md) |
| 排查常见环境问题 | [reference/TROUBLESHOOTING.md](./reference/TROUBLESHOOTING.md) |

---

## 接手建议

如果你是新接手的 AI，推荐阅读顺序：

1. [SESSION_CONTEXT.md](./SESSION_CONTEXT.md)
2. [01-README.md](./01-README.md)
3. [02-PRD.md](./02-PRD.md)
4. [03-TASKS.md](./03-TASKS.md)
5. 开发/合并/上线前补读 [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md)
6. 订单/库存相关开发再补读 [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md)

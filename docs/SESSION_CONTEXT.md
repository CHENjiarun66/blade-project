# 当前会话上下文

> 本文件是项目的快速状态快照，用于新 AI 和新会话快速接手。
> 这里只保留摘要信息；任务明细以 `03-TASKS.md` 为准，变更历史以 `05-CHANGELOG.md` 为准。

---

## 2026-09-03 最新基线（优先于下方历史快照）

- 订单重构 `BE-1040`～`BE-1051` 已完成三轮整改并获 `CODEX_APPROVED_FOR_RELEASE_PREPARATION`；仍未执行生产副本迁移、NAS 部署或生产数据变更。
- 已并入旧 ERP 只读审计。后续优先顺序是商品/SKU 库存边界与价格隐私 → 生产副本迁移门禁 → 旧 ERP 期初库存/历史档案 → 无价采购收货；完整资金账户、AR/AP、跨单核销和采购付款暂缓。
- 当前订单财务实现是可追溯的订单专用子账，不等于完整财务模块。后续使用 `ARCH-FIN-002` 设计桥接和加法迁移，不删除、不双写、不把现有订单任务退回 TODO。
- SKU 库存契约：`NORMAL` 和当前真正无规格的 `DEFAULT` 可产生库存事实；`PLACEHOLDER` 与已有真实规格后的历史 `DEFAULT` 只保留订单/分析引用，必须拆分后才能配货、预留或出库。
- 旧 ERP 迁移与 Blade V42 订单迁移是两个独立工作包：前者采用真实 SKU×仓期初快照与历史只读档案，后者由现有 `OrderLegacyMigrator` 处理，禁止混用。

## 2026-08-30 项目状态核对结论

- 订单状态、收款与履约重构方案已确认，详见 [14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md](./14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md)。本次只完成设计和影响面扫描，代码、数据库和生产数据尚未修改。
- 订单金额与统计口径已补充，详见 [15-ORDER_FINANCE_ANALYTICS_DESIGN.md](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md)。客户实收、现金退款、销售退回和短款核销分开记录；订单、现金、结清和库存指标使用各自业务时间。
- 目标模型把草稿状态、收款状态、履约方式和履约状态拆开。正式订单结清后选择 `STOCK_LINKED` 进入库存履约，或选择 `RECORD_ONLY` 直接完成且不影响库存。
- 旧 `sale_order.status` 不原地重解释。实施时新增字符串履约字段、收款流水、状态日志和并发版本，并保留一个发布周期的兼容读取。
- 旧生产备份中的 81 张订单全部为 `status=0`，其中 74 张已结清、6 张部分收款、1 张未收款，且没有配货计划。正式迁移必须按金额和履约证据分流，不能按旧状态数字批量映射。
- 最近两条已完成的本地主线是 WhatsApp 只读归档/客户工作区，以及纸单 Agent 批量草稿/SPU 占位 SKU。
- 纸单草稿的 V48-V50、候选匹配、快速录单式工作台、人工确认和分析隔离已通过本地测试，但尚未部署 NAS 生产，也未完成 30 张真实纸单验收。
- 占位 SKU 的创建、匹配、展示和分析已完成；占位数量拆到真实 SKU、拆分审计、配货与出库保护尚未实现。正式履约前必须补齐这组联动能力。
- 旧 OCR 任务已转由本机订单识别 Agent 承担。BladeProject 不再重复建设图片识别和表格解析，只接收原图与结构化结果。
- WhatsApp 已完成本地真实数据验证，但 Mac → NAS 生产同步、生产凭证和回滚验收仍待执行。
- 任务状态现在区分功能开发、本地验证、生产部署和真实业务验收，详见 [03-TASKS.md](./03-TASKS.md)。
- 订单大重构的联动系统、SOW、Agent 文件边界、Git 分支和 NAS 发布门禁已整理到 [2026-08-30-order-lifecycle-finance-refactor-rom-sow.md](./superpowers/plans/2026-08-30-order-lifecycle-finance-refactor-rom-sow.md)。
- 实施责任已锁定：`ORDER-SOW-0` 已在 `d800ec4` 完成并由 Codex 放行。Z Code 按[长任务文档](./superpowers/plans/2026-08-30-order-refactor-zcode-long-run-task.md)连续完成原 SOW-1～SOW-7、自测和分系列提交，中间不再逐阶段等待；全部完成后 Codex 一次性审核完整代码 Diff，用户仍负责批准生产发布。
- 正式订单采用“两旧两新”：保留旧整数 `status`、`payment_status`；新增字符串 `fulfillment_status`、`collection_status`。`fulfillment_mode` 是履约选择，草稿状态仍在独立草稿表，不新增重复的 `order_lifecycle_status`。
- 本轮整理前，GitHub `codex/phase2-order-drafts` 与本地代码基线同为 `38c969b`，相对 `master` 前进 27 个提交并同时包含 V43-V47 WhatsApp 与 V48-V50 Phase 2；本轮新增设计文档随交接基线提交。已推送功能分支不代表已合入主干或已部署生产。
- 2026-08-30 只读复核 NAS：四个生产容器均运行，生产 Flyway 为 V42，V43-V50 尚未发布。本次核查未修改生产数据或容器。

## 2026-08-30 订单生命周期、财务与统计大重构（长任务系列 A~G 完成，待终审）

- 在 `feature/order-lifecycle-finance-refactor` 分支完成订单大重构连续实施（基线 `1594a8f` → 最终 tip 见交付报告）：V51/V52 加法迁移、统一动作服务 11 动作、统一财务快照与唯一兼容适配器、占位 SKU 拆分与履约保护、`/api/inventory/out-by-plan` 410 收口、PC/移动端/共享类型/导出切换新契约、`OrderFactsService` 统一统计口径并切换全部消费者、客户偏好缓存按订单/财务动作失效、离线迁移工具（dry-run 默认 + 幂等重放）。
- 验证：隔离库（Docker `blade-mysql-test`，端口 3307）空库 Flyway V1→V52 连续升级成功；后端全量测试通过；PC/移动端/类型包构建通过；Playwright 结果见交付报告。迁移工具以合成数据预演，**V42 生产副本预演未执行**（本机无备份，留 Codex/发布阶段）。
- 当时状态曾为 `WAITING_CODEX_FINAL_REVIEW`；此门禁已在后续三轮整改后关闭。BE-1048（旧字段下线）与 BE-1052（V42 迁移 + NAS 发布）仍不在已完成范围。

## 2026-08-27 Phase 2 SPU/SKU 颗粒度补充

- V49/V50/V56 为 `product_sku` 增加并校正 `NORMAL / DEFAULT / PLACEHOLDER`；任何显式规格商品（包括只有一个具体组合）自动维护一个“未指定颜色 / 未指定尺码”占位 SKU，无规格商品使用正常 `DEFAULT` SKU。
- 纸单 Agent 只识别款号时优先匹配占位 SKU；识别到颜色或尺码时排除占位；草稿工作台明确显示“整款（未指定颜色/尺码）”。
- 占位销量计入款号总量，但从真实颜色尺码排行中分离；Agent 分析返回未指定汇总、规格覆盖率和数据质量等级。
- 占位 SKU 不进入对外商品目录和库存可用性判断；生产尚未发布 V49/V50。

## 项目基本信息

| 项目 | 值 |
|------|---|
| 项目名称 | BladeProject |
| 启动日期 | 2026-03-21 |
| 当前阶段 | 后端核心模块、PC 管理端主要业务页面、库存并发控制、跨仓总量预留、配货计划、权限基础能力、订单编辑和追加收款均已落地；统一文件上传和文件中心底座已完成；WhatsApp 本地归档与客户工作区已完成；纸单识别 Agent 的 SKU 候选、批量订单草稿、占位 SKU、草稿工作台和人工确认正式订单 MVP 已于 2026-08-27 完成本地验证；移动端继续开发中 |
| 下一步 | 订单 A～G 已终审通过；先完成商品/SKU 与价格隐私增量回归，再准备 BE-1052 生产副本预演。未经用户生产批准，不部署 NAS。 |

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
| 订单状态、收款与履约重构 | [14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md](./14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md) |
| 订单金额、结清与经营统计 | [15-ORDER_FINANCE_ANALYTICS_DESIGN.md](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md) |
| 订单大重构实施分工、Git 与 NAS 门禁 | [2026-08-30-order-lifecycle-finance-refactor-rom-sow.md](./superpowers/plans/2026-08-30-order-lifecycle-finance-refactor-rom-sow.md) |
| 实现 Agent 任务与 Codex 审核门禁 | [2026-08-30-order-refactor-agent-execution-board.md](./superpowers/plans/2026-08-30-order-refactor-agent-execution-board.md) |
| 当前任务进度 | [03-TASKS.md](./03-TASKS.md) |
| 最近变更历史 | [05-CHANGELOG.md](./05-CHANGELOG.md) |
| 分支开发与生产发布 | [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md) |
| 快速接手摘要 | 本文档 |

---

## 当前摘要

### WhatsApp 本地归档 v1（2026-08-24）

- `BE-564` 方案验证已完成，正式实施契约为 [2026-08-24-whatsapp-local-archive-rom-sow.md](./superpowers/plans/2026-08-24-whatsapp-local-archive-rom-sow.md)。
- 已锁定“Mac 只读源 → Git 外加密快照 → 独立 Collector → Blade 内部导入 API → `wa_*` 事实表/文件中心 → 只读 Agent Gateway”的链路。
- v1 保留原 WhatsApp Business 号码，只接 1:1 联系人、会话、文本和已下载媒体；不自动回复、不自动创建 CRM 客户、不让 Agent 直接访问数据库或执行营销。
- `BE-566～BE-576`、`BA-1101～BA-1102` 已完成：V43～V46 建立事实层、采集链路、缺失媒体诊断、混合 Agent 分析队列、领取时上下文快照和客户跟进工作台。
- `BA-1104` 已修复 PC 权限缓存过旧导致“WhatsApp归档”入口不显示的问题；新页面会话会自动刷新一次服务端权限。
- `BE-579`、`BA-1105` 已把缺失媒体改为按聊天号码聚合：首页一个号码一行并显示分类计数，详情抽屉再分页查看该客户全部缺失媒体、打开聊天和重扫。
- `BE-580`、`BA-1106` 已修复 WhatsApp LID 被误作手机号：聚合、展示和打开聊天均优先使用 `wa_contact.phone_normalized`；本机 355 个 LID 会话全部有真实号码映射。
- `BE-578`、`BE-581`、`BE-582`、`BA-1107` 已完成双范围重扫：顶部“扫描整个账号”保留全量能力，客户详情“仅扫描此客户”按真实号码覆盖其 phone JID/LID 会话；服务端只要求补传尚未 IMPORTED 的媒体，定向批次不会恢复其他客户的问题。
- `BA-1103` 已完成 ERP 只读聊天归档：WhatsApp 归档默认进入双栏聊天视图，按真实号码聚合客户，支持文字、图片、视频、音频、贴纸、文档及明确的缺失媒体占位；继续使用 JWT/租户权限且没有发送入口。
- `BA-1108` 已修复 CRM 国际号码绑定：候选匹配会组合客户国家区号与本地号码，并在 WhatsApp 归档加载时自动重算；真实样本 `+243 + 835453734` 已与 WhatsApp `243835453734` 生成待确认候选。
- `BA-1109` 已让绑定结果可见：页面分开展示待确认/已绑定，说明绑定与 ERP/Agent 分析的关系，并可跳客户档案或聊天；聊天窗口修复网格高度约束，打开默认在最新消息，向上滚动每次加载 50 条更早消息且保持阅读位置。真实样本 2,595 条聊天已验证 50→100 条连续加载，`Sbk(刚果金) Fashion+243` 已在已绑定列表显示。
- `BE-583`、`BA-1110` 已把 WhatsApp 正式嵌入 ERP 客户详情：客户档案新增 WhatsApp 页签，直接处理待确认绑定并显示只读聊天、缺失媒体、同步状态和“仅扫描此客户”；真实本地 API 样本 `Sbk(刚果金) Fashion+243` 返回 212 条已归档消息。Mac → NAS 生产接入已记录在 [2026-08-26-whatsapp-nas-production-integration-plan.md](./superpowers/plans/2026-08-26-whatsapp-nas-production-integration-plan.md)，仍明确后置且尚未部署生产。
- Mac Collector 已升级为 v0.2，支持 `configure`、`sync` 和 `watch`：从一致性快照生成结构化 spool，分块上传 ERP；后台可领取 ERP 发起的重扫任务。
- 合成端到端验证：首次导入 5 条逻辑消息、4 条媒体元数据和 1 个文件；重复导入总数保持 5/4/1；补载旧图片后保持 5 条消息、4 条媒体并新增第 2 个文件，账号问题状态变为 2 个待处理、1 个已恢复。
- 混合 Agent 链路已实现：ERP 以独立 scoped Worker Key 提供最近 90 天/最多 200 条的脱敏上下文和订单商品汇总；NAS Worker 可接本地或 OpenAI-compatible 云端模型，结果必须携带有效消息证据，用户只在 ERP 采纳、忽略或完成。
- 自动化验证通过：Flyway 已到 V47，后端全量测试、前端生产构建和 WhatsApp Playwright 通过，Collector/Worker 15 项测试通过；覆盖脱敏、幂等、非法证据、失败重试、跨租户队列隔离、目标 phone/LID 合并与定向问题恢复隔离。
- 2026-08-25 已完成本地真实数据部署验证：ERP 后端 `127.0.0.1:18080`、Admin `127.0.0.1:5777`、Mac Assistant 自动同步；成功批次导入 1,527 联系人、989 会话、32,050 消息、17,132 媒体元数据、2,140 已下载媒体和 14,992 待恢复项。真实内容只进入本机测试库/文件中心，未进入 Git、NAS、生产或模型。
- 真实定向扫描验收：目标客户仅处理 1 条消息（全量基准 32,050 条），全局 14,586 条待恢复记录保持不变；目标客户媒体未下载时其 1 条问题仍正确保持待恢复。只读聊天以 `+234 803 391 2244` 验收，返回 83 条消息，图片和视频预览均为 200，视频 Range 为 206；生产/NAS 尚未部署，上线时仍由运维一次配置，业务用户无需终端操作。

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
- 外部 Agent Gateway 默认只读；安全边界、认证审计、款式趋势和颜色尺码结构已完成。2026-08-27 新增唯一已批准的窄范围写入：本机纸单识别 Agent 可用 `agent:orders:write` 批量创建订单草稿，但不能确认正式订单、调整库存或确认收款。客户跟进/风险、库存建议、周期分析、统一搜索和限流验证尚未完成。
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
| 查订单状态、收款和历史迁移方案 | [14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md](./14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md) |
| 查订单金额、结清和统计口径 | [15-ORDER_FINANCE_ANALYTICS_DESIGN.md](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md) |
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

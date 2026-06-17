# 变更记录

> 所有需求变更、架构变更、重大决策变更必须记录在此。
> 新 AI 阅读本文档可以快速了解项目的演变过程。
> 格式：日期 + 变更内容 + 原因 + 影响范围

---

## 2026-06-17 变更记录

### [运维] - 修正 NAS 生产发布 HTTPS 验证口径

**变更内容**：
- 将 `deploy/nas/deploy_app_from_local.sh` 发布后 `/catalog` 验证从 `http://127.0.0.1:8899/catalog` 修正为 `https://127.0.0.1:8899/catalog` 并使用 `curl -k` 兼容 NAS 自签证书。
- 同步更新 `docs/13-NAS_PRODUCTION_OPS.md` 和 `deploy/nas/README.md` 中的生产访问入口与验证命令。

**变更原因**：
- 生产 `blade-web` 的 compose 端口映射为 `8899 -> 443`，使用 HTTP 验证会返回 `400 Bad Request`，导致发布脚本误判失败。

**影响范围**：
- `deploy/nas/deploy_app_from_local.sh`
- `docs/13-NAS_PRODUCTION_OPS.md`
- `deploy/nas/README.md`

**验证结果**：
- NAS 上 `curl -k -fsSI https://127.0.0.1:8899/catalog` 返回 `200 OK`。

**执行人**：AI

---

### [文档] - 补充带数据库变更的推送与发布规范

**变更内容**：
- 在 `docs/reference/GIT_BRANCH_WORKFLOW.md` 新增带数据库变更的 push 规则：必须通过 Flyway migration 提交、版本号不可复用、已执行 migration 禁止修改、发布前验证和备份。
- 在 `docs/13-NAS_PRODUCTION_OPS.md` 补充日常发布中的 Flyway 自动迁移流程，明确应用可回滚但数据库 migration 默认不自动回滚。
- 强化 NAS 发布门禁：包含 Flyway migration 的版本必须列出数据库影响范围并在本地或测试库验证通过。

**变更原因**：
- 后续使用 GitHub Actions 构建镜像、NAS pull 镜像发布时，数据库结构变更仍需随版本可追踪发布，避免手工改表导致环境漂移。

**影响范围**：
- `docs/reference/GIT_BRANCH_WORKFLOW.md`
- `docs/13-NAS_PRODUCTION_OPS.md`
- `docs/05-CHANGELOG.md`

**验证结果**：
- 文档规则更新，无代码构建。

**执行人**：AI

---

### [Bug修复] - 已付款订单允许维护订单图片

**变更内容**：
- 修复订单列表编辑弹窗保存已付款订单时报“已收款或配货订单不允许直接修改金额和明细”的问题。
- 前端编辑弹窗按订单状态分级：创建状态可编辑运费等金额字段；已付款/待配货状态锁定运费和金额结构，但允许维护客户基础信息、配送、备注和订单图片；已发货后仅允许维护备注和订单图片。
- 后端 `OrderServiceImpl.update()` 调整金额变更判断：只有运费值实际变化或提交明细替换时才视为金额结构修改；前端带回未变化的运费值不再误触发拦截。
- 修复订单号生成器在 Redis 计数器重置但数据库已有当天订单时可能生成重复订单号的问题；生成前按租户和日期前缀对齐数据库最大订单序号。
- PRD 补充订单编辑权限规则，明确订单图片属于凭证资料，已付款后仍可追加、移除和重新绑定。

**变更原因**：
- 已付款订单应锁定数量、单价、成本、运费等金额结构，避免账目被误改；但纸质单据图片、备注等资料不影响金额，应允许后续补充维护。

**影响范围**：
- `blade-backend/src/main/java/com/blade/order/service/impl/OrderServiceImpl.java`
- `blade-backend/src/main/java/com/blade/order/mapper/OrderMapper.java`
- `blade-admin/src/views/orders/index.vue`
- `docs/02-PRD.md`
- `docs/03-TASKS.md`

**验证结果**：
- `cd blade-admin && npm run build` 通过。
- `cd blade-backend && mvn -DskipTests compile` 通过。
- `cd blade-backend && mvn -Dtest=OrderControllerTest#testCreateOrderWithMultipleItems test` 通过。
- `cd blade-backend && mvn test` 通过：244 个测试，0 失败，0 错误。

**执行人**：AI

---

### [需求调整] - 抹零/短款结清收款状态口径

**变更内容**：
- 明确 `payment_status=2` 的业务文案从“已付全款”调整为“已结清”，兼容全款支付和少量短款确认不再追收的场景。
- 规划 `sale_order` 新增 `write_off_amount`、`write_off_reason`，用于记录抹零/短款结清金额和原因。
- 收款状态按应收净额判断：`receivable_net_amount = max(total_amount - refund_amount - write_off_amount, 0)`；`paid_amount >= receivable_net_amount` 即已结清。
- 订单尾款按 `max(total_amount - refund_amount - write_off_amount - paid_amount, 0)` 计算。
- 销售额和毛利统计需要扣减 `write_off_amount`，避免少收金额继续计入经营收入和利润。
- 后续追加收款弹窗需要支持“标记结清”，自动将当前尾款写入抹零/短款金额。

**变更原因**：
- 批发收款中常见客户少付 1-10 元但业务确认不再追收的情况，不能长期显示为“部分收款/定金”，否则欠款筛选和经营判断会失真。

**影响范围**：
- `docs/02-PRD.md`
- `docs/03-TASKS.md`
- `docs/06-ORDER_INVENTORY_DESIGN.md`
- `docs/architecture/DATABASE.md`
- `docs/architecture/order-inventory-soft-coupling-flow.drawio`

**验证结果**：
- 文档规划变更，未开始代码开发。

**执行人**：AI

---

### [架构调整] - 订单库存软解耦生产口径

**变更内容**：
- 确认订单系统与库存系统第一版采用“软连接”：订单创建、快速录单、确认收款、追加收款均不得因库存不足、未建库存记录或仓库未配置失败。
- 收款动作只更新 `paid_amount`、`payment_status`、`pay_time` 和订单待配货状态，不再作为库存硬预留节点。
- 库存只在配货/发货阶段作为提示、复核和实际扣减依据；发货时按实际发货 SKU、仓库、数量扣减库存。
- 明确 `status` 表达发货/履约进度，`payment_status` 表达收款状态；追加收款不自动改变发货状态，配货/发货不自动改变收款金额。
- `inventory_global_reserve` 和 `global_reserved_qty` 暂不删除，作为历史兼容和后续软预留扩展点保留。
- 新增 drawio 流程图：`docs/architecture/order-inventory-soft-coupling-flow.drawio`。

**变更原因**：
- 生产录单场景需要优先保证纸质订单录入和收款不中断；库存模块尚未完全成熟，硬预留会导致正常收款和录单流程被库存数据质量阻断。

**影响范围**：
- `docs/02-PRD.md`
- `docs/03-TASKS.md`
- `docs/06-ORDER_INVENTORY_DESIGN.md`
- `docs/architecture/order-inventory-soft-coupling-flow.drawio`

**验证结果**：
- 文档规划变更，未开始代码开发。

**执行人**：AI

---

## 2026-06-16 变更记录

### [运维] - NAS 发布连接增加 WireGuard 备用地址

**变更内容**：
- NAS 生产运维手册新增连接规则：默认优先连接局域网地址 `192.168.1.10`，本地环境无法访问 `192.168.1.10:22` 时，使用 WireGuard 地址 `10.13.13.1` 连接 NAS。
- `deploy/nas/deploy_app_from_local.sh`、`deploy/nas/backup_db.sh`、`deploy/nas/check_platform.sh`、`deploy/nas/deploy_from_local.sh` 增加自动 SSH fallback：主地址不可达时自动尝试 `10.13.13.1`。
- 如需强制指定地址，可通过 `NAS_HOST=<host> NAS_HOST_FIXED=1` 禁用自动 fallback。

**变更原因**：
- 当前本地网络环境可能无法直接访问 NAS 局域网 SSH 端口，但 WireGuard 通道可用；需要把该规则固化到规范和脚本，避免后续发布时重复人工提醒。

**影响范围**：
- `docs/13-NAS_PRODUCTION_OPS.md`
- `deploy/nas/deploy_app_from_local.sh`
- `deploy/nas/backup_db.sh`
- `deploy/nas/check_platform.sh`
- `deploy/nas/deploy_from_local.sh`

**验证结果**：
- `bash -n deploy/nas/deploy_app_from_local.sh deploy/nas/backup_db.sh deploy/nas/check_platform.sh deploy/nas/deploy_from_local.sh` 通过。
- `ssh -o BatchMode=yes -o ConnectTimeout=8 admin008@10.13.13.1 true` 通过。

**执行人**：AI

---

### [规划] - 图片派生图架构脚手架边界

**变更内容**：
- 在文件中心派生图设计中补充架构脚手架要求：预留统一派生图服务、图片生成器、存储 Provider、派生图类型常量/枚举、状态字段和历史补生成入口。
- 明确第一版仍只做本地存储下的 `thumb/card` 派生图和统一访问接口，不提前接真实 CDN、七牛云、NAS、多格式自适应或复杂队列。
- 明确脚手架目标是让后续接入异步生成、失败重试、NAS/七牛云/CDN、视频封面或 WebP/AVIF 时，不需要改业务表和商品/订单/Catalog 的图片调用链。
- 同步更新 `BE-1012` 任务描述，要求开发时先把边界接口搭好，避免缩略图逻辑散落到 Controller 或业务页面。

**变更原因**：
- 当前系统规模还不大，先补架构扩展点成本较低；如果后续图片访问量变大、存储迁移到 NAS/对象存储/CDN，再补抽象会牵动更多业务模块。

**影响范围**：
- `docs/02-PRD.md`
- `docs/03-TASKS.md`
- `docs/12-FILE_CENTER_ASSET_DESIGN.md`

**验证结果**：
- 文档规划变更，未开始代码开发。

**执行人**：AI

---

### [修复] - 订单录入图片上传后不回显

**变更内容**：
- 修复 PC 快速录单页订单图片上传成功后缩略图破图、不正常回显的问题。
- 同步修复旧新建订单页的同类图片回显逻辑。
- 上传成功后前端不再直接使用上传响应里的 `url`，统一使用 `filePreviewUrl(fileId)` 生成带 `previewToken` 的预览地址。
- 订单提交仍保存 fileId 数组，数据结构不变。

**变更原因**：
- 当前文件预览权限已收口到 `/api/files/{id}/preview?previewToken=...`；浏览器原生 `<img>` 直接访问普通 `url` 不会携带认证信息，导致上传成功但缩略图无法加载。

**影响范围**：
- `blade-admin/src/views/orders/quick.vue`
- `blade-admin/src/views/orders/new.vue`

**验证结果**：
- `cd blade-admin && npm run build` 通过。

**执行人**：AI

---

### [优化] - 订单列表与详情页订单图片预览

**变更内容**：
- 订单列表图片列改为显示订单图片的第一张缩略图，不再只显示图片图标。
- 鼠标悬停在订单列表缩略图上时显示放大预览。
- 点击订单列表缩略图时打开该订单图片大图预览。
- 订单详情页订单图片支持点击打开大图预览，并从当前点击图片开始查看。

**变更原因**：
- 订单列表需要快速识别纸质单图片；订单详情页需要查看大图核对纸单内容。

**影响范围**：
- `blade-admin/src/views/orders/index.vue`
- `blade-admin/src/views/orders/detail.vue`

**验证结果**：
- `cd blade-admin && npm run build` 通过。

**执行人**：AI

---

### [优化] - 快速录单商品批量选择图片辅助

**变更内容**：
- 快速录单「按商品批量添加」区域新增商品图片预览位。
- 商品搜索下拉列表中鼠标悬停到商品选项时，页面上方浮出该商品主图大图，便于看清款式并确认款号；大图不参与页面布局，不挤压录单表单和 SKU 矩阵。
- SKU 颜色 x 尺码矩阵新增图片列，优先展示该颜色行对应 SKU 图片；没有 SKU 图片时回退商品主图。
- 鼠标悬停 SKU 图片时显示放大预览。
- 快速录单底部 SKU 明细列表新增图片列，批量添加和单行选择 SKU 后都会带出 SKU 图片或商品主图，鼠标悬停可浮层放大查看。

**变更原因**：
- 生产录单时同款不同花色/图片容易混淆，需要在选择商品和批量录入 SKU 数量时直接看到图片，减少误选。

**影响范围**：
- `blade-admin/src/views/orders/quick.vue`

**验证结果**：
- `cd blade-admin && npm run build` 通过。

**执行人**：AI

---

## 2026-06-15 变更记录

### [规划] - 后端测试基线修复任务

**变更内容**：
- 新增独立修复分支：`fix/backend-test-baseline`。
- 在 `docs/03-TASKS.md` 新增 Phase 6.8「后端测试基线修复」。
- 新增任务：
  - `BE-1030` 后端全量测试失败归因与基线记录
  - `BE-1031` Catalog/Product Controller 测试认证基线修复
  - `BE-1032` OrderControllerTest 状态码与订单状态口径修复
  - `BE-1033` 后端全量测试收口

**变更原因**：
- 商品管理 v2 合入 `develop` 后，前端构建和商品/文件相关定向测试通过，但 `blade-backend mvn test` 仍有 40 个失败。
- 复核 `master` 基线后确认同样存在这 40 个失败，属于历史测试口径未同步，不是本次商品管理 v2 新增回归。
- 需要单独修复测试基线，避免后续每次集成时无法判断真实回归。

**当前失败范围**：
- `CatalogControllerTest`：15 个失败，登录请求缺少 `tenantCode`，返回“租户编码不能为空”，token 为 null。
- `ProductControllerTest`：14 个失败，同样因旧登录测试夹具缺少 `tenantCode`，后续接口使用 `Bearer null` 返回 403。
- `OrderControllerTest`：11 个失败，主要是测试断言仍按旧业务错误码 `500`、旧取消状态值和旧状态流转口径。

**验收标准**：
- `cd blade-backend && mvn test` 通过。
- 不为通过测试而放宽生产认证、权限或订单状态业务规则。
- 修复后保留全量测试结果和关键定向测试结果。

**影响范围**：
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`
- 后续预计修改 `blade-backend/src/test/java/com/blade/catalog/CatalogControllerTest.java`
- 后续预计修改 `blade-backend/src/test/java/com/blade/product/ProductControllerTest.java`
- 后续预计修改 `blade-backend/src/test/java/com/blade/order/OrderControllerTest.java`

**执行人**：AI

---

### [修复] - 后端测试基线恢复为全量通过

**变更内容**：
- 修复 `CatalogControllerTest`、旧 `ProductControllerTest` 登录夹具：补齐 `tenantCode=test_tenant`，使用当前测试数据密码，避免 token 为 null 后续接口统一 403。
- 修复 `ProductControllerTest` 重复执行污染：测试商品编码改为运行时唯一值，重复跑测试不再因固定编码冲突失败。
- 修复 `OrderControllerTest` 旧状态机断言：业务错误对齐 `400`，订单状态对齐当前 0-8 状态值，发货流程补齐创建配货计划和确认调整步骤。
- 修复全量测试暴露的 SQL 字段映射问题：为 `Product`、`ProductSku`、`ProductColor`、`ProductSize`、`ProductCategory`、商品颜色/尺码关联、`FileStorage`、`FileBusinessBind` 等实体补齐显式 `@TableField`，避免 MyBatis-Plus 生成 `productCode`、`createTime`、`businessType`、`businessId`、`productId` 等错误列名。

**变更原因**：
- `master/develop` 既有后端测试基线长期红灯，导致后续集成无法区分真实回归和历史测试口径问题。
- 全量测试进一步暴露部分实体依赖默认驼峰映射不稳定，影响商品/文件相关查询的可靠性。

**验证结果**：
- `cd blade-backend && mvn test -Dtest=CatalogControllerTest,ProductControllerTest,OrderControllerTest -DfailIfNoTests=false` 通过：55/55。
- `cd blade-backend && mvn test -Dtest=ProductControllerTest,CatalogControllerTest,FileControllerTest,FileBindingControllerTest,ProductFileBindingServiceTest,ProductFileBindingControllerTest -DfailIfNoTests=false` 通过：73/73。
- `cd blade-backend && mvn test` 通过：Tests run 244, Failures 0, Errors 0, Skipped 0。

**影响范围**：
- `blade-backend/src/test/java/com/blade/catalog/CatalogControllerTest.java`
- `blade-backend/src/test/java/com/blade/product/ProductControllerTest.java`
- `blade-backend/src/test/java/com/blade/order/OrderControllerTest.java`
- `blade-backend/src/main/java/com/blade/product/entity/*`
- `blade-backend/src/main/java/com/blade/file/entity/*`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`

**执行人**：AI

---

## 2026-06-14 变更记录

### [规划] - 商品管理 v2：SKU 精细维护与商品素材管理

**变更内容**：
- 在 PRD 商品模块新增“商品管理 v2”章节，明确商品编辑页需要覆盖基础信息、颜色尺码、SKU 明细和商品素材四个区域。
- 明确 SKU 明细维护规则：SKU 可逐行维护售价、成本价、条码、状态和 SKU 图片；颜色/尺码变化时不得破坏历史订单和库存引用。
- 明确商品素材统一走文件中心绑定关系：商品主图、商品图集、SKU 图片均使用 `file_business_bind`，不新增商品图集或 SKU 图片业务字段。
- 明确图片性能规则：商品列表、商品编辑缩略图、文件中心、订单图片墙和 Catalog 后续优先加载 thumb/card 派生图。
- 明确删除与历史引用规则：商品、SKU、颜色、尺码、分类删除前必须考虑订单、库存、文件绑定引用，存在有效引用时默认改为禁用。
- 新增后端任务 `BE-1013` 商品素材查询 API、`BE-1014` 商品/SKU 删除引用保护验收。
- 新增前端任务 `BA-407` 商品编辑页 v2 信息架构、`BA-408` SKU 明细精细维护、`BA-409` 商品素材管理内聚到商品页、`BA-410` 商品删除/禁用交互优化。
- 新增 Claude Code ROM/SOW 文档：`docs/superpowers/plans/2026-06-14-product-management-v2-rom-sow.md`。

**变更原因**：
- 当前商品基础 CRUD 已完成，但生产使用中商品资料维护还缺 SKU 单独价格/成本/状态/条码、商品图集、SKU 图片和删除引用保护。
- 商品图片同时服务 PC 商品页、快速录单、文件中心和 iPad Catalog，必须先规划统一素材入口和缩略图性能方案，避免后续重复实现。

**影响范围**：
- `docs/02-PRD.md`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`
- `docs/superpowers/plans/2026-06-14-product-management-v2-rom-sow.md`

**边界说明**：
- 本次只做 PRD 和 ROM/SOW 规划，不开始代码开发。
- 供应商管理继续后置；商品 v2 第一版只保留 supplierId 兼容，不做供应商 CRUD。

**执行人**：AI

---

### [实现] - BE-1013 + BE-1014 商品管理 v2 后端 Slice 1 (2026-06-14)

**变更内容**：
- 新增 `GET /api/products/{id}/file-bindings` 商品素材查询 API，返回主图/图集/SKU图片分组（ProductFileBindingsVO），预览 URL 统一为 `/api/files/{fileId}/preview`
- 新增 `PUT /api/products/skus` 单个 SKU 更新 API（SkuUpdateDTO），支持更新 price/costPrice/barCode/status，含租户归属校验
- 修复 `syncProductSkus`：已有 SKU 保留其 price/costPrice/barCode/status，不再被商品级更新覆盖；颜色/尺码组合移除时禁用而非物理删除
- 添加删除引用保护：商品删除检查订单明细/库存/文件绑定；颜色/尺码删除检查活跃商品关联；存在引用时抛 RuntimeException 建议禁用
- 新增 ProductFileBindingsVO.java、SkuUpdateDTO.java、ProductServiceV2Test.java
- Claude Code 完成实现后，Codex 两轮审核补正租户过滤、businessType 分离、空颜色/尺码禁用 SKU、空数组返回和脏绑定过滤等问题
- 39 个后端测试全部通过（ProductServiceV2Test 26/26 + ProductFileBindingServiceTest 11/11 + ProductFileBindingControllerTest 2/2）

**变更原因**：
- 商品管理 v2 需要为 SKU 精细维护和商品素材管理提供后端数据支持
- 原有 SKU 同步逻辑会覆盖手动维护的 SKU 价格，不符合 PRD 4.8.2 规则

**影响范围**：
- `blade-backend/src/main/java/com/blade/product/`
- `blade-backend/src/test/java/com/blade/product/`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`

**执行人**：AI

---

### [实现] - BA-407 to BA-410 商品管理 v2 前端 Slice (2026-06-14)

**变更内容**：
- **api/product.ts**：新增 `getProductFileBindings`、`updateSku`、`batchUpdateSkus` 函数及类型定义
- **products/index.vue**：商品编辑弹窗升级为 1100px 宽 4-Tab 分区（基础信息/颜色尺码/SKU明细/商品素材）；SKU明细支持 inline 编辑售价/成本价/条码/状态 + 批量保存；素材Tab展示主图/图集/SKU图片，支持上传和 fileId 输入，复用 `filePreviewUrl()` 预览；素材独立保存
- **BA-409 审核补正**：SKU 图片区域从只读展示补齐为可维护；每个 SKU 行支持当前图片预览、点击移除、上传图片、fileId 添加；保存素材时会提交全部 SKU 的 `skuImageBindings`
- **products/colors.vue / sizes.vue / categories.vue**：删除确认提示增加引用风险说明；删除失败弹窗展示后端引用保护消息
- **删除错误处理补正**：商品、颜色、尺码、分类删除接口若以异常形式返回，前端不再静默吞掉错误；取消/关闭仍静默忽略，其他错误弹窗展示后端消息
- **categories.vue 字段补正**：分类列表和删除文案使用 `row.categoryName || row.name` 回退，避免字段名不一致导致显示空值

**变更原因**：完成商品管理 v2 前端四个任务 (BA-407 ~ BA-410)，实现 SKU 精细维护、商品素材内聚和删除交互优化

**影响范围**：
- `blade-admin/src/api/product.ts`
- `blade-admin/src/views/products/index.vue`
- `blade-admin/src/views/products/colors.vue`
- `blade-admin/src/views/products/sizes.vue`
- `blade-admin/src/views/products/categories.vue`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`

**构建结果**：`vue-tsc -b && vite build` 通过，无 TS/编译错误

**执行人**：AI

---

### [优化] - 商品编辑页 UI 布局精修 (2026-06-14)

**变更内容**：
- 将商品编辑弹窗宽度调整为 1180px，并新增顶部商品摘要区，集中展示主图/状态/编码/颜色尺码/SKU 数量、进货价、批发价和素材数量。
- 基础信息 Tab 改为“商品资料 + 价格与状态”左右分区，表单标签改为顶部对齐，减少字段拥挤。
- 颜色尺码 Tab 改为卡片式选项网格，并保留 SKU 组合预览。
- SKU 明细 Tab 新增统计工具栏，展示 SKU 总数、启用数、禁用数，状态列补充文字说明。
- 商品素材 Tab 重排为主图、商品图集、SKU 图片三个清晰工作区，上传、fileId 绑定和保存区域保持独立。
- 新增响应式兜底样式，避免弹窗在窄屏下横向挤压。

**变更原因**：
- 商品管理 v2 功能已完成，但编辑页视觉层级和操作密度不足，影响生产环境维护商品资料的效率。

**影响范围**：
- `blade-admin/src/views/products/index.vue`
- `blade-admin/src/views/clients/index.vue`（修正历史 `::deep` 写法，消除构建 CSS 语法警告）
- `docs/05-CHANGELOG.md`

**验证结果**：
- `cd blade-backend && mvn test -Dtest=ProductServiceV2Test,ProductFileBindingServiceTest,ProductFileBindingControllerTest -DfailIfNoTests=false` 通过，39/39。
- `cd blade-admin && npm run build` 通过，无 TS/模板错误，无 `::deep` CSS 语法警告；仅保留项目既有 chunk size 提示。
- 真实 API 回归通过：临时商品创建、SKU 单独价格/成本/条码更新、商品更新后不覆盖手动 SKU 价格、素材绑定空保存/查询、临时商品删除清理均正常。
- 本地浏览器烟测通过：临时创建测试商品后打开编辑弹窗，基础信息、SKU 明细、商品素材 Tab 均正常渲染，无页面横向溢出；测试商品已删除。
- 当前登录页面只读 UI 回归通过：现有商品编辑弹窗基础信息、SKU 明细、商品素材、颜色尺码均正常渲染，无横向溢出，浏览器 console 无 error。

**执行人**：AI

---

### [修复] - 商品素材保存后预览图片失效 (2026-06-14)

**变更内容**：
- 修复商品编辑页保存素材绑定后，主图、商品图集、SKU 图片重新加载时显示破图的问题。
- `loadFileBindings()` 不再直接使用后端返回的裸 `/api/files/{id}/preview`，而是统一按 `fileId` 调用 `filePreviewUrl(fileId)` 生成带 `previewToken` 的预览地址。

**变更原因**：
- 商品列表能显示主图，是因为列表图片走 `filePreviewUrl()`，浏览器 `<img>` 请求带有 `previewToken`。
- 商品素材保存后重新加载绑定关系时，前端直接使用后端返回的裸 previewUrl；PRIVATE 文件预览接口要求登录，原生 `<img>` 不会携带 Authorization header，因此主图、图集、SKU 图片都会显示破图。
- 该问题不是缩略图未完成导致，属于预览鉴权 URL 生成不一致。

**影响范围**：
- `blade-admin/src/views/products/index.vue`
- `docs/05-CHANGELOG.md`

**验证结果**：
- `cd blade-admin && npm run build` 通过，无 TS/模板错误。

**执行人**：AI

---

## 2026-06-12 变更记录

### [规划] - 文件中心图片派生图/缩略图性能优化

**变更内容**：
- 在 PRD 文件中心章节补充“列表优先加载派生图”规则：商品列表、订单图片墙、文件中心网格、Catalog 卡片等列表/卡片场景优先加载缩略图或中图，点击大图/下载时再加载原图。
- 在文件中心设计文档新增 `file_derivative` 派生图设计，第一版只规划图片 `thumb` 和 `card` 两类派生图。
- 规划统一派生图访问接口：`GET /api/files/{id}/variant?type=thumb/card`，权限继承原文件预览权限，不绕过租户、登录和业务权限校验。
- 明确业务表仍只保存原始 `fileId`，不保存缩略图路径，避免后续切换本地、七牛云、NAS 或 CDN 时大规模改表。
- 明确历史图片可通过后续批量任务补生成派生图；派生图缺失或生成失败时允许回退原图，不影响上传主流程。
- 新增待开发任务：`BE-1012` 图片派生图/缩略图底座、`BA-1007` PC 图片缩略图接入、`BA-1028` Catalog 派生图加载优化。

**变更原因**：
- 现有图片原图可能达到数 MB，商品列表、订单图片墙、文件中心和 iPad Catalog 浏览时直接加载原图会导致首屏和滚动加载变慢。
- 文件中心已经统一了上传、预览和 fileId 引用，适合在统一入口生成派生图，后续能同时服务 PC、订单、商品、Catalog 和移动端。

**影响范围**：
- `docs/02-PRD.md`
- `docs/03-TASKS.md`
- `docs/12-FILE_CENTER_ASSET_DESIGN.md`
- `docs/SESSION_CONTEXT.md`
- `docs/05-CHANGELOG.md`

**边界说明**：
- 本次只更新需求、设计和任务文档，未开始代码开发。
- 第一版派生图优化不包含视频封面、视频转码、断点续传、WebP/AVIF、自适应 CDN，这些保留为后续扩展。

**验证结果**：
- 文档关键字核对和 Markdown diff 检查通过后再进入后续开发。

**执行人**：AI

---

## 2026-06-11 变更记录

### [功能开发] - BA-207 快速录单商品级批量 SKU 录入

**变更内容**：
- PC 快速录单页商品明细区新增“按商品批量添加”区域，支持按商品款号/商品名远程搜索商品。
- 选择商品后按颜色 x 尺码展示正常状态 SKU 数量矩阵，可一次填写多个 SKU 数量并批量添加到订单明细。
- 批量添加时不读取、不展示、不校验库存，不调用库存接口；SKU 是否显示只依赖商品和 SKU 自身正常状态。
- 相同 `skuId` 重复添加时自动合并数量，不新增重复行，并保留原明细中已手动修改的单价和成本价。
- 添加成功后清空本次矩阵数量，降低误点重复累计风险。
- 旧的单 SKU 备用下拉同步过滤为正常状态商品/SKU。
- 根据页面实测反馈修复 3 个细节：批量默认单价/默认成本改为可编辑输入；批量添加后的明细 SKU 选择框显示商品/颜色/尺码而不是原始 `skuId`；批量添加前自动移除初始空白占位行。
- 批量默认单价/默认成本输入框改为普通金额输入，默认显示去掉末尾 `.00`；如需小数可由录单员手动输入，提交时仍按数字金额写入订单明细。
- 订单明细表中的单价/成本价输入框同步改为普通金额输入，批量添加到下方 SKU 明细后不再默认显示 `.00`。

**变更原因**：
- 实际纸质订单录入按商品款号集中录入，同一商品多颜色/多尺码时逐个 SKU 搜索效率低。
- 当前库存功能仍在完善阶段，订单录入需要与库存暂时解耦，优先保证生产环境正常录单。

**影响范围**：
- `blade-admin/src/views/orders/quick.vue`
- `docs/03-TASKS.md`

**验证结果**：
- Claude Code 负责第一版实现；Codex 负责代码复核和监督修正。
- `git diff --check` 通过。
- `cd blade-admin && npm run build` 通过，保留项目既有 `::deep` 与 chunk size 构建警告。
- 本地 Playwright 渲染验证 `/orders/quick` 可打开，页面出现“按商品批量添加”和“搜索款号 / 商品名”区域。

**执行人**：Claude Code + AI

---

### [规划] - 快速录单商品级批量 SKU 录入 SOW

**变更内容**：
- 在 PRD 的 PC 快速录单章节补充“按商品批量录入 SKU”作为高频主路径：先选择商品款号/商品名称，再在 SKU 矩阵中填写颜色/尺码数量，一次性添加到订单明细。
- 明确第一版快速录单与库存功能暂时解耦：不读取库存、不展示库存、不按库存过滤 SKU、不做库存不足校验；只展示正常状态 SKU。
- 明确重复添加规则：相同 `skuId` 自动合并数量，不新增重复行，且不覆盖已手动修改的单价和成本价。
- 新增任务 `BA-207 PC 快速录单商品级批量 SKU 录入`，状态为未开始。
- 新增 Claude Code SOW：`docs/superpowers/plans/2026-06-11-quick-order-product-batch-entry-sow.md`。

**变更原因**：
- 纸质订单实际录入通常按商品款号集中录入，而不是逐个 SKU 搜索；现有流程在同一商品多颜色/多尺码时操作次数过多。
- 当前库存功能仍在完善阶段，订单录入必须优先保证生产可用，不能被库存数据完整性影响。

**影响范围**：
- `docs/02-PRD.md`
- `docs/03-TASKS.md`
- `docs/superpowers/plans/2026-06-11-quick-order-product-batch-entry-sow.md`
- `docs/SESSION_CONTEXT.md`

**执行人**：AI

---

### [文档] - 新增 Git 分支与发布工作流规范

**变更内容**：
- 新增 `docs/reference/GIT_BRANCH_WORKFLOW.md`，明确 `master`、`develop`、`feature/*`、`release/*`、`hotfix/*`、`snapshot/*` 的职责边界。
- 明确 NAS 生产环境只部署 `master`，功能开发不得直接在 `master` 上进行。
- 明确多功能并行开发时，通过 `feature/*` 开发、`develop` 集成测试、`release/*` 挑选上线内容，测试通过后再合入 `master`。
- 明确 GitHub 远程通道复用 `origin = https://github.com/CHENjiarun66/blade-project.git`，push/fetch 失败时先检查代理和认证，不得擅自更换远程仓库。
- 明确 Agent 开发前后必须汇报当前分支、工作区状态、提交列表、测试结果、是否 push、是否需要合并或发布。
- 在 `AGENTS.md`、`README.md`、`docs/01-README.md`、`docs/SESSION_CONTEXT.md`、`docs/reference/PROJECT_STRUCTURE.md` 中补充该规范链接。

**变更原因**：
- 用户确认后续需要多个功能模块并行开发，并希望 Agent 自动创建和使用正确分支；上线时集中到测试/发布分支，验证通过后再进入主分支和 NAS 生产环境。

**影响范围**：
- 文档与协作流程，不涉及业务代码。

**执行人**：AI

---

## 2026-06-08 变更记录

### [运维规范] - NAS 生产发布安全门禁

**变更内容**：
- 将原 `deploy/nas/deploy_from_local.sh` 标记为首次部署/基础设施重建专用，新增 `FIRST_DEPLOY_CONFIRM=YES` 确认锁，避免日常发布误用全量流程。
- 新增 `deploy/nas/deploy_app_from_local.sh` 作为日常生产发布脚本：默认 dry run，必须传 `--execute`；只构建和发布 `blade-backend:prod`、`blade-web:prod`，只重启 `backend` 和 `web`，不重启 MySQL/Redis。
- 日常发布脚本内置发布前 NAS 数据库备份、备份非空校验、Docker 镜像 `linux/amd64` 架构校验和 `/catalog` 基础验证。
- 新增 `deploy/nas/backup_db.sh`，标准化 NAS 当前生产库只读备份。
- 新增 `deploy/nas/check_platform.sh`，用于确认群晖 Linux、Docker server 架构、compose 版本、持久化目录和容器状态。
- 更新 `docs/13-NAS_PRODUCTION_OPS.md` 和 `deploy/nas/README.md`，补充数据安全优先级、发布门禁、备份门禁、平台确认和操作红线。

**变更原因**：
- 用户明确强调生产发布风险高，尤其担心 NAS 数据丢失；同时群晖 Linux/Docker 环境与本机不同，必须把备份、架构校验和日常发布边界固化为可执行规范。

**影响范围**：
- `deploy/nas/deploy_from_local.sh`
- `deploy/nas/deploy_app_from_local.sh`
- `deploy/nas/backup_db.sh`
- `deploy/nas/check_platform.sh`
- `deploy/nas/README.md`
- `docs/13-NAS_PRODUCTION_OPS.md`
- `docs/05-CHANGELOG.md`

**验证结果**：
- 新增/修改的 shell 脚本已通过 `bash -n` 语法检查。

---

### [Bug修复] - 文件中心视频上传与预览

**变更内容**：
- 排查文件中心视频上传链路，确认视频 MIME 支持本身存在：`video/mp4`、`video/webm`、`video/quicktime` 已在后端允许列表内。
- 修复默认上传大小与视频场景不匹配的问题：Spring multipart 单文件上限从 10MB 提升到 200MB，请求上限提升到 220MB；业务校验 `blade.file.max-size-mb` 默认提升到 200MB。
- 上传大小支持环境变量覆盖：`BLADE_MULTIPART_MAX_FILE_SIZE`、`BLADE_MULTIPART_MAX_REQUEST_SIZE`、`BLADE_FILE_MAX_SIZE_MB`。
- NAS 前端 Nginx `client_max_body_size` 从 50m 提升到 220m，避免生产入口在请求到达后端前拦截较大视频。
- NAS compose 和 `.env.prod.example` 补充上传大小相关环境变量，便于生产环境显式调整。
- 新增 `MaxUploadSizeExceededException` 处理，超过 Spring multipart 限制时返回明确错误，不再表现为模糊系统错误。
- PC 文件中心上传前增加 200MB 前端预检，超过限制直接提示具体文件名和限制值。
- PC 文件中心视频预览弹窗从占位图改为 `<video controls preload="metadata" playsinline>`，上传成功后可直接播放基础视频。
- 更新文件中心相关任务和设计文档，将旧 10MB 配置对齐为 200MB。

**变更原因**：
- 用户反馈“视频上传好像有问题”。根因是第一版虽支持基础视频类型，但仍沿用图片上传阶段的 10MB 默认上限，普通手机/iPad 视频容易超过限制；同时前端只显示上传失败数量，缺少明确原因。

**影响范围**：
- `blade-backend/src/main/resources/application.yml`
- `blade-backend/src/main/java/com/blade/file/config/FileStorageProperties.java`
- `blade-backend/src/main/java/com/blade/common/exception/GlobalExceptionHandler.java`
- `blade-backend/src/test/java/com/blade/file/FileVideoSupportTest.java`
- `blade-backend/src/test/java/com/blade/file/FileAllowedTypesRegressionTest.java`
- `blade-admin/src/views/files/index.vue`
- `deploy/nas/nginx/default.conf`
- `deploy/nas/docker-compose.prod.yml`
- `deploy/nas/.env.prod.example`
- `docs/03-TASKS.md`
- `docs/09-FILE_STORAGE_DESIGN.md`
- `docs/12-FILE_CENTER_ASSET_DESIGN.md`
- `docs/13-NAS_PRODUCTION_OPS.md`

**验证结果**：
- `mvn test -Dtest=FileVideoSupportTest,FileAllowedTypesRegressionTest,FileControllerTest -DfailIfNoTests=false` 通过，29/29。
- `mvn test '-Dtest=File*Test' -DfailIfNoTests=false` 沙盒内因 Mockito/ByteBuddy JVM attach 限制失败；沙盒外重跑通过，98/98。
- `blade-admin` 执行 `npm run build` 通过；仍有既有 `::deep` CSS 警告和大 chunk 警告，与本次修复无关。

---

## 2026-06-05 变更记录

### [运维文档] - NAS 生产发布 Agent 手册

**变更内容**：
- 新增 `docs/13-NAS_PRODUCTION_OPS.md`，作为后续 Agent 从本地开发环境发布到 NAS 生产环境的专用运维文档。
- 文档覆盖：NAS 基础信息、生产目录、容器、数据库边界、项目架构、首次部署、日常发布、GitHub/Gitee/本机归档代码来源、数据库迁移、uploads 迁移、回滚、常见问题和 Agent 操作红线。
- 明确发布分层：首次部署才处理 MySQL/Redis/基础镜像；日常发布只更新 `blade-backend:prod` 和 `blade-web:prod`。
- 明确生产目录为 `/volume2/blade`，入口为 `http://192.168.1.10:8899/catalog`。
- 更新 `docs/01-README.md` 与 `docs/SESSION_CONTEXT.md`，加入 NAS 运维手册索引。
- 将关键文档快照同步到 NAS `/volume2/blade/docs`，并新增 `README_FOR_AGENTS.md` 作为 NAS 侧 Agent 接手入口。

**变更原因**：
- 用户要求后续其他 Agent 能快速接手本地开发到 NAS 生产部署流程，并清楚环境边界和操作红线。

---

### [数据迁移] - 本机生产库迁移到 NAS

**变更内容**：
- 将本机 MySQL 容器中的 `blade_project_prod` 只读导出为 SQL。
- 本机生产库不做任何写入修改。
- 在迁移 SQL 末尾追加租户归一化语句，将 NAS 目标库中 `sys_tenant.id=1` 的 `tenant_code` 设置为 `dwy_jiajiadress`。
- 导入前备份 NAS 当前库到 `/volume2/blade/db-backups/nas_blade_project_prod_before_import_20260605.sql`。
- 停止 NAS 后端和前端后，重建 NAS `blade_project_prod` 并导入生产库数据，再启动服务。

**验证结果**：
- NAS `sys_tenant`: `id=1 tenant_code=dwy_jiajiadress`。
- NAS 关键数据量：`product=164`、`product_sku=416`、`sale_order=81`、`file_storage=22`、`flyway=39`。
- `http://192.168.1.10:8899/catalog` 返回 200。
- 使用 `dwy_jiajiadress/admin/admin123` 登录后，`/api/catalog/filters` 返回 200。

**注意事项**：
- 本次仅迁移数据库；`file_storage` 中已有 22 条文件记录，但对应的本机上传文件实体是否已同步到 NAS `uploads` 目录需要另行核对。
- 本机生产库仍保持原租户 code，不受本次 NAS 导入转换影响。

---

### [部署] - 群晖 NAS 生产部署骨架

**变更内容**：
- 新增 `deploy/nas/docker-compose.prod.yml`，生产环境在 `/volume2/blade` 下运行独立 MySQL、Redis、后端和前端 Nginx。
- 新增 `deploy/nas/nginx/default.conf`，支持 `/api/` 反向代理和前端 SPA 路由回退。
- 新增 `blade-backend/Dockerfile`、`blade-admin/Dockerfile`，用于 NAS 本地构建生产镜像。
- 新增 `deploy/nas/.env.prod.example`、`deploy/nas/README.md`、`deploy/nas/deploy_from_local.sh`，记录部署目录、端口、启动命令、备份重点和一键发布流程。
- 调整后端 JWT 配置，允许生产环境通过 `JWT_SECRET` 覆盖默认密钥。

**部署约定**：
- NAS 地址：`192.168.1.10`
- 部署目录：`/volume2/blade`
- 外部端口：`8899`
- 访问入口：`http://192.168.1.10:8899/catalog`
- 真实 `.env.prod` 只保存在 NAS，不提交到项目文档。

**验证结果**：
- `blade-backend`: `mvn clean package -DskipTests` 通过。
- `blade-admin`: `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build` 通过。
- NAS 实际部署完成：`blade-mysql`、`blade-redis`、`blade-backend`、`blade-web` 均为 Up。
- `http://192.168.1.10:8899/catalog` 返回 200。
- 使用管理员登录后，`/api/catalog/filters` 返回 200。

**部署经验**：
- 群晖访问 Docker Hub 超时，改为本机按 `linux/amd64` 构建并导出 Docker 离线镜像包，再上传到 NAS `docker load`。
- Apple Silicon 本机默认镜像不能直接给群晖 x86_64 使用，否则容器会 `exec format error`。
- Synology SSH 环境的 `scp` 默认 SFTP 子系统不可用，发布脚本使用 `scp -O`。
- MySQL 首次初始化和 Flyway 迁移较慢，API 在后端启动完成前会短暂 502。

---

## 2026-06-06 变更记录

### [界面优化] - Catalog 手机竖屏版

**变更内容**：
- Catalog 增加 iPhone 14 Pro 竖屏适配断点（393 × 852 CSS px），延续 iPad 版 quiet luxury 米白/金棕视觉。
- 手机竖屏保留三层浏览逻辑：两列商品网格 → 底部详情抽屉 → 全屏大图。
- 手机顶部栏压缩品牌、搜索、筛选 chips 的间距；商品卡片保持 3:4 图片比例；底部操作栏改为更适合拇指点击的固定栏。
- 底部详情抽屉在手机尺寸下压缩图片、标签和 SKU 矩阵密度，避免内容溢出。
- 手机版横屏不进入 iPad 并排详情布局，显示“请切回竖屏浏览”提示，第一版只支持竖屏使用。
- 新增 Playwright 回归覆盖 iPhone 14 Pro 竖屏两列网格和横屏限制提示。

**变更原因**：
- 用户要求在保持 iPad 展示页风格一致的前提下，做一版 iPhone 14 Pro 手机界面，并且手机版只支持竖屏，不提供横屏版本。

**影响范围**：
- `blade-admin/src/views/catalog/index.vue`
- `blade-admin/src/views/catalog/DetailView.vue`
- `blade-admin/e2e-catalog-infinite-cache.spec.ts`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`

**验证结果**：
- `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build` 通过；仍有既有 `::deep` CSS 警告和大 chunk 警告。
- `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npx playwright test e2e-catalog-infinite-cache.spec.ts --project=chromium --reporter=list` 通过，4/4。
- Playwright 截图验证 iPhone 14 Pro 竖屏首屏：两列商品卡片、顶部搜索/筛选、底部操作栏显示正常。
- Playwright 截图验证 iPhone 14 Pro 横屏：显示“请切回竖屏浏览”提示，没有进入 iPad 并排详情布局。

---

## 2026-06-04 变更记录

### [交互优化] - Catalog 图片滑动切换

**变更内容**：
- Catalog 详情轮播图支持左右滑动切换图片，保留左右按钮和缩略图点击。
- Catalog 全屏大图支持左右滑动切换图片，左滑下一张、右滑上一张，保留原左右按钮和底部缩略图。
- 图片滑动改为跟手轨道动画：拖动时图片随手指横向移动，松手后用 220ms ease-out 过渡到上一张/下一张；未达到阈值时回弹原图。
- 图片区域增加 `touch-action: pan-y pinch-zoom`、`user-select` 和 `-webkit-user-drag` 控制，减少 iPad 上拖拽图片等误操作，同时保留两指缩放。
- `index.html` viewport 保留 `width=device-width, initial-scale=1.0, viewport-fit=cover`，不设置 `maximum-scale/user-scalable`；Catalog 通过单指 `touchstart/touchend` 手势判断只拦截双击页面放大，避免影响两指 pinch 缩放。
- Catalog 在 `orientationchange` / `visualViewport.resize` 后重置滚动位置，降低竖屏切横屏后页面停留在异常放大状态的概率。
- 详情轮播滑动后只抑制滑动结束产生的合成点击，不影响用户随后正常点击进入全屏大图。

**变更原因**：
- 用户反馈 iPad 上只点左右按钮切换图片体验较麻烦；第一版滑动切换是瞬间切图，缺少 iPhone 相册式动效；同时网页双击/横竖屏切换容易导致页面异常放大，需要禁止单指双击放大并尽量保持正常视口，但保留两指缩放用于临时查看细节。

**影响范围**：
- `blade-admin/index.html`
- `blade-admin/src/views/catalog/DetailView.vue`
- `blade-admin/src/views/catalog/index.vue`
- `blade-admin/e2e-catalog-infinite-cache.spec.ts`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`

**验证结果**：
- 先新增 Playwright 规格确认旧实现失败：滑动后详情轮播图片没有变化。
- 实现后 `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npx playwright test e2e-catalog-infinite-cache.spec.ts --project=chromium --reporter=list --grep swipe` 通过，1/1。
- 完整 Catalog 规格 `e2e-catalog-infinite-cache.spec.ts` 通过，3/3。
- `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build` 通过；仅保留旧页面 `::deep` 和大 chunk 体积警告。
- 二次优化后再次运行完整 Catalog 规格通过，3/3；`npm run build` 通过。

**执行人**：Codex

### [功能优化] - Catalog 无限滚动与本地缓存

**变更内容**：
- Catalog 商品网格取消底部分页器，改为滚动接近底部时自动请求下一页。
- 搜索、现货、有图、分类、颜色、尺码筛选变化时，清空当前列表并从第一页重新加载。
- 新增商品列表缓存：按筛选条件生成 `catalog:products:v1:*` 缓存 key；页面打开时先渲染缓存数据，再后台刷新第一页。
- 新增 `CachedImage` 组件和 Catalog 图片缓存工具：私有文件预览图按 fileId 写入 IndexedDB，命中缓存后使用本地 Blob URL 渲染，避免 token 变化导致图片缓存失效。
- 登录态失效或退出登录时清理 Catalog 商品缓存和 IndexedDB 图片缓存，降低同一台 iPad 上私有图片残留风险。
- 因 iPad 当前通过局域网 HTTP 地址调试，第一版未采用 Service Worker/Cache Storage，避免 iOS Safari 在非安全源上注册不稳定。

**变更原因**：
- 用户反馈 Catalog 商品卡片不应使用分页器，应像相册一样持续下滑；同时服务器网速较慢，希望图片和商品数据能缓存在本地，未变化的数据下次访问不重复下载。

**影响范围**：
- `blade-admin/src/views/catalog/index.vue`
- `blade-admin/src/views/catalog/DetailView.vue`
- `blade-admin/src/components/CachedImage.vue`
- `blade-admin/src/utils/catalogCache.ts`
- `blade-admin/src/stores/auth.ts`
- `blade-admin/src/api/client.ts`
- `blade-admin/e2e-catalog-infinite-cache.spec.ts`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`

**验证结果**：
- 先运行新增 Playwright 规格确认旧实现失败：分页器仍存在、缓存商品未先渲染。
- 实现后 `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npx playwright test e2e-catalog-infinite-cache.spec.ts --project=chromium --reporter=list` 通过，2/2。
- `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build` 通过；仅保留旧页面 `::deep` 和大 chunk 体积警告。
- 真实后端数据冒烟：`/catalog` 初始 20 张商品卡片，滚动后加载到 163 张；无 `.grid-pagination`；实际请求页码 1 到 9。
- 搜索 `8001` 后图片缓存验证通过：卡片图渲染为 `blob:` 本地 URL，IndexedDB `blade-catalog-image-cache` 中有图片缓存记录，商品列表缓存 key 已写入。

**执行人**：Codex

### [数据同步] - 生产库商品主数据同步到开发库

**变更内容**：
- 将本地生产库 `blade_project_prod` 的商品主数据同步到开发库 `blade_project`。
- 覆盖同步范围：`product_category`、`product_color`、`product_size`、`product`、`product_sku`、`product_color_rel`、`product_size_rel`。
- 清理开发库旧的 `product/sku` 文件业务绑定，避免旧测试绑定误挂到同步后的生产商品 ID。
- 文件中心只补充商品主图实际引用的 `file_storage` 记录：`file_id=10`，未批量覆盖开发库其他文件元数据。
- 未同步订单、客户、库存、库存流水、入库记录等业务数据；开发库原库存记录未主动清空。

**变更原因**：
- 用户要求将生产环境中的商品数据搬到测试/开发环境，便于在当前 iPad Catalog 和商品管理页面中使用真实商品数据测试。

**影响范围**：
- 本地 MySQL 容器 `blade-mysql`
- 源库：`blade_project_prod`
- 目标库：`blade_project`
- 备份文件：`tmp/db-backups/blade_project_product_tables_before_prod_sync_20260604_211015.sql`

**验证结果**：
- 同步后开发库数量：商品 164、SKU 416、分类 13、颜色 22、尺码 14、颜色关联 412、尺码关联 163。
- 商品表中 163 个启用且未软删除、1 个软删除；业务接口 `/api/products` 和 `/api/catalog/products` 返回 total=163，符合过滤逻辑。
- `8001#` 在 Catalog 接口中返回 `mainImageUrl=/api/files/10/preview`，对应本地图片文件存在。

**执行人**：Codex

### [Bug修复] - 登录态保持与 refresh token 续签稳固

**变更内容**：
- 确认登录页 `remember` 字段已传入后端，后端配置为 access token 1 小时、普通 refresh token 7 天、勾选保持登录时 refresh token 30 天。
- refresh token 新增 `tenantId` claim；登录生成 refresh token 时写入当前租户 ID。
- `/api/auth/refresh` 续签时先读 Redis 中的 `token:tenant:{refreshToken}`，若 Redis 映射缺失则回退读取 refresh token 自身的 `tenantId` claim，并在续签前恢复 `TenantContext`。
- 续签生成的新 access token 与 refresh token 继续写入租户映射；勾选保持登录继续按 30 天，不勾选按 7 天。
- 前端自动刷新失败或收到 401/403 后跳转登录页时，保留当前页面 `redirect`，避免从 `/catalog` 等页面重新登录后跳回后台默认页。

**变更原因**：
- 用户反馈登录态保持时间过短，半小时到一小时后重新打开页面经常要求重新登录；需要确认 30 天保持登录为何未稳定生效，并将未勾选保持登录的有效期调整为 7 天。
- 排查结果显示 30 天配置与请求字段本身有效，主要风险在 refresh 续签链路：refresh token 的租户信息只依赖 Redis 映射，映射丢失后无法稳定重建登录态。

**影响范围**：
- `blade-backend/src/main/java/com/blade/auth/service/JwtTokenProvider.java`
- `blade-backend/src/main/java/com/blade/auth/service/AuthService.java`
- `blade-backend/src/test/java/com/blade/auth/JwtTokenProviderTest.java`
- `blade-backend/src/test/java/com/blade/auth/AuthServiceRefreshTokenTest.java`
- `blade-admin/src/api/client.ts`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`

**验证结果**：
- `mvn test -Dtest=JwtTokenProviderTest,AuthServiceRefreshTokenTest -DfailIfNoTests=false` 通过，3/3。
- `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build` 通过；仅保留旧页面 `::deep` 和大 chunk 体积警告。
- 本地重启后端并实测 `/api/auth/login`：`remember=true` 返回 refresh token 2,592,000 秒（30 天），`remember=false` 返回 604,800 秒（7 天），二者均带 `tenantId=1`。
- 删除 Redis 中旧 refresh token 的 `token:tenant:*` 映射后，调用 `/api/auth/refresh` 仍返回 200，并续签出 30 天、带 `tenantId=1` 的新 refresh token。

**执行人**：Codex

### [Bug修复] - Catalog PWA 登录后回跳原页面

**变更内容**：
- 路由守卫在未登录访问受保护页面时，跳转登录页并携带 `redirect=to.fullPath`。
- 登录页登录成功后优先读取 `redirect`，校验为站内安全路径且用户具备对应权限后，回跳原页面。
- `/catalog` 纳入登录页回跳权限映射，要求 `data:catalog:view`。
- 已登录状态下访问 `/login?redirect=/catalog` 时，也会优先回跳 `/catalog`，不再固定进入后台首屏。

**变更原因**：
- iPad 从主屏图标打开 `/catalog` 时，未登录会先进入登录页；原逻辑登录后跳到后台默认页面，不符合“从 Catalog 进入就回 Catalog”的 App 使用预期。

**影响范围**：
- `blade-admin/src/router/index.ts`
- `blade-admin/src/views/login/index.vue`
- `docs/05-CHANGELOG.md`

**验证结果**：
- `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build` 通过；仅保留旧页面 `::deep` 和大 chunk 体积警告。
- Playwright 验证清空登录态后访问 `/catalog`，自动跳转到 `/login?redirect=/catalog`；登录 `test_tenant/admin/admin123` 后回到 `/catalog`，页面显示 Catalog 标题和商品卡片。

**执行人**：Codex

### [功能优化] - Catalog iPad PWA 主屏调试支持

**变更内容**：
- `blade-admin` Vite dev/preview server 改为监听 `0.0.0.0:5777`，支持 iPad 通过同一 Wi-Fi 访问 Mac 局域网地址。
- 新增 `/manifest.webmanifest`，默认 `start_url` 为 `/catalog`，`display` 为 `standalone`，主题色为金棕色。
- `index.html` 新增 PWA 和 iOS 主屏 meta：manifest、apple touch icon、theme color、standalone、主屏标题和状态栏样式。
- 新增 Catalog 主屏图标：`catalog-app-icon.svg` 和 `catalog-app-icon.png`。
- 环境文档补充 iPad Safari 访问、添加到主屏幕、独立窗口调试流程。

**变更原因**：
- 用户希望在 iPad 上按类似 App 的方式调试 Catalog，不显示 Safari 地址栏和标签栏，而是从桌面图标直接进入选款页。

**影响范围**：
- `blade-admin/vite.config.ts`
- `blade-admin/index.html`
- `blade-admin/public/manifest.webmanifest`
- `blade-admin/public/catalog-app-icon.svg`
- `blade-admin/public/catalog-app-icon.png`
- `docs/00-SETUP.md`
- `docs/05-CHANGELOG.md`
- `docs/12-FILE_CENTER_ASSET_DESIGN.md`

**验证结果**：
- `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build` 通过；仅保留旧页面 `::deep` 和大 chunk 体积警告。
- `curl http://192.168.1.3:5777/catalog` 返回 200 HTML。
- `curl http://192.168.1.3:5777/manifest.webmanifest` 返回 PWA manifest。
- 当前前端进程已监听 `*:5777`，Mac 当前 Wi-Fi IP 为 `192.168.1.3`。

**执行人**：Codex

### [视觉优化] - iPad Catalog 对齐 Stitch 效果图

**变更内容**：
- Catalog 页面由 Codex 直接调整，不调用 Claude Code；按用户提供的 Stitch 参考图重做视觉细节。
- 顶部改为品牌/搜索/游客模式/筛选图标主栏，筛选 chip 独立一行；整体主题从紫色残留调整为暖白 + 金棕 quiet luxury。
- 商品卡片改为更紧凑的图片比例、轻边框、低阴影、金色选中边框和圆形勾选标识；横屏选中详情时左侧继续保持三列网格。
- 右侧详情面板改为独立圆角边框卡片；详情图片、缩略图、商品信息和 SKU 矩阵调整为更接近参考图的密度和金棕色调。
- 竖屏新增底部固定操作栏（筛选 / 选款清单 / 游客模式），抽屉关闭默认标题栏，改为顶部短把手；抽屉高度压缩为更接近参考图的底部详情抽屉。
- 全屏看图模式调整为深色背景、圆形切图按钮、顶部款号计数、底部缩略图胶片条和大图圆角阴影。

**变更原因**：
- 用户确认功能已完善，但界面没有严格按 Stitch 效果图执行，要求本轮由 Codex 亲自优化前端设计。

**影响范围**：
- `blade-admin/src/views/catalog/index.vue`
- `blade-admin/src/views/catalog/DetailView.vue`
- `docs/05-CHANGELOG.md`

**验证结果**：
- `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build` 通过；仅保留旧页面 `::deep` 和大 chunk 体积警告。
- Playwright 截图验证横屏详情、竖屏抽屉、全屏看图三态；最终横屏截图确认激活 chip 为金棕色 `rgb(155, 107, 34)`，抽屉默认 header 已移除并显示自定义把手。

**执行人**：Codex

### [功能优化] - iPad Catalog 三层图片浏览逻辑

**变更内容**：
- 明确并实现 Catalog 三层图片源规则：商品网格只展示商品主图；详情面板/竖屏抽屉顶部轮播展示商品图 + 所有 SKU 图片全集；点击详情大图进入全屏时只浏览商品图片集。
- `DetailView` 新增 `fullscreenImages` 入参，顶部轮播可以显示 SKU 图片，但打开全屏时按商品图片集定位，SKU 图不会混入商品大图浏览。
- Catalog 页面新增 `skuImages()`、`detailImages()` 聚合函数，商品详情顶部从 `product.mainImageUrl + product.imageUrls` 扩展为 `product 图片 + sku.imageUrls` 去重集合。

**变更原因**：
- 用户确认现有抽屉顶部只显示商品图片不够流畅，客户选款时应能在详情顶部看到所有 SKU 照片，但全屏大图仍应聚焦商品图片集。

**影响范围**：
- `blade-admin/src/views/catalog/index.vue`
- `blade-admin/src/views/catalog/DetailView.vue`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`
- `docs/12-FILE_CENTER_ASSET_DESIGN.md`

**验证结果**：
- `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build` 通过；仅保留旧页面 `::deep` 和大 chunk 体积警告。
- Playwright 竖屏验证 `/catalog`：点击商品信息打开底部抽屉，抽屉顶部轮播出现 2 个唯一图片源（商品图 + SKU 图），图片均加载成功。
- Playwright 验证点击抽屉大图进入全屏后，全屏只显示 1 个唯一商品图片源，加载成功。

**执行人**：Codex

### [Bug修复] - 私有文件预览 403 导致图片不显示

**变更内容**：
- 修复文件中心和业务页面图片“上传成功但不显示”的问题：浏览器原生 `<img>` 和新窗口打开不会携带 Axios 的 `Authorization` 请求头，后端私有文件预览因此返回 `403`。
- 后端 `JwtAuthenticationFilter` 仅对 `/api/files/{id}/preview` 支持 `previewToken` 查询参数，继续由 `FileController.preview` 执行 PUBLIC/PRIVATE、租户和业务权限校验。
- 前端统一 `filePreviewUrl(fileId)` 自动拼接当前 access token；文件中心“打开原文件”和 Catalog 后端返回的 `/api/files/{id}/preview` 地址也统一转成带认证的预览地址。
- Catalog 页面补齐数据规范化：商品主图、商品图集、SKU 图片在进入页面状态前统一转换为带认证的预览地址，避免卡片模板直接渲染原始 `mainImageUrl`。
- 订单、商品、入库、文件中心等已走 `parseImageSources()`/`filePreviewUrl()` 的图片展示链路同步恢复，不改业务保存结构，仍保存 fileId。

**变更原因**：
- 管理员上传商品图片后，文件中心已有记录，但缩略图为空；点击原文件返回 `{"code":403,"message":"您没有该操作权限，请联系管理员授权"}`。

**影响范围**：
- `blade-backend/src/main/java/com/blade/config/SecurityConfig.java`
- `blade-backend/src/test/java/com/blade/config/JwtAuthenticationFilterTest.java`
- `blade-admin/src/api/file.ts`
- `blade-admin/src/views/files/index.vue`
- `blade-admin/src/views/catalog/index.vue`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`
- `docs/SESSION_CONTEXT.md`

**验证结果**：
- `mvn test -Dtest=JwtAuthenticationFilterTest,FileControllerTest -DfailIfNoTests=false` 通过，22/22。
- `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build` 通过；仅保留旧页面 `::deep` 和大 chunk 体积警告。
- `curl` 验证无 token 预览仍返回权限错误；带 `previewToken` 的 `/api/files/7/preview` 返回 PNG 图片数据。
- Playwright 验证 `/files` 文件中心 7/7 张 `/api/files/*/preview` 图片加载成功。
- Playwright 验证 `/products` 商品列表 1/1 张 fileId 图片加载成功。
- Playwright 验证 `/catalog` 商品卡片图片加载成功，点击卡片图进入全屏大图后 2/2 张 `/api/files/*/preview` 图片均加载成功。

**执行人**：Codex

### [功能开发] - iPad Catalog 现货选款页第一版

**变更内容**：
- 后端新增 Catalog 只读接口：`GET /api/catalog/products`、`GET /api/catalog/products/{id}`、`GET /api/catalog/filters`。
- Catalog 接口只返回客户展示需要的商品、SKU、图片预览 URL、颜色/尺码和库存状态，不返回价格、成本、供应商、真实库存数量。
- 库存状态按 `quantity - reserved_qty - global_reserved_qty > 0` 聚合为 `有现货/暂无现货`；`stockMode=in_stock` 在分页前过滤。
- V37 新增 `menu:catalog` 和 `data:catalog:view` 权限，并默认授权 `ROLE_OWNER`、`ROLE_ADMIN`、`ROLE_SALES`。
- PC 管理端新增独立全屏 `/catalog` 页面，采用 quiet luxury 风格，支持横屏网格+右侧详情、竖屏网格+底部抽屉、筛选、SKU 矩阵和全屏大图模式。

**变更原因**：
- 用户确认 iPad 现货选款展示页设计方向，要求按该版进入设计开发。

**影响范围**：
- `blade-backend/src/main/java/com/blade/catalog/**`
- `blade-backend/src/main/resources/db/migration/V37__catalog_permissions.sql`
- `blade-backend/src/test/java/com/blade/catalog/**`
- `blade-admin/src/api/catalog.ts`
- `blade-admin/src/views/catalog/**`
- `blade-admin/src/router/index.ts`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`
- `docs/SESSION_CONTEXT.md`

**验证结果**：
- `mvn test -Dtest=CatalogAvailabilityTest,CatalogDtoTest,CatalogVoSecurityTest -DfailIfNoTests=false` 通过，17/17。
- `mvn spring-boot:run` 启动成功，Flyway V37 已应用到开发库 `blade_project`。
- `curl` 验证 `/api/catalog/filters`、`/api/catalog/products` 返回 200。
- `PATH="/Users/chenjiarun/.local/node-v22/current/bin:$PATH" npm run build` 通过；仅保留旧页面 `::deep` 和大 chunk 体积警告。
- Playwright 验证 `/catalog` 横屏/竖屏渲染、商品详情、竖屏抽屉和全屏大图模式通过。

**执行人**：Claude Code + Codex 复核修正

### [设计锁定] - iPad Catalog 展示页 quiet luxury 方向

**变更内容**：
- 锁定 `/catalog` 第一版视觉方向：米白背景、深炭黑文字、少量金色点缀、轻边框、低阴影的 quiet luxury 风格。
- 锁定横竖屏响应式结构：横屏为商品网格 + 右侧详情；竖屏为商品网格 + 底部/全屏详情。
- 锁定三层浏览：商品网格 → 商品详情 → 全屏大图看图模式。
- 全屏大图模式需支持切图、缩略图胶片条、关闭返回、款号/图片序号和现货状态展示。
- 第一版身份边界为游客/散客模式；客户选择、扫码识别、行为埋点和选款清单进入后续阶段。

**变更原因**：
- 用户确认采用结合 Stitch 配色氛围与 Codex 三层浏览结构的设计稿，并要求按该版本进入设计开发。

**影响范围**：
- `docs/12-FILE_CENTER_ASSET_DESIGN.md`
- `docs/02-PRD.md`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`

### [功能开发] - BA-1004~BA-1006 PC 文件中心上传/移动/删除/绑定/清理

**变更内容**：
- BA-1004：上传按钮改为隐藏多文件 input，支持批量上传到 temp；网格视图新增 el-checkbox 多选，列表视图新增 type=selection 列；批量工具栏（移动/绑定/删除/取消选择）；移动弹窗 radio-group 选文件夹或未归档；删除前并行查询绑定展示风险详情，后端有绑定则拒绝，确认后仅调 POST /api/files/batch-delete
- BA-1005：新增 FileBindDialog.vue，remote 搜索商品→选角色 main/gallery/sku_image→sku_image 时显示 SKU 多选→PUT /api/products/{id}/file-bindings
- BA-1006：新增 FileCleanupPanel.vue，清理说明/保留天数 input-number/候选统计刷新/软删除确认/回收站快捷入口；GET unbound-candidates + POST soft-delete-unbound
- 扩展 blade-admin/src/api/file.ts：新增 batchDeleteFiles/batchMoveFiles/getFileBindings/createFileBindings/deleteFileBinding/getUnboundCandidates/softDeleteUnbound/createFileFolder；FileUploadVO 新增 fileType/fileExt；新增 FileBindingVO/FileBindingCreateDTO/FileBatchDeleteDTO/FileBatchMoveDTO/FileFolderCreateDTO/UnboundCandidateVO 类型
- 扩展 blade-admin/src/api/product.ts：新增 ProductFileBindingDTO/SkuImageBindingDTO 和 setProductFileBindings()

**变更原因**：
- 完成 PC 文件中心 BA-1004~BA-1006 第一可用切片，补全上传、批量操作、商品/SKU 绑定和未绑定清理管理功能

**影响范围**：
- `blade-admin/src/api/file.ts`（扩展 API + 类型）
- `blade-admin/src/api/product.ts`（新增绑定 DTO + API）
- `blade-admin/src/views/files/index.vue`（上传/选择/批量操作/移动/删除/绑定/清理）
- `blade-admin/src/views/files/FileBindDialog.vue`（新建）
- `blade-admin/src/views/files/FileCleanupPanel.vue`（新建）
- `docs/03-TASKS.md`（BA-1004~BA-1006 标记完成 + 执行记录）
- `docs/05-CHANGELOG.md`（本记录）
- `docs/SESSION_CONTEXT.md`（更新摘要）

**验证结果**：
- `npm run build`（Node v22）通过，无 TypeScript 编译错误。`files-mySDnOsX.js`（35 kB gzip 10 kB）产出正常，包含所有新增组件逻辑。
- Codex 复核修正绑定风险判断和清理统计字段：`GET /api/files/{id}/bindings` 返回的即为有效绑定列表，不再读取不存在的 `deleted` 字段；`GET /api/files/cleanup/unbound-candidates` 使用后端真实字段 `candidateCount`。
- Codex 复核将未绑定清理默认保留期对齐为 7 天，并让回收站状态同时识别 `status=0` 与 `deletedTime`。

**执行人**：Claude Code

### [环境修正] - 本地后端默认数据库切回开发库

**变更内容**：
- 后端默认数据源从 `blade_project_prod` 切回开发库 `blade_project`，继续保留 `BLADE_DB_URL` / `BLADE_DB_USERNAME` / `BLADE_DB_PASSWORD` 覆盖能力。
- 修正 `application-test.yml` 的 MySQL 密码，从过期的 `root` 改为当前容器可用的 `root123`。
- 更新 `00-SETUP.md`：修正 Docker MySQL 创建命令、连接密码、开发库/本地生产库说明、数据库登录命令和两库切换方式。
- 更新 `SESSION_CONTEXT.md`：当前默认后端数据库改为 `blade_project`，本地生产库 `blade_project_prod` 仅保留用于真实/演示数据隔离。

**变更原因**：
- 文档中混杂旧配置（`root/root`、`blade`）和生产库默认连接，导致按文档无法登录数据库，并且日常开发误连本地生产库。

**影响范围**：
- `blade-backend/src/main/resources/application.yml`
- `blade-backend/src/test/resources/application-test.yml`
- `docs/00-SETUP.md`
- `docs/SESSION_CONTEXT.md`
- `docs/05-CHANGELOG.md`

**验证结果**：
- 当前 Docker MySQL 使用 `root/root123` 登录成功，`root/root` 登录失败。
- 当前容器内确认存在 `blade_project` 和 `blade_project_prod` 两个库。
- 后端按默认配置启动成功，Flyway 日志确认连接 `jdbc:mysql://localhost:3306/blade_project`，并将开发库从 V30 自动迁移到 V36。

### [功能开发] - BA-1001~BA-1003 PC 文件中心基础页面

**变更内容**：
- BA-1001: 新增 `/files` 路由、`menu:file` 权限、页面标题映射、优先页面映射。
- BA-1001: 新增布局侧边栏"文件中心"菜单项（folder 图标）。
- BA-1001: 新增 `V36__file_center_permissions.sql`，补齐 `menu:file` 及文件上传、删除、绑定、解绑、批量、查看全部、查看自己、清理等按钮权限，并默认分配给 `ROLE_OWNER`、`ROLE_ADMIN`。
- BA-1002: 左侧快捷入口（全部文件、未绑定、商品素材、SKU 图片、订单图片、入库凭证、视频、回收站），各入口映射到后端 FilePageDTO 查询参数。
- BA-1002: 集成 `GET /api/file-folders/tree` 真实文件夹树，支持多层级缩进展示。
- BA-1003: 网格视图（图片卡片缩略图、视频占位符、文件类型角标、绑定标记）和列表视图（el-table：预览/文件名/类型/大小/业务类型/绑定/来源/时间/状态）。
- BA-1003: 筛选栏：keyword 搜索、fileType 下拉、businessType 下拉、网格/列表切换。
- BA-1003: 分页（prev/pager/next）、loading 状态、空状态提示。
- BA-1003: 图片预览弹窗（大图 + 元数据信息面板）、视频/文档占位预览。
- 扩展 `blade-admin/src/api/file.ts`：新增 `FileVO`/`FileFolderVO`/`FilePageParams`/`PageResult` 类型和 `getFilePage`/`getFileFoldersTree`/`getFileDetail` 函数，以及 `formatFileSize`/`isImageFile`/`isVideoFile`/`getFileTypeLabel`/`getBusinessTypeLabel` 工具函数。

**变更原因**：
- 完成 PC 文件中心第一阶段前端基础，衔接后端已完成的 BE-1001~BE-1011 文件中心底座。

**影响范围**：
- `blade-admin/src/router/index.ts`（新增 /files 路由）
- `blade-admin/src/views/layout/index.vue`（新增菜单项和页面标题）
- `blade-admin/src/views/login/index.vue`（首个可访问页面优先级补充 /files）
- `blade-admin/src/api/file.ts`（扩展 API 层）
- `blade-admin/src/views/files/index.vue`（新增文件中心主页面）
- `blade-backend/src/main/resources/db/migration/V36__file_center_permissions.sql`（新增文件中心权限迁移）
- `docs/03-TASKS.md`（BA-1001~BA-1003 状态更新）
- `docs/05-CHANGELOG.md`（本记录）

**验证结果**：
- `npm run build`（Path: Node v22）通过，无 TypeScript 编译错误。`files-DPVLA6ub.js`（18 kB）和 `file-PWSDhYRK.js`（1.5 kB）产出正常。
- 后端启动时 Flyway 成功应用 `V36__file_center_permissions.sql`，`/api/auth/codes` 已返回 `menu:file` 和 `btn:file:*` 文件中心权限。
- Playwright 本地页面验证通过：登录后访问 `http://localhost:5777/files`，页面显示文件中心、快捷入口和文件列表，截图保存到 `blade-admin/test-results/file-center-page.png`。

### [功能开发] - BE-1011 文件中心回归测试与删除保护收口

**变更内容**：
- 文件中心批量删除增加有效绑定保护：当前租户存在 `file_business_bind.deleted=0` 绑定的文件，拒绝软删除。
- 新增 `FileBindingServiceImplTest.batchDelete_rejectsActiveBoundFiles`，锁定“已绑定文件不能被批量删除”的回归行为。
- 确认文件中心回归测试覆盖上传、列表/详情、绑定、文件夹、未绑定治理、清理标记、基础视频和私有预览权限。
- `BE-1011` 标记完成，文件中心后端 Phase 6.6 收口到回归测试通过。

**变更原因**：
- 文件中心 MVP 要求删除前识别有效绑定，避免订单、商品、SKU、入库凭证等业务文件被误删。
- 为后续 PC `/files` 页面和清理管理功能提供稳定后端边界。

**影响范围**：
- `blade-backend/src/main/java/com/blade/file/service/impl/FileBindingServiceImpl.java`
- `blade-backend/src/test/java/com/blade/file/FileBindingServiceImplTest.java`
- `docs/03-TASKS.md`
- `docs/SESSION_CONTEXT.md`

**验证结果**：
- `mvn test '-Dtest=FileBindingServiceImplTest' -DfailIfNoTests=false` 通过，13/13。
- `mvn test '-Dtest=File*Test' -DfailIfNoTests=false` 通过，96/96。

**执行人**：Claude Code 尝试，Codex 接手实现与验证

---

## 2026-06-03 变更记录

### [架构规划] - 文件中心与数字资产中心落地设计

**变更内容**：
- 新增 [12-FILE_CENTER_ASSET_DESIGN.md](./12-FILE_CENTER_ASSET_DESIGN.md)，将“相册池”升级定义为通用数字资产中心。
- 明确文件中心第一版边界：文件夹、图片/基础视频、未绑定文件治理、商品/SKU 绑定、订单/入库绑定、客户 iPad 现货展示页。
- 明确第一版不做：视频转码、分片上传、七牛云/NAS 切换、客户公开分享链接、AI 自动打标签、文档在线预览和文件版本管理。
- PRD 新增“文件中心与客户展示页”章节，锁定 fileId、`file_business_bind`、未绑定清理和 iPad 展示页库存口径。
- TASKS 新增 BE-1001~BE-1011、BE-1020~BE-1023、BA-1001~BA-1006、BA-1020~BA-1024。
- README、SESSION_CONTEXT、DECISIONS_LOG 和 09 文件存储设计文档补充新设计入口和边界说明。

**变更原因**：
- 系统后续需要统一管理来自订单、商品、SKU、入库、OCR 和外部相册迁移的图片/视频素材。
- 客户 iPad 现货展示页需要消费商品/SKU 图片和实时库存，不能继续依赖群晖相册或 iPad 本地相册。
- 为防止后续 Agent 将文件中心范围漂移到视频转码、对象存储、分享链接等非 MVP 能力，需要先锁定边界。

**影响范围**：
- `docs/12-FILE_CENTER_ASSET_DESIGN.md`
- `docs/02-PRD.md`
- `docs/03-TASKS.md`
- `docs/01-README.md`
- `docs/SESSION_CONTEXT.md`
- `docs/09-FILE_STORAGE_DESIGN.md`
- `docs/reference/DECISIONS_LOG.md`

**执行人**：AI

### [功能开发] - BE-1001 数字资产表结构扩展

**变更内容**：
- 新增 `V35__file_center_asset_schema.sql`：扩展 `file_storage`，新增文件夹、业务绑定、操作日志、清理日志表。
- `FileStorage` 新增 14 个资产字段：`folderId`、`fileType`、`fileExt`、`fileHash`、`source`、`purpose`、`bindCount`、`visibility`、`imageWidth`、`imageHeight`、`durationSeconds`、`coverFileId`、`deletedTime`、`purgedTime`。
- 新增 `FileFolder`、`FileBusinessBind`、`FileOperationLog`、`FileCleanupLog` 实体和对应 Mapper。
- 新增 `FileAssetSchemaTest`，用反射验证实体字段和 `@TableName` 映射。

**变更原因**：
- 为文件中心分页、文件夹、多业务绑定、未绑定治理和客户 iPad 展示页提供后端表结构基础。
- 保持现有上传/预览/软删除接口不变，先完成资产中心数据层扩展。

**影响范围**：
- `blade-backend/src/main/resources/db/migration/V35__file_center_asset_schema.sql`
- `blade-backend/src/main/java/com/blade/file/entity/*`
- `blade-backend/src/main/java/com/blade/file/mapper/*`
- `blade-backend/src/test/java/com/blade/file/FileAssetSchemaTest.java`
- `docs/03-TASKS.md`

**验证结果**：
- `mvn test -Dtest=FileAssetSchemaTest,FileControllerTest -DfailIfNoTests=false` 通过。

**执行人**：Hermes Agent 执行，Codex 审核

---

### [功能开发] - BE-1002 文件中心分页/详情 API

**变更内容**：
- 新增 FilePageDTO.java：分页查询 DTO，支持 keyword/folderId/fileType/businessType/bound/purpose/createBy/startDate/endDate/status 筛选
- 新增 FileVO.java：文件视图对象，含 bound 标志（基于 file_business_bind.deleted=0 实时判断）
- 新增 GET /api/files（FileController.list）：文件分页列表，委托 FileService.pageList(FilePageDTO)
- 新增 GET /api/files/{id}（FileController.detail）：文件详情，委托 FileService.getDetail(Long id)
- 更新 FileService 接口：新增 pageList(FilePageDTO) 和 getDetail(Long id) 方法
- 更新 FileServiceImpl：实现分页查询、详情查询、基于 `file_business_bind` 的 `businessType/bound` 过滤和 bound 标志填充
- 更新 FileControllerTest：新增 4 个测试用例（分页返回、筛选参数透传、默认分页、详情返回），CapturingFileService stub 补齐新方法

**变更原因**：
- BE-1002 需要文件中心的分页列表和详情接口作为前端页面的数据基础
- `businessType` 和 `bound` 筛选统一基于 `file_business_bind.deleted=0` 判断，不依赖 `bind_count` 字段或 `file_storage` 旧业务字段

**影响范围**：
- `blade-backend/src/main/java/com/blade/file/dto/FilePageDTO.java`（新建）
- `blade-backend/src/main/java/com/blade/file/dto/FileVO.java`（新建）
- `blade-backend/src/main/java/com/blade/file/service/FileService.java`（修改）
- `blade-backend/src/main/java/com/blade/file/service/impl/FileServiceImpl.java`（修改）
- `blade-backend/src/main/java/com/blade/file/controller/FileController.java`（修改）
- `blade-backend/src/test/java/com/blade/file/FileControllerTest.java`（修改）
- `docs/03-TASKS.md`（更新状态）
- `docs/05-CHANGELOG.md`（本次变更）

**验证结果**：
- `mvn test -Dtest=FileControllerTest,FileAssetSchemaTest -DfailIfNoTests=false`：28/28 测试通过

**执行人**：Hermes Agent 执行，Codex 审核

---

### [功能开发] - BE-1003 文件夹管理 API

**变更内容**：
- 新增 FileFolderCreateDTO：文件夹名称(必填)、parentId、sort
- 新增 FileFolderUpdateDTO：folderName、parentId、sort（均为可选）
- 新增 FileFolderVO：id、parentId、folderName、sort、children（树形结构）
- 新增 FileFolderService 接口 + FileFolderServiceImpl 实现
- 新增 FileFolderController：/api/file-folders/tree(GET)、/api/file-folders(POST)、/api/file-folders/{id}(PUT)、/api/file-folders/{id}(DELETE)
- 新增 FileFolderControllerTest：7 个测试用例覆盖 tree/create/update/delete 四个入口的参数透传和响应
- 新增 FileFolderServiceImplTest：4 个测试用例覆盖不存在、存在子文件夹、存在文件未移动、移动文件后软删除等删除规则
- Codex 复核后补强 update/delete 的 tenantId、deleted、status 过滤，避免跨租户或已删除数据被误操作
- 删除规则：验证存在→检查子文件夹阻止→检查文件→moveFilesToUnfiled=false 阻止/moveFilesToUnfiled=true 清空 folderId→软删除
- 树构建：查询全部后按 parentId 分组在内存中构建层级

**变更原因**：
- BE-1003 需要文件夹管理能力作为文件中心左侧树的基础

**影响范围**：
- `blade-backend/src/main/java/com/blade/file/dto/FileFolderVO.java`（新建）
- `blade-backend/src/main/java/com/blade/file/dto/FileFolderCreateDTO.java`（新建）
- `blade-backend/src/main/java/com/blade/file/dto/FileFolderUpdateDTO.java`（新建）
- `blade-backend/src/main/java/com/blade/file/service/FileFolderService.java`（新建）
- `blade-backend/src/main/java/com/blade/file/service/impl/FileFolderServiceImpl.java`（新建）
- `blade-backend/src/main/java/com/blade/file/controller/FileFolderController.java`（新建）
- `blade-backend/src/test/java/com/blade/file/FileFolderControllerTest.java`（新建）
- `blade-backend/src/test/java/com/blade/file/FileFolderServiceImplTest.java`（新建）
- `docs/03-TASKS.md`（更新状态）
- `docs/05-CHANGELOG.md`（本次变更）

**验证结果**：
- `mvn test -Dtest=FileFolderControllerTest,FileFolderServiceImplTest,FileControllerTest,FileAssetSchemaTest -DfailIfNoTests=false`：39/39 测试通过

**执行人**：Hermes Agent 执行，Codex 审核

---

### [功能开发] - BE-1004 + BE-1006 多业务绑定与文件批量操作 API

**变更内容**：
- 新增 FileBindingCreateDTO：fileIds、businessType、businessId、bindRole、isPrimary（@NotEmpty/@NotNull/@NotBlank 校验）
- 新增 FileBindingVO：绑定关系视图对象
- 新增 FileBatchDeleteDTO：fileIds
- 新增 FileBatchMoveDTO：fileIds、folderId
- 新增 FileBindingService 接口 + FileBindingServiceImpl 实现
- 新增 FileBindingController（/api/files），承载绑定和批量操作端点：
  - GET /api/files/{id}/bindings：查询文件的有效绑定关系
  - POST /api/files/bindings：批量绑定（验证文件存在→插入绑定记录→写操作日志）
  - DELETE /api/files/bindings/{id}：软删除绑定（验证绑定存在→deleted=1→写日志）
  - POST /api/files/batch-delete：批量软删除（status=0，只操作当前租户正常文件）
  - POST /api/files/batch-move：批量移动文件夹（验证文件夹存在→更新folder_id→写日志）
- 操作日志：bind、unbind、batch_delete、batch_move 四种类型写入 file_operation_log，带 operatorId/tenantId
- Codex 复核后补强解绑写操作的 `tenantId/deleted` 过滤、批量 DTO 的 `fileIds` 非空校验，以及 Controller 测试的批量参数透传断言

**变更原因**：
- BE-1004 需要多业务绑定 API 将文件与商品/SKU/订单/入库日志关联
- BE-1006 需要批量操作 API 作为文件中心管理功能的基础

**影响范围**：
- `blade-backend/src/main/java/com/blade/file/dto/FileBindingCreateDTO.java`（新建）
- `blade-backend/src/main/java/com/blade/file/dto/FileBindingVO.java`（新建）
- `blade-backend/src/main/java/com/blade/file/dto/FileBatchDeleteDTO.java`（新建）
- `blade-backend/src/main/java/com/blade/file/dto/FileBatchMoveDTO.java`（新建）
- `blade-backend/src/main/java/com/blade/file/service/FileBindingService.java`（新建）
- `blade-backend/src/main/java/com/blade/file/service/impl/FileBindingServiceImpl.java`（新建）
- `blade-backend/src/main/java/com/blade/file/controller/FileBindingController.java`（新建）
- `blade-backend/src/test/java/com/blade/file/FileBindingControllerTest.java`（新建，9 测试用例）
- `blade-backend/src/test/java/com/blade/file/FileBindingServiceImplTest.java`（新建，12 测试用例）
- `docs/03-TASKS.md`（更新 BE-1004/BE-1006 状态）
- `docs/05-CHANGELOG.md`（本次变更）

**验证结果**：
- `mvn test -Dtest=FileBindingControllerTest,FileBindingServiceImplTest,FileFolderControllerTest,FileFolderServiceImplTest,FileControllerTest,FileAssetSchemaTest -DfailIfNoTests=false`：60/60 测试通过
- Service 测试覆盖：getBindings 返回、createBindings 文件校验、createBindings 日志写入、deleteBinding 不存在、deleteBinding 软删除与日志、batchDelete 更新状态、batchDelete 空列表跳过、batchMove 文件夹不存在、batchMove null folderId、batchMove 正常移动
- Controller 测试覆盖：绑定查询/创建/删除、批量删除/移动参数透传、空 fileIds 校验

**执行人**：Hermes Agent 执行，Codex 审核

---

### [功能开发] - BE-1007 + BE-1008 未绑定文件治理与清理定时任务

**变更内容**：
- 新增 FileCleanupService 接口 + FileCleanupServiceImpl 实现：
  - countUnboundCandidates(days)：统计未绑定未归档超期文件数（基于 file_business_bind.deleted=0 判断）
  - softDeleteUnbound(days)：软删除候选文件（status=0, deletedTime=now），写入 file_cleanup_log
  - markPurged(days)：标记可清理文件（purgedTime=now），仅处理未绑定、未归档，且 purpose IN ('temp','ocr','import') 或无 purpose 的文件；不物理删除
- 新增 FileCleanupController（/api/files/cleanup/）：GET unbound-candidates、POST soft-delete-unbound、POST mark-purged
- 新增 FileCleanupScheduler：@ConditionalOnProperty 控制（默认 disabled），第一版按配置 tenant-id + cron 执行两步清理
- BladeApplication 新增 @EnableScheduling
- FileStorageProperties 新增 Cleanup 嵌套配置类（enabled=false 默认），支持 blade.file.cleanup.* 配置
- application.yml 新增 blade.file.cleanup.* 配置节（enabled、tenant-id、保留天数、cron）

**变更原因**：
- 文件中心需要自动清理未绑定临时文件和过期软删除元数据
- 安全保守策略：仅操作元数据不做物理删除；业务凭证文件自动保留

**影响范围**：
- `blade-backend/src/main/java/com/blade/BladeApplication.java`（修改：加 @EnableScheduling）
- `blade-backend/src/main/java/com/blade/file/config/FileStorageProperties.java`（修改：加 Cleanup 嵌套类）
- `blade-backend/src/main/resources/application.yml`（修改：加 cleanup 配置）
- `blade-backend/src/main/java/com/blade/file/service/FileCleanupService.java`（新建）
- `blade-backend/src/main/java/com/blade/file/service/impl/FileCleanupServiceImpl.java`（新建）
- `blade-backend/src/main/java/com/blade/file/controller/FileCleanupController.java`（新建）
- `blade-backend/src/main/java/com/blade/file/scheduler/FileCleanupScheduler.java`（新建）
- `blade-backend/src/test/java/com/blade/file/FileCleanupControllerTest.java`（新建，6 测试用例）
- `blade-backend/src/test/java/com/blade/file/FileCleanupServiceImplTest.java`（新建，7 测试用例）
- `docs/03-TASKS.md`（更新 BE-1007/BE-1008 状态）
- `docs/05-CHANGELOG.md`（本次变更）

**验证结果**：
- `mvn test '-Dtest=FileCleanup*Test,FileBindingControllerTest,FileBindingServiceImplTest,FileFolderControllerTest,FileFolderServiceImplTest,FileControllerTest,FileAssetSchemaTest' -DfailIfNoTests=false`：74/74 测试通过
- `git diff --check`：通过
- Service 测试覆盖：countUnbound 返回计数/零值、softDelete 状态保护更新+日志/空列表跳过、markPurged 绑定保护+日志写入/purgedTime 更新/空列表跳过、非法保留天数拒绝
- Controller 测试覆盖：3 个端点参数透传与默认值、响应结构

**执行人**：Hermes Agent 执行，Codex 审核并补齐状态/绑定/租户调度边界

### [功能开发] - BE-1005 商品/SKU 图片绑定服务

**变更内容**：
- 新增 `ProductFileBindingDTO` 和 `SkuImageBindingDTO`：请求 DTO 支持 mainFileId（主图）、galleryFileIds（图集）、skuImageBindings（SKU 图片绑定列表）。
- 新增 `PUT /api/products/{id}/file-bindings` 端点：支持替换语义的商品/SKU 图片绑定。
- 新增 `ProductService.bindFiles(Long productId, ProductFileBindingDTO dto)` 方法，在 `ProductServiceImpl` 实现：
  - mainFileId 存在时：软删除已有 product/main 绑定，插入新主图绑定（isPrimary=1），更新 product.imageUrl。
  - galleryFileIds 非 null 时：软删除已有 product/gallery 绑定，按提供的列表插入新图集绑定。空列表=清空图集。
  - skuImageBindings 非 null 时：逐 SKU 验证归属（productId、tenantId、status=1、deleted=0），软删除已有 sku/sku_image 绑定，插入新图片。
  - 批量文件验证：所有去重后的 fileId 必须在当前租户存在且 status=1。
  - 绑定写入带 tenantId、deleted=0、bindRole、isPrimary、sort、createBy。
- `create()` 和 `update()` 新增 `syncMainImageBinding()` 调用：当 product.imageUrl 为纯数字 fileId 时，自动同步到 file_business_bind（main/1）。非数字历史 URL 忽略不抛异常。
- `ProductServiceImpl` 新增 `FileBusinessBindMapper` 和 `FileStorageMapper` 依赖注入。

**变更原因**：
- BE-1005 需要商品主图/图集/SKU 图片统一走 file_business_bind 管理，支持替换语义的前端操作。

**影响范围**：
- `blade-backend/src/main/java/com/blade/product/controller/ProductController.java`（修改：新增端点）
- `blade-backend/src/main/java/com/blade/product/service/ProductService.java`（修改：新增 bindFiles 方法）
- `blade-backend/src/main/java/com/blade/product/service/impl/ProductServiceImpl.java`（修改：新增 bindFiles 实现 + syncMainImageBinding 辅助 + 依赖注入）
- `blade-backend/src/main/java/com/blade/product/dto/ProductFileBindingDTO.java`（新建）
- `blade-backend/src/main/java/com/blade/product/dto/SkuImageBindingDTO.java`（新建）
- `blade-backend/src/test/java/com/blade/product/ProductFileBindingServiceTest.java`（新建：11 测试用例）
- `blade-backend/src/test/java/com/blade/product/ProductFileBindingControllerTest.java`（新建：2 测试用例，验证 DTO 透传）
- `docs/03-TASKS.md`（更新状态）
- `docs/SESSION_CONTEXT.md`（更新接手快照）
- `docs/05-CHANGELOG.md`（本次变更）

**验证结果**：
- `mvn test '-Dtest=ProductFileBinding*Test,FileBindingControllerTest,FileBindingServiceImplTest,FileControllerTest,FileAssetSchemaTest' -DfailIfNoTests=false`：62/62 测试通过
- 包含 `ProductControllerTest` 的完整 SOW 命令已尝试运行，但本地 MySQL 连接失败导致 SpringBoot 上下文无法启动；新增 standalone controller 测试已覆盖本轮接口透传。
- `git diff --check`：通过
- Service 测试覆盖：mainFileId 替换主图+更新 imageUrl、重复 fileId 去重验证、gallery 空列表清空、gallery 正常插入、SKU 归属校验失败、SKU 正常绑定、SKU 空列表清空、商品不存在、文件不存在、三角色同时操作、非数字历史 URL 不写绑定
- Controller 测试覆盖：path productId + main/gallery/sku DTO 透传、空图集清空语义透传

**执行人**：Hermes Agent 执行，Codex 审核并补齐重复 fileId、主图替换、createBy、Controller DTO 透传和文档边界

### [功能开发] - BE-1010 基础视频文件支持

**变更内容**：
- FileStorageProperties 默认 allowedTypes 新增 video/mp4、video/webm、video/quicktime。
- application.yml 运行配置同步放行 video/mp4、video/webm、video/quicktime，避免默认属性被配置覆盖后运行时仍拒绝视频上传。
- FileServiceImpl.upload 自动分类：contentType 以 image/ 开头→IMAGE，video/ 开头→VIDEO，其他→OTHER；文件扩展名从原始文件名提取（小写无点）。
- FileUploadVO 新增 fileType 和 fileExt 字段，上传响应带这两个字段。
- 无 DB 迁移、无转码/封面/Range/分片/物理删除。

**变更原因**：
- BE-1010 需要基础视频上传和预览能力；后续转码/封面等单独立项。

**影响范围**：
- FileStorageProperties.java、FileServiceImpl.java、FileUploadVO.java
- application.yml
- FileVideoSupportTest.java（新建：6 测试用例）
- FileAllowedTypesRegressionTest.java（新增：1 个 application.yml allowed-types 配置漂移回归测试）
- FileControllerTest.java（补充上传响应 fileType/fileExt 和视频响应断言）

**验证结果**：
- `mvn test '-Dtest=FileVideoSupportTest,FileControllerTest,FileAssetSchemaTest,FileCleanup*Test' -DfailIfNoTests=false`：49/49 测试通过
- `mvn test '-Dtest=File*Test' -DfailIfNoTests=false`：82/82 测试通过

**执行人**：Hermes Agent 执行，Codex 审核并补齐运行配置和 Controller 上传响应断言；Claude Code 执行小范围配置漂移回归测试，Codex 复核并修正报告口径

### [功能开发] - BE-1009A 文件预览 PUBLIC/PRIVATE 基础收口

**变更内容**：
- `FileController.preview` 新增可见性校验：`visibility=PUBLIC` 可匿名预览；`PRIVATE` 或 `null` 需要已认证用户。
- 预览端点仍保留 SecurityConfig 的 `permitAll` 入口能力，避免商品公开图无法匿名访问；权限判断下沉到 Controller。
- 登录判断改为基于 Spring Security `Authentication.isAuthenticated()` 且排除 `AnonymousAuthenticationToken`，不绑定业务 `User` 实体。
- `FileControllerTest` 新增 4 个预览访问控制用例：PUBLIC 匿名成功、PRIVATE 匿名拒绝、null visibility 匿名拒绝、PRIVATE 登录成功。
- 未改 DB 迁移、未改 SecurityConfig、未做分享 token、未做物理删除、未做前端改造。

**变更原因**：
- BE-1009 要求商品公开图可 `PUBLIC`，私有文件预览必须校验登录、租户和业务权限。本次先完成 PUBLIC/PRIVATE 登录边界，业务权限映射后续继续。

**影响范围**：
- `blade-backend/src/main/java/com/blade/file/controller/FileController.java`
- `blade-backend/src/test/java/com/blade/file/FileControllerTest.java`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`

**验证结果**：
- `mvn test '-Dtest=FileControllerTest' -DfailIfNoTests=false`：11/11 测试通过
- `mvn test '-Dtest=File*Test' -DfailIfNoTests=false`：86/86 测试通过

**剩余事项**：
- BE-1009 仍未完全完成：还需要定义并实现私有文件的业务权限映射，例如订单图、入库凭证、商品素材分别对应哪些权限码。

**执行人**：Claude Code 执行初稿，Codex 复核并修正认证主体判断、测试预期和文档状态

### [功能开发] - BE-1009B 文件预览业务权限映射

**变更内容**：
- `FileController` 新增业务权限映射 `BUSINESS_PERMISSION_MAP`：`product/sku → menu:product`、`order → btn:order:view`、`inventory_log → btn:inventory:viewLog`、`ocr_document → menu:file`。
- 非 PUBLIC 文件在登录校验通过后，增加 `checkBusinessPermission(FileStorage)` 方法进行业务权限校验。
- `btn:file:viewAll` 绕过所有业务权限映射，直接放行。
- 有绑定的文件：查询 `file_business_bind`（deleted=0），任一绑定业务类型的映射权限匹配即放行；若绑定业务类型均未映射，则视为 temp/unknown，继续走 viewOwn 判断。
- 无绑定的文件：回退到 `file_storage.businessType`，若仍无匹配则视为 unbound。
- unbound/temp/unknown：仅 `btn:file:viewOwn` 可访问，且要求 `Authentication.getPrincipal()` 是 `com.blade.system.user.entity.User` 实例且 `userId.equals(file.createBy)`，不回退到默认 userId=1。
- `FileService` 新增 `getActiveBindings(Long fileId)` 方法。
- `FileControllerTest` 新增 8 个业务权限测试用例 + 修复 1 个已有测试的权限上下文。

**变更原因**：
- BE-1009 要求私有文件预览必须校验业务权限。BE-1009A 已完成 PUBLIC/PRIVATE 登录边界，本次完成业务权限映射部分，BE-1009 现已完整满足登录+租户+业务权限三项校验。

**影响范围**：
- `blade-backend/src/main/java/com/blade/file/controller/FileController.java`
- `blade-backend/src/main/java/com/blade/file/service/FileService.java`
- `blade-backend/src/main/java/com/blade/file/service/impl/FileServiceImpl.java`
- `blade-backend/src/test/java/com/blade/file/FileControllerTest.java`
- `docs/03-TASKS.md`
- `docs/05-CHANGELOG.md`

**验证结果**：
- `mvn test '-Dtest=FileControllerTest' -DfailIfNoTests=false`：20/20 测试通过
- `mvn test '-Dtest=File*Test' -DfailIfNoTests=false`：95/95 测试通过

**执行人**：Claude Code 执行，Codex 复核并补齐 temp/unknown 绑定边界与最终测试口径

### [功能开发] - Agent 颜色尺码结构数据包

**变更内容**：
- 新增 `GET /api/agent/analytics/sku-mix`，基于现有商品详情分析输出同款 SKU、颜色、尺码销售结构事实。
- 新增 `AgentSkuMixDTO` 和 `AgentSkuMixService`，返回 `skus`、`colors`、`sizes`、`reasons`。
- 每个结构行新增 `signal`，当前表示销售结构：`HOT` / `NORMAL` / `LOW`。
- 接口要求 `agent:analytics:read`，不返回成本、毛利、毛利率。
- 更新外部 Agent 接入指南和 API 文档，明确缺货、积压和补货优先级由 BE-558 库存建议接口承接。
- 更新 Agent 接入任务状态，BE-555 标记为完成。

**变更原因**：
- 用户希望 Agent 能看出同款下哪些颜色、尺码、SKU 表现好或表现弱，避免只看商品总销量。

**影响范围**：
- `blade-backend/src/main/java/com/blade/agent/dto/AgentSkuMixDTO.java`
- `blade-backend/src/main/java/com/blade/agent/service/AgentSkuMixService.java`
- `blade-backend/src/main/java/com/blade/agent/controller/AgentAnalyticsController.java`
- `blade-backend/src/test/java/com/blade/agent/AgentSkuMixServiceTest.java`
- `docs/03-TASKS.md`
- `docs/11-AGENT_ACCESS_GUIDE.md`
- `docs/reference/API_SPEC.md`

**验证结果**：
- `mvn -q -Dtest=AgentAuthenticationFilterTest,AgentStyleTrendServiceTest,AgentKeyAuthenticationServiceTest,AgentCallAuditServiceTest,AgentSkuMixServiceTest test` 通过。
- `mvn -q -DskipTests compile` 通过。
- `git diff --check` 通过。

**执行人**：AI

---

### [功能开发] - Agent 款式趋势多周期数据包

**变更内容**：
- `GET /api/agent/analytics/style-trends` 新增 `comparePeriods` 参数，默认对比 3 个周期，当前限制 1-6。
- 款式趋势数据包从单周期销售排行升级为多周期事实序列，返回 `periodSeries`。
- 新增趋势标签 `GROWING` / `STABLE` / `DECLINING` / `INSUFFICIENT_DATA`。
- 新增建议字段 `KEEP` / `WATCH` / `REDUCE` 和 `reasons`，为 Agent 判断“持续向好/走弱/减少投入”提供结构化依据。
- 更新外部 Agent 接入指南和 API 文档，明确当前趋势依据尚未叠加库存、客户覆盖面和利润事实。
- 更新 Agent 接入任务状态，BE-554 从部分完成调整为完成。

**变更原因**：
- 用户希望 Agent 能分析哪些款持续向好、哪些款不建议继续做；单周期销售排行不足以支持趋势判断，需要至少多周期事实和可解释标签。

**影响范围**：
- `blade-backend/src/main/java/com/blade/agent/dto/AgentStyleTrendDTO.java`
- `blade-backend/src/main/java/com/blade/agent/service/AgentStyleTrendService.java`
- `blade-backend/src/main/java/com/blade/agent/controller/AgentAnalyticsController.java`
- `blade-backend/src/test/java/com/blade/agent/AgentStyleTrendServiceTest.java`
- `docs/03-TASKS.md`
- `docs/11-AGENT_ACCESS_GUIDE.md`
- `docs/reference/API_SPEC.md`

**验证结果**：
- `mvn -q -Dtest=AgentAuthenticationFilterTest,AgentStyleTrendServiceTest,AgentKeyAuthenticationServiceTest,AgentCallAuditServiceTest test` 通过。
- `mvn -q -DskipTests compile` 通过。
- `git diff --check` 通过。

**执行人**：AI

---

### [功能开发] - Agent Gateway 调用审计

**变更内容**：
- 新增 V34 Agent 调用日志表，成功的 Agent 请求会记录 key 前缀、租户、路径、状态码、耗时、来源 IP 和 User-Agent，不记录原始 Agent Key。
- `AgentAuthenticationFilter` 在认证成功并完成请求后写入调用审计事件。
- 新增 `AgentCallAuditService`，写入调用日志并同步更新 Agent Key 最近使用时间/IP。
- 更新 Agent 接入任务状态，BE-553 从部分完成调整为完成。
- 更新外部 Agent 接入指南，补充调用审计和最近使用信息已实现。

**变更原因**：
- 外部 Agent 接入需要可追踪的调用来源、调用路径和最近使用信息，便于排查异常调用、后续限流和真实接入验证。

**影响范围**：
- `blade-backend/src/main/java/com/blade/agent/auth/AgentAuthenticationFilter.java`
- `blade-backend/src/main/java/com/blade/agent/auth/AgentCallAuditEvent.java`
- `blade-backend/src/main/java/com/blade/agent/auth/AgentCallAuditRecorder.java`
- `blade-backend/src/main/java/com/blade/agent/service/AgentCallAuditService.java`
- `blade-backend/src/main/java/com/blade/agent/entity/AgentCallLog.java`
- `blade-backend/src/main/java/com/blade/agent/mapper/AgentCallLogMapper.java`
- `blade-backend/src/main/resources/db/migration/V34__agent_call_log.sql`
- `blade-backend/src/test/java/com/blade/agent/AgentAuthenticationFilterTest.java`
- `blade-backend/src/test/java/com/blade/agent/AgentCallAuditServiceTest.java`
- `docs/03-TASKS.md`
- `docs/11-AGENT_ACCESS_GUIDE.md`

**验证结果**：
- `mvn -q -Dtest=AgentAuthenticationFilterTest,AgentStyleTrendServiceTest,AgentKeyAuthenticationServiceTest,AgentCallAuditServiceTest test` 通过。
- `mvn -q -DskipTests compile` 通过。
- `git diff --check` 通过。

**执行人**：AI

---

## 2026-05-22 变更记录

### [功能开发] - Agent Gateway 首个后端切片

**变更内容**：
- 新增 [11-AGENT_ACCESS_GUIDE.md](./11-AGENT_ACCESS_GUIDE.md)，给外部 Agent 接入方提供鉴权、当前接口、工具封装和安全检查清单。
- 新增 `agent` 后端模块的首个只读入口 `GET /api/agent/analytics/style-trends`，以商品销售排行事实包作为款式趋势分析的第一版数据输入。
- 新增 V33 Agent Key 表，key 采用公开 prefix + secret 哈希保存，凭证绑定租户并把逗号分隔 scope 映射为 `agent:*` authority，过期 key 会在认证阶段拒绝。
- 新增 `X-Agent-Key` 认证过滤器，Agent 请求认证后写入 `TenantContext`，趋势接口要求 `agent:analytics:read`。
- 收紧客户接口安全边界，`/api/customers/**` 不再位于 Spring Security 公开放行列表。
- 增加 Agent 趋势事实包、Agent Key 认证服务、HTTP 认证过滤器 focused tests，并补充本轮 Superpowers 实现计划。

**变更原因**：
- Agent 第一阶段需要先建立独立、只读、租户隔离的 Gateway 链路，再继续扩展客户跟进、周期报告和 WhatsApp 信息分析。

**影响范围**：
- `blade-backend/src/main/java/com/blade/agent/**`
- `blade-backend/src/main/java/com/blade/config/SecurityConfig.java`
- `blade-backend/src/main/resources/db/migration/V33__agent_gateway_keys.sql`
- `blade-backend/src/test/java/com/blade/agent/**`
- `docs/superpowers/plans/2026-05-22-agent-gateway-style-trends.md`
- `docs/11-AGENT_ACCESS_GUIDE.md`
- `docs/03-TASKS.md`
- `docs/reference/API_SPEC.md`

**验证结果**：
- `mvn -q -Dtest=AgentAuthenticationFilterTest,AgentStyleTrendServiceTest,AgentKeyAuthenticationServiceTest test` 通过。
- `mvn -q -DskipTests compile` 通过。
- `git diff --check` 通过。

**执行人**：AI

---

### [需求规划] - 外部 AI Agent 对接第一版

**变更内容**：
- 新增 [10-AGENT_INTEGRATION_DESIGN.md](./10-AGENT_INTEGRATION_DESIGN.md)，明确外部 Agent 不直连数据库，第一期采用只读 Agent Gateway。
- PRD 新增外部 AI Agent 对接章节，锁定款式趋势判断、颜色尺码结构、客户跟进/风险、库存建议事实、周期经营分析数据包和统一搜索。
- TASKS 新增 BE-551~BE-565，覆盖 Agent 安全边界复核、独立凭证、scope、多租户接入、款式趋势、颜色尺码结构、客户经营、库存建议、周期报告、定时提醒验证、限流、真实 Agent 验证、统一事件日志设计、WhatsApp 接入方案验证和后续能力路线评审。
- Agent 专项设计补充能力地图和分层路线，记录商品经营、库存与供应、订单运营、利润分析、沟通反馈、异常检测和经营记忆等后续方向。
- 需求讨论记录新增需求 #006，入口文档和会话上下文增加 Agent 对接设计索引。

**变更原因**：
- 用户希望把外部 Agent 接入 BladeProject 的方案沉淀为正式需求，并基于当前系统事实筛选参考架构中可复用的部分。

**影响范围**：
- `docs/10-AGENT_INTEGRATION_DESIGN.md`
- `docs/02-PRD.md`
- `docs/03-TASKS.md`
- `docs/04-REQUISITION_LOG.md`
- `docs/01-README.md`
- `docs/SESSION_CONTEXT.md`
- `docs/reference/API_SPEC.md`
- `docs/reference/PROJECT_STRUCTURE.md`

**执行人**：AI

---

## 2026-05-21 变更记录

### [功能优化] - 订单编辑图片上传交互

**变更内容**：
- PC 订单列表编辑弹窗移除原始 `images` JSON 文本框，改为图片墙展示。
- 编辑弹窗打开时解析已有 fileId 和历史 URL 图片，支持预览、追加上传和移除。
- 上传新图时在当前图片集合后追加 fileId，不再覆盖编辑前保留的历史图片值。
- 上传按钮在请求进行中显示上传状态，保存时仍统一提交到 `editForm.images`。
- 上传 E2E 增加订单编辑弹窗回归，验证上传后预览图已加载。

**变更原因**：
- 编辑订单时要求用户手工理解 fileId JSON 不适合业务录入，且旧交互无法直接查看和管理已上传图片。

**影响范围**：
- `blade-admin/src/views/orders/index.vue`
- `blade-admin/src/api/file.ts`
- `blade-admin/e2e-file-upload.spec.ts`
- `docs/03-TASKS.md`

**验证结果**：
- `npm run build` 通过。
- `npx playwright test e2e-file-upload.spec.ts --project=chromium` 通过，商品主图和订单编辑图片上传均可预览。

**执行人**：AI

---

### [Bug修复] - 商品主图上传后前端不显示

**变更内容**：
- 修复 PC 文件预览解析器：单图字段保存单个 fileId 字符串时，`parseImageSources("3")` 现在会生成 `/api/files/3/preview`，不再被误判为空数组。
- `parseFileIds` 同步支持单个 JSON 标量 fileId，保持单图字段和多图数组字段行为一致。
- 收紧商品主图上传 E2E，除断言上传接口返回 fileId 外，增加预览 `<img>` 可见、预览地址匹配、图片实际加载成功断言。

**变更原因**：
- 商品主图字段保存的是单个 fileId。旧解析逻辑对 `JSON.parse("3")` 的数字结果只按数组处理，导致上传成功但表单和列表拿不到预览地址。

**影响范围**：
- `blade-admin/src/api/file.ts`
- `blade-admin/e2e-file-upload.spec.ts`
- `docs/03-TASKS.md`

**验证结果**：
- `npm run build` 通过。
- `npx playwright test e2e-file-upload.spec.ts --project=chromium` 通过，商品主图上传后预览图已加载。

**执行人**：AI

---

### [功能优化] - 快速录单补齐订单图片上传

**变更内容**：
- PC 快速录单页在结算与配送区新增订单图片入口，支持多图上传、图片墙预览和移除。
- 快速录单复用统一文件接口上传订单图片，创建订单时将 fileId JSON 数组字符串写入 `sale_order.images`。
- 扩展 PC 文件上传 Playwright 回归，覆盖快速录单上传后预览图可加载且页面无系统错误。

**变更原因**：
- 快速录单此前缺少订单图片入口，导致纸质单据录入与标准新建订单的图片能力不一致。

**影响范围**：
- `blade-admin/src/views/orders/quick.vue`
- `blade-admin/e2e-file-upload.spec.ts`
- `docs/03-TASKS.md`

**执行人**：AI

---

## 格式

```markdown
### [日期] {变更类型} - {变更标题}

**变更内容**：{具体变更了什么}
**变更原因**：{为什么变更}
**影响范围**：{影响哪些模块/功能}
**执行人**：AI / 用户
```

---

## 2026-05-20 变更记录

### [Bug修复] - 图片上传系统错误

**变更内容**：
- 定位上传接口返回 500 的原因：后端服务仍停留在 V31 数据库结构，生产库缺少 `file_storage` 表。
- 重启后端并执行 Flyway V32，`file_storage` 表已创建，`POST /api/files/upload` 可正常返回 fileId。
- 新增后端 `FileControllerTest`，覆盖 multipart 上传返回 fileId 和预览地址。
- 新增 PC 管理端 `e2e-file-upload.spec.ts`，真实浏览器验证商品主图上传走统一文件接口且不出现系统错误。

**变更原因**：
- 统一文件存储代码已合入，但运行中的后端未重启，数据库迁移未执行，导致上传插入文件记录失败。

**影响范围**：
- `blade-backend/src/test/java/com/blade/file/FileControllerTest.java`
- `blade-admin/e2e-file-upload.spec.ts`
- `docs/03-TASKS.md`

**验证结果**：
- `mvn -q -Dtest=FileControllerTest test` 通过。
- `npx playwright test e2e-file-upload.spec.ts --project=chromium` 通过。
- 直接调用 `POST /api/files/upload` 返回 `code=200` 和 fileId。

**执行人**：AI

### [架构规划] - 统一文件存储与图片上传方案

**变更内容**：
- 新增 [09-FILE_STORAGE_DESIGN.md](./09-FILE_STORAGE_DESIGN.md)，明确统一文件入口、业务保存 fileId、本地存储第一版、后续可切七牛云/NAS 的整体方案。
- PRD 中订单图片和入库凭证字段说明从直接保存 URL 调整为保存 fileId JSON 数组字符串。
- OCR 拍照录单的图片上传任务调整为复用统一文件接口，不再单独设计孤立上传能力。
- TASKS 新增统一文件存储任务组：BE-901~BE-906、BA-901~BA-903、FE-901。
- SESSION_CONTEXT 和 README 增加统一文件存储设计入口。
- 已完成统一文件存储第一版开发：V32 文件表、本地上传/预览/软删除/绑定接口、PC 订单图片上传、PC/移动端入库图片上传、PC 商品主图上传。

**统一接入清单**：
| 业务入口 | 保存字段 | 保存形式 | 状态 |
|----------|----------|----------|------|
| PC 新建订单图片 | `sale_order.images` | fileId JSON 数组字符串 | ✅ 已完成 |
| PC 编辑订单图片 | `sale_order.images` | fileId JSON 数组字符串 | ✅ 已完成 |
| PC 订单详情图片预览 | `/api/files/{id}/preview` | fileId 预览地址 | ✅ 已完成 |
| PC 入库凭证图片 | `inventory_log.images` | fileId JSON 数组字符串 | ✅ 已完成 |
| 移动端入库凭证图片 | `inventory_log.images` | fileId JSON 数组字符串 | ✅ 已完成 |
| PC 商品主图 | `product.image_url` | 单个 fileId 字符串 | ✅ 已完成 |
| OCR 原始单据图片 | 后续 OCR 业务表 | fileId | ⏳ OCR 功能未开发，统一入口可复用 |

**变更原因**：
- 当前代码只具备图片字段和部分前端临时预览，缺少真实上传、长期保存和统一访问链路。
- 业务表保存 fileId 可以降低后续从本地迁移到七牛云或 NAS 的重构成本。

**影响范围**：
- `docs/09-FILE_STORAGE_DESIGN.md`
- `docs/02-PRD.md`
- `docs/03-TASKS.md`
- `docs/01-README.md`
- `docs/SESSION_CONTEXT.md`
- `blade-backend/src/main/java/com/blade/file/`
- `blade-backend/src/main/resources/db/migration/V32__file_storage.sql`
- `blade-admin/src/api/file.ts`
- `blade-mobile/src/api/file.ts`

**执行人**：AI

---

## 2026-05-13 变更记录

### [Bug修复] - 前端 Material 图标英文裸露

**变更内容**：
- 移除 PC 管理端对 Google Material Symbols 字体的强依赖。
- 新增本地 SVG 图标 fallback，将现有 `material-symbols-outlined` 图标名在运行时转换为内联 SVG。
- 修复订单列表、侧边栏、顶部导航、按钮等位置出现 `dashboard`、`download`、`edit` 等英文图标名的问题。

**变更原因**：
- 本地开发环境无法稳定加载 Google 字体时，Material Symbols ligature 不生效，浏览器会直接展示图标名称文本。

**影响范围**：
- `blade-admin/src/main.ts`
- `blade-admin/src/styles/main.css`
- `blade-admin/src/utils/materialIconFallback.ts`

**执行人**：AI

---

## 2026-05-05 变更记录

### [Bug修复] - 保持登录 30 天生效

**变更内容**：
- 登录页“保持登录状态 (30天)”从仅前端勾选项改为真实影响后端 token 策略。
- 前端登录请求新增 `remember` 参数，并默认勾选保持登录。
- access token 有效期调整为 1 小时。
- 后端新增 `jwt.remember-refresh-expiration=2592000000`，勾选保持登录时 refresh token 有效期为 30 天；未勾选时仍使用默认 7 天。
- refresh token 续期时保留 remember 标记，后续续期继续按 30 天策略滚动。
- 前端请求拦截器新增主动续期：业务请求发出前解析 access token 过期时间，若已过期或 10 分钟内过期，先 refresh 再提交原请求。

**变更原因**：
- 原实现 access token 30 分钟、refresh token 7 天，登录页 30 天文案未真正生效，长时间录单后可能出现登录异常。
- 原前端只在请求返回 401/403 后被动 refresh，关键提交请求可能正好撞上 token 过期窗口。

**影响范围**：
- `blade-admin/src/views/login/index.vue`
- `blade-admin/src/api/auth.ts`
- `blade-admin/src/api/client.ts`
- `blade-backend/src/main/java/com/blade/auth/dto/LoginRequest.java`
- `blade-backend/src/main/java/com/blade/auth/controller/LoginController.java`
- `blade-backend/src/main/java/com/blade/auth/service/AuthService.java`
- `blade-backend/src/main/java/com/blade/auth/service/JwtTokenProvider.java`
- `blade-backend/src/main/resources/application.yml`

**执行人**：AI

### [功能开发] - 数据分析页 v1

**变更内容**：
- 新增独立 PC 页面 `/analytics`，菜单为“数据分析”，用于经营决策分析。
- 新增 `menu:analytics` 菜单权限与 `data:analytics:profit` 毛利数据权限；老板/系统管理员默认可见毛利，销售员默认只看销售额、销量、订单数等基础指标。
- 新增 `/api/analytics/summary`、`/trend`、`/product-ranking`、`/product-detail` 接口，支持按商品、SKU、颜色、尺码维度分析。
- 现有仪表盘保留概览定位，只增加“查看数据分析”入口。

**变更原因**：
- 现有仪表盘偏概览，缺少可用于经营决策的维度拆解和商品排行明细。

**影响范围**：
- `blade-backend/src/main/java/com/blade/analytics/`
- `blade-backend/src/main/resources/db/migration/V31__analytics_permissions.sql`
- `blade-admin/src/views/analytics/index.vue`
- `blade-admin/src/api/analytics.ts`
- `blade-admin/src/router/index.ts`
- `blade-admin/src/views/layout/index.vue`

**执行人**：AI

### [Bug修复] - 订单列表编辑保存 ID 校验

**变更内容**：
- 修复订单列表点击“编辑”后保存提示“订单ID不能为空”的问题。
- 后端更新订单接口改为以路径参数 `/orders/{id}` 为订单 ID 来源，不再要求请求体必须携带 `id` 才能通过参数校验。
- 前端 `updateOrder` 请求体同步补充 `id` 字段，兼容现有接口处理逻辑。

**变更原因**：
- `@Valid` 在 Controller 内部 `dto.setId(id)` 之前执行，请求体未带 `id` 时会提前触发 `OrderUpdateDTO.id` 的非空校验。

**影响范围**：
- `blade-backend/src/main/java/com/blade/order/dto/OrderUpdateDTO.java`
- `blade-admin/src/api/order.ts`

**执行人**：AI

### [功能优化] - 仪表盘统计卡片排序

**变更内容**：
- 仪表盘第一行跟随上方日期范围动态展示：周期订单、周期销售额、周期毛利、周期销量，卡片标题同步切换为“今日/本周/本月”等周期文案。
- 将原“平均客单价”卡片替换为“销量”卡片，销量按当前周期内已产生收款订单的商品明细数量汇总。
- 仪表盘第二行固定展示：本周订单、本周销售额、商品数量、待处理订单。
- 仪表盘第三行固定展示：库存周转率、低库存预警、库存总量、库存积压预警。
- 前端移除额外今日统计请求，第一行恢复使用当前筛选周期的统计数据。

**变更原因**：
- 按经营关注优先级重新排列仪表盘指标，同时保留顶部周期筛选对第一行核心经营指标的联动能力。

**影响范围**：
- `blade-backend/src/main/java/com/blade/dashboard/dto/DashboardStatsDTO.java`
- `blade-backend/src/main/java/com/blade/dashboard/service/impl/DashboardServiceImpl.java`
- `blade-admin/src/views/dashboard/index.vue`
- `blade-admin/src/api/dashboard.ts`

**执行人**：AI

### [功能优化] - 仪表盘毛利统计

**变更内容**：
- 移除仪表盘“平均在库天数”统计卡片和 `avgDaysInStock` 字段。
- `GET /api/dashboard/stats` 新增当前周期毛利、本周毛利及对应环比字段。
- 毛利统计沿用订单统计口径：按 `order_date`、已产生收款订单统计，金额为 `max(gross_profit - refund_amount, 0)`。
- 前端第一行统计卡片展示当前筛选周期毛利，库存相关指标保留在第三行展示。

**变更原因**：
- 平均在库天数当前业务价值不高，管理端更需要直接查看销售毛利。

**影响范围**：
- `blade-backend/src/main/java/com/blade/dashboard/dto/DashboardStatsDTO.java`
- `blade-backend/src/main/java/com/blade/dashboard/dto/InventoryStatsVO.java`
- `blade-backend/src/main/java/com/blade/dashboard/service/impl/DashboardServiceImpl.java`
- `blade-admin/src/views/dashboard/index.vue`
- `blade-admin/src/api/dashboard.ts`

**执行人**：AI

### [功能优化] - 仪表盘订单统计口径调整

**变更内容**：
- 仪表盘订单统计日期口径改为 `order_date`，旧数据为空时回退 `create_time`。
- 订单数、销售额、趋势图、热销商品和状态分布统一只统计已产生收款订单：`paid_amount > 0` 或 `payment_status in (1, 2)`。
- 销售额改为应收净额：`max(total_amount - refund_amount, 0)`。
- 状态分布补齐 `7=退货中`、`8=已退货`。

**变更原因**：
- 仪表盘需要反映真实经营订单，定金订单和未发货的已付款订单也应纳入统计，退款订单按净额体现。

**影响范围**：
- `blade-backend/src/main/java/com/blade/dashboard/service/impl/DashboardServiceImpl.java`
- `blade-backend/src/test/java/com/blade/dashboard/DashboardServiceTest.java`
- `docs/02-PRD.md`
- `docs/reference/API_SPEC.md`

**执行人**：AI

### [数据补充] - 国家区号补充利比里亚

**变更内容**：
- 国家区号选择器新增 `Liberia / 利比里亚 / +231`。
- 核对并补齐非洲国家区号，覆盖 54 个非洲主权国家；新增贝宁、布隆迪、佛得角、中非共和国、乍得、科摩罗、吉布提、赤道几内亚、厄立特里亚、斯威士兰、加蓬、冈比亚、几内亚、几内亚比绍、莱索托、马拉维、毛里塔尼亚、尼日尔、圣多美和普林西比、塞舌尔、塞拉利昂、索马里、南苏丹、多哥等。
- 补充 `CF` 中非共和国旗帜映射，避免国家选择器显示默认旗帜。

**变更原因**：
- 快速录单和客户建档需要支持更完整的非洲客户电话区号。

**影响范围**：
- `blade-admin/src/data/countries.ts`

**执行人**：AI

### [功能优化] - 快速录单连续录入细节

**变更内容**：
- “保存并录下一单”后，纸质单号按上一单末尾数字自动 +1，并回填到下一单的单据信息中。
- 快速录单提交时，如果客户名称、电话、地址均为空，自动使用已建档的 `散客用户 / 88888888`。
- SKU 商品明细的数量输入框初始为空，只有录入数量后才参与应收、成本、毛利和提交校验。

**变更原因**：
- 贴合纸质订单连续录入习惯，减少重复输入，并避免数量字段默认值误导录单。

**影响范围**：
- `blade-admin/src/views/orders/quick.vue`

**执行人**：AI

### [功能优化] - 快速录单客户建档一体化

**变更内容**：
- 快速录单客户信息区新增国家区号选择，默认 `+86`。
- 修复国家区号组件在快速录单中无法选择的问题：将外层原生 `label` 改为普通容器，避免弹层点击事件被 label 干扰。
- 老客户可通过客户名称下拉筛选选择，选中后自动回填国家区号、电话和地址。
- 新客户可直接填写国家区号、电话、客户名称和地址；保存订单前会先自动创建客户，再将新客户 ID 带入订单创建。
- “保存并录下一单”后客户区恢复默认国家区号 `+86`。

**变更原因**：
- 将客户模块的新建客户能力融入快速录单流程，减少纸质订单录入时来回切换客户管理页面。

**影响范围**：
- `blade-admin/src/views/orders/quick.vue`

**执行人**：AI

### [Bug修复] - 登录过期自动续期

**变更内容**：
- 前端保存登录接口返回的 `refreshToken`。
- Axios 响应拦截器在请求返回 401/403 且存在 refresh token 时，自动调用 `/api/auth/refresh` 刷新 access token，并重试原请求。
- 续期失败时统一清理 `token`、`refreshToken`、`userInfo`、`permissions` 并跳转登录页。
- 后端刷新 token 时同步写入新 access token 和新 refresh token 的租户缓存，避免续期后权限/租户上下文丢失。

**变更原因**：
- 原 access token 有效期为 30 分钟，长时间停留页面后请求会过期，前端未续期导致页面变成无权限或被迫重新登录。

**影响范围**：
- `blade-admin/src/api/client.ts`
- `blade-admin/src/stores/auth.ts`
- `blade-admin/src/views/login/index.vue`
- `blade-backend/src/main/java/com/blade/auth/service/AuthService.java`

**执行人**：AI

### [功能优化] - 快速录单来源档口默认值

**变更内容**：
- 快速录单页“来源档口/店铺”默认填入 `御龙`。
- 点击“保存并录下一单”重置表单后，来源档口恢复默认值 `御龙`。

**变更原因**：
- 当前录单主要来自固定档口，默认值可减少重复输入。

**影响范围**：
- `blade-admin/src/views/orders/quick.vue`

**执行人**：AI

### [功能优化] - 快速录单客户名称下拉筛选

**变更内容**：
- 快速录单页客户名称输入框改为可筛选下拉。
- 输入客户名称时按关键字查询已有客户，并展示客户名称、首个电话和地址。
- 选中已有客户后自动回填 `customerId`、客户电话和客户地址。
- 保留手动输入新客户名称能力，未选择下拉项时按新客户信息继续录单。

**变更原因**：
- 提升纸质订单录入效率，减少重复输入已有客户资料。

**影响范围**：
- `blade-admin/src/views/orders/quick.vue`

**执行人**：AI

### [Bug修复] - 登录页验证码初始化随机化

**变更内容**：
- 登录页验证码初始值从固定 `8K2M` 改为页面加载时随机生成。
- 保留点击“看不清？换一张”和验证码错误后自动刷新的原有逻辑。

**变更原因**：
- 原实现刷新登录页时验证码总是固定值，容易造成误解且不符合验证码交互预期。

**影响范围**：
- `blade-admin/src/views/login/index.vue`

**执行人**：AI

### [Bug修复] - PC 管理端刷新后重复登录

**变更内容**：
- `blade-admin` 登录态持久化补齐：除 `token` 外，`userInfo` 和 `permissions` 也写入 `localStorage`。
- 路由守卫增加刷新恢复逻辑：当本地存在 token 但 Pinia 内存态丢失时，优先从本地恢复；必要时调用 `/user/info` 和 `/auth/codes` 重新拉取用户信息与权限。
- 修复刷新页面后权限列表为空，被误判为无权限并跳回登录页的问题。

**变更原因**：
- Pinia 状态刷新后会清空，原实现只持久化 token，导致路由权限守卫无法识别已登录用户。

**影响范围**：
- `blade-admin/src/stores/auth.ts`
- `blade-admin/src/router/index.ts`

**执行人**：AI

### [数据变更] - 复制 624 系列商品

**变更内容**：
- 参照 `624-1#` 在本地生产库 `blade_project_prod` 新增商品 `624-2#` 至 `624-5#`。
- 新商品仅 `product_code` 和 `name` 改为对应编号，其余商品基础字段保持与 `624-1#` 一致。
- 同步复制商品颜色关联、尺码关联和 SKU 数据，SKU 编码前缀替换为对应商品编号。

**变更原因**：
- 用户要求批量创建同款不同编号商品，减少人工重复录入。

**影响范围**：
- 本地 MySQL 数据库：`blade_project_prod`
- 表：`product`、`product_color_rel`、`product_size_rel`、`product_sku`

**执行人**：AI

## 2026-05-04 变更记录

### [Bug修复] - 商品编辑同步 SKU

**变更内容**：
- 修复商品编辑后只更新颜色/尺码关联、不同步 SKU 的问题。
- 商品更新颜色或尺码后，后端会按当前颜色 × 尺码组合自动补齐缺失 SKU。
- 对已不属于当前颜色/尺码组合的 SKU 执行软删除，避免快速录单继续看到旧颜色。
- 新增商品接口测试，覆盖编辑商品后 SKU 数量与颜色配置同步的场景。

**变更原因**：
- 快速录单页面按 SKU 列表展示商品颜色，商品编辑只改关联不改 SKU 会导致新增颜色在快速录单中不可见。

**影响范围**：
- [ProductServiceImpl.java](/Users/chenjiarun/Documents/BladeProject/blade-backend/src/main/java/com/blade/product/service/impl/ProductServiceImpl.java)
- [ProductControllerTest.java](/Users/chenjiarun/Documents/BladeProject/blade-backend/src/test/java/com/blade/product/ProductControllerTest.java)

**执行人**：AI

### [数据变更] - 复制 616 系列商品

**变更内容**：
- 参照 `616-1#` 在本地生产库 `blade_project_prod` 新增商品 `616-2#` 至 `616-10#`，以及 `616-21#` 至 `616-23#`。
- 新商品仅 `product_code` 和 `name` 改为对应编号，其余商品基础字段保持与 `616-1#` 一致。
- 同步复制商品颜色关联、尺码关联和 SKU 数据，SKU 编码前缀替换为对应商品编号。

**变更原因**：
- 用户要求批量创建同款不同编号商品，减少人工重复录入。

**影响范围**：
- 本地 MySQL 数据库：`blade_project_prod`
- 表：`product`、`product_color_rel`、`product_size_rel`、`product_sku`

**执行人**：AI

### [数据变更] - 复制 70020 系列商品

**变更内容**：
- 参照 `70020#01` 在本地生产库 `blade_project_prod` 新增商品 `70020#02` 至 `70020#06`。
- 新商品仅 `product_code` 和 `name` 改为对应编号，其余商品基础字段保持与 `70020#01` 一致。
- 同步复制商品颜色关联、尺码关联和 SKU 数据，SKU 编码前缀替换为对应商品编号。

**变更原因**：
- 用户要求批量创建同款不同编号商品，减少人工重复录入。

**影响范围**：
- 本地 MySQL 数据库：`blade_project_prod`
- 表：`product`、`product_color_rel`、`product_size_rel`、`product_sku`

**执行人**：AI

### [数据变更] - 复制 70019 系列商品

**变更内容**：
- 参照 `70019#01` 在本地生产库 `blade_project_prod` 新增商品 `70019#02` 至 `70019#19`。
- 新商品仅 `product_code` 和 `name` 改为对应编号，其余商品基础字段保持与 `70019#01` 一致。
- 同步复制商品颜色关联、尺码关联和 SKU 数据，SKU 编码前缀替换为对应商品编号。

**变更原因**：
- 用户要求批量创建同款不同编号商品，减少人工重复录入。

**影响范围**：
- 本地 MySQL 数据库：`blade_project_prod`
- 表：`product`、`product_color_rel`、`product_size_rel`、`product_sku`

**执行人**：AI

### [数据变更] - 复制 70018 系列商品

**变更内容**：
- 参照 `70018#01` 在本地生产库 `blade_project_prod` 新增商品 `70018#02` 至 `70018#12`。
- 新商品仅 `product_code` 和 `name` 改为对应编号，其余商品基础字段保持与 `70018#01` 一致。
- 同步复制商品颜色关联、尺码关联和 SKU 数据，SKU 编码前缀替换为对应商品编号。

**变更原因**：
- 用户要求批量创建同款不同编号商品，减少人工重复录入。

**影响范围**：
- 本地 MySQL 数据库：`blade_project_prod`
- 表：`product`、`product_color_rel`、`product_size_rel`、`product_sku`

**执行人**：AI

### [环境变更] - 本地系统切换到生产数据库

**变更内容**：
- 在本地 MySQL 容器 `blade-mysql` 中新增生产库 `blade_project_prod`。
- 后端默认数据源从 `blade_project` 切换到 `blade_project_prod`。
- `application.yml` 改为支持 `BLADE_DB_URL` / `BLADE_DB_USERNAME` / `BLADE_DB_PASSWORD` 环境变量覆盖，便于后续临时切回开发库或接入真实生产数据库。
- 开发库 `blade_project` 保留，不迁移、不清空。
- 清理生产库演示业务数据：订单、订单明细、配货/出库记录、商品、SKU、商品颜色/尺码关联、客户、客户电话、客户标签、库存与库存日志均已清空。
- 保留生产库系统基础数据：登录账号、租户、角色、权限、颜色、尺码、分类、默认仓库和 Flyway 迁移记录。

**变更原因**：
- 将系统运行环境切换到独立生产库，避免继续使用开发库承载生产录入数据。
- 清除初始化迁移脚本带入的演示业务数据，使生产库可用于真实录入。

**影响范围**：
- `blade-backend/src/main/resources/application.yml`
- 本地 MySQL 数据库：`blade_project_prod`
- `docs/00-SETUP.md`
- `docs/SESSION_CONTEXT.md`

**执行人**：AI

### [功能新增] - PC 快速录单增强

**变更内容**：
- 新增 PC 后台 `/orders/quick` 快速录单页，支持单张纸质订单连续录入。
- 优化快速录单 PC 布局：商品明细下方将“结算与配送”和“金额汇总”改为左右并列，窄屏自动回落为上下排列。
- 订单新增纸质单号、订单日期、订单类型（现货/订货）、运费收入、运费成本、总成本、毛利字段。
- 新增订单来源档口/店铺字段 `source_shop`；快速录单不再把“档口”绑定到仓库，仓库仅保留给后续配货和库存流程。
- 订单明细新增成本价、成本金额、明细毛利快照。
- 创建订单支持初始实收金额；追加收款放宽为未完成/未取消/未退货订单可继续收尾款。
- 订单列表新增订单类型、欠款筛选和尾款/毛利展示；订单详情展示运费、成本、毛利；订单导出补充财务字段。

**变更原因**：
- 将原 Excel 月度记账表中的纸质订单录入流程迁移到系统中，并保留历史成本与利润快照。

**影响范围**：
- `blade-backend/src/main/resources/db/migration/V29__order_quick_entry_finance.sql`
- `blade-backend/src/main/resources/db/migration/V30__order_source_shop.sql`
- `blade-backend/src/main/java/com/blade/order/**`
- `blade-admin/src/views/orders/quick.vue`
- `blade-admin/src/views/orders/index.vue`
- `blade-admin/src/views/orders/detail.vue`
- `blade-admin/src/api/order.ts`
- `packages/types/src/order.ts`

**执行人**：AI

## 2026-04-27 变更记录

### [功能优化] - 看板系统 BA-603 库存统计（周转分析）

**变更内容**：
- 新增 `GET /api/dashboard/inventory-stats` 接口
- 新增 `InventoryStatsVO` DTO：库存周转率、平均在库天数、库存总量、SKU数、低库存预警数、库存积压预警数
- `DashboardService` 新增 `getInventoryStats()` 方法
- `DashboardController` 新增 `/inventory-stats` 端点
- 仪表盘新增第三行统计卡片：库存周转率、平均在库天数、库存总量、库存积压预警
- 周转率 = 90天销售量 / 当前库存；平均在库天数 = 90 / 周转率

**影响范围**：
- `blade-backend/.../dashboard/dto/InventoryStatsVO.java`（新建）
- `blade-backend/.../dashboard/service/DashboardService.java`
- `blade-backend/.../dashboard/service/impl/DashboardServiceImpl.java`
- `blade-backend/.../dashboard/controller/DashboardController.java`
- `blade-admin/src/api/dashboard.ts`
- `blade-admin/src/views/dashboard/index.vue`

**执行人**：AI

### [功能优化] - 订单管理 BA-204 订单导出

**变更内容**：
- 新增 `GET /api/orders/export` 接口，支持筛选条件导出Excel
- 新增 `OrderExportDTO` Excel模型：订单号、状态、客户、商品明细（SKU/颜色/尺码/数量/单价/小计）、金额、开单人、创建时间、备注
- `OrderService` 新增 `exportOrders()` 方法，查询订单及明细，按订单明细展开行
- EasyExcel 3.3.4 依赖添加到 pom.xml
- 订单列表页新增"导出"按钮，浏览器直接下载Excel文件

**影响范围**：
- `blade-backend/pom.xml`（新增 EasyExcel 依赖）
- `blade-backend/.../order/dto/OrderExportDTO.java`（新建）
- `blade-backend/.../order/service/OrderService.java`
- `blade-backend/.../order/service/impl/OrderServiceImpl.java`
- `blade-backend/.../order/controller/OrderController.java`
- `blade-admin/src/api/order.ts`
- `blade-admin/src/views/orders/index.vue`

**执行人**：AI

### [功能优化] - 个人中心 BA-704

**变更内容**：
- 新增个人中心页面 `/personal`
- 用户信息展示：头像、昵称、账号、邮箱、手机号、角色、创建时间
- 修改密码功能：弹窗表单，验证旧密码 + 新密码 + 确认密码
- 头部用户区域改为下拉菜单：个人中心 / 退出登录

**影响范围**：
- `blade-admin/src/router/index.ts`（新增 /personal 路由）
- `blade-admin/src/views/personal/index.vue`（新建）
- `blade-admin/src/views/layout/index.vue`（用户区改为下拉菜单）

**执行人**：AI

---

## 2026-04-26 变更记录

### [功能优化] - 客户模块 M1 数据质量（BE-412~BE-414）

**变更内容**：

1. **BE-412 电话重复检查**：
   - 新增迁移脚本 `V25__customer_phone_unique.sql`，对 `crm_customer_phone(tenant_id, phone, deleted)` 建唯一索引
   - `CustomerServiceImpl` 新增 `checkPhoneDuplicate()` 方法
   - `createCustomer()` 创建客户前校验电话是否重复
   - `updateCustomer()` 更新客户前校验新电话是否与其他客户冲突（排除自己）

2. **BE-413 删除客户订单保护**：
   - `deleteCustomer()` 删除前检查 `status NOT IN (4, 5)` 的进行中订单
   - 有进行中订单时抛出 RuntimeException，提示订单号

3. **BE-414 N+1 查询优化**：
   - `getCustomerOrders()` 方法优化：原为循环内单条查询 OrderItem（ N+1 问题）
   - 改为单条 IN 查询获取所有订单项，内存中按 orderId 分组
   - 数据库查询次数从 N+1 降为 2（1 次订单查询 + 1 次订单项查询）

**影响范围**：
- `blade-backend/src/main/resources/db/migration/V25__customer_phone_unique.sql`（新建）
- `blade-backend/.../CustomerServiceImpl.java`

**执行人**：AI

### [功能优化] - 客户模块 M2 用户体验（BE-415~BE-417）

**变更内容**：

1. **BE-415 订单记录分页**：
   - 新增 `CustomerOrderPageDTO` 分页参数类
   - `GET /api/customers/{id}/orders` 支持 `page` + `size` 参数（默认 1/20，最大 100）
   - 返回 `PageResult<CustomerOrderVO>` 包含 total/pages/size/current

2. **BE-416 常用国家置顶**：
   - `CountryCodeSelect.vue` 新增 `loadRecentCountry()` / `saveRecentCountry()` 方法
   - localStorage key = `recentCountries`，最多存储 5 个国家码
   - 选择国家时自动写入，列表顶部显示「常用」分区

3. **BE-417 国家选择器键盘导航**：
   - 搜索输入框 `@keydown` 处理 `ArrowUp/ArrowDown/Enter/Escape`
   - `focusedIndex` 追踪当前聚焦项，↑↓ 键移动，Enter 选中，Esc 关闭
   - 列表项 `@mouseenter` 同步更新 `focusedIndex`

**影响范围**：
- `blade-backend/.../CustomerController.java`
- `blade-backend/.../CustomerService.java`
- `blade-backend/.../CustomerServiceImpl.java`
- `blade-admin/src/components/CountryCodeSelect.vue`

**执行人**：AI

### [功能优化] - 客户模块 M3 业务功能（BE-418~BE-420）

**变更内容**：

1. **BE-418 客户标签功能**：
   - 新增迁移脚本 `V26__customer_tag.sql`，创建 `crm_customer_tag` 和 `crm_customer_tag_rel` 表
   - 新增实体类：`CustomerTag.java`、`CustomerTagRel.java`
   - 新增 Mapper：`CustomerTagMapper.java`、`CustomerTagRelMapper.java`
   - 新增 Service：`CustomerTagService.java`、`CustomerTagServiceImpl.java`
   - 新增 Controller：`CustomerTagController.java`，REST API 完整 CRUD + 客户标签分配/移除
   - API 接口：
     - `GET /api/customer-tags` - 标签列表
     - `POST /api/customer-tags` - 创建标签
     - `PUT /api/customer-tags` - 更新标签
     - `DELETE /api/customer-tags/{id}` - 删除标签
     - `GET /api/customer-tags/customer/{customerId}` - 获取客户标签
     - `POST /api/customer-tags/customer/{customerId}` - 为客户分配标签
     - `DELETE /api/customer-tags/customer/{customerId}/tag/{tagId}` - 移除客户标签

2. **BE-419 沉默客户预警**：
   - `DashboardService` 新增 `getSilentCustomers(Integer days)` 方法
   - `DashboardController` 新增 `GET /api/dashboard/silent-customers?days=90` 接口
   - 沉默客户定义：最后订单距今 >N 天（N 默认为 90），且有已完成订单（status >= 4）
   - 返回数据结构：`SilentCustomerResultDTO` 包含 total 和 customers 列表

3. **BE-420 偏好时间范围筛选**：
   - 新增 `CustomerPreferenceQueryDTO.java`，包含 startDate/endDate 字段
   - `CustomerService.getPreference()` 方法签名更新为接受 `CustomerPreferenceQueryDTO dto`
   - `CustomerServiceImpl.getPreference()` 实现时间范围过滤逻辑
   - 接口调用示例：`GET /api/customers/1/preference?startDate=2025-01-01&endDate=2026-12-31`

**影响范围**：
- `blade-backend/src/main/resources/db/migration/V26__customer_tag.sql`（新建）
- `blade-backend/.../customer/entity/CustomerTag.java`（新建）
- `blade-backend/.../customer/entity/CustomerTagRel.java`（新建）
- `blade-backend/.../customer/mapper/CustomerTagMapper.java`（新建）
- `blade-backend/.../customer/mapper/CustomerTagRelMapper.java`（新建）
- `blade-backend/.../customer/dto/CustomerTagCreateDTO.java`（新建）
- `blade-backend/.../customer/dto/CustomerTagUpdateDTO.java`（新建）
- `blade-backend/.../customer/dto/CustomerTagVO.java`（新建）
- `blade-backend/.../customer/service/CustomerTagService.java`（新建）
- `blade-backend/.../customer/service/impl/CustomerTagServiceImpl.java`（新建）
- `blade-backend/.../customer/controller/CustomerTagController.java`（新建）
- `blade-backend/.../dashboard/dto/SilentCustomerDTO.java`（新建）
- `blade-backend/.../dashboard/dto/SilentCustomerResultDTO.java`（新建）
- `blade-backend/.../dashboard/service/DashboardService.java`
- `blade-backend/.../dashboard/service/impl/DashboardServiceImpl.java`
- `blade-backend/.../dashboard/controller/DashboardController.java`
- `blade-backend/.../customer/dto/CustomerPreferenceQueryDTO.java`（新建）
- `blade-backend/.../customer/service/CustomerService.java`
- `blade-backend/.../customer/service/impl/CustomerServiceImpl.java`

**执行人**：AI

### [功能优化] - 客户模块 M4 数据权限与审计（BE-421~BE-423）

**变更内容**：

1. **BE-421 客户数据权限（mine筛选）**：
   - 新增迁移脚本 `V27__customer_add_create_by.sql`，为 `crm_customer` 表新增 `create_by` 字段
   - `Customer.java` 实体新增 `createBy` 字段
   - `CustomerPageDTO` 新增 `private Boolean mine = false` 字段
   - `CustomerServiceImpl.pageList()` 支持 `mine=true` 筛选：仅返回当前用户创建的客户
   - `getCurrentUserId()` 方法：从 `SecurityContextHolder` 获取当前登录用户ID

2. **BE-422 操作审计日志**：
   - 新增迁移脚本 `V28__customer_operation_log.sql`，创建 `crm_customer_operation_log` 表
   - 新增实体类：`CustomerOperationLog.java`
   - `CustomerServiceImpl` 新增 `logOperation()` 方法：在 create/update/delete 操作后记录审计日志
   - 操作类型：`CREATE`/`UPDATE`/`DELETE`，detail 字段存储变更详情JSON

3. **BE-423 偏好数据Redis缓存**：
   - `CustomerServiceImpl.getPreference()` 方法新增 Redis 缓存
   - 缓存Key格式：`customer:preference:{customerId}:{startDate}:{endDate}`
   - 缓存有效期：1小时
   - 缓存命中时直接返回，避免重复查询数据库

**影响范围**：
- `blade-backend/src/main/resources/db/migration/V27__customer_add_create_by.sql`（新建）
- `blade-backend/src/main/resources/db/migration/V28__customer_operation_log.sql`（新建）
- `blade-backend/.../customer/entity/CustomerOperationLog.java`（新建）
- `blade-backend/.../customer/mapper/CustomerOperationLogMapper.java`（新建）
- `blade-backend/.../customer/entity/Customer.java`
- `blade-backend/.../customer/dto/CustomerPageDTO.java`
- `blade-backend/.../customer/service/impl/CustomerServiceImpl.java`

**执行人**：AI

---

## 2026-04-20 变更记录

### [Bug修复] - 订单状态机 4 项缺陷修复

**变更内容**：
1. **P0-1 confirmPayment 未同步 paymentStatus**：确认收款后 `status` 变更为已付款，但 `paymentStatus` 字段未同步更新。修复：根据 `paidAmount` 与 `totalAmount` 关系自动设置 paymentStatus（全额→2，部分→1）
2. **P0-2 create 未初始化 adjustmentStatus**：新建订单 adjustmentStatus 为 NULL。修复：create() 中设置 `order.setAdjustmentStatus(Order.AdjustmentStatus.NONE)`
3. **P1-1 confirmAdjustment 减配未释放库存**：配货员减少 allocatedQty 后多余的 `global_reserved_qty` 未释放。修复：confirmAdjustment() 遍历计划项，对差值调用 `inventoryService.globalReleasePartial()`
4. **P1-2 cancelOrder 创建状态误调库存**：STATUS_CREATED 订单从未预留过库存，但 cancelOrder() 无条件调用 globalRelease()。修复：仅在 `status >= STATUS_PAID` 时才释放；状态白名单限制仅允许 0/1/2 状态取消

**新增接口**：
- `InventoryService.globalReleasePartial()`：无需查 inventory_global_reserve 记录，直接从各仓库 global_reserved_qty 按比例释放，type = `GLOBAL_RESERVE_ADJUST`

**影响范围**：
- `blade-backend/.../OrderServiceImpl.java`
- `blade-backend/.../OrderDeliveryPlanServiceImpl.java`
- `blade-backend/.../InventoryService.java`
- `blade-backend/.../InventoryServiceImpl.java`

**执行人**：AI

### [功能优化] - 订单编辑界面与追加收款功能

**变更内容**：
1. **订单编辑弹窗优化**（`blade-admin/src/views/orders/index.vue`）：
   - 弹窗顶部新增订单上下文摘要（订单号、状态标签、金额）
   - `editingOrderId` ref 替换为 `editingOrder` ref（保存整行数据）
   - 历史记录：当时新增 `images` 文本字段；现已在统一文件存储改造中调整为 fileId JSON 数组字符串
2. **追加收款功能**（`blade-admin/src/views/orders/detail.vue`）：
   - 新增"追加收款"按钮，条件：`order.status === 0 && order.paymentStatus !== 2`
   - 新增追加收款弹窗（显示订单总额/已付金额/待付余额，输入本次收款金额）
3. **新增后端接口**：
   - `POST /api/orders/{id}/add-payment`：仅创建状态且未付全款可调用，累加 paidAmount，超额拦截，自动更新 paymentStatus
4. **GlobalExceptionHandler 修复**：`RuntimeException` 新增专项处理，返回 400 + 可读错误信息，不再被兜底的 500 异常处理器吞没

**影响范围**：
- `blade-backend/.../dto/AddPaymentDTO.java`（新建）
- `blade-backend/.../OrderService.java`
- `blade-backend/.../OrderServiceImpl.java`
- `blade-backend/.../OrderController.java`
- `blade-backend/.../GlobalExceptionHandler.java`
- `blade-admin/src/api/order.ts`
- `blade-admin/src/views/orders/index.vue`
- `blade-admin/src/views/orders/detail.vue`

**执行人**：AI

---

## 2026-04-19 变更记录

### [测试修复] - 订单全流程测试基线与迁移阻塞排查

**变更内容**：
1. 新增 [docs/testing/ORDER_FULLFLOW_TEST_CASES.md](/Users/chenjiarun/Documents/BladeProject/docs/testing/ORDER_FULLFLOW_TEST_CASES.md)，整理订单系统全流程测试用例与当前阻塞问题
2. 后端补充 Flyway 依赖，修复 `application.yml` 与 `application-test.yml` 的 MySQL 8 JDBC 兼容参数
3. 调整重复迁移版本号：
   - `V8__order_images.sql` → `V8_1__order_images.sql`
   - `V12__add_status_to_size_color.sql` → `V12_1__add_status_to_size_color.sql`
4. 修复已验证的历史迁移问题：
   - `V3__product_module.sql` 初始化商品数据字段错误
   - `V7__order_table_rename.sql` 条件重命名缺失
   - `V13__sys_user_role_fix.sql` 改为兼容 MySQL 8 的动态列/索引修复写法

**变更原因**：
- 订单全流程测试在登录前即被数据库迁移和测试基线问题阻断
- 需要先恢复空库可迁移、可启动、可登录的联调基础，才能继续验证订单业务流程

**影响范围**：
- `docs/testing/ORDER_FULLFLOW_TEST_CASES.md`
- `blade-backend/pom.xml`
- `blade-backend/src/main/resources/application.yml`
- `blade-backend/src/test/resources/application-test.yml`
- `blade-backend/src/main/resources/db/migration/V3__product_module.sql`
- `blade-backend/src/main/resources/db/migration/V7__order_table_rename.sql`
- `blade-backend/src/main/resources/db/migration/V13__sys_user_role_fix.sql`
- `blade-backend/src/main/resources/db/migration/V8_1__order_images.sql`
- `blade-backend/src/main/resources/db/migration/V12_1__add_status_to_size_color.sql`

**执行人**：AI

### [功能优化] - 订单录入页校验增强与配货计划首次保存修复

**变更内容**：
1. 优化 `blade-admin/src/views/orders/new.vue` 提交前校验：
   - 增加送货地址必填校验
   - 增加定金金额必须大于 0 且不能超过订单总额的前端校验
   - 提交按钮改为更准确的“保存订单并进入详情”文案，避免与后续“确认收款”动作混淆
2. 修复 `blade-admin/src/views/orders/detail.vue` 首次创建配货计划时未提交弹窗编辑结果的问题：
   - 首次创建后立即按弹窗内容执行一次更新
   - 确保仓库、计划数量、配货数量和备注能正确保存

**变更原因**：
- 订单录入页原有前端校验偏弱，部分错误只能等后端报错后才能发现
- 订单创建按钮文案与实际行为不一致，容易让用户误以为保存时已经完成收款
- 配货计划首次创建时前端未带上弹窗编辑内容，存在实际保存结果与用户操作不一致的风险

**影响范围**：
- `blade-admin/src/views/orders/new.vue`
- `blade-admin/src/views/orders/detail.vue`

**执行人**：AI

---

## 2026-04-04 变更记录

### [文档校准] - 数据库设计文档与迁移脚本对齐

**变更内容**：
1. 重写 `docs/architecture/DATABASE.md`，以 Flyway 迁移脚本累计结果为数据库结构真相来源
2. 更新 `docs/02-PRD.md` 中商品、库存、订单相关数据库字段描述
3. 更新 `docs/06-ORDER_INVENTORY_DESIGN.md`，明确“当前实现”与“待收尾”边界
4. 更新 `docs/08-PERMISSION_SYSTEM_DESIGN.md`，使权限表结构和权限编码与 `V12~V16` 迁移一致

**变更原因**：
- 多份数据库相关文档与当前迁移脚本和代码实现存在漂移
- 需要统一数据库结构的单一事实来源，避免后续开发误判字段、可空性和状态

**影响范围**：
- `docs/architecture/DATABASE.md`
- `docs/02-PRD.md`
- `docs/06-ORDER_INVENTORY_DESIGN.md`
- `docs/08-PERMISSION_SYSTEM_DESIGN.md`

**执行人**：AI

### [功能增强] - 商品表字段扩展 + 供应商模块预留

**变更内容**：
1. PRD更新 (PRD-401)：
   - 4.1商品表新增字段：product_code、supplier_id、cost_price、wholesale_price、weight、remark
   - 新增4.2供应商表（supplier，P1暂不开发管理页面）

2. 数据库迁移 (V23__product_add_fields.sql)：
   - product表：新增 supplier_id、cost_price、wholesale_price、weight、remark 字段
   - 新建supplier表结构

3. 后端更新：
   - `Product.java`: 增加新字段，price改为wholesale_price
   - `ProductCreateDTO.java`: 增加新字段
   - `ProductUpdateDTO.java`: 增加新字段
   - `ProductVO.java`: 增加新字段（costPrice、wholesalePrice、weight、remark、supplierId、supplierName）
   - `ProductServiceImpl.java`: 更新create/update/toVO方法

**影响范围**：
- `docs/02-PRD.md` (4.1、4.2节)
- `blade-backend/src/main/resources/db/migration/V23__product_add_fields.sql`
- `blade-backend/src/main/java/com/blade/product/entity/Product.java`
- `blade-backend/src/main/java/com/blade/product/dto/ProductCreateDTO.java`
- `blade-backend/src/main/java/com/blade/product/dto/ProductUpdateDTO.java`
- `blade-backend/src/main/java/com/blade/product/dto/ProductVO.java`
- `blade-backend/src/main/java/com/blade/product/service/impl/ProductServiceImpl.java`

**执行人**：AI

---

## 2026-04-02 变更记录

### [功能开发] - 看板统计系统

**变更内容**：
1. 后端接口（BE-501~503）：
   - `DashboardController`: `/api/dashboard/stats`, `/trend`, `/top-products`
   - `DashboardService`: 看板统计服务
   - `DashboardServiceImpl`: 实现统计数据查询逻辑

2. 前端页面（BA-601~602）：
   - `dashboard/index.vue`: 重写为真实数据驱动的仪表盘
   - 集成 ECharts + vue-echarts 实现图表
   - 数字卡片：今日订单、今日销售额、商品数量、待处理订单
   - 销售趋势图：30天订单数和销售额折线图
   - 热销商品排行：Top 5 水平柱状图

3. 安装依赖：`npm install echarts vue-echarts`

**影响范围**：
- `blade-backend/src/main/java/com/blade/dashboard/`
- `blade-admin/src/views/dashboard/index.vue`
- `blade-admin/src/api/dashboard.ts`

**执行人**：AI

### [功能增强] - 仪表盘增强

**变更内容**：
1. 后端新增 DTO：
   - `OrderStatusDTO`: 订单状态分布（status, label, count）
   - `InventoryAlertDTO`: 库存预警（skuId, skuCode, productName, warehouseName, quantity, alertThreshold）
2. 后端扩展 `DashboardStatsDTO` 新增字段：
   - `lowStockAlerts`: 低库存预警数
   - `weekOrders`, `weekOrdersTrend`: 本周订单及环比
   - `weekSales`, `weekSalesTrend`: 本周销售额及环比
   - `avgOrderValue`: 平均客单价
3. DashboardService 新增方法：
   - `getOrderStatusDistribution()`: 返回订单状态分布
   - `getInventoryAlerts()`: 返回低库存预警列表
4. DashboardController 新增端点：
   - `GET /api/dashboard/order-status`
   - `GET /api/dashboard/inventory-alerts`
5. 前端增强：
   - 统计卡片从4个扩展到8个（新增：低库存预警、本周订单、本周销售额、平均客单价）
   - 新增订单状态饼图
   - 新增库存预警列表

**影响范围**：
- `blade-backend/src/main/java/com/blade/dashboard/`
- `blade-admin/src/views/dashboard/index.vue`
- `blade-admin/src/api/dashboard.ts`

**执行人**：AI

### [功能增强] - 仪表盘日期筛选

**变更内容**：
1. 后端新增 DTO 和枚举：
   - `PeriodType`: 周期类型枚举（TODAY, WEEK, MONTH, QUARTER, YEAR, CUSTOM）
   - `DashboardQueryDTO`: 查询参数 DTO，支持 periodType 和自定义日期范围
2. 后端 Service 层改造：
   - 所有方法增加 `DashboardQueryDTO` 参数
   - `getStats()`: 根据周期计算当前周期和上一周期的数据，支持趋势计算
   - `getOrderTrend()`: 根据周期返回对应天数的数据
   - `getTopProducts()`: 根据周期过滤热销商品
   - `getOrderStatusDistribution()`: 根据周期过滤订单状态
   - `getInventoryAlerts()`: 保持不变（库存预警与时间无关）
3. 前端增强：
   - 新增日期筛选组件（el-radio-group + el-date-picker）
   - 支持 6 种周期：今日、本周、本月、本季度、本年、自定义
   - 图表标题动态显示当前周期
   - API 调用传递日期筛选参数

**影响范围**：
- `blade-backend/src/main/java/com/blade/dashboard/`
- `blade-admin/src/views/dashboard/index.vue`
- `blade-admin/src/api/dashboard.ts`

**执行人**：AI

---

## 2026-04-01 变更记录

### [功能开发] - 新建订单页面显示SKU库存

**变更内容**：
1. `new.vue` 新增库存相关功能：
   - 新增 `skuInventoryMap` 存储每个SKU的可用库存
   - 新增 `loadInventoryByWarehouse(warehouseId)` 加载指定仓库的库存
   - 新增辅助函数：`getSkuAvailableQty`、`getSkuAvailableQtyText`、`getInventoryClass`、`getSkuByColorSize`
   - 监听仓库选择变化，自动加载该仓库的库存
   - SKU矩阵单元格改造：每格显示"可用库存"文本 + 数量输入框
   - 库存状态样式：无货(红色)、库存<10(橙色)、库存>=10(绿色)
   - `batchAddProducts()` 添加库存不足校验

2. 库存显示逻辑：
   - 选择档口后，打开添加商品弹窗时加载该仓库库存
   - 每个SKU单元格上方显示"可用: X"或"无货"
   - 无货时输入框自动禁用

**影响范围**：
- `blade-admin/src/views/orders/new.vue`

**执行人**：AI

---

### [Bug修复] - 库存页面数据不显示

**变更内容**：
1. `inventory/index.vue` 响应解析修复：
   - 原代码：`res.data.code === 200`
   - 修复后：`res.code === 200`
   - 原因：axios 拦截器已展开 `data`，无需再访问 `.data.data`
2. 修复了 6 处响应解析错误

**变更原因**：
- 库存页面显示"暂无库存数据"，但 API 实际返回了 230 条数据
- 原因：响应拦截器已展开外层 `data`，但前端代码还在用 `res.data.xxx`

**影响范围**：
- `blade-admin/src/views/inventory/index.vue`

**执行人**：AI

---

### [逻辑优化] - 订单创建移除库存校验（支持订货/预售场景）

**变更内容**：
1. `OrderServiceImpl.create()` 方法移除库存校验循环
   - 删除原 lines 177-187 的跨仓总量校验代码
   - 订单创建时不再检查 SKU 是否有库存
2. 库存校验仍保留在以下流程：
   - 确认收款 (`confirmPayment()`) → 调用 `globalReserve()` 检查库存并预留
   - 创建配货计划 (`OrderDeliveryPlanServiceImpl.create()`) → 检查库存
   - 确认出库 (`confirmDelivery()`) → 检查并扣减库存

**变更原因**：
- 支持"订货/预售"场景：部分订单没有现货，需要先创建订单等补货
- 订单流程分为：创建(不检查) → 收款(预留) → 配货(检查) → 出库(扣减)
- 混合订单支持：部分 SKU 有库存可配货，部分等待入库

**业务场景说明**：
| 订单阶段 | 是否检查库存 | 说明 |
|---------|------------|------|
| 订单创建 | ❌ 不检查 | 订货/预售订单可直接创建 |
| 确认收款 | ✅ 检查 | 预留库存，库存不足则失败 |
| 配货计划 | ✅ 检查 | 分配仓库，库存不足则失败 |
| 出库 | ✅ 检查 | 扣减库存，库存不足则失败 |

**影响范围**：
- `blade-backend/src/main/java/com/blade/order/service/impl/OrderServiceImpl.java`

**执行人**：AI

---

### [Bug修复] - 库存记录弹窗点击"记录"按钮无数据

**变更内容**：
1. `blade-admin/src/views/inventory/index.vue`：
   - 原按钮 `@click="showLogDialog = true"` 只打开弹窗，不加载数据
   - 修复：改为调用 `openLogDialog()` 函数
   - 新增 `openLogDialog()` 函数：
     - 重置筛选条件（skuId、changeType）
     - 重置分页到第1页
     - 打开弹窗
     - 自动调用 `loadLogData()` 加载数据

**变更原因**：
- 点击"记录"按钮后弹窗显示"暂无记录"
- 后端 API 正常返回 295 条数据
- 前端问题：没有触发数据加载

**影响范围**：
- `blade-admin/src/views/inventory/index.vue`

**执行人**：AI

---

### [Bug修复] - 商品列表页面无数据显示

**变更内容**：
1. `blade-admin/src/views/products/index.vue`：
   - 修复响应解析错误：`res.data.code === 200` → `res.code === 200`
   - 修复数据路径：`res.data.data.records` → `res.data.records`
   - 修复消息路径：`res.data.message` → `res.message`
2. `blade-admin/src/views/products/colors.vue`：
   - `res.data.code` → `res.code`
   - `res.data.data` → `res.data`
3. `blade-admin/src/views/products/sizes.vue`：
   - `res.data.code` → `res.code`
   - `res.data.data` → `res.data`
4. `blade-admin/src/views/products/categories.vue`：
   - `res.data.code` → `res.code`
   - `res.data.data` → `res.data`
   - 原因：axios 拦截器已展开外层 `data`，无需再访问 `.data.data`

**变更原因**：
- 商品列表页面显示"暂无商品数据"
- 后端 API 正常返回多条商品记录
- 前端响应解析路径错误

**影响范围**：
- `blade-admin/src/views/products/index.vue`
- `blade-admin/src/views/products/colors.vue`
- `blade-admin/src/views/products/sizes.vue`
- `blade-admin/src/views/products/categories.vue`

**执行人**：AI

---

## 2026-03-31 变更记录

### [Bug修复] - confirmAdjustment 修复仓库同步问题

**变更内容**：
1. `OrderDeliveryPlanServiceImpl.confirmAdjustment()` 方法修复：
   - 设置默认仓库逻辑：优先使用订单的 warehouse_id，否则使用第一个可用仓库
   - 同步仓库到配货计划：`OrderDeliveryPlan.warehouseId` 未设置时更新
   - 同步仓库到订单明细：`OrderItem.warehouseId` 为空时更新
2. 之前 `confirmAdjustment` 不会设置仓库，导致后续 `deliverOrder` 查找仓库时 500 错误

**变更原因**：
- 调用发货接口时报 500 错误，原因是 `confirmAdjustment` 未同步仓库信息到 order_items
- 后续 `deliverOrder` 尝试从 order_items 获取仓库时为空

**影响范围**：
- `blade-backend/src/main/java/com/blade/order/service/impl/OrderDeliveryPlanServiceImpl.java`

**执行人**：AI

---

### [功能开发] - 前端配货计划功能实现

**变更内容**：

#### 1. API 类型新增
- `blade-admin/src/api/order.ts` 新增：
  - `DeliveryPlanItemDTO` - 配货计划项
  - `DeliveryPlanDTO` - 配货计划请求
  - `DeliveryPlanVO` - 配货计划响应（含 skuCode/productName/colorName/sizeName/warehouseName）
  - `AdjustmentLogDTO` - 调整记录

#### 2. API 函数新增
- `createDeliveryPlan(orderId)` - 创建配货计划
- `updateDeliveryPlan(orderId, data)` - 更新配货计划
- `getDeliveryPlan(orderId)` - 获取配货计划
- `deleteDeliveryPlan(orderId)` - 删除配货计划
- `confirmAdjustment(orderId)` - 确认调整方案
- `cancelAdjustment(orderId)` - 取消调整
- `getAdjustmentLogs(orderId)` - 获取调整记录
- `recordAdjustment(data)` - 记录调整

#### 3. 详情页改造
- `detail.vue` 新增：
  - 配货计划区块（表格 + 状态标签 + 操作按钮）
  - 调整记录区块
  - 创建/编辑配货计划弹窗
- 按钮逻辑改造：
  - status=1 时显示"创建配货计划"按钮
  - status=2 时显示"确认调整"/"取消调整"/"编辑配货计划"按钮
  - status=3 时显示"发货"按钮
- 新增辅助函数：`adjustmentStatusName`、`adjustmentStatusTagClass`、`planStatusName`、`deliveryPlanStatusTagClass`、`adjustmentTypeName`

**影响范围**：
- `blade-admin/src/api/order.ts`
- `blade-admin/src/views/orders/detail.vue`

**执行人**：AI

---

### [Bug修复] - Vue Router 导航守卫 deprecated warning

**变更内容**：
- `router.beforeEach` 改用新语法，直接 return 路由路径或 `true`
- 移除 `next()` 回调函数的使用

**变更原因**：
- Vue Router 4+ 推荐直接返回路由而非调用 `next(value)`

**影响范围**：
- `blade-admin/src/router/index.ts`

**执行人**：AI

---

### [Bug修复] - el-input blur 事件参数验证失败

**变更内容**：
- `searchCustomer` 函数添加可选参数 `_event?: Event`

**变更原因**：
- Element Plus el-input 的 blur 事件会传递 FocusEvent，与函数签名不匹配

**影响范围**：
- `blade-admin/src/views/orders/new.vue`

**执行人**：AI

---

### [Bug修复] - 新建订单页"保存订单"按钮无点击事件

**变更内容**：
- "保存订单"按钮缺少 `@click="handleSubmit"` 事件绑定
- 添加点击事件后，按钮可正常触发订单保存

**变更原因**：
- 测试发现点击"保存订单"按钮无任何反应
- 只有底部"确认订单并收款"按钮绑定了提交事件

**影响范围**：
- `blade-admin/src/views/orders/new.vue`

**执行人**：AI

---

### [Bug修复] - 前端调整记录 API 路径错误

**变更内容**：
- 前端 `getAdjustmentLogs` 调用 `/orders/${orderId}/adjustment-logs`
- 后端接口是 `/orders/${orderId}/adjustment`
- 修改前端 API 路径为正确的 `/orders/${orderId}/adjustment`

**变更原因**：
- 测试发现 500 错误：加载调整记录失败

**影响范围**：
- `blade-admin/src/api/order.ts`

**执行人**：AI

---

### [Bug修复] - inventory/index.vue warehouse.name 错误

**变更内容**：
- `warehouseList` 的类型是 `{ id: number; warehouseName: string; ... }`
- 原代码使用 `w.name`，修正为 `w.warehouseName`

**变更原因**：
- 预编译错误，warehouse 对象属性名是 `warehouseName` 而非 `name`

**影响范围**：
- `blade-admin/src/views/inventory/index.vue`（4处）

**执行人**：AI

---

## 2026-03-30 变更记录

### [功能完善] - 订单创建时跨仓总量校验 + 确认收款跨仓预留

**变更内容**：

#### 1. 新增跨仓总量预留机制

- 新增 `inventory_global_reserve` 表（V20 迁移）
- `inventory` 表新增 `global_reserved_qty` 字段
- 新增 `InventoryGlobalReserve` 实体和 Mapper
- 新增 `InventoryService.globalReserve()` / `globalRelease()` / `getGlobalAvailableQty()`

#### 2. 创建订单时校验跨仓总量

- 在 `OrderServiceImpl.create()` 中，计算订单总额后、插入数据库前
- 遍历订单商品，调用 `getGlobalAvailableQty(skuId)` 校验跨仓总量是否充足
- 库存不足时抛出明确提示：`商品[XXX]跨仓总量不足，可用:XX, 需要:XX`

#### 3. 确认收款改用跨仓预留

- `OrderServiceImpl.confirmPayment()` 改用 `reserveInventoryGlobal()` 跨仓总量预留
- `OrderServiceImpl.cancelOrder()` 改用 `releaseInventoryGlobal()` 跨仓总量释放
- 不再按单仓库预留，直接按 SKU 总量锁定

#### 4. 出库时扣减 global_reserved_qty

- `InventoryServiceImpl.out()` 方法：
  - 出库校验时考虑 `global_reserved_qty`
  - 出库扣减时同时扣减 `quantity`、`reserved_qty`、`global_reserved_qty`

**变更原因**：

按设计文档 `06-ORDER_INVENTORY_DESIGN.md` 实现：
- 销售开单时不关心仓库，只看跨仓总量是否充足
- 付款确认时一次性锁定跨仓总量，不绑定具体仓库
- 后续配货灵活分配仓库

**影响范围**：

- 数据库：`inventory` 表（需 V20 迁移）、`inventory_global_reserve` 表（新建）
- 后端：`InventoryService` / `InventoryServiceImpl` / `OrderServiceImpl`
- 前端：订单创建/确认收款流程

**执行人**：AI

---

### [Bug修复] - 订单创建/确认收款时 salesmanName 为空

**变更内容**：

1. `sale_order` 表新增 `salesman_name` 字段（V19 迁移）
2. `Order.java` 新增 `salesmanName` 字段
3. `OrderServiceImpl.create()` 创建订单时直接写入 `salesmanName`
4. `OrderServiceImpl.convertToVO()` 优先使用存储的 `salesmanName`

**变更原因**：

- `sys_user` 表有多租户隔离，`salesman_id` 查询时租户拦截器加条件导致查不到用户
- 改用冗余字段存储 `salesman_name`，创建订单时直接写入

**影响范围**：

- 数据库：`sale_order` 表（需 V19 迁移）
- 后端：`Order.java` / `OrderServiceImpl.java`

**执行人**：AI

---

## 2026-03-29 变更记录

### [Bug修复] - 出库单确认发货时预留未释放

**变更内容**：
1. `OrderDeliveryServiceImpl.confirmDelivery()` 中 `source` 从 `"ORDER_DELIVERY"` 改为 `"ORDER"`
2. 修复后确认发货时会同时扣减 `quantity` 和释放 `reserved_qty`

**变更原因**：
- 出库单确认发货时只扣减了库存总量，没有释放预留，导致预留数一直挂在那里
- 可用库存计算错误（available = quantity - reserved）

**影响范围**：
- `OrderDeliveryServiceImpl.java`

**执行人**：AI

---

### [Bug修复] - Inventory API 500 错误

**变更内容**：
1. `Inventory.java` 中 `availableQty` 字段添加 `@TableField(exist = false)` 注解
2. 该字段是计算字段，不对应数据库列

**变更原因**：
- MyBatis-Plus 将 `@TableField(exist = false)` 的 `availableQty` 误认为数据库列
- 实际数据库中 `available_qty` 已作为 GENERATED 列存在，但后来被 DROP

**影响范围**：
- `Inventory.java`

**执行人**：AI

---

### [功能完善] - 出库单表缺失字段补充

**变更内容**：
1. 手动创建 `order_delivery` 和 `order_delivery_item` 表（Flyway 迁移脚本未执行）
2. 为 `order_delivery_item` 表添加 `tenant_id` 字段

**变更原因**：
- V17 迁移脚本存在但未在测试环境执行
- 实体类定义了 `tenant_id` 但数据库表缺少该字段

**影响范围**：
- 数据库：`order_delivery`、`order_delivery_item` 表

**执行人**：AI

---

## 2026-03-26 变更记录

### [架构设计] - 库存锁定流程与多仓库出库方案确认

**变更内容**：

#### 一、完整订单生命周期（已确认）

```
1. 销售新建订单 (status=0)
   └── 库存无变动

2. 销售确认收款（定金或全款）(status=1)
   └── 调用 reserve() → reservedQty++
   └── 仓库人员能看到此订单（显示"待付尾款"或"待发货"）

3. 仓库配货（按仓库分组出库）
   └── 创建出库单 OrderDelivery（无库存变动）

4. 仓库确认发货
   └── 调用 out() → quantity--, reservedQty--
   └── 出库单状态：2（已发货）

5. 全部出库完成后，订单自动变为"已发货"状态 (status=2)

6. 订单完成 (status=3)
   └── 库存无变动

7. 订单取消 (status=4)
   └── 调用 release() → reservedQty--
```

#### 二、库存字段说明

| 字段 | 说明 | 计算方式 |
|------|------|----------|
| quantity | 库存总量 | 入库累加，出库扣减 |
| reservedQty | 已锁定数量 | 预留时++，释放时--，出库时-- |
| availableQty | 可用数量 | quantity - reservedQty（前端计算显示） |

**注意**：`availableQty` 数据库列是冗余的，应去掉或在前端计算。

#### 三、当前代码问题（需修复）

| # | 问题 | 位置 | 说明 |
|---|------|------|------|
| 1 | OrderItem 没有 warehouseId | OrderItem.java | 无法支持多仓库分配 |
| 2 | reserveInventory 用 order.warehouseId | OrderServiceImpl:376 | 所有商品按一个仓库预留 |
| 3 | out() 没有扣减 reservedQty | InventoryServiceImpl:258 | 出库后 reservedQty 不归零 |
| 4 | 新出库流程没有调用库存操作 | OrderDeliveryServiceImpl.confirmDelivery() | 确认发货但不扣库存 |

#### 四、需要修复的完整清单

1. **OrderItem 新增 warehouseId 字段**（数据库迁移 + Entity + DTO）
2. **修改 OrderCreateDTO** - 创建订单时指定每个 item 的 warehouseId
3. **修改 reserveInventory()** - 按 OrderItem.warehouseId 分别预留
4. **修改 releaseInventory()** - 按 OrderItem.warehouseId 分别释放
5. **修改 OrderDeliveryService.confirmDelivery()** - 调用 out() 扣库存
6. **修复 out() 方法** - 出库时同时扣减 quantity 和 reservedQty
7. **修改前端** - 创建订单时支持选择每个商品的仓库

#### 五、多仓库支持设计

**订单结构**：
```
Order (order_id, warehouse_id = 默认发货仓库)
   └── OrderItem (sku_id, warehouse_id, quantity) ← 每个商品可指定不同仓库
           ↓
出库单 (OrderDelivery) - 按仓库分组
   └── OrderDeliveryItem (order_item_id, sku_id, quantity)
           ↓
库存变动 (InventoryLog): 记录 order_id, warehouse_id, sku_id
```

**预留/出库按 warehouseId 维度**：
- reserve(skuId, warehouseId, quantity)
- out(skuId, warehouseId, quantity)

#### 六、订单与出库单关系

| 关系 | 说明 |
|------|------|
| 1 Order → N OrderDelivery | 一个订单可以有多个出库单（不同仓库） |
| 1 OrderDelivery → 1 Warehouse | 每个出库单对应一个仓库 |
| 1 OrderDelivery → N OrderDeliveryItem | 每个出库单包含多个商品明细 |
| 1 OrderItem → N OrderDeliveryItem | 一个订单商品可能分多次出库 |

**变更原因**：确认库存锁定完整流程和多仓库出库方案

**影响范围**：
- OrderItem.java（新增 warehouseId）
- OrderCreateDTO.java
- OrderServiceImpl.java
- OrderDeliveryServiceImpl.java
- InventoryServiceImpl.java
- 前端订单创建页

**执行人**：AI + 用户讨论

### [Bug修复] - 库存并发控制与多仓库分配修复

**变更内容**：

#### 1. reserveInventory/releaseInventory/outInventory 按仓库分组
- **问题**：之前使用 `order.getWarehouseId()` 对所有订单明细统一处理，忽略了 OrderItem.warehouseId 字段
- **修复**：按 `OrderItem.warehouseId` 分组，每个仓库单独调用库存服务
- **文件**：`OrderServiceImpl.java`

#### 2. out() 方法未扣减 reservedQty
- **问题**：出库时只扣减了 quantity，未扣减 reservedQty
- **修复**：当 source="ORDER" 时，同时执行 `quantity--` 和 `reserved_qty--`
- **文件**：`InventoryServiceImpl.java`

#### 3. confirmDelivery() 未调用库存服务
- **问题**：仓库确认发货时未实际扣减库存
- **修复**：调用 `inventoryService.out()` 扣减库存
- **文件**：`OrderDeliveryServiceImpl.java`

**影响范围**：
- OrderServiceImpl.java（reserveInventory/releaseInventory/outInventory）
- InventoryServiceImpl.java（out 方法）
- OrderDeliveryServiceImpl.java（confirmDelivery 方法）

**执行人**：AI

---

## 2026-03-25 变更记录

### [功能增强] - 前端菜单权限过滤 + 路由守卫

**变更内容**：
1. **Auth Store 增强** (`blade-admin/src/stores/auth.ts`)：
   - 新增 `permissions` ref 存储用户权限码列表
   - 新增 `setPermissions()` 方法
   - `logout()` 时清除 permissions

2. **登录页改造** (`blade-admin/src/views/login/index.vue`)：
   - 登录成功后同时调用 `getUserInfo()` 和 `getAuthCodes()` 获取用户信息和权限
   - 根据权限动态计算第一个可访问页面（避免跳转到无权限页面）

3. **布局菜单过滤** (`blade-admin/src/views/layout/index.vue`)：
   - `navItems` 改为 computed 根据用户权限动态过滤
   - 仪表盘、订单、库存、商品、客户、系统管理各有对应的 `menu:xxx` 权限码要求
   - `hasPermission()` 检查用户是否拥有指定权限码

4. **路由守卫** (`blade-admin/src/router/index.ts`)：
   - 每个路由配置 `meta.permission` 指定所需权限码
   - 无权限访问时跳转到第一个有权限页面或登录页
   - 访问 `/login` 但无任何可访问页面时，清除token重新登录

**权限码配置**：
| 菜单 | 路由 | 所需权限码 |
|------|------|-----------|
| 仪表盘 | /dashboard | menu:dashboard |
| 订单管理 | /orders | menu:order |
| 库存 | /inventory | menu:inventory |
| 商品 | /products | menu:product |
| 客户管理 | /clients | menu:customer |
| 系统管理 | /system | menu:system |

**权限检查逻辑**：
- 菜单是否显示：只看用户是否拥有 `menu:xxx` 权限码
- 角色无关：同一个角色可以拥有不同的菜单权限

**影响范围**：
- blade-admin/src/stores/auth.ts
- blade-admin/src/views/login/index.vue
- blade-admin/src/views/layout/index.vue
- blade-admin/src/router/index.ts

**执行人**：AI

---

### [Bug修复] - sell01 权限不足导致死循环问题

**变更内容**：
1. sell01 角色没有 `menu:dashboard` 权限
2. 访问无权限页面时跳转到第一个有权限页面而不是 `/`
3. 如果没有任何可访问页面，清除token重新登录

**变更原因**：
- sell01 访问 `/system` 被拦截 → 跳转 `/` → `/` 重定向 `/dashboard` → 无权限 → 死循环

**影响范围**：
- blade-admin/src/router/index.ts

**执行人**：AI

---

### [性能优化] - 系统管理页面 el-tree 性能优化

**变更内容**：
1. 分配权限对话框 el-tree 优化：
   - 移除 `default-expand-all` 属性（之前37个节点全部展开，渲染大量DOM导致卡顿）
   - 添加 `expand-on-click-node="false"` 避免点击节点自动展开
2. 权限配置标签页 el-tree 优化：
   - 移除 `default-expand-all` 属性
   - 添加 `expand-on-click-node="false"`
   - 将 `v-show` 改为 `v-if`（切换标签页时完全销毁组件，避免后台持续消耗资源）
3. 新增 `expandedPermissionIds` 变量支持后续按需展开

**变更原因**：
- 用户反馈点击"分配权限"对话框时卡顿
- 用户反馈切换权限配置标签页到其他页面时卡顿
- `default-expand-all` 导致37个权限节点全部渲染，产生大量DOM元素

**影响范围**：
- blade-admin/src/views/system/index.vue（el-tree 配置优化）

**执行人**：AI

---

### [Bug修复] - 分配权限对话框 API 权限无法保存

**变更内容**：
1. 修复分配权限时 API 权限（type=4）无法正确保存的问题
2. 修复保存后重新打开已分配的 API 权限不显示勾选状态
3. 新增数据库迁移 `V15__add_api_permissions.sql`：添加 API 级别权限数据
   - user:create、user:update、user:delete、user:password:reset
   - role:create、role:update、role:delete、role:assign
   - permission:create、permission:update、permission:delete
4. 前端 el-tree 优化：
   - 改用 `default-checked-keys` 属性初始化选中状态
   - 移除 `check-strictly` 模式，改用父子联动模式
   - 自动展开系统管理菜单（id=6）确保 API 权限子节点正确渲染
5. 调整 `loadPermissionTree` 和 `loadRolePermissions` 执行顺序

**变更原因**：
- 用户反馈给系统管理员分配 API 权限后，保存成功但重新打开不显示勾选
- 原 `setCheckedKeys` 方法在父节点未展开时无法正确设置子节点选中状态
- API 权限的 parent_id=6（系统管理菜单），需要父节点展开才能访问

**影响范围**：
- blade-admin/src/views/system/index.vue（el-tree 选中状态逻辑）
- blade-backend/src/main/resources/db/migration/V15__add_api_permissions.sql（新增）

**执行人**：AI

---

### [Bug修复] - 用户具体权限未加载导致 403 Access Denied

**变更内容**：
1. 修复 `UserDetailsServiceImpl`，使其同时加载：
   - 角色权限（如 `ROLE_ADMIN`）
   - 用户被分配的具体权限（如 `user:create`、`role:create`）
2. 修改 `loadUserByUsername()` 方法，注入 `PermissionMapper` 并查询用户的具体权限

**变更原因**：
- 用户反馈给系统管理员分配了 API 权限后，调用对应接口时报 403 Access Denied
- 原 `loadUserByUsername()` 只加载了角色权限，没有加载用户被分配的具体权限
- 导致 `hasAuthority('role:create')` 校验失败

**权限加载链路**：
```
登录 → 生成 Token
请求 → JwtAuthenticationFilter → loadUserByUsername()
                                    ↓
                          加载角色（ROLE_ADMIN）
                          + 具体权限（user:create 等）
                                    ↓
                          设置到 SecurityContext
                                    ↓
                         @PreAuthorize 校验通过
```

**影响范围**：
- `blade-backend/.../auth/service/UserDetailsServiceImpl.java`

**执行人**：AI

---

## 2026-03-24 变更记录

### [架构设计] - 权限系统设计确认

**变更内容**：
讨论并确认权限系统完整设计方案：

**设计原则**：
1. 多租户：所有表都有 tenant_id
2. 可审计：关联表有 deleted、create_time
3. 防重复：关联表加唯一约束
4. 类型明确：权限分类型（菜单/按钮/字段/API）
5. 可扩展：预留字段，支持未来新增类型
6. 松耦合：权限表和菜单表分离

**权限类型**：
| type | 说明 | 示例 |
|------|------|------|
| 1 | 菜单权限 | 看到左侧菜单 |
| 2 | 按钮权限 | 看到"新建"按钮，能点击 |
| 3 | 字段权限 | 看到/隐藏某个字段 |
| 4 | API权限 | 能调用某个接口 |

**字段脱敏设计（mask_type）**：
| mask_type | 说明 | 示例 |
|-----------|------|------|
| 0 | 不脱敏 | 正常显示 |
| 1 | 置空 | cost_price = null |
| 2 | 脱星 | 138****8888 |
| 3 | 替换 | **** |

**角色权限矩阵**：
| 角色 | 可看菜单 | 成本价 | 销售价 | 配送数量 | 财务数据 |
|------|---------|--------|--------|---------|---------|
| 老板/经理 | 全部 | ✅ | ✅ | ✅ | ✅ |
| 销售员 | 订单/库存/商品/客户 | ❌ | ✅ | ✅ | ❌ |
| 仓库管理员 | 库存 | ❌ | ❌ | ✅ | ❌ |
| 财务 | 订单/仪表盘 | ❌ | ✅ | ❌ | ✅ |
| 采购 | 商品/库存 | ❌ | ❌ | ❌ | ❌ |
| 系统管理员 | 全部 | ✅ | ✅ | ✅ | ✅ |

**数据库设计**：
- sys_permission（权限定义表）：id, name, code(unique), type, module, parent_id, path, method, icon, sort, status, mask_type, mask_value, description, tenant_id, deleted, create_time, update_time
- sys_role_permission（角色权限关联表）：id, role_id, permission_id, tenant_id, deleted, create_time, UNIQUE(role_id, permission_id)
- 修改 sys_user_role：增加 tenant_id, deleted, create_time, UNIQUE(user_id, role_id, tenant_id)
- 修改 sys_role_menu：增加 tenant_id, deleted, create_time，或废弃改用 sys_role_permission

**后端需要补充**：
- SysPermission 实体 + Mapper
- SysRolePermission 实体 + Mapper
- PermissionService：权限 CRUD + hasPermission() 判断逻辑
- 字段脱敏拦截器：根据权限过滤 VO 字段
- 预置数据：6个角色 + 完整权限数据

**前端需要补充**：
- /system/users - 用户管理页面
- /system/roles - 角色管理页面
- /system/permissions - 权限配置页面
- /profile - 个人中心页面

**影响范围**：
- 新增表：sys_permission, sys_role_permission
- 修改表：sys_user_role, sys_role_menu
- 新增文档：docs/08-PERMISSION_SYSTEM_DESIGN.md

**执行人**：AI + 用户讨论

---

### [功能优化] - BA-205 订单显示开单人员

**变更内容**：
1. 订单列表增加"开单人员"列
2. 展示 `OrderVO.salesmanName` 字段

**影响范围**：
- blade-admin/src/views/orders/index.vue

**执行人**：AI

---

### [功能开发] - BA-501~BA-502 客户管理页面

**变更内容**：
1. 后端新增 DTO：CustomerPageDTO, CustomerCreateDTO, CustomerUpdateDTO
2. 后端 CustomerService 新增方法：
   - pageList - 客户分页列表
   - getById - 获取客户详情
   - updateCustomer - 更新客户
   - deleteCustomer - 删除客户（软删除）
3. 后端 CustomerController 新增接口：
   - GET /api/customers - 客户分页列表
   - GET /api/customers/{id} - 获取客户详情
   - PUT /api/customers - 更新客户
   - DELETE /api/customers/{id} - 删除客户
4. 前端 customer.ts API 全面更新
5. 前端 clients/index.vue：
   - 真实 API 调用
   - 新建/编辑客户弹窗
   - 删除确认对话框
   - 分页组件
   - 搜索和重置筛选

**影响范围**：
- blade-backend/src/main/java/com/blade/customer/dto/CustomerPageDTO.java（新增）
- blade-backend/src/main/java/com/blade/customer/dto/CustomerCreateDTO.java（新增）
- blade-backend/src/main/java/com/blade/customer/dto/CustomerUpdateDTO.java（新增）
- blade-backend/src/main/java/com/blade/customer/dto/CustomerVO.java（修改）
- blade-backend/src/main/java/com/blade/customer/service/CustomerService.java（修改）
- blade-backend/src/main/java/com/blade/customer/service/impl/CustomerServiceImpl.java（修改）
- blade-backend/src/main/java/com/blade/customer/controller/CustomerController.java（修改）
- blade-admin/src/api/customer.ts（修改）
- blade-admin/src/views/clients/index.vue（修改）

**执行人**：AI

---

### [Bug修复] - 库存并发控制修复（P0）

**变更内容**：
1. 添加 Redisson 依赖：`redisson-spring-boot-starter 3.27.0`
2. 新增 `RedissonConfig.java`：`RedissonClient` Bean 配置
3. 新增数据库迁移 `V11__inventory_add_version.sql`：为 `inventory` 表添加 `version` 字段
4. 修改 `Inventory.java`：添加 `@Version` 注解的 `version` 字段及 get/set 方法
5. 修改 `MybatisPlusConfig.java`：启用 `OptimisticLockerInnerInterceptor`
6. 重构 `InventoryServiceImpl.java`：所有库存操作方法添加并发控制
   - `in()` - 入库加锁
   - `out()` - 出库加锁
   - `adjust()` - 调整加锁（新增：调整后库存不能为负校验）
   - `reserve()` - 预留加锁
   - `release()` - 释放加锁（新增：预留数量不足校验）
   - 所有方法使用 Redis 分布式锁 + 乐观锁双重保护

**变更原因**：
- 修复订单系统 P0 问题：库存并发控制缺失，存在超卖风险
- 防止两销售同时对同一 SKU 付款确认导致超额预留

**影响范围**：
- `blade-backend/pom.xml`
- `blade-backend/src/main/java/com/blade/config/RedissonConfig.java`（新增）
- `blade-backend/src/main/resources/db/migration/V11__inventory_add_version.sql`（新增）
- `blade-backend/src/main/java/com/blade/inventory/entity/Inventory.java`
- `blade-backend/src/main/java/com/blade/config/MybatisPlusConfig.java`
- `blade-backend/src/main/java/com/blade/inventory/service/impl/InventoryServiceImpl.java`

**执行人**：AI

---

### [文档更新] - 新增订单系统问题清单文档

**变更内容**：
1. 新增 `docs/reference/ORDER_SYSTEM_ISSUES.md` - 订单系统已知问题清单
   - 库存并发控制缺失（P0）
   - 跨仓总量预留未实现（P0）
   - 配货计划机制缺失（P1）
   - 支付状态与订单状态不同步（P1）
   - 表结构不足清单
   - 文档索引和接手须知

2. 更新 `docs/SESSION_CONTEXT.md`：
   - 新增"当前阻塞问题"章节，列出订单系统核心缺陷
   - 更新"给接手的 AI"章节，要求订单/库存开发必须先读问题清单

**变更原因**：
- 让后续 AI 能快速定位订单系统问题
- 避免重复踩坑或破坏已有设计
- 已有 `06-ORDER_INVENTORY_DESIGN.md` 设计方案，但缺少问题汇总文档

**影响范围**：
- `docs/reference/ORDER_SYSTEM_ISSUES.md`（新增）
- `docs/SESSION_CONTEXT.md`

**执行人**：AI

---

### [Bug修复] - 订单分页和 TypeScript 编译错误修复

**变更内容**：
1. 订单分页点击无效：el-pagination 组件缺少 `@current-change="loadData"` 事件绑定
2. TypeScript 编译错误修复：
   - orders/detail.vue：移除未使用的 `deleteOrder` 导入
   - orders/new.vue：移除未使用的 `filteredSkus` 和 `addProduct`
   - orders/new.vue：修复 `warehouseId` 类型（`number | undefined` → `number`）
   - orders/new.vue：修复 `res.data.records` API 响应结构
   - products/index.vue：修复 `sizesRes.code` → `sizesRes.data.code`

**影响范围**：
- blade-admin/src/views/orders/index.vue（分页事件）
- blade-admin/src/views/orders/detail.vue（import）
- blade-admin/src/views/orders/new.vue（多出错误）

**执行人**：AI

---

### [功能开发] - 商品菜单子页面（颜色/尺码/分类）

**变更内容**：
1. 侧边栏支持子菜单：
   - layout/index.vue：新增 `NavItem.children` 类型和 `expandedMenus` 状态
   - 商品菜单默认展开子菜单
   - 点击父菜单切换展开/收起子菜单

2. 新增前端页面：
   - /products/colors - 颜色列表页（调用 getAllColors API）
   - /products/sizes - 尺码列表页（调用 getAllSizes API）
   - /products/categories - 商品分类页（调用 getAllCategories API）

3. 后端新增 API：
   - ProductCategoryController - GET /api/product-categories（获取所有分类）
   - ProductCategoryService - 分类列表查询服务
   - ProductCategoryVO - 分类返回对象

4. 前端 API 更新：
   - product.ts：新增 `ProductCategory` 接口和 `getAllCategories()` 函数
   - categories.vue：从模拟数据改为调用真实 API

**影响范围**：
- blade-admin/src/views/layout/index.vue（侧边栏子菜单）
- blade-admin/src/views/products/colors.vue（新增）
- blade-admin/src/views/products/sizes.vue（新增）
- blade-admin/src/views/products/categories.vue（新增）
- blade-admin/src/router/index.ts（新增路由）
- blade-admin/src/api/product.ts（新增接口）
- blade-backend/.../product/controller/ProductCategoryController.java（新增）
- blade-backend/.../product/service/ProductCategoryService.java（新增）
- blade-backend/.../product/service/impl/ProductCategoryServiceImpl.java（新增）
- blade-backend/.../product/dto/ProductCategoryVO.java（新增）

**执行人**：AI

---

### [文档更新] - 系统架构文档扩充

**变更内容**：
- 扩充 `docs/architecture/ARCHITECTURE.md`，新增内容：
  1. 系统概述与边界图
  2. 技术架构总览（前端 PC / 前端移动 / 后端）
  3. 前端项目详细目录结构（blade-admin / blade-mobile）
  4. 后端包结构详解
  5. 模块依赖关系图
  6. 数据流架构（订单创建 / 订单付款）
  7. 安全架构（JWT 认证流程 / Token 结构 / 公开接口配置）
  8. 多租户架构（隔离策略 / 超级管理员）
  9. 部署架构（开发环境 / 生产环境规划）

**变更原因**：
- 原架构文档内容简略，仅包含技术选型决策
- 需要更完整的系统架构文档支撑后续开发和交接

**影响范围**：
- `docs/architecture/ARCHITECTURE.md`

**执行人**：AI

---

### [Bug修复] - 客户管理页面操作列按钮显示不完整

**变更内容**：
1. 客户管理页面操作列宽度从 160px 调整为 200px（3个按钮）
2. 商品管理相关页面操作列宽度从 120px 调整为 160px（2个按钮）
3. 按钮间距从 `gap-2` 调整为 `gap-3`

**变更原因**：
- 客户管理页面操作列有3个按钮（编辑/查看订单/删除），原有160px宽度显示不完整
- 商品相关页面操作列有2个按钮，原有120px宽度过于拥挤

**影响范围**：
- blade-admin/src/views/clients/index.vue（操作列宽度）
- blade-admin/src/views/products/index.vue（操作列宽度）
- blade-admin/src/views/products/colors.vue（操作列宽度）
- blade-admin/src/views/products/sizes.vue（操作列宽度）
- blade-admin/src/views/products/categories.vue（操作列宽度）

**执行人**：AI

---

### [Bug修复] - 侧边栏菜单 Bug 修复

**变更内容**：
1. 修复侧边栏宽度计算 bug：
   - 原因：`calculateWidth()` 使用 `scrollWidth` 会随菜单展开/收起而不断增长
   - 修复：改用固定宽度 `220px`
   - 移除 `calculateWidth()`、`sidebarWidth`、`nextTick` 等无用代码

2. 修复商品菜单高亮不消除 bug：
   - 原因：`isActive('/products')` 使用 `startsWith` 导致子路径也匹配父级
   - 修复：改为精确匹配 `route.path === path || route.path.startsWith(path + '/')`

**影响范围**：
- blade-admin/src/views/layout/index.vue

**执行人**：AI

---

### [功能优化] - BE-404/405 客户列表订单数量查询

**变更内容**：
1. CustomerServiceImpl 注入 OrderMapper
2. pageList() 方法：查询每个客户的订单数量（替代原来的 setOrderCount(0)）
3. getById() 方法：同样查询订单数量
4. getByPhone() 方法：同样查询订单数量

**变更原因**：
- 客户列表页需要显示订单数量，之前一直是写死的 0

**影响范围**：
- blade-backend/.../customer/service/impl/CustomerServiceImpl.java

**执行人**：AI

---

### [功能开发] - BA-401~403 商品管理页面 + BA-301~305 库存管理页面

**变更内容**：

**前端 - 商品管理（BA-401~403）**：
1. blade-admin/src/api/product.ts - 完整 CRUD API：
   - getProductPage / getProductById / createProduct / updateProduct / deleteProduct
   - getAllColors / getAllSizes
2. blade-admin/src/views/products/index.vue - 商品列表页完整功能：
   - 真实 API 数据加载
   - 关键字/分类/状态筛选
   - 分页组件
   - 新建/编辑商品弹窗（含颜色/尺码选择和 SKU 预览）
   - 删除确认

**前端 - 库存管理（BA-301~305）**：
1. blade-admin/src/api/inventory.ts - 库存 API：
   - getInventoryPage / getInventoryAlerts
   - stockIn / stockOut / adjustInventory
   - getInventoryLogPage / getWarehousePage / getAllSkus
2. blade-admin/src/views/inventory/index.vue - 库存页面完整功能：
   - 真实 API 数据加载
   - 仓库/预警状态筛选
   - 入库弹窗（支持多商品入库）
   - 出库弹窗（支持多商品出库）
   - 库存调整弹窗（盘盈盘亏）
   - 库存记录查询弹窗

**后端 - 新增接口**：
1. InventoryLogVO.java + InventoryLogPageDTO.java（新增 DTO）
2. InventoryController.listLogs() - GET /api/inventory/logs（库存记录分页查询）
3. InventoryService.listLogs() - 实现库存记录分页查询
4. ProductController.listSkus() - GET /api/products/skus（SKU 下拉列表）
5. ProductService.listAllSkus() - 实现 SKU 列表查询
6. SkuVO.java（新增 DTO）
7. ProductSkuMapper.selectAllSkuList() - 自定义 SQL 关联查询
8. InventoryAdjustItemDTO - 新增 reason 字段

**影响范围**：
- blade-admin/src/api/product.ts（新增）
- blade-admin/src/api/inventory.ts（新增）
- blade-admin/src/views/products/index.vue
- blade-admin/src/views/inventory/index.vue
- blade-backend/src/main/java/com/blade/inventory/dto/InventoryLogVO.java（新增）
- blade-backend/src/main/java/com/blade/inventory/dto/InventoryLogPageDTO.java（新增）
- blade-backend/src/main/java/com/blade/inventory/controller/InventoryController.java
- blade-backend/src/main/java/com/blade/inventory/service/InventoryService.java
- blade-backend/src/main/java/com/blade/inventory/service/impl/InventoryServiceImpl.java
- blade-backend/src/main/java/com/blade/product/dto/SkuVO.java（新增）
- blade-backend/src/main/java/com/blade/product/mapper/ProductSkuMapper.java
- blade-backend/src/main/java/com/blade/product/service/ProductService.java
- blade-backend/src/main/java/com/blade/product/service/impl/ProductServiceImpl.java
- blade-backend/src/main/java/com/blade/product/controller/ProductController.java
- blade-backend/src/main/java/com/blade/inventory/dto/InventoryAdjustItemDTO.java

**执行人**：AI

---

## 2026-03-23 变更记录

### [Bug修复] - BUG-002/BUG-003/BUG-004 调查结果

**变更内容**：
- BUG-002（+86电话报错）：前端已修复（handleSubmit过滤+和空格），后端OrderCreateDTO phone长度从11改为20
- BUG-003（订单详情商品信息空白）：**非代码bug**，系历史测试订单使用了不存在的SKU ID=1，代码对缺失SKU处理正确（优雅降级为空字符串）
- BUG-004（多商品添加失败）：**未复现**，E2E测试验证2商品4 SKU共25件添加功能正常

**影响范围**：
- blade-admin/src/views/orders/TEST_CASES.md（更新bug状态和执行记录）
- blade-backend/src/main/java/com/blade/order/dto/OrderCreateDTO.java（phone长度调整）

**执行人**：AI

### [功能开发] - PC 管理端新建订单页功能增强

**变更内容**：
- 批量添加商品：商品列表支持展开/收起，显示颜色×尺码矩阵，每个 SKU 可输入数量
- 单价可调整：商品级别统一定价，弹窗内可调整单价
- 商品明细单价可编辑：表格内单价直接可修改
- 档口选择：新建订单页新增档口下拉选择
- 订单自动记录开单人员：创建订单时自动关联当前登录用户
- 修复：批量添加时使用用户自定义单价而非 SKU 原价

**影响范围**：
- blade-admin/src/views/orders/new.vue
- blade-admin/src/views/orders/TEST_CASES.md（新增测试用例 NC-008~011）
- 后端：Order/OrderCreateDTO/OrderVO/OrderServiceImpl 新增 salesman_id 字段

**执行人**：AI

---

### [功能开发] - 订单添加销售人员字段

**变更内容**：
- sale_order 表新增 salesman_id 字段，关联 sys_user.id
- Order.java、OrderCreateDTO.java、OrderVO.java 添加 salesmanId/salesmanName 字段
- OrderServiceImpl 创建订单时自动设置 salesmanId（从当前登录用户获取）
- convertToVO 时查询并设置 salesmanName（用户昵称）

**影响范围**：
- blade-backend/src/main/resources/db/migration/V10__order_salesman.sql
- blade-backend/src/main/java/com/blade/order/entity/Order.java
- blade-backend/src/main/java/com/blade/order/dto/OrderCreateDTO.java
- blade-backend/src/main/java/com/blade/order/dto/OrderVO.java
- blade-backend/src/main/java/com/blade/order/service/impl/OrderServiceImpl.java

**执行人**：AI

---

### [功能开发] - PC 管理端新建订单页 BA-203

**变更内容**：
- 参考 Stitch 设计搭建新建订单页面
- 布局：左侧8列（客户信息、支付信息、商品明细），右侧4列（金额汇总、状态卡片、送货设置等）
- 支付状态联动：切换状态自动清零相关金额
- 客户搜索：blur 时按电话搜索，自动填充或清空解锁
- 电话号码 normalize：支持 +86 等国家代码

**影响范围**：
- blade-admin/src/views/orders/new.vue
- blade-admin/src/api/customer.ts

**执行人**：AI

---

## 2026-03-21 变更记录

### [架构变更] - 技术栈重新选型

**变更内容**：
- 移动端：从 UniApp + Vue 2 变更为 Vue3 + Vite + TypeScript + PWA
- 后端：从 SpringBlade 微服务变更为 Spring Boot 3 单体

**变更原因**：
1. SpringBlade 对 AI 开发不友好，每次启动都有问题
2. UniApp + Vue 2 AI 生成质量差，Vue 2 必须升级 Vue 3
3. Flutter Web Bundle 太大（1.5-2MB+），PWA 兼容性差

**影响范围**：
- 移动端：整个技术栈变更，原 app/ 文件夹废弃
- 后端：整个后端迁移，原 SpringBlade 废弃（参考）

**执行人**：AI（团队讨论结果）

---

### [需求变更] - 功能优先级确认

**变更内容**：
确认功能优先级：订单系统(P0) > 库存系统(P1) > 看板系统(P2)

**变更原因**：用户确认

**影响范围**：开发顺序按此优先级执行

**执行人**：用户

---

### [架构变更] - 多租户方案

**变更内容**：
从 SpringBlade 手动拼接 tenant_id 变更为 MyBatis-Plus TenantLineInnerInterceptor 自动处理

**变更原因**：
- 手动拼接容易遗漏，导致数据串租户（安全隐患）
- TenantLineInnerInterceptor 配置即生效，零代码

**影响范围**：
- 后端：所有数据库查询自动带 tenant_id 条件
- 开发规范：禁止手动拼接 tenant_id

**执行人**：AI（主动提出，用户采纳）

---

### [文档变更] - 项目文档体系建立

**变更内容**：
建立完整的项目文档体系：
- 01-README.md（入口）
- 02-PRD.md（产品需求文档）
- 03-TASKS.md（开发任务清单）
- 04-REQUISITION_LOG.md（需求讨论记录）
- 05-CHANGELOG.md（变更记录）
- architecture/（架构文档）
- reference/（参考文档）

**变更原因**：用户要求任何新 AI 来了都能快速接手

**影响范围**：整个项目的文档规范

**执行人**：AI

---

### [工作流程变更] - AI 自主执行模式

**变更内容**：
- 需求讨论在 04-REQUISITION_LOG.md 进行
- PRD 是开发唯一依据，锁定后 AI 自主执行
- 任务清单 03-TASKS.md 是 AI 驱动，AI 自己领任务、自己更新状态
- 交接必须同步文档

**变更原因**：用户希望 AI 自主执行，不参与开发细节

**影响范围**：AI 开发流程

**执行人**：AI（根据用户需求设计）

---

### [功能开发] - BE-007 用户 CRUD 接口完成

**变更内容**：
- 新增 UserController（/api/system/users）
- 新增 UserService 接口和 UserServiceImpl 实现
- 新增 UserVO、UserCreateDTO、UserUpdateDTO、UserPageDTO
- RoleMapper 新增 selectByUserId、insertUserRole、deleteUserRoles 方法

**变更原因**：用户 CRUD 是系统基础功能

**影响范围**：
- 后端：系统用户管理模块
- 接口：GET /api/system/users、GET /api/system/users/{id}、POST /api/system/users、PUT /api/system/users、DELETE /api/system/users/{id}、PUT /api/system/users/{id}/password

**执行人**：AI

---

### [需求讨论] - 服装订单系统多角度讨论

**变更内容**：
通过 Agent Teams 讨论，从开发者、客户、技术架构三个角度分析服装订单系统

**讨论结论**：
1. SKU 矩阵（颜色×尺码）是服装行业核心
2. 订单与库存必须联动，付款后扣库存
3. 库存变动必须全部记录
4. 微信通知是员工痛点（已有公众号）
5. 商品模块独立，订单和库存共用

**执行人**：AI（团队讨论）+ 用户确认

---

### [PRD 更新] - v1.1 版本

**变更内容**：
- 新增商品模块设计（颜色、尺码、SKU）
- 新增库存模块设计（完整出入库记录）
- 重构订单模块（与库存联动）
- 订单状态简化为：待处理→已确认→货中→已完成/已取消
- 库存扣减时机：付款后扣（避免超卖）

**影响范围**：
- BE-101~BE-104 旧版代码需要重构
- 需要先完成商品模块，再完成库存模块，最后重构订单

**执行人**：AI

---

### [功能开发] - BE-101~BE-104 订单系统接口完成（旧版）

**变更内容**：
- 新增 OrderController（/api/orders）
- 新增 OrderService 接口和 OrderServiceImpl 实现
- 新增 OrderVO、OrderCreateDTO、OrderPageDTO、OrderUpdateStatusDTO
- 新增 OrderMapper、OrderItemMapper
- 新增 V2__product_order.sql 迁移脚本（含测试数据）

**变更原因**：订单系统是 P0 优先级功能

**影响范围**：
- 后端：订单管理模块（旧版，需重构）
- ⚠️ 注意：此版本不符合新 PRD v1.1 设计，已标记为需重构

**执行人**：AI

---

### [功能开发] - BE-201~BE-206 商品模块完成

**变更内容**：
- 新增 ProductController（/api/products）
- 新增 ProductService 和 ProductServiceImpl
- 新增商品分类/颜色/尺码/SKU 实体和 Mapper
- 新增 V3__product_module.sql 迁移脚本（含默认颜色尺码和测试商品）
- 商品创建时自动生成 SKU（商品+颜色+尺码）

**影响范围**：
- 后端：商品管理模块
- 接口：GET /api/products、GET /api/products/{id}、POST /api/products、PUT /api/products、DELETE /api/products/{id}、GET /api/products/colors、GET /api/products/sizes

**执行人**：AI

---

### [问题修复] - 后端项目验证与多项 bug 修复

**变更内容**：
1. 修复 UserDetailsServiceImpl 角色加载 null 问题
2. 添加 sys_user_role.tenant_id 字段
3. 修复 admin 用户密码（BCrypt 哈希错误）
4. 添加 product.price 字段
5. 添加关联表 tenant_id 字段（product_color_rel、product_size_rel）
6. 修复 @PutMapping 路径注解错误
7. 安装 Java 17 以支持 Spring Boot 3.2+
8. 手动执行 Flyway 迁移脚本

**影响范围**：
- 后端：系统模块、商品模块
- 数据库：blade_project

**执行人**：AI

**详见**：docs/reference/TROUBLESHOOTING.md（第三节）

---

### [需求讨论] - 库存模块设计确认

**变更内容**：
- 讨论并确认库存模块设计方案
- 入库接口增加图片字段（最多5张，非必填）
- 入库接口增加供应商字段（supplier_id, supplier_name）
- 出库接口增加 reason 字段（其他出库时必填）
- 确认采用方案A（订单模块调用库存模块预留/释放接口）
- PRD 更新库存表设计（新增 alert_threshold、supplier_id、supplier_name、images 字段）

**影响范围**：
- V4__inventory_module.sql 脚本
- PRD 02-PRD.md 库存模块设计

**执行人**：用户确认方案

---

### [功能开发] - BE-301~BE-309 库存模块完成

**变更内容**：
- 新增 WarehouseController、WarehouseService、WarehouseServiceImpl
- 新增 InventoryController、InventoryService、InventoryServiceImpl
- 新增 V4__inventory_module.sql（warehouse、inventory、inventory_log 表）
- 仓库管理：CRUD 接口
- 库存查询：分页 + 筛选 + 预警
- 入库接口：支持图片上传、供应商字段
- 出库接口：ORDER/OTHER 来源 + reason 字段
- 直接调整接口：盘盈盘亏
- 预留锁定/释放接口：订单联动

**影响范围**：
- 后端：库存模块完整功能
- 接口：/api/warehouse/*, /api/inventory/*

**执行人**：AI

**变更内容**：
- 新增 ProductControllerTest.java
- 15 个测试用例覆盖：登录、列表查询、创建商品、更新商品、删除商品、异常场景
- 所有测试用例使用独立测试数据，不依赖其他测试

**影响范围**：
- 后端：商品模块测试

**执行人**：AI

---

### [功能开发] - BE-101~BE-110 订单模块重构（对接库存联动）

**变更内容**：
- V5__order_refactor.sql：订单表新增 customer_id, paid_amount, warehouse_id, pay_time, confirm_time, deliver_time, complete_time；订单明细表新增 sku_code, color_name, size_name
- OrderServiceImpl 重构：
  - create()：创建订单和明细
  - updateStatus()：状态更新（含时间戳设置）
  - confirmPayment()：付款确认→调用 reserveInventory() 锁定库存
  - deliverOrder()：发货→调用 outInventory() 预留转出库
  - completeOrder()：完成订单
  - cancelOrder()：取消→调用 releaseInventory() 释放预占
- 新增 DTO：PaymentConfirmDTO、CancelOrderDTO
- OrderController 新增接口：/api/orders/confirm-payment、/api/orders/{id}/deliver、/api/orders/{id}/complete、/api/orders/{id}/cancel

**影响范围**：
- 后端：订单模块完整功能，与库存模块联动
- 接口：/api/orders（原有）+ confirm-payment/deliver/complete/cancel

**执行人**：AI

**详见**：docs/reference/TROUBLESHOOTING.md（第三节）

---

### [架构决策] - PC 管理端独立项目（blade-admin）

**变更内容**：
- 确定采用独立项目 blade-admin，不集成到 blade-mobile
- 技术选型：Vue3 + Vite + TypeScript + Element Plus
- 项目定位：PC 端后台管理（订单管理、库存管理、商品管理、客户管理、报表）
- blade-mobile 专注移动端 PWA（触屏操作）
- 未来可通过 Monorepo 结构共享 packages/api 和 packages/types

**影响范围**：
- 新增项目：blade-admin/（待搭建）
- 文档更新：02-PRD.md（v1.2）、03-TASKS.md

**执行人**：AI（Agent Teams 讨论结果）

---

### [架构变更] - blade-admin 放弃 vben-admin，从零搭建

**变更内容**：
- 决定放弃 vben-admin 模板（已下载但有 Node 22 兼容性问题）
- 重新搭建 blade-admin，采用 Vue3 + Element Plus + TailwindCSS
- 原因：vben-admin 是大型 monorepo，依赖复杂，AI 调试成本高；用户要求代码可控、全 AI 驱动

**变更原因**：
1. vben-admin 与 Node 22 不兼容（sass-embedded 问题）
2. 大型 monorepo 架构复杂，不适合 AI 主导的开发模式
3. 用户明确要求：从零搭建、代码可控、AI 驱动

**影响范围**：
- blade-admin 项目需要重建
- 新技术栈：Vue3 + Element Plus + TailwindCSS + TailKit

**执行人**：AI（用户决策）

---

### [功能开发] - blade-admin PC 管理端搭建

**变更内容**：
- 使用 degit 下载 vben-admin vue-vben-admin 模板
- 选择 apps/web-ele（Element Plus 版本）
- 配置 API 代理指向 localhost:8080/api
- 关闭 Nitro Mock 服务（VITE_NITRO_MOCK=false）
- pnpm 安装依赖完成
- 启动验证成功：http://localhost:5777/
- API 代理验证成功：登录接口正常返回 token

**影响范围**：
- 新增项目：blade-admin/
- 技术栈：Vue3 + Vite + TypeScript + Element Plus
- vben-admin MIT 协议，可商用

**执行人**：AI

---

### [功能开发] - packages/types 共享类型定义

**变更内容**：
- 创建 packages/types 包
- 定义 auth、order、inventory、product 类型
- blade-mobile 已集成使用 @blade/types
- 编译产物：dist/index.js + dist/index.d.ts

**影响范围**：
- 新增包：packages/types/
- blade-mobile 引用：src/types/* 已改为 re-export

**执行人**：AI

---

### [架构变更] - 放弃 vben-admin，从零搭建 blade-admin

**变更内容**：
- 删除 vben-admin blade-admin
- 从零搭建新 blade-admin（Vue3 + Element Plus + TailwindCSS）
- 完成页面：登录页（深色+玻璃拟态）、布局（侧边栏+头部）、仪表盘（统计卡片）
- 完成占位页面：订单、库存、商品、客户
- 修复 TailwindCSS v4 PostCSS 配置问题
- 修复 Vue reactive 组件警告（使用 shallowRef）

**变更原因**：
1. vben-admin 使用 Node 22 时 sass-embedded 报错，无法解决
2. vben-admin monorepo 结构复杂，AI 调试成本高
3. 用户要求代码可控，AI 全驱动开发

**影响范围**：
- blade-admin 完全重建
- 端口：5777
- 技术栈：Vue3 + Vite + TypeScript + Element Plus + TailwindCSS v4 + Pinia + Vue Router

**执行人**：AI

---

### [功能开发] - Stitch 登录页 UI 集成

**变更内容**：
- 集成 Stitch 设计的登录页 UI（位于 stitch/登录界面/）
- 登录页功能：所属企业（租户选择）、账号、密码、验证码
- 专业主题风格：品牌蓝 #408aee、清晰白底、浅灰背景
- 验证码：前端生成 4 位随机字符，支持刷新
- 登录表单校验：完整填写验证、验证码校验

**设计规范**：
- 品牌色：#408aee
- 背景色：#f5f7f9
- 卡片背景：#ffffff
- 输入框背景：#f3f5f7
- 文字主色：#2c2f31
- 文字副色：#595c5e

**影响范围**：
- blade-admin 登录页：src/views/login/index.vue
- 登录页样式：整合 Stitch 设计 + 自定义 CSS

**执行人**：AI（集成 Stitch 设计）

---

### [文档新增] - blade-admin 测试用例文档

**变更内容**：
- 新增 TEST_CASES.md 测试用例文档
- 包含登录功能 8 个测试用例（LC-001 ~ LC-008）
- 包含路由守卫 2 个测试用例（RC-001 ~ RC-002）
- 包含退出登录 1 个测试用例（LO-001）
- 更新 CLAUDE.md 关联测试用例文档

**影响范围**：
- blade-admin/TEST_CASES.md（新增）
- blade-admin/CLAUDE.md（更新文档关联）

**执行人**：AI

---

### [问题修复] - 多租户登录功能修复

**变更内容**：
1. 修复 admin 用户密码（BCrypt hash 损坏）
2. 更新 V6__tenant_login_support.sql 使用正确的 BCrypt hash
3. 添加租户 2 (demo_tenant) 的 admin 用户
4. 启动 Redis 服务解决 token 存储问题

**测试账号**：
| 租户编码 | 租户名称 | 账号 | 密码 |
|---------|---------|------|------|
| test_tenant | 测试服装公司 | admin | admin123 |
| demo_tenant | 演示服装企业 | admin | admin123 |

**影响范围**：
- 后端：登录认证
- 数据库：sys_user 表

**执行人**：AI

---

### [架构决策] - 系统软删除机制与订单冗余字段

**变更内容**：
1. 决策：系统所有数据实行软删除（deleted=1），禁止物理 DELETE
2. 决策：订单明细表保留 product_name、color_name、size_name 等冗余字段作为快照
3. 决策：商品表禁止物理删除，保证订单可追溯

**影响范围**：
- 后端：所有 Service 层删除操作必须改为逻辑删除
- 前端：删除操作改为软删除确认
- 文档：PRD v1.3 新增"架构决策记录"章节

**执行人**：AI（与用户讨论确认）

---

### [功能完善] - 订单模块重命名与冗余字段填充

**变更内容**：
1. 表名重命名：product_order → sale_order, order_item → sale_order_item
2. 订单状态命名统一：创建(0), 已付款(1), 已发货(2), 已完成(3), 已取消(4), 退货中(5), 已退货(6)
3. 创建订单时填充冗余字段：product_name, sku_code, color_name, size_name
4. V7__order_table_rename.sql 迁移脚本

**影响范围**：
- 后端：Order.java, OrderItem.java, OrderServiceImpl.java
- 数据库：sale_order, sale_order_item 表

**执行人**：AI

---

### [问题修复] - PC 管理端登录页导航和错误处理

**变更内容**：
1. 修复登录成功不跳转：使用 `router.replace('/dashboard')` 替代 `router.push('/')`
2. 修复错误账号密码显示登录成功：client.ts 响应拦截器检查 `code !== 200` 时正确 reject
3. 添加路由守卫调试日志

**影响范围**：
- 前端：blade-admin 登录流程
- 文件：src/api/client.ts、src/views/login/index.vue、src/router/index.ts

**执行人**：AI

---

### [需求讨论] - 线下录单流程支持（定金/送货状态）

**变更内容**：
讨论并确认线下录单流程的线上化方案：

**新增字段**：
- payment_status：支付状态（0未付款 1已付定金 2已付全款）
- deposit_amount：定金金额
- need_delivery：是否需要送货
- delivery_address：送货地址
- is_delivered：是否已送货
- delivered_at：送货时间

**设计决策**：
1. 支付状态与订单状态（处理进度）是两个独立维度
2. payment_status=2(已付全款) 时自动确认付款
3. payment_status=0(未付款) 的订单仍可取消
4. 送货状态与订单状态独立，可单独操作

**影响范围**：
- PRD v1.4 更新
- 订单模块需要新增字段和校验逻辑
- 前端需要新增支付状态单选框、定金输入、送货勾选

**执行人**：AI + 用户讨论

---

### [需求讨论] - OCR 拍照录单功能（P2）

**变更内容**：
讨论并确认 OCR 拍照录单功能的设计方案：

**字段识别难度分析**：
| 字段 | 难度 | 预估准确率 |
|------|------|-----------|
| 款号 | 🔴最难 | 40-60% |
| 客户名称 | 🔴难 | 60-70% |
| 日期 | 🟡中等 | 85%+ |
| 单价 | 🟡中等 | 80%+ |
| 数量 | 🟢较易 | 90%+ |
| 总金额 | 🟢最易 | 90%+ |

**方案 A：半自动录单（推荐）**
- 款号：预设不填，用户手动选择
- 数量/单价/总金额/客户名/日期：自动填入
- 工作量：从"每个字段都填"变成"只选款号"
- 优点：逻辑简单，不依赖 AI，准确率高

**方案 B：AI 辅助全自动（后续探索）**
- OCR 识别 → AI 解析结构 → 模糊匹配商品库 → 置信度标注 → 用户确认
- 高置信度自动填，中置信度填入但待确认，低置信度红色标注手动选择
- 需要 AI 模糊匹配款号，支持缩写/谐音/部分匹配

**方案 C：纯手动录单（基线）**
- 无 OCR，直接手动输入

**决策**：
- 优先级：P2，等订单核心流程跑通后再重点开发
- Phase 1：先做方案 A（半自动），快速可用
- Phase 2：再做方案 B（AI 全自动），更智能

**影响范围**：
- PRD v1.5 新增第九章：OCR 拍照录单
- TASKS.md 新增 BE-116~BE-120

**执行人**：AI + 用户讨论

---

### [功能开发] - 订单模块 API 测试用例

**变更内容**：
- 新增 OrderControllerTest.java
- 24 个测试用例覆盖：
  - 认证测试：登录获取 token
  - 订单列表：分页/筛选/权限验证
  - 订单创建：正常/多商品/无权限/数据验证
  - 订单查询：详情/不存在订单
  - 订单删除：正常/不存在订单
  - 状态流转：创建→付款确认→发货→完成
  - 状态流转：创建→付款确认→取消（库存释放）
  - 异常场景：已发货不可取消/未付款不可发货/未发货不可完成
  - 冗余字段：验证订单明细中 productName、colorName、sizeName、skuCode 等快照字段
- 修复 ProductServiceImpl.convertToVO() 的 SKU 列表填充问题
- 修复商品编码唯一性问题（测试数据使用时间戳）

**影响范围**：
- 后端：订单模块测试
- 文件：src/test/java/com/blade/order/OrderControllerTest.java
- 修改：ProductServiceImpl.java（添加 SKU 列表填充）

**执行人**：AI

---

### [功能开发] - BE-113~BE-115 订单支付状态和配送字段

**变更内容**：
- 新增 V8__order_payment_delivery_fields.sql 迁移脚本
- Order.java 新增字段：paymentStatus, depositAmount, needDelivery, deliveryAddress, isDelivered, deliveredAt
- OrderCreateDTO.java 新增字段：paymentStatus(必填), depositAmount, needDelivery, deliveryAddress
- OrderVO.java 新增字段：paymentStatus, paymentStatusName, depositAmount, needDelivery, deliveryAddress, isDelivered, deliveredAt
- OrderServiceImpl.java 实现逻辑：
  - 创建订单时设置支付状态和配送设置
  - 已付定金(paymentStatus=1)时校验定金金额必须大于0
  - 已付全款(paymentStatus=2)时自动设置paidAmount=totalAmount
  - 订单创建时初始化 isDelivered=0, deliveredAt=null

**影响范围**：
- 后端：订单模块
- 数据库：sale_order 表新增 6 个字段
- 测试：OrderControllerTest.java 更新所有测试用例的 paymentStatus 字段，24 个测试全部通过

**执行人**：AI

---

### [问题修复] - 订单模块配送状态和校验逻辑完善

**变更内容**：
1. deliverOrder 方法修复：发货时设置 isDelivered=1 和 deliveredAt
2. 定金校验完善：新增定金金额不能大于订单总额的校验
3. convertToVO 完善：添加 paymentStatusName 转换方法

**影响范围**：
- 后端：OrderServiceImpl.java
- 测试：24 个订单测试全部通过

**执行人**：AI

---

### [架构设计] - 订单与库存系统解耦方案讨论

**变更内容**：
多角度讨论订单与库存系统解耦设计，明确实际业务场景和设计方向：

**实际业务场景**：
1. 销售开单不选仓库，只看跨仓总量是否有货
2. 仓库库存不够时，线下沟通解决方案（换款/减数量/退款）
3. 配货调整直接在订单明细上修改，记录调整历史

**设计方案**：
1. 新增 order_delivery_plan 表（发货计划）
2. 新增 order_adjustment_log 表（调整记录）
3. 新增 inventory_global_reserve 表（跨仓总量预留）
4. 修改 sale_order、sale_order_item、inventory 表
5. 订单状态新增"配货中-待确认"状态

**并发控制设计**：
1. Redis分布式锁（防并发）
2. 乐观锁版本号（数据一致性）
3. 限流保护（RateLimiter）

**影响范围**：
- 文档：新增 docs/06-ORDER_INVENTORY_DESIGN.md
- 数据库：需要新增3张表，修改3张表
- 后端：库存服务和订单服务需要较大重构
- 前端：订单详情页新增配货调整区块

**执行人**：AI + 用户讨论

---

### [规范更新] - 后端开发规范新增并发控制设计章节

**变更内容**：
1. blade-backend/CLAUDE.md 新增第十章：并发控制设计规范
2. 明确必须考虑并发的场景清单
3. 定义并发控制方案：Redis分布式锁 + 乐观锁
4. 定义锁粒度设计规范
5. 新增设计检查清单，要求AI设计时主动考虑并发

**并发控制设计检查清单**：
- [ ] 是否涉及库存扣减？是否加了Redis锁？
- [ ] 是否涉及库存预留/释放？是否加了Redis锁？
- [ ] 是否有"查询-判断-更新"逻辑？是否改为原子操作？
- [ ] 是否有唯一性校验？是否加了数据库唯一约束？
- [ ] 高并发场景是否加了限流保护？

**影响范围**：
- blade-backend/CLAUDE.md
- 所有后续开发必须遵守此规范

**执行人**：AI

---

### [需求讨论] - 库存系统扩展功能问题清单

**变更内容**：
讨论并记录库存管理系统中可能需要的 10 个扩展功能点：

1. **批次管理（先进先出）**：同一SKU不同批次的成本/有效期管理
2. **库存成本计算**：采购成本跟踪、销售利润计算
3. **负库存问题**：是否允许库存为0时仍可出库
4. **库存盘点**：盘盈盘亏处理、实际库存与系统库存差异
5. **仓库调拨**：商品在仓库之间的转移
6. **库存冻结/锁定**：质量问题/客户预留/促销活动的临时冻结
7. **退货处理**：退货入库、退款流程
8. **权限控制**：仓库级数据权限细分
9. **日志与审计**：完整的库存变动记录
10. **数据一致性校验**：定期校验系统与实际一致性

**影响范围**：
- 新增文档：docs/07-INVENTORY_EXTENDED_QUESTIONS.md
- 待业务方确认哪些功能需要实现

**执行人**：AI（用户决策）

---

### [功能开发] - BA-201 订单列表页开发

**变更内容**：
1. 参考 Stitch 设计的 UI 搭建订单列表页面
2. 实现页面元素：
   - 页面标题区（标题 + 描述 + 刷新/新建按钮）
   - 筛选区域（关键字搜索、订单状态下拉、支付状态下拉、日期范围、重置筛选）
   - 订单表格（订单编号、客户、订单金额、已付金额、订单状态、支付状态、配送、操作）
   - 分页组件
3. Material Symbols 图标字体集成
4. 状态标签组件（订单状态、支付状态）
5. 修复 TailwindCSS v4 样式兼容性问题

**影响范围**：
- blade-admin/src/views/orders/index.vue
- blade-admin/src/api/order.ts（新增）
- blade-admin/src/styles/main.css
- blade-admin/tsconfig.app.json

**执行人**：AI

---

### [功能开发] - BA-203 新建订单页 + 客户搜索功能

**变更内容**：
1. 新建订单页面布局（参考 Stitch 设计）：
   - 左侧8列：客户信息、支付信息、商品明细
   - 右侧4列：金额汇总、状态卡片、送货设置、图片上传、备注信息
2. 支付状态联动逻辑：
   - 切换到"未付款"→清零定金金额和已付金额
   - 切换到"已付定金"→清零已付金额
   - 切换到"已付全款"→清零定金金额
3. 客户电话搜索匹配：
   - 输入电话后 blur 时搜索客户
   - 有匹配：自动填充名称/地址，显示"已匹配客户"标签，字段锁定
   - 无匹配：清空名称和地址，解锁字段，当作新客户处理
4. 电话号码 normalize：
   - 去掉 +、空格、- 等字符，只保留纯数字搜索
   - 支持国际号码：+86 138-0000-1111 → 8613800001111

**影响范围**：
- blade-admin/src/views/orders/new.vue
- blade-admin/src/api/customer.ts（新增）
- blade-admin/src/api/order.ts（更新 OrderCreateDTO）

**执行人**：AI

---

### [功能开发] - BE-401~BE-403 客户模块开发

**变更内容**：
1. 数据库设计：
   - crm_customer 表：客户基本信息
   - crm_customer_phone 表：一个客户可有多个电话（is_primary 区分主号）
   - V9__customer_module.sql 迁移脚本
2. 后端接口：
   - GET /api/customers/search?phone：按电话搜索客户
   - POST /api/customers：创建客户
3. 电话号码 normalize：
   - 存储时去掉 +-空格
   - 搜索时也 normalize
4. SecurityConfig：/api/customers/** 放行

**影响范围**：
- 后端：com.blade.customer 模块（entity/mapper/service/controller）
- 数据库：crm_customer、crm_customer_phone 表

**执行人**：AI

---

### [功能开发] - PC 管理端侧边栏优化

**变更内容**：
1. 侧边栏两种宽度状态：收起(64px图标)、展开(自适应文字宽度)
2. 移除侧边栏"新增订单"按钮（已移至订单列表页）
3. 订单列表页点击新建按钮跳转 /orders/new

**影响范围**：
- blade-admin/src/views/layout/index.vue

**执行人**：AI

---

### [功能开发] - 客户模块国际化升级

**变更内容**：

1. **数据库变更**（V24 迁移）：
   - `crm_customer` 表新增 `country_code`（区号，如+86）、`country_name`（国家名称）
   ```sql
   ALTER TABLE crm_customer
     ADD COLUMN country_code VARCHAR(8)  COMMENT '国家区号，如+86',
     ADD COLUMN country_name VARCHAR(64) COMMENT '国家名称，如China';
   ```

2. **后端实体/DTO**：
   - `Customer.java`：新增 `countryCode`、`countryName` 字段
   - `CustomerCreateDTO.java`：新增 `countryCode`
   - `CustomerUpdateDTO.java`：新增 `countryCode`
   - `CustomerVO.java`：新增 `countryCode`、`countryName`、`countryFlag`（计算属性，根据区号返回国旗emoji）

3. **后端新接口**：
   - `GET /api/customers/{id}/stats`：客户基础统计（订单数/消费总额/完成订单数/时间范围）
   - `GET /api/customers/{id}/orders`：客户历史订单列表（完整订单项信息）
   - `GET /api/customers/{id}/preference`：客户商品偏好分析（颜色/尺码/品类偏好，支持已发货/已完成订单统计）

4. **前端国家选择器**：
   - 新建 `CountryCodeSelect.vue`：WhatsApp 风格可搜索下拉（el-popover + 搜索输入框）
   - 新建 `countries.ts` 数据文件：~140 个国家/地区数据，包含 ISO 代码和 emoji 国旗
   - 支持搜索：国家名（中英文）、区号实时筛选

5. **客户列表页**：
   - 新增"国家"列，显示区号（如 +86）
   - 电话列显示完整格式：🇨🇳 +86 13800001111

6. **新建/编辑客户弹窗**：
   - 新增"国家区号"字段，使用 CountryCodeSelect 组件
   - 电话输入框前缀显示当前选中区号

7. **客户详情页**（`/customers/:id`）：
   - 3 个独立 Tab：基本信息 / 订单记录 / 商品偏好
   - 基本信息：国家区号、地址、备注等完整信息
   - 订单记录：历史订单列表，含金额、状态、商品明细
   - 商品偏好：颜色/尺码/品类偏好柱状图

**影响范围**：
- 数据库：`V24__customer_add_country.sql`
- 后端：com.blade.customer 模块（entity/dto/service/controller）
- 前端：api/customer.ts、views/clients/index.vue、views/customers/detail.vue、components/CountryCodeSelect.vue、data/countries.ts、router/index.ts、views/layout/index.vue

**执行人**：AI

---

## 客户模块技术设计

### 客户与订单关联关系

```
客户表 (crm_customer)  1:N  订单表 (order)
       │                        │
       │◄── customer_id ───────►│
       │                        │
       │              1:N        │
       │◄── order_id ─────────►│
       │                        │
  电话表                     订单项表
(crm_customer_phone)      (order_item)
```

**关联字段**：`Order.customer_id` → `Customer.id`

### 客户商品偏好计算逻辑

**数据来源**：
```java
// 只统计已完成/已发货的订单
orderWrapper.in(Order::getStatus, Arrays.asList(4, 5));  // 已发货、已完成
```

**偏好类型**：
1. **颜色偏好** (`colors`)：统计 `OrderItem.colorName`
2. **尺码偏好** (`sizes`)：统计 `OrderItem.sizeName`
3. **品类偏好** (`categories`)：统计 `OrderItem.productName`

**百分比计算**：
```java
percentage = (该偏好count / 总订单项数) * 100
// 保留1位小数，最多返回top 10
```

**示例**：
```
订单项总数：40件
颜色统计：黑色18件、白色10件、蓝色7件
百分比：黑色 45%、白色 25%、蓝色 17.5%
```

---

## 2026-04-26 变更记录

### [规划] - 客户模块优化计划

**变更内容**：
完成客户模块国际化（Phase 4.5）后，深度分析发现以下优化方向，制定 Phase 4.6 优化计划：

**M1: 数据质量（P1）**：
- BE-412: 电话重复检查 — 创建/更新客户时校验租户内电话唯一
- BE-413: 删除客户订单保护 — 删除前检查进行中订单
- BE-414: N+1 查询优化 — getPreference() 改用单条 IN 查询

**M2: 用户体验（P2）**：
- BE-415: 订单记录分页 — 客户详情订单记录支持分页
- BE-416: 常用国家置顶 — localStorage 记忆常用国家
- BE-417: 国家选择器键盘导航 — ↑↓/Enter/Esc 支持

**M3: 业务功能（P2）**：
- BE-418: 客户标签功能 — 新建标签表和关联表，支持客户打标签
- BE-419: 沉默客户预警 — 仪表盘新增沉默客户统计（>90天无订单）
- BE-420: 偏好时间范围筛选 — 偏好分析支持自定义时间范围

**M4: 架构能力（P3）**：
- BE-421: 客户数据权限 — 支持「只看我的客户」
- BE-422: 操作审计日志 — 客户增删改记录到日志表
- BE-423: 偏好数据缓存 — Redis 缓存偏好结果，TTL=1小时

**详细文档**：docs/08-CUSTOMER_OPTIMIZATION.md

**影响范围**：
- 数据库：V25（唯一索引）、V26（标签表）
- 后端：com.blade.customer 模块优化
- 前端：客户模块交互优化

**执行人**：AI

---

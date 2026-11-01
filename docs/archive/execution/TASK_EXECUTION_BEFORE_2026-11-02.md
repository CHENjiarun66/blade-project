# 早期任务执行明细

> 文档状态：历史归档。保留当时的审核、方案或交付证据，不作为当前实现状态、待办认领或生产操作指令。当前入口见 [文档中心](../../01-README.md)。


## 任务执行记录

> AI 完成每个任务后，在下方记录执行时间和结果。

### 执行模板

```markdown
### {任务ID} - {任务名称}
- 执行时间：YYYY-MM-DD HH:mm
- 执行结果：✅ 完成 / ❌ 失败
- 备注：如有问题在此说明
```

### 记录

### BE-1001 - 数字资产表结构扩展
- 执行时间：2026-06-03 13:44
- 执行结果：✅ 完成
- 备注：Hermes Agent 按 Codex SOW 新增 V35 文件中心资产表结构迁移，补充文件夹、业务绑定、操作日志、清理日志实体和 Mapper；新增 FileAssetSchemaTest，Codex 复核并运行 FileAssetSchemaTest + FileControllerTest 通过。

### BE-1002 - 文件中心分页/详情 API
- 执行时间：2026-06-03 13:58
- 执行结果：✅ 完成
- 备注：Hermes Agent 按 Codex SOW 新增文件分页/详情接口、FilePageDTO/FileVO 和 Controller 测试；Codex 复核后将 businessType/bound 查询收口到 file_business_bind，并独立运行 FileControllerTest + FileAssetSchemaTest 通过。

### BE-1003 - 文件夹管理 API
- 执行时间：2026-06-03 14:07
- 执行结果：✅ 完成
- 备注：Hermes Agent 按 Codex SOW 新增文件夹树、新建、更新、删除 API；Codex 复核后补强租户/软删除边界并新增 FileFolderServiceImplTest，最终 FileFolderControllerTest + FileFolderServiceImplTest + FileControllerTest + FileAssetSchemaTest 通过。

### BE-1004 + BE-1006 - 多业务绑定与文件批量操作 API
- 执行时间：2026-06-03 15:49
- 执行结果：✅ 完成
- 备注：Hermes Agent 按 Codex SOW 新增多业务绑定和批量操作 API；Codex 复核后补强租户/解绑边界并运行 FileBindingControllerTest + FileBindingServiceImplTest + FileFolderControllerTest + FileFolderServiceImplTest + FileControllerTest + FileAssetSchemaTest 通过。

### BE-1005 - 商品/SKU 图片绑定服务
- 执行时间：2026-06-03 16:25
- 执行结果：✅ 完成
- 备注：Hermes Agent 执行，Codex 复核后补强重复 fileId、主图替换、createBy 和 Controller DTO 透传测试。新增 PUT /api/products/{id}/file-bindings，支持主图(main)/图集(gallery)/SKU图片(sku_image)替换语义绑定，product.image_url fileId 同步到 file_business_bind，非数字历史 URL 忽略。

### BE-1010 - 基础视频文件支持
- 执行时间：2026-06-03 16:37
- 执行结果：✅ 完成
- 备注：Hermes Agent 执行，Codex 复核后补齐 application.yml 运行配置中的 video/mp4、video/webm、video/quicktime，并补充 FileControllerTest 上传响应 fileType/fileExt 断言。Claude Code 小范围试跑补充 FileAllowedTypesRegressionTest，防止运行配置 allowed-types 漂移。FileServiceImpl.upload 自动分类 fileType（IMAGE/VIDEO/OTHER）和 fileExt；FileUploadVO 新增 fileType/fileExt。`FileVideoSupportTest + FileControllerTest + FileAssetSchemaTest + FileCleanup*Test` 共 49/49 测试通过；`File*Test` 共 82/82 测试通过。

### BE-1009A - 文件预览 PUBLIC/PRIVATE 基础收口
- 执行时间：2026-06-03 18:11
- 执行结果：✅ 完成（BE-1009 部分完成）
- 备注：Claude Code 执行初稿，Codex 复核后修正真实认证主体判断和测试预期。`/api/files/{id}/preview` 保持可进入 Controller，PUBLIC 文件可匿名预览；PRIVATE/null 文件必须是已认证用户；租户/status 过滤继续由 `FileService.getActiveFile(id)` 执行。未改 SecurityConfig，未做业务权限映射，BE-1009 仍需继续收口业务权限。`FileControllerTest` 11/11 通过；`File*Test` 86/86 通过。

### BE-1009B - 文件预览业务权限映射
- 执行时间：2026-06-03 18:35
- 执行结果：✅ 完成（BE-1009 全部完成）
- 备注：Claude Code 执行，Codex 复核后补齐 temp/unknown 绑定走 viewOwn 的边界。新增 BUSINESS_PERMISSION_MAP（product/sku→menu:product、order→btn:order:view、inventory_log→btn:inventory:viewLog、ocr_document→menu:file）；checkBusinessPermission() 先查 viewAll 绕过，再查 file_business_bind 绑定映射，无绑定时回退 file_storage.businessType，unbound/temp/unknown 仅靠 viewOwn+可靠 userId；getReliableUserId() 不 fallback 到 1L。FileService 新增 getActiveBindings()。`FileControllerTest` 20/20 通过；`File*Test` 95/95 通过。

### BE-1009C - 浏览器原生私有图片预览认证
- 执行时间：2026-06-04 17:20
- 执行结果：✅ 完成（BE-1009 预览链路补强）
- 备注：修复文件已保存但前端图片不显示的问题。根因是 `<img>` 标签和 `window.open()` 不会携带 Axios `Authorization` 头，PRIVATE 文件预览进入 Controller 后被判定未登录。`JwtAuthenticationFilter` 仅对 `/api/files/{id}/preview` 接受 `previewToken` 查询参数；`filePreviewUrl(fileId)` 统一拼接当前 access token；文件中心“打开原文件”和 Catalog 预览地址同步接入。`JwtAuthenticationFilterTest + FileControllerTest` 22/22 通过；Playwright 验证 `/files` 7/7 张图加载成功、`/products` 1/1 张 fileId 图片加载成功。

### BE-1011 - 文件中心回归测试
- 执行时间：2026-06-04 09:57
- 执行结果：✅ 完成
- 备注：Claude Code 执行尝试因预算上限中断，Codex 接手完成。确认现有文件中心回归覆盖上传、列表/详情、绑定、文件夹、未绑定治理、清理标记、视频类型和私有预览权限；补齐批量删除保护，`FileBindingServiceImpl.batchDelete()` 发现当前租户有效绑定时拒绝软删除，新增 `batchDelete_rejectsActiveBoundFiles` 回归测试。`FileBindingServiceImplTest` 13/13 通过；`File*Test` 96/96 通过。

### BE-1013 + BE-1014 - 商品管理 v2 后端 Slice 1
- 执行时间：2026-06-14 15:47
- 执行结果：✅ 完成
- 备注：Claude Code 实现，Codex 两轮审核后补正租户过滤、businessType 分离、空颜色/尺码禁用 SKU 和空数组返回等问题。新增 GET /api/products/{id}/file-bindings（返回主图/图集/SKU图片分组）、PUT /api/products/skus（单个SKU更新 price/costPrice/barCode/status）；修复 syncProductSkus 保留已有 SKU 字段不覆盖；delete/deleteColor/deleteSize 添加引用保护；ProductServiceV2Test 26/26 通过，ProductFileBindingServiceTest 11/11 通过，ProductFileBindingControllerTest 2/2 通过。新增文件：ProductFileBindingsVO.java、SkuUpdateDTO.java、ProductServiceV2Test.java。

### BE-001 ~ BE-008 - 后端骨架搭建
- 执行时间：2026-03-21 18:10
- 执行结果：✅ 完成
- 备注：Spring Boot 3 项目初始化完成，包含 pom.xml、application.yml、SecurityConfig、MybatisPlusConfig、多租户配置、统一响应 R.java、认证接口、Flyway 迁移脚本

### BE-007 - 用户 CRUD 接口
- 执行时间：2026-03-21 19:00
- 执行结果：✅ 完成
- 备注：UserController（/api/system/users）、UserService、UserServiceImpl、UserVO/UserCreateDTO/UserUpdateDTO/UserPageDTO、RoleMapper 新增用户角色关联方法

### BE-101 ~ BE-104 - 订单系统接口（旧版，需重构）
- 执行时间：2026-03-21 19:10
- 执行结果：⚠️ 已完成但需重构
- 备注：旧版订单系统不符合新设计（无SKU、颜色尺码、库存联动），需要按 PRD v1.1 重构

### BE-201 ~ BE-206 - 商品模块
- 执行时间：2026-03-21 19:40
- 执行结果：✅ 完成
- 备注：ProductController、ProductService、ProductServiceImpl、商品分类/颜色/尺码/SKU 实体、DTO、Mapper，V3__product_module.sql（含默认颜色尺码和测试商品）

### BE-201 ~ BE-206 - 商品模块验证
- 执行时间：2026-03-21 21:20
- 执行结果：✅ 完成
- 备注：API 全面测试通过，修复了多个问题：UserDetailsServiceImpl 角色加载、sys_user_role.tenant_id、admin 密码、product.price 字段、关联表 tenant_id、@PutMapping 路径注解、Java 17 安装

### BE-007 - 用户管理接口验证
- 执行时间：2026-03-21 21:20
- 执行结果：✅ 完成
- 备注：修复 UserDetailsServiceImpl 角色加载 null 问题，登录功能正常

### 订单模块 API 测试用例
- 执行时间：2026-03-22 22:00
- 执行结果：✅ 完成
- 备注：
  - 新增 OrderControllerTest.java，24 个测试用例
  - 覆盖：认证、CRUD、状态流转、库存联动、异常场景
  - 修复 ProductServiceImpl.convertToVO() SKU 列表填充问题
  - 修复商品编码唯一性问题（测试使用时间戳）

### 线下录单流程需求讨论
- 执行时间：2026-03-23
- 执行结果：✅ 完成
- 备注：
  - 讨论并确认线下录单流程的线上化方案
  - 新增支付状态（未付款/已付定金/已付全款）
  - 新增定金金额、是否需要送货、已送货状态等字段
  - PRD v1.4 更新
  - TASKS.md 新增 BE-113~BE-115

### OCR 拍照录单功能讨论
- 执行时间：2026-03-23
- 执行结果：✅ 完成
- 备注：
  - 讨论 OCR 识别各字段的难度（款号最难，数量/金额最易）
  - 确定方案 A（半自动）：款号手动选，其他自动填
  - 确定方案 B（AI 全自动）：后续探索，OCR + AI 模糊匹配
  - 优先级 P2，等订单核心流程跑通后再重点开发
  - PRD v1.5 新增第九章 OCR 拍照录单
  - TASKS.md 新增 BE-601~605, FE-601~602, BA-701~702

### BE-301 ~ BE-309 - 库存模块开发
- 执行时间：2026-03-21 23:40
- 执行结果：✅ 完成
- 备注：仓库CRUD、库存查询、入库（支持图片+供应商）、出库（ORDER/OTHER+reason）、直接调整、预留锁定/释放、库存预警

### BE-101 ~ BE-110 - 订单模块重构（对接库存联动）
- 执行时间：2026-03-21 23:51
- 执行结果：✅ 完成
- 备注：
  - V5__order_refactor.sql：订单表新增 customer_id, paid_amount, warehouse_id, pay_time, confirm_time, deliver_time, complete_time；订单明细表新增 sku_code, color_name, size_name
  - OrderServiceImpl：重构 create/updateStatus/delete 方法，新增 confirmPayment/deliverOrder/completeOrder/cancelOrder 方法
  - 库存联动：付款确认→reserveInventory()、发货→outInventory()、取消→releaseInventory()
  - 全流程测试通过：创建→付款确认→发货→完成，创建→付款确认→取消（库存正确释放）

### BE-201 ~ BE-206 - 商品模块测试用例
- 执行时间：2026-03-21 23:16
- 执行结果：✅ 完成
- 备注：编写 ProductControllerTest，15 个测试用例覆盖：登录、列表查询、创建商品（普通/带SKU/不带SKU）、更新商品、删除商品、查询商品、异常场景

### FE-001, FE-005~FE-007 - 移动端骨架搭建
- 执行时间：2026-03-22 00:30
- 执行结果：✅ 完成
- 备注：
  - Vue3 + Vite + TypeScript + Vuetify 4 + Pinia + Vue Router
  - PWA 配置：vite-plugin-pwa + Service Worker + Web App Manifest
  - 路由：/login, /orders, /orders/create, /orders/:id, /inventory, /inventory/in, /inventory/out, /products, /dashboard
  - 页面组件：Login, Layout（底部导航）, OrderList, OrderCreate, OrderDetail, InventoryList, InventoryIn, InventoryOut, ProductList, Dashboard
  - API 层：client（Axios 封装），auth/order/inventory/product API
  - 类型定义：auth, order, inventory, product 类型
  - 构建产物：dist/（PWA + 24个资源文件）

### 架构讨论 - PC 管理端方案
- 执行时间：2026-03-22 01:00
- 执行结果：✅ 完成
- 备注：
  - Agent Teams 讨论确定：采用独立项目 blade-admin（方案 B）
  - 推荐 Monorepo 结构：packages/api + packages/types 共享
  - blade-mobile 专注移动端，blade-admin 专注 PC 端
  - 技术选型：Vue3 + Element Plus（PC端更合适）

### PRD v1.2 更新 - 前端架构规划
- 执行时间：2026-03-22 01:15
- 执行结果：✅ 完成
- 备注：
  - 更新项目概述：新增 Monorepo 结构说明
  - 更新技术栈：移动端（Vuetify 4）、PC端（Element Plus）
  - 新增前端项目定位表格
  - 新增版本历史 v1.2

### packages/types - 共享类型定义
- 执行时间：2026-03-22 01:30
- 执行结果：✅ 完成
- 备注：
  - 创建 packages/types 包，包含：auth、order、inventory、product 类型定义
  - blade-mobile 已集成使用 @blade/types
  - 编译产物：dist/（.js + .d.ts）
  - 以后 blade-admin 也可以复用这些类型

### BA-203 - PC 管理端新建订单页开发

- 执行时间：2026-03-23
- 执行结果：✅ 完成
- 备注：
  - 参考 Stitch 设计搭建新建订单页面
  - 左侧8列：客户信息（电话搜索匹配客户）、支付信息（支付状态单选+定金/已付金额）、商品明细（SKU选择表格）
  - 右侧4列：金额汇总卡片、状态卡片、送货设置、图片上传、备注信息
  - 支付状态联动逻辑：切换状态自动清零相关金额
  - 客户电话搜索：blur 时搜索客户，自动填充名称/地址并锁定；无匹配时解锁可编辑
  - 电话号码 normalize：支持 +86、+1 等国家代码，去掉 +-空格后搜索
  - 字段样式：输入框背景 rgb(243 244 246)，圆角12px，文字深灰色

### BA-201 - PC 管理端订单列表页开发

- 执行时间：2026-03-23
- 执行结果：✅ 完成
- 备注：
  - 集成 Stitch 设计 UI（专业主题 + 品牌蓝 #408aee）
  - 筛选区域：关键字搜索、订单状态下拉、支付状态下拉、日期范围、重置筛选
  - 2026-08-17 补充：筛选区新增“确认筛选”按钮，关键字回车提交；日期范围传入后端并按 `order_date` 查询，旧数据为空时回退 `create_time`；同一关键字按订单号或客户名匹配。
  - 表格：订单编号、客户、订单金额、已付金额、订单状态、支付状态、配送、备注、图片
  - 分页组件 + 状态标签组件
  - 侧边栏：收起/展开两种宽度状态

### BE-401~BE-403 - 客户模块开发

- 执行时间：2026-03-23
- 执行结果：✅ 完成
- 备注：
  - V9__customer_module.sql：crm_customer + crm_customer_phone 表
  - 一个客户可有多个电话号码（is_primary 区分主号）
  - 客户电话表支持 tenant_id 多租户
  - GET /api/customers/search?phone：按电话搜索，自动 normalize（去掉 +-空格）
  - POST /api/customers：创建客户同时创建主电话
  - 前端：searchCustomerByPhone + createCustomer API
  - SecurityConfig：/api/customers/** 放行

### BA-004 - PC 管理端登录页 + 多租户登录
- 执行时间：2026-03-22 18:30
- 执行结果：✅ 完成
- 备注：
  - 集成 Stitch 设计的登录页 UI（专业主题 + 企业/账号/密码/验证码）
  - 实现多租户登录：test_tenant、demo_tenant
  - 修复登录跳转问题：router.replace('/dashboard')
  - 修复错误响应处理：client.ts 检查 code !== 200 时 reject
  - 修复密码 BCrypt hash：使用正确 hash 更新数据库
  - 修复 Redis 未启动问题

### 后端多租户登录支持
- 执行时间：2026-03-22 18:30
- 执行结果：✅ 完成
- 备注：
  - AuthService.login() 支持 tenantCode 查询租户后查询用户
  - V6__tenant_login_support.sql 更新正确的 BCrypt hash
  - 添加 demo_tenant 的 admin 用户

### BA-301~BA-305 - PC 管理端库存页面
- 执行时间：2026-03-24
- 执行结果：✅ 完成
- 备注：
  - 库存列表页：表格 + 仓库/预警状态筛选 + 分页
  - 入库操作：弹窗 + SKU选择 + 多商品入库
  - 出库操作：弹窗 + SKU选择 + reason字段 + 多商品出库
  - 库存调整：弹窗 + 盘盈盘亏 + 调整原因
  - 库存记录：弹窗查询变动日志
  - 后端新增：InventoryLogVO, InventoryLogPageDTO, /api/inventory/logs, /api/products/skus

### BA-401~BA-406 - PC 管理端商品管理页面
- 执行时间：2026-03-24
- 执行结果：✅ 完成
- 备注：
  - 商品列表页：表格 + 分类/状态筛选 + 分页 + 新建/编辑商品弹窗
  - 商品编辑页：颜色/尺码选择 + SKU 生成预览
  - 颜色列表页：颜色管理 + 新建/编辑弹窗
  - 尺码列表页：尺码管理 + 新建/编辑弹窗
  - 商品分类页：分类管理 + 新建/编辑弹窗
  - 后端新增：ProductCategoryController, ProductCategoryService, ProductCategoryVO

### BA-501~BA-502 - PC 管理端客户管理页面
- 执行时间：2026-03-24 14:15
- 执行结果：✅ 完成
- 备注：
  - 后端新增 CustomerPageDTO, CustomerCreateDTO, CustomerUpdateDTO
  - 后端 CustomerService 新增 pageList, getById, updateCustomer, deleteCustomer 方法
  - 后端 CustomerController 新增 GET /customers, GET /customers/{id}, PUT /customers, DELETE /customers/{id}
  - 前端 customer.ts API 全面更新
  - 前端 clients/index.vue：真实 API 调用 + 新建/编辑弹窗 + 删除确认 + 分页

### BA-205 - 订单显示开单人员
- 执行时间：2026-03-24 14:30
- 执行结果：✅ 完成
- 备注：
  - 订单列表增加"开单人员"列，展示 salesmanName
  - OrderVO 已有 salesmanName 字段，无需修改后端
- 执行时间：2026-03-24
- 执行结果：✅ 完成
- 备注：
  - 商品列表页：表格 + 分类/状态筛选 + 分页 + 新建/编辑商品弹窗
  - 商品编辑页：颜色/尺码选择 + SKU 生成预览
  - 颜色列表页：颜色管理 + 新建/编辑弹窗
  - 尺码列表页：尺码管理 + 新建/编辑弹窗
  - 商品分类页：分类管理 + 新建/编辑弹窗
  - 后端新增：ProductCategoryController, ProductCategoryService, ProductCategoryVO

### Bug 修复 - 订单分页 + TypeScript 编译错误
- 执行时间：2026-03-24
- 执行结果：✅ 完成
- 备注：
  - 修复订单分页无效：el-pagination 缺少 @current-change 事件
  - 修复 TypeScript 错误：orders/detail.vue 移除未使用 deleteOrder、orders/new.vue 移除未使用 filteredSkus/addProduct
  - 修复 warehouseId 类型问题、API 响应结构问题

### BE-117~BE-122 - 库存并发控制修复（P0）
- 执行时间：2026-03-24 14:00
- 执行结果：✅ 完成
- 备注：
  - 添加 Redisson 依赖（pom.xml）
  - 创建 RedissonConfig 配置类
  - 创建 V11 数据库迁移脚本（inventory 表添加 version 字段）
  - Inventory Entity 添加 @Version 字段 + get/set 方法
  - MybatisPlusConfig 启用 OptimisticLockerInnerInterceptor
  - InventoryServiceImpl 所有库存操作方法（in/out/adjust/reserve/release）全部添加：
    - Redis 分布式锁（inventory:lock:{skuId}:{warehouseId}）
    - 乐观锁校验（version 字段）
  - adjust() 新增：调整后库存不能为负校验
  - release() 新增：预留数量不足校验
  - 编译验证通过

### BE-129~BE-131 - 配货计划后端实现
- 执行时间：2026-03-31
- 执行结果：✅ 完成
- 备注：
  - OrderDeliveryPlanServiceImpl 实现配货计划 CRUD + 确认/取消调整
  - confirmAdjustment 修复：同步仓库信息到 order_items（修复 500 错误）
  - 完整订单流转验证：CREATED(0) → PAID(1) → ADJUSTMENT_PENDING(2) → READY_TO_SHIP(3) → DELIVERED(4) → COMPLETED(5)
  - 库存扣减验证通过：SKU 101: 100→85, SKU 102: 50→46

### 前端配货计划实现（BA-210/BA-211）
- 执行时间：2026-03-31
- 执行结果：✅ 完成
- 备注：
  - blade-admin/src/api/order.ts 新增 DeliveryPlanVO/DeliveryPlanDTO/AdjustmentLogDTO 类型
  - 新增 7 个配货计划 API 函数：createDeliveryPlan/updateDeliveryPlan/getDeliveryPlan/deleteDeliveryPlan/confirmAdjustment/cancelAdjustment/getAdjustmentLogs/recordAdjustment
  - detail.vue 新增配货计划区块、调整记录区块
  - 改造按钮逻辑：status=1 显示"创建配货计划"，status=2 显示确认/取消调整
  - 新增辅助函数：adjustmentStatusName/adjustmentStatusTagClass/planStatusName/deliveryPlanStatusTagClass/adjustmentTypeName
  - 修复 TypeScript 错误：移除未使用的 import 和变量

### BA-1004~BA-1006 - PC 文件中心上传/移动/删除/绑定/清理
- 执行时间：2026-06-04
- 执行结果：✅ 完成
- 备注：
  - 扩展 blade-admin/src/api/file.ts：新增 batchDeleteFiles/batchMoveFiles/getFileBindings/createFileBindings/deleteFileBinding/getUnboundCandidates/softDeleteUnbound/createFileFolder；FileUploadVO 新增 fileType/fileExt 字段
  - 扩展 blade-admin/src/api/product.ts：新增 ProductFileBindingDTO/SkuImageBindingDTO 和 setProductFileBindings()
  - files/index.vue：上传按钮改为隐藏多文件 input + loading 态；网格视图新增 el-checkbox 多选；列表视图新增 type=selection 列；批量工具栏（移动/绑定/删除/取消选择）；移动弹窗选文件夹或未归档；删除前先查绑定/展示风险详情弹窗/确认后调 batch-delete；绑定按钮打开 FileBindDialog；清理按钮打开 FileCleanupPanel
  - FileBindDialog.vue：remote 搜索商品→选角色 main/gallery/sku_image→sku_image 时显示 SKU 多选→PUT /api/products/{id}/file-bindings
  - FileCleanupPanel.vue：清理说明/保留天数/候选统计刷新/软删除确认/回收站快捷入口
  - npm run build 通过；所有 API 仅使用 POST batch-delete（非 DELETE /api/files/{id}）

### DEPLOY-001 - 群晖 NAS 生产部署骨架
- 执行时间：2026-06-05
- 执行结果：✅ 完成
- 备注：
  - 新增 `deploy/nas/docker-compose.prod.yml`，在 `/volume2/blade` 独立运行 MySQL、Redis、后端和前端 Nginx
  - 新增 `deploy/nas/nginx/default.conf`，统一代理 `/api/` 到后端，前端 SPA 使用 `try_files` 回退
  - 新增 `blade-backend/Dockerfile` 和 `blade-admin/Dockerfile`
  - 新增 `deploy/nas/.env.prod.example`、`deploy/nas/README.md` 和 `deploy/nas/deploy_from_local.sh`
  - 后端 `JWT_SECRET` 支持通过生产环境变量覆盖
  - NAS 目标端口：`8899`；生产访问入口：`http://192.168.1.10:8899/catalog`
  - 本地验证：`mvn clean package -DskipTests` 通过；`npm run build` 通过
  - NAS 实际部署验证：前端 `/catalog` 200；登录后 `/api/catalog/filters` 200；四个容器 `blade-mysql/blade-redis/blade-backend/blade-web` 均 Up
  - 群晖部署注意：NAS 拉 Docker Hub 超时，发布脚本已改为本机 `linux/amd64` 离线镜像包 + NAS `docker load`；Synology `scp` 使用 `scp -O`
  - 部署目录已迁移到存储空间 2 共享文件夹 `/volume2/blade`
  - 数据库已从本机生产库 `blade_project_prod` 迁移到 NAS `blade_project_prod`；本机生产库未修改；导入 SQL 中将主租户 `tenant_id=1` 的 `tenant_code` 调整为 `dwy_jiajiadress`
  - NAS 导入前备份：`/volume2/blade/db-backups/nas_blade_project_prod_before_import_20260605.sql`
  - 数据迁移验证：`product=164`、`product_sku=416`、`sale_order=81`、`file_storage=22`、`flyway=39`；`dwy_jiajiadress/admin/admin123` 登录后 Catalog API 200

### DEPLOY-002 - NAS 生产运维手册
- 执行时间：2026-06-05
- 执行结果：✅ 完成
- 备注：
  - 新增 `docs/13-NAS_PRODUCTION_OPS.md`，作为后续 Agent 发布、备份、迁移和回滚的专用运维手册
  - 覆盖 NAS 基础信息、生产目录、容器、数据库边界、项目架构、首次部署、日常发布、GitHub/Gitee/本机归档代码来源、数据库迁移、uploads 迁移、回滚、常见问题和 Agent 操作红线
  - 明确日常发布只更新 `blade-backend:prod` 和 `blade-web:prod`，不重建 MySQL/Redis，不删除生产数据目录
  - 已在 `docs/01-README.md` 和 `docs/SESSION_CONTEXT.md` 加入索引
  - 已同步关键文档快照到 NAS：`/volume2/blade/docs`，并新增 `README_FOR_AGENTS.md` 作为 NAS 侧 Agent 接手入口

---

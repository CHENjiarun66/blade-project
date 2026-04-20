# 开发任务清单

> AI 根据本文档领取任务、自主执行、主动更新状态。
> 新 AI 来了一定要先读本文档，了解当前进度。

---

## 任务领取规则

1. **自主领取**：AI 根据状态自行领取 `TODO` 任务
2. **主动更新**：完成任务后立即更新状态
3. **交接同步**：任务状态变更必须同步到本文档
4. **阻塞上报**：遇到阻塞立即在本文档注明，并通知用户

---

## 项目阶段

| 阶段 | 状态 | 说明 |
|------|------|------|
| 技术方案讨论 | ✅ 完成 | 多 Agent 团队讨论，决策已锁定 |
| 文档体系建设 | ✅ 完成 | 完整文档体系建立 |
| 后端骨架搭建 | ✅ 完成 | Spring Boot 3 项目初始化 |
| 商品模块开发 | ✅ 完成 | 颜色/尺码/SKU 管理，API 验证通过 |
| 库存模块开发 | ✅ 完成 | 仓库/库存/出入库/预警/预留锁定 |
| 订单系统开发 | ✅ 完成 | 重构，与库存联动 |
| 客户系统开发 | ✅ 完成 | 客户档案 CRUD + 电话搜索 |
| 移动端骨架搭建 | ✅ 完成 | Vue3 PWA 项目初始化 |
| 移动端页面开发 | ⏳ 进行中 | 订单/库存/商品页面开发 |
| PC 管理端骨架搭建 | ✅ 完成 | Vue3 + Element Plus + TailwindCSS，登录/布局/仪表盘全部完成 |
| PC 管理端页面开发 | ✅ 完成 | 订单/库存/商品/客户管理页面 |
| 看板系统开发 | ⏳ 待开始 | 统计展示 |

---

## 后端开发任务

### Phase 0: 基础骨架（已完成）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-001 | Spring Boot 3 项目初始化 | ✅ 完成 | pom.xml + 依赖 |
| BE-002 | application.yml 配置 | ✅ 完成 | 数据库 + Redis |
| BE-003 | Security 配置 + JWT | ✅ 完成 | Spring Security OAuth2 |
| BE-004 | MyBatis-Plus + 多租户配置 | ✅ 完成 | TenantLineInnerInterceptor |
| BE-005 | 统一响应 R.java | ✅ 完成 | Controller 基础 |
| BE-006 | 认证接口（登录/登出/刷新） | ✅ 完成 | /api/auth/* |
| BE-007 | 用户 CRUD 接口 | ✅ 完成 | /api/system/users |
| BE-008 | Flyway 数据库迁移脚本 | ✅ 完成 | V1__init_schema |
| BE-009 | 社交登录适配 | ⏳ TODO | 微信/钉钉（暂缓） |

### Phase 1: 商品模块（P0）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-201 | 商品表和 CRUD | ✅ 完成 | product 表 |
| BE-202 | 颜色管理 CRUD | ✅ 完成 | product_color 表 |
| BE-203 | 尺码管理 CRUD | ✅ 完成 | product_size 表 |
| BE-204 | SKU 表和自动编码 | ✅ 完成 | product_sku 表，SKU=商品+颜色+尺码 |
| BE-205 | 商品-颜色关联 | ✅ 完成 | product_color_rel |
| BE-206 | 商品-尺码关联 | ✅ 完成 | product_size_rel |

### Phase 2: 库存模块（P0）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-301 | 仓库管理 CRUD | ✅ 完成 | warehouse 表 |
| BE-302 | 库存表管理 | ✅ 完成 | inventory 表 |
| BE-303 | 库存变动记录表 | ✅ 完成 | inventory_log 表 |
| BE-304 | 入库接口 | ✅ 完成 | POST /api/inventory/in（支持图片、供应商） |
| BE-305 | 出库接口 | ✅ 完成 | POST /api/inventory/out（reason字段） |
| BE-306 | 直接调整接口 | ✅ 完成 | POST /api/inventory/adjust |
| BE-307 | 库存查询接口 | ✅ 完成 | GET /api/inventory |
| BE-308 | 库存预警接口 | ✅ 完成 | GET /api/inventory/alerts |
| BE-309 | 预留锁定/释放接口 | ✅ 完成 | POST /api/inventory/reserve, /release |

### Phase 3: 订单模块（P0）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-101 | 订单表重构 | ✅ 完成 | sale_order 表（原 product_order），状态值统一：0创建/1已付款/2已发货/3已完成/4已取消 |
| BE-102 | 订单明细表重构 | ✅ 完成 | sale_order_item 表（原 order_item），新增冗余字段填充 |
| BE-103 | 订单列表接口 | ✅ 完成 | 分页 + 筛选 + OrderVO 含 OrderItemVO 列表 |
| BE-104 | 订单详情接口 | ✅ 完成 | 含 SKU 明细、冗余颜色尺码 |
| BE-105 | 创建订单接口 | ✅ 完成 | 不扣库存，生成订单号，填充冗余字段 |
| BE-106 | 更新订单状态接口 | ✅ 完成 | 含库存联动逻辑 |
| BE-107 | 订单付款确认接口 | ✅ 完成 | 预占库存（调用 inventoryService.reserve） |
| BE-108 | 订单取消接口 | ✅ 完成 | 释放预占（调用 inventoryService.release） |
| BE-109 | 订单删除接口 | ✅ 完成 | 仅待处理可删（软删除） |
| BE-110 | 订单发货接口 | ✅ 完成 | 预占转出库（调用 inventoryService.out） |
| BE-111 | 订单表重命名 | ✅ 完成 | product_order → sale_order, order_item → sale_order_item |
| BE-112 | 冗余字段完善 | ✅ 完成 | 创建订单时填充 product_name, color_name, size_name, sku_code |
| BE-113 | 支付状态字段 | ✅ 完成 | V8__order_payment_delivery_fields.sql 迁移脚本已创建，Order/OrderCreateDTO/OrderVO 已添加字段 |
| BE-114 | 支付状态校验逻辑 | ✅ 完成 | 创建订单时校验定金金额，全款时自动设置已支付金额 |
| BE-115 | 配送状态管理 | ✅ 完成 | OrderCreateDTO 支持 needDelivery/deliveryAddress，订单创建时初始化配送状态 |
| BE-116 | 客户模块-电话搜索客户 | ✅ 完成 | GET /api/customers/search?phone，按电话搜索客户，支持国家代码 normalize |

### Phase 3.1: 库存并发控制修复（P0）

> 详细内容见：docs/reference/ORDER_SYSTEM_ISSUES.md 第一章

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-117 | 添加 Redisson 依赖 | ✅ 完成 | pom.xml 添加 redisson-spring-boot-starter |
| BE-118 | 创建 RedissonConfig | ✅ 完成 | RedissonClient Bean 配置 |
| BE-119 | 数据库迁移-添加 version 字段 | ✅ 完成 | V11__inventory_add_version.sql |
| BE-120 | Inventory Entity 添加 version | ✅ 完成 | @Version 注解 + get/set 方法 |
| BE-121 | 启用乐观锁插件 | ✅ 完成 | OptimisticLockerInnerInterceptor |
| BE-122 | 库存服务并发控制重构 | ✅ 完成 | in/out/adjust/reserve/release 全部加锁 |

### Phase 3.2: 订单库存解耦重构（P0）- 部分完成

> 详细内容见：docs/06-ORDER_INVENTORY_DESIGN.md

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-123 | 数据库迁移-新表创建 | ✅ 完成 | V20 创建 inventory_global_reserve 表，inventory 表加 global_reserved_qty 字段 |
| BE-124 | 数据库迁移-表结构修改 | ⏳ 进行中 | sale_order, sale_order_item 仍需与当前配货/调整流程完全对齐 |
| BE-125 | 库存服务-跨仓总量预留 | ✅ 完成 | globalReserve/globalRelease/getGlobalAvailableQty 方法 |
| BE-126 | 库存服务-按计划出库 | ⏳ 部分完成 | `outByPlan` 已实现，待结合任务验收和文档状态统一收口 |
| BE-127 | 订单服务-创建订单重构 | ✅ 完成 | warehouseId 可选，创建时校验跨仓总量 |
| BE-128 | 订单服务-付款确认重构 | ✅ 完成 | 调用跨仓总量预留 |
| BE-129 | 订单服务-配货计划 | ✅ 完成 | OrderDeliveryPlanService + Controller，配货计划 CRUD + 确认/取消调整 |
| BE-130 | 订单服务-调整记录 | ✅ 完成 | AdjustmentLogDTO + recordAdjustment + getAdjustmentLogs |
| BE-131 | 订单状态-配货中状态 | ✅ 完成 | status=2(ADJUSTMENT_PENDING)，status=3(READY_TO_SHIP)，完整流转 |

### Phase 3.3: 订单状态机修复与功能完善（P0）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-132 | 订单状态机 4 项修复 | ✅ 完成 | confirmPayment 同步 paymentStatus；create 初始化 adjustmentStatus=NONE；confirmAdjustment 减配释放多余预留；cancelOrder 白名单校验+按状态条件释放库存 |
| BE-133 | 库存 InventoryVO 补充 globalReservedQty | ✅ 完成 | pageList/convertToVO 的 availableQty 计算已扣减 globalReservedQty |
| BE-134 | 库存 Mapper XML 修复 | ✅ 完成 | global_reserved_qty 加入 SELECT 和 resultMap，预警过滤条件扣减该字段 |
| BE-135 | 订单编辑接口 | ✅ 完成 | PUT /api/orders/{id}，支持修改客户信息/送货方式/备注/图片，status>=4 禁止修改 |
| BE-136 | 追加收款接口 | ✅ 完成 | POST /api/orders/{id}/add-payment，仅 status=0 且 paymentStatus≠2 可调用，累加 paidAmount 并自动更新 paymentStatus |
| BE-137 | GlobalExceptionHandler 补充 RuntimeException 处理 | ✅ 完成 | 业务 RuntimeException 返回 400 + 可读错误信息，不再返回 500 |

### Phase 4: 客户模块（P1）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-401 | 客户表和 CRUD | ✅ 完成 | crm_customer + crm_customer_phone 表（一个客户可有多个电话） |
| BE-402 | 客户电话搜索接口 | ✅ 完成 | GET /api/customers/search?phone，按电话搜索，自动 normalize |
| BE-403 | 客户创建接口 | ✅ 完成 | POST /api/customers，支持创建客户时同时创建主电话 |
| BE-404 | 客户列表接口 | ✅ 完成 | 分页 + 筛选 + 订单数量查询 |
| BE-405 | 客户详情接口 | ✅ 完成 | 含订单数量统计 |

### Phase 5: 看板系统（P2）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-501 | 看板统计接口 | ✅ 完成 | GET /api/dashboard/stats |
| BE-502 | 订单趋势接口 | ✅ 完成 | GET /api/dashboard/trend |
| BE-503 | 热销商品接口 | ✅ 完成 | GET /api/dashboard/top-products |

### Phase 6: OCR 拍照录单（P2）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-601 | 图片上传接口 | ⏳ TODO | 支持拍照上传单据图片 |
| BE-602 | OCR 识别服务 | ⏳ TODO | 对接微信扫一扫/腾讯云 OCR |
| BE-603 | 字段提取与表单填充 | ⏳ TODO | 数量/单价/总金额/客户名/日期自动提取 |
| BE-604 | AI 解析服务（方案B） | ⏳ TODO | 表格结构识别 + 款号模糊匹配 |
| BE-605 | 置信度标注与候选推荐 | ⏳ TODO | 高/中/低置信度 + 款号候选列表 |

---

## PC 管理端开发任务（blade-admin）

### Phase 1: 骨架搭建

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-001 | Vue3 项目初始化 | ✅ 完成 | 从零搭建 Vue3 + Element Plus + TailwindCSS，已完成登录页、布局、仪表盘 |
| BA-002 | 项目结构搭建 | ✅ 完成 | views/ router/ stores/ api/ 已搭建 |
| BA-003 | 布局组件 | ✅ 完成 | 侧边栏菜单 + 顶部导航 |
| BA-004 | 登录页 | ✅ 完成 | 多租户登录 + 验证码 + 错误处理 |

### Phase 2: 订单管理

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-201 | 订单列表页 | ✅ 完成 | 表格 + 高级筛选 + 分页 + 支付状态筛选 + 编辑按钮 + 订单上下文摘要 + 图片链接字段 |
| BA-202 | 订单详情页 | ✅ 完成 | 查看 + 状态操作 + 支付状态/定金/送货状态 + 追加收款按钮（status=0且paymentStatus≠2）+ 追加收款弹窗 |
| BA-203 | 新建订单页 | ✅ 完成 | 支付状态单选框 + 定金输入 + 送货设置 + 客户搜索（电话匹配）+ 批量添加商品（颜色尺码矩阵）+ 单价可调整 + 档口选择 |
| BA-204 | 订单导出 | ⏳ TODO | Excel 导出 |
| BA-205 | 订单显示开单人员 | ✅ 完成 | 订单列表增加开单人员列，展示 salesmanName |

### Phase 3: 库存管理

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-301 | 库存列表页 | ✅ 完成 | 表格 + 筛选 + 预警标识 + 分页 |
| BA-302 | 入库操作 | ✅ 完成 | 表单 + SKU选择 + 多商品入库 |
| BA-303 | 出库操作 | ✅ 完成 | 表单 + reason字段 + 多商品出库 |
| BA-304 | 库存调整 | ✅ 完成 | 盘盈盘亏表单 + 调整原因 |
| BA-305 | 库存记录 | ✅ 完成 | 变动日志查询弹窗 |

### Phase 4: 商品管理

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-401 | 商品列表页 | ✅ 完成 | 表格 + 分类/状态筛选 + 分页 + 新建/编辑商品弹窗 |
| BA-402 | 商品编辑页 | ✅ 完成 | 颜色/尺码选择 + SKU 生成预览 |
| BA-403 | SKU 矩阵配置 | ✅ 完成 | 颜色×尺码 矩阵输入数量 |
| BA-404 | 颜色列表页 | ✅ 完成 | 颜色管理 + 新建/编辑弹窗 |
| BA-405 | 尺码列表页 | ✅ 完成 | 尺码管理 + 新建/编辑弹窗 |
| BA-406 | 商品分类页 | ✅ 完成 | 分类管理 + 新建/编辑弹窗 + 后端 API |

### Phase 5: 客户管理

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-501 | 客户列表页 | ✅ 完成 | 表格 + 筛选 + 分页 + 新建/编辑/删除弹窗 |
| BA-502 | 客户编辑页 | ✅ 完成 | 新增/编辑客户表单 |

### Phase 6: 看板统计

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-601 | 仪表盘 | ✅ 完成 | 数字卡片 + 趋势图 + ECharts |
| BA-602 | 订单统计 | ✅ 完成 | 趋势图集成到仪表盘 |
| BA-603 | 库存统计 | ⏳ TODO | 预警/周转分析 |

### Phase 7: 权限系统（P1）

> 详细内容见：docs/08-PERMISSION_SYSTEM_DESIGN.md

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-701 | 数据库迁移-权限表 | ✅ 完成 | V12__permission_system.sql：sys_permission, sys_role_permission 表 |
| BE-702 | 修改 sys_user_role 表 | ✅ 完成 | V13__sys_user_role_fix.sql：增加 tenant_id/deleted/create_time |
| BE-703 | 修改 sys_role_menu 表 | ✅ 完成 | V14__permission_data.sql：预置角色和权限数据 |
| BE-704 | Permission 实体和 Mapper | ✅ 完成 | SysPermission + PermissionMapper（含自定义SQL） |
| BE-705 | RolePermission 实体和 Mapper | ✅ 完成 | SysRolePermission + RolePermissionMapper |
| BE-706 | 角色管理接口 | ✅ 完成 | RoleController + RoleService + RoleVO/DTO |
| BE-707 | 权限管理接口 | ✅ 完成 | PermissionController + PermissionService + PermissionVO/DTO |
| BE-708 | 权限判断逻辑 | ✅ 完成 | hasPermission() + getUserPermissionCodes() + getVisibleMenus() |
| BE-709 | 预置角色和权限数据 | ✅ 完成 | 6个预置角色 + 完整权限数据（菜单/按钮/字段） |
| BA-701 | 用户管理页面 | ⏳ 部分完成 | 系统管理页已包含用户列表、新建/编辑、重置密码、分配角色；待补最终验收与文档收口 |
| BA-702 | 角色管理页面 | ⏳ 部分完成 | 系统管理页已包含角色列表、新建/编辑与权限分配；待补最终验收与文档收口 |
| BA-703 | 权限配置页面 | ⏳ 部分完成 | 系统管理页已包含权限树配置；按钮/字段权限仍需结合实际验收继续完善 |
| BA-704 | 个人中心页面 | ⏳ TODO | 修改密码、个人信息 |

### Phase 8: OCR 拍照录单（P2）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-801 | 拍照录单页（方案A） | ⏳ TODO | 上传图片 + 半自动表单（款号手动选） |
| BA-802 | 拍照录单页（方案B） | ⏳ TODO | AI 识别 + 置信度标注 + 候选推荐 |

---

## 移动端开发任务（blade-mobile）

### Phase 1: 骨架搭建

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| FE-001 | Vue3 PWA 项目初始化 | ✅ 完成 | Vite + Vue3 + TS |
| FE-002 | PWA 配置 | ✅ 完成 | Service Worker + manifest |
| FE-003 | 路由配置 | ✅ 完成 | Vue Router |
| FE-004 | 响应式布局基础 | ✅ 完成 | Vuetify 4 响应式 |
| FE-005 | @blade/core 封装 | ⏳ TODO | API + Auth + Store |
| FE-006 | 共享类型定义 | ✅ 完成 | packages/types 已搭建 |

### Phase 2: 订单系统

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| FE-101 | 订单列表页 | ✅ 完成 | 骨架已搭建，含列表/筛选/状态Tab |
| FE-102 | 订单详情页 | ✅ 完成 | 骨架已搭建，含状态操作按钮 |
| FE-103 | 新建订单页 | ✅ 完成 | 骨架已搭建，含SKU选择/仓库选择 |
| FE-104 | 订单状态筛选 | ⏳ TODO | 底部 Tab 筛选优化 |
| FE-105 | 订单搜索 | ⏳ TODO | 订单号/客户名搜索 |

### Phase 3: 库存系统

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| FE-201 | 库存列表页 | ✅ 完成 | 骨架已搭建，含仓库筛选/预警过滤 |
| FE-202 | 入库页 | ✅ 完成 | 骨架已搭建，含SKU选择/图片上传 |
| FE-203 | 出库页 | ✅ 完成 | 骨架已搭建，含ORDER/OTHER来源选择 |
| FE-204 | 扫码功能 | ⏳ TODO | 扫码枪/相机集成 |
| FE-205 | 库存调整页 | ⏳ TODO | 直接调整表单 |

### Phase 4: 商品管理

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| FE-301 | 商品列表页 | ✅ 完成 | 骨架已搭建 |
| FE-302 | 商品编辑页 | ⏳ TODO | 颜色/尺码选择 |
| FE-303 | SKU 矩阵配置 | ⏳ TODO | 颜色×尺码 |

### Phase 5: 看板系统

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| FE-401 | 统计概览页 | ✅ 完成 | 骨架已搭建，含数字卡片/趋势图 |
| FE-402 | 订单趋势图 | ⏳ TODO | 折线图 |
| FE-403 | 热销商品 Top 5 | ⏳ TODO | 柱状图 |

### Phase 7: OCR 拍照录单（P2）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| FE-701 | 拍照录单页（方案A） | ⏳ TODO | 上传图片 + 半自动表单 |
| FE-702 | 拍照录单页（方案B） | ⏳ TODO | AI 识别 + 置信度标注 |

---

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

---

## 注意事项

1. ✅ **订单系统（BE-101~BE-115）已完成**
2. ✅ **客户模块（BE-401~BE-403）已完成**
3. ✅ **商品模块（BE-201~BE-206）已完成**
4. ✅ **库存模块（BE-301~BE-309）已完成**
5. ✅ **库存并发控制（BE-117~BE-122）已完成** - Redis 分布式锁 + 乐观锁
6. ✅ **移动端骨架（FE-001~FE-007）已完成**，页面开发进行中
7. ✅ **blade-admin PC 管理端**：订单/库存/商品管理页面全部完成
8. 微信服务（BE-009）暂缓，等业务系统稳定后再接入
9. **下一步**：客户管理页面 BA-501~BA-502、看板统计 BA-601~BA-603

---

## 开发质量保障规范

> 所有新功能开发必须遵守以下规范，适用于后端和前端。

### 一、设计文档规范

**关键方法设计章节必须包含：**

| 章节 | 必须包含的内容 |
|------|--------------|
| 方法签名 | 完整的 Java/TypeScript 方法签名 |
| 验证逻辑 | 按顺序列出的所有检查，每条检查注明不符合时抛出的异常 |
| 库存变动 | 每个字段的增减说明（如 `quantity - quantity`） |
| 与同类方法的区别 | 必须有对比表格（如 `out() vs outByPlan()`） |

**示例章节格式：**

```markdown
#### X.X.X 方法名

**方法签名：**
```java
void methodName(Type param);
```

**验证逻辑**（必须按顺序执行）：
1. 检查 ...，不符合则抛出 `RuntimeException("...")`
2. 检查 ...，不符合则抛出 `RuntimeException("...")`
3. 获取 Redis 分布式锁：`lock:key:{param}`

**库存变动**：
| 字段 | 变动 | 说明 |
|------|------|------|
| quantity | `quantity - n` | 扣减库存 |
| reserved_qty | 不变 | 说明原因 |

**与 Y.Y.Y 的区别**：
| 维度 | methodName() | Y.Y.Y() |
|------|-------------|----------|
| 适用场景 | ... | ... |
| 扣减字段 | quantity | quantity + reserved_qty |
```

### 二、代码审查清单

实现完成后自检：

- [ ] 是否有单元测试？
- [ ] 测试是否覆盖正常流程和异常流程？
- [ ] 是否符合设计文档？
- [ ] 是否有并发安全考虑？（Redis锁 + 乐观锁）
- [ ] 边界条件是否处理？（库存为0、负数等）
- [ ] 是否正确释放锁？（finally块）
- [ ] 是否有日志记录？
- [ ] 异常情况是否都有明确错误信息？

### 三、单元测试要求

**关键业务方法必须测试：**

| 方法类型 | 必须有的测试用例 |
|----------|----------------|
| 出库方法 | 正常流程、库存不足、并发安全 |
| 预留方法 | 正常流程、库存不足、重复预留 |
| 创建方法 | 正常流程、参数校验、唯一性冲突 |

**测试用例命名规范：**
```java
@Test
void testMethodName_NormalFlow() { ... }           // 正常流程

@Test
void testMethodName_InsufficientStock() { ... }   // 库存不足

@Test
void testMethodName_ConcurrentAccess() { ... }    // 并发安全
```

### 四、并发控制设计检查

涉及库存、金额的操作必须检查：

- [ ] 是否使用 Redis 分布式锁？
- [ ] 锁的 Key 设计是否合理？（粒度不过粗也不过细）
- [ ] 是否使用乐观锁（version 字段）？
- [ ] 是否有"查询-判断-更新"模式？需改为原子操作
- [ ] 是否有唯一性校验？是否加了数据库唯一约束？

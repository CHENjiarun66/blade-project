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
| 客户系统开发 | ⏳ 待开始 | 客户档案管理 |
| 移动端骨架搭建 | ✅ 完成 | Vue3 PWA 项目初始化 |
| 移动端页面开发 | ⏳ 进行中 | 订单/库存/商品页面开发 |
| PC 管理端搭建 | ⏳ 待开始 | blade-admin 项目初始化 |
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
| BE-101 | 订单表重构 | ✅ 完成 | product_order 表，新增字段：customer_id, paid_amount, warehouse_id, pay_time, confirm_time, deliver_time, complete_time |
| BE-102 | 订单明细表重构 | ✅ 完成 | order_item 表，新增字段：sku_code, color_name, size_name |
| BE-103 | 订单列表接口 | ✅ 完成 | 分页 + 筛选 + OrderVO 含 OrderItemVO 列表 |
| BE-104 | 订单详情接口 | ✅ 完成 | 含 SKU 明细、冗余颜色尺码 |
| BE-105 | 创建订单接口 | ✅ 完成 | 不扣库存，生成订单号 |
| BE-106 | 更新订单状态接口 | ✅ 完成 | 含库存联动逻辑 |
| BE-107 | 订单付款确认接口 | ✅ 完成 | 预占库存（调用 inventoryService.reserve） |
| BE-108 | 订单取消接口 | ✅ 完成 | 释放预占（调用 inventoryService.release） |
| BE-109 | 订单删除接口 | ✅ 完成 | 仅待处理可删 |
| BE-110 | 订单发货接口 | ✅ 完成 | 预占转出库（调用 inventoryService.out） |

### Phase 4: 客户模块（P1）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-401 | 客户表和 CRUD | ⏳ TODO | client 表 |

### Phase 5: 看板系统（P2）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-501 | 看板统计接口 | ⏳ TODO | GET /api/dashboard/stats |
| BE-502 | 订单趋势接口 | ⏳ TODO | GET /api/dashboard/trend |
| BE-503 | 热销商品接口 | ⏳ TODO | GET /api/dashboard/top-products |

---

## PC 管理端开发任务（blade-admin）

### Phase 1: 骨架搭建

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-001 | Vue3 项目初始化 | ⏳ TODO | Vite + Vue3 + TS + Element Plus |
| BA-002 | 项目结构搭建 | ⏳ TODO | views/ router/ stores/ api/ |
| BA-003 | 布局组件 | ⏳ TODO | 侧边栏 + 顶部导航 |
| BA-004 | 登录页 | ⏳ TODO | 后台登录 |

### Phase 2: 订单管理

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-201 | 订单列表页 | ⏳ TODO | 表格 + 高级筛选 + 分页 |
| BA-202 | 订单详情页 | ⏳ TODO | 查看 + 状态操作 |
| BA-203 | 订单导出 | ⏳ TODO | Excel 导出 |

### Phase 3: 库存管理

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-301 | 库存列表页 | ⏳ TODO | 表格 + 筛选 + 预警标识 |
| BA-302 | 入库操作 | ⏳ TODO | 表单 + 图片上传 |
| BA-303 | 出库操作 | ⏳ TODO | 表单 + reason 字段 |
| BA-304 | 库存调整 | ⏳ TODO | 盘盈盘亏表单 |
| BA-305 | 库存记录 | ⏳ TODO | 变动日志查询 |

### Phase 4: 商品管理

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-401 | 商品列表页 | ⏳ TODO | 表格 + 分类筛选 |
| BA-402 | 商品编辑页 | ⏳ TODO | 颜色/尺码选择 + SKU 生成 |
| BA-403 | SKU 矩阵配置 | ⏳ TODO | 颜色×尺码 矩阵 |

### Phase 5: 客户管理

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-501 | 客户列表页 | ⏳ TODO | 表格 + 筛选 |
| BA-502 | 客户编辑页 | ⏳ TODO | 新增/编辑客户 |

### Phase 6: 看板统计

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-601 | 仪表盘 | ⏳ TODO | 数字卡片 + 趋势图 |
| BA-602 | 订单统计 | ⏳ TODO | 订单量/金额统计 |
| BA-603 | 库存统计 | ⏳ TODO | 预警/周转分析 |

---

## 移动端开发任务（blade-mobile）

### Phase 1: 骨架搭建

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| FE-001 | Vue3 PWA 项目初始化 | ✅ 完成 | Vite + Vue3 + TS |
| FE-002 | PWA 配置 | ✅ 完成 | Service Worker + manifest |
| FE-003 | 路由配置 | ✅ 完成 | Vue Router |
| FE-004 | 响应式布局基础 | ✅ 完成 | Vuetify 3 响应式 |
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
  - Vue3 + Vite + TypeScript + Vuetify 3 + Pinia + Vue Router
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
  - 更新技术栈：移动端（Vuetify3）、PC端（Element Plus）
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

---

## 注意事项

1. **订单系统 BE-101~BE-104 已完成代码需要重构**，不满足新 PRD 设计
2. ✅ **商品模块（BE-201~BE-206）已完成**，可以开始库存模块开发
3. ✅ **库存模块（BE-301~BE-309）已完成**，订单模块已重构
4. ✅ **移动端骨架（FE-001~FE-007）已完成**，页面开发进行中
5. **blade-admin PC 管理端规划已完成**，下一步开始搭建
6. 微信服务（BE-009）暂缓，等业务系统稳定后再接入

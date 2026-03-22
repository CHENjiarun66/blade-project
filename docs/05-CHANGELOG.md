# 变更记录

> 所有需求变更、架构变更、重大决策变更必须记录在此。
> 新 AI 阅读本文档可以快速了解项目的演变过程。
> 格式：日期 + 变更内容 + 原因 + 影响范围

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

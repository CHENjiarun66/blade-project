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
   - 新增图片链接字段（images，textarea 输入，逗号分隔 URL）
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

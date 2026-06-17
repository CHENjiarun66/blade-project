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
| 外部 Agent 对接 | ⏳ 进行中 | 第一版只读 Agent Gateway 已起步，款式趋势/客户跟进/客户风险/颜色尺码结构/库存建议/周期分析/搜索 |

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
| BE-006 | 认证接口（登录/登出/刷新） | ✅ 完成 | /api/auth/*；refresh token 支持 remember=30天 / 默认7天，并携带 tenantId 以便续签时恢复租户上下文 |
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
| BE-127 | 订单服务-创建订单重构 | ✅ 完成 | warehouseId 可选；创建订单不扣库存、不预占、不因库存不足失败 |
| BE-128 | 订单服务-付款确认重构 | ✅ 完成 | 历史实现为调用跨仓总量预留；2026-06-17 生产口径已调整为收款不锁库存，待 BE-138/BE-139 收尾 |
| BE-129 | 订单服务-配货计划 | ✅ 完成 | OrderDeliveryPlanService + Controller，配货计划 CRUD + 确认/取消调整 |
| BE-130 | 订单服务-调整记录 | ✅ 完成 | AdjustmentLogDTO + recordAdjustment + getAdjustmentLogs |
| BE-131 | 订单状态-配货中状态 | ✅ 完成 | status=2(ADJUSTMENT_PENDING)，status=3(READY_TO_SHIP)，完整流转 |

### Phase 3.2A: 订单库存软解耦生产口径（P0）- 待开发

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-138 | 确认收款移除硬库存预留 | ⏳ TODO | confirmPayment / addPayment 只更新 paidAmount、paymentStatus、payTime 和待配货状态；不得调用 globalReserve，不得因库存不足失败 |
| BE-139 | 发货出库按实际配货明细扣库存 | ⏳ TODO | outByPlan 改为只在发货阶段校验并扣减实际发货 SKU/仓库/数量；第一版不依赖 global_reserved_qty；库存不足返回明确业务提示 |
| BE-140 | 抹零/短款结清收款口径 | ⏳ TODO | sale_order 增加 write_off_amount/write_off_reason；paymentStatus=2 表示已结清；尾款按 total-refund-writeOff-paid 计算；销售额/毛利统计扣减 writeOff |
| BA-212 | 订单详情配货页软提示改造 | ⏳ TODO | 配货阶段展示库存提示、替换/减配说明；库存不足不阻断方案保存，发货确认前再校验 |
| BA-213 | 追加收款标记结清交互 | ⏳ TODO | 追加收款弹窗支持“标记结清”，自动把当前尾款写入抹零/短款金额；列表/详情将 paymentStatus=2 展示为“已结清” |
| DOC-ORDER-INV-001 | 订单库存软解耦文档与流程图 | ✅ 完成 | 更新 PRD、订单库存设计、任务清单、变更记录；新增 drawio 流程图，明确发货状态和收款状态独立变化 |

### Phase 3.3: 订单状态机修复与功能完善（P0）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-132 | 订单状态机 4 项修复 | ✅ 完成 | confirmPayment 同步 paymentStatus；create 初始化 adjustmentStatus=NONE；confirmAdjustment 减配释放多余预留；cancelOrder 白名单校验+按状态条件释放库存 |
| BE-133 | 库存 InventoryVO 补充 globalReservedQty | ✅ 完成 | pageList/convertToVO 的 availableQty 计算已扣减 globalReservedQty |
| BE-134 | 库存 Mapper XML 修复 | ✅ 完成 | global_reserved_qty 加入 SELECT 和 resultMap，预警过滤条件扣减该字段 |
| BE-135 | 订单编辑接口 | ✅ 完成 | PUT /api/orders/{id}；创建状态可改金额结构；已收款后锁商品明细/数量/金额/运费但允许维护基础信息、备注、图片；已发货后仅允许备注/图片 |
| BE-136 | 追加收款接口 | ✅ 完成 | POST /api/orders/{id}/add-payment，仅 status=0 且 paymentStatus≠2 可调用，累加 paidAmount 并自动更新 paymentStatus |
| BE-137 | GlobalExceptionHandler 补充 RuntimeException 处理 | ✅ 完成 | 业务 RuntimeException 返回 400 + 可读错误信息，不再返回 500 |
| BE-141 | 订单号生成器防重复 | ✅ 完成 | Redis 计数器生成订单号前对齐数据库当天最大订单序号，避免 Redis 重置或测试库已有历史订单导致唯一索引冲突 |

### Phase 4: 客户模块（P1）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-401 | 客户表和 CRUD | ✅ 完成 | crm_customer + crm_customer_phone 表（一个客户可有多个电话） |
| BE-402 | 客户电话搜索接口 | ✅ 完成 | GET /api/customers/search?phone，按电话搜索，自动 normalize |
| BE-403 | 客户创建接口 | ✅ 完成 | POST /api/customers，支持创建客户时同时创建主电话 |
| BE-404 | 客户列表接口 | ✅ 完成 | 分页 + 筛选 + 订单数量查询 |
| BE-405 | 客户详情接口 | ✅ 完成 | 含订单数量统计 |

### Phase 4.5: 客户模块国际化升级（2026-04-24）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-406 | 客户国家区号字段 | ✅ 完成 | 数据库 V24 迁移，crm_customer 新增 country_code/country_name |
| BE-407 | 国家选择器组件 | ✅ 完成 | CountryCodeSelect.vue，WhatsApp 风格，约 140 国家 |
| BE-408 | 客户详情页 3 Tab | ✅ 完成 | 基本信息/订单记录/商品偏好 Tab |
| BE-409 | 客户商品偏好分析接口 | ✅ 完成 | GET /api/customers/{id}/preference，颜色/尺码/品类偏好统计 |
| BE-410 | 客户基础统计接口 | ✅ 完成 | GET /api/customers/{id}/stats，订单数/消费总额/时间范围 |
| BE-411 | E2E 测试验证 | ✅ 完成 | 12/12 测试用例全部通过 |

### Phase 4.6: 客户模块优化（P1-P2）

> 详细内容见：docs/08-CUSTOMER_OPTIMIZATION.md

#### M1: 数据质量（P1）

| 任务 ID | 任务 | 状态 | 验收标准 |
|---------|------|------|---------|
| BE-412 | 电话重复检查 | ✅ 完成 | V25迁移建唯一索引；create/update时校验租户内电话唯一性，冲突抛 RuntimeException |
| BE-413 | 删除客户订单保护 | ✅ 完成 | deleteCustomer() 前检查 status NOT IN (4,5)，有进行中订单则抛异常阻止删除 |
| BE-414 | N+1 查询优化 | ✅ 完成 | getCustomerOrders() 改为单条 IN 查询获取所有 OrderItem，内存分组赋值 |

#### M2: 用户体验（P2）

| 任务 ID | 任务 | 状态 | 验收标准 |
|---------|------|------|---------|
| BE-415 | 订单记录分页 | ✅ 完成 | 后端支持 page/size 参数（默认1/20，最大100），返回 PageResult 含 total/pages；前端需结合实际页面接入 |
| BE-416 | 常用国家置顶 | ✅ 完成 | localStorage 存储最近使用国家（最多5个），选中国家时写入，列表顶部显示「常用」区块 |
| BE-417 | 国家选择器键盘导航 | ✅ 完成 | ↑↓ 键导航，Enter 选中，Esc 关闭，打字搜索时自动聚焦 |

#### M3: 业务功能（P2）

| 任务 ID | 任务 | 状态 | 验收标准 |
|---------|------|------|---------|
| BE-418 | 客户标签功能 | ✅ 完成 | V26迁移创建crm_customer_tag和crm_customer_tag_rel表；后端完整CRUD + 客户标签分配/移除接口；前端待接入 |
| BE-419 | 沉默客户预警 | ✅ 完成 | GET /api/dashboard/silent-customers?days=90 返回沉默客户列表（含最后订单日期和天数）；前端待接入 |
| BE-420 | 偏好时间范围筛选 | ✅ 完成 | preference接口支持startDate/endDate参数（默认365天前~今天）；已通过curl测试验证 |

#### M4: 架构能力（P3）

| 任务 ID | 任务 | 状态 | 验收标准 |
|---------|------|------|---------|
| BE-421 | 客户数据权限 | ✅ 完成 | CustomerPageDTO 新增 mine 参数；pageList() 支持 mine=true 过滤只看自己创建的客户；createBy 字段已在客户创建时填充 |
| BE-422 | 操作审计日志 | ✅ 完成 | V28迁移创建 crm_customer_operation_log 表；CustomerServiceImpl 在 create/update/delete 时记录操作日志（操作人/时间/变更详情） |
| BE-423 | 偏好数据缓存 | ✅ 完成 | getPreference() 增加 Redis 缓存，key=customer:preference:{id}:{startDate}:{endDate}，TTL=1小时；已通过 redis-cli KEYS 验证缓存写入 |

### Phase 5: 看板系统（P2）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-501 | 看板统计接口 | ✅ 完成 | GET /api/dashboard/stats，订单统计按 order_date + 已产生收款 + 应收净额 |
| BE-502 | 订单趋势接口 | ✅ 完成 | GET /api/dashboard/trend，按筛选周期统计已产生收款订单 |
| BE-503 | 热销商品接口 | ✅ 完成 | GET /api/dashboard/top-products，仅统计已产生收款订单 |
| BE-504 | 数据分析接口 | ✅ 完成 | GET /api/analytics/summary、trend、product-ranking、product-detail |
| BE-505 | 数据分析权限 | ✅ 完成 | menu:analytics + data:analytics:profit，销售员默认无毛利权限 |

### Phase 5.5: 外部 Agent 对接（P2）

> 详细内容见：docs/10-AGENT_INTEGRATION_DESIGN.md

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-551 | Agent 接入安全边界复核 | ✅ 完成 | `/api/customers/**` 移除公开放行，Agent 入口独立走 `/api/agent/**` 鉴权与租户上下文 |
| BE-552 | Agent 凭证与 scope 模型 | ⏳ 部分完成 | V33 落 Agent Key 哈希、租户绑定和 scope 映射；管理入口与毛利 scope 待补 |
| BE-553 | Agent 认证过滤器与调用审计 | ✅ 完成 | `X-Agent-Key` 过滤器写入 TenantContext，V34 记录调用日志并更新 Agent Key 最近使用信息 |
| BE-554 | Agent 款式趋势分析数据包 | ✅ 完成 | GET /api/agent/analytics/style-trends 返回多周期销量事实、趋势标签和建议依据；库存建议由 BE-558 承接 |
| BE-555 | Agent 颜色尺码结构数据包 | ✅ 完成 | GET /api/agent/analytics/sku-mix 返回同款 SKU/颜色/尺码销售结构和热卖/低销量信号；缺货/积压由 BE-558 承接 |
| BE-556 | Agent 客户跟进清单 | ⏳ TODO | GET /api/agent/tasks/follow-up，基于订单日期、复购和跟进规则给出提醒依据 |
| BE-557 | Agent 客户风险与分层事实 | ⏳ TODO | GET /api/agent/customers/risk，支持核心/增长/低活跃/流失风险判断 |
| BE-558 | Agent 库存建议事实 | ⏳ TODO | GET /api/agent/inventory/recommendations，积压、缺货影响、补货优先级和跨仓事实 |
| BE-559 | Agent 周期经营报告数据包 | ⏳ TODO | GET /api/agent/reports/periodic，支持月度/季度/年度分析建议 |
| BE-560 | Agent Gateway 统一搜索 | ⏳ TODO | GET /api/agent/search，支持客户/订单/商品/SKU 最小结果 |
| BE-561 | Agent 定时提醒接入验证 | ⏳ TODO | 验证客户跟进清单可被定时 Agent 调用，提醒渠道另行锁定 |
| BE-562 | Agent API 限流与回归验证 | ⏳ TODO | 多租户隔离、无毛利 scope、真实 Agent 工具调用验证 |
| BE-563 | 统一业务事件日志设计 | ⏳ TODO | 为后续 /api/agent/changes、订单异常分析和窄范围 Agent 写动作打基础 |
| BE-564 | WhatsApp 数据接入方案验证 | ⏳ TODO | 验证合规接入方式、客户映射、消息保留/权限/脱敏策略 |
| BE-565 | Agent 后续能力路线评审 | ⏳ TODO | 订单运营异常、利润解释、WhatsApp 反馈分析、经营记忆分阶段排序 |

### Phase 6: OCR 拍照录单（P2）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-601 | OCR 图片上传接入 | ⏳ TODO | 复用统一文件接口上传原始单据图片 |
| BE-602 | OCR 识别服务 | ⏳ TODO | 对接微信扫一扫/腾讯云 OCR |
| BE-603 | 字段提取与表单填充 | ⏳ TODO | 数量/单价/总金额/客户名/日期自动提取 |
| BE-604 | AI 解析服务（方案B） | ⏳ TODO | 表格结构识别 + 款号模糊匹配 |
| BE-605 | 置信度标注与候选推荐 | ⏳ TODO | 高/中/低置信度 + 款号候选列表 |

### Phase 6.5: 统一文件存储（P1）

> 详细内容见：docs/09-FILE_STORAGE_DESIGN.md

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-901 | 统一文件表与迁移脚本 | ✅ 完成 | V32__file_storage.sql 新增 file_storage 表，业务表保存 fileId |
| BE-902 | 本地文件上传/预览/删除接口 | ✅ 完成 | POST /api/files/upload；GET /api/files/{id}/preview；DELETE /api/files/{id} |
| BE-903 | 文件业务绑定接口 | ✅ 完成 | PUT /api/files/bind；订单/入库服务内部自动绑定 |
| BE-904 | 订单图片 fileId 保存改造 | ✅ 完成 | sale_order.images 保存 fileId JSON 数组，并兼容历史 URL/blob 数据展示 |
| BE-905 | 入库凭证 fileId 保存改造 | ✅ 完成 | inventory_log.images 保存 fileId JSON 数组 |
| BE-906 | 商品主图 fileId 保存改造 | ✅ 完成 | product.image_url 保存单个 fileId，并兼容历史 URL |
| BE-907 | 上传接口回归测试 | ✅ 完成 | FileControllerTest 覆盖 multipart 上传返回 fileId/previewUrl |

### Phase 6.6: 文件中心与数字资产中心（P1）

> 详细内容见：docs/12-FILE_CENTER_ASSET_DESIGN.md

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-1001 | 数字资产表结构扩展 | ✅ 完成 | 扩展 file_storage（14 个新字段），新增 file_folder、file_business_bind、file_operation_log、file_cleanup_log 四张表；对应实体和 Mapper；22 项反射测试全部通过 |
| BE-1002 | 文件中心分页/详情 API | ✅ 完成 | GET /api/files (分页+筛选)、GET /api/files/{id} (文件详情)；支持 keyword/folderId/fileType/businessType/bound/purpose/createBy/startDate/endDate/status 筛选；bound 基于 file_business_bind.deleted=0 判断 |
| BE-1003 | 文件夹管理 API | ✅ 完成 | 树形获取、创建、更新、删除（含子文件夹/文件保护，moveFilesToUnfiled 参数） |
| BE-1004 | 多业务绑定 API | ✅ 完成 | GET /api/files/{id}/bindings、POST /api/files/bindings、DELETE /api/files/bindings/{id}；绑定前验证文件存在，解绑校验租户；写入操作日志 |
| BE-1005 | 商品/SKU 图片绑定服务 | ✅ 完成 | PUT /api/products/{id}/file-bindings；支持 main 主图、gallery 图集、sku_image SKU 图片的替换语义绑定；product.image_url 兼容 fileId 同步到 file_business_bind；非数字历史 URL 忽略 |
| BE-1006 | 文件批量操作 API | ✅ 完成 | POST /api/files/batch-delete（软删除）、POST /api/files/batch-move（移动到文件夹/未归档）；批量删除拒绝有效绑定文件；批量移动前验证文件夹存在；写入操作日志 |
| BE-1007 | 未绑定文件治理 | ✅ 完成 | 查询/软删除未绑定未归档超期文件（基于 file_business_bind.deleted=0 判断）；写入 file_cleanup_log |
| BE-1008 | 文件清理定时任务 | ✅ 完成 | 第一版按配置 tenant-id + cron 执行两步清理（软删除未绑定+标记已清理）；默认 disabled；仅元数据不物理删除，全租户遍历/物理删除留到后续 |
| BE-1009 | 文件权限与私有预览收口 | ✅ 完成 | BE-1009A 完成 PUBLIC/PRIVATE 预览登录边界；BE-1009B 完成业务权限映射（order→btn:order:view、product/sku→menu:product、inventory_log→btn:inventory:viewLog、ocr_document→menu:file），viewAll 绕过、viewOwn 要求可靠 userId；补充浏览器原生 `<img>`/新窗口预览的 `previewToken` 支持；FileControllerTest 20/20 通过，JwtAuthenticationFilterTest+FileControllerTest 22/22 通过 |
| BE-1009A | 文件预览 PUBLIC/PRIVATE 基础收口 | ✅ 完成 | 子任务，已合并到 BE-1009 |
| BE-1009B | 文件预览业务权限映射 | ✅ 完成 | 子任务，已合并到 BE-1009 |
| BE-1010 | 基础视频文件支持 | ✅ 完成 | 上传支持 video/mp4、video/webm、video/quicktime；上传上限默认 200MB 且支持环境变量覆盖；自动分类 fileType（IMAGE/VIDEO/OTHER）和 fileExt；FileUploadVO 新增 fileType/fileExt；不做转码/封面/Range/分片 |
| BE-1011 | 文件中心回归测试 | ✅ 完成 | 覆盖上传、列表、绑定、未绑定清理、删除保护；补充批量删除有效绑定文件拒绝测试；`File*Test` 98/98 通过 |
| BE-1012 | 图片派生图/缩略图底座 | ⏳ TODO | 新增 file_derivative；建立派生图服务、生成器、存储 Provider 脚手架；上传图片后生成 thumb/card；新增 GET /api/files/{id}/variant?type=thumb/card；权限继承原图预览权限；历史图片批量补生成后续实现；预留异步/重试/NAS/七牛云/CDN 扩展点但第一版不实现 |
| BE-1013 | 商品素材查询 API | ✅ 完成 | GET /api/products/{id}/file-bindings，返回 main/gallery/skuImages 分组，previewUrl 统一为 /api/files/{fileId}/preview |
| BE-1014 | 商品/SKU 删除引用保护验收 + SKU精细更新 | ✅ 完成 | 新增 PUT /api/products/skus 单个SKU更新；syncProductSkus 保留已有 SKU price/costPrice/barCode/status；delete/deleteColor/deleteSize 添加引用保护，有引用时提示建议禁用；39 个后端测试全部通过 |

### Phase 6.7: 客户 iPad 现货展示页后端（P1）

> 详细内容见：docs/12-FILE_CENTER_ASSET_DESIGN.md

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-1020 | Catalog 商品/SKU 展示接口 | ✅ 完成 | GET /api/catalog/products、GET /api/catalog/products/{id}；只返回展示字段和图片预览 URL，不返回价格/成本/供应商/真实库存数量 |
| BE-1021 | Catalog 库存状态聚合 | ✅ 完成 | 按 quantity - reservedQty - globalReservedQty 判断有现货/暂无现货；stockMode=in_stock 在分页前过滤，避免 total 与卡片数量不一致 |
| BE-1022 | Catalog 筛选项接口 | ✅ 完成 | GET /api/catalog/filters；分类、颜色、尺码、全部/现货/有图筛选 |
| BE-1023 | Catalog 只读权限 | ✅ 完成 | V37 新增 menu:catalog、data:catalog:view，默认授权 ROLE_OWNER/ROLE_ADMIN/ROLE_SALES；第一版不做公开分享链接 |

### Phase 6.8: 后端测试基线修复（P1）

> 分支：`fix/backend-test-baseline`。目标是清理当前 `master/develop` 都存在的后端全量测试红灯，使后续集成测试可以可靠判断真实回归；不得为通过测试而放宽生产认证、权限或订单状态业务规则。

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BE-1030 | 后端全量测试失败归因与基线记录 | ✅ 完成 | 初始 `mvn test` 失败 40 个；已确认 `master` 基线同样失败，非商品管理 v2 新增回归。失败主因：登录测试缺 `tenantCode`、订单测试状态机口径过旧、实体字段缺少显式列映射。 |
| BE-1031 | Catalog/Product Controller 测试认证基线修复 | ✅ 完成 | `CatalogControllerTest`、旧 `ProductControllerTest` 已按现有多租户登录规则补齐 `tenantCode=test_tenant` 和正确测试密码；Product 测试改用唯一商品编码，避免重复执行污染。 |
| BE-1032 | OrderControllerTest 状态码与订单状态口径修复 | ✅ 完成 | 断言已对齐 `GlobalExceptionHandler` 业务错误 400、当前订单状态值 0-8，以及发货前需创建配货计划并确认调整的状态机流程。 |
| BE-1033 | 后端全量测试收口 | ✅ 完成 | `cd blade-backend && mvn test` 通过：Tests run 244, Failures 0, Errors 0, Skipped 0；定向回归 `ProductControllerTest,CatalogControllerTest,FileControllerTest,FileBindingControllerTest,ProductFileBindingServiceTest,ProductFileBindingControllerTest` 通过 73/73。 |

---

## PC 管理端开发任务（blade-admin）

### Phase 1: 骨架搭建

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-001 | Vue3 项目初始化 | ✅ 完成 | 从零搭建 Vue3 + Element Plus + TailwindCSS，已完成登录页、布局、仪表盘 |
| BA-002 | 项目结构搭建 | ✅ 完成 | views/ router/ stores/ api/ 已搭建 |
| BA-003 | 布局组件 | ✅ 完成 | 侧边栏菜单 + 顶部导航 |
| BA-004 | 登录页 | ✅ 完成 | 多租户登录 + 验证码 + 错误处理 + 保持登录 30 天；登录过期回登录页时保留当前页面 redirect |

### Phase 2: 订单管理

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-201 | 订单列表页 | ✅ 完成 | 表格 + 高级筛选 + 分页 + 支付状态筛选 + 编辑按钮 + 订单上下文摘要 + 图片 fileId 字段 |
| BA-202 | 订单详情页 | ✅ 完成 | 查看 + 状态操作 + 支付状态/定金/送货状态 + 追加收款按钮（status=0且paymentStatus≠2）+ 追加收款弹窗 |
| BA-203 | 新建订单页 | ✅ 完成 | 支付状态单选框 + 定金输入 + 送货设置 + 客户搜索（电话匹配）+ 批量添加商品（颜色尺码矩阵）+ 单价可调整 + 可选库存筛选 |
| BA-204 | 订单导出 | ✅ 完成 | GET /api/orders/export，支持筛选条件，EasyExcel生成，浏览器下载 |
| BA-205 | 订单显示开单人员 | ✅ 完成 | 订单列表增加开单人员列，展示 salesmanName |
| BA-206 | PC 快速录单增强 | ✅ 完成 | 单张纸单连续录入、来源档口/店铺独立于仓库、订货/现货标记、运费收入/成本、成本与毛利快照、订单图片统一上传、结算与汇总左右并列、列表/详情/导出展示 |
| BA-207 | PC 快速录单商品级批量 SKU 录入 | ✅ 完成 | 选择商品后展示正常状态 SKU 矩阵，批量填写颜色/尺码数量并一次性添加到订单；不读取、不展示、不校验库存；重复 skuId 自动合并数量且不覆盖已改单价/成本价；Claude Code 实现，Codex 复核并修正重复合并提示与正常状态过滤 |

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
| BA-407 | 商品编辑页 v2 信息架构 | ✅ 完成 | 商品编辑弹窗升级为1100px宽4Tab分区：基础信息、颜色尺码、SKU明细、商品素材；Tab内容独立滚动；新建时仅显示基础信息和颜色尺码 |
| BA-408 | SKU 明细精细维护 | ✅ 完成 | SKU明细Tab展示SKU表格，支持inline编辑售价/成本价/条码/状态（el-switch）；脏跟踪+批量保存调用PUT /api/products/skus |
| BA-409 | 商品素材管理内聚到商品页 | ✅ 完成 | 素材Tab展示主图/图集/SKU图片管理；每个SKU行可上传/fileId添加/点击移除图片；saveFileBindings发送全部SKU的skuImageBindings；复用filePreviewUrl()预览；素材独立保存 |
| BA-410 | 商品删除/禁用交互优化 | ✅ 完成 | 商品/颜色/尺码/分类删除前提示引用风险和建议禁用；删除失败时弹窗展示后端引用保护消息 |

### Phase 5: 客户管理

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-501 | 客户列表页 | ✅ 完成 | 表格 + 筛选 + 分页 + 新建/编辑/删除弹窗 |
| BA-502 | 客户编辑页 | ✅ 完成 | 新增/编辑客户表单 |

### Phase 6: 看板统计

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-601 | 仪表盘 | ✅ 完成 | 数字卡片 + 趋势图 + ECharts，第一行随日期范围动态展示订单/销售额/毛利/销量 |
| BA-602 | 订单统计 | ✅ 完成 | 趋势图集成到仪表盘 |
| BA-603 | 库存统计 | ⏳ TODO | 预警/周转分析，平均在库天数已移除 |
| BA-604 | 数据分析页 | ✅ 完成 | 独立 /analytics 页面，销售+商品分析，毛利字段按权限展示 |

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
| BA-704 | 个人中心页面 | ✅ 完成 | 修改密码、个人信息；头部下拉菜单新增个人中心入口 |

### Phase 8: OCR 拍照录单（P2）

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-801 | 拍照录单页（方案A） | ⏳ TODO | 上传图片 + 半自动表单（款号手动选） |
| BA-802 | 拍照录单页（方案B） | ⏳ TODO | AI 识别 + 置信度标注 + 候选推荐 |

### Phase 9: 统一文件存储接入（P1）

> 详细内容见：docs/09-FILE_STORAGE_DESIGN.md

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-901 | PC 订单图片上传接入 | ✅ 完成 | 新建/快速录单/编辑/详情页接入统一文件接口，快速录单与编辑弹窗支持图片墙上传/预览/移除，订单保存 fileId |
| BA-902 | PC 商品主图上传接入 | ✅ 完成 | 商品新建/编辑/列表接入统一文件接口，商品保存 fileId |
| BA-903 | PC 入库凭证上传接入 | ✅ 完成 | 入库弹窗接入统一文件接口，入库日志保存 fileId |
| BA-904 | PC 图片上传 E2E 回归 | ✅ 完成 | Playwright 覆盖商品主图、编辑订单、快速录单图片上传，断言统一文件接口返回 fileId、预览图可加载且无系统错误 |

### Phase 10: PC 文件中心（P1）

> 详细内容见：docs/12-FILE_CENTER_ASSET_DESIGN.md

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-1001 | 文件中心路由与菜单 | ✅ 完成 | 新增 /files 一级入口、menu:file 权限、pageTitle 和 firstPage 映射；V36 补齐文件中心菜单/按钮权限并分配 ROLE_OWNER/ROLE_ADMIN |
| BA-1002 | 文件夹树与虚拟入口 | ✅ 完成 | 全部文件、未绑定、商品素材、SKU 图片、订单图片、入库凭证、视频、回收站 + 真实文件夹树 API 集成 |
| BA-1003 | 文件网格/列表视图 | ✅ 完成 | 图片卡片+视频占位网格、el-table 列表、keyword/fileType/businessType 筛选、分页、图片/视频预览弹窗、loading/empty 状态 |
| BA-1004 | 上传/预览/移动/删除 | ✅ 完成 | 上传按钮支持多文件到 temp；前端按 200MB 做上传前校验；网格/列表视图可多选；批量工具栏移动/绑定/删除；移动弹窗选文件夹或未归档；删除前查询绑定关系展示风险信息；仅 POST /api/files/batch-delete |
| BA-1005 | 商品/SKU 绑定弹窗 | ✅ 完成 | FileBindDialog：远程搜索商品，选角色 main/gallery/sku_image，SKU 图片角色显示 SKU 多选，PUT /api/products/{id}/file-bindings |
| BA-1006 | 未绑定文件清理管理 | ✅ 完成 | FileCleanupPanel：清理说明/保留天数/候选统计/刷新/软删除确认/回收站快捷入口；使用 GET unbound-candidates + POST soft-delete-unbound |
| BA-1007 | PC 图片缩略图接入 | ⏳ TODO | 新增 fileVariantUrl 工具；商品列表、订单图片墙、文件中心网格/列表优先加载 thumb/card，点击预览/打开原文件仍加载原图 |

### Phase 11: 客户 iPad 现货展示页（P1）

> 详细内容见：docs/12-FILE_CENTER_ASSET_DESIGN.md

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| BA-1020 | Catalog 展示页路由 | ✅ 完成 | 新增独立全屏 `/catalog` 路由，登录后需 `data:catalog:view`；不挂后台侧边栏 |
| BA-1021 | 商品相册网格 | ✅ 完成 | quiet luxury 视觉；横屏网格 + 右侧详情、竖屏 2 列网格；商品网格只展示商品主图，无图时显示占位 |
| BA-1022 | 商品详情与 SKU 矩阵 | ✅ 完成 | 横屏右侧详情、竖屏底部抽屉；详情顶部轮播展示商品图 + 所有 SKU 图片全集；SKU 颜色/尺码矩阵展示有现货/暂无现货 |
| BA-1023 | 全部/现货/有图筛选 | ✅ 完成 | 使用 Catalog API 的 keyword/category/color/size/stockMode/hasImage；前端不自行拼库存 |
| BA-1024 | iPad PWA 体验优化 | ✅ 完成 | 大触控区域、懒加载、横竖屏适配、全屏大图模式；全屏大图只浏览商品图片集，不混入 SKU 图片；不展示成本、毛利、真实库存 |
| BA-1025 | Catalog 无限滚动与本地缓存 | ✅ 完成 | 商品网格取消分页器，滚动触底自动请求下一页；筛选维度缓存商品列表；图片按 fileId 写入 IndexedDB，命中后使用本地 Blob URL |
| BA-1026 | Catalog 图片滑动切换 | ✅ 完成 | 详情轮播和全屏大图支持左右滑动切图；增加跟手滑动与 220ms 相册式过渡；保留按钮/缩略图；仅拦截单指双击页面放大，保留两指缩放；横竖屏切换后恢复正常视口 |
| BA-1027 | Catalog 手机竖屏版 | ✅ 完成 | iPhone 14 Pro 竖屏断点；保持 iPad quiet luxury 风格；两列商品卡片、底部详情抽屉、全屏大图；手机版横屏显示切回竖屏提示，不提供横屏浏览布局 |
| BA-1028 | Catalog 派生图加载优化 | ⏳ TODO | Catalog 商品卡片使用 card，缩略图条使用 thumb，全屏大图/下载仍使用原图；与 IndexedDB 图片缓存策略兼容 |

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

### Phase 3.5: 统一文件存储接入（P1）

> 详细内容见：docs/09-FILE_STORAGE_DESIGN.md

| 任务 ID | 任务 | 状态 | 备注 |
|---------|------|------|------|
| FE-901 | 移动端入库图片上传接入 | ✅ 完成 | 入库页上传图片到统一文件接口，入库日志保存 fileId |

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

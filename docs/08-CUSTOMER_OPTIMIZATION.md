# 客户模块优化方案

> 本文档详细描述客户模块国际化完成后的优化计划。
> 版本：v1.0，日期：2026-04-26

---

## 一、问题背景

客户模块国际化（Phase 4.5）已完成，E2E 测试 12/12 通过。但在深度分析中发现以下问题：

### 1.1 数据质量问题

| 问题 | 严重度 | 影响 |
|------|--------|------|
| 电话号码未做租户内唯一性校验 | P1 | 同租户可创建重复电话客户，导致订单关联错乱 |
| 删除客户时未检查进行中订单 | P1 | 删除后订单丢失客户关联，统计数据不准确 |
| getPreference() 存在 N+1 查询 | P1 | 100 个订单用户偏好分析需 100+ 次 DB 查询，延迟 >2s |

### 1.2 用户体验问题

| 问题 | 严重度 | 影响 |
|------|--------|------|
| 订单记录无分页 | P2 | 客户历史订单 >50 条时加载卡顿 |
| 国家选择器无记忆功能 | P2 | 常用国家每次都需搜索 |
| 国家选择器无键盘导航 | P2 | 高级用户效率低 |

### 1.3 业务功能缺失

| 问题 | 严重度 | 影响 |
|------|--------|------|
| 无客户标签功能 | P2 | 无法对客户分层运营（VIP/沉默/新客） |
| 无沉默客户预警 | P2 | 流失客户无法提前发现 |
| 偏好分析无时间范围 | P2 | 新老客户偏好数据混排 |

### 1.4 架构能力缺失

| 问题 | 严重度 | 影响 |
|------|--------|------|
| 无数据权限隔离 | P3 | 租户下数据可被其他用户查看 |
| 无操作审计日志 | P3 | 敏感操作无记录 |
| 偏好数据无缓存 | P3 | 每次查看都重新计算 |

---

## 二、优化方案

### M1: 数据质量（P1）

#### BE-412: 电话重复检查

**问题**：同租户内可创建重复电话客户。

**方案**：
- 数据库层：`crm_customer` 表对 `(tenant_id, country_code, phone)` 建唯一索引
- 应用层：创建/更新客户时查询是否存在冲突，冲突返回 `400 Bad Request`

**关键文件**：
```
blade-backend/src/main/java/com/blade/customer/
├── service/impl/CustomerServiceImpl.java  # 新增 checkPhoneDuplicate()
└── mapper/CustomerMapper.java             # 新增 selectByPhone()

db/migration/V25__customer_phone_unique.sql  # 新建：唯一索引
```

**验收标准**：
```
1. 创建客户时，电话与现有客户冲突 → 返回 400 + "该电话已被使用"
2. 更新客户时，改为已有电话 → 返回 400
3. 同一租户内电话可重复（不同区号）
4. 无区号客户与有区号客户电话不冲突
```

---

#### BE-413: 删除客户订单保护

**问题**：删除客户时未检查是否存在进行中订单。

**方案**：
- 删除前查询客户是否有 `status NOT IN (4, 5)` 的订单
- 有则返回 400，提示「该客户有进行中订单，请先处理」

**关键文件**：
```
blade-backend/src/main/java/com/blade/customer/
└── service/impl/CustomerServiceImpl.java  # 新增 checkActiveOrders()
```

**验收标准**：
```
1. 删除有进行中订单的客户 → 返回 400 + 提示订单号
2. 删除已发货/已完成订单的客户 → 成功
3. 删除无订单客户 → 成功
```

---

#### BE-414: N+1 查询优化

**问题**：`getPreference()` 循环查询 OrderItem。

**当前代码**：
```java
// ❌ N+1 查询：100个订单 = 100次 DB 查询
for (Order order : orders) {
    List<OrderItem> items = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId())
    );
}
```

**优化后**：
```java
// ✅ 单条 IN 查询
Set<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toSet());
List<OrderItem> allItems = orderItemMapper.selectList(
    new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds)
);
// 内存中按 orderId 分组
Map<Long, List<OrderItem>> itemsByOrder = allItems.stream()
    .collect(Collectors.groupingBy(OrderItem::getOrderId));
```

**关键文件**：
```
blade-backend/src/main/java/com/blade/customer/
└── service/impl/CustomerServiceImpl.java  # 重构 getPreference()
```

**验收标准**：
```
1. 100 个历史订单用户，偏好分析接口响应时间 < 500ms
2. 返回的偏好数据与优化前完全一致
3. 数据库查询次数从 N+1 降为 1
```

---

### M2: 用户体验（P2）

#### BE-415: 订单记录分页

**问题**：客户详情订单记录无分页。

**方案**：
- `GET /api/customers/{id}/orders` 支持 `page` + `pageSize` 参数
- `CustomerOrderVO` 保持不变，新增分页元数据

**接口变更**：
```
GET /api/customers/{id}/orders?page=1&pageSize=20

Response:
{
  "code": 200,
  "data": {
    "records": [...],      // List<CustomerOrderVO>
    "total": 100,          // 总记录数
    "page": 1,             // 当前页
    "pageSize": 20        // 每页条数
  }
}
```

**关键文件**：
```
blade-backend/src/main/java/com/blade/customer/
├── controller/CustomerController.java      # 分页参数
├── service/CustomerService.java           # 接口签名更新
└── service/impl/CustomerServiceImpl.java  # 分页实现

blade-admin/src/api/customer.ts            # 前端分页调用
blade-admin/src/views/customers/detail.vue  # 分页组件
```

**验收标准**：
```
1. 默认 page=1, pageSize=20
2. 支持自定义 pageSize（最大 100）
3. 前端订单记录 Tab 显示分页器
4. 总数显示「共 X 条订单」
```

---

#### BE-416: 常用国家置顶

**问题**：常用国家每次需搜索。

**方案**：
- 选中国家后，写入 `localStorage.setItem('recentCountries', JSON.stringify([...]))`
- 最多存储 5 个，按使用频率排序
- 国家选择器打开时，列表顶部显示「常用」区块

**关键文件**：
```
blade-admin/src/components/CountryCodeSelect.vue  # 读取/写入 localStorage
```

**验收标准**：
```
1. 选择国家后，localStorage 更新
2. 重新打开选择器，常用国家显示在顶部
3. 最多显示 5 个常用国家
4. 清除浏览器缓存后，常用国家重置
```

---

#### BE-417: 国家选择器键盘导航

**问题**：无键盘导航，鼠标操作效率低。

**方案**：
- 聚焦搜索框时，↑↓ 键在列表间移动
- Enter 键选中当前项
- Esc 键关闭面板
- 打字时自动聚焦第一个匹配项

**关键文件**：
```
blade-admin/src/components/CountryCodeSelect.vue  # 键盘事件处理
```

**验收标准**：
```
1. 聚焦输入框后，按 ↓ 键选中第一个国家
2. 继续按 ↓/↑ 键上下导航
3. 按 Enter 键选中当前项
4. 按 Esc 键关闭面板
5. 打字搜索时自动选中第一个匹配项
```

---

### M3: 业务功能（P2）

#### BE-418: 客户标签功能

**问题**：无法对客户分层运营。

**方案**：

**数据库**：
```sql
-- 标签表
CREATE TABLE crm_customer_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(32) NOT NULL COMMENT '标签名',
    color VARCHAR(8) NOT NULL COMMENT '颜色，如 #FF6B6B',
    sort INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_name (tenant_id, name)
);

-- 客户-标签关联表
CREATE TABLE crm_customer_tag_rel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_tag (customer_id, tag_id)
);
```

**API**：
```
GET    /api/customer-tags         # 标签列表
POST   /api/customer-tags         # 创建标签
PUT    /api/customer-tags/{id}    # 更新标签
DELETE /api/customer-tags/{id}    # 删除标签
POST   /api/customers/{id}/tags   # 分配标签
DELETE /api/customers/{id}/tags/{tagId}  # 移除标签
```

**前端**：
- 客户列表页：标签筛选下拉 + 标签列显示
- 客户详情页：标签管理区块
- 标签管理页（系统管理下）：CRUD 标签

**验收标准**：
```
1. 可创建/编辑/删除标签（颜色+名称）
2. 可为客户分配多个标签
3. 客户列表页支持按标签筛选
4. 客户列表页显示标签列（彩色 tag）
5. 删除标签时自动解除关联
```

---

#### BE-419: 沉默客户预警

**问题**：流失客户无法提前发现。

**方案**：

**定义**：最后订单距今 >90 天，且不属于已流失客户（已完成订单 >0）。

**接口**：
```
GET /api/dashboard/silent-customers?days=90

Response:
{
  "code": 200,
  "data": {
    "total": 15,
    "customers": [
      {
        "id": 1,
        "name": "张三",
        "countryCode": "+86",
        "phone": "13800138000",
        "lastOrderDate": "2026-01-15",
        "daysSinceLastOrder": 101
      }
    ]
  }
}
```

**关键文件**：
```
blade-backend/src/main/java/com/blade/dashboard/
└── service/DashboardService.java    # 新增 getSilentCustomers()
```

**前端**：
- 仪表盘新增「沉默客户」统计卡片
- 点击卡片跳转到沉默客户列表页

**验收标准**：
```
1. 仪表盘显示沉默客户数量卡片
2. 默认定义 90 天，可后台配置
3. 沉默客户列表显示最后订单日期和天数
4. 支持跳转至客户详情
```

---

#### BE-420: 偏好时间范围筛选

**问题**：偏好分析无时间范围，新老客户数据混排。

**方案**：

**接口变更**：
```
GET /api/customers/{id}/preference?startDate=2025-01-01&endDate=2026-04-26

# 新增参数
- startDate: 可选，默认 365 天前
- endDate: 可选，默认今天
```

**关键文件**：
```
blade-backend/src/main/java/com/blade/customer/
├── service/CustomerService.java           # 接口签名更新
└── service/impl/CustomerServiceImpl.java  # 时间范围过滤
```

**前端**：
- 客户详情页「商品偏好」Tab 顶部新增日期范围选择器
- 默认显示「近一年」，支持自定义

**验收标准**：
```
1. 默认显示近 365 天数据
2. 支持自定义日期范围
3. 切换时间范围后，偏好数据重新计算
4. 图表标题显示当前时间范围
```

---

### M4: 架构能力（P3）

#### BE-421: 客户数据权限

**问题**：租户筛选自动附加，但无「只看我的客户」选项。

**方案**：
- 客户表 `create_by` 字段记录创建人
- 列表接口支持 `mine=true` 筛选只看自己创建的客户
- 仅超管（admin）可看全部

**接口变更**：
```
GET /api/customers?mine=true  # 只看我的客户
```

**关键文件**：
```
blade-backend/src/main/java/com/blade/customer/
├── service/impl/CustomerServiceImpl.java  # 按 create_by 过滤
```

**验收标准**：
```
1. admin 用户可看所有客户
2. 普通用户默认只看自己创建的客户（create_by = 当前用户）
3. mine=true 时强制只看自己
4. 客户详情页，任何用户可查看（不限制）
```

---

#### BE-422: 操作审计日志

**问题**：客户增删改无记录。

**方案**：

**复用已有日志表**：`operation_log`（如已存在）或新建 `crm_customer_log`。

**日志字段**：
```java
private Long id;
private String module;        // "客户管理"
private String operation;      // "CREATE/UPDATE/DELETE"
private Long customerId;       // 客户ID
private Long operatorId;       // 操作人
private String detail;         // JSON：变更前后值
private LocalDateTime createTime;
```

**关键文件**：
```
blade-backend/src/main/java/com/blade/common/
└── handler/OperationLogHandler.java   # AOP 拦截或手动记录
```

**验收标准**：
```
1. 创建客户记录：operatorId + customerId + detail（含创建的基本信息）
2. 更新客户记录：operatorId + customerId + detail（含变更字段旧值→新值）
3. 删除客户记录：operatorId + customerId + detail
4. 操作日志仅管理员可查询
```

---

#### BE-423: 偏好数据缓存

**问题**：每次查看客户偏好都重新计算。

**方案**：
- 偏好结果缓存至 Redis，key = `customer:preference:{customerId}`
- TTL = 1 小时
- 订单状态变更时，主动淘汰缓存

**Redis Key 设计**：
```
customer:preference:123  # JSON 字符串，TTL=3600s
```

**淘汰策略**：
- 订单创建/发货/完成时，删除相关客户偏好缓存
- 客户编辑时不淘汰（偏好与个人信息无关）

**关键文件**：
```
blade-backend/src/main/java/com/blade/config/
└── RedisConfig.java                   # RedisTemplate 配置

blade-backend/src/main/java/com/blade/customer/
└── service/impl/CustomerServiceImpl.java  # 缓存读写
```

**验收标准**：
```
1. 首次访问偏好接口，查询 DB 并写入 Redis
2. 1 小时内再次访问，直接返回缓存（响应时间 < 10ms）
3. 订单状态变更后，缓存被淘汰
4. 缓存 key 过期后自动失效，下次访问重新计算
```

---

## 三、里程碑

| 里程碑 | 任务 | 计划工期 | 验收方式 |
|--------|------|---------|---------|
| M1: 数据质量 | BE-412, BE-413, BE-414 | 2 天 | API 测试 |
| M2: 用户体验 | BE-415, BE-416, BE-417 | 2 天 | E2E 测试 |
| M3: 业务功能 | BE-418, BE-419, BE-420 | 3 天 | 功能测试 |
| M4: 架构能力 | BE-421, BE-422, BE-423 | 2 天 | API 测试 |

**总计**：9 个工作日

---

## 四、依赖关系

```
BE-412 (电话重复检查)
    ↓
BE-413 (删除订单保护)  [BE-412 是前提，需先有唯一索引]

BE-414 (N+1优化)
    ↓
BE-415 (订单分页)  [可选：独立，不依赖]

BE-418 (标签功能)
    ↓
BE-419 (沉默客户)  [依赖 BE-418 的标签表结构]

BE-421 (数据权限)  [独立]
BE-422 (审计日志)  [独立]
BE-423 (缓存)      [独立]
```

---

## 五、风险与注意事项

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 唯一索引冲突数据 | 迁移脚本可能失败 | 提前检查并清理重复数据 |
| 缓存与 DB 不一致 | 偏好数据过期 | 订单变更时主动淘汰缓存 |
| 标签删除时客户残留关联 | 数据不一致 | 删除标签前检查并提示 |

---

## 六、验收检查清单

完成每个任务后：

- [ ] 后端接口测试通过（curl 或 Postman）
- [ ] 前端功能正常，无 console 错误
- [ ] 数据库迁移脚本验证
- [ ] 任务状态更新至 03-TASKS.md
- [ ] 变更记录更新至 05-CHANGELOG.md

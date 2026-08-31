# API 接口规范

> 本文档记录所有后端 API 接口的详细规格。
> AI 开发时必须严格按照本文档执行。
> 新 AI 必须先阅读本文档再开始对接接口。

---

## 一、基础信息

### 1.1 基础 URL

```
开发环境：http://localhost:8080
```

### 1.2 认证方式

所有接口（除登录相关）需要在请求头中携带 Token：

```
Authorization: Bearer {jwt_token}
```

### 1.3 统一响应格式

所有接口响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1742572800
}
```

### 1.4 错误码

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未认证（Token 无效或过期） |
| 403 | 无权限（角色/权限不足） |
| 404 | 资源不存在 |
| 500 | 服务器错误 |

---

## 二、认证接口

### 2.1 登录

```
POST /api/auth/login
```

**请求体**：
```json
{
  "tenantCode": "super_admin",
  "username": "admin",
  "password": "admin123",
  "remember": true
}
```

`remember=true` 表示保持登录 30 天：access token 为 1 小时，refresh token 为 30 天，前端在 access token 已过期或 10 分钟内即将过期时自动续期并重试原请求。未传或为 `false` 时 refresh token 使用默认 7 天。

**成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 3600
  }
}
```

**失败响应**：
```json
{
  "code": 401,
  "message": "用户名或密码错误",
  "data": null
}
```

---

### 2.2 刷新 Token

```
POST /api/auth/refresh
```

**请求头**：
```
Authorization: Bearer {refresh_token}
```

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 1800
  }
}
```

---

### 2.3 登出

```
POST /api/auth/logout
```

**请求头**：
```
Authorization: Bearer {token}
```

**成功响应**：
```json
{
  "code": 200,
  "message": "success"
}
```

---

## 三、订单接口

### 3.1 订单列表

```
GET /api/orders
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| current | int | 否 | 当前页，默认 1 |
| size | int | 否 | 每页条数，默认 20 |
| status | int | 否 | 订单状态：0创建/1已付款/2已发货/3已完成/4已取消/5退货中/6已退货 |
| keyword | string | 否 | 搜索关键词（订单号/客户名） |

**请求示例**：
```
GET /api/orders?current=1&size=20&status=0&keyword=张三
```

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "orderNo": "ORD202603210001",
        "customerName": "张三",
        "totalAmount": 1000.00,
        "status": 0,
        "statusName": "待处理",
        "createTime": "2026-03-21 10:00:00"
      }
    ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

---

### 3.2 订单详情

```
GET /api/orders/{id}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 订单 ID |

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "orderNo": "ORD202603210001",
    "customerName": "张三",
    "customerPhone": "13800138000",
    "customerAddress": "北京市朝阳区xxx",
    "totalAmount": 1000.00,
    "status": 0,
    "statusName": "待处理",
    "remark": "尽快发货",
    "items": [
      {
        "productName": "商品A",
        "sku": "SKU001",
        "price": 100.00,
        "quantity": 10,
        "subtotal": 1000.00
      }
    ],
    "createTime": "2026-03-21 10:00:00",
    "updateTime": "2026-03-21 10:00:00"
  }
}
```

---

### 3.3 创建订单

```
POST /api/orders
```

**请求体**：
```json
{
  "customerName": "张三",
  "customerPhone": "13800138000",
  "customerAddress": "北京市朝阳区xxx",
  "remark": "尽快发货",
  "items": [
    {
      "productId": 1,
      "skuId": 101,
      "price": 100.00,
      "quantity": 10
    }
  ]
}
```

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "orderNo": "ORD202603210001"
  }
}
```

---

### 3.4 更新订单状态

```
PUT /api/orders/{id}/status
```

**请求体**：
```json
{
  "status": 1
}
```

**状态码说明**：
| 值 | 名称 |
|----|------|
| 0 | 创建 |
| 1 | 已付款 |
| 2 | 配货中（ADJUSTMENT_PENDING） |
| 3 | 待发货（READY_TO_SHIP） |
| 4 | 已发货 |
| 5 | 已完成 |
| 6 | 已取消 |
| 7 | 退货中（预留，流程未实现） |
| 8 | 已退货（预留，流程未实现） |

**成功响应**：
```json
{
  "code": 200,
  "message": "状态更新成功"
}
```

---

### 3.5 删除订单

```
DELETE /api/orders/{id}
```

**成功响应**：
```json
{
  "code": 200,
  "message": "删除成功"
}
```

---

### 3.6 更新订单基础信息

```
PUT /api/orders/{id}
```

**请求体**：
```json
{
  "customerName": "张三",
  "customerPhone": "13800138000",
  "customerAddress": "北京市朝阳区xxx",
  "needDelivery": 1,
  "deliveryAddress": "送货地址",
  "remark": "备注",
  "images": "[\"101\",\"102\"]"
}
```

**说明**：所有字段均为可选，按字段是否传值选择性更新。`status >= 4`（已发货/已完成/已取消）的订单禁止修改。

**成功响应**：
```json
{
  "code": 200,
  "message": "success"
}
```

---

### 3.7 追加收款

```
POST /api/orders/{id}/add-payment
```

**触发条件**：订单 `status === 0`（创建状态）且 `paymentStatus !== 2`（未付全款）

**请求体**：
```json
{
  "additionalAmount": 2000.00
}
```

**业务规则**：
- 在当前 `paidAmount` 基础上累加本次收款金额
- 累加后 `paidAmount >= totalAmount` → `paymentStatus` 自动变为 2（全款）
- 累加后 `paidAmount < totalAmount` → `paymentStatus` 保持或变为 1（定金）
- 不改变订单 `status`，不涉及库存操作

**成功响应**：
```json
{
  "code": 200,
  "message": "success"
}
```

**错误响应**（400 + 可读错误信息）：
- "只有创建状态的订单才可追加收款"
- "订单已付全款，无需追加"
- "追加后金额不能超过订单总额"

---

## 三、客户接口

### 3.1 客户分页列表

```
GET /api/customers
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| current | int | 否 | 当前页，默认 1 |
| size | int | 否 | 每页条数，默认 20 |
| keyword | string | 否 | 搜索关键词（客户名称/电话） |

**成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "name": "张三",
        "countryCode": "+86",
        "countryName": "China",
        "countryFlag": "🇨🇳",
        "phones": ["13800138000"],
        "address": "北京市朝阳区",
        "remark": "VIP客户",
        "orderCount": 5,
        "createTime": "2026-04-01T10:00:00"
      }
    ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

### 3.2 获取客户详情

```
GET /api/customers/{id}
```

**路径参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 客户ID |

**成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "张三",
    "countryCode": "+86",
    "countryName": "China",
    "countryFlag": "🇨🇳",
    "phones": ["13800138000"],
    "address": "北京市朝阳区",
    "remark": "VIP客户",
    "orderCount": 5,
    "createTime": "2026-04-01T10:00:00"
  }
}
```

### 3.3 根据电话搜索客户

```
GET /api/customers/search?phone={phone}
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| phone | string | 是 | 电话号码（支持带区号或不带） |

**成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "张三",
    "countryCode": "+86",
    "phones": ["13800138000"],
    ...
  }
}
```

### 3.4 创建客户

```
POST /api/customers
```

**请求体**：
```json
{
  "name": "李小姐",
  "phones": ["688888888"],
  "countryCode": "+255",
  "address": "Dar es Salaam, Tanzania",
  "remark": "来自坦桑尼亚的客户"
}
```

**成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": 1
}
```

### 3.5 更新客户

```
PUT /api/customers
```

**请求体**：
```json
{
  "id": 1,
  "name": "李小姐（已编辑）",
  "phones": ["688888888"],
  "countryCode": "+1",
  "address": "New York, USA",
  "remark": "搬家了"
}
```

**成功响应**：
```json
{
  "code": 200,
  "message": "success"
}
```

### 3.6 删除客户

```
DELETE /api/customers/{id}
```

**路径参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 客户ID |

**成功响应**：
```json
{
  "code": 200,
  "message": "success"
}
```

### 3.7 客户基础统计

```
GET /api/customers/{id}/stats
```

**功能说明**：获取指定客户的统计数据，包括订单数、消费金额、时间范围等。

**路径参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 客户ID |

**成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "customerId": 1,
    "customerName": "张三",
    "totalOrders": 10,
    "completedOrders": 8,
    "totalSpending": 25800.00,
    "lastOrderTime": "2026-04-20T15:30:00",
    "firstOrderTime": "2026-03-01T09:00:00"
  }
}
```

### 3.8 客户历史订单

```
GET /api/customers/{id}/orders
```

**功能说明**：获取指定客户的所有历史订单列表（包含订单项详情）。

**路径参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 客户ID |

**成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "orderNo": "ORD202604200001",
      "status": 5,
      "statusName": "已完成",
      "paymentStatus": 2,
      "totalAmount": 5000.00,
      "paidAmount": 5000.00,
      "totalAmountText": "¥5000.00",
      "paidAmountText": "¥5000.00",
      "createTime": "2026-04-20T10:00:00",
      "totalQuantity": 10,
      "items": [
        {
          "productName": "T恤",
          "skuDesc": "红色 / XL",
          "quantity": 5,
          "price": 500.00
        },
        {
          "productName": "牛仔裤",
          "skuDesc": "蓝色 / 32",
          "quantity": 5,
          "price": 500.00
        }
      ]
    }
  ]
}
```

### 3.9 客户商品偏好分析

```
GET /api/customers/{id}/preference
```

**功能说明**：基于客户已完成/已发货的订单，分析其商品偏好（颜色、尺码、品类）。

**数据来源**：
- 统计范围：订单状态为 `4=已发货` 或 `5=已完成` 的订单
- 不统计：状态 `0=创建`、`1=已付款`、`2=配货中`、`3=待发货`、`6=已取消`

**路径参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 客户ID |

**成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "customerId": 1,
    "productTypeCount": 3,
    "categories": [
      { "categoryName": "T恤", "count": 15, "percentage": 37.5 },
      { "categoryName": "牛仔裤", "count": 12, "percentage": 30.0 },
      { "categoryName": "连衣裙", "count": 8, "percentage": 20.0 }
    ],
    "colors": [
      { "colorName": "黑色", "count": 18, "percentage": 45.0 },
      { "colorName": "白色", "count": 10, "percentage": 25.0 },
      { "colorName": "蓝色", "count": 7, "percentage": 17.5 }
    ],
    "sizes": [
      { "sizeName": "M", "count": 20, "percentage": 50.0 },
      { "sizeName": "L", "count": 10, "percentage": 25.0 },
      { "sizeName": "XL", "count": 5, "percentage": 12.5 }
    ]
  }
}
```

**偏好计算逻辑**：
```
百分比 = (该偏好 count) / (总订单项数) * 100
```
- 每种偏好类型最多返回 10 条记录（按 count 降序）
- percentage 保留 1 位小数

---

## 四、库存接口

### 4.1 库存列表

```
GET /api/inventory
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| current | int | 否 | 当前页，默认 1 |
| size | int | 否 | 每页条数，默认 20 |
| keyword | string | 否 | 搜索关键词（商品名称/SKU） |
| categoryId | long | 否 | 分类 ID |

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "productName": "商品A",
        "sku": "SKU001",
        "categoryName": "分类1",
        "stock": 100,
        "unit": "件",
        "alertThreshold": 10,
        "alertStatus": "normal"
      }
    ],
    "total": 50,
    "size": 20,
    "current": 1,
    "pages": 3
  }
}
```

---

### 4.2 入库

```
POST /api/inventory/in
```

**请求体**：
```json
{
  "items": [
    {
      "skuId": 101,
      "quantity": 50,
      "remark": "采购入库"
    }
  ]
}
```

**成功响应**：
```json
{
  "code": 200,
  "message": "入库成功"
}
```

---

### 4.3 出库

```
POST /api/inventory/out
```

**请求体**：
```json
{
  "items": [
    {
      "skuId": 101,
      "quantity": 10,
      "remark": "订单出库"
    }
  ]
}
```

**成功响应**：
```json
{
  "code": 200,
  "message": "出库成功"
}
```

---

### 4.4 库存预警

```
GET /api/inventory/alerts
```

**成功响应**：
```json
{
  "code": 200,
  "data": [
    {
      "skuId": 101,
      "productName": "商品A",
      "sku": "SKU001",
      "stock": 5,
      "alertThreshold": 10
    }
  ]
}
```

---

## 五、看板接口

### 5.1 统计概览

```
GET /api/dashboard/stats?periodType=WEEK
```

**订单统计口径**：按 `order_date` 统计，旧数据回退 `create_time`；只统计已产生收款订单（`paid_amount > 0` 或 `payment_status in (1,2)`）；销售额为 `max(total_amount - refund_amount, 0)`；销量按订单明细 `quantity` 汇总。

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "periodOrders": 25,
    "periodSales": 15800.00,
    "periodGrossProfit": 4200.00,
    "periodSalesQuantity": 96,
    "pendingOrders": 8,
    "lowStockAlerts": 3,
    "weekOrders": 25,
    "weekSales": 15800.00,
    "weekGrossProfit": 4200.00,
    "avgOrderValue": 632.00
  }
}
```

---

### 5.2 订单趋势

```
GET /api/dashboard/trend?periodType=WEEK
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| periodType | string | 否 | 周期：TODAY/WEEK/MONTH/QUARTER/YEAR/CUSTOM，默认 WEEK |
| startDate | date | 否 | 自定义开始日期，periodType=CUSTOM 时使用 |
| endDate | date | 否 | 自定义结束日期，periodType=CUSTOM 时使用 |

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "dates": ["03-15", "03-16"],
    "orderCounts": [20, 25],
    "salesAmounts": [15000.00, 18000.00]
  }
}
```

---

### 5.3 热销商品

```
GET /api/dashboard/top-products
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| limit | int | 否 | 返回条数，默认 5 |

**成功响应**：
```json
{
  "code": 200,
  "data": [
    {
      "productName": "商品A",
      "sku": "SKU001",
      "salesCount": 150,
      "salesAmount": 15000.00
    }
  ]
}
```

---

## 六、数据分析接口

### 6.1 经营汇总

```
GET /api/analytics/summary?periodType=WEEK
```

**统计口径**：与仪表盘订单口径一致；毛利字段受 `data:analytics:profit` 权限控制，无权限时返回为空。

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "orderCount": 25,
    "salesAmount": 15800.00,
    "salesQuantity": 96,
    "grossProfit": 4200.00,
    "grossProfitRate": 26.58,
    "refundAmount": 300.00,
    "avgOrderValue": 632.00,
    "avgItemPrice": 164.58,
    "profitVisible": true
  }
}
```

### 6.2 经营趋势

```
GET /api/analytics/trend?periodType=WEEK
```

返回 `dates`、`orderCounts`、`salesAmounts`、`salesQuantities`，有毛利权限时额外返回 `grossProfits`。

### 6.3 商品排行

```
GET /api/analytics/product-ranking?periodType=WEEK&dimension=PRODUCT&sortBy=SALES&limit=20
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| dimension | string | 否 | PRODUCT/SKU/COLOR/SIZE |
| sortBy | string | 否 | SALES/QUANTITY/GROSS_PROFIT |
| limit | number | 否 | 默认20，最大100 |

商品排行按订单明细聚合，不将订单级退款反摊到商品明细。

### 6.4 商品详情

```
GET /api/analytics/product-detail?periodType=WEEK&productName=624-1%23
```

返回该商品下 SKU、颜色、尺码三个维度的销售拆解。

---

## Agent 对接接口

> 接入方使用说明见 [11-AGENT_ACCESS_GUIDE.md](../11-AGENT_ACCESS_GUIDE.md)，需求边界见 [10-AGENT_INTEGRATION_DESIGN.md](../10-AGENT_INTEGRATION_DESIGN.md)。

### 认证约束

Agent API 不复用前端 JWT 登录态。第一版使用独立 Agent 凭证，示例请求头：

```http
X-Agent-Key: {agent_key}
```

凭证必须绑定租户和 scope。无 `agent:analytics:profit` 授权时，接口不得返回成本、毛利、毛利率。

### 已实现接口

#### 款式趋势销售事实包

```http
GET /api/agent/analytics/style-trends?periodType=MONTH&comparePeriods=3&limit=20
X-Agent-Key: {agent_key}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `periodType` | string | 否 | `TODAY` / `WEEK` / `MONTH` / `QUARTER` / `YEAR` / `CUSTOM`，默认 `WEEK` |
| `startDate` | date | 否 | `CUSTOM` 周期开始日期 |
| `endDate` | date | 否 | `CUSTOM` 周期结束日期 |
| `comparePeriods` | int | 否 | 对比周期数量，默认 `3`，当前限制 1-6 |
| `limit` | int | 否 | 返回条数，默认 `20` |

当前响应返回商品维度多周期趋势包，字段包含 `dimension`、`sortBy`、`periodType`、`comparePeriods` 和 `rows`。每个 row 包含当前周期销售事实、`trend`、`recommendation`、`periodSeries` 和 `reasons`。当前不返回成本、毛利、毛利率；库存和补货建议由后续库存建议接口承接。

#### 颜色尺码结构事实包

```http
GET /api/agent/analytics/sku-mix?productName=624-1%23&periodType=MONTH&limit=20
X-Agent-Key: {agent_key}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `productName` | string | 是 | 款式名称 |
| `periodType` | string | 否 | `TODAY` / `WEEK` / `MONTH` / `QUARTER` / `YEAR` / `CUSTOM`，默认 `WEEK` |
| `startDate` | date | 否 | `CUSTOM` 周期开始日期 |
| `endDate` | date | 否 | `CUSTOM` 周期结束日期 |
| `limit` | int | 否 | 每组返回条数，默认 `20` |

当前响应返回同款 SKU、颜色、尺码三组销售结构事实，字段包含 `productName`、`periodType`、`skus`、`colors`、`sizes` 和 `reasons`。每个 row 包含销售事实和 `signal`，当前 `signal` 表示销售结构：`HOT` / `NORMAL` / `LOW`。当前不返回成本、毛利、毛利率；缺货、积压和补货优先级由后续库存建议接口承接。

### 规划接口清单

| 接口 | 方法 | scope | 说明 |
|------|------|-------|------|
| `/api/agent/tasks/follow-up` | GET | `agent:followup:read` | 需跟进客户清单 |
| `/api/agent/customers/risk` | GET | `agent:customers:risk:read` | 客户流失风险与分层事实 |
| `/api/agent/inventory/recommendations` | GET | `agent:inventory:read` | 库存积压、缺货和补货优先级事实 |
| `/api/agent/reports/periodic` | GET | `agent:reports:read` | 月度、季度、年度经营分析数据包 |
| `/api/agent/search` | GET | `agent:search:read` | 客户、订单、商品/SKU 跨模块搜索 |

### 暂缓接口

| 接口 | 状态 | 原因 |
|------|------|------|
| `/api/agent/query` | 暂缓 | 第一版先由外部 Agent 调用结构化工具接口 |
| `/api/agent/action` | 暂缓 | 泛化写操作风险高，后续仅开放窄范围授权动作 |
| `/api/agent/changes` | 暂缓 | 依赖统一业务事件日志 |
| WhatsApp 消息接入接口 | 暂缓 | 需先验证接入方式、客户映射、权限和消息保留策略 |
| 订单异常/利润解释/经营记忆接口 | 后续路线 | 依赖事件日志、毛利权限和人工确认规则 |

### Agent 纸单订单草稿

| Method | Path | 鉴权 / scope | 说明 |
|--------|------|--------------|------|
| GET | `/api/agent/catalog/skus?keyword=...&limit=...` | `X-Agent-Key` / `agent:catalog:read` | 返回 SKU 候选与系统参考价，不返回成本价 |
| POST | `/api/agent/order-drafts/source-files` | `X-Agent-Key` / `agent:orders:write` | multipart 上传纸单原图，`businessType=order_draft` |
| POST | `/api/agent/order-drafts/batch` | `X-Agent-Key` / `agent:orders:write` | 批量创建草稿；按租户 + externalRefNo 幂等，每单返回 CREATED、CREATED_WITH_WARNINGS、DUPLICATE 或 ERROR |
| GET | `/api/order-drafts` | JWT / `menu:order` | 草稿分页列表 |
| GET | `/api/order-drafts/{id}` | JWT / `menu:order` | 草稿详情、纸单原值、警告和明细 |
| PUT | `/api/order-drafts/{id}` | JWT / `menu:order` | 保存人工修改，未匹配 SKU 可继续保留 |
| POST | `/api/order-drafts/{id}/confirm` | JWT / `menu:order` | 人工确认并幂等创建正式订单 |

约束：`salePrice`、`quantity`、`paperAmount`、`paperTotalAmount` 和 `deposit` 来自纸单识别或人工修正；`systemReferencePrice` 仅用于对照，不能覆盖纸单售价。客户无法匹配时使用“散客”。草稿确认前不进入正式订单、库存、财务和经营统计。

SKU 候选补充规则：候选返回 `skuType` 和 `placeholder`。只按款号查询多规格商品时，`PLACEHOLDER` 以 `matchScore=1.00` 优先返回；请求包含 `colorName` 或 `sizeCode` 时不返回占位 SKU。单真实 SKU 商品直接返回该 SKU。英文 SKU 编码是接口稳定标识，前端应将 `DEFAULT/NA-NA` 显示为“无规格商品（实际 SKU）”，将 `PLACEHOLDER/UNSPEC-UNSPEC` 显示为“整款录入（颜色/尺码未指定）”。`GET /api/agent/analytics/sku-mix` 的款号总量包含占位销量，真实 `skus/colors/sizes` 排名排除占位量，并通过 `unspecified`、`historicalNoVariant`、`variantCoverageRate`、`variantDataQuality` 分别描述当前整款录入量、商品升级规格前的历史无规格量及规格覆盖质量。

---

## 六、商品接口

### 6.1 商品列表（分页）

```
GET /api/products
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| current | long | 否 | 当前页，默认 1 |
| size | long | 否 | 每页条数，默认 20 |
| keyword | string | 否 | 商品名称/编码（模糊搜索） |
| categoryId | long | 否 | 分类ID |
| status | int | 否 | 状态：1启用 0禁用 |

**请求示例**：
```
GET /api/products?current=1&size=20&categoryId=1&status=1
```

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "productCode": "P001",
        "name": "经典T恤",
        "categoryId": 1,
        "categoryName": "上衣",
        "unit": "件",
        "price": 99.00,
        "status": 1,
        "colors": [
          {"id": 1, "colorCode": "BLACK", "colorName": "黑色"}
        ],
        "sizes": [
          {"id": 3, "sizeCode": "M", "sort": 3}
        ],
        "createTime": "2026-03-21T10:00:00"
      }
    ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

---

### 6.2 商品详情

```
GET /api/products/{id}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 商品 ID |

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "productCode": "P001",
    "name": "经典T恤",
    "categoryId": 1,
    "categoryName": "上衣",
    "unit": "件",
    "description": "经典款纯棉T恤",
    "imageUrl": "101",
    "price": 99.00,
    "status": 1,
    "colors": [...],
    "sizes": [...],
    "skus": [
      {
        "id": 1,
        "skuCode": "P001-BLACK-M",
        "colorId": 1,
        "colorName": "黑色",
        "sizeId": 3,
        "sizeName": "M",
        "price": 99.00,
        "costPrice": 50.00,
        "barCode": null,
        "status": 1
      }
    ],
    "createTime": "2026-03-21T10:00:00",
    "updateTime": "2026-03-21T10:00:00"
  }
}
```

---

### 6.3 创建商品

```
POST /api/products
```

**请求体**：
```json
{
  "productCode": "P001",
  "name": "经典T恤",
  "categoryId": 1,
  "unit": "件",
  "description": "经典款纯棉T恤",
  "imageUrl": "101",
  "price": 99.00,
  "status": 1,
  "colorIds": [1, 2, 3],
  "sizeIds": [3, 4, 5]
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| productCode | string | 是 | 商品编码 |
| name | string | 是 | 商品名称 |
| categoryId | long | 否 | 分类ID |
| unit | string | 否 | 单位，默认"件" |
| description | string | 否 | 描述 |
| imageUrl | string | 否 | 商品主图 fileId，历史数据可为URL |
| price | decimal | 否 | 单价 |
| status | int | 否 | 状态，默认1启用 |
| colorIds | long[] | 否 | 颜色ID列表 |
| sizeIds | long[] | 否 | 尺码ID列表 |

**成功响应**：
```json
{
  "code": 200,
  "data": 1
}
```

---

### 6.4 更新商品

```
PUT /api/products
```

**请求体**：
```json
{
  "id": 1,
  "name": "经典T恤-升级版",
  "price": 129.00,
  "colorIds": [1, 2, 3, 4],
  "sizeIds": [3, 4, 5, 6]
}
```

**成功响应**：
```json
{
  "code": 200,
  "message": "success"
}
```

---

### 6.5 删除商品

```
DELETE /api/products/{id}
```

**成功响应**：
```json
{
  "code": 200,
  "message": "success"
}
```

---

### 6.6 获取所有颜色

```
GET /api/products/colors
```

**成功响应**：
```json
{
  "code": 200,
  "data": [
    {"id": 1, "colorCode": "BLACK", "colorName": "黑色"},
    {"id": 2, "colorCode": "WHITE", "colorName": "白色"},
    {"id": 3, "colorCode": "GRAY", "colorName": "灰色"}
  ]
}
```

---

### 6.7 获取所有尺码

```
GET /api/products/sizes
```

**成功响应**：
```json
{
  "code": 200,
  "data": [
    {"id": 1, "sizeCode": "XS", "sort": 1},
    {"id": 2, "sizeCode": "S", "sort": 2},
    {"id": 3, "sizeCode": "M", "sort": 3},
    {"id": 4, "sizeCode": "L", "sort": 4},
    {"id": 5, "sizeCode": "XL", "sort": 5},
    {"id": 6, "sizeCode": "XXL", "sort": 6}
  ]
}
```

---

## 七、用户接口

### 7.1 获取当前用户信息

```
GET /api/auth/current-user
```

```
GET /api/auth/current-user
```

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "username": "admin",
    "nickname": "管理员",
    "tenantId": 1,
    "tenantName": "租户1",
    "roles": ["ADMIN"]
  }
}
```

---

### 7.2 用户列表（分页）

```
GET /api/system/users
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| current | long | 否 | 当前页，默认 1 |
| size | long | 否 | 每页条数，默认 20 |
| username | string | 否 | 用户名（模糊搜索） |
| nickname | string | 否 | 昵称（模糊搜索） |
| phone | string | 否 | 手机号（精确搜索） |
| status | int | 否 | 状态：1启用 0禁用 |

**请求示例**：
```
GET /api/system/users?current=1&size=20&username=admin&status=1
```

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "username": "admin",
        "nickname": "管理员",
        "email": "admin@example.com",
        "phone": "13800138000",
        "avatar": "https://...",
        "status": 1,
        "roles": [
          {
            "id": 1,
            "roleName": "管理员",
            "roleCode": "ADMIN"
          }
        ],
        "createTime": "2026-03-21T10:00:00",
        "updateTime": "2026-03-21T10:00:00"
      }
    ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

---

### 7.3 用户详情

```
GET /api/system/users/{id}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 用户 ID |

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "username": "admin",
    "nickname": "管理员",
    "email": "admin@example.com",
    "phone": "13800138000",
    "avatar": "https://...",
    "status": 1,
    "roles": [...],
    "createTime": "2026-03-21T10:00:00",
    "updateTime": "2026-03-21T10:00:00"
  }
}
```

---

### 7.4 创建用户

```
POST /api/system/users
```

**请求头**：
```
Authorization: Bearer {token}
```

**权限要求**：需具有 `user:create` 权限

**请求体**：
```json
{
  "username": "newuser",
  "password": "123456",
  "nickname": "新用户",
  "email": "newuser@example.com",
  "phone": "13900139000",
  "avatar": "https://...",
  "status": 1,
  "roleIds": [1, 2]
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名（3-20位） |
| password | string | 是 | 密码（6-20位） |
| nickname | string | 否 | 昵称（最多30位） |
| email | string | 否 | 邮箱 |
| phone | string | 否 | 手机号（最多11位） |
| avatar | string | 否 | 头像URL |
| status | int | 否 | 状态，默认1启用 |
| roleIds | long[] | 否 | 角色ID数组 |

**成功响应**：
```json
{
  "code": 200,
  "data": 1
}
```

**失败响应**（用户名已存在）：
```json
{
  "code": 500,
  "message": "用户名已存在"
}
```

---

### 7.5 更新用户

```
PUT /api/system/users
```

**请求头**：
```
Authorization: Bearer {token}
```

**权限要求**：需具有 `user:update` 权限

**请求体**：
```json
{
  "id": 1,
  "nickname": "新昵称",
  "email": "newemail@example.com",
  "phone": "13900139000",
  "avatar": "https://...",
  "status": 1,
  "roleIds": [1, 2]
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 用户ID |
| nickname | string | 否 | 昵称 |
| email | string | 否 | 邮箱 |
| phone | string | 否 | 手机号 |
| avatar | string | 否 | 头像URL |
| status | int | 否 | 状态 |
| roleIds | long[] | 否 | 角色ID数组 |

**成功响应**：
```json
{
  "code": 200,
  "message": "success"
}
```

---

### 7.6 删除用户

```
DELETE /api/system/users/{id}
```

**请求头**：
```
Authorization: Bearer {token}
```

**权限要求**：需具有 `user:delete` 权限

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 用户 ID |

**成功响应**：
```json
{
  "code": 200,
  "message": "success"
}
```

**失败响应**（删除超级管理员）：
```json
{
  "code": 500,
  "message": "不能删除超级管理员"
}
```

---

### 7.7 重置密码

```
PUT /api/system/users/{id}/password
```

**请求头**：
```
Authorization: Bearer {token}
```

**权限要求**：需具有 `user:password:reset` 权限

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 用户 ID |

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| newPassword | string | 是 | 新密码 |

**请求示例**：
```
PUT /api/system/users/1/password?newPassword=654321
```

**成功响应**：
```json
{
  "code": 200,
  "message": "success"
}
```

---

## 八、数据字典

### 8.1 订单状态

| 值 | 名称 | 说明 |
|----|------|------|
| 0 | 创建 | 新建订单，待付款 |
| 1 | 已付款 | 已确认收款，库存已预留 |
| 2 | 已发货 | 已确认发货，库存已出库 |
| 3 | 已完成 | 订单完成 |
| 4 | 已取消 | 订单取消，库存预留已释放 |
| 5 | 退货中 | 退货处理中 |
| 6 | 已退货 | 退货完成 |

> ⚠️ 注意：详细状态设计见 `06-ORDER_INVENTORY_DESIGN.md`，未来将增加配货中/待发货状态。

### 8.2 支付状态

| 值 | 名称 | 说明 |
|----|------|------|
| 0 | 未付款 | 客户未付款 |
| 1 | 已付定金 | 客户已付部分定金 |
| 2 | 已付全款 | 客户已付清全款 |

### 8.3 库存变动类型

| 值 | 名称 | 说明 |
|----|------|------|
| PURCHASE_IN | 采购入库 | 供应商入库 |
| SALE_OUT | 销售出库 | 订单出库 |
| ADJUSTMENT | 调整 | 盘盈盘亏 |
| SALE_CANCEL | 销售取消 | 订单取消释放预留 |
| RETURN_IN | 退货入库 | 退货入库 |
| GLOBAL_RESERVE | 跨仓总量预留 | 付款确认时锁定跨仓总量 |
| GLOBAL_RELEASE | 跨仓总量释放 | 取消订单时释放跨仓预留 |
| OTHER_OUT | 其他出库 | 非销售出库 |

### 8.4 预警状态

| 值 | 名称 |
|----|------|
| normal | 正常 |
| warning | 预警 |
| danger | 危险 |

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
  "username": "admin",
  "password": "123456"
}
```

**成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 1800
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
  "images": "http://xxx.com/1.jpg,http://xxx.com/2.jpg"
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
GET /api/dashboard/stats
```

**成功响应**：
```json
{
  "code": 200,
  "data": {
    "todayOrders": 25,
    "todaySales": 15800.00,
    "pendingOrders": 8,
    "lowStockAlerts": 3
  }
}
```

---

### 5.2 订单趋势

```
GET /api/dashboard/trend
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| days | int | 否 | 天数，默认 7 |

**成功响应**：
```json
{
  "code": 200,
  "data": [
    {
      "date": "2026-03-15",
      "orders": 20,
      "sales": 15000.00
    },
    {
      "date": "2026-03-16",
      "orders": 25,
      "sales": 18000.00
    }
  ]
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
    "imageUrl": "https://...",
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
  "imageUrl": "https://...",
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
| imageUrl | string | 否 | 图片URL |
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

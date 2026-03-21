# 产品需求文档（PRD）

> **本文档是 AI 开发的唯一依据。**
> 所有功能开发必须严格按本文档执行。
> 需求变更必须先更新本文档，再执行开发。
> 新 AI 必须先阅读本文档再开始开发。

---

## 一、项目概述

### 1.1 项目背景

BladeProject 是对原有 Blade 项目的重大技术升级，采用 **Monorepo 结构**，包含：
- **blade-backend**：Spring Boot 3 后端 API
- **blade-mobile**：Vue3 PWA 移动端（员工操作）
- **blade-admin**：Vue3 PC 端后台管理系统（管理后台）
- **packages/**：共享代码（API 类型定义）

### 1.2 技术栈

| 层级 | 技术 |
|------|------|
| 移动端 | Vue3 + Vite + TypeScript + PWA + Vuetify3 |
| PC 管理端 | Vue3 + Vite + TypeScript + Element Plus |
| 后端 | Spring Boot 3.2 + Spring Security + MyBatis-Plus |
| 数据库 | MySQL 8 + Redis 7 |
| AI 工具 | Claude Code（主力） |

### 1.3 前端项目定位

| 项目 | 定位 | 使用场景 | 技术栈 |
|------|------|----------|--------|
| blade-mobile | 移动端 PWA | 员工移动操作：接单、扫码出入库、订单查询 | Vue3 + Vuetify3 + PWA |
| blade-admin | PC 管理端 | 管理员：订单管理、库存管理、商品管理、客户管理、报表 | Vue3 + Element Plus |

**设计原则**：
- 两个前端项目独立开发，通过共享 `packages/` 中的 API 类型保持一致性
- blade-mobile 专注移动端体验（触屏、扫码、简洁）
- blade-admin 专注 PC 端体验（表格、批量操作、复杂筛选）

---

## 二、模块优先级

| 优先级 | 模块 | 说明 |
|--------|------|------|
| P0 | 订单系统 | 最核心，与库存联动 |
| P0 | 商品模块 | 颜色/尺码/SKU管理，订单和库存共用 |
| P0 | 库存系统 | 完整出入库记录，与订单联动 |
| P1 | 客户系统 | 客户档案，后续接入 AI |
| P2 | 看板系统 | 统计展示 |
| P3 | 微信服务 | 通知推送（后续接入） |

---

## 三、业务模式

- **批发为主 + 偶尔零售**
- **标准尺码**，无需量体定制
- **SKU 级别管理**，库存精确到颜色+尺码

---

## 四、商品模块（P0）

### 4.1 商品表（product）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| name | varchar | 商品名称 |
| category_id | bigint | 分类ID |
| unit | varchar | 单位（如：件、套） |
| description | text | 描述 |
| image_url | varchar | 商品图片 |
| status | tinyint | 状态：1启用 0禁用 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

### 4.2 颜色表（product_color）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| color_code | varchar | 颜色编码 |
| color_name | varchar | 颜色名称（如：黑色、白色、灰色） |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |

### 4.3 尺码表（product_size）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| size_code | varchar | 尺码编码（如：XS、S、M、L、XL、XXL） |
| sort | int | 排序 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |

### 4.4 SKU表（product_sku）

SKU = 商品 + 颜色 + 尺码的组合

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| product_id | bigint | 商品ID |
| color_id | bigint | 颜色ID |
| size_id | bigint | 尺码ID |
| sku_code | varchar | SKU编码（系统自动生成） |
| price | decimal | 单价 |
| cost_price | decimal | 成本价 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |

**SKU 编码规则**：`{商品编码}-{颜色编码}-{尺码编码}`
例：Jacket-BLACK-M

### 4.5 商品-颜色关联表（product_color_rel）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| product_id | bigint | 商品ID |
| color_id | bigint | 颜色ID |

### 4.6 商品-尺码关联表（product_size_rel）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| product_id | bigint | 商品ID |
| size_id | bigint | 尺码ID |

---

## 五、库存模块（P0）

### 5.1 仓库表（warehouse）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| warehouse_name | varchar | 仓库名称 |
| address | varchar | 地址 |
| contact | varchar | 联系人 |
| phone | varchar | 电话 |
| status | tinyint | 状态：1启用 0禁用 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |
| create_time | datetime | 创建时间 |

### 5.2 库存表（inventory）

实时库存，按 SKU + 仓库维度存储

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| sku_id | bigint | SKU ID |
| warehouse_id | bigint | 仓库ID |
| quantity | int | 当前库存数量 |
| reserved_qty | int | 预留数量（订单占用） |
| available_qty | int | 可用数量（计算得出：quantity - reserved_qty） |
| alert_threshold | int | 预警阈值 |
| tenant_id | bigint | 租户ID |
| update_time | datetime | 更新时间 |

### 5.3 库存变动记录表（inventory_log）

**所有库存变动都必须记录**，不可删除

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| sku_id | bigint | SKU ID |
| warehouse_id | bigint | 仓库ID |
| change_type | varchar | 变动类型 |
| change_qty | int | 变动数量（正数=入库，负数=出库） |
| before_qty | int | 变动前库存 |
| after_qty | int | 变动后库存 |
| order_id | bigint | 关联订单ID（可空） |
| reference_no | varchar | 关联单据号 |
| supplier_id | bigint | 供应商ID（预留，后续供应商管理用） |
| supplier_name | varchar | 供应商名称（冗余存储） |
| operator_id | bigint | 操作人ID |
| remark | varchar | 备注 |
| images | varchar | 图片URLs，逗号分隔（入库凭证等） |
| tenant_id | bigint | 租户ID |
| create_time | datetime | 操作时间 |

### 5.4 库存变动类型

| 类型 | 说明 | 关联 |
|------|------|------|
| PURCHASE_IN | 采购入库 | purchase_order |
| SALE_OUT | 订单出库 | order |
| SALE_CANCEL | 订单取消回补 | order |
| SALE_RETURN | 退货入库 | order |
| TRANSFER_IN | 调拨入库 | transfer |
| TRANSFER_OUT | 调拨出库 | transfer |
| ADJUST | 直接调整 | - |
| CHECK | 盘点调整 | inventory_check |
| INITIAL | 期初录入 | - |

### 5.5 库存操作规则

1. **订单创建**：不扣库存，只记录意向
2. **订单付款**：扣除可用库存，增加预留
3. **订单取消**：释放预留，回补可用库存
4. **订单发货**：预留转正式出库
5. **直接调整**：记录调整原因，可正可负

---

## 六、订单模块（P0）

### 6.1 订单表（sale_order）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| order_no | varchar | 订单编号 |
| customer_id | bigint | 客户ID |
| customer_name | varchar | 客户名称（冗余） |
| customer_phone | varchar | 客户电话 |
| customer_address | varchar | 客户地址 |
| total_amount | decimal | 订单总金额 |
| paid_amount | decimal | 已支付金额 |
| status | tinyint | 订单状态 |
| warehouse_id | bigint | 发货仓库 |
| remark | varchar | 备注 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |
| pay_time | datetime | 支付时间 |
| confirm_time | datetime | 确认时间 |
| deliver_time | datetime | 发货时间 |
| complete_time | datetime | 完成时间 |

### 6.2 订单明细表（sale_order_item）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| order_id | bigint | 订单ID |
| sku_id | bigint | SKU ID |
| product_name | varchar | 商品名称（冗余） |
| sku_code | varchar | SKU编码（冗余） |
| color_name | varchar | 颜色（冗余） |
| size_name | varchar | 尺码（冗余） |
| price | decimal | 单价 |
| quantity | int | 数量 |
| subtotal | decimal | 小计金额 |
| tenant_id | bigint | 租户ID |
| create_time | datetime | 创建时间 |

### 6.3 订单状态流转

```
待处理(0) → 已确认(1) → 货中(2) → 已完成(3)
                ↓
            已取消(4)
                ↓
            退货中(5) → 已退货(6)
```

| 状态码 | 名称 | 说明 |
|--------|------|------|
| 0 | 待处理 | 新建订单，待确认 |
| 1 | 已确认 | 已确认，待安排发货 |
| 2 | 货中 | 已发货/处理中 |
| 3 | 已完成 | 订单完成 |
| 4 | 已取消 | 订单取消 |
| 5 | 退货中 | 退货处理中 |
| 6 | 已退货 | 已完成退货 |

**状态流转规则**：
- 待处理 → 已确认：手动确认
- 已确认 → 货中：发货时自动变
- 已确认 → 已取消：手动取消
- 货中 → 已完成：手动确认收货
- 货中 → 退货中：申请退货
- 退货中 → 已退货：退货完成

### 6.4 订单与库存联动

**付款后扣库存流程**：
```
1. 订单创建（status=0）
   → 不扣库存，不预占

2. 订单确认（status=1）
   → 不扣库存，不预占

3. 客户付款
   → 预占库存（reserved_qty + quantity）
   → 变更订单 paid_amount
   → 记录库存变动日志（类型：订单预占）

4. 订单发货（status=2）
   → 预占转正式出库（reserved_qty - quantity, quantity - quantity）
   → 记录库存变动日志（类型：订单出库）

5. 订单取消（status=4）
   → 如果有预占：释放预占（reserved_qty - quantity）
   → 记录库存变动日志（类型：订单取消）

6. 退货入库
   → 增加可用库存（quantity + quantity）
   → 记录库存变动日志（类型：退货入库）
```

### 6.5 订单 API

```
GET    /api/orders              # 订单列表（分页+筛选）
GET    /api/orders/{id}        # 订单详情（含明细）
POST   /api/orders              # 创建订单
PUT    /api/orders/{id}        # 更新订单（基本信息）
PUT    /api/orders/{id}/status  # 更新状态
PUT    /api/orders/{id}/pay    # 确认付款
DELETE /api/orders/{id}        # 删除订单（仅待处理状态可删除）
```

---

## 七、客户模块（P1）

> 暂缓开发，后续补充

### 7.1 客户表（client）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| client_name | varchar | 客户名称 |
| contact | varchar | 联系人 |
| phone | varchar | 电话 |
| address | varchar | 地址 |
| level | tinyint | 客户等级 |
| remark | varchar | 备注 |
| last_order_time | datetime | 最后下单时间 |
| total_amount | decimal | 累计消费 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

---

## 八、微信服务（P3）

> 后续接入，已有公众号

### 8.1 功能范围

- 订单状态变更通知
- 库存预警通知
- 其他系统通知

### 8.2 实现方式

- 独立微信服务模块（wx-service）
- 对外提供 HTTP 接口
- 其他服务调用发送模板消息

---

## 九、多租户设计

### 9.1 租户隔离

- 所有业务表通过 `tenant_id` 字段隔离
- 多租户通过 MyBatis-Plus TenantLineInnerInterceptor 自动处理
- **禁止手动拼接 tenant_id**

### 9.2 超级管理员

- tenant_id = 0 为超级管理员
- 可访问所有租户数据
- 一般不启用

---

## 十、API 通用规范

### 10.1 请求格式

所有请求必须带 Authorization 头：
```
Authorization: Bearer {jwt_token}
```

### 10.2 响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1742572800
}
```

### 10.3 分页响应

```json
{
  "code": 200,
  "data": {
    "records": [...],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

### 10.4 错误码

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器错误 |

---

## 十一、非功能性需求

### 11.1 性能

- 列表接口响应时间 < 500ms
- PWA 首屏加载时间 < 3s

### 11.2 兼容性

- iOS Safari 14+
- Android Chrome 90+
- iPad Safari 14+

### 11.3 PWA

- 支持添加到主屏幕
- 支持离线缓存
- 支持推送通知

---

## 十二、版本历史

| 版本 | 日期 | 修改内容 |
|------|------|---------|
| v1.0 | 2026-03-21 | 初始版本 |
| v1.1 | 2026-03-21 | 新增商品模块（颜色/尺码/SKU）、库存模块（完整变动记录）、订单与库存联动规则 |
| v1.2 | 2026-03-22 | 新增 blade-admin PC 管理端项目规划，采用 Monorepo 结构 |

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
| 移动端 | Vue3 + Vite + TypeScript + PWA + Vuetify 4 |
| PC 管理端 | Vue3 + Vite + TypeScript + Element Plus |
| 后端 | Spring Boot 3.2 + Spring Security + MyBatis-Plus |
| 数据库 | MySQL 8 + Redis 7 |
| AI 工具 | Codex（当前主力） |

### 1.3 前端项目定位

| 项目 | 定位 | 使用场景 | 技术栈 |
|------|------|----------|--------|
| blade-mobile | 移动端 PWA | 员工移动操作：接单、扫码出入库、订单查询 | Vue3 + Vuetify 4 + PWA |
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

> 完整数据库字段、索引和历史兼容字段以 [architecture/DATABASE.md](./architecture/DATABASE.md) 和 Flyway 迁移脚本累计结果为准。本节只保留业务层核心字段。

### 4.1 商品表（product）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| product_code | varchar | 商品编码（唯一标识） |
| name | varchar | 商品名称 |
| category_id | bigint | 分类ID |
| supplier_id | bigint | 供应商ID（关联supplier表） |
| unit | varchar | 单位（如：件、套） |
| cost_price | decimal | 进货价（成本参考） |
| wholesale_price | decimal | 批发价 |
| weight | decimal | 重量（用于物流/运费计算） |
| description | text | 描述 |
| image_url | varchar | 商品主图 fileId 字符串（历史数据可为URL，详见 [09-FILE_STORAGE_DESIGN.md](./09-FILE_STORAGE_DESIGN.md)） |
| remark | varchar | 备注 |
| status | tinyint | 状态：1启用 0禁用 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

### 4.2 供应商表（supplier）

> ⚠️ P1优先级，暂不开发管理页面，先预留字段关联

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| supplier_code | varchar | 供应商编码 |
| supplier_name | varchar | 供应商名称 |
| contact | varchar | 联系人 |
| phone | varchar | 电话 |
| address | varchar | 地址 |
| status | tinyint | 状态：1启用 0禁用 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

### 4.3 颜色表（product_color）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| color_code | varchar | 颜色编码 |
| color_name | varchar | 颜色名称（如：黑色、白色、灰色） |
| status | tinyint | 状态：1启用 0禁用 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |

### 4.4 尺码表（product_size）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| size_code | varchar | 尺码编码（如：XS、S、M、L、XL、XXL） |
| sort | int | 排序 |
| status | tinyint | 状态：1启用 0禁用 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |

### 4.5 SKU表（product_sku）

SKU = 商品 + 颜色 + 尺码的组合

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| product_id | bigint | 商品ID |
| color_id | bigint | 颜色ID |
| size_id | bigint | 尺码ID |
| sku_code | varchar | SKU编码（系统自动生成） |
| price | decimal | 单价（可覆盖商品级批发价） |
| cost_price | decimal | 成本价（可覆盖商品级进货价） |
| bar_code | varchar | 条形码（预留） |
| status | tinyint | 状态：1启用 0禁用 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |
| create_time | datetime | 创建时间 |

**SKU 编码规则**：`{商品编码}-{颜色编码}-{尺码编码}`
例：Jacket-BLACK-M

### 4.6 商品-颜色关联表（product_color_rel）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| product_id | bigint | 商品ID |
| color_id | bigint | 颜色ID |

### 4.7 商品-尺码关联表（product_size_rel）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| product_id | bigint | 商品ID |
| size_id | bigint | 尺码ID |

---

## 五、库存模块（P0）

> 完整数据库字段、索引和可空性以 [architecture/DATABASE.md](./architecture/DATABASE.md) 和 Flyway 迁移脚本累计结果为准。本节只保留业务层核心字段。

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
| reserved_qty | int | 单仓预留数量 |
| global_reserved_qty | int | 跨仓总量预留数量 |
| available_qty | int | 可用数量（计算得出：quantity - reserved_qty - global_reserved_qty） |
| alert_threshold | int | 预警阈值 |
| version | int | 乐观锁版本号 |
| tenant_id | bigint | 租户ID |
| update_time | datetime | 更新时间 |

### 5.3 库存变动记录表（inventory_log）

**所有库存变动都必须记录**，不可删除

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| sku_id | bigint | SKU ID |
| warehouse_id | bigint | 仓库ID（跨仓预留类日志可为空） |
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
| images | varchar | 入库凭证 fileId JSON数组字符串（详见 [09-FILE_STORAGE_DESIGN.md](./09-FILE_STORAGE_DESIGN.md)） |
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
2. **订单付款**：执行跨仓总量预留，增加 `global_reserved_qty`
3. **订单取消**：释放未消耗的跨仓总量预留
4. **订单发货**：按配货计划出库，扣减 `quantity` 和 `global_reserved_qty`
5. **直接调整**：记录调整原因，可正可负

**双轨预留规则**：
- `reserved_qty`：单仓预占场景使用
- `global_reserved_qty`：跨仓总量预留和配货计划发货场景使用

---

## 六、订单模块（P0）

> 完整数据库字段、索引和历史兼容字段以 [architecture/DATABASE.md](./architecture/DATABASE.md) 和 Flyway 迁移脚本累计结果为准。本节只保留业务层核心字段。

### 6.1 订单表（sale_order）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| order_no | varchar | 订单编号 |
| order_date | date | 订单日期（纸质单据日期） |
| source_doc_no | varchar | 纸质单据号/外部单号 |
| source_shop | varchar | 订单来源档口/店铺，不等同于仓库 |
| order_type | varchar | 订单类型：SPOT现货/PREORDER订货 |
| customer_id | bigint | 客户ID |
| customer_name | varchar | 客户名称（冗余） |
| customer_phone | varchar | 客户电话 |
| customer_address | varchar | 客户地址 |
| total_amount | decimal | 订单总金额 |
| original_amount | decimal | 原始订单金额（调整前） |
| refund_amount | decimal | 已退款金额 |
| paid_amount | decimal | 已支付金额 |
| **payment_status** | tinyint | **支付状态：0未付款 1已付定金 2已付全款** |
| **deposit_amount** | decimal | **定金金额** |
| freight_amount | decimal | 客户运费收入 |
| freight_cost | decimal | 实际运费成本 |
| total_cost_amount | decimal | 订单总成本（商品成本 + 运费成本） |
| gross_profit | decimal | 订单毛利 |
| adjustment_status | varchar | 调整状态：NONE/PENDING/APPROVED/COMPLETED |
| **need_delivery** | tinyint | **是否需要送货：0否 1是** |
| **delivery_address** | varchar | **送货地址** |
| **is_delivered** | tinyint | **是否已送货：0否 1是** |
| **delivered_at** | datetime | **送货时间** |
| status | tinyint | 订单状态 |
| warehouse_id | bigint | 发货仓库 |
| salesman_id | bigint | 开单销售人员ID |
| salesman_name | varchar | 开单销售人员姓名 |
| images | varchar | 订单图片 fileId JSON数组字符串（详见 [09-FILE_STORAGE_DESIGN.md](./09-FILE_STORAGE_DESIGN.md)） |
| remark | varchar | 备注 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记（0未删，1已删） |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |
| pay_time | datetime | 支付时间 |
| deliver_time | datetime | 发货时间 |
| complete_time | datetime | 完成时间 |

### 6.2 订单明细表（sale_order_item）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| order_id | bigint | 订单ID |
| product_id | bigint | 商品ID（历史兼容字段） |
| sku_id | bigint | SKU ID |
| warehouse_id | bigint | 仓库ID（支持多仓分配） |
| product_name | varchar | 商品名称（冗余快照） |
| sku_code | varchar | SKU编码（冗余快照） |
| color_name | varchar | 颜色（冗余快照） |
| size_name | varchar | 尺码（冗余快照） |
| price | decimal | 下单时的单价 |
| cost_price | decimal | 下单成本价快照 |
| quantity | int | 数量 |
| planned_quantity | int | 计划数量（原订单数量） |
| allocated_quantity | int | 配货数量（调整后数量） |
| out_quantity | int | 已出库数量 |
| adjustment_remark | varchar | 调整说明 |
| subtotal | decimal | 小计金额 |
| cost_amount | decimal | 成本金额快照 |
| gross_profit | decimal | 明细毛利快照 |
| tenant_id | bigint | 租户ID |
| create_time | datetime | 创建时间 |

### 6.2.1 PC 快速录单与财务快照

- 快速录单用于把纸质订单按单张连续录入系统，最终仍保存到 `sale_order` / `sale_order_item` 标准订单表。
- 快速录单页面按 PC 后台高频录入场景布局：单据/客户信息在上方，商品明细居中，结算与配送和金额汇总在商品明细下方左右并列展示，减少录入时上下滚动和视线跳转。
- “来源档口/店铺”用于记录订单来自哪个档口商店，是订单来源字段；不绑定库存仓库，也不参与库存预留、出库逻辑。
- 订单类型仅作标记与筛选：`SPOT` 现货订单，`PREORDER` 订货订单；第一版不改变库存预留和发货流程。
- 运费分两项：`freight_amount` 为向客户收取的运费，计入订单应收；`freight_cost` 为实际运费成本，计入订单成本。
- 利润公式：`gross_profit = 商品销售小计 - 商品成本金额 + 运费收入 - 运费成本`。
- 成本价、成本金额和毛利在订单保存时固化为历史快照，后续商品成本变化不影响历史订单。
- 后续追加收款只更新 `paid_amount` 和 `payment_status`，不自动改变订单处理状态。

### 6.3 订单状态流转（处理进度）

> 当前实现已包含配货计划相关中间状态。完整设计可继续参考 `06-ORDER_INVENTORY_DESIGN.md`。

**当前实现状态模型（9 状态）**：

```text
创建(0) → 已付款(1) → 配货中待确认(2) → 待发货(3) → 已发货(4) → 已完成(5)
      └──────────────────────────────→ 已取消(6)
已发货(4) → 退货中(7) → 已退货(8)
```

| 状态码 | 名称 | 库存操作 |
|--------|------|---------|
| 0 | 创建 | 无 |
| 1 | 已付款 | 跨仓总量预留 |
| 2 | 配货中待确认 | 无，等待确认调整方案 |
| 3 | 待发货 | 无，已完成配货确认 |
| 4 | 已发货 | 配货计划出库完成 |
| 5 | 已完成 | 无 |
| 6 | 已取消 | 释放未消耗的跨仓总量预留 |
| 7 | 退货中 | 无 |
| 8 | 已退货 | 退货入库 |

**状态流转规则**：
- 创建(0) → 已付款(1)：确认收款时
- 已付款(1) → 配货中待确认(2)：创建配货计划后
- 配货中待确认(2) → 待发货(3)：确认调整方案后
- 待发货(3) → 已发货(4)：仓库按配货计划发货
- 已发货(4) → 已完成(5)：订单完成
- 创建(0) / 已付款(1) / 配货中待确认(2) → 已取消(6)：人工取消
- 已发货(4) → 退货中(7) → 已退货(8)：退货流程

**与旧版差异**：原设计 `payment_status=2(已付全款)` 时自动设置 `status=1`，现已改为手动确认收款操作。

### 6.4 支付状态（收款状态）

与订单状态是两个独立维度：

| 支付状态码 | 名称 | 说明 |
|------------|------|------|
| 0 | 未付款 | 客户未付款或仅登记未收款 |
| 1 | 已付定金 | 客户交了部分定金，欠尾款 |
| 2 | 已付全款 | 客户已付清全款 |

**与订单状态的关系**：
- 订单状态描述"处理进度"，支付状态描述"收款情况"
- `payment_status=2(已付全款)` 时，**手动确认收款**后设置 `status=1(已付款)`
- `payment_status=0(未付款)` 的订单仍可取消，不涉及退款

**定金规则**：
- `payment_status=1` 时，`deposit_amount > 0` 且 `deposit_amount < total_amount`
- `payment_status=2` 时，`paid_amount = total_amount`，`deposit_amount = 0`

**确认收款操作**：
- 接口：`POST /api/orders/confirm-payment`
- 参数：`orderId`, `paidAmount`
- 效果：调用跨仓总量预留，订单状态 0→1

### 6.5 配送设置

| 字段 | 类型 | 说明 |
|------|------|------|
| need_delivery | tinyint | 是否需要送货：0否 1是 |
| delivery_address | varchar | 送货地址（need_delivery=1 时必填） |
| is_delivered | tinyint | 是否已送货：0否 1是 |
| delivered_at | datetime | 送货时间 |

**配送流程**：
1. 录单时勾选"需要送货"并填写送货地址
2. 送货完成后勾选"已送货"，记录送货时间
3. 与订单状态 `status` 无关，可独立操作

### 6.6 订单与库存联动

**跨仓总量预留流程**（当前实现）：

> 详细设计见 `06-ORDER_INVENTORY_DESIGN.md`

```
1. 订单创建（status=0）
   → 不绑定仓库（支持先下单、后配仓）
   → 不扣库存，不预占

2. 确认收款（status=0 → 1）
   → 调用跨仓总量预留（global_reserve）
   → **此处检查跨仓总量是否充足**
   → 锁定 SKU 跨仓总量（global_reserved_qty）
   → 变更订单 paid_amount、pay_time
   → 记录库存变动日志

3. 配货确认（status=1 → 2 → 3）
   → 创建配货计划，进入配货中待确认
   → 仓库与销售确认调整方案
   → 确认后进入待发货

4. 订单发货（status=3 → 4）
   → 按配货计划出库（outByPlan）
   → 扣减 quantity、global_reserved_qty
   → **此流程不扣减 reserved_qty**
   → 记录库存变动日志

5. 订单取消（status=6）
   → 调用跨仓总量释放（global_release）
   → 释放尚未消耗的 global_reserved_qty
   → 记录库存变动日志

6. 退货入库
   → 增加可用库存（quantity + quantity）
   → 记录库存变动日志
```

**关键设计**：
- **跨仓总量预留**：不按单仓库预留，直接按 SKU 总量锁定
- **配货灵活**：后续可按任意仓库配货出库
- **库存字段**：`quantity`(总量)、`reserved_qty`(单仓预留)、`global_reserved_qty`(跨仓预留)
- **双轨预留规则**：单仓预占使用 `reserved_qty`；跨仓总量预留和配货计划发货使用 `global_reserved_qty`

### 6.7 订单 API

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

## 八、看板系统（P2）

### 8.1 功能概述

看板系统提供销售数据统计和可视化，帮助管理者快速了解业务运营状况。

### 8.2 统计接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/dashboard/stats` | GET | 获取看板统计数据 |
| `/api/dashboard/trend` | GET | 获取订单趋势数据（按筛选周期） |
| `/api/dashboard/top-products` | GET | 获取热销商品排行（Top 5） |

**订单统计口径**：
- 日期按 `sale_order.order_date` 统计；旧数据没有订单日期时回退 `create_time`。
- 统计对象为已产生收款订单：`paid_amount > 0` 或 `payment_status in (1, 2)`。
- 销售额为应收净额：`max(total_amount - refund_amount, 0)`。
- 取消、退货中、已退货订单不直接排除，退款后按净额体现。

### 8.3 统计数据（DashboardStatsDTO）

| 字段 | 类型 | 说明 |
|------|------|------|
| periodOrders | Long | 当前筛选周期已产生收款订单数 |
| periodOrdersTrend | Long | 周期订单数环比（百分比） |
| periodSales | BigDecimal | 当前筛选周期应收净额 |
| periodSalesTrend | Long | 周期销售额环比（百分比） |
| periodGrossProfit | BigDecimal | 当前筛选周期毛利净额 |
| periodGrossProfitTrend | Long | 周期毛利环比（百分比） |
| periodSalesQuantity | Long | 当前筛选周期销量，按已产生收款订单明细数量汇总 |
| periodSalesQuantityTrend | Long | 周期销量环比（百分比） |
| totalProducts | Long | 商品总数 |
| pendingOrders | Long | 待处理订单数 |
| pendingOrdersTrend | Long | 待处理订单环比（百分比） |
| weekOrders | Long | 本周已产生收款订单数 |
| weekSales | BigDecimal | 本周应收净额 |
| weekGrossProfit | BigDecimal | 本周毛利净额 |
| avgOrderValue | BigDecimal | 当前筛选周期平均客单价（接口兼容保留，前端卡片不展示） |

### 8.4 订单趋势（OrderTrendDTO）

| 字段 | 类型 | 说明 |
|------|------|------|
| dates | List<String> | 日期列表（MM-DD格式，按筛选周期，最多365天） |
| orderCounts | List<Long> | 每日已产生收款订单数 |
| salesAmounts | List<BigDecimal> | 每日应收净额 |

### 8.5 热销商品（TopProductDTO）

| 字段 | 类型 | 说明 |
|------|------|------|
| productId | Long | 商品ID（可为null） |
| productName | String | 商品名称 |
| totalQuantity | Long | 总销量 |
| totalAmount | BigDecimal | 总销售额 |

### 8.6 前端页面

| 页面 | 路径 | 说明 |
|------|------|------|
| 仪表盘 | `/dashboard` | 数字卡片 + 图表 |

**图表类型**：
- 销售趋势：折线图（双Y轴：订单数 + 销售额）
- 热销商品：水平柱状图（Top 5）

### 8.7 技术实现

| 技术 | 说明 |
|------|------|
| ECharts | 图表库 |
| vue-echarts | Vue3 ECharts 封装 |
| MyBatis-Plus | 数据聚合查询 |

---

## 九、数据分析（P2）

### 9.1 功能概述

数据分析页面向经营决策，独立于首页仪表盘，第一版聚焦销售与商品分析。页面路径为 `/analytics`，菜单权限为 `menu:analytics`。

### 9.2 分析接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/analytics/summary` | GET | 获取周期经营汇总 |
| `/api/analytics/trend` | GET | 获取周期趋势 |
| `/api/analytics/product-ranking` | GET | 获取商品/SKU/颜色/尺码排行 |
| `/api/analytics/product-detail` | GET | 获取单商品维度拆解 |

**统计口径**：
- 日期按 `sale_order.order_date` 统计；旧数据没有订单日期时回退 `create_time`。
- 统计对象为已产生收款订单：`paid_amount > 0` 或 `payment_status in (1, 2)`。
- 汇总销售额为应收净额：`max(total_amount - refund_amount, 0)`。
- 汇总毛利为 `max(gross_profit - refund_amount, 0)`。
- 商品排行第一版不反摊订单级退款，按订单明细 `subtotal`、`quantity`、`gross_profit` 聚合。

### 9.3 权限

| 权限码 | 类型 | 说明 |
|--------|------|------|
| `menu:analytics` | 菜单 | 数据分析页入口 |
| `data:analytics:profit` | 字段 | 成本、毛利、毛利率数据权限 |

老板/系统管理员默认拥有毛利数据权限；销售员默认只有数据分析菜单权限，不返回也不展示毛利、成本、毛利率字段。

### 9.4 前端页面

| 页面 | 路径 | 说明 |
|------|------|------|
| 数据分析 | `/analytics` | 日期筛选 + 经营指标 + 趋势图 + 商品排行 + 商品详情抽屉 |

---

## 十、外部 AI Agent 对接（P2）

> 详细设计见 [10-AGENT_INTEGRATION_DESIGN.md](./10-AGENT_INTEGRATION_DESIGN.md)。
> 第一版目标是让外部 Agent 通过稳定 API 做款式趋势判断、客户跟进提醒、周期经营分析和搜索，不直接访问数据库，不直接执行订单/库存高风险写操作。

### 10.1 功能定位

Agent 是 BladeProject 的外部 API 消费者，不替代 PC 管理端和移动端。第一版支持：

| 场景 | 说明 |
|------|------|
| 款式趋势判断 | 判断哪些款持续向好、哪些款需观察、哪些款不建议继续做 |
| 客户跟进提醒 | 基于客户订单日期、复购和跟进规则输出需联系客户 |
| 周期经营分析 | 支持月度、季度、年度复盘数据与建议输入 |
| 颜色尺码结构 | 分析同款不同颜色、尺码、SKU 的热卖、缺货和积压差异 |
| 库存建议事实 | 提供积压、缺货影响、补货优先级和跨仓库存事实 |
| 客户风险 | 支持客户分层、流失风险和跟进优先级判断 |
| 统一搜索 | 跨客户、订单、商品和 SKU 查找业务对象 |
| 沟通上下文扩展 | 后续接入 WhatsApp 信息，结合客户沟通和系统订单数据分析 |

### 10.2 第一版接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/agent/analytics/style-trends` | GET | 多周期款式趋势与建议依据 |
| `/api/agent/analytics/sku-mix` | GET | 款式颜色/尺码/SKU 结构事实 |
| `/api/agent/tasks/follow-up` | GET | 需跟进客户清单与提醒依据 |
| `/api/agent/customers/risk` | GET | 客户流失风险与分层事实 |
| `/api/agent/inventory/recommendations` | GET | 库存积压、缺货和补货优先级事实 |
| `/api/agent/reports/periodic` | GET | 月度、季度、年度经营分析数据包 |
| `/api/agent/search` | GET | 客户、订单、商品/SKU 跨模块搜索 |

实现时优先复用 `/api/dashboard/*`、`/api/analytics/*` 和客户统计能力，统计口径不得与现有看板、数据分析页分叉。

### 10.3 安全与权限

1. Agent 使用独立凭证，不复用前端 JWT 登录态。
2. 每个 Agent 凭证必须绑定 `tenant_id` 和 scope，认证通过后进入当前多租户隔离链路。
3. 第一版 Agent API 默认只读，禁止创建/编辑订单、库存调整、收款确认等高风险动作。
4. 成本、毛利、毛利率需单独授权，默认不返回。
5. 对外开放 Agent 前必须复核客户数据接口认证边界、Agent API 调用审计和限流。

### 10.4 暂缓范围

- 后端自然语言问答 `/agent/query` 暂缓，先由外部 Agent 调用结构化工具接口。
- 泛化写动作 `/agent/action` 暂缓；后续只允许经过明确授权的窄范围动作。
- 增量变化接口 `/agent/changes` 依赖统一业务事件日志，第一版不纳入验收。
- WhatsApp 数据接入第一版不纳入实现验收；后续先验证合规接入方式、客户映射、权限和消息保留策略。
- 订单流程异常、利润解释、WhatsApp 反馈分析和经营记忆属于后续能力路线，按事件、权限和外部数据接入成熟度分阶段落地。

---

## 十一、微信服务（P3）

> 后续接入，已有公众号

### 10.1 功能范围

- 订单状态变更通知
- 库存预警通知
- 其他系统通知

### 10.2 实现方式

- 独立微信服务模块（wx-service）
- 对外提供 HTTP 接口
- 其他服务调用发送模板消息

---

## 十二、OCR 拍照录单（P2）

> 优先级 P2，等订单核心流程跑通后再重点开发。
> 目标：尽量减少手动输入，让入单流程尽可能简单。

### 9.1 核心目标

**拍照 → 自动识别 → 自动填表 → 一键确认**

理想情况：完全不用手动选，识别完直接提交。

### 9.2 字段识别难度分析

| 字段 | 识别难度 | 原因 | 预估准确率 |
|------|---------|------|-----------|
| 款号 | 🔴 最难 | 非标准缩写、无固定格式、手写潦草、AI 无法猜对应关系 | 40-60% |
| 客户名称 | 🔴 难 | 手写中文、字迹潦草、可能有别名/简称 | 60-70% |
| 日期 | 🟡 中等 | 格式多样（2024.1.15 / 2024年1月15日 / 24.1.15） | 85%+ |
| 单价 | 🟡 中等 | 手写数字为主，但有¥符号辅助定位 | 80%+ |
| 数量 | 🟢 较易 | 纯数字、结构清晰、在表格内 | 90%+ |
| 总金额 | 🟢 最易 | 经常预印或在固定位置、有"¥"符号辅助 | 90%+ |

**核心结论**：款号识别是最大难点，必须有兜底方案。

### 9.3 字段识别策略

| 字段 | 处理策略 | 原因 |
|------|---------|------|
| 数量 | ✅ 自动填入 | 识别准确率高 |
| 单价 | ✅ 自动填入 | 有¥符号定位，准确率高 |
| 总金额 | ✅ 自动填入 | 位置固定，准确率高 |
| 客户名称 | ✅ 自动填入 | 识别后自动填，可修改 |
| 日期 | ✅ 自动填入 | 数字为主，自动填 |
| **款号** | ⚠️ **手动选择** | 识别难，预设不填，用户从商品库选择 |

### 9.4 实施方案

#### 方案 A：半自动录单（推荐，P2 优先实现）

**核心思路**：款号识别不准确 → 预设不填；数量/单价/总金额识别准确 → 自动填入。

**流程**：
```
拍照 → OCR 识别 → 数量/单价/总金额/客户名/日期自动填入 → 用户只选款号 → 提交
```

**表单设计**：
```
┌─────────────────────────────────────────────────────────────┐
│  📷 拍照录单 - 单据识别结果                                   │
├─────────────────────────────────────────────────────────────┤
│  客户：张三（自动填入，可修改）  📅 2024-01-15              │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ # │ 款号（手动选择）     │ 颜色  │ 数量 │ 单价 │ 小计 │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │ 1 │ [从商品库选择 ▼]    │ 黑色  │  10  │ ¥120 │ ¥1200│  │
│  │ 2 │ [从商品库选择 ▼]    │ 白色  │   5  │ ¥135 │ ¥675  │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  总额：¥ 1875（自动计算）  已付：□定金 ¥500  □全款          │
│  送货：☑ 需要送货  地址：[________________]                    │
│                                                              │
│                    [ 确认提交订单 ]                            │
└─────────────────────────────────────────────────────────────┘
```

**工作量对比**：
| 方案 | 操作步骤 |
|------|---------|
| 原来（无 OCR） | 选款号 → 选颜色 → 填数量 → 填单价 → 选仓库 → 填客户 → ... |
| 方案 A（半自动） | 拍照 → 选款号 → 提交（减少 80% 操作） |

**优点**：
- 款号跳过识别，只靠人工选，避免识别错误
- 数量/单价是最高频字段，自动填入节省大量时间
- 逻辑简单，不依赖 AI 模糊匹配
- 容错率高，款号选错了可以改

#### 方案 B：AI 辅助全自动化（后续探索）

**核心思路**：OCR + AI 智能匹配商品库 + 置信度标注 + 用户确认。

**流程**：
```
拍照 → OCR 识别 → AI 解析结构 → 模糊匹配商品库 → 生成预填表单 → 用户核对修正 → 提交
```

**AI 解析示例**：

输入（OCR 识别后的文字）：
```
客户：张三  138xxxx
日期：2024-01-15

款号        颜色    数量    单价
001         黑      10      120
002         白      5       135
```

输出（AI 解析后）：
```json
{
  "customerName": "张三",
  "customerPhone": "138xxxx",
  "items": [
    {
      "skuCode": "P001-BLACK-M",
      "productName": "经典T恤",
      "colorName": "黑色",
      "quantity": 10,
      "price": 120,
      "confidence": "high"
    },
    {
      "skuCode": "P002-WHITE-M",
      "productName": "休闲裤",
      "colorName": "白色",
      "quantity": 5,
      "price": 135,
      "confidence": "medium"
    }
  ]
}
```

**置信度标注**：
| 置信度 | 表现 | 处理 |
|--------|------|------|
| 高 | 绿色标识，自动填入 | 直接使用 |
| 中 | 橙色标识，填入但待确认 | 用户确认/修改 |
| 低 | 红色标识，预设为空 | 用户手动选择 |

**界面设计**：
```
┌─────────────────────────────────────────────────────────────┐
│  📷 拍照录单 - AI 智能识别                                   │
├─────────────────────────────────────────────────────────────┤
│  📋 识别结果预览（绿色=高置信 橙色=待确认 红色=需手动）        │
├─────────────────────────────────────────────────────────────┤
│  客户：张三  138xxxx                           ✅ 高置信      │
│                                                              │
│  商品明细：                                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 001 经典T恤   黑色   x10   ¥120    ✅ 高置信          │  │
│  │ 002 休闲裤    白色   x5    ¥135    ⚠️ 中置信(待确认) │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  款号识别候选（如置信度低）：                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 002 可能匹配：                                        │  │
│  │   - P002-白色-M（经典衬衫）← 推荐                     │  │
│  │   - P005-白色-S（休闲T恤）                           │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ⚠️ 2 个商品需要您确认                                       │
│                                                              │
│                    [ 确认提交订单 ]                            │
└─────────────────────────────────────────────────────────────┘
```

**技术要点**：
- AI 模糊匹配款号（支持缩写、谐音、部分匹配）
- 颜色名称标准化映射（"黑" → "黑色"，"BLK" → "黑色"）
- 置信度评分机制
- 逐步学习优化（用户修正记录用于模型改进）

#### 方案 C：纯手动录单（基线方案）

无 OCR，直接手动输入所有字段。

### 9.5 技术方案

| 模块 | 方案 | 说明 |
|------|------|------|
| OCR 服务 | 微信扫一扫 / 腾讯云 OCR | 手写识别效果好 |
| AI 解析（方案B） | 本地 LLM 或云函数 | 解析文字结构，提取字段 |
| 商品匹配（方案B） | 模糊匹配算法 | 支持缩写、模糊搜索 |
| 图片存储 | 统一文件入口 + fileId 保存 | 第一版本地存储，后续可切七牛云/NAS；详见 [09-FILE_STORAGE_DESIGN.md](./09-FILE_STORAGE_DESIGN.md) |

### 9.6 实施计划

**Phase 1：基础版（方案 A，半自动）**
1. 统一图片上传接口（复用文件存储模块）
2. OCR 识别文字服务
3. 表单字段提取（数量/单价/总金额/客户名/日期）
4. 半自动表单（款号手动选择，其他自动填入）
5. 用户核对确认提交

**Phase 2：AI 增强版（方案 B，全自动）**
1. AI 解析服务（表格结构识别）
2. 商品库模糊匹配
3. 置信度标注
4. 候选商品推荐
5. 用户快速确认/修正

### 9.7 OCR 识别准确率保证

| 措施 | 说明 |
|------|------|
| 保留人工确认环节 | 不强制自动填充，识别结果仅供参考 |
| 置信度标注 | 高亮标注"不确定"字段，提醒用户检查 |
| 修正记录 | 用户修正历史用于优化识别模型 |
| 逐步学习 | 越用越准 |

---

## 十三、文件中心与客户展示页（P1）

> 详细设计见：[12-FILE_CENTER_ASSET_DESIGN.md](./12-FILE_CENTER_ASSET_DESIGN.md)

### 13.1 产品定位

文件中心不是单纯图片/视频相册，而是系统统一的数字资产中心。

第一版重点服务三个场景：

1. 后台统一管理所有上传图片和基础视频文件。
2. 管理未绑定到商品、SKU、订单或入库记录的临时文件，支持定期清理。
3. 为客户 iPad 现货展示页提供商品/SKU 图片来源。

### 13.2 核心规则

| 规则 | 说明 |
|------|------|
| 业务表继续保存 fileId | 禁止业务表保存物理路径 |
| 文件资产独立管理 | 图片、视频、后续文档均进入文件中心 |
| 业务关系走绑定表 | 商品、SKU、订单、入库日志关系统一走 `file_business_bind` |
| 商品主图兼容旧字段 | `product.image_url` 继续保存主图 fileId，同时同步绑定关系 |
| 未绑定文件可治理 | 未绑定、未归档、超过保留期的临时文件可自动软删除 |
| 私有文件需鉴权 | 订单图片、入库凭证、OCR 原图默认私有 |

### 13.3 文件中心能力边界

第一版必须支持：

- PC 后台 `/files` 文件中心。
- 文件夹管理。
- 图片上传、预览、移动、软删除。
- 基础视频文件上传和基础预览。
- 未绑定文件筛选和清理。
- 绑定到商品主图、商品图集和 SKU 图片。
- 查看并追加绑定订单图片、入库凭证。

第一版不做：

- 视频转码。
- 分片上传和断点续传。
- 七牛云/NAS 切换。
- 客户公开分享链接。
- AI 自动打标签。
- 文档在线预览。
- 文件版本管理。

### 13.4 客户 iPad 展示页

新增只读客户展示页，建议路由：

```text
/catalog
```

或：

```text
/showroom
```

页面用于 iPad 展示现货服装，数据来自系统商品、SKU、文件中心图片绑定和实时库存。

第一版展示规则：

| 能力 | 说明 |
|------|------|
| 商品相册 | 展示商品主图、商品图集、SKU 图片 |
| 筛选 | 支持全部 / 现货 / 有图，后续扩展分类、颜色、尺码 |
| 响应式布局 | 横屏为商品网格 + 右侧详情；竖屏为商品网格 + 底部/全屏详情 |
| 大图模式 | 商品详情图片可进入全屏大图模式，支持切图、缩略图胶片条和关闭返回 |
| 库存口径 | 使用系统可用库存：`quantity - reservedQty - globalReservedQty` |
| 库存展示 | 第一版只显示“有现货 / 暂无现货”，不显示真实数量 |
| 访问方式 | 第一版采用 iPad 登录只读账号，不做公开分享链接 |
| 数据边界 | 不展示成本、毛利、真实库存数量和后台管理信息 |
| 身份边界 | 第一版默认游客/散客模式；客户选择、扫码识别、行为埋点和选款清单后续再做 |

客户展示页是静态前端页面 + 动态 API 数据，不读取群晖相册或 iPad 本地相册。

---

## 十四、多租户设计

### 9.1 租户隔离

- 所有业务表通过 `tenant_id` 字段隔离
- 多租户通过 MyBatis-Plus TenantLineInnerInterceptor 自动处理
- **禁止手动拼接 tenant_id**

### 9.2 超级管理员

- tenant_id = 0 为超级管理员
- 可访问所有租户数据
- 一般不启用

---

## 十五、API 通用规范

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

## 十六、非功能性需求

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

## 十七、版本历史

| 版本 | 日期 | 修改内容 |
|------|------|---------|
| v1.0 | 2026-03-21 | 初始版本 |
| v1.1 | 2026-03-21 | 新增商品模块（颜色/尺码/SKU）、库存模块（完整变动记录）、订单与库存联动规则 |
| v1.2 | 2026-03-22 | 新增 blade-admin PC 管理端项目规划，采用 Monorepo 结构 |
| v1.3 | 2026-03-22 | 架构决策：系统全面实行软删除，数据可追溯；订单保留冗余字段保证历史追溯 |
| v1.4 | 2026-03-23 | 新增支付状态（未付款/已付定金/已付全款）、定金金额、配送设置（送货地址、已送货状态）等线下录单核心功能 |
| v1.5 | 2026-03-23 | 新增 OCR 拍照录单功能（方案A半自动 + 方案B AI辅助），目标尽量减少手动输入 |
| v1.6 | 2026-04-02 | 新增看板系统（仪表盘 + 销售趋势图 + 热销商品排行） |
| v1.7 | 2026-04-04 | 商品表新增字段：product_code、supplier_id、cost_price、wholesale_price、weight、remark；新增供应商表（supplier，P1暂不开发） |
| v1.8 | 2026-05-22 | 新增外部 AI Agent 对接需求，第一期锁定款式趋势、客户跟进、周期经营分析、只读 Agent Gateway、独立鉴权和租户绑定 |
| v1.9 | 2026-06-03 | 新增文件中心/数字资产中心和客户 iPad 现货展示页需求，锁定 fileId、绑定表、未绑定清理和第一版边界 |

---

## 十八、架构决策记录

### 14.1 软删除机制（2026-03-22）

**决策**：系统所有数据实行软删除，禁止物理删除。

**原因**：
1. 数据可追溯，满足财务审计要求
2. 历史订单、库存记录等核心数据永不丢失
3. 避免数据关联断裂导致的统计异常

**规则**：
- 所有业务表必须有 `deleted` 字段（tinyint，默认 0）
- 删除操作必须使用 `UPDATE SET deleted = 1`，禁止 DELETE
- 查询时自动过滤 `deleted = 1` 的数据
- 特殊情况下可查询已删除数据用于数据恢复

**实现**：
- MyBatis-Plus 配置 `LogicDeleteInterceptor` 自动处理
- 配置 ignore-tables 排除不需要软删的表（如系统配置表）

### 14.2 订单冗余字段（2026-03-22）

**决策**：订单明细表保留商品名称、颜色、尺码等冗余字段。

**原因**：
1. 订单是财务核心数据，必须保证任何时候都能完整展示
2. 查询性能优化，避免多表 JOIN
3. 历史追溯：客户对账时需要看到下单时的商品信息

**规则**：
- 商品表（product）实行软删除，禁止物理删除
- 订单创建时填充冗余字段
- 冗余字段作为"快照"，极端情况下保证可展示性

### 14.3 线下录单流程支持（2026-03-23）

### 14.4 OCR 拍照录单（2026-03-23）

**决策**：订单系统支持 OCR 拍照录单，实现半自动/全自动识别填表。

**优先级**：P2，等订单核心流程跑通后再重点开发。

**原因**：
1. 批发业务录单频繁，手动输入效率低
2. 款号识别是最大难点，必须有兜底方案
3. 用户核心诉求：尽量把手写或手动填入做到最低

**方案选择**：
- **Phase 1（推荐）**：方案 A 半自动 - 款号手动选，其他自动填入
- **Phase 2（后续）**：方案 B AI 全自动 - OCR + AI 模糊匹配 + 置信度标注

**核心原则**：
- 保留人工确认环节，不强制自动填充
- 款号识别不准 → 预设不填，用户手动选择
- 数量/单价识别准 → 自动填入

**决策**：订单系统支持"定金/尾款"和"送货状态"两个核心线下流程。

**原因**：
1. 批发业务常见先付定金、后付尾款的分期付款方式
2. 送货状态是线下录单的重要环节，与订单处理进度独立
3. 灵活的资金处理是线下记账的核心痛点

**规则**：
- 支付状态（payment_status）：0未付款 / 1已付定金 / 2已付全款
- 定金金额（deposit_amount）：仅当 payment_status=1 时有效，必须 > 0 且 < 总额
- 送货设置（need_delivery, delivery_address, is_delivered）：与订单状态独立
- 支付状态=已付全款时，自动确认付款（status=1）
- 支付状态=未付款时，订单仍可取消

### 14.5 外部 AI Agent 对接（2026-05-22）

**决策**：新增只读 Agent Gateway，外部 Agent 通过受控 API 获取款式趋势、客户跟进清单、周期经营报告数据包、搜索和分析结果。

**原因**：
1. 当前系统已经有看板、数据分析、库存预警和沉默客户等聚合能力，适合作为 Agent 数据底座。
2. 直接向 Agent 暴露数据库会绕开多租户、业务口径和权限约束。
3. Agent 写操作风险高，必须先验证只读接入、凭证和审计边界。

**规则**：
- Agent 使用独立凭证并绑定租户与 scope。
- 第一版不实现后端自然语言问答和泛化写动作。
- Agent 统计优先复用现有领域服务，避免与看板/分析口径漂移。
- WhatsApp 信息属于后续客户沟通上下文扩展，需先验证合规接入、客户映射和消息权限，再进入实现。
- 统一业务事件日志落地后，再评估 `/api/agent/changes` 和窄范围授权动作。

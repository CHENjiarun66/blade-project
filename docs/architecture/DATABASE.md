# 数据库表设计文档

> 本文档汇总 BladeProject 当前数据库表结构，按模块分组。
> **完整结构以 `blade-backend/src/main/resources/db/migration/*.sql` 的累计结果为准。**
> 新增或变更字段时，必须同步更新本文档；专题设计文档只记录增量设计，不重复维护整表最终版。
> 最后更新：2026-06-21

---

## 一、系统模块（System）

### 1.1 sys_tenant 租户表

**来源迁移**：`V1__init_schema.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 租户ID |
| tenant_name | varchar(50) | NOT NULL | 租户名称 |
| tenant_code | varchar(20) | UNIQUE, NOT NULL | 租户编码 |
| status | tinyint | DEFAULT 1 | 状态: 1启用 0禁用 |
| expire_time | datetime | | 过期时间 |
| package_id | bigint | | 租户套餐ID |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`uk_tenant_code(tenant_code)`

---

### 1.2 sys_role 角色表

**来源迁移**：`V1__init_schema.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 角色ID |
| role_name | varchar(30) | NOT NULL | 角色名称 |
| role_code | varchar(20) | NOT NULL | 角色编码 |
| description | varchar(100) | | 描述 |
| status | tinyint | DEFAULT 1 | 状态: 1启用 0禁用 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`uk_role_code(role_code, tenant_id)`

---

### 1.3 sys_user 用户表

**来源迁移**：`V1__init_schema.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 用户ID |
| username | varchar(20) | UNIQUE, NOT NULL | 用户名 |
| password | varchar(100) | NOT NULL | 密码（BCrypt加密） |
| nickname | varchar(30) | | 昵称 |
| email | varchar(50) | | 邮箱 |
| phone | varchar(11) | | 手机号 |
| avatar | varchar(255) | | 头像 |
| status | tinyint | DEFAULT 1 | 状态: 1启用 0禁用 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`uk_username(username)`

**说明**：当前唯一索引只建在 `username` 上，迁移注释里虽提到多租户复用用户名场景，但现行表结构仍是全局唯一。

---

### 1.4 sys_user_role 用户角色关联表

**来源迁移**：`V1__init_schema.sql`、`V13__sys_user_role_fix.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | ID |
| user_id | bigint | NOT NULL | 用户ID |
| role_id | bigint | NOT NULL | 角色ID |
| tenant_id | bigint | NOT NULL, DEFAULT 1 | 租户ID |
| deleted | int | DEFAULT 0 | 软删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |

**索引**：`uk_user_role_tenant(user_id, role_id, tenant_id)`

---

### 1.5 sys_role_menu 角色菜单关联表

**来源迁移**：`V1__init_schema.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | ID |
| role_id | bigint | NOT NULL | 角色ID |
| menu_id | bigint | NOT NULL | 菜单ID |

**状态**：历史兼容表，当前权限判断主链路以 `sys_permission + sys_role_permission` 为主，不依赖本表。

---

### 1.6 sys_menu 菜单表

**来源迁移**：`V1__init_schema.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 菜单ID |
| menu_name | varchar(30) | NOT NULL | 菜单名称 |
| parent_id | bigint | DEFAULT 0 | 父菜单ID |
| path | varchar(100) | | 路由地址 |
| component | varchar(100) | | 组件路径 |
| is_frame | tinyint | DEFAULT 1 | 是否为外链 |
| status | tinyint | DEFAULT 1 | 状态 |
| perms | varchar(100) | | 权限标识 |
| icon | varchar(50) | | 菜单图标 |
| sort | int | DEFAULT 0 | 排序 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

---

### 1.7 sys_dict 字典表

**来源迁移**：`V1__init_schema.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 字典ID |
| dict_name | varchar(50) | UNIQUE, NOT NULL | 字典名称 |
| dict_code | varchar(50) | UNIQUE, NOT NULL | 字典编码 |
| description | varchar(100) | | 描述 |
| status | tinyint | DEFAULT 1 | 状态 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`uk_dict_code(dict_code)`

---

### 1.8 sys_permission 权限定义表

**来源迁移**：`V12__permission_system.sql`、`V15__add_api_permissions.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 权限ID |
| name | varchar(50) | NOT NULL | 权限名称 |
| code | varchar(100) | UNIQUE, NOT NULL | 权限编码，全局唯一 |
| type | int | NOT NULL | 类型: 1菜单 2按钮 3字段 4API |
| module | varchar(50) | | 所属模块 |
| parent_id | bigint | DEFAULT 0 | 父权限ID |
| path | varchar(200) | | 路由路径或接口路径 |
| method | varchar(10) | | HTTP方法 |
| icon | varchar(50) | | 图标 |
| sort | int | DEFAULT 0 | 排序 |
| status | int | DEFAULT 1 | 状态: 1启用 0禁用 |
| mask_type | int | | 脱敏类型 |
| mask_value | varchar(100) | | 脱敏替换值 |
| description | varchar(200) | | 权限描述 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | int | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`uk_code(code)`, `idx_type(type)`, `idx_module(module)`, `idx_parent_id(parent_id)`, `idx_tenant_id(tenant_id)`

---

### 1.9 sys_role_permission 角色权限关联表

**来源迁移**：`V12__permission_system.sql`、`V16__fix_role_permission_tenant.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | ID |
| role_id | bigint | NOT NULL | 角色ID |
| permission_id | bigint | NOT NULL | 权限ID |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | int | DEFAULT 0 | 软删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |

**索引**：`uk_role_permission(role_id, permission_id)`, `idx_role_id(role_id)`, `idx_permission_id(permission_id)`, `idx_tenant_id(tenant_id)`

---

## 二、商品模块（Product）

### 2.1 product_category 商品分类表

**来源迁移**：`V3__product_module.sql`、`V49__product_sku_types_and_placeholder.sql`、`V50__correct_default_sku_classification.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 分类ID |
| category_name | varchar(50) | NOT NULL | 分类名称 |
| parent_id | bigint | DEFAULT 0 | 父分类ID |
| sort | int | DEFAULT 0 | 排序 |
| status | tinyint | DEFAULT 1 | 状态: 1启用 0禁用 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`idx_tenant_id(tenant_id)`

---

### 2.2 product 商品表

**来源迁移**：`V3__product_module.sql`、`V23__product_add_fields.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 商品ID |
| product_code | varchar(30) | UNIQUE, NOT NULL | 商品编码 |
| name | varchar(100) | NOT NULL | 商品名称 |
| category_id | bigint | | 分类ID |
| supplier_id | bigint | | 供应商ID |
| unit | varchar(10) | DEFAULT '件' | 单位 |
| cost_price | decimal(12,2) | | 进货价（成本参考） |
| wholesale_price | decimal(12,2) | | 批发价 |
| weight | decimal(10,2) | | 重量 |
| description | text | | 描述 |
| image_url | varchar(255) | | 商品主图 fileId，历史数据可为URL |
| remark | varchar(500) | | 备注 |
| status | tinyint | DEFAULT 1 | 状态: 1启用 0禁用 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`uk_product_code(product_code, tenant_id)`, `idx_tenant_id(tenant_id)`, `idx_category_id(category_id)`

---

### 2.3 supplier 供应商表

**来源迁移**：`V23__product_add_fields.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 供应商ID |
| supplier_code | varchar(30) | UNIQUE, NOT NULL | 供应商编码 |
| supplier_name | varchar(100) | NOT NULL | 供应商名称 |
| contact | varchar(50) | | 联系人 |
| phone | varchar(20) | | 电话 |
| address | varchar(255) | | 地址 |
| status | tinyint | DEFAULT 1 | 状态: 1启用 0禁用 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`uk_supplier_code(supplier_code, tenant_id)`, `idx_tenant_id(tenant_id)`

---

### 2.4 product_color 颜色表

**来源迁移**：`V3__product_module.sql`、`V12__add_status_to_size_color.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 颜色ID |
| color_code | varchar(20) | UNIQUE, NOT NULL | 颜色编码 |
| color_name | varchar(50) | NOT NULL | 颜色名称 |
| status | tinyint | NOT NULL, DEFAULT 1 | 状态: 1启用 0禁用 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |

**索引**：`uk_color_code(color_code, tenant_id)`, `idx_tenant_id(tenant_id)`

---

### 2.5 product_size 尺码表

**来源迁移**：`V3__product_module.sql`、`V12__add_status_to_size_color.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 尺码ID |
| size_code | varchar(10) | UNIQUE, NOT NULL | 尺码编码 |
| sort | int | DEFAULT 0 | 排序 |
| status | tinyint | NOT NULL, DEFAULT 1 | 状态: 1启用 0禁用 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |

**索引**：`uk_size_code(size_code, tenant_id)`, `idx_tenant_id(tenant_id)`

---

### 2.6 product_sku SKU表

**来源迁移**：`V3__product_module.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | SKU ID |
| product_id | bigint | NOT NULL | 商品ID |
| color_id | bigint | NOT NULL | 颜色ID |
| size_id | bigint | NOT NULL | 尺码ID |
| sku_code | varchar(50) | UNIQUE, NOT NULL | SKU编码（系统自动生成） |
| sku_type | varchar(20) | NOT NULL, DEFAULT NORMAL | `NORMAL` 真实规格、`DEFAULT` 无规格默认、`PLACEHOLDER` 款号占位 |
| price | decimal(12,2) | NOT NULL | 单价 |
| cost_price | decimal(12,2) | DEFAULT 0 | 成本价 |
| bar_code | varchar(50) | | 条形码 |
| status | tinyint | DEFAULT 1 | 状态: 1启用 0禁用 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |

**索引**：`uk_sku_code(sku_code, tenant_id)`, `idx_product_id(product_id)`, `idx_color_id(color_id)`, `idx_size_id(size_id)`, `idx_tenant_id(tenant_id)`, `idx_product_sku_type(tenant_id, product_id, sku_type, status, deleted)`

系统保留属性 `UNSPECIFIED/UNSPEC` 用于 `PLACEHOLDER`，`NA/NA` 用于无规格 `DEFAULT`；它们状态为禁用且不写入商品颜色/尺码关联，因此不会出现在普通属性维护列表。多规格商品最多一个有效占位 SKU，编码为 `{product_code}-UNSPEC-UNSPEC`。

---

### 2.7 product_color_rel 商品颜色关联表

**来源迁移**：`V3__product_module.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | ID |
| product_id | bigint | NOT NULL | 商品ID |
| color_id | bigint | NOT NULL | 颜色ID |

**索引**：`uk_product_color(product_id, color_id)`, `idx_product_id(product_id)`, `idx_color_id(color_id)`

---

### 2.8 product_size_rel 商品尺码关联表

**来源迁移**：`V3__product_module.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | ID |
| product_id | bigint | NOT NULL | 商品ID |
| size_id | bigint | NOT NULL | 尺码ID |

**索引**：`uk_product_size(product_id, size_id)`, `idx_product_id(product_id)`, `idx_size_id(size_id)`

---

## 三、库存模块（Inventory）

### 3.1 warehouse 仓库表

**来源迁移**：`V4__inventory_module.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 仓库ID |
| warehouse_name | varchar(50) | NOT NULL | 仓库名称 |
| address | varchar(200) | | 地址 |
| contact | varchar(30) | | 联系人 |
| phone | varchar(20) | | 电话 |
| status | tinyint | DEFAULT 1 | 状态: 1启用 0禁用 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`idx_tenant_id(tenant_id)`

---

### 3.2 inventory 库存表

**来源迁移**：`V4__inventory_module.sql`、`V11__inventory_add_version.sql`、`V20__inventory_global_reserve.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 库存ID |
| sku_id | bigint | NOT NULL | SKU ID |
| warehouse_id | bigint | NOT NULL | 仓库ID |
| quantity | int | NOT NULL, DEFAULT 0 | 当前库存数量 |
| reserved_qty | int | NOT NULL, DEFAULT 0 | 单仓预留数量 |
| global_reserved_qty | int | NOT NULL, DEFAULT 0 | 跨仓总量预留 |
| available_qty | int | GENERATED | 可用数量（`quantity - reserved_qty - global_reserved_qty`） |
| alert_threshold | int | DEFAULT 10 | 预警阈值 |
| version | int | NOT NULL, DEFAULT 0 | 乐观锁版本号 |
| tenant_id | bigint | NOT NULL | 租户ID |
| update_time | datetime | | 更新时间 |

**索引**：`uk_sku_warehouse(sku_id, warehouse_id, tenant_id)`, `idx_sku_id(sku_id)`, `idx_warehouse_id(warehouse_id)`, `idx_tenant_id(tenant_id)`

---

### 3.3 inventory_log 库存变动记录表

**来源迁移**：`V4__inventory_module.sql`、`V22__inventory_log_warehouse_nullable.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 记录ID |
| sku_id | bigint | NOT NULL | SKU ID |
| warehouse_id | bigint | DEFAULT NULL | 仓库ID；跨仓预留类日志可为 `NULL` |
| change_type | varchar(20) | NOT NULL | 变动类型 |
| change_qty | int | NOT NULL | 变动数量（正数=入库，负数=出库） |
| before_qty | int | NOT NULL | 变动前库存 |
| after_qty | int | NOT NULL | 变动后库存 |
| order_id | bigint | | 关联订单ID |
| reference_no | varchar(50) | | 关联单据号 |
| supplier_id | bigint | | 供应商ID（预留） |
| supplier_name | varchar(100) | | 供应商名称（冗余） |
| operator_id | bigint | | 操作人ID |
| remark | varchar(500) | | 备注 |
| images | varchar(1000) | | 入库凭证 fileId JSON数组格式 |
| tenant_id | bigint | NOT NULL | 租户ID |
| create_time | datetime | DEFAULT | 操作时间 |

**索引**：`idx_sku_id(sku_id)`, `idx_warehouse_id(warehouse_id)`, `idx_order_id(order_id)`, `idx_change_type(change_type)`, `idx_tenant_id(tenant_id)`, `idx_create_time(create_time)`

**说明**：`change_type` 的精确取值以当前后端实现为准，本文档不再单独维护一套可能过期的枚举列表。

---

### 3.4 inventory_global_reserve 跨仓总量预留表

**来源迁移**：`V20__inventory_global_reserve.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 主键ID |
| order_id | bigint | NOT NULL | 订单ID |
| sku_id | bigint | NOT NULL | SKU ID |
| reserve_qty | int | NOT NULL | 预留数量 |
| released_qty | int | NOT NULL, DEFAULT 0 | 已释放数量 |
| tenant_id | bigint | | 租户ID |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`idx_order_sku(order_id, sku_id)`, `idx_sku(sku_id)`

---

## 四、订单模块（Order）

### 4.1 sale_order 订单表

**来源迁移**：`V2__product_order.sql`、`V5__order_refactor.sql`、`V7__order_table_rename.sql`、`V8__order_images.sql`、`V8__order_payment_delivery_fields.sql`、`V10__order_salesman.sql`、`V19__order_add_salesman_name.sql`、`V21__order_delivery_plan.sql`、`V29__order_quick_entry_finance.sql`、`V30__order_source_shop.sql`、`V39__order_write_off.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 订单ID |
| order_no | varchar(30) | UNIQUE, NOT NULL | 订单号 |
| order_date | date | | 订单日期（纸质单据日期） |
| source_doc_no | varchar(50) | | 纸质单据号/外部单号 |
| source_shop | varchar(100) | | 订单来源档口/店铺，不等同于仓库 |
| order_type | varchar(20) | NOT NULL, DEFAULT 'SPOT' | 订单类型：SPOT现货/PREORDER订货 |
| customer_id | bigint | | 客户ID |
| customer_name | varchar(50) | NOT NULL | 客户名称 |
| customer_phone | varchar(11) | | 客户电话 |
| customer_address | varchar(255) | | 客户地址 |
| total_amount | decimal(12,2) | NOT NULL | 订单总金额 |
| original_amount | decimal(12,2) | | 原始订单金额 |
| refund_amount | decimal(12,2) | DEFAULT 0 | 已退款金额 |
| paid_amount | decimal(12,2) | DEFAULT 0 | 已支付金额 |
| write_off_amount | decimal(12,2) | NOT NULL, DEFAULT 0 | 抹零/短款结清金额 |
| write_off_reason | varchar(255) | | 抹零/短款结清原因 |
| payment_status | tinyint | NOT NULL, DEFAULT 0 | 收款状态: 0未付款 1部分收款 2已结清 |
| deposit_amount | decimal(12,2) | NOT NULL, DEFAULT 0 | 定金金额 |
| freight_amount | decimal(12,2) | NOT NULL, DEFAULT 0 | 客户运费收入 |
| freight_cost | decimal(12,2) | NOT NULL, DEFAULT 0 | 实际运费成本 |
| total_cost_amount | decimal(12,2) | NOT NULL, DEFAULT 0 | 订单总成本 |
| gross_profit | decimal(12,2) | NOT NULL, DEFAULT 0 | 订单毛利 |
| adjustment_status | varchar(20) | DEFAULT 'NONE' | 调整状态 |
| status | tinyint | NOT NULL, DEFAULT 0 | 订单状态 |
| warehouse_id | bigint | | 发货仓库 |
| salesman_id | bigint | | 开单销售人员ID |
| salesman_name | varchar(100) | | 开单销售人员姓名 |
| need_delivery | tinyint | NOT NULL, DEFAULT 0 | 是否需要送货 |
| delivery_address | varchar(255) | | 送货地址 |
| is_delivered | tinyint | NOT NULL, DEFAULT 0 | 是否已送货 |
| delivered_at | datetime | | 送货时间 |
| pay_time | datetime | | 支付时间 |
| confirm_time | datetime | | 历史确认时间字段 |
| deliver_time | datetime | | 发货时间 |
| complete_time | datetime | | 完成时间 |
| images | varchar(1000) | | 订单图片 fileId JSON数组格式 |
| remark | varchar(255) | | 备注 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`uk_order_no(order_no, tenant_id)`, `idx_tenant_id(tenant_id)`, `idx_status(status)`, `idx_customer_phone(customer_phone)`

**说明**：
- 当前库中保留 `confirm_time`，但业务主流程更多使用 `pay_time`、`deliver_time`、`complete_time`。
- 当前实现订单状态为 9 状态：`0-8`。

---

### 4.2 sale_order_item 订单明细表

**来源迁移**：`V2__product_order.sql`、`V5__order_refactor.sql`、`V7__order_table_rename.sql`、`V18__order_item_warehouse.sql`、`V21__order_delivery_plan.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 明细ID |
| order_id | bigint | NOT NULL | 订单ID |
| product_id | bigint | | 商品ID（历史字段，当前以 `sku_id` 为主） |
| sku_id | bigint | | SKU ID |
| warehouse_id | bigint | | 仓库ID（支持多仓分配） |
| sku_code | varchar(50) | | SKU编码（冗余） |
| color_name | varchar(20) | | 颜色（冗余） |
| size_name | varchar(10) | | 尺码（冗余） |
| product_name | varchar(100) | NOT NULL | 商品名称（冗余） |
| price | decimal(12,2) | NOT NULL | 单价 |
| cost_price | decimal(12,2) | NOT NULL, DEFAULT 0 | 下单成本价快照 |
| quantity | int | NOT NULL | 下单数量 |
| planned_quantity | int | DEFAULT 0 | 计划数量（原订单数量） |
| allocated_quantity | int | DEFAULT 0 | 配货数量（调整后数量） |
| out_quantity | int | DEFAULT 0 | 已出库数量 |
| adjustment_remark | varchar(255) | | 调整说明 |
| subtotal | decimal(12,2) | NOT NULL | 小计金额 |
| cost_amount | decimal(12,2) | NOT NULL, DEFAULT 0 | 成本金额快照 |
| gross_profit | decimal(12,2) | NOT NULL, DEFAULT 0 | 明细毛利快照 |
| tenant_id | bigint | NOT NULL | 租户ID |
| create_time | datetime | DEFAULT | 创建时间 |

**索引**：`idx_order_id(order_id)`, `idx_tenant_id(tenant_id)`

---

### 4.3 order_delivery 出库单表

**来源迁移**：`V17__order_delivery_tables.sql`、`V40__order_delivery_display_columns.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 主键 |
| delivery_no | varchar(30) | UNIQUE, NOT NULL | 出库单号 |
| order_id | bigint | NOT NULL | 订单ID |
| warehouse_id | bigint | NOT NULL | 仓库ID |
| warehouse_name | varchar(50) | | 仓库名称（冗余） |
| status | tinyint | NOT NULL, DEFAULT 0 | 状态: 0待出库 1部分出库 2已出库 3已取消 |
| total_quantity | int | NOT NULL, DEFAULT 0 | 出库总数量 |
| deliverer | varchar(50) | | 发货人 |
| deliver_time | datetime | | 发货时间 |
| remark | varchar(255) | | 备注 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | NOT NULL, DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`uk_delivery_no(delivery_no)`, `idx_order_id(order_id)`, `idx_warehouse_id(warehouse_id)`, `idx_status(status)`

---

### 4.4 order_delivery_item 出库明细表

**来源迁移**：`V17__order_delivery_tables.sql`、`V40__order_delivery_display_columns.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 主键 |
| delivery_id | bigint | NOT NULL | 出库单ID |
| order_item_id | bigint | NOT NULL | 订单明细ID |
| sku_id | bigint | NOT NULL | SKU ID |
| sku_code | varchar(50) | | SKU编码（冗余） |
| product_name | varchar(100) | | 商品名称（冗余） |
| color_name | varchar(50) | | 颜色名称（冗余） |
| size_name | varchar(50) | | 尺码名称（冗余） |
| quantity | int | NOT NULL | 出库数量 |
| create_time | datetime | DEFAULT | 创建时间 |

**索引**：`idx_delivery_id(delivery_id)`, `idx_order_item_id(order_item_id)`

---

### 4.5 order_delivery_plan 订单配货计划表

**来源迁移**：`V21__order_delivery_plan.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 主键ID |
| order_id | bigint | NOT NULL | 订单ID |
| order_item_id | bigint | | 原订单明细ID |
| sku_id | bigint | NOT NULL | SKU ID |
| warehouse_id | bigint | | 仓库ID |
| planned_qty | int | NOT NULL, DEFAULT 0 | 计划数量（原订单数量） |
| allocated_qty | int | NOT NULL, DEFAULT 0 | 配货数量（调整后数量） |
| out_qty | int | NOT NULL, DEFAULT 0 | 已出库数量 |
| status | varchar(20) | DEFAULT 'PENDING' | 状态：PENDING/ALLOCATED/OUT |
| remark | varchar(255) | | 备注 |
| tenant_id | bigint | | 租户ID |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`idx_order_id(order_id)`, `idx_sku_warehouse(sku_id, warehouse_id)`, `idx_tenant_id(tenant_id)`

---

### 4.6 order_adjustment_log 订单调整记录表

**来源迁移**：`V21__order_delivery_plan.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 主键ID |
| order_id | bigint | NOT NULL | 订单ID |
| operator_id | bigint | | 操作人ID |
| operator_name | varchar(50) | | 操作人名称 |
| adjustment_type | varchar(20) | NOT NULL | 调整类型：REDUCE/REPLACE/REFUND |
| original_sku_id | bigint | | 原SKU ID |
| original_quantity | int | | 原数量 |
| new_sku_id | bigint | | 新SKU ID |
| new_quantity | int | | 新数量 |
| reason | varchar(255) | | 调整原因 |
| confirmed_time | datetime | | 确认时间 |
| create_time | datetime | DEFAULT | 创建时间 |
| tenant_id | bigint | | 租户ID |

**索引**：`idx_order_id(order_id)`, `idx_tenant_id(tenant_id)`

---

## 五、客户模块（Customer）

### 5.1 crm_customer 客户表

**来源迁移**：`V9__customer_module.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 客户ID |
| name | varchar(50) | NOT NULL | 客户名称 |
| address | varchar(255) | | 客户地址 |
| remark | varchar(500) | | 备注 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`idx_tenant_id(tenant_id)`, `idx_deleted(deleted)`

---

### 5.2 crm_customer_phone 客户电话表

**来源迁移**：`V9__customer_module.sql`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 电话ID |
| customer_id | bigint | NOT NULL | 客户ID |
| phone | varchar(20) | NOT NULL | 电话号码 |
| is_primary | tinyint | DEFAULT 0 | 是否主号: 0否 1是 |
| tenant_id | bigint | NOT NULL | 租户ID |
| deleted | tinyint | DEFAULT 0 | 删除标记 |
| create_time | datetime | DEFAULT | 创建时间 |
| update_time | datetime | | 更新时间 |

**索引**：`idx_customer_id(customer_id)`, `idx_phone(phone)`, `idx_deleted(deleted)`

---

## 六、多租户设计

### 6.1 租户隔离方式

**唯一正确方式**：MyBatis-Plus 的 `TenantLineInnerInterceptor` 自动处理。

### 6.2 租户字段规范

除忽略表外，所有业务表都应包含 `tenant_id` 字段。

### 6.3 忽略租户的表

| 表名 | 说明 |
|------|------|
| `sys_dict` | 字典表，全局共享 |
| `sys_param` | 参数配置表，全局共享 |
| `sys_tenant` | 租户表本身 |

---

## 七、版本来源速查

| 迁移 | 说明 |
|------|------|
| V1 | 初始化系统表 |
| V2 | 订单模块初始表结构 |
| V3 | 商品模块表结构 |
| V4 | 库存模块表结构 |
| V5 | 订单补充客户、金额、仓库和冗余字段 |
| V8 | 订单图片、支付状态和配送设置字段 |
| V9 | 客户模块表结构 |
| V10 | 订单表添加销售人员ID |
| V11 | 库存表添加乐观锁版本号 |
| V12 | 权限系统核心表 + 颜色尺码状态字段 |
| V13 | 修复 `sys_user_role` 表结构 |
| V15 | API 权限定义 |
| V17 | 出库单表 |
| V18 | 订单明细补仓库ID |
| V19 | 订单表添加销售人员姓名 |
| V20 | 跨仓总量预留表 + `inventory.global_reserved_qty` |
| V21 | 配货计划、调整记录、订单扩展字段 |
| V22 | `inventory_log.warehouse_id` 改为可空 |
| V23 | 商品扩展字段 + 供应商表 |

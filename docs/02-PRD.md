# 产品需求文档（PRD）

> 现行业务契约，2026-11-02 对齐。部署/验收状态见 [项目范围](./PROJECT_SCOPE.md)；表格为业务字段摘要，精确结构以累计 Flyway 和 DTO 为准。

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

SKU 分为真实规格、无规格默认和款号占位三类。真实 SKU = 商品 + 颜色 + 尺码；款号占位 SKU 用于纸单只记录 SPU/款号、无法确定颜色尺码的场景。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| product_id | bigint | 商品ID |
| color_id | bigint | 颜色ID |
| size_id | bigint | 尺码ID |
| sku_code | varchar | SKU编码（系统自动生成） |
| sku_type | varchar | `NORMAL` 真实规格、`DEFAULT` 无规格默认、`PLACEHOLDER` 款号占位 |
| price | decimal | 单价（可覆盖商品级批发价） |
| cost_price | decimal | 成本价（可覆盖商品级进货价） |
| bar_code | varchar | 条形码（预留） |
| status | tinyint | 状态：1启用 0禁用 |
| tenant_id | bigint | 租户ID |
| deleted | tinyint | 删除标记 |
| create_time | datetime | 创建时间 |

**SKU 编码规则**：`{商品编码}-{颜色编码}-{尺码编码}`
例：Jacket-BLACK-M

**SKU 类型规则**：

- 显式规格商品（只要存在至少一个启用的 `NORMAL` SKU）自动维护一个 `PLACEHOLDER`。即使当前只有一个颜色尺码组合，也必须提供“整款录入”。后端编码保留 `{款号}-UNSPECIFIED-UNSPEC`（兼容历史 `{款号}-UNSPEC-UNSPEC`），页面统一显示为“整款录入（颜色/尺码未指定）”，不得要求用户理解技术编码。
- 无颜色且无尺码的商品自动创建一个 `DEFAULT`，后端编码保留 `{款号}-NA-NA`，页面统一显示为“无规格商品（实际 SKU）”。它是正常可售 SKU，不是占位 SKU。
- 只有纯无规格 `DEFAULT` 商品按款号直接匹配实际 SKU；显式规格商品无论具体 SKU 数量多少，按款号查询都优先匹配 `PLACEHOLDER`。
- `PLACEHOLDER` 计入款号总销量、销售额和客户贡献，但不进入真实颜色/尺码排名，也不参与商品目录库存可用性判断。
- 占位订单后续拆到真实 SKU 时必须做数量转移，禁止同时保留占位销量和新增真实 SKU 销量。
- 商品从无规格升级为有颜色/尺码后，原 `DEFAULT/NA-NA` 禁用但不得删除，历史订单继续引用原 SKU。它在页面和分析中单列为“历史无规格”；记录型订单保留原事实，库存关联订单在配货/出库前必须像占位明细一样拆到当前真实 SKU。
- 库存写入口必须在服务端统一校验 SKU 语义：`PLACEHOLDER` 永远不能入库、调整、预留或出库；`DEFAULT` 仅在商品不存在启用的 `NORMAL` 时可继续形成库存事实。释放既有预留等纠错动作不受此门禁阻断。
- 正式订单可以先选择 `STOCK_LINKED` 表达履约意图，但只要仍有占位或历史无规格明细，就不能创建配货、预留和出库事实。

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

### 4.8 商品管理 v2：SKU 精细维护与商品素材管理

当前商品模块第一版已满足商品建档、颜色/尺码选择、SKU 自动生成、商品主图上传和基础 CRUD。v2 的目标是让商品资料维护能直接支撑生产录单、iPad 现货展示和后续数据分析，不再要求管理员频繁跳转到文件中心才能完成商品素材维护。

#### 4.8.1 商品编辑页结构

PC 商品编辑页应从单一表单升级为分区或 Tab 式编辑：

| 区域 | 目标 |
|------|------|
| 基础信息 | 商品编码、名称、分类、单位、进货价、批发价、重量、状态、描述、备注 |
| 颜色尺码 | 选择商品启用的颜色和尺码，继续自动生成/同步 SKU |
| SKU 明细 | 按颜色/尺码展示 SKU 列表，可逐行维护售价、成本价、条码、状态和 SKU 图片 |
| 商品素材 | 维护商品主图、商品图集；可从本地上传，也可从文件中心选择已有文件 |

第一版可以保留弹窗编辑形态，但必须避免 SKU 明细和图片素材挤在同一个长表单里；若弹窗承载过重，应改为商品详情/编辑页。

颜色尺码区域应支持在当前商品弹窗内快速新增颜色或尺码。新增操作复用颜色/尺码主档接口，成功后立即刷新当前选项、自动选中新建项并更新 SKU 组合预览，不得要求用户关闭商品弹窗后跳转到颜色或尺码列表页。重复输入当前已有选项时直接选中已有项，避免重复创建。

#### 4.8.2 SKU 明细维护规则

SKU 由商品 + 颜色 + 尺码生成，商品编码不允许在编辑时修改。SKU 明细维护必须满足：

- 颜色/尺码组合新增时，创建新的正常状态 SKU。
- 颜色/尺码组合移除时，不物理删除已有 SKU；优先软删除或禁用，避免影响历史订单和库存引用。
- 已存在 SKU 的 `price`、`cost_price`、`bar_code`、`status` 可单独编辑。
- 更新商品级进货价/批发价时，不应强制覆盖已手动维护过的 SKU 价格；如确需批量同步，应提供显式按钮或二次确认。
- 快速录单、订单编辑和 Catalog 只展示正常状态 SKU；历史订单继续显示订单明细快照，不依赖商品/SKU 当前名称。
- SKU 行应展示颜色名称、尺码、SKU 编码、售价、成本价、条码、状态、SKU 图片数量或缩略图。

#### 4.8.3 商品素材规则

商品素材统一走文件中心和 `file_business_bind` 绑定关系，不新增商品图集或 SKU 图片业务字段。

| 素材类型 | 绑定规则 |
|----------|----------|
| 商品主图 | 同步 `product.image_url` 保存原始 fileId，并写入 `file_business_bind` 的 `businessType=product, bindRole=main` |
| 商品图集 | 写入 `businessType=product, bindRole=gallery`，按 sort 排序 |
| SKU 图片 | 写入 `businessType=sku, bindRole=sku_image`，SKU 表不直接保存图片字段 |

商品编辑页应支持：

- 上传并设置商品主图。
- 上传/选择多张商品图集图片，支持排序、移除、预览。
- 在 SKU 明细行中绑定 SKU 图片，支持查看缩略图和移除。
- 历史 URL 或历史 fileId 继续兼容展示。
- 图片预览仍通过统一 `filePreviewUrl(fileId)` 或后续 `fileVariantUrl(fileId, type)`，不得手写裸 `/api/files/{id}/preview`。

#### 4.8.4 图片性能规则

商品列表、商品编辑页缩略图、文件中心网格、订单图片墙和 Catalog 卡片后续应优先加载派生图：

- 列表/卡片场景优先使用 `card`。
- 小缩略图、胶片条、SKU 行缩略图优先使用 `thumb`。
- 点击预览、大图、下载仍使用原图。
- 派生图缺失时必须兜底原图，不影响页面使用。

派生图底座对应任务为 `BE-1012`；PC 接入对应 `BA-1007`；Catalog 接入对应 `BA-1028`。

#### 4.8.5 删除与历史引用规则

- 商品、SKU、颜色、尺码、分类均应以软删除或禁用为主，避免破坏订单、库存、文件绑定和 Catalog 历史引用。
- 删除商品前应校验是否存在订单明细、库存记录或有效文件绑定；存在有效引用时，默认不允许删除，建议改为禁用。
- 若仅无业务引用，允许软删除，并同步处理有效 SKU 状态。
- 商品删除/禁用不得删除文件中心真实文件，只能解绑或保留绑定关系。

#### 4.8.6 供应商边界

供应商表和 `product.supplier_id` 已在 PRD 中预留，但供应商管理仍不是商品 v2 第一优先级。商品 v2 第一版只保留 supplierId 字段兼容，不开发供应商 CRUD；供应商模块应在商品录入、SKU 图片和派生图性能稳定后单独规划。

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
2. **订单收款/确认收款**：只更新订单收款状态和履约优先级，不检查库存、不锁定库存，不得因库存不足失败
3. **订单配货**：查看库存、选择实际发货 SKU/仓库/数量；库存不足、缺库存记录、替换 SKU 均只做提示和记录，不阻断配货方案保存
4. **订单发货**：按实际发货明细出库，扣减 `quantity`；这是订单流程中唯一真实扣减销售库存的节点
5. **直接调整**：记录调整原因，可正可负

**双轨预留规则**：
- `reserved_qty`：单仓预占场景使用
- `global_reserved_qty`：历史跨仓总量预留字段，第一版生产订单流程暂停使用；后续若基于超卖率重新启用，只能作为软预留/优先级能力，不得阻断录单和收款

---

## 六、订单模块（P0）

> 完整数据库字段、索引和历史兼容字段以 [architecture/DATABASE.md](./architecture/DATABASE.md) 和 Flyway 迁移脚本累计结果为准。本节只保留业务层核心字段。

### 6.1 订单表（sale_order）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| order_no | varchar | 订单编号 |
| order_date | date | 订单日期（纸质单据日期） |
| source_doc_no | varchar | 正式订单兼容纸质单据号；草稿中的独立批次与单号按 `批次_单号` 生成 |
| source_outlet_id | bigint | 档口主数据 ID，作为权限、筛选和统计依据；V63 已加列（可空），历史回填由生产副本显式人工映射执行，保持可空 |
| source_shop | varchar | 订单来源档口名称快照，不等同于仓库；不再作为权限依据 |
| order_type | varchar | 订单类型：SPOT现货/PREORDER订货 |
| customer_id | bigint | 客户ID |
| customer_name | varchar | 客户名称（冗余） |
| customer_phone | varchar | 客户电话 |
| customer_address | varchar | 客户地址 |
| total_amount | decimal | 订单总金额 |
| original_amount | decimal | 原始订单金额（调整前） |
| refund_amount | decimal | 已退款金额 |
| paid_amount | decimal | 已支付金额 |
| write_off_amount | decimal | 抹零/短款结清金额，客户少付但业务确认不再追收时使用 |
| write_off_reason | varchar | 抹零/短款结清原因 |
| payment_status | tinyint | 旧收款状态兼容投影；当前使用 collection_status |
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
| fulfillment_status | varchar | 当前履约状态（见 6.3） |
| collection_status | varchar | 当前收款状态（见 6.4） |
| fulfillment_mode | varchar | 当前履约方式（见 6.4） |
| status | tinyint | 旧状态兼容投影 |
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
- 快速录单新增“按商品批量录入 SKU”作为第一优先操作路径：录单员先搜索/选择商品款号或商品名称，再在该商品的 SKU 矩阵中直接填写各颜色/尺码数量，点击“添加到订单”后一次性生成多条订单明细。
- 批量 SKU 矩阵第一版只展示正常状态 SKU，不读取、不展示、不校验库存数量；库存为 0、未建库存记录或库存模块数据不完整时，均不得影响订单录入。快速录单与库存流程暂时解耦，后续库存提醒只能作为非阻塞提示另行设计。
- 批量添加时，数量为空或 0 的 SKU 不写入订单明细；数量大于 0 的 SKU 写入现有 `items` 明细结构。若相同 `skuId` 已存在于当前订单明细中，自动合并数量，不新增重复行，且不覆盖原明细中已手动修改过的单价和成本价。
- 保留现有明细表作为已添加商品的复核与修正区，支持继续修改数量、单价、成本价和删除行；原单 SKU 搜索添加能力可作为备用路径保留，但不应成为高频录入主路径。
- “来源档口/店铺”用于记录订单来自哪个档口商店，是订单归属和经营分析维度；不绑定库存仓库，也不参与库存预留、出库逻辑。
- 档口必须来自 `sales_outlet` 主数据。正式订单使用 `source_outlet_id` 做权限和统计，`source_shop` 只保存确认时名称快照；禁止再用单据批次、仓库或任意文本兜底。
- 用户可以绑定一个或多个档口。单档口销售员录单时自动带出并锁定；多档口用户只能选择授权集合；老板/管理员可选择本租户全部启用档口。
- 订单读取范围按“租户 ∩ 档口范围 ∩ 人员范围”计算。销售员默认只看本人在绑定档口的订单，档口负责人可看绑定档口内全部人员，老板可看全部档口全部人员。
- 订单类型仅作标记与筛选：`SPOT` 现货订单，`PREORDER` 订货订单；第一版不改变库存提醒和发货流程。
- 运费分两项：`freight_amount` 为向客户收取的运费，计入订单应收；`freight_cost` 为实际运费成本，计入订单成本。
- 利润公式：`gross_profit = 商品销售小计 - 商品成本金额 + 运费收入 - 运费成本`。
- 成本价、成本金额和毛利在订单保存时固化为历史快照，后续商品成本变化不影响历史订单。
- 后续收款通过统一财务动作更新流水、金额快照和 collection_status；旧字段由兼容层同步，不把收款等同履约。

### 6.3 订单履约状态（现行）

统一动作由 `OrderActionService` 处理；`fulfillment_status` 是主状态，旧 `status` 数字仅由 `OrderCompatAdapter` 产生兼容投影，不能重新拿来驱动状态机。

| 状态 | 含义 |
|---|---|
| CONFIRMED | 正式单已确认，等待收款/选择履约 |
| WAITING_ALLOCATION | 关联库存，待配货 |
| ALLOCATING | 配货中 |
| READY_TO_SHIP | 待发货 |
| SHIPPED | 已发货 |
| COMPLETED | 已完成 |
| CANCELLED | 已取消 |

草稿 `EDITING/CONFIRMED` 独立于正式单；作废/删除能力需按实际集成状态判断，不能因预留枚举就视为已上线。

### 6.4 收款状态与履约方式

收款状态 `collection_status` 为 `UNPAID/PARTIAL/SETTLED`，由服务端金额快照和流水计算，不接受客户端指定最终状态。实收、现金退款、销售退货和短款核销必须分开。

履约方式 `fulfillment_mode` 为 `UNDECIDED/STOCK_LINKED/RECORD_ONLY`。正式单结清后才能选择方式；仅记录直接完成且不操作库存，关联库存才走配货/出库。完整金额定义见 [财务契约](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md)。

### 6.4.1 订单编辑权限规则

客户、商品、数量和订单金额等结构内容仅允许在以下条件同时满足时修改：有 `btn:order:edit`，已迁移新模型，`CONFIRMED + UNDECIDED`，尚未结清，且没有核销、现金退款或销售退货。部分收款仍可改，但修改后应收不得低于当前净实收。

进入履约或结清后，不再开放结构编辑；备注、图片和档口变更按各自后端权限与校验处理。前端以 `allowedActions` 展示入口，后端必须再次校验，不能依靠隐藏按钮实现权限。

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

人工确认草稿 → 正式单 → 收款/结清 → 显式选择履约方式。

- `RECORD_ONLY`：完成，不预留、不生成配货计划、不扣库存。
- `STOCK_LINKED`：真实 SKU 配货、确认计划、出库发货、完成；占位 SKU 必须先拆分。
- 收款不等于出库；旧全局预留服务保留兼容，不再表示所有正式单付款都自动跨仓预留。
- 库存写入需事务、锁、幂等和审计；身份与写动作权限缺口见 [代码评估](./CODE_REVIEW_2026-11-02.md)。
- 完整订单回退、审批和库存逆向尚未实现，不能用修改状态代替逆向业务。

### 6.7 订单 API

列表/详情/新增/编辑分别为 `GET /api/orders`、`GET /api/orders/{id}`、`POST /api/orders`、`PUT /api/orders/{id}`。收款、结清、退款、流水冲销、履约方式、配货和发货使用独立动作接口，不提供客户端任意写主状态的通用接口。

当前动作路由见 [接口参考](./reference/API_SPEC.md)，精确请求体与权限以 `OrderController` 和对应 DTO 为准。权限和状态必须同时允许才能执行动作。

### 6.8 档口主数据与订单数据权限

- 档口建立独立主数据、稳定编码、启停状态和租户默认档口，不使用自由文本作为唯一真相。
- 用户与档口为多对多关系，并可设置个人默认档口；“全部档口”由权限表达，不要求逐条绑定。
- 档口范围与人员范围独立：`ALL/ASSIGNED/NONE × ALL_USERS/SELF`。
- 列表、详情、动作、草稿、统计、导出、订单图片和 Agent API 必须由后端统一应用同一数据范围；前端隐藏不构成权限控制。
- Agent Key 的业务 scope 控制“能做什么”，Key 的档口关联控制“能在哪些档口做”。
- 历史 `source_shop` 先生成映射和异常报告，再回填 `source_outlet_id`；疑似批次值不得自动创建为档口。
- 历史回填只在生产副本以显式人工映射执行：默认 dry-run，apply 需显式批准映射/租户/副本安全闸门并拒绝生产特征；只改 `source_outlet_id`，保留 `source_shop` 原值；前后对账不一致即回滚；`source_outlet_id` 保持可空，不做 NOT NULL。
- 初始用户-档口授权只产出建议与确认模板，无法判断一律 `NEEDS_REVIEW`，绝不自动授权。

完整数据库、接口、角色矩阵、迁移和验收规则见 [20-OUTLET_ACCESS_CONTROL_DESIGN.md](./20-OUTLET_ACCESS_CONTROL_DESIGN.md)。

---

## 七、客户模块（已实现，继续维护）

当前实体是 `customer` 与多号码 `customer_phone`，不是旧提案中的 `client` 表。已实现客户 CRUD、电话归一化/搜索、下单关联与自动建档、客户历史订单和偏好统计。已有字段与校验以 Entity/DTO 和 Flyway 为准。

“我的客户”按真实用户创建归属过滤，创建/修改/删除必须记录真实操作者；当前身份回退缺陷待修复，见 `BE-STAB-001`。客户交易统计遵循正式订单和统一财务口径。

客户分级、积分和完整应收账款等属于后续规划，不因为优化方案写过就视为已实现。现行范围见 [客户说明](./08-CUSTOMER_OPTIMIZATION.md)。

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
- 统计按正式订单、统一金额快照及对应业务时间口径查询；销售与现金统计不得混用，精确定义见 15 号财务契约。
- 销售额为应收净额：`max(total_amount - refund_amount - write_off_amount, 0)`。
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
- 统计按正式订单、统一金额快照及对应业务时间口径查询；销售与现金统计不得混用，精确定义见 15 号财务契约。
- 汇总销售额为应收净额：`max(total_amount - refund_amount - write_off_amount, 0)`。
- 汇总毛利为 `max(gross_profit - refund_amount - write_off_amount, 0)`。
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
| 沟通上下文扩展 | WhatsApp 本地归档 v1 已实现采集、导入、CRM 绑定和完整性工作台；后续接入 Agent 只读分析 |
| 纸单批量草稿 | 本机识别 Agent 经可配置 NAS API 查询 SKU 候选、上传对应纸单原图并提交结构化结果，批量建立可人工复核的订单草稿 |

### 10.2 第一版接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/agent/analytics/style-trends` | GET | 多周期款式趋势与建议依据 |
| `/api/agent/analytics/sku-mix` | GET | 款式颜色/尺码/SKU 结构事实 |
| `/api/agent/customers` | GET/POST | 分页读取客户资料或只新增客户；详情使用 `/{id}` |
| `/api/agent/tasks/follow-up` | GET | 需跟进客户清单与提醒依据 |
| `/api/agent/customers/risk` | GET | 客户流失风险与分层事实 |
| `/api/agent/inventory/recommendations` | GET | 库存积压、缺货和补货优先级事实 |
| `/api/agent/reports/periodic` | GET | 月度、季度、年度经营分析数据包 |
| `/api/agent/search` | GET | 客户、订单、商品/SKU 跨模块搜索 |

实现时优先复用 `/api/dashboard/*`、`/api/analytics/*` 和客户统计能力，统计口径不得与现有看板、数据分析页分叉。

### 10.3 安全与权限

1. Agent 使用独立凭证，不复用前端 JWT 登录态。
2. 每个 Agent 凭证必须绑定 `tenant_id` 和 scope，认证通过后进入当前多租户隔离链路。
3. Agent API 默认只读；已批准的窄范围写入只有“创建订单草稿”“新增商品”和“新增客户”，分别使用 `agent:orders:write`、`agent:products:create`、`agent:customers:create`。新增动作不得覆盖同编码商品或同电话客户，不得创建库存事实；Agent 仍不能直接创建/确认正式订单、修改/删除商品或客户、调整库存或确认收款。
4. 商品和正式订单原始事实分别使用 `agent:products:read`、`agent:orders:read`，必须分页并使用 Agent 专用脱敏 DTO；订单不返回电话、地址、成本和毛利，商品不返回成本价。客户列表与详情使用独立高敏 `agent:customers:read`，明确包含客户名称、电话、地址和备注，不能因拥有订单读取权限而自动获得。
5. 成本、毛利、毛利率需单独授权，默认不返回。
6. 客户新增必须记录实际 Agent Key 来源；普通用户和 Agent 的创建审计不得互相冒充。对外开放 Agent 前必须复核客户数据接口认证边界、Agent API 调用审计和限流。

### 10.4 暂缓范围

- 后端自然语言问答 `/agent/query` 暂缓，先由外部 Agent 调用结构化工具接口。
- 泛化写动作 `/agent/action` 暂缓；后续只允许经过明确授权的窄范围动作。
- 增量变化接口 `/agent/changes` 依赖统一业务事件日志，第一版不纳入验收。
- WhatsApp 的 Agent 分析和自动营销不纳入当前验收；本地归档 v1 先实现只读采集、结构化事实、客户映射和导入审计。
- 订单流程异常、利润解释、WhatsApp 反馈分析和经营记忆属于后续能力路线，按事件、权限和外部数据接入成熟度分阶段落地。

### 10.5 WhatsApp 本地归档 v1（2026-08-24）

> 详细实施契约见 [WhatsApp Mac 本地归档 ROM/SOW](./superpowers/plans/2026-08-24-whatsapp-local-archive-rom-sow.md)。

- 数据源为当前 WhatsApp Business Mac App，继续使用原号码；本阶段不接 Business Platform。
- 源 SQLite 和媒体目录永久只读，先生成一致性快照；原始归档位于 Git 外的加密本地目录。
- Blade MySQL 建立租户隔离的账号、批次、水位、联系人、CRM 绑定、会话、逻辑消息、源行引用和媒体事实表。
- v1 只接 1:1 私聊、联系人、文本和已下载媒体；排除群聊、状态、频道、广播、通话和自动发送。
- WhatsApp 联系人只生成 CRM 精确号码匹配候选，不自动创建或覆盖客户主档。
- CRM 号码匹配必须按 `country_code + 本地号码` 生成国际纯数字号码；ERP 客户晚于 WhatsApp 联系人创建或修改国家区号/号码后，绑定工作台需重新计算候选。只有同租户内唯一精确匹配才允许进入待确认列表。
- 绑定经人工确认后，WhatsApp 联系人才成为对应 ERP 客户的沟通上下文，并触发该客户的 Agent 分析任务。绑定页必须分开展示“待确认”和“已绑定”，明确说明聊天归档、ERP 订单联合分析和智能跟进的关系；绑定不得自动发送消息、修改订单或覆盖人工标签。
- Collector 写入使用独立凭证；Analysis Agent 只读 Gateway，营销动作必须由用户确认。
- 提供采集完整性工作台：识别 Mac 已有媒体消息但本地路径为空、文件不存在、仅缩略图或导入失败的记录；用户在 WhatsApp 中手动加载后可选择“扫描整个账号”或“仅扫描此客户”。客户扫描以真实号码为身份边界，同时覆盖该号码关联的 phone JID/LID 会话，不得处理或恢复其他客户的问题。
- 提供 ERP 只读聊天归档浏览：按真实号码合并同一客户的 phone JID/LID 会话，左右区分收发方向，并原位预览已归档图片、视频、音频、贴纸和文档；没有原文件的媒体必须显示缺失原因，不得伪装成可预览内容。打开会话默认停在最新消息，用户向上滚动时自动分页加载更早消息，追加旧消息后不得使当前阅读位置跳动。页面不得提供消息发送、修改或删除入口。
- 本地单端无法证明 iPhone 上完全没有同步到 Mac 的消息，界面不得把“未发现问题”表述为手机与 Mac 绝对完整一致。
- v0.2 已实现独立 Collector Key、分块幂等导入、媒体哈希归档、ERP 扫描任务和缺失媒体工作台；生产部署与 Agent 沟通分析仍按独立任务验收。

### 10.6 WhatsApp 混合 Agent 分析（2026-08-24）

> 详细实施契约见 [WhatsApp 混合 Agent 分析 ROM/SOW](./superpowers/plans/2026-08-24-whatsapp-hybrid-agent-analysis-rom-sow.md)。

- Mac 只负责确定性采集、标准化、去重、哈希和完整性检查，不承担长期客户画像。
- NAS/Blade 保存结构化聊天、客户绑定、ERP 事实、分析任务、画像和推荐；分析结果在 ERP 工作台统一展示。
- Agent 上下文以已确认 CRM 客户为边界，默认最近 90 天、最多 200 条消息；移除姓名、完整电话、邮箱、链接、地址和媒体原件。
- 云端与 NAS 本地模型通过同一任务契约接入；原始 SQLite、联系人全量、完整媒体和无关历史不得发送给云端。
- Agent 结果必须是结构化 JSON，包含偏好、意向阶段、风险、建议跟进时间、建议动作、置信度和证据消息 ID。
- Agent 只能生成建议；用户负责采纳、忽略或完成跟进，系统不得自动发送 WhatsApp 消息、修改人工标签或创建订单。

### 10.7 WhatsApp 客户详情工作区（2026-08-26）

- ERP 客户详情新增“WhatsApp”Tab，用户从客户档案直接查看该客户的绑定状态、同步状态、缺失媒体和聊天时间线，不需要先在独立归档页再次搜索号码。
- 服务端必须按当前租户和 `customerId` 返回 WhatsApp 工作区；已确认绑定才允许把聊天作为该 ERP 客户的沟通上下文。待确认匹配只能展示候选与确认入口，未匹配时显示号码检查指引。
- 已绑定客户显示 WhatsApp 联系人、真实号码、来源账号、最近消息、归档消息总数和待恢复媒体数量；支持安全打开对应 WhatsApp 聊天和“仅扫描此客户”。
- 聊天时间线复用只读归档规则：默认显示最新 50 条，向上滚动连续加载更早消息并保持阅读位置；原位预览图片、视频、音频、贴纸、文档和缺失占位，不得提供发送、修改或删除入口。
- 客户详情的定向扫描必须使用服务端返回的 accountId、真实号码和会话身份，不允许前端从客户名称或未规范化电话自行拼接扫描目标。
- NAS 生产接入按 [WhatsApp Mac 采集器接入 NAS 生产 ERP 方案](./superpowers/plans/2026-08-26-whatsapp-nas-production-integration-plan.md) 后置实施；本轮不得操作 NAS 或生产数据。

### 10.8 纸单识别 Agent 批量订单草稿（2026-08-27）

- 本机 Agent 完成纸单图片识别和 Excel/结构化数据整理后，通过可配置的 NAS 生产 API 查询商品/SKU 候选；有纸单图片时先逐张上传，再以 `sourceFileIds` 关联到批量草稿，全程不直接访问数据库。当前外网入口为 `https://www.chenjianas.asia:33294`，由本机 Key 元数据配置，不写死在业务请求中。
- 款号允许简写、潦草或暂时无法匹配；未匹配行仍可进入草稿，由人工选择 SKU、拆行或删除。
- 纸单识别出的数量、销售单价、行金额、整单总额和定金属于业务原值。系统商品价格只作醒目参考，禁止覆盖纸单销售单价。
- 无法匹配客户时默认“散客”；原始客户名、日期、定金、商品描述、价格及识别警告均需保留，便于对照原图。
- 草稿状态下不进入正式订单、库存、财务和经营统计。用户在 `/orders/drafts` 编辑保存并确认后，系统才幂等创建正式订单。
- 草稿工作台持续采用“左侧编辑、右侧原图”的并排核对布局；原图支持多页切换、清晰大图预览和隐藏。中屏不切换为抽屉，编辑、保存和切换明细时原图保持可见；商品表格在自身区域横向滚动。确认正式订单时，草稿关联的全部原图继承为订单图片。
- 批量写入以租户和 `externalRefNo` 幂等；重复提交返回既有草稿，不覆盖人工已修改内容。
- 纸单只有款号、没有颜色尺码时，Agent 优先选择该款的 `PLACEHOLDER` SKU；若已识别出颜色或尺码，则禁止候选返回占位 SKU。
- 分析系统将占位销量计入款号总量，并把它单列为“未指定”；颜色/尺码榜只比较真实规格，同时返回规格数据覆盖率和质量等级。
- 占位 SKU 不能直接配货或出库。人工必须先把占位数量转移到一个或多个真实 SKU，系统校验拆分前后数量与销售额守恒，并记录操作审计。
- 截至 2026-08-29，创建、匹配、草稿编辑、确认和分析隔离已完成本地验证；占位拆分、履约保护、NAS 发布和 30 单真实验收仍待完成。

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

## 十二、纸单识别与人工复核（现行）

纸单识别由外部 Agent 完成，ERP 提供商品候选、受控 Key、结构化批量草稿和人工确认。内置 OCR 引擎、旧“半自动/全自动”分阶段方案不再作为内部待开发主线；原方案见 [历史设计](./archive/designs/BUILTIN_OCR_ORIGINAL.md)。

草稿不计入正式收款、销量和库存，Agent 不得自动确认正式单。金额、客户、款号、占位 SKU 与档口归属必须人工复核。原图上传是可选证据；幂等键、最小 scope 和租户/档口校验必须保留。

代码和迁移已部署，真实 30 单验收仍待完成，不能把合成样本验证视为真实业务通过。字段与操作见 [草稿手册](./17-AGENT_ORDER_DRAFT_RUNBOOK.md) 和 [连接手册](./19-AGENT_CONNECTION_PLAYBOOK.md)。

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
| 列表优先加载派生图 | 商品列表、订单图片墙、文件中心网格、Catalog 卡片等列表/卡片场景应优先加载缩略图或中图，点击大图/下载时再加载原图 |

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

后续性能优化必须支持：

- 图片上传后生成派生图版本，至少预留 `thumb` 和 `card` 两种用途。
- 业务表仍只保存原始 `fileId`，不得保存缩略图路径或派生图路径。
- 派生图权限继承原图权限，私有文件的缩略图也必须经过统一鉴权。
- 历史图片允许后续批量补生成；派生图缺失时可兜底原图，避免页面不可用。
- 当前系统规模仍小时，派生图第一版应先打好架构脚手架：统一派生图服务、统一存储 Provider、统一 URL 工具、可表达状态的派生图表结构，避免后续接 NAS、七牛云、CDN 或异步队列时重构业务表和页面调用链。
- 脚手架只保留扩展点，不提前实现复杂外部能力；第一版仍以本地存储、`thumb/card`、同步或轻量异步生成、失败可回退原图为边界。
- 视频封面、转码、分片上传仍作为后续独立能力，不纳入图片派生图第一版。

### 13.4 客户选款与批发清单

现行入口 `/catalog`，保持登录和 `data:catalog:view` 权限；新版代码已接入，尚未发布 NAS。实现与验收详见 [客户选款说明](./21-CATALOG_WHOLESALE_DESIGN.md)。

| 能力 | 当前规则 |
|---|---|
| 页面 | 暖白、柔绿、紧凑直角画册；横屏三列，手机与 iPad 竖屏两列；批发清单全页编辑 |
| 商品相册 | 主图、已上传图集、SKU 图片按顺序去重；列表、详情、大图共享当前照片；无图不伪造 |
| 筛选 | 款号/名称搜索、分类、颜色、尺码、现货、有图、只看已选；读取实际 API |
| 可用现货 | 商品/SKU 展示扣除预留后的可选件数及颜色/尺码分项，现货不等于已为本单保留；超出现货可交店员确认交期 |
| 数字输入 | 触控设备数量/单价用页面内数字键盘；文字键盘收起后恢复视口，保持 pinch 缩放 |
| 手势 | 明确横向才锁定切图；上下和不明确斜向保留滚动；单次翻一张，首尾不循环；保留原生双指缩放 |
| 批发清单 | 每款一行：款号、颜色说明（混色/色数/具体颜色）、整款数量、可编辑客户单价、自动金额 |
| 金额 | 数量不因颜色数倍增；价格留空为待报价，显式 0 是零价；精确分单位计算，不写回商品价格 |
| 添加后 | 回到选款主列表，保留原筛选、位置和当前照片；不自动打开清单 |
| 移交 | 仅勾选行提交；需要 `btn:order:create` 和可用档口；进入现有订单草稿，店员核对客户及履约规格后开单 |
| 数据边界 | 用户确认客户可看商品/SKU 可用件数；不返回成本、供应商、毛利、原始库量或预留明细；报价仅本次输入，不直接产生正式订单、收款或库存变更 |
| 本机恢复 | 按租户与用户隔离保存选款和报价；刷新恢复，登出清除；不能跨设备同步或代替服务端草稿 |
| 后置 | 公开分享、客户扫码识别、行为埋点、原生 App 本轮不做 |

库存标记仍来自 `quantity - reservedQty - globalReservedQty`；展示页不读取群晖相册或设备本地相册。真机触控与完整业务验收单独跟踪，不以浏览器尺寸模拟视为通过。

---

## 十四、多租户设计

业务租户来自可信认证上下文，由 `TenantLineHandler` 对受管业务表自动加条件。缺失租户必须拒绝，不得回退租户 1。全局权限定义和少数无租户列关系表按代码白名单处理。

不存在“tenant_id=0 自动读取所有租户”的通用旁路。Owner 的跨档口能力限定在当前租户内；特殊后台工具需要自己的明确权限与范围。手写 SQL、统计、导出、文件和 Agent 都必须校验可信租户及档口/人员范围。

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
| v1.10 | 2026-06-17 | 收款状态口径调整：`payment_status=2` 表示已结清，新增抹零/短款结清 `write_off_amount` 规划 |
| v1.11 | 2026-06-21 | 锁定订单库存软解耦生产口径：收款/取消/减配不操作库存，配货库存仅软提示，确认发货统一按实际计划扣库；抹零结清正式落地 |
| v1.12 | 2026-08-24 | 锁定 WhatsApp Mac 本地归档 v1：只读快照、结构化事实表、CRM 人工确认绑定、独立导入凭证和 Agent 只读分析边界 |
| v1.13 | 2026-08-24 | 完成 WhatsApp Collector v0.2、V44 导入鉴权/扫描任务、消息媒体幂等入库、CRM 候选绑定和缺失媒体工作台 |
| v1.14 | 2026-08-24 | 锁定 WhatsApp 混合 Agent：Mac 确定性预处理、NAS 事实/分析中心、云端最小脱敏上下文和人工确认营销边界 |
| v1.15 | 2026-08-25 | WhatsApp 重扫拆分为账号全盘与单客户定向两种范围；定向扫描按真实号码覆盖 phone JID/LID，并只补传服务端缺失媒体 |
| v1.16 | 2026-08-25 | WhatsApp 绑定页区分待确认/已绑定并明示分析用途；聊天使用默认最新、向上连续加载的阅读方式 |
| v1.17 | 2026-08-26 | WhatsApp 整合进 ERP 客户详情：按 customerId 返回绑定工作区，直接查看完整只读聊天、缺失媒体并定向重扫；NAS 生产接入方案记录后置 |
| v1.18 | 2026-09-20 | 档口升级为正式主数据和数据权限边界：用户可绑定一个或多个档口，订单按档口范围与人员范围二维隔离，统计/导出/文件/Agent 统一应用后端范围 |

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

### 14.4 纸单识别决策更新

2026-03-23 的内置半自动 OCR 方案已被外部 Agent 识别→ERP 草稿→人工确认替代，旧方案只作历史参考。部分收款、短款结清与履约分离已经实施；当前以 `collection_status/fulfillment_mode/fulfillment_status` 和财务流水为准，旧 payment/status 字段仅兼容。

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

### 14.6 WhatsApp Mac 本地归档（2026-08-24）

**决策**：先以当前 WhatsApp Business Mac App 为只读数据源，建立 Git 外原始归档和 Blade 内结构化事实层；继续使用原号码，不接自动回复。

**原因**：
1. 当前本机数据已验证可只读提取，能满足先集中存档和分析的目标。
2. WhatsApp 私有 SQLite 可能变化，需要用快照、schema guard、源行映射和幂等重放隔离风险。
3. 聊天信息是沟通上下文，不应覆盖 ERP 客户、订单和人工标签等业务真相。

**规则**：
- 原始数据库和媒体只读，快照/真实内容不得进入 Git。
- v1 只接 1:1 联系人、会话、消息和已下载媒体；群聊、状态、发送能力后置。
- 不自动创建 CRM 客户；只有唯一精确号码可以产生待确认绑定候选。
- Collector 使用独立导入凭证，Agent 只通过 scoped Gateway 读取必要事实。
- Agent 只生成偏好与跟进建议，不自动执行营销。

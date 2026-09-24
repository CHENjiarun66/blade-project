# Agent 纸单 / Excel 批量订单草稿操作手册

> 本文档供执行纸单识别、Excel 整理和批量草稿录入的外部 Agent 阅读。
> 鉴权方式见 [11-AGENT_ACCESS_GUIDE.md](./11-AGENT_ACCESS_GUIDE.md)，本机 Key 选择与授权见 [16-AGENT_LOCAL_KEY_MANAGER.md](./16-AGENT_LOCAL_KEY_MANAGER.md)。
> Agent 尚未连接系统时，先按 [19-AGENT_CONNECTION_PLAYBOOK.md](./19-AGENT_CONNECTION_PLAYBOOK.md) 完成 MCP 配置和只读验收，再执行本手册。

---

## 一、不可突破的业务边界

1. 只允许查询商品候选和创建订单草稿，不允许确认正式订单、收款、退款、短款核销、配货、出库或改库存。
2. 纸单/Excel 中的数量、售卖单价、行金额、订单总额和定金优先；系统商品价格只作识别参考，不得覆盖纸单售价。
3. 客户无法可靠匹配时使用散客，不自动创建客户。
4. 未匹配、歧义和金额不一致必须保留原值并写入 warning，不能猜测后静默提交为已匹配。
5. 一张纸单对应一个稳定 `externalRefNo`。重试必须复用同一个值，不能通过改编号制造重复草稿。
6. 纸单图片存在时，必须先通过本机授权工具逐张上传，并把返回的 `fileId` 按页序写入该草稿的 `sourceFileIds`。纯 Excel 来源或原图确实缺失时仍允许创建草稿，但必须写入 `SOURCE_IMAGE_MISSING` warning，不能用备注中的文件名冒充已上传原图。
7. 默认不提交 `costPrice` 和 `freightCost`，由系统在人工确认正式订单时按商品/SKU 主档成本取值。只有来源数据确有明确成本、且 Key 同时拥有 `orders:cost:write` 时才能写入；不得把销售价、纸单金额或猜测值当作成本。
8. 每张草稿必须归属到 Key 可用的档口：优先使用运营方提供的稳定 `sourceOutletCode`；Key 已配置默认档口且来源不区分档口时可省略。禁止传内部 `sourceOutletId`，服务端会拒绝并要求改用 `sourceOutletCode`。

## 二、执行顺序

```text
读取图片/Excel
  → 逐张纸单分组
  → 调 blade_order_draft_source_upload 上传每张原图并记录 fileId
  → 逐行保留原始字段
  → 调 blade_catalog_search 查询候选
  → 按规则选择 DEFAULT / PLACEHOLDER / NORMAL 或保留未匹配
  → 校验行金额和订单总额
  → 先生成本地预览/摘要
  → 用户确认“可以创建草稿”
  → 调 blade_order_drafts_create（每批最多 100 单）
  → 汇总 CREATED / CREATED_WITH_WARNINGS / DUPLICATE / ERROR
  → 提醒用户到 /orders/drafts 人工复核
```

Agent 不应把“创建草稿成功”表述成“订单已完成录入”。只有用户在草稿工作台人工确认后才成为正式订单。

## 三、商品与 SKU 匹配

### 3.1 查询工具

使用 `blade_catalog_search`，对应 `GET /api/agent/catalog/skus`，可传 `keyword`、`productCode`、`colorName`、`sizeCode`、`limit`。

### 3.2 选择规则

| 纸单信息 | 应选 SKU | 页面语义 |
|----------|----------|----------|
| 商品本身没有颜色尺码 | `DEFAULT` / `NA-NA` | 无规格商品（实际 SKU） |
| 商品有规格，但纸单只有款号 | `PLACEHOLDER` / `UNSPEC*` | 整款录入（颜色/尺码未指定） |
| 纸单有可靠颜色/尺码 | `NORMAL` | 具体颜色尺码 SKU |
| 多个候选无法确定 | 不填 `skuId`，保留候选 | 待人工匹配 |
| 查不到可靠候选 | 不填 `productId/skuId` | 未匹配 |

不得因为只返回一个具体 `NORMAL` SKU 就替代纸单的“整款录入”语义；有规格商品但纸单未写规格时优先选择 `PLACEHOLDER`。不得把历史 `DEFAULT/NA-NA` 当成当前有规格商品的整款占位。

创建草稿接口还有一层服务端安全兜底：如果 Agent 漏传 `skuId`，但 `rawProductCode` 是精确款号，且没有明确颜色/尺码，服务端会自动选择该商品唯一的 `PLACEHOLDER`；商品本身没有规格时选择唯一的 `DEFAULT/NA-NA`。如果款号模糊、具体规格不唯一或候选存在歧义，服务端仍会保留 `UNMATCHED/AMBIGUOUS`，不会猜测。该兜底不能替代 `blade_catalog_search`，Agent 仍应先查询并回传可靠的 `productId`、`skuId` 和 `MATCHED`。

示例：纸单只写 `618-16`、没有颜色尺码时，应提交 `rawProductCode: "618-16"`、`rawColor: null`。不要把“无品名”“未指定颜色”伪造成某个具体颜色；服务端会把这种 SPU 级明细落到“整款录入（颜色/尺码未指定）”SKU，同时保留纸单的数量和销售单价。

一行中出现多个款号、斜杠分隔款号或无法判断数量如何分摊时，不自动拆分数量/金额。原行保留并标记人工处理。

## 四、批量请求字段

`blade_order_drafts_create` 的 `payload` 就是下面的 JSON 对象：

```json
{
  "orders": [
    {
      "externalRefNo": "paper-batch-33-0000471",
      "sourceBatchNo": "33",
      "sourceOrderNo": "0000471",
      "sourceOutletCode": "YL",
      "sourceFileIds": [9001, 9002],
      "rawCustomerName": "纸单原客户名",
      "rawCustomerPhone": "纸单原电话",
      "customerId": null,
      "customerName": "散客",
      "customerPhone": null,
      "rawOrderDate": "2026-08-01",
      "orderDate": "2026-08-01",
      "deliveryDate": null,
      "rawDeposit": "100",
      "deposit": 100.00,
      "paperTotalAmount": 1200.00,
      "note": "来源图片：0000471.jpg",
      "warnings": [],
      "items": [
        {
          "sourceRowNo": 2,
          "rawProductCode": "7000#",
          "rawDescription": "纸单原描述",
          "rawColor": "",
          "rawQuantity": "10",
          "rawSalePrice": "120",
          "rawAmount": "1200",
          "productId": 1001,
          "skuId": 2001,
          "quantity": 10,
          "salePrice": 120.00,
          "paperAmount": 1200.00,
          "systemReferencePrice": 118.00,
          "matchStatus": "MATCHED",
          "matchCandidates": [],
          "warnings": []
        }
      ]
    }
  ]
}
```

字段规则：

| 字段 | 规则 |
|------|------|
| `externalRefNo` | 必填；建议 `paper-batch-{批次}-{纸单号}`，同一来源永不变化 |
| `sourceBatchNo` | 必填；原册/批次号，最多 20 位。不要与纸单号拼成一个字段 |
| `sourceOrderNo` | 必填；纸单号，最多 29 位；不要用 Excel 行号替代已有纸单号 |
| `sourceOutletCode` | 可选；档口稳定编码。传入则草稿归属该档口，越权/禁用/不存在返回 `ERROR`；省略时使用 Key 的默认档口，无默认档口返回 `ERROR`。禁止传内部 `sourceOutletId`（返回 `ERROR`） |
| `sourceFileIds` | 纸单原图上传返回的 fileId 数组，按页序排列；最多 10 张。兼容字段 `sourceFileId` 只表示第一张主图，新接入统一使用数组 |
| `raw*` | 保存识别到的原始文本，即使无法解析也不丢失 |
| `orderDate` | 只有日期可靠时填写；原文本始终放 `rawOrderDate` |
| `customerId` | 只有可靠命中既有客户时填写；否则留空并使用散客 |
| `deposit` | 纸单已收定金；无法解析时留空，不把空值当 0 |
| `paperTotalAmount` | 纸单总额；与行合计不一致时仍保留纸单值并 warning |
| `quantity` | 来自纸单解析；不得使用系统数据推算 |
| `salePrice` | 来自纸单售卖单价；不得使用 `systemReferencePrice` 覆盖 |
| `costPrice` | 可选敏感字段；默认省略并由系统取主档成本。提交时需 `orders:cost:write`，不得根据售价推算 |
| `freightCost` | 可选敏感字段；默认省略。提交时需 `orders:cost:write`，与客户运费收入 `freightAmount` 分开 |
| `paperAmount` | 纸单行金额；与数量×单价不一致时保留并 warning |
| `productId/skuId` | 只有匹配可靠时填写 |
| `matchStatus` | 使用 `MATCHED`、`AMBIGUOUS` 或 `UNMATCHED` |
| `matchCandidates` | 歧义时保留候选，方便草稿工作台人工选择 |
| `warnings` | 金额差、字段缺失、多款号一行、低置信度等可读说明 |

草稿中批次和单号必须始终分开传入。人工确认生成正式订单时，系统会以 `sourceBatchNo + "_" + sourceOrderNo` 生成兼容的纸质单据号；Agent 不需要自行重复拼接。

## 五、金额校验

每张纸单至少计算：

```text
计算行金额 = quantity × salePrice
解析明细合计 = Σ paperAmount（存在时）
订单差额 = paperTotalAmount - 解析明细合计
```

处理原则：

- `paperAmount` 与数量×单价不一致：保留纸单行金额，并 warning；不要擅自改价或改数量。
- `paperTotalAmount` 与明细合计不一致：保留纸单总额，并 warning；草稿仍可创建。
- 空白、未识别和明确填写 0 是三种不同状态，不得统一转成 0。
- 定金只是草稿信息，创建草稿时不产生正式收款流水。

## 六、响应与重试

每单返回状态：

| 状态 | 含义 | 后续动作 |
|------|------|----------|
| `CREATED` | 草稿创建成功，无警告 | 进入人工复核 |
| `CREATED_WITH_WARNINGS` | 草稿创建成功，有待核对项 | 优先人工修正 |
| `DUPLICATE` | 同一 `externalRefNo` 已存在 | 不再次创建，定位已有草稿 |
| `ERROR` | 该单失败 | 记录错误，修正后只重试失败单 |

网络超时或 5xx 时允许有限次数退避重试。认证失败、scope 不足、参数错误不自动重试；401 应提示用户检查 Key 是否过期、停用或录入错误。

档口必须在每张草稿创建时确定。若 Key 有多个可用档口且未配置默认档口，未传 `sourceOutletCode` 会返回 `ERROR`（提示传稳定编码）；此时应先向运营方确认该单据对应的 `sourceOutletCode`，不要用相同请求反复重试。草稿详情/列表会返回 `sourceOutletId`、`sourceOutletCode` 和 `sourceShop` 供人工核对。

Series E3 起 Key 的档口范围由 Key Manager 显式配置：`ALL`（全部启用档口）/`ASSIGNED`（指定档口）/`NONE`（不开放）。`NONE` 的 Key 新建草稿必然 `ERROR`；`ASSIGNED` 只接受绑定集合内且启用的 `sourceOutletCode`。运营方可通过 `GET /api/agent/capabilities` 确认当前 Key 的 `outletScopeType`/`usableOutlets`；授予 `outlets:read` 后可调用 `GET /api/agent/outlets` 列出可用编码。Agent 始终使用 `outletCode`，不得缓存或猜测内部 `outletId`。

当前幂等实现对相同 `externalRefNo` 返回 `DUPLICATE`，不会覆盖已有草稿。若源数据发生变化，应由用户在草稿工作台修改，或明确删除/作废原草稿后使用新的受控流程，Agent 不得自行更换编号绕过幂等。

## 七、交付给用户的结果摘要

完成后至少报告：

- 来源文件、批次号、纸单数量和商品行数；
- `CREATED`、`CREATED_WITH_WARNINGS`、`DUPLICATE`、`ERROR` 数量；
- `MATCHED`、`AMBIGUOUS`、`UNMATCHED` 行数；
- 纸单总额、解析明细合计和差额；
- 是否执行了幂等重试；
- 原图应上传张数、成功张数，以及每张草稿关联的 fileId；
- 明确说明没有确认正式订单、没有产生收款、没有改库存；
- 草稿复核入口：`/orders/drafts`。

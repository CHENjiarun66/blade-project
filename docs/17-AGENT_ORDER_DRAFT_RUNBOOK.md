# Agent 纸单 / Excel 批量订单草稿操作手册

> 本文档供执行纸单识别、Excel 整理和批量草稿录入的外部 Agent 阅读。
> 鉴权方式见 [11-AGENT_ACCESS_GUIDE.md](./11-AGENT_ACCESS_GUIDE.md)，本机 Key 选择与授权见 [16-AGENT_LOCAL_KEY_MANAGER.md](./16-AGENT_LOCAL_KEY_MANAGER.md)。

---

## 一、不可突破的业务边界

1. 只允许查询商品候选和创建订单草稿，不允许确认正式订单、收款、退款、短款核销、配货、出库或改库存。
2. 纸单/Excel 中的数量、售卖单价、行金额、订单总额和定金优先；系统商品价格只作识别参考，不得覆盖纸单售价。
3. 客户无法可靠匹配时使用散客，不自动创建客户。
4. 未匹配、歧义和金额不一致必须保留原值并写入 warning，不能猜测后静默提交为已匹配。
5. 一张纸单对应一个稳定 `externalRefNo`。重试必须复用同一个值，不能通过改编号制造重复草稿。
6. 图片不是前置条件。可把原图文件名写进 `note`，但不要为了创建草稿强制上传图片。

## 二、执行顺序

```text
读取图片/Excel
  → 逐张纸单分组
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
| `sourceBatchNo` | 原册/批次号 |
| `sourceOrderNo` | 纸单号；不要用 Excel 行号替代已有纸单号 |
| `raw*` | 保存识别到的原始文本，即使无法解析也不丢失 |
| `orderDate` | 只有日期可靠时填写；原文本始终放 `rawOrderDate` |
| `customerId` | 只有可靠命中既有客户时填写；否则留空并使用散客 |
| `deposit` | 纸单已收定金；无法解析时留空，不把空值当 0 |
| `paperTotalAmount` | 纸单总额；与行合计不一致时仍保留纸单值并 warning |
| `quantity` | 来自纸单解析；不得使用系统数据推算 |
| `salePrice` | 来自纸单售卖单价；不得使用 `systemReferencePrice` 覆盖 |
| `paperAmount` | 纸单行金额；与数量×单价不一致时保留并 warning |
| `productId/skuId` | 只有匹配可靠时填写 |
| `matchStatus` | 使用 `MATCHED`、`AMBIGUOUS` 或 `UNMATCHED` |
| `matchCandidates` | 歧义时保留候选，方便草稿工作台人工选择 |
| `warnings` | 金额差、字段缺失、多款号一行、低置信度等可读说明 |

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

当前幂等实现对相同 `externalRefNo` 返回 `DUPLICATE`，不会覆盖已有草稿。若源数据发生变化，应由用户在草稿工作台修改，或明确删除/作废原草稿后使用新的受控流程，Agent 不得自行更换编号绕过幂等。

## 七、交付给用户的结果摘要

完成后至少报告：

- 来源文件、批次号、纸单数量和商品行数；
- `CREATED`、`CREATED_WITH_WARNINGS`、`DUPLICATE`、`ERROR` 数量；
- `MATCHED`、`AMBIGUOUS`、`UNMATCHED` 行数；
- 纸单总额、解析明细合计和差额；
- 是否执行了幂等重试；
- 明确说明没有确认正式订单、没有产生收款、没有改库存；
- 草稿复核入口：`/orders/drafts`。

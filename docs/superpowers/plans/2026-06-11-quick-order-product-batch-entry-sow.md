# SOW: PC 快速录单商品级批量 SKU 录入

> 面向 Claude Code / 外部开发 Agent 的实现说明。执行前必须先阅读根目录 `AGENTS.md`、`docs/SESSION_CONTEXT.md`、`docs/02-PRD.md`、`docs/03-TASKS.md`、`docs/reference/GIT_BRANCH_WORKFLOW.md`。

---

## 1. 背景

当前 PC 快速录单页 `/orders/quick` 的商品明细主要按单个 SKU 搜索添加。实际纸质订单录入常按商品款号操作：例如先录入 `6000` 这个商品，再一次填写该商品下多个颜色/尺码的数量。

现有流程会导致同一商品下多个 SKU 需要重复搜索和添加，多颜色订单录入步骤过多，不适合生产环境高频录单。

---

## 2. 目标

在 PC 快速录单页新增“按商品批量录入 SKU”能力：

1. 录单员搜索并选择商品款号/商品名称。
2. 系统展示该商品下所有正常状态 SKU。
3. 录单员在 SKU 矩阵中直接填写各颜色/尺码数量。
4. 点击“添加到订单”后，将数量大于 0 的 SKU 一次性加入现有订单明细。
5. 相同 `skuId` 重复添加时自动合并数量，不新增重复行。

第一版重点是提高订单录入效率，不做库存联动。

---

## 3. 强约束

- 只改 PC 管理端第一版；后端原则上不改，除非现有商品接口无法满足正常 SKU 数据返回。
- 快速录单与库存功能暂时解耦。
- 不读取库存数量。
- 不展示库存数量。
- 不按库存过滤 SKU。
- 不校验库存是否足够。
- 不因为库存为 0、未建库存记录、库存模块数据不完整而影响录单。
- SKU 展示条件只看 SKU 自身状态：正常状态 SKU 可录入，停用/删除 SKU 不展示。
- 不改订单创建 API 数据结构，继续提交 `items: [{ skuId, quantity, price, costPrice }]`。
- 不改变现有订单保存、客户创建、运费、毛利、图片上传、保存并录下一单逻辑。

---

## 4. 用户流程

### 4.1 主流程

1. 打开 `/orders/quick`。
2. 在商品明细区顶部的“按商品批量添加”区域输入商品款号/商品名，例如 `6000`。
3. 选择商品后，显示该商品下所有正常 SKU。
4. 在颜色/尺码矩阵或列表中填写数量。
5. 点击“添加到订单”。
6. 系统将数量大于 0 的 SKU 添加到下方订单明细表。
7. 下方明细表继续用于复核、修改数量、修改单价、修改成本价和删除行。

### 4.2 重复添加规则

如果订单明细中已经存在相同 `skuId`：

- 自动合并数量。
- 不新增重复行。
- 不覆盖原明细中的 `price`。
- 不覆盖原明细中的 `costPrice`。

如果是同一个商品但不同 SKU：

- 正常新增明细行。

添加后提示：

```text
已添加 X 个 SKU，合并 Y 个重复 SKU
```

---

## 5. UI 建议

在当前“商品明细”卡片中增加上半区：

```text
按商品批量添加

[搜索款号 / 商品名              ]

商品：6000# / 商品名称
默认单价：xxx   默认成本：xxx

颜色 / 尺码      S      M      L      XL
黑色             [ ]    [2]    [ ]    [1]
白色             [1]    [ ]    [ ]    [ ]
红色             [ ]    [ ]    [3]    [ ]

[清空数量] [添加到订单]
```

下半区保留当前明细表：

```text
已添加订单明细
SKU / 数量 / 单价 / 成本价 / 小计 / 成本 / 毛利 / 操作
```

如果商品 SKU 不是完整颜色 x 尺码矩阵，可接受第一版使用分组列表：

```text
颜色：黑色
- S [数量]
- M [数量]

颜色：白色
- S [数量]
- L [数量]
```

但优先推荐矩阵，因为纸质订单录入效率更高。

---

## 6. 数据与实现建议

### 6.1 现有可复用结构

当前 `blade-admin/src/views/orders/quick.vue` 已有：

- `form.items: QuickLine[]`
- `QuickLine.skuId`
- `QuickLine.quantityText`
- `QuickLine.quantity`
- `QuickLine.price`
- `QuickLine.costPrice`
- `lineSubtotal`
- `lineCost`
- `lineProfit`
- `submit`

当前 `blade-admin/src/api/product.ts` 已有：

- `getProductPage(params)`
- `ProductVO.skus`
- `ProductSku.id`
- `ProductSku.skuCode`
- `ProductSku.colorName`
- `ProductSku.sizeName`
- `ProductSku.price`
- `ProductSku.costPrice`
- `ProductSku.status`

### 6.2 推荐新增前端状态

示例命名仅供参考：

```ts
const productSearchKeyword = ref('')
const productOptions = ref<ProductVO[]>([])
const selectedProduct = ref<ProductVO | null>(null)
const skuQuantityMap = reactive<Record<number, string>>({})
```

### 6.3 SKU 过滤

```ts
const activeSkus = computed(() =>
  (selectedProduct.value?.skus || []).filter(sku => sku.status === 1)
)
```

注意：

- 不得引用库存字段。
- 不得判断库存数量。
- 不得调用库存接口。

### 6.4 添加到订单逻辑

伪代码：

```ts
function addSelectedProductSkusToOrder() {
  let added = 0
  let merged = 0

  for (const sku of activeSkus.value) {
    const quantity = Number(skuQuantityMap[sku.id] || 0)
    if (quantity <= 0) continue

    const existing = form.items.find(item => item.skuId === sku.id)
    if (existing) {
      const currentQuantity = getLineQuantity(existing)
      const nextQuantity = currentQuantity + quantity
      existing.quantity = nextQuantity
      existing.quantityText = String(nextQuantity)
      merged += 1
      continue
    }

    form.items.push({
      skuId: sku.id,
      skuCode: sku.skuCode,
      productCode: selectedProduct.value?.productCode,
      productName: selectedProduct.value?.name,
      colorName: sku.colorName,
      sizeName: sku.sizeName,
      quantity,
      quantityText: String(quantity),
      price: sku.price || selectedProduct.value?.wholesalePrice || 0,
      costPrice: sku.costPrice || selectedProduct.value?.costPrice || 0,
    })
    added += 1
  }

  ElMessage.success(`已添加 ${added} 个 SKU，合并 ${merged} 个重复 SKU`)
}
```

合并时不改 `price` 和 `costPrice`。

---

## 7. 验收标准

### 7.1 功能验收

- 搜索商品 `6000` 后可选择商品。
- 选择商品后展示该商品所有正常状态 SKU。
- SKU 是否展示不受库存数量影响。
- SKU 没有库存记录时仍可展示和录入。
- 填写多个 SKU 数量后，点击“添加到订单”可一次性生成多条明细。
- 数量为空或 0 的 SKU 不添加。
- 同一 `skuId` 第二次添加时合并数量，不新增重复行。
- 合并数量时不覆盖已有行的单价和成本价。
- 商品明细下方金额汇总实时正确更新：商品应收、商品成本、毛利、订单应收、尾款。
- 保存订单时仍提交现有 `createOrder` 结构，后端可正常创建订单。

### 7.2 回归验收

- 原有保存、保存并录下一单仍可用。
- 散客默认逻辑仍可用。
- 客户名称下拉筛选仍可用。
- 国家区号选择仍可用。
- 来源档口默认 `御龙` 仍可用。
- 订单图片上传仍可用。
- 现有单 SKU 明细修改和删除仍可用。

---

## 8. 建议测试命令

前端类型与构建：

```bash
cd blade-admin
npm run build
```

建议补充浏览器手工验证：

1. 进入 `/orders/quick`。
2. 搜索并选择一个有多个颜色/尺码 SKU 的商品。
3. 批量填写多个 SKU 数量并添加。
4. 再次选择同一商品，填写一个已存在 SKU 数量，确认数量合并。
5. 修改已存在明细行的单价/成本价，再重复添加同 SKU，确认只合并数量、不覆盖价格。
6. 保存订单，确认订单创建成功。

---

## 9. 预期修改文件

主要文件：

- `blade-admin/src/views/orders/quick.vue`

可能涉及：

- `blade-admin/src/api/product.ts`：仅在类型字段不完整时补充类型定义。
- `docs/03-TASKS.md`：开发完成后将 `BA-207` 状态更新为完成。
- `docs/05-CHANGELOG.md`：开发完成后记录实现变更和验证结果。

不应修改：

- 后端订单创建接口，除非发现现有接口无法接收当前 `items` 结构。
- 库存服务、库存接口、库存表结构。
- 订单状态机。

---

## 10. Agent 交付要求

Claude Code 完成后需汇报：

- 当前分支。
- 修改文件列表。
- 关键实现说明。
- 是否改变后端接口。
- 是否读取/依赖库存接口。
- 测试命令和结果。
- 是否存在未完成事项。

必须明确确认：

```text
本次快速录单商品级批量添加不依赖库存数量，不按库存过滤 SKU。
```

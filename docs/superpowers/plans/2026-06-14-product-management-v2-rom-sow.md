# ROM/SOW: 商品管理 v2 - SKU 精细维护与商品素材管理

> 面向 Claude Code / 外部开发 Agent 的实现规划。执行前必须先阅读根目录 `AGENTS.md`、`docs/SESSION_CONTEXT.md`、`docs/02-PRD.md`、`docs/03-TASKS.md`、`docs/reference/GIT_BRANCH_WORKFLOW.md`。

---

## 1. 背景

商品模块第一版已经完成商品 CRUD、颜色/尺码/SKU 自动生成、商品主图上传和基础列表管理。当前生产使用的主要缺口不是“能不能建商品”，而是：

1. SKU 不能在商品页逐行维护售价、成本价、条码、状态和图片。
2. 商品图集、SKU 图片虽然有文件中心绑定能力，但商品编辑页内没有直接入口。
3. 商品图片会服务 PC 后台、快速录单、文件中心和 iPad Catalog，后续图片数量增加后需要派生图性能优化。
4. 商品/SKU 删除规则需要明确保护历史订单、库存和文件绑定，避免生产数据被误删。

---

## 2. 目标

商品管理 v2 第一版目标：

- 管理员在商品模块内即可完成商品基础信息、颜色尺码、SKU 明细和商品素材维护。
- SKU 支持逐行维护：售价、成本价、条码、状态、SKU 图片。
- 商品素材支持：主图、商品图集、SKU 图片；底层继续走统一文件中心和 `file_business_bind`。
- 删除商品/颜色/尺码/SKU 前能识别引用风险，优先禁用而不是破坏历史数据。

本规划不要求一次完成图片派生图底座，但实现商品素材 UI 时必须预留 `fileVariantUrl` 接入点。

---

## 3. 强约束

- PRD 是唯一依据：以 `docs/02-PRD.md` 的 4.8 商品管理 v2 为准。
- 不新增商品图集字段，不新增 SKU 图片字段；商品图集和 SKU 图片统一走 `file_business_bind`。
- `product.image_url` 只继续保存商品主图原始 fileId，兼容历史 URL。
- 不破坏订单历史快照；订单明细仍以 `sale_order_item` 冗余字段为历史真相。
- 不物理删除有历史订单、库存或有效文件绑定引用的商品/SKU/颜色/尺码/分类。
- 不把供应商 CRUD 纳入本轮；供应商模块后置。
- 图片预览必须走统一工具，不手写裸 `/api/files/{id}/preview`。
- 代码风格遵守现有 `blade-admin` 和 `blade-backend` 模式，先做业务闭环，不提前抽象通用组件。

---

## 4. 建议开发阶段

### Phase A: 后端能力补齐

任务：

- `BE-1013` 商品素材查询 API。
- `BE-1014` 商品/SKU 删除引用保护验收。

建议接口：

```text
GET /api/products/{id}/file-bindings
```

返回建议：

```json
{
  "main": { "fileId": 1, "previewUrl": "..." },
  "gallery": [
    { "fileId": 2, "previewUrl": "...", "sort": 0 }
  ],
  "skuImages": [
    {
      "skuId": 101,
      "files": [
        { "fileId": 3, "previewUrl": "...", "sort": 0 }
      ]
    }
  ]
}
```

可复用现有：

- `PUT /api/products/{id}/file-bindings`
- `GET /api/files/{id}/bindings`
- `file_business_bind`
- `FileService` preview token 机制

删除保护建议：

- 商品存在订单明细、库存记录、库存现存量、有效文件绑定时，`DELETE /api/products/{id}` 返回 400，并提示“存在历史引用，请禁用商品”。
- 颜色/尺码存在商品关联或 SKU 引用时，删除前拦截。
- SKU 已被订单或库存引用时，不物理删除；颜色/尺码移除导致 SKU 不再属于当前矩阵时，应软删除或禁用。

### Phase B: 商品编辑页 v2 信息架构

任务：

- `BA-407` 商品编辑页 v2 信息架构。

建议 UI：

```text
商品编辑

[基础信息] [颜色尺码] [SKU 明细] [商品素材]
```

第一版可继续使用弹窗，但如果内容拥挤，优先改独立页面：

```text
/products/:id/edit
```

页面要求：

- 基础信息保留现有字段。
- 颜色尺码区域继续驱动 SKU 自动生成。
- SKU 明细和商品素材不要挤在基础信息表单中。
- 保存按钮清晰区分“保存基础信息”和“保存素材/绑定”，避免一次提交失败丢失所有编辑。

### Phase C: SKU 明细精细维护

任务：

- `BA-408` SKU 明细精细维护。

SKU 明细表列建议：

| 列 | 说明 |
|----|------|
| SKU 编码 | 只读 |
| 颜色 | 只读 |
| 尺码 | 只读 |
| 售价 | 可编辑 |
| 成本价 | 可编辑 |
| 条码 | 可编辑 |
| 状态 | 启用/禁用 |
| 图片 | 缩略图 + 管理入口 |

关键规则：

- 商品级进货价/批发价变化不自动覆盖 SKU 已维护价格。
- 如需要批量同步价格，必须显式按钮和二次确认。
- 禁用 SKU 后，快速录单和 Catalog 不展示；历史订单不受影响。

### Phase D: 商品素材内聚到商品页

任务：

- `BA-409` 商品素材管理内聚到商品页。

能力：

- 商品主图：上传、预览、替换、清空。
- 商品图集：多图上传、预览、排序、移除。
- SKU 图片：每个 SKU 行内管理图片，支持上传和绑定已有文件。
- 从文件中心选择已有文件：第一版可复用现有 `FileBindDialog` 思路；如果文件选择器过大，可先支持上传，再补“选择已有文件”。

提交方式：

- 主图、图集、SKU 图片最终调用 `PUT /api/products/{id}/file-bindings`。
- 素材保存成功后刷新商品素材查询 API。

### Phase E: 删除/禁用交互

任务：

- `BA-410` 商品删除/禁用交互优化。

前端规则：

- 点击删除前先调用后端删除接口或引用检查接口。
- 若后端提示存在历史引用，弹窗明确展示风险，并提供“改为禁用”操作。
- 删除确认文案避免使用“删除后无法恢复”这种与软删除事实冲突的描述。

### Phase F: 图片派生图性能优化

关联任务：

- `BE-1012`
- `BA-1007`
- `BA-1028`

建议在商品 v2 UI 完成后执行。商品 v2 中图片展示函数先集中封装，后续可从 `filePreviewUrl(fileId)` 平滑切到 `fileVariantUrl(fileId, 'thumb' | 'card')`。

---

## 5. ROM 预估

| 阶段 | 复杂度 | 预估工作量 | 风险 |
|------|--------|------------|------|
| Phase A 后端素材查询 + 删除保护 | M | 1-2 天 | 引用检查涉及订单、库存、文件绑定，需避免误拦截 |
| Phase B 商品编辑页 v2 信息架构 | M | 1-2 天 | 现有弹窗可能承载过重，可能需要改路由页 |
| Phase C SKU 明细精细维护 | M/L | 2-3 天 | 后端现有 update 会同步 SKU 价格，需避免覆盖手动维护值 |
| Phase D 商品素材内聚 | L | 3-5 天 | 多文件上传、排序、SKU 图片绑定、预览状态较多 |
| Phase E 删除/禁用交互 | S/M | 1 天 | 后端错误码和前端提示需要一致 |
| Phase F 派生图性能优化 | L | 3-5 天 | 图片处理、权限继承、历史兜底和前端缓存都需测试 |

建议第一版交付范围：

```text
Phase A + Phase B + Phase C + Phase D 的上传/绑定闭环 + Phase E
```

派生图性能优化作为第二个开发批次，避免商品页改造和图片处理风险叠加。

---

## 6. 验收标准

### 后端

- 商品素材查询能返回主图、图集、SKU 图片。
- `PUT /api/products/{id}/file-bindings` 继续支持 main/gallery/sku_image 替换语义。
- 商品/SKU/颜色/尺码/分类删除存在引用时返回可读错误。
- 后端测试覆盖商品文件绑定、删除保护、SKU 同步不覆盖手动价格。

### 前端

- 商品编辑页能维护基础信息、颜色尺码、SKU 明细和商品素材。
- SKU 售价/成本价/条码/状态修改后保存生效。
- SKU 禁用后快速录单不展示该 SKU。
- 商品图集和 SKU 图片保存后，Catalog 可读取展示。
- 没有图片时有空状态；图片加载失败不阻塞保存。
- PC 1366px 宽度下表格和素材区域不拥挤，文本不重叠。

### 回归

- 创建新商品后 SKU 自动生成。
- 编辑已有商品不破坏历史订单展示。
- 快速录单选择商品时仍能显示正常 SKU。
- 文件中心绑定商品/SKU 图片仍可用。
- `cd blade-admin && npm run build` 通过。
- `cd blade-backend && mvn test` 或相关 product/file 测试通过。

---

## 7. Claude Code 执行提示

建议 Claude Code 先做只读调研：

1. 阅读 `blade-admin/src/views/products/index.vue`。
2. 阅读 `blade-admin/src/views/files/FileBindDialog.vue`。
3. 阅读 `blade-backend/src/main/java/com/blade/product/service/impl/ProductServiceImpl.java`。
4. 阅读 `blade-backend/src/main/java/com/blade/file/service/impl/FileBindingServiceImpl.java`。
5. 对照 `docs/02-PRD.md` 4.8 和 `docs/03-TASKS.md` 新增任务。

执行时按阶段提交，不要一次性改完整文件中心、Catalog 和商品页。


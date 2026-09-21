# Series D 交付报告：订单与草稿档口交互

> 分支：`feature/outlet-access-control`　基线：`e8137b9`
> 执行：DeepSeek（`[dsh]`）
> 范围：BE-OUTLET-007 / BA-OUTLET-003 / BA-OUTLET-004 / TEST-OUTLET-002；不进入 Series E/F/G，不 push/deploy/NAS/生产。
> 依据：[20-OUTLET_ACCESS_CONTROL_DESIGN.md](../../20-OUTLET_ACCESS_CONTROL_DESIGN.md)、ROM/SOW Series D、Series C 交付报告。

---

## 0. 提交

| commit | 内容 |
|---|---|
| `8071dc0` | `feat(outlet): enforce concrete order outlet and audited change (BE-OUTLET-007) [dsh]`（后端：迁移 V66、DTO、策略、写入规则、17 例集成测试） |
| `7759a67` | `feat(outlet): add order/draft outlet selectors and list filters (BA-OUTLET-003/004) [dsh]`（前端：OutletSelect、四个页面接入、列表筛选、e2e） |
| 本文档所在 commit | 文档与状态同步（TASKS/CHANGELOG/SESSION_CONTEXT/本报告/ROM-SOW/API_SPEC） |

---

## 0.1 Codex Series D 终审整改记录（2026-09-21）

针对 Codex 对 Series D 的终审发现，追加整改 commit `795800c`（代码/测试/e2e）与本报告所在文档 commit：

| 编号 | 问题 | 修复 | 测试 |
|---|---|---|---|
| P0/P1-1 | 前端档口 options 永久缓存：完成后的 Promise 不释放，退出换账号/档口增删停用后仍可能读到旧账号或旧数据 | `outletOptions.ts` 改为“只对并发中的请求去重，不持久缓存已完成结果”：请求 settle 后仅当槽位仍指向本次 Promise 才清空；`force` 先清空再请求，旧请求的 `finally` 因身份不匹配不会清掉新请求。新增无业务依赖的 `utils/outletOptionsCache.ts` 承载缓存槽，`client.clearAuthState` 与 `stores/auth.logout` 调用 `resetOutletOptionsCache` 作为纵深防御（避免 client/auth ↔ api 循环依赖） | Playwright `e2e-order-outlet.spec.ts` › `BA-OUTLET-003 档口 options 不持久缓存：跨页面重挂载重新拉取真实结果`（两次不同 options 响应，断言第二次请求发生且新选项生效） |
| P1-2 | 草稿列表真实契约缺 `sourceShop`：`OrderDraftDTO.Summary` 与 `toSummary` 未返回名称，前端 `OrderDraftSummary` 与 `draft-list.vue` 依赖 `row.sourceShop`，远端只回编码 | `OrderDraftDTO.Summary` 新增 `sourceShop`，`OrderDraftService.toSummary` 写入 `draft.getSourceShop()`（服务端名称快照）；前端 `OrderDraftSummary` 补 `sourceShop` | 后端 `OrderOutletWriteRulesTest.draftPage_filterByOutlet_andUnassignedPermission`、`OrderDraftOutletAttributionTest.viewAndSummary_exposeOutletIdAndCode` 对真实 page Summary 断言 `sourceOutletId`/`sourceOutletCode`/`sourceShop`；Playwright 草稿列表用例断言正常行显示“御龙” |
| P1-3 | 交付文档失真：误称订单导出“仍受统一读范围保护”；实际 `OrderServiceImpl.exportOrders` 只加 tenant + 页面筛选，未调用 `applyReadPredicate` | 保留 Summary 三字段陈述；将导出准确记录为**现存全出口安全缺口**（Series E 必须修复），明确本 feature 分支在 Series E 完成前不得部署；修正测试文件 EOF 多余空行 | `git diff --check e8137b9..HEAD` 无输出；全量后端回归 |

验证（整改 commit）：后端 `mvn test` **650/650**，Failures 0、Errors 0、Skipped 0；前端 `npm run build` 通过；Playwright `e2e-order-outlet.spec.ts` **6 passed**，相关 e2e 回归通过。

---

## 1. 迁移 V66：订单改档口高权限

`V66__order_change_outlet_permission.sql`：
- 新增 `btn:order:changeOutlet`（type=2，挂在 `menu:order`），仅授予 `ROLE_OWNER` / `ROLE_ADMIN`。
- 幂等：`ON DUPLICATE KEY UPDATE` 恢复 `status/deleted/tenant`，沿用 V64/V65 语义；不新增权限类型。
- 不修改历史迁移，仅追加。

## 2. BE-OUTLET-007 正式订单/草稿写入规则

新建正式订单（`OrderServiceImpl.create`）：
- 必须有具体档口：显式 `sourceOutletId` → 稳定 `sourceOutletCode` → 统一默认优先级（个人/租户/唯一可用）；仍不能确定返回 `400 请选择档口`。
- `data:outlet:unassigned` 只用于历史空值读取/草稿待归档，**不能新建空档口正式订单**。
- `source_shop` 一律由 `sales_outlet.outlet_name` 生成，忽略客户端 `sourceShop`；`sourceBatchNo` 不得兜底。

返回契约：
- `OrderVO` 返回 `sourceOutletId`、`sourceOutletCode`（编码派生自主数据，不冗余落订单表）、`sourceShop`（名称快照）。
- `OrderCreateDTO` 新增 `sourceOutletCode`；`OrderUpdateDTO` 新增 `sourceOutletId` + `outletChangeReason`；`OrderPageDTO` 新增 `sourceOutletId` + `unassignedOnly`。

改档口（高风险，本轮落地）：
- 未传 `sourceOutletId` = 保持原值；传入与原值相同 = 不写审计。
- 变更或历史 NULL 归档必须拥有 `btn:order:changeOutlet`（迁移 V66，仅 OWNER/ADMIN，后端强制，不依赖前端隐藏）；目标档口必须同租户、未删、启用且当前可写。
- 必须填写非空原因；同一事务向 `order_outlet_change_log` 追加旧/新 ID、名称、原因、操作者。
- 历史 NULL 归档额外要求 `data:outlet:unassigned`。
- 已发货/已完成订单保留“仅备注/图片”约束，显式改档口作为独立高权限例外；金额、明细、其他字段不被放开。
- `source_shop` 更新始终来自主数据；自由文本 `sourceShop` 不再生效。

列表筛选（订单 + 草稿）：
- `sourceOutletId` 只能筛当前 `readable` 集合；伪造/越权返回 `403`（不静默空结果）。
- `unassignedOnly=true` 仅 `data:outlet:unassigned` 可用，与 `sourceOutletId` 互斥（`400`）。
- “全部档口”不传筛选，仍由统一档口 × 人员范围在分页前 SQL 裁剪。

草稿：
- 保持 Series C 的 Agent `sourceOutletCode` / PC `sourceOutletId` 契约与 create/update 规则；确认沿用 `sourceOutletId` + 服务端名称快照，空档口确认阻断。
- 草稿列表新增 `sourceOutletId` / `unassignedOnly` 结构化筛选；`View`/`Summary` 已返回 ID/编码/名称。

## 3. BA-OUTLET-003 档口选择器

- 新增共享 `blade-admin/src/components/OutletSelect.vue` 与 `src/utils/outletOptions.ts`（模块级缓存，一页一次请求）。
- 统一调用 `GET /api/outlets/options` 结构化契约：
  - `locked=true` / 单可用档口：只读显示档口名称（编码提示），不出现可切换下拉。
  - 多档口：仅展示 `items` 中授权且启用项，默认选中 `defaultOutletId`。
  - Owner/Admin ALL：展示本租户全部启用档口，一单只选一个。
  - 无可用档口 / 无默认且未选：前端阻止提交并提示“请选择档口”。
  - 草稿历史 `sourceOutletId=NULL` 且有 `data:outlet:unassigned`：显示“待归档档口（请选择）”，确认前必须补齐。
- 接入页面：`orders/quick.vue`、`orders/new.vue`、`orders/drafts.vue`（草稿详情）、`orders/index.vue`（订单编辑弹窗，改档口需原因）。
- 页面不再出现可编辑 `sourceShop` 文本框；详情/只读展示名称。
- 快速录单“添加到草稿”和直接生成正式订单都提交 `sourceOutletId`；图片/金额/商品原有流程未回归。
- 草稿切换子单/首次加载时档口值稳定（按载入值仅在变更时提交，未变更不重复送已禁用档口）。
- 未建立全局档口切换器。

## 4. BA-OUTLET-004 列表筛选与待归档

- 正式订单列表与草稿列表新增档口筛选，使用同一 options；“全部档口”= 当前用户全部可读档口。
- 仅 `data:outlet:unassigned` 时显示“待归档档口”筛选项；历史空值行显示克制但醒目的“待归档档口”标签。
- 普通销售员不显示、后端也不允许按未授权/空档口筛选（403）。
- 查询/重置/分页保持筛选条件；详情页来源档口显示名称，历史空值显示“待归档档口”，不以批次代替。

## 5. TEST-OUTLET-002 测试

后端真实集成测试 `OrderOutletWriteRulesTest`（**17 例**，真实隔离库）：
- 新建：显式授权档口成功且 `source_shop` 用主数据覆盖伪造文本；未传可解析默认成功；无默认失败且不落库；禁用/跨租户/未授权 ID 失败；拥有 unassigned 也无默认仍拒绝，正式订单永不新写 NULL。
- 列表：按 `sourceOutletId` 筛选成功；越权筛选 403；待归档仅授权者可筛；两者互斥 400。
- 改档口：无权限/无原因拒绝且不写审计；正常变更写审计并刷新名称；同值不写审计；历史 NULL 归档需 unassigned 并写审计；已完成订单允许高权限改档口但金额不变；自由文本 `sourceShop` 失效。
- 草稿：按档口筛选、待归档权限、越权 403。
- 迁移：`btn:order:changeOutlet` 仅 OWNER/ADMIN。

Playwright `e2e-order-outlet.spec.ts`（**5 例**）：
- 快速录单单档口只读、多档口选择并提交 `sourceOutletId`；
- 草稿列表档口/待归档筛选与待归档标签；
- 正式订单列表档口/待归档筛选与待归档标签；
- 真实后端伪造档口创建正式订单返回 403。

回归：`e2e-outlet.spec.ts`（3）、`e2e-quick-order-draft.spec.ts`（1）、`e2e-manual-draft-confirm.spec.ts`（1）全部通过（含更新后的新契约 mock）。

## 6. 验证

```bash
cd blade-backend && mvn test                                   # 650/650
cd blade-admin && npm run build                                # vue-tsc + vite 通过
npx playwright test e2e-order-outlet.spec.ts e2e-outlet.spec.ts \
  e2e-quick-order-draft.spec.ts e2e-manual-draft-confirm.spec.ts   # 11 passed
```

- 全量后端 **650/650**，Failures 0、Errors 0、Skipped 0。
- 前端 `npm run build` 通过。
- Series D Playwright **6 passed**；相关既有 e2e **5 passed**（合计 11）。

## 7. 已知限制与安全缺口（临时）

- Series E/F/G 未做：统计/导出/文件/Agent 全出口档口范围、历史生产回填、非空约束与旧权限下线、兼容期复盘。
- **【现存全出口安全缺口 · Series E 必须修复】** `OrderServiceImpl.exportOrders` 目前只按 `tenant_id + deleted + 页面字段筛选` 查询，**没有调用** `OrderAccessPolicy.applyReadPredicate`，也没有显式档口筛选；拥有 `btn:order:export` 的越权用户可导出当前租户其他档口订单。**本 feature 分支在 Series E 完成导出范围收口前不得部署。**
- Agent 仍无档口选项查询接口（`GET /api/agent/outlets` 属 Series E）；当前 Key 依赖默认档口或运营方提供的 `sourceOutletCode`。
- 历史 `source_outlet_id` 为空的数据未回填，按“待归档档口”处理。

# Series E2 交付报告：文件中心与订单/草稿图片档口权限闭环

> 分支：`feature/outlet-access-control`　基线：`a60da1b`
> 执行：DeepSeek（`[dsh]`）
> 范围：BE-OUTLET-009（文件部分）、TEST-OUTLET-003（文件出口）；不进入 Series E3，不 push/deploy/NAS/生产。
> 依据：[20-OUTLET_ACCESS_CONTROL_DESIGN.md](../../20-OUTLET_ACCESS_CONTROL_DESIGN.md)、ROM/SOW Series E、Series E1 交付报告。

---

## 0. 提交

| commit | 内容 |
|---|---|
| `5280817` | `fix(outlet): enforce outlet scope on file center and order/draft images [dsh]`（后端策略、全出口接入、14+1 例测试） |
| `1fb76a5` | `feat(outlet): add order_draft file filter and 403 hint [dsh]`（前端筛选/提示、e2e） |
| `4c8260f` | `refactor(outlet): treat temp-only bindings as unbound for file read/list [dsh]`（仅 temp/未知绑定的文件按创建者或 viewAll；product/sku 等保持权限映射） |
| 本文档所在 commit | `docs(outlet): record Series E2 delivery [dsh]`（TASKS/CHANGELOG/SESSION_CONTEXT/API_SPEC/ROM-SOW/本报告/STATUS） |

## 1. 集中授权：FileBusinessAccessPolicy

新增 `com.blade.file.policy.FileBusinessAccessPolicy`，文件中心所有出口的唯一授权入口：

- `requireFileRead(file)`：严格租户；敏感绑定按业务范围；非敏感绑定沿用权限映射；未绑定仅创建者或 `btn:file:viewAll`；缺 tenant/actor fail closed。
- `requireTargetAccess(type,id)`：`order` → `OrderAccessPolicy.requireAccess`；`order_draft` → `OutletAccessPolicy.requireDraftAccess`；`product/sku/...` → 既有权限映射；`btn:file:viewAll` 不绕过 order/order_draft 业务范围。
- `requireFilesRead(files)`：批量文件读取校验（bind/delete/move 前置）。
- `buildVisibilityCondition()`：文件中心 SQL 预分页可见性条件（见 §4）。
- 权威来源 `file_business_bind`；当权威绑定为空且 legacy `file_storage.business_type/business_id` 为 order/order_draft 时按 legacy 目标校验。
- 新增 `FileBusinessBindMapper.selectActiveByFileAndTenant`（`@InterceptorIgnore(tenantLine)` + 显式 tenant 参数），避免匿名 PUBLIC 预览在缺少 TenantContext 时被租户拦截器兜底到 tenant=1。

## 2. 多敏感绑定：ALL 规则

同一文件存在多个有效 `order`/`order_draft` 绑定时，调用者必须能访问**所有**敏感绑定；任一不可访问即 `403`。不采用“命中一个可访问绑定就放行”，防止跨档口重复绑定泄露。对应测试 `multiBindingAandB_deniedForA`。

## 3. PUBLIC / previewToken 语义

- `preview`/`variant`：只要 `FileBusinessAccessPolicy.hasSensitiveTargets(file)` 为真（权威 order/order_draft 绑定或 legacy-only），**无论 visibility 是否为 PUBLIC** 都必须通过 `requireFileRead` 业务范围校验。
- `previewToken` 仅作为 JWT 的携带方式由 `JwtAuthenticationFilter` 建立身份（校验签名、Redis 会话、租户）；随后控制器仍执行 `FileBusinessAccessPolicy`，不构成绕过。
- 受保护 order/order_draft 图片响应使用 `Cache-Control: no-store`；商品公开图保持原缓存策略（`maxAge`）。

## 4. 文件中心 SQL 预分页

`FileServiceImpl.pageList` 在 `count`/`page` 之前通过 `wrapper.apply(visibilityCondition)` 过滤，绝无 Java 后过滤。可见性等价于：

```
敏感绑定存在 -> 不存在任一不可访问的 order/order_draft 绑定（EXISTS 子查询 + 档口/人员谓词）
非敏感绑定  -> 可见（沿用 product/sku 权限映射）
无权威绑定  -> legacy 为 order/order_draft 时校验 legacy 目标；否则 create_by=actor 或 viewAll
档口范围 NONE -> 1=0
```

子查询使用 `sale_order`/`order_draft` 的 `tenant_id + deleted + source_outlet_id IN (readable) / IS NULL（unassigned）+ salesman_id/created_by_user_id` 参数；所有 tenant 显式绑定，不 fallback。测试 `fileCenterList_excludesOtherOutletFiles_andCountMatches`、`fileCenterList_unboundOnlyOwnerOrViewAll` 断言 count 与 page 使用同一可见性、B 档口文件不计入总数。

## 5. 全出口接入

- 上传：`upload` 带 `businessId` 在 `storageService.store` **之前**执行 `requireTargetAccess`；缺少 TenantContext fail closed。
- 绑定：`bind`（控制器）、`FileServiceImpl.bindFiles/syncFiles`（控制器与内部调用）先 `requireTargetAccess` + `requireFilesRead`。
- 绑定管理：`createBindings`、`deleteBinding`、`getBindings` 先校验文件与目标；`batchDelete`、`batchMove`、`folder delete(moveFilesToUnfiled)` 先 `requireFilesRead`。
- 读：`preview`、`variant`、`detail`、`list`、`getBindings` 全部接入；`getBindings`/`detail` 不返回不可访问目标信息。
- 变更操作失败时事务回滚，测试断言数据库无副作用（无绑定新增、status/deleted/folder_id 不变）。

## 6. 草稿转订单图片

`OrderDraftService.confirm` 后图片同时保留 `order_draft` 与 `order` 绑定；两者同租户同档口，ALL 规则下合法访问不受影响。测试 `OrderDraftConfirmFinanceTest.confirmDraft_carriesAllBoundPaperImagesIntoFormalOrder` 断言两种绑定均存在且 `requireFileRead` 可访问；未新增迁移/软删逻辑（在证明同范围的前提下保持既有行为，避免破坏草稿详情回显）。

## 7. 测试与验证

后端（真实隔离库）：
- 新增 `FileOutletAccessPolicyTest`（14 例）：A 读成功/B 403、多绑 A+B 拒绝、PUBLIC B 图片拒绝、legacy-only order/draft 受保护、临时文件 owner/other/viewAll、list 不含 B 且 count 一致、未绑定列表权限、伪造 bind/createBindings 无副作用、upload 带 B 业务 ID 写文件前拒绝、delete/unbind/batch-delete/batch-move 拒绝且无副作用、getBindings 隔离、缺 TenantContext fail closed、跨租户不可读。
- 新增 `FilePreviewTokenAccessTest`（1 例）：真实 MVC + Spring Security 过滤器 + Redis 会话，`?previewToken=` 跨档口 PUBLIC 订单图片仍返回 403。
- `FileControllerTest` 等现有 153 个文件测试适配策略替身后全部通过；`OrderDraftConfirmFinanceTest` 增加双绑定断言。
- 全量后端 `mvn test`：**675/675**，Failures 0、Errors 0、Skipped 0。

前端：
- `npm run build` 通过。
- 新增 `e2e-file-outlet-filter.spec.ts` 1 passed（order_draft 参数、403 提示）。
- 订单/草稿/档口/分析相关 e2e 13 passed。

命令：
```bash
cd blade-backend && mvn test                                   # 675/675
cd blade-admin && npm run build                                # 通过
npx playwright test e2e-file-outlet-filter.spec.ts e2e-analytics-outlet.spec.ts \
  e2e-order-outlet.spec.ts e2e-outlet.spec.ts e2e-quick-order-draft.spec.ts \
  e2e-manual-draft-confirm.spec.ts                             # 14 passed
```

## 8. 已知问题与未完成边界

- `e2e-file-upload.spec.ts` 依赖本地不存在的 `super_admin` 租户（既有环境问题，非本轮引入），本机无法运行；文件安全由后端 675/675 覆盖，未修改该 spec 的租户语义。
- 未新增 Flyway：本轮无新表/列/权限，仅索引复用现有 `file_business_bind(file_id)` 与 `sale_order/order_draft` 主线索引；如后续大表需要可再评估。
- Series E3 未做：`GET /api/agent/outlets`、capability 档口摘要、Agent Key 签发/轮换档口配置、Agent 全出口回归。
- 本轮未在文件中心做前端权限过滤（按设计后端为事实源）；前端仅展示后端返回数据并提示 403。

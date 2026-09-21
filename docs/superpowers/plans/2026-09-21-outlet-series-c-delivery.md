# Series C 交付报告：统一后端数据访问策略与授权矩阵

> 分支：`feature/outlet-access-control`　基线：`28909c6`
> 执行：DeepSeek（`[dsh]`）
> 范围：BE-OUTLET-004/005/006 后端；不进入 Series D UI/订单档口选择器，不 push/deploy/NAS/生产。
> 依据：设计文档第 5/9.2/10 章、ROM/SOW Series C。

---

## 0. Codex 终审整改记录（2026-09-21）

逐条映射问题 → 修复 → 测试（独立整改 commit `[dsh]`）：

| 编号 | 问题 | 修复 | 测试 |
|---|---|---|---|
| P0-1 | 列表/详情对 `source_outlet_id=NULL` 授权不一致 | `OutletAccessScope.outletFilter()` 统一六种形态（ALL±/ASSIGNED±/NONE±），`OrderAccessPolicy.applyScopePredicate` 与 `OutletAccessPolicy.applyDraftReadScope` 共用；先档口谓词再独立叠加人员维度 | `OutletScopeMatrixTest.outletFilterCoversAllSixCombinations`、`orderAndDraftDetailsMatchFilterSemanticsForAssignedUnassigned`、`noneUnassignedShowsOnlyNullRowsAndDetailAllowsNull`、`noneWithoutUnassignedDeniesAll`；`OutletNullScopeIntegrationTest.assignedWithUnassignedSeesNullInListAndDetail`、`assignedWithoutUnassignedCannotSeeNullInListOrDetail` |
| P0-2 | `scope.tenantId==0` 绕过租户隔离 | 删除绕过；实体/范围租户任一为空或不相等 fail closed；Agent 校验 principal/context/DB key 租户一致且非空、Key 存在且启用未过期 | `orderRequireAccessFailsClosedOnTenantMismatchOrZeroScope`、`draftRequireAccessFailsClosedOnTenantMismatch`、`agentScopeFailsClosedWhenKeyMissing`、`agentScopeFailsClosedWhenKeyDisabledOrExpiredOrTenantMismatch` |
| P0-3 | 草稿确认丢失档口 ID；禁用档口仍可确认 | `OrderCreateDTO.sourceOutletId`；`toOrderCreate` 携带；`OrderServiceImpl.create` 先 `requireUseOutlet` 再由主数据生成 `sourceShop` 快照并写 `order.sourceOutletId`（不信任客户端文本）；`confirm` 顺序 = requireDraftAccess → 幂等返回 → 空档口阻断 → requireUseOutlet | `OrderDraftConfirmFinanceTest.confirmDraft_propagatesOutletIdAndMasterNameSnapshot`、`confirmDraft_disabledOutletIsRejected`、`confirmDraft_withSourceShop_preservesExplicitSourceShop`（断言改为主数据名称） |
| P0-4 | 重复 `externalRefNo` IDOR/存在性泄露 | `OrderDraftWriter.resolveDuplicate`：仅同创建主体（Agent key id / 手工 user id）幂等并仍需读取策略；不同主体 409 且不暴露 ID；并发 `DuplicateKeyException` 同处理；manual(null creator) 不可被认领 | `OrderDraftDuplicateRefTest.manualSameActorIsIdempotentWithDraftId`、`manualDifferentActorGets409WithoutExistingDraftId`、`manualNullCreatorCannotBeClaimedByAnotherActor`、`agentSameKeyIsIdempotentWithDraftId`、`agentDifferentKeyGets409`、`agentCannotClaimManualDraft` |
| P1-1 | 范围解析与默认 | ALL 也使用个人/Key 默认；ASSIGNED 绑定与本租户未删档口取交集；NONE 保留真实 peopleAll/unassigned；`canReadOutlet` 仅认 readable 集合（不再无条件 true）；`loadTenantOutlets` 显式租户+未删 | `userAllUsesPersonalDefault`、`agentAllUsesKeyDefaultAndAssignedIntersectsTenantOutlets`、`disabledOutletReadableButNotUsable`、`peopleScopeIndependentAndViewAllCompatDoesNotBypass` |
| P1-2 | 测试用宽泛 mock 掩盖策略 | 新增真实矩阵/集成测试（行为断言，非字符串/仅 scope 对象）：`OutletScopeMatrixTest`、`OutletNullScopeIntegrationTest`、`OrderDraftDuplicateRefTest`；保留 `OutletTestScopes` 仅给不关心权限的旧单测 | 上列各测试 |
| P1-7 | V65 迁移/权限 | 静态+真实库断言：`data:outlet:unassigned` 仅 OWNER/ADMIN（FINANCE/SALES 无）；`order_draft.created_by_user_id` 与索引存在；V1→V65 空库通过 | `OutletV65SchemaTest.addsUnassignedPermissionGrantedOnlyToOwnerAdmin`、`addsDraftCreatorColumnAndIndexes`；`OutletPermissionMigrationIntegrationTest.unassignedPermissionExistsOnlyForOwnerAdmin`、`orderDraftHasCreatorColumn`；`OutletFlywayMigrationTest` |

验证（整改 commit）：全量后端 **617/617**；前端 `npm run build` 通过；Playwright `e2e-outlet.spec.ts` **3 passed**。

---

## 0.1 第二轮 Codex 终审整改记录（2026-09-21）

Codex 对 `098d12b`/`8b6335e` 复核后指出 P0 完整性缺口：BE-OUTLET-006 设计明确要求草稿“新建”也统一接入档口范围，而当前 `OrderDraftWriter.create` 完全不解析/校验档口（手工恒为 NULL；Agent NONE 可写 NULL；Agent 重试破坏幂等，第二次 403 而非 DUPLICATE）。独立整改 commit `c8551b0`：

| 编号 | 问题 | 修复 | 测试 |
|---|---|---|---|
| P0-5 | 草稿新建未接入档口范围：归属缺失、可写 NULL、重试幂等被破坏 | `SaveRequest` 增加 `sourceOutletId`（JWT/PC）与 `sourceOutletCode`（Agent）；`OrderDraftWriter.create` 服务端权威归属：Agent 只认 code 且传 ID 直接 400，未传 code 用默认档口、无默认 403、绝不写空；手工显式 ID → 默认 → 无默认时仅 `data:outlet:unassigned` 可写空，否则 400；`update` 省略保留、传入则校验可用且授权并刷新 `sourceShop`；`OutletAccessPolicy` 新增 `requireUsableOutlet`/`requireUsableOutletByCode`/`findOutlet` 供 writer 与 `OrderServiceImpl.create` 复用 | `OrderDraftOutletAttributionTest`（16 例）：Agent NONE 拒绝不落库、ASSIGNED 单档口默认自动归属 + 同 Key 重试 DUPLICATE 同一 `draftId`、显式授权 code 成功、未知/禁用/越权 code 拒绝、传 ID 拒绝；手工显式/默认回填/无默认拒绝/unassigned 写空；更新保留/改档刷新/越权拒绝；View+Summary 字段；历史 Agent 空档口重试不升级且不泄漏内部 ID |
| P0-6 | 草稿 View/Summary 未返回档口稳定标识 | `View`/`Summary` 返回 `sourceOutletId` + `sourceOutletCode`（编码由 `SalesOutlet` 主数据派生，不冗余落库）；`sourceShop` 仍为服务端名称快照 | `OrderDraftOutletAttributionTest.viewAndSummary_exposeOutletIdAndCode`、`manualExplicitAuthorizedId_succeedsWithMasterSnapshot` |

验证（commit `c8551b0`）：`mvn test` **633/633**，Failures 0、Errors 0、Skipped 0；前端 `npm run build` 通过；Playwright `e2e-outlet.spec.ts` **3 passed**。本轮为后端整改，未新增前端页面或档口选择器 UI。

---

## 1. 迁移

**V65__outlet_access_policy.sql**（最高旧版本 V64）：
- `data:outlet:unassigned`（type=2）仅授予 `ROLE_OWNER` / `ROLE_ADMIN`；FINANCE 有 `data:outlet:all` 也不自动获得。沿用 V64 恢复语义（`ON DUPLICATE KEY UPDATE tenant_id=VALUES(tenant_id), deleted=0`）。
- `order_draft.created_by_user_id bigint NULL`（历史 NULL = 遗留/待归档，不回填、不猜测创建人）。
- 索引 `idx_order_draft_tenant_outlet(tenant_id,source_outlet_id,status)`、`idx_order_draft_tenant_creator(tenant_id,created_by_user_id,status)`。
- 空库 V1→V65 通过（`OutletFlywayMigrationTest`）。

## 2. BE-OUTLET-004 统一 OutletAccessPolicy

新增 `com.blade.outlet.policy.OutletAccessScope`（不可变快照：tenant、actor 类型/ID、outletScope ALL/ASSIGNED/NONE、peopleScope ALL_USERS/SELF、unassignedAllowed、readableOutletIds、usableOutletIds、defaultOutletId）与 `OutletAccessPolicy`（用户 + Agent 唯一事实）：
- 用户：`data:outlet:all`→ALL；否则有效 `sys_user_outlet`→ASSIGNED；无绑定→NONE。`data:order:peopleAll` 独立控制人员范围；`data:outlet:unassigned` 控制遗留空档口。绝不按角色名推断。
- Agent：读 `agent_key.outlet_scope_type` + 按 key id 的 `agent_key_outlet`，人员范围恒 ALL_USERS；NONE 不回退。
- 读写分离：`readableOutletIds` 含禁用档口历史；`usableOutletIds` 仅启用档口（写入/选项）。
- 默认优先级：个人/Key 默认（有效且在可用集）→ 租户默认（启用且在可用集）→ 唯一可用档口 → null。
- API：`resolveCurrentScope / allowedOutletIds / canAccessOutlet+requireAccessOutlet / canUseOutlet+requireUseOutlet / resolveDefaultOutletId / listAvailableOptions`。
- `GET /api/outlets/options` 改为结构化契约 `{scopeType, peopleScope, locked, defaultOutletId, items}`（`OutletOptionsVO`），前端 `api/outlet.ts` 与用户表单已适配（`res.data.items`）。

## 3. BE-OUTLET-005 OrderAccessPolicy 二维重构

- `requireAccess(Order)`：租户 + 档口维度（null 需 unassigned；否则 canReadOutlet）+ 人员维度（SELF 时 `salesmanId=actorId`）。`btn:order:viewAll` 仅兼容，不再独立绕过；`agent:orders:read` 仅端点授权，数据范围由 Agent scope 决定。
- 列表 `OrderServiceImpl.pageList` 改调 `OrderAccessPolicy.applyReadPredicate(wrapper)`：NONE→`1=0` 失败关闭；ASSIGNED→`source_outlet_id IN readable`；ALL 省略 IN 但保留租户、人员与（无 unassigned 时）`source_outlet_id IS NOT NULL`；SELF→`salesman_id=actorId`。SQL 先于分页。
- 详情/动作/配货/发货/文件等既有调用点全部经由同一 `requireAccess`/`canAccess`（含 `allowedActions`）。
- Agent 订单读取复用 `OrderService.pageList`，因此同样受 Agent 档口范围约束。

## 4. BE-OUTLET-006 草稿访问

- `OrderDraftService.page/batches` 在分页前应用 `applyDraftReadScope`（档口维度 + 人员维度；手工草稿 SELF 用 `created_by_user_id`，Agent 恒 ALL_USERS）。
- `get`、`confirm`（`selectForUpdate` 后复核，防 TOCTOU）与 `OrderDraftWriter.update`（`selectForUpdate` 后复核）统一 `requireDraftAccess`。
- `create` 归属由服务端唯一决定（第二轮整改补齐）：手工显式 `sourceOutletId` → 默认档口 → 无默认时仅 `data:outlet:unassigned` 可写空，否则 400；Agent 只认 `sourceOutletCode`（越权/禁用/不存在 403），未传时用默认档口、无默认 403，绝不写 NULL，传 `sourceOutletId` 直接 400。始终写主数据 `source_shop` 名称快照；手工记录 `created_by_user_id`，Agent 保留 `created_by_agent_key_id`。
- `update`：`sourceOutletId`/`sourceOutletCode` 均省略则保留既有档口；传入则要求可用且授权并刷新 `source_shop` 快照。
- `View`/`Summary` 返回 `sourceOutletId`/`sourceOutletCode`（编码派生自主数据，不冗余落库）。
- 空档口草稿确认被阻断：`400 请先归档档口后再确认正式订单`，不会生成新的空档口正式订单。
- 未猜测自由文本 `source_shop` 为档口。

## 5. 测试

```bash
cd blade-backend
mvn test -Dtest='OutletAccessPolicyTest,OrderAccessControlTest,OrderDraftConfirmFinanceTest'   # 核心
mvn test                                                                                        # 全量
cd blade-admin && npm run build
npx playwright test e2e-outlet.spec.ts --reporter=line
```

- 全量后端 **591/591**，Failures 0、Errors 0、Skipped 0。
- 前端 `npm run build`（vue-tsc + vite）通过。
- Playwright `e2e-outlet.spec.ts` **3 passed**（用户表单使用结构化 options）。
- 新增/迁移测试：`OutletAccessPolicyTest`（用户 ALL/ASSIGNED/NONE、人员独立、禁用读/不可用、默认优先级、Agent ALL/ASSIGNED/NONE、options 契约）；既有 order/draft 集成测试迁移到新契约（授权 authorities、绑定档口、种子可用档口），未放宽断言。

## 6. 已知限制（临时）

- 手工快速录单后端已按档口范围归属（显式选择 → 默认档口 → 无默认时仅 `data:outlet:unassigned` 可写空）；PC 快速录单页尚未提供档口选择器，未显式选择时依赖默认档口，无默认且无 unassigned 会返回 400（Series D 接入选择器）。历史遗留空档口草稿仍按待归档处理，只有 `data:outlet:unassigned` 可读，且**确认被阻断**（`请先归档档口`）。
- 未实现统计/导出/文件/Agent 全出口的档口范围（Series E）。
- 缓存键尚未纳入档口范围摘要（Series E）。
- `data:outlet:unassigned` 专项用例已覆盖草稿新建/更新与 Agent 越权 code；订单/统计等全出口的跨租户回归仍在 Series E 补齐。

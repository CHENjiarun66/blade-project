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
| `045495f` | `fix(outlet): close Series E2 file scope gaps (tenant SQL, permissions, public media) [dsh]`（Codex 第一轮终审 P0/P1 整改） |
| `563b736` | `fix(outlet): fail closed on missing file tenant/operator context [dsh]`（Codex 第二轮终审整改：缺 TenantContext/可靠 User 一律 403，清理任务多租户遍历） |
| 本文档所在 commit | `docs(outlet): record Series E2 delivery [dsh]`（TASKS/CHANGELOG/SESSION_CONTEXT/API_SPEC/ROM-SOW/本报告/STATUS） |

## 0.1 Codex Series E2 终审整改记录

| 编号 | 问题 | 修复 | 测试 |
|---|---|---|---|
| P0-1 | 列表 SQL 的 `file_business_bind` 子查询缺少 `b.tenant_id` 边界，跨租户污染绑定可能改变本租户文件可见性 | `FileBusinessAccessPolicy.buildVisibilityCondition()` 所有 bind 子查询统一 `b.tenant_id = {tenant}`；target 查询同样显式租户 | `FileOutletAccessPolicyTest.crossTenantBindings_doNotAffectVisibilityOrRead` |
| P0-2 | 列表 `hasMappedNonSensitive` 只要存在映射绑定即可见，与 direct read 的权限校验不一致 | SQL 拆为 `hasPermittedNonSensitive`（仅当前 authorities 拥有的映射类型，viewAll 可全部）与 `hasAnyMappedNonSensitive`（存在任意映射绑定即不得回落创建者规则）；与 direct read“任一映射权限满足”语义一致 | `FileOutletAccessPolicyTest.nonSensitiveListRequiresMatchingPermission_alignedWithDirectRead` |
| P0-3 | `preview/variant` permitAll 但 `getActiveFile` 强制 TenantContext，导致匿名 PUBLIC 商品图无法加载 | 新增全局媒体加载：`FileStorageMapper.selectActiveByIdGlobal` + `FileService.getActiveFileGlobal/loadResourceForMedia`；`authorizeMedia` 先定位文件，敏感按业务范围、非敏感 PUBLIC 匿名、非 PUBLIC 认证 + 同租户 + 业务授权；不恢复 tenant=1 fallback | `FilePreviewTokenAccessTest.anonymousPublicNonSensitiveFile_succeedsWithoutTenantContext`、`anonymousPublicOrderFile_rejected`、`previewToken_crossOutletPublicOrderFile_stillForbidden` |
| P0-4 | `FileController.getCurrentUserId` fallback 1L；upload 可归属用户 1 | `getCurrentUserId` 仅接受可靠 `User` principal，否则 403；`FileService.upload` 拒绝 null operatorId 且在任何 storage 调用之前 | `FileControllerTest.uploadWithoutReliableUser_returns403AndNeverCallsService`、`FileOrderBindingRegressionTest.uploadWithoutOperatorId_isRejectedBeforeStorage` |
| P1 | `buildVisibilityCondition` 直接拼接 actorId/readable IDs | 改为不可变 `VisibilityCondition(sql, params)` 模板 + `FileService.pageList` 用 `wrapper.apply(sql, params)` 的 `{n}` 占位符参数化 tenant/actor/outlet IDs；表名/列名仍为固定字符串 | `FileOutletAccessPolicyTest.assignedMultiOutlet_inClauseCoversAllReadable`（多 ID）、`noneOutletScope_hidesOrderFiles_regardlessOfOrderPermission`（NONE）、`unassignedOrderFile_needsUnassignedPermission`（unassigned NULL） |
| 检查 | legacy sensitive 判定、`hasSensitiveTargets` 仅用文件自带 tenant、bind 查询显式 tenant、受保护文件 no-store | 已在实现中落实；匿名媒体路径对 legacy-only 也先判 `hasSensitiveTargets` | `FilePreviewTokenAccessTest`、`FileOutletAccessPolicyTest.legacyOnlyOrderBinding_isProtected`/`legacyOnlyDraftBinding_isProtected` |

验证（整改 commit `045495f`）：全量后端 **684/684**；`npm run build` 通过；`e2e-file-outlet-filter` 1 passed。

## 0.2 Codex Series E2 第二轮终审整改记录（commit `563b736`）

第二轮审核指出：文件中心仍有多处“缺 `TenantContext` 回退 tenant=1 / 缺可靠 User 回退 user=1”的旁路，且清理定时任务实际只处理 tenant=1。本轮按“请求路径一律 fail closed、系统任务显式遍历租户”收口。

| 编号 | 问题 | 修复 | 测试 |
|---|---|---|---|
| R2-1 | `FileBindingServiceImpl`/`FileFolderServiceImpl`/`FileCleanupServiceImpl`/`FileDerivativeServiceImpl` 共 13 处 `TenantContext.getTenantId() != null ? ... : 1L`，缺租户时静默落到 tenant=1 | 新增 `FileRequestContext.requireTenantId()`（缺租户 `403 缺少租户上下文`），替换全部 fallback；绑定/文件夹/清理/派生图服务不再出现 `: 1L` | `FileBindingServiceImplTest.missingTenant_*`（5 例）、`FileFolderServiceImplTest.missingTenant_*`（4 例）、`FileCleanupServiceImplTest.missingTenant_*`（3 例）、`FileDerivativeServiceImplTest.backfill_withoutTenant_isRejectedWithoutMapperAccess` |
| R2-2 | `FileBindingServiceImpl.getCurrentUserId` 与 `FileFolderController.getCurrentUserId` 缺可靠 principal 时回退 user=1，绑定/日志可归属用户 1 | 新增 `FileRequestContext.requireOperatorId()`：仅接受非匿名 `User` principal 且 id 非空，否则 `403 无法解析当前用户`；身份校验先于任何 mapper 写操作 | `FileBindingServiceImplTest.missingReliableUser_createBindings_isRejectedWithoutMapperAccess`；既有 `FileControllerTest.uploadWithoutReliableUser_returns403AndNeverCallsService`、`FileOrderBindingRegressionTest.uploadWithoutOperatorId_isRejectedBeforeStorage` 保持通过 |
| R2-3 | 回退逻辑分散复制，缺少统一说明（`/api/files`、`/api/file-folders` 为 JWT 用户路径，不接受 Agent） | 抽取单一 `com.blade.file.service.FileRequestContext`；Javadoc 明确 JWT-user 语义与系统任务例外，控制器不再各自解析 principal | 上述全部反例测试；`FileFolderControllerTest` 使用可靠 User principal 建文件夹 |
| R2-4 | `afterCommit` 单文件派生图生成与 `variant` 读取可能读取请求结束后的 `TenantContext`（可能为 null 或串租户） | `generate(FileStorage)` 全程使用 `file.getTenantId()`；`loadVariantResource(fileId,type,tenantId)` 新增显式租户重载，`FileController.variant` 传入 `authorizeMedia` 校验过的 `file.getTenantId()`；用户触发的 `backfill` 使用校验后的请求租户 | `FileDerivativeServiceImplTest.generate_afterCommit_usesFileTenantNotContextTenant`、`loadVariantResource_explicitTenant_isUsedInsteadOfContext`、`recordFailed_usesExplicitTenantId_notContext` |
| R2-5 | `FileCleanupScheduler` 是系统任务却按单一 `tenant-id`（默认 1）执行，无法覆盖其他租户 | `cleanup.tenant-id` 默认改为 `null` 并从 `application.yml` 移除硬编码 `1`；未配置时用 `FileStorageMapper.selectDistinctTenantIds()`（`@InterceptorIgnore`）遍历有文件的 tenant，每租户 try/finally `setTenantId`/`clear`，单租户异常仅记日志不阻断后续；显式配置时只处理该租户 | 新增 `FileCleanupSchedulerTest`（5 例）：默认无隐式 tenant 1、多租户逐个设置上下文且结束清空、单租户失败不阻断、显式单租户不查 distinct、无租户不调用 service |
| R2-6 | 需要确认文件包内不存在请求路径 `return 1L` / `TenantContext null -> 1L` | `rg`/`grep` 复核 `com.blade.file`：无 `1L` 回退；其余 `TenantContext.getTenantId()` 使用均为显式 null 检查后 403（`FileController.authorizeMedia`、`FileRequestContext`、`FileBusinessAccessPolicy.requiredTenantId`、`FileServiceImpl`） | 见 §9 复核命令与结果 |

验证（整改 commit `563b736`）：全量后端 **705/705**（新增 21 例反向/隔离测试）；`npm run build` 通过；`git diff --check ebc1390..HEAD` 无输出。


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
- 新增/扩展 `FileOutletAccessPolicyTest`（20 例）：A 读成功/B 403、多绑 A+B 拒绝、PUBLIC B 图片拒绝、legacy-only order/draft 受保护、临时文件 owner/other/viewAll、list 不含 B 且 count 一致、未绑定列表权限、伪造 bind/createBindings 无副作用、upload 带 B 业务 ID 写文件前拒绝、delete/unbind/batch-delete/batch-move 拒绝且无副作用、getBindings 隔离、缺 TenantContext fail closed、跨租户不可读。
- 新增/扩展 `FilePreviewTokenAccessTest`（3 例）：真实 MVC + Spring Security 过滤器 + Redis 会话，`?previewToken=` 跨档口 PUBLIC 订单图片仍返回 403。
- `FileControllerTest` 等现有 153 个文件测试适配策略替身后全部通过；`OrderDraftConfirmFinanceTest` 增加双绑定断言。
- 第二轮新增 21 例：`FileBindingServiceImplTest` 6 例缺租户/缺用户无副作用、`FileFolderServiceImplTest` 4 例缺租户无副作用、`FileCleanupServiceImplTest` 3 例缺租户无副作用、`FileDerivativeServiceImplTest` 3 例（backfill 缺租户、afterCommit 使用文件租户、显式变体租户）、`FileCleanupSchedulerTest` 5 例多租户遍历/隔离。
- 全量后端 `mvn test`（整改 `563b736`）：**705/705**，Failures 0、Errors 0、Skipped 0。

前端：
- `npm run build` 通过。
- 新增 `e2e-file-outlet-filter.spec.ts` 1 passed（order_draft 参数、403 提示）。
- 订单/草稿/档口/分析相关 e2e 13 passed。

命令：
```bash
cd blade-backend && mvn test                                   # 684/684
cd blade-admin && npm run build                                # 通过
npx playwright test e2e-file-outlet-filter.spec.ts e2e-analytics-outlet.spec.ts \
  e2e-order-outlet.spec.ts e2e-outlet.spec.ts e2e-quick-order-draft.spec.ts \
  e2e-manual-draft-confirm.spec.ts                             # 14 passed
```

## 8. 已知问题与未完成边界

- `e2e-file-upload.spec.ts` 依赖本地不存在的 `super_admin` 租户（既有环境问题，非本轮引入），本机无法运行；文件安全由后端 705/705 覆盖，未修改该 spec 的租户语义。
- 未新增 Flyway：本轮无新表/列/权限，仅索引复用现有 `file_business_bind(file_id)` 与 `sale_order/order_draft` 主线索引；如后续大表需要可再评估。
- Series E3 未做：`GET /api/agent/outlets`、capability 档口摘要、Agent Key 签发/轮换档口配置、Agent 全出口回归。
- 本轮未在文件中心做前端权限过滤（按设计后端为事实源）；前端仅展示后端返回数据并提示 403。

## 9. 文件包 tenant/user 回退复核（R2-6）

复核准入命令与结果（工作区 HEAD = `563b736`）：

```bash
cd blade-backend
grep -rn "return 1L\|? 1L\|: 1L" src/main/java/com/blade/file --include='*.java'   # 无输出
grep -rn "TenantContext.getTenantId()" src/main/java/com/blade/file --include='*.java'
```

文件包内仅剩以下 `TenantContext.getTenantId()` 使用，全部为**显式 null 检查后 403 的 fail closed**，非回退：

| 位置 | 用途 | 缺失时行为 |
|---|---|---|
| `FileRequestContext.requireTenantId()` | 请求路径统一租户 | `BusinessException 403 缺少租户上下文` |
| `FileController.authorizeMedia` | `preview/variant` 非 PUBLIC 媒体同租户校验 | `BusinessException 403 无权访问该文件` |
| `FileBusinessAccessPolicy.requiredTenantId()` | 策略内显式查询 | `BusinessException 403 缺少租户上下文` |
| `FileServiceImpl.upload` / `requiredTenantId()` | 上传写入前租户校验 | `BusinessException 403 缺少租户上下文` |

`com.blade.file` 包内无 `return 1L` / `TenantContext null -> 1L`。范围外既有模块（`ProductServiceImpl`、`CustomerServiceImpl`、`UserServiceImpl`/`RoleServiceImpl`/`PermissionServiceImpl` 等）仍有历史 `: 1L` 兜底，属 Series E 之外、本轮未触碰，记录在此以免误判为文件包遗漏。

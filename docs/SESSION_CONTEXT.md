# 当前会话上下文

> 本文件是项目的快速状态快照，用于新 AI 和新会话快速接手。
> 这里只保留摘要信息；任务明细以 `03-TASKS.md` 为准，变更历史以 `05-CHANGELOG.md` 为准。

---

## 2026-09-21 档口第二批A 整改（文件一致性/财务鉴权/入口权限/回填审计/跨租户）

- 基线 `2070ee0`；本批小提交（按项拆分）：文件一致性修复、财务先鉴权后幂等、看板/分析入口 `@PreAuthorize`、回填报告审计证据、跨租户真实反例，另含一处 MyBatis 测试元数据修复。
- 文件中心：`FileBusinessAccessPolicy.buildVisibilityCondition` 在 `data:order:peopleAll` 且无 `btn:file:viewAll` 时，`actorId` 占位必须仍可用于“本人未绑定文件”条件（此前写成 `create_by = null`，列表/计数与详情不一致）。真实 DB `FileOutletAccessPolicyTest.peopleAll_keepsOwnUnboundFileVisible_butNotOthers_andOrderDraftScopeStillApplies`：本人未绑定文件 list/count/detail 一致可见，他人不可见，订单/草稿仍按档口范围；修复前该用例失败。
- 财务动作：`OrderActionService.lockForFinancialAction` 改为先加载/锁定订单并 `requireAccess`，再判断幂等短路，避免越权者用真实 orderId + 幂等键从“静默成功/键被占用”推断存在性。`OrderScopeRealDbIntegrationTest` 新增 2 例真实 DB：越权 403 且无流水写入；授权用户同键重试幂等。
- 看板/分析：`DashboardController` 全部端点加 `menu:dashboard`、`AnalyticsController` 全部端点加 `menu:analytics`；`DashboardAnalyticsPermissionIntegrationTest`（真实登录/JWT/DB 权限）验证无权限 403、有权限 200、互不越权。
- 回填报告：`OutletBackfillReport` 增加 operator/起止时间/expected+actual 库名/映射文件 SHA-256/逐条 `MappingDecision`（候选与更新数）/前后 `source_outlet_id` 赋值摘要哈希；apply 安全闸门强制 operator，preview 缺省明确记为 `PREVIEW`；报告仍不含凭据。`OutletBackfill*Test` 22/22 通过。文档标记“代码完成、生产副本演练与人工确认未完成”。
- 跨租户真实反例：新增 `CrossTenantOutletIsolationIntegrationTest`（真实 DB，非 mock）：tenant1 用户绑 tenant2 档口失败且无绑定/无用户残留；tenant1 Agent Key ASSIGNED/ALL 默认 tenant2 档口失败且无 key/绑定副作用；档口服务读/改/启停 tenant2 档口 404 且原行不变（增强既有 mock 的 `UserOutletBindingTest.crossTenantOutletIsRejectedAsNotFound`）。
- 测试基建：`ProductServiceV2Test` 由普通 `Configuration` 改为 `MybatisConfiguration` + `GlobalConfig`，避免把 `inventory.tenant_id` 污染成 `tenantId` 导致全量顺序下真实 DB 看板用例坏 SQL。
- 验证：后端全量 **805/805**，Failures 0 / Errors 0 / Skipped 0；本批无前端改动，未运行前端构建；`git diff --check` 无输出；工作区干净（工具隐藏目录本地排除）。
- 未处理（后续独立批次）：`TenantLineHandler` tenant=1 全局兜底、默认档口数据库唯一约束/N+1、Agent batch 403 HTTP 语义、移动端档口选择、软删除。

## 2026-09-21 档口完整性审计第一批 Codex 终审整改（缓存键顺序 + 真实 DB/Redis 证据）

- P0-1 缓存键顺序：Codex 终审指出第一批键 `customer:preference:{scopeFingerprint}:{customerId}:...` 无法被 `evictPreferenceCache` 前缀 `customer:preference:{customerId}:*` 命中，动作后缓存永不失效。已修正为 `customer:preference:{customerId}:{scopeFingerprint}:{start}:{end}`，evict 覆盖该客户全部范围/时间窗，不误删他人；`CustomerStatsCacheService` 注释同步。
- 真实 Redis 测试 `CustomerPreferenceCacheEvictionTest`（2）：同客户两时间窗 + 他人一键，evict 后前者消失后者保留；真实 `getPreference` 写键后经真实 `OrderActionService.cancelOrder` 动作链路（`persist`）键被清空。
- P1 证据补强：新增 `OrderScopeRealDbIntegrationTest`（3，真实 MySQL，非 mock）：仅绑 A 用户对 B 档口 READY_TO_SHIP 发货 403 且订单状态/`order_delivery_plan`/`order_state_transition_log`/`inventory_log` 均不变；已 SHIPPED 幂等路径同样先 403；B 档口 WAITING_ALLOCATION 占位拆单 403 且明细/adjustment log 不变。原 mock 单测保留，二者分工（mock 证明鉴权先于数据层调用；真实 DB 证明真实 SQL 副作用为零）。
- 验证：targeted 5 例通过；后端全量 **796/796**（791 + 5），Failures 0 / Errors 0 / Skipped 0；`git diff --check` 无输出。
- 文档：整改报告新增 §8（Codex 终审整改）与 §9（本批验证）；`03-TASKS` 中 `BE-OUTLET-005`/`TEST-OUTLET-001` 补记；同步本文件与 CHANGELOG。

## 2026-09-21 档口完整性审计第一批整改（P0 旁路 + P1 必绑）

- 初次只读审计发现：`OrderActionService.shipOrder` 与 `OrderPlaceholderSplitService.splitPlaceholderItem` 未挂 `requireAccess`（跨档口发货/拆分写旁路）；`CustomerServiceImpl` 订单/统计/偏好未接 `OrderReadScope` 且缓存键无范围指纹、Controller 无鉴权。
- P0-1：`shipOrder` 改为先 `lockOrder`（租户 + `requireAccess`）再幂等/占位/配货/库存/状态；`OrderShipScopeGuardTest` 验证 NONE 403、幂等也先鉴权、授权路径仍出库。
- P0-2：`OrderPlaceholderSplitService` 注入 `OrderAccessPolicy`，锁单后先鉴权再读写明细；`OrderPlaceholderSplitScopeGuardTest` 验证 403 且无明细/审计写入。
- P0-3：`CustomerServiceImpl` 三出口一次 `resolveReadScope(null,false)` + `applySalesPredicate` 预分页；`getPreference` 缓存键加入 `cacheFingerprint()`；`CustomerController` stats/orders/preference 加 `btn:customer:viewOrders`；`CustomerOrderScopeIntegrationTest`（真实 JWT+DB+Redis）覆盖 SELF/负责人 ALL_USERS/ALL+NONE、NULL 默认排除、分页 total 一致、缓存不串、缺权限 403。
- P1：`UserServiceImpl.update` 角色变更（即使 `outletIds=null`）按最终角色 + 有效启用绑定校验 SALES 必绑，事务回滚；`outletIds` 去重；`UserRoleChangeOutletBindingIntegrationTest` 真实 DB 覆盖 400/回滚/保留/禁用绑定/重复 ID。
- V64 兼容：WAREHOUSE 由 `btn:order:viewAll`（V55）迁移获得 `data:outlet:all`+`data:order:peopleAll`；未在代码中给角色名特权，无范围主体仍 403。
- 文档：新建 `docs/superpowers/plans/2026-09-21-outlet-integrity-audit-remediation-1.md`；`BE-OUTLET-005` 补记整改，`TEST-OUTLET-001` 降级为"第一批整改完成"，剩余 P1/P2 见报告。

## 2026-09-21 档口 Series F：本地发布准备完成（生产执行待外部）

- DATA-OUTLET-002 本地工具：新增 `com.blade.outlet.migration`（`OutletBackfillMapping` 显式 CSV 解析校验、`OutletBackfillService` dry-run/apply、`OutletBackfillSafetyGate` 四重闸门、`OutletBackfillCli`）。只更新 `sale_order`/`order_draft.source_outlet_id`，`source_shop` 原值保留；纯数字/批次疑似行不映射；冲突不覆盖；确认草稿跳过；前后对账（行数/金额/收款/状态/明细/文件绑定/source_shop）；幂等；apply 默认关闭且拒绝生产/NAS 特征连接。只在生产副本操作。
- DATA-OUTLET-003 本地建议包：`scripts/outlet-user-outlet-authorization-suggestions.sql`（只读）+ `scripts/outlet-user-outlet-authorization-template.csv`；decision 恒 NEEDS_REVIEW，绝不自动授权；人工确认/授权待外部。
- TEST-OUTLET-004 本地：`OutletScopeCrossResourceAuditTest`（档口×人员×user/agent×订单/草稿/统计读范围矩阵）+ `OutletIndexCoverageTest`（索引核对）+ `scripts/outlet-scope-explain.sql`（single/multi/all/none/unassigned EXPLAIN）。发现 `sale_order.salesman_id` 无独立索引，生产规模性能待评估；未改 schema。
- 文档：新增 Series F 本地交付报告与生产副本/备份/灰度/回滚 checklist（只写不执行）；修正 ROM-SOW 中 B/C 已完成却仍 TODO 的行。
- Codex 终审整改：安全闸门改为 fail-closed 正向副本身份（expected-database-name 与实际一致 + `*_copy/_rehearsal/_staging/_test` 命名 + report-dir；`blade` 拒绝；黑名单第二层；`OutletBackfillApproval` 包内构造，写入入口限可见性）；报告补 `groups` 分类/样例与 JSON+Markdown；UPDATE 带 tenant/null/分块并处理并发；对账加 id+source_shop 摘要；运维 SQL 全部 `@tenant_id` fail-closed + 静态契约测试。全量后端 **777/777**。
- 未完成（待外部）：生产副本映射预演与对账、初始授权人工确认与写入、备份/灰度/回滚执行、生产规模性能、Series G 收口（btn:order:viewAll 下线评估、NOT NULL 评估）。
- 未做（保持）：不删除 `btn:order:viewAll`，不加 `source_outlet_id NOT NULL`，不 push/部署/NAS/生产。

## 2026-09-21 档口 Series E3：Agent Key 档口范围与全出口反泄露完成

- BE-OUTLET-010：Key create/rotate/view/credential 支持 `outletScopeType`(ALL/ASSIGNED/NONE)/`outletIds`/`defaultOutletId`；create 默认 NONE；rotate 字段 null 分别继承、显式传入按新配置，单事务先建新 Key 再停旧 Key；严格校验同租户未删除、ASSIGNED 默认在集合内且启用、ALL 无冗余绑定、NONE 禁止绑定/默认。
- 新增 `GET /api/system/agent-keys/outlets`（含禁用档口，不受管理账号自身范围限制）与 scope `outlets:read`（`agent:outlets:read`）。
- Agent API：`GET /api/agent/capabilities` 返回档口范围/默认编码/只读与可用档口摘要；新增 `GET /api/agent/outlets`（缺 scope 403、Key 无效 401、NONE 为空）；每请求实时重读 Key 与绑定，无缓存，停用/禁用/轮换下一请求即生效。
- BA-OUTLET-006：Key Manager 档口范围编辑器（全部/指定/不开放）、列表展示、rotate 回显完整配置、订单/分析权限 + NONE 非阻塞警告、凭证弹窗 capabilities→outlets 接入说明（只用 outletCode）。
- commit `be2ed13`（后端）、`1e1fef9`（Agent NULL 反例）、`65f38e1`（前端）、`96eb2ae`（docs）、`3312585`（Agent 统计读范围反例）、`652b565`（E3 终审整改：P0-1 ALL 继承轮换、P0-2 Agent 统一 sourceOutletCode/sourceOutletCodes、style-trends 跨周期保留筛选、前端文案）。
- 终审整改：`readOutletConfig` ALL 的 outletIds 恒空仅留默认档口；Agent orders 拒绝内部 ID、新增 `sourceOutletCode` 并返回 `sourceOutletCode`；Agent analytics 拒绝 `sourceOutletIds`、新增 `sourceOutletCodes` 按 readable 解析；`OutletAccessPolicy.requireReadableOutletByCode`/`resolveReadableOutletIdsByCodes` 集中处理（历史可读禁用、新建仅启用）；`AgentStyleTrendService.toCustomQuery` 复制原筛选。全量后端 **751/751**，`npm run build` 通过，Agent Playwright 2 passed，`git diff --check 3312585..HEAD` 无输出。交付报告见 [2026-09-21-outlet-series-e3-delivery.md](./superpowers/plans/2026-09-21-outlet-series-e3-delivery.md)。
- 未做（保持 TODO）：Series F 历史档口回填与发布、Series G 旧权限下线与收口。

## 2026-09-21 档口 Series E2：文件中心/图片档口闭环完成

- 新增 `FileBusinessAccessPolicy`：order/draft 复用统一订单/草稿策略；多敏感绑定 ALL；legacy 兜底；未绑定仅创建者或 viewAll；缺 tenant/actor fail closed。
- `preview`/`variant` 的 PUBLIC 订单/草稿图片仍按业务范围校验；`previewToken` 仅建立身份；受保护响应 no-store。
- 上传/绑定/解绑/删除/批量删除/移动/详情/列表/getBindings 全部前置授权；文件中心 list 在 count/page 前 SQL 过滤。
- 草稿转订单双绑定同范围可访问；前端补 order_draft 筛选与 403 提示。
- commit `5280817`（后端）、`1fb76a5`（前端）、`4c8260f`（temp 绑定收口）、`045495f`（Codex 第一轮终审 P0/P1 整改）、`563b736`（第二轮终审：文件包租户/用户回退收口）、`808612b`（第二轮补强：upload/backfill/清理变更统一可靠 User）。
- 第二轮整改：新增 `FileRequestContext`，绑定/文件夹/清理/派生图去掉全部 tenant=1 / user=1 回退，缺上下文 403 fail closed；`afterCommit` 派生图与变体读取用文件自带 tenant；`FileCleanupScheduler` 遍历存在文件的租户并逐租户 try/finally 隔离，`cleanup.tenant-id` 默认 `null`；backfill/清理变更先解析可靠 User。新增 24 例反向测试，全量后端 **708/708**，`npm run build` 通过，`git diff --check ebc1390..HEAD` 无输出。交付报告见 [2026-09-21-outlet-series-e2-delivery.md](./superpowers/plans/2026-09-21-outlet-series-e2-delivery.md)。
- 未做（保持 TODO）：Series E3 Agent 档口出口与 capability、Series F 历史迁移与发布、Series G 收口。

## 2026-09-21 档口 Series E1：统计/仪表盘/导出隔离完成

- 新增 `OrderReadScope` + `OrderAccessPolicy.resolveReadScope`：Dashboard/Analytics 所有订单型指标（summary/trend/ranking/product detail/pending/周月同期/沉默客户/周转分子）统一档口 × 人员范围；未归档默认排除，仅 `data:outlet:unassigned` + `pendingArchive=true` 看独立 `pendingArchiveCount`。
- 订单导出在 count/select 前应用与列表一致的读范围 + 显式档口/待归档校验；导出可见集合与列表一致。
- `DashboardQueryDTO.sourceOutletIds/pendingArchive`（向后兼容）；分析页/仪表盘档口多选与待归档开关，前端仅展示后端授权数据。
- 缓存结论：Dashboard/Analytics 当前无缓存，本轮不新增；新增 `cacheFingerprint` 与测试防未来跨用户复用。非订单型全局指标保持全局语义。
- commit（本批）见 [2026-09-21-outlet-series-e1-delivery.md](./superpowers/plans/2026-09-21-outlet-series-e1-delivery.md)；全量后端 659/659，`npm run build` 通过，Playwright 13 passed。
- 未做（保持 TODO）：Series E2 文件/图片绑定与预览、E3 Agent 档口出口与全出口回归、F 历史迁移与发布、G 收口。

## 2026-09-21 档口 Series D：订单与草稿档口交互完成

- 正式订单创建必须有具体档口（显式 ID/编码 → 默认 → 400），`data:outlet:unassigned` 不能新建空档口订单；`source_shop` 只用主数据名称快照。订单/草稿列表新增 `sourceOutletId`/`unassignedOnly` 结构化筛选（越权 403、待归档仅授权、互斥）。
- 改档口：V66 `btn:order:changeOutlet`（仅 OWNER/ADMIN）+ 原因 + `order_outlet_change_log` 审计；历史 NULL 归档另需 `data:outlet:unassigned`；已完成订单保留金额/明细约束，仅允许备注/图片/显式高权限改档口。
- 前端共享 `OutletSelect`：单档口只读、多档口授权选择、待归档提示、改档口原因；快速录单/新建/草稿详情/订单编辑/列表筛选/待归档标签全部接入，移除可编辑 `sourceShop`。
- commit `8071dc0`（后端）、`7759a67`（前端）；全量后端 650/650，`npm run build` 通过，Series D Playwright 5 passed + 相关 e2e 5 passed。交付报告见 [2026-09-21-outlet-series-d-delivery.md](./superpowers/plans/2026-09-21-outlet-series-d-delivery.md)。
- 终审整改：档口 options 改为仅并发去重不持久缓存（logout/clearAuthState 清理），`OrderDraftDTO.Summary` 补 `sourceShop` 名称快照。
- **现存安全缺口（Series E P0 必须先修）**：`OrderServiceImpl.exportOrders` 未接 `applyReadPredicate`/显式档口筛选，拥有导出权限的越权用户可导出其他档口订单；**feature 分支在 Series E 修复前不得部署**。
- 未做（保持 TODO）：Series E 统计/导出/文件/Agent 全出口、Series F 历史迁移与发布、Series G 收口。

## 2026-09-21 档口 Series C 第二轮 Codex 终审整改完成

- 补漏 P0：草稿“新建”统一接入档口范围（`OrderDraftWriter.create/update`）。Agent 只认 `sourceOutletCode`，传 `sourceOutletId` 返回 400；未传 code 时用默认档口，无默认 403，绝不写空。手工显式 ID → 默认 → 无默认时仅 `data:outlet:unassigned` 可写空，否则 400。
- `View`/`Summary` 返回 `sourceOutletId`/`sourceOutletCode`（编码由主数据派生）；同主体同 `externalRefNo` 重试幂等返回 `DUPLICATE` 同一 `draftId`；历史 Agent 空档口草稿重试不升级、错误信息不泄漏内部 ID。
- 整改 commit `c8551b0`；新增 `OrderDraftOutletAttributionTest`（16 例），全量后端 633/633。交付报告见 [2026-09-21-outlet-series-c-delivery.md](./superpowers/plans/2026-09-21-outlet-series-c-delivery.md)。

## 2026-09-21 档口 Series C Codex 终审整改完成

- 修复 P0：NULL 档口列表/详情一致性、严格租户隔离（含 tenant=0 不绕过）、草稿确认档口贯通与禁用档口阻断、重复 externalRefNo IDOR；P1：默认优先级/绑定交集/NONE 保留范围位、真实矩阵测试。
- 交付报告见 [2026-09-21-outlet-series-c-delivery.md](./superpowers/plans/2026-09-21-outlet-series-c-delivery.md)；全量后端 617/617 通过。

## 2026-09-21 档口 Series C 统一访问策略完成

- `OutletAccessPolicy`（用户/Agent 二维范围快照、读写分离、默认优先级、结构化 options）、`OrderAccessPolicy` 二维重构、草稿访问收口、V65 迁移（`data:outlet:unassigned` + `order_draft.created_by_user_id`）已完成。
- 交付报告见 [2026-09-21-outlet-series-c-delivery.md](./superpowers/plans/2026-09-21-outlet-series-c-delivery.md)；全量后端 591/591 通过，前端构建与 e2e-outlet 通过。
- 临时限制：手工草稿后端已按档口范围归属（显式 ID → 默认档口 → 仅 `data:outlet:unassigned` 可写空）；PC 快速录单页尚未提供档口选择器，未显式选择时依赖默认档口，无默认且无 unassigned 会 400（Series D 接入选择器）。Series E 统计/导出/文件/Agent 出口与缓存键仍待接入。

## 2026-09-21 档口 Series B2 前端完成

- BA-OUTLET-001 档口管理页（`/outlets`：列表/搜索/状态筛选/分页/新建编辑/设默认/启停二次确认）与 BA-OUTLET-002 用户档口授权（多选+默认+权限摘要、销售员空档口阻止、状态切换不提交档口字段）已完成。
- 交付报告见 [2026-09-21-outlet-series-b2-delivery.md](./superpowers/plans/2026-09-21-outlet-series-b2-delivery.md)；前端 `npm run build` 通过，Playwright `e2e-outlet.spec.ts` 2 passed。
- 未做（保持 TODO）：Series C 统一访问策略、Series D 订单/草稿交互、Series E 统计/导出/文件/Agent、Series F 历史迁移与生产发布、Series G 收口。

## 2026-09-21 档口 Series B1 后端完成

- 档口主数据服务/API（`/api/outlets` CRUD/启停/options/引用统计/默认唯一）、用户多档口绑定（`outletIds/defaultOutletId` + `outletScope/peopleScope` 摘要）、V64 权限迁移（menu/btn/data/agent 权限码 + 角色赋权 + `btn:order:viewAll` 兼容）已完成。
- 仅后端，未做前端（BA-OUTLET-001/002 保持 TODO）；交付报告见 [2026-09-21-outlet-series-b1-delivery.md](./superpowers/plans/2026-09-21-outlet-series-b1-delivery.md)；空库 Flyway V1→V64 通过，全量后端 581/581。
- 经 Codex 终审整改：二维权限解耦、角色先校验后写、V64 赋权恢复语义、outlet_code 不可变、默认批量清理，均已并入同一交付。
- 未做（保持 TODO）：Series B2 前端、Series C 统一访问策略、Series D 订单/草稿交互、Series E 统计/导出/文件/Agent、Series F 历史迁移与生产发布、Series G 收口。

## 2026-09-20 档口 Series A 数据模型完成（含 Codex 审核整改）

- 档口权限改造 Series A（数据模型与兼容迁移）已完成并经 Codex 审核整改，交付报告见 [2026-09-20-outlet-series-a-delivery.md](./superpowers/plans/2026-09-20-outlet-series-a-delivery.md)。
- V63 加法迁移落地：`sales_outlet`、`sys_user_outlet`、`agent_key_outlet`、`order_outlet_change_log` 四表；`sale_order.source_outlet_id`（可空）与 `order_draft.source_outlet_id`（可空）及租户前缀索引；`source_shop` 保留不改写历史数据。
- Codex 整改补齐：`agent_key.outlet_scope_type`（ALL/ASSIGNED/NONE 默认 NONE）消除范围歧义；`rotate` 同事务复制范围与有效绑定、新建 Key 保持 NONE；审计脚本新增可编辑名称映射 CTE（全程只读）；文档状态统一。
- 新增 `com.blade.outlet` 实体/Mapper 与只读审计 `scripts/outlet-source-shop-audit.sql`；空库 Flyway V1→V63 通过，相关测试与全量后端通过（见交付报告）。
- 未做（保持 TODO）：档口 CRUD、用户授权、统一访问策略、订单/草稿写入、统计/导出/文件/Agent、历史回填与生产发布（Series B-G）。

## 2026-09-20 档口主数据与数据权限规划

- 档口已确定从订单自由文本升级为正式主数据和数据权限边界，设计见 [20-OUTLET_ACCESS_CONTROL_DESIGN.md](./20-OUTLET_ACCESS_CONTROL_DESIGN.md)。
- 用户与档口为多对多关系：Owner/Admin 可访问全部档口；档口负责人可看绑定档口内全部人员；销售员默认只能看本人在绑定档口的订单。
- 订单范围按“租户 ∩ 档口范围 `ALL/ASSIGNED/NONE` ∩ 人员范围 `ALL_USERS/SELF` ∩ 动作/字段权限”计算，必须统一覆盖列表、详情、草稿、统计、导出、文件和 Agent API。
- 目标结构包括 `sales_outlet`、`sys_user_outlet`、`agent_key_outlet`、`order_outlet_change_log` 以及订单/草稿 `source_outlet_id`；现有 `source_shop` 保留为名称快照。
- 开发任务已拆为 A～G 七个系列，见 [2026-09-20-outlet-access-control-rom-sow.md](./superpowers/plans/2026-09-20-outlet-access-control-rom-sow.md)。当前仅完成设计和任务规划，尚未修改运行代码、数据库或生产环境。

## 2026-09-18 Agent 接入与生产基线

- 新增 [19-AGENT_CONNECTION_PLAYBOOK.md](./19-AGENT_CONNECTION_PLAYBOOK.md)，把生产入口、MCP 配置、14 个工具、scope、首次只读验收、错误处理和标准 Agent 指令集中为单页接入流程。
- 当前生产入口为 `https://www.chenjianas.asia:33294`，本机授权程序位于 `/Users/chenjiarun/Library/Application Support/Blade Agent Key Manager/bin/blade-agent-request`。模型不得读取 Key 原文，只能通过 Key Manager、MCP 和用户授权调用。
- 生产已于 2026-09-16 发布 commit `fff329b1447164f9c79927c734153d4cf7be13a9`、release `20260916_104900`，Flyway 为 V60。商品/订单读取、商品新增、客户读取/新增、手工草稿、草稿分页工作台、批次/单号拆分和占位 SKU 界面修正均已上线。
- 发布后 145 张订单、43 张草稿、49 个客户、187 个商品、699 个 SKU 和 187 个占位 SKU 保持正常；历史迁移重放为 0，订单、财务和占位 SKU 发布门禁全部通过。
- 生产备份位于 NAS `/volume2/blade/db-backups/nas_blade_project_prod_20260916_104900`，NAS 外副本位于 Mac `/Users/chenjiarun/Documents/BladeProject生产备份/nas_blade_project_prod_20260916_104900`。

## 2026-09-15 录单字段与 SKU 显示修正基线

- 订单全流程回退设计已形成 `18-ORDER_REVERSAL_APPROVAL_DESIGN.md`：禁止任意状态编辑，按未过账路径纠错、业务撤销和库存/财务事实冲销分层；R0 二次确认、R1 负责人审批、R2 强制职责分离、R3 禁止直接回退。当前仅完成设计和任务拆分，回退与审批代码尚未开发。
- 订单详情配货流程已补齐占位 SKU 前端保护：仍含整款录入/历史待明确规格时，“创建配货计划”只提示先拆分，不加载仓库、不打开弹窗；后端 `startAllocation` 继续承担最终阻断。遗留配货计划弹窗也统一显示“整款录入（颜色/尺码未指定）”，不暴露乱码和 `UNSPEC`。
- 订单详情“加收金额”默认 `0.0`，按钮按 1 元步进，允许手工录入 1 位小数；该弹窗金额统一显示 1 位小数，后端金额存储精度和历史数据不变。
- 快速录单、手工草稿和 Agent 草稿统一使用独立的“单据批次 + 单据号”，两个字段均为必填；草稿分别保存，转正式订单时生成兼容值 `批次_单号` 写入 `sale_order.source_doc_no`。
- 快速录单“保存并录下一单”保留批次，仅递增单据号末尾数字。旧手工草稿若仍是 `数字批次-单号` 或 `数字批次_单号` 且没有批次字段，详情页会兼容拆分，保存后规范化；不批量修改生产历史数据。
- 批量录单的 `PLACEHOLDER` SKU 固定显示“整款录入（颜色/尺码未指定）”，不再暴露占位属性乱码；草稿详情、商品/SKU 字典和批次导航并行加载，详情不受约 9 秒的全量商品请求阻塞，目录未就绪时以识别款号作友好回退，不显示 `SKU {id}` 临时标签。
- 当前改动已完成本地 PC 构建与专项 Playwright 验证；真实订单 1131 的占位配货前置阻断也已只读验证，并已随 2026-09-16 release 上线。

## 2026-09-14 草稿订单列表与批次工作台开发基线

- 订单管理左侧入口改为“草稿订单列表”。列表只显示 `EDITING` 草稿，提供服务端分页以及批次、关键字、来源、待匹配和日期筛选；点击行进入 `/orders/drafts/{id}` 详情。
- 详情页不再让用户切换“待处理/已确认”，而是按“单据批次 + 当前批次子单”切换。确认后草稿从列表消失，用户可查看正式订单或继续同批次下一张。
- 后台继续保留 `CONFIRMED`、`confirmed_order_id`、确认人和确认时间，用于幂等、防重与审计；它不是用户日常查看的第二个草稿箱状态。
- 本次无需数据库迁移，不改写既有生产数据；后端批次筛选集成测试、PC 构建和草稿关键路径 Playwright 已覆盖，尚未提交发布 NAS 生产。

## 2026-09-13 Agent 客户权限扩展开发基线

- Agent Gateway 新增 `customers:read` 和 `customers:create`。前者分页/单项读取客户名称、电话、地址、备注和订单数，属于独立敏感只读权限；后者只新增客户，不允许修改、删除、合并或标签调整。
- 电话会规范化、去重并在同租户查重；已有号码返回 `DUPLICATE`，不覆盖旧客户。V60 为客户和操作日志增加 Agent Key 来源字段，既有客户无需改写。
- 系统管理 Key 页面、本机 Key 管理器、请求白名单和 MCP 已同步客户列表、详情、新增能力。历史 Key 不自动扩权，必须由 Owner 调整权限并轮换 Key。
- 本地验证：后端全量 517/517、PC 生产构建、Mac Key 管理器 9/9；真实 Agent Key 链路覆盖客户分页/详情、新增、重复号码幂等、来源审计和最小权限反例。
- 本项已随 2026-09-16 release 部署 NAS 生产，生产数据库为 V60。

## 2026-09-13 Agent 数据权限扩展开发基线

- Agent Gateway 增加 `products:read`、`orders:read`、`products:create`：可分页读取脱敏商品/正式订单，可读取商品新增选项，并可只新增商品。
- 商品/SKU 查询不返回成本价；订单查询不返回电话、地址、成本和毛利。草稿与正式订单保持不同接口，单页上限 100，不提供无上限全量导出。
- 新增商品以商品编码作为重复保护：同编码返回 `DUPLICATE`，不覆盖旧数据；颜色尺码只能引用已有启用编码，系统保留编码和 DEFAULT/NORMAL/PLACEHOLDER SKU 由既有商品服务维护；不产生库存事实。
- 系统管理 Agent Key 页可选择新增 scope。调整权限会轮换 Key并停用旧 Key，历史 Key 不自动扩权；Mac Key 管理器和 MCP 白名单同步增加商品/订单查询与商品新增工具。
- 修改、删除、库存、收付款、正式订单动作仍未开放给 Agent；成本/毛利独立 scope 与限流分别继续由 BE-585、BE-562 跟进。本项已随 2026-09-16 release 部署生产。
- 本地验证：后端全量 510/510、PC 生产构建、Mac Key 管理器 9/9；MockMvc 真实 Agent Key 链路已覆盖 scope 鉴权、只读 Key 拒绝写入、脱敏查询、商品新增和重复编码幂等。

## 2026-09-13 快速录单暂存开发基线

- 快速录单的“添加到草稿”已改为真实保存动作：允许半成品暂存、停留当前页、重复点击更新同一草稿；之后从左侧“草稿订单列表”继续填写并确认为正式订单。
- V59 为 `order_draft` 增加 `MANUAL/AGENT` 来源和快速录单完整字段，为草稿明细增加成本价快照。既有 Agent 草稿默认保持 `AGENT`；手工草稿金额按明细+运费计算，Agent 草稿继续遵守纸单总额优先。
- 手工草稿确认会保留订单类型、来源店铺、客户快照、实收、运费、配送、备注、图片和成本价，并沿用正式订单统一收款流水；未完成草稿不会进入正式订单、库存、财务或统计。
- 本地门禁：后端全量 502/502、PC 生产构建、草稿关键路径 Playwright 3/3 通过；本地开发库已由 Flyway V58 升至 V59，未连接或修改 NAS 生产。
- 本项已随 release `20260916_104900` 发布，生产已执行 V59/V60 加法迁移和全部发布门禁。

## 2026-09-11 本机 Agent Key 管理基线

- 新增 `tools/blade-agent-key-manager` 原生 macOS 工具：用户可双击应用录入 Key、选择所属 Agent/环境/scope/到期日并查看剩余时间；完整 Key 只进入 macOS 钥匙串，普通元数据不保存密钥。
- 应用附带 `blade-agent-request`，首次启动安装到用户 Application Support。支持 stdio MCP 和一次性命令调用；只开放商品候选、订单草稿、款式趋势和 SKU 结构四类白名单工具，远程 HTTP、任意 URL、任意写操作和跨主机重定向均被拒绝。
- Agent 请求时由用户选择符合 scope 的 Key 并授权；1 小时授权只留在当前 MCP 进程。模型只得到 API 响应，不得到 Key。v1 的 Agent 名称为本机配置声明，调用进程强身份和服务器状态同步列为后续增强。
- 接入文档新增 [16-AGENT_LOCAL_KEY_MANAGER.md](./16-AGENT_LOCAL_KEY_MANAGER.md) 和 [17-AGENT_ORDER_DRAFT_RUNBOOK.md](./17-AGENT_ORDER_DRAFT_RUNBOOK.md)。

## 2026-09-11 最新基线（优先于下方历史快照）

- 生产已以 commit `12e1eb91c19401dde3919afec0b3d80cbc910750`、release `20260911_032005` 从 Flyway V42 升级至 V58。NAS 与 Mac 双份备份均通过 SHA-256；维护模式已关闭，内外网恢复访问，可信外网入口为 `https://www.chenjianas.asia:33294`。
- 145 张旧订单全部迁移，人工核对 0，重放新增 0：129 单为 `COMPLETED/RECORD_ONLY/SETTLED`，14 单为 `CONFIRMED/UNDECIDED/PARTIAL`，1 单为 `CONFIRMED/UNDECIDED/UNPAID`，1 单为 `CANCELLED/UNDECIDED/SETTLED`。订单总额 `367811.00`、实收 `367145.00`、余额 `666.00`，迁移前后守恒。
- 生产现有 144 条历史实收期初流水、187 个商品、699 个 SKU，其中 187 个占位 SKU；全部订单/财务/SKU 发布 SQL 门禁为 0。
- 备份：NAS `/volume2/blade/db-backups/nas_blade_project_prod_20260911_032005`；Mac 持久副本 `/Users/chenjiarun/Documents/BladeProject生产备份/nas_blade_project_prod_20260911_032005`（目录 `700`、文件 `600`、SHA-256 已验签）。release manifest：`/volume2/blade/releases/20260911_032005/blade-release-manifest-20260911_032005.txt`。
- `BE-1052` 已完成。下一主线是 `TEST-PHASE2-001` 真实纸单本地验收与 `TEST-PHASE2-002` 外网 30 单联调；完整财务仍后置。

## 2026-09-10 基线

- Codex 发布前复审发现并修复 JWT 租户/注销/刷新缺口：访问令牌携带租户，过滤器要求 Redis 活跃会话、在用户查询前恢复租户，声明与 Redis 不一致时拒绝，并在请求后清理线程上下文；刷新接口拒绝 access token，refresh token 采用 Redis 单次轮换，PC/移动端退出同时撤销两类会话。Agent/Collector 保持独立鉴权链。
- Agent 草稿批次上限为 100 张、每单 200 行；`AgentSkuMixService` 同时识别两种历史占位编码。
- 后端全量回归 496/496，`OrderDraftConfirmFinanceTest` 已改为事务内自建真实 SKU，不再依赖历史测试数据，并增加 100 单/每单 200 行硬上限反例；共享类型、PC、移动端生产构建全部通过。真实 HTTP 验证：受保护接口注销前 200、注销 200、复用同一 JWT 后 403。一次性空库完成 Flyway V1→V58、3 条内置旧订单迁移（人工核对 0）、幂等重放和全部发布 SQL 不变量检查，验证库已删除。
- `BE-1052` 发布工具已实现：要求干净 Git、同 commit 生产副本预演证据、不可变镜像、维护页、压缩备份+SHA-256+NAS 外副本、历史迁移执行与幂等重放、迁移后 SQL 不变量校验。Web TLS 证书/私钥改为 NAS 私有目录只读挂载，不再进入 Git 和镜像。
- 2026-09-10 将 NAS V42 生产库以一致性快照只读拉取到本地隔离库，完成 145 单 V42→V58 迁移预演、幂等重放和全部 SQL 门禁；人工核对 0，订单总额 367811.00、收款 367145.00 前后一致。该预演随后用于 2026-09-11 正式发布。
- 2026-09-10 将 TrustAsia 证书以 NAS 私有目录只读挂载到 `blade-web`，`https://chenjianas.asia:33294/catalog` 和 `https://www.chenjianas.asia:33294/catalog` 均不带 `-k` 返回 200。证书 2026-11-26 到期且尚无自动续期，最迟需在 2026-11-19 前替换。

## 2026-09-05 最新基线（优先于下方历史快照）

- `feature/order-lifecycle-finance-refactor` 在 V57 基础上新增 V58 Agent Key 生命周期管理：Owner 可在系统管理页签签发、轮换和不可逆停用当前租户 Key；完整密钥仅返回一次，数据库只存 BCrypt 哈希，并记录签发用户、停用时间和轮换来源。
- Mac 纸单 Agent 的 NAS 入口通过 `BLADE_AGENT_API_BASE_URL` 配置，当前外网值为 `https://www.chenjianas.asia:33294`；租户和 scope 仍由 `X-Agent-Key` 决定，接口请求不能自选租户。
- 纸单原图不再是草稿创建前置条件；结构化批次没有 `sourceFileId` 时不再产生缺图警告。现有 source-files 接口只保留为可选凭证兼容。
- 新增 Key 管理与无原图草稿测试通过，后端跳过测试打包和 PC 生产构建通过。全量后端测试因本机 Docker/MySQL 测试库未运行而无法完成，需恢复隔离测试库后重新执行；未连接或修改 NAS 生产环境。

## 2026-09-03 最新基线（优先于下方历史快照）

- 订单重构 `BE-1040`～`BE-1051` 已完成三轮整改并获 `CODEX_APPROVED_FOR_RELEASE_PREPARATION`；仍未执行生产副本迁移、NAS 部署或生产数据变更。
- 已并入旧 ERP 只读审计。后续优先顺序是商品/SKU 库存边界与价格隐私 → 生产副本迁移门禁 → 旧 ERP 期初库存/历史档案 → 无价采购收货；完整资金账户、AR/AP、跨单核销和采购付款暂缓。
- 当前订单财务实现是可追溯的订单专用子账，不等于完整财务模块。后续使用 `ARCH-FIN-002` 设计桥接和加法迁移，不删除、不双写、不把现有订单任务退回 TODO。
- SKU 库存契约：`NORMAL` 和当前真正无规格的 `DEFAULT` 可产生库存事实；`PLACEHOLDER` 与已有真实规格后的历史 `DEFAULT` 只保留订单/分析引用，必须拆分后才能配货、预留或出库。
- 旧 ERP 迁移与 Blade V42 订单迁移是两个独立工作包：前者采用真实 SKU×仓期初快照与历史只读档案，后者由现有 `OrderLegacyMigrator` 处理，禁止混用。

## 2026-08-30 项目状态核对结论

- 订单状态、收款与履约重构方案已确认，详见 [14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md](./14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md)。本次只完成设计和影响面扫描，代码、数据库和生产数据尚未修改。
- 订单金额与统计口径已补充，详见 [15-ORDER_FINANCE_ANALYTICS_DESIGN.md](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md)。客户实收、现金退款、销售退回和短款核销分开记录；订单、现金、结清和库存指标使用各自业务时间。
- 目标模型把草稿状态、收款状态、履约方式和履约状态拆开。正式订单结清后选择 `STOCK_LINKED` 进入库存履约，或选择 `RECORD_ONLY` 直接完成且不影响库存。
- 旧 `sale_order.status` 不原地重解释。实施时新增字符串履约字段、收款流水、状态日志和并发版本，并保留一个发布周期的兼容读取。
- 旧生产备份中的 81 张订单全部为 `status=0`，其中 74 张已结清、6 张部分收款、1 张未收款，且没有配货计划。正式迁移必须按金额和履约证据分流，不能按旧状态数字批量映射。
- 最近两条已完成的本地主线是 WhatsApp 只读归档/客户工作区，以及纸单 Agent 批量草稿/SPU 占位 SKU。
- 纸单草稿的 V48-V50、候选匹配、快速录单式工作台、人工确认和分析隔离已通过本地测试，但尚未部署 NAS 生产，也未完成 30 张真实纸单验收。
- 占位 SKU 的创建、匹配、展示和分析已完成；占位数量拆到真实 SKU、拆分审计、配货与出库保护尚未实现。正式履约前必须补齐这组联动能力。
- 旧 OCR 任务已转由本机订单识别 Agent 承担。BladeProject 不再重复建设图片识别和表格解析，只接收原图与结构化结果。
- WhatsApp 已完成本地真实数据验证，但 Mac → NAS 生产同步、生产凭证和回滚验收仍待执行。
- 任务状态现在区分功能开发、本地验证、生产部署和真实业务验收，详见 [03-TASKS.md](./03-TASKS.md)。
- 订单大重构的联动系统、SOW、Agent 文件边界、Git 分支和 NAS 发布门禁已整理到 [2026-08-30-order-lifecycle-finance-refactor-rom-sow.md](./superpowers/plans/2026-08-30-order-lifecycle-finance-refactor-rom-sow.md)。
- 实施责任已锁定：`ORDER-SOW-0` 已在 `d800ec4` 完成并由 Codex 放行。Z Code 按[长任务文档](./superpowers/plans/2026-08-30-order-refactor-zcode-long-run-task.md)连续完成原 SOW-1～SOW-7、自测和分系列提交，中间不再逐阶段等待；全部完成后 Codex 一次性审核完整代码 Diff，用户仍负责批准生产发布。
- 正式订单采用“两旧两新”：保留旧整数 `status`、`payment_status`；新增字符串 `fulfillment_status`、`collection_status`。`fulfillment_mode` 是履约选择，草稿状态仍在独立草稿表，不新增重复的 `order_lifecycle_status`。
- 本轮整理前，GitHub `codex/phase2-order-drafts` 与本地代码基线同为 `38c969b`，相对 `master` 前进 27 个提交并同时包含 V43-V47 WhatsApp 与 V48-V50 Phase 2；本轮新增设计文档随交接基线提交。已推送功能分支不代表已合入主干或已部署生产。
- 2026-08-30 只读复核 NAS：四个生产容器均运行，生产 Flyway 为 V42，V43-V50 尚未发布。本次核查未修改生产数据或容器。

## 2026-08-30 订单生命周期、财务与统计大重构（长任务系列 A~G 完成，待终审）

- 在 `feature/order-lifecycle-finance-refactor` 分支完成订单大重构连续实施（基线 `1594a8f` → 最终 tip 见交付报告）：V51/V52 加法迁移、统一动作服务 11 动作、统一财务快照与唯一兼容适配器、占位 SKU 拆分与履约保护、`/api/inventory/out-by-plan` 410 收口、PC/移动端/共享类型/导出切换新契约、`OrderFactsService` 统一统计口径并切换全部消费者、客户偏好缓存按订单/财务动作失效、离线迁移工具（dry-run 默认 + 幂等重放）。
- 验证：隔离库（Docker `blade-mysql-test`，端口 3307）空库 Flyway V1→V52 连续升级成功；后端全量测试通过；PC/移动端/类型包构建通过；Playwright 结果见交付报告。迁移工具以合成数据预演，**V42 生产副本预演未执行**（本机无备份，留 Codex/发布阶段）。
- 当时状态曾为 `WAITING_CODEX_FINAL_REVIEW`；此门禁已在后续三轮整改后关闭。BE-1048（旧字段下线）与 BE-1052（V42 迁移 + NAS 发布）仍不在已完成范围。

## 2026-08-27 Phase 2 SPU/SKU 颗粒度补充

- V49/V50/V56 为 `product_sku` 增加并校正 `NORMAL / DEFAULT / PLACEHOLDER`；任何显式规格商品（包括只有一个具体组合）自动维护一个“未指定颜色 / 未指定尺码”占位 SKU，无规格商品使用正常 `DEFAULT` SKU。
- 纸单 Agent 只识别款号时优先匹配占位 SKU；识别到颜色或尺码时排除占位；草稿工作台明确显示“整款（未指定颜色/尺码）”。
- 占位销量计入款号总量，但从真实颜色尺码排行中分离；Agent 分析返回未指定汇总、规格覆盖率和数据质量等级。
- 占位 SKU 不进入对外商品目录和库存可用性判断；生产尚未发布 V49/V50。

## 项目基本信息

| 项目 | 值 |
|------|---|
| 项目名称 | BladeProject |
| 启动日期 | 2026-03-21 |
| 当前阶段 | 后端核心模块、PC 管理端主要业务页面、库存并发控制、跨仓总量预留、配货计划、权限基础能力、订单编辑和追加收款均已落地；统一文件上传和文件中心底座已完成；WhatsApp 本地归档与客户工作区已完成；纸单识别 Agent 的 SKU 候选、批量订单草稿、占位 SKU、草稿工作台和人工确认正式订单 MVP 已于 2026-08-27 完成本地验证；移动端继续开发中 |
| 下一步 | 订单 A～G 已终审通过；先完成商品/SKU 与价格隐私增量回归，再准备 BE-1052 生产副本预演。未经用户生产批准，不部署 NAS。 |

---

## 项目路径

| 项目 | 路径 |
|------|------|
| BladeProject 主目录 | `./` |
| 文档中心 | `./docs/` |
| 后端 | `./blade-backend/` |
| 移动端 | `./blade-mobile/` |
| PC 管理端 | `./blade-admin/` |
| 共享类型 | `./packages/types/` |
| Stitch 原型 | `./stitch/` |

## 当前本地运行环境

| 项目 | 值 |
|------|------|
| MySQL 容器 | `blade-mysql` |
| Redis 容器 | `blade-redis` |
| Nacos 容器 | `blade-nacos` |
| 默认后端数据库 | `blade_project` |
| 本地生产库保留 | `blade_project_prod` |
| 数据库覆盖方式 | `BLADE_DB_URL` / `BLADE_DB_USERNAME` / `BLADE_DB_PASSWORD` |
| NAS 生产环境 | `192.168.1.10:/volume2/blade`，入口 `http://192.168.1.10:8899/catalog` |
| NAS 运维手册 | [13-NAS_PRODUCTION_OPS.md](./13-NAS_PRODUCTION_OPS.md) |
| Git 分支/发布规范 | [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md) |

> 2026-08-17 接手复验备注：本机 MySQL 已停止，Docker `blade-mysql` 已成功占用 `3306`；`blade_project` 开发库启动后由 Flyway 从 V38 迁移到 V40。`test_tenant/admin/admin123` 可登录，真实 Catalog API 返回 `code=200,total=1119`；真实前端 `/catalog` iPad 竖屏冒烟通过。

---

## 单一事实来源

| 信息类型 | 以此文档为准 |
|-----------|--------------|
| 技术栈与业务规则 | [02-PRD.md](./02-PRD.md) |
| 订单状态、收款与履约重构 | [14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md](./14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md) |
| 订单金额、结清与经营统计 | [15-ORDER_FINANCE_ANALYTICS_DESIGN.md](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md) |
| 订单大重构实施分工、Git 与 NAS 门禁 | [2026-08-30-order-lifecycle-finance-refactor-rom-sow.md](./superpowers/plans/2026-08-30-order-lifecycle-finance-refactor-rom-sow.md) |
| 实现 Agent 任务与 Codex 审核门禁 | [2026-08-30-order-refactor-agent-execution-board.md](./superpowers/plans/2026-08-30-order-refactor-agent-execution-board.md) |
| 当前任务进度 | [03-TASKS.md](./03-TASKS.md) |
| 最近变更历史 | [05-CHANGELOG.md](./05-CHANGELOG.md) |
| 分支开发与生产发布 | [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md) |
| 快速接手摘要 | 本文档 |

---

## 当前摘要

### WhatsApp 本地归档 v1（2026-08-24）

- `BE-564` 方案验证已完成，正式实施契约为 [2026-08-24-whatsapp-local-archive-rom-sow.md](./superpowers/plans/2026-08-24-whatsapp-local-archive-rom-sow.md)。
- 已锁定“Mac 只读源 → Git 外加密快照 → 独立 Collector → Blade 内部导入 API → `wa_*` 事实表/文件中心 → 只读 Agent Gateway”的链路。
- v1 保留原 WhatsApp Business 号码，只接 1:1 联系人、会话、文本和已下载媒体；不自动回复、不自动创建 CRM 客户、不让 Agent 直接访问数据库或执行营销。
- `BE-566～BE-576`、`BA-1101～BA-1102` 已完成：V43～V46 建立事实层、采集链路、缺失媒体诊断、混合 Agent 分析队列、领取时上下文快照和客户跟进工作台。
- `BA-1104` 已修复 PC 权限缓存过旧导致“WhatsApp归档”入口不显示的问题；新页面会话会自动刷新一次服务端权限。
- `BE-579`、`BA-1105` 已把缺失媒体改为按聊天号码聚合：首页一个号码一行并显示分类计数，详情抽屉再分页查看该客户全部缺失媒体、打开聊天和重扫。
- `BE-580`、`BA-1106` 已修复 WhatsApp LID 被误作手机号：聚合、展示和打开聊天均优先使用 `wa_contact.phone_normalized`；本机 355 个 LID 会话全部有真实号码映射。
- `BE-578`、`BE-581`、`BE-582`、`BA-1107` 已完成双范围重扫：顶部“扫描整个账号”保留全量能力，客户详情“仅扫描此客户”按真实号码覆盖其 phone JID/LID 会话；服务端只要求补传尚未 IMPORTED 的媒体，定向批次不会恢复其他客户的问题。
- `BA-1103` 已完成 ERP 只读聊天归档：WhatsApp 归档默认进入双栏聊天视图，按真实号码聚合客户，支持文字、图片、视频、音频、贴纸、文档及明确的缺失媒体占位；继续使用 JWT/租户权限且没有发送入口。
- `BA-1108` 已修复 CRM 国际号码绑定：候选匹配会组合客户国家区号与本地号码，并在 WhatsApp 归档加载时自动重算；真实样本 `+243 + 835453734` 已与 WhatsApp `243835453734` 生成待确认候选。
- `BA-1109` 已让绑定结果可见：页面分开展示待确认/已绑定，说明绑定与 ERP/Agent 分析的关系，并可跳客户档案或聊天；聊天窗口修复网格高度约束，打开默认在最新消息，向上滚动每次加载 50 条更早消息且保持阅读位置。真实样本 2,595 条聊天已验证 50→100 条连续加载，`Sbk(刚果金) Fashion+243` 已在已绑定列表显示。
- `BE-583`、`BA-1110` 已把 WhatsApp 正式嵌入 ERP 客户详情：客户档案新增 WhatsApp 页签，直接处理待确认绑定并显示只读聊天、缺失媒体、同步状态和“仅扫描此客户”；真实本地 API 样本 `Sbk(刚果金) Fashion+243` 返回 212 条已归档消息。Mac → NAS 生产接入已记录在 [2026-08-26-whatsapp-nas-production-integration-plan.md](./superpowers/plans/2026-08-26-whatsapp-nas-production-integration-plan.md)，仍明确后置且尚未部署生产。
- Mac Collector 已升级为 v0.2，支持 `configure`、`sync` 和 `watch`：从一致性快照生成结构化 spool，分块上传 ERP；后台可领取 ERP 发起的重扫任务。
- 合成端到端验证：首次导入 5 条逻辑消息、4 条媒体元数据和 1 个文件；重复导入总数保持 5/4/1；补载旧图片后保持 5 条消息、4 条媒体并新增第 2 个文件，账号问题状态变为 2 个待处理、1 个已恢复。
- 混合 Agent 链路已实现：ERP 以独立 scoped Worker Key 提供最近 90 天/最多 200 条的脱敏上下文和订单商品汇总；NAS Worker 可接本地或 OpenAI-compatible 云端模型，结果必须携带有效消息证据，用户只在 ERP 采纳、忽略或完成。
- 自动化验证通过：Flyway 已到 V47，后端全量测试、前端生产构建和 WhatsApp Playwright 通过，Collector/Worker 15 项测试通过；覆盖脱敏、幂等、非法证据、失败重试、跨租户队列隔离、目标 phone/LID 合并与定向问题恢复隔离。
- 2026-08-25 已完成本地真实数据部署验证：ERP 后端 `127.0.0.1:18080`、Admin `127.0.0.1:5777`、Mac Assistant 自动同步；成功批次导入 1,527 联系人、989 会话、32,050 消息、17,132 媒体元数据、2,140 已下载媒体和 14,992 待恢复项。真实内容只进入本机测试库/文件中心，未进入 Git、NAS、生产或模型。
- 真实定向扫描验收：目标客户仅处理 1 条消息（全量基准 32,050 条），全局 14,586 条待恢复记录保持不变；目标客户媒体未下载时其 1 条问题仍正确保持待恢复。只读聊天以 `+234 803 391 2244` 验收，返回 83 条消息，图片和视频预览均为 200，视频 Range 为 206；生产/NAS 尚未部署，上线时仍由运维一次配置，业务用户无需终端操作。

### 当前 Git / 发布规则

- 当前已建立分支规范：[reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md)。
- `master` 定义为生产稳定分支，NAS 生产环境只部署 `master`。
- 新功能默认使用 `feature/*` 分支开发，集成测试使用 `develop`，上线候选使用 `release/*`。
- 当前大范围开发快照分支为 `snapshot/current-all-work-20260611`，用于保存 2026-06-11 分类提交后的完整开发成果；是否整体上线需先创建/验证 release，再合入 `master`。
- GitHub 远程通道为 `origin = https://github.com/CHENjiarun66/blade-project.git`；push/fetch 失败时先检查代理和认证，不要擅自更换远程仓库。

### 已完成的关键能力

- 后端商品、库存、订单、客户、权限、看板接口主体已完成。
- 库存并发控制已完成，包含 Redis 分布式锁和乐观锁。
- 跨仓总量预留已完成，`inventory_global_reserve` 和 `global_reserved_qty` 已落地。
- 配货计划与调整记录已落地，订单支持 `ADJUSTMENT_PENDING`、`READY_TO_SHIP` 等中间状态。
- 订单状态机 4 项缺陷已修复（paymentStatus 同步、adjustmentStatus 初始化、减配释放多余预留、取消条件校验）。
- 订单编辑功能已实现（客户信息/送货/备注/图片，支持 status>=4 禁止修改）。
- 追加收款与抹零/短款结清已实现：收款不锁库存，`payment_status=2` 表示已结清；尾款统一按 `max(total-refund-writeOff-paid,0)` 计算。
- `blade-admin` 已完成订单、库存、商品、客户、系统管理等主要页面。
- 前端菜单权限过滤已完成，系统可按权限展示菜单和路由。
- `packages/types` 已搭建并被移动端集成使用。
- **客户模块国际化已完成**：国家区号选择器（WhatsApp 风格可搜索下拉，约 140 个国家/地区，支持中英文+区号筛选）、客户详情页（3 个独立 Tab：基本信息/订单记录/商品偏好，支持颜色/尺码/品类偏好柱状图）。E2E 测试全部通过（12/12 测试用例）。
- **客户模块优化 Phase 4.6 M1~M4 全部完成**：M1 数据质量（BE-412~414）✅ + M2 用户体验（BE-415~417）✅ + M3 业务功能（BE-418~420）✅ + M4 架构能力（BE-421~423）✅ 已完成
- **库存周转分析已完成**：仪表盘新增库存周转率、库存总量、库存积压预警卡片（BA-603），平均在库天数已移除。
- **仪表盘订单统计口径已调整**：订单统计按 `order_date`（为空回退 `create_time`）+ 已产生收款订单（`paid_amount > 0` 或 `payment_status in (1,2)`）+ 应收净额（`total_amount - refund_amount - write_off_amount`，最低 0）统计，并新增毛利和销量统计。
- **数据分析页 v1 已完成**：新增 `/analytics` 独立页面，支持经营汇总、趋势、商品/SKU/颜色/尺码排行和商品详情拆解；新增 `menu:analytics` 与 `data:analytics:profit` 权限，毛利/成本/毛利率按权限展示。
- **保持登录 30 天已生效**：登录页 `remember` 会传到后端，勾选时 refresh token 有效期为 30 天并在续期时延续；access token 保持 1 小时，前端会在业务请求发出前发现 10 分钟内过期并主动刷新。
- **订单导出已完成**：订单列表页新增导出按钮，支持筛选条件导出 Excel（BA-204）
- **订单列表筛选确认按钮已完成（BA-214）**：订单列表筛选区新增“确认筛选”按钮，关键字回车提交；日期范围已传入后端并按 `order_date` 查询，旧数据为空时回退 `create_time`；导出复用同一筛选条件。
- **快速录单商品级批量录入已完成（BA-207）**：选择商品后展示正常状态 SKU 颜色 x 尺码矩阵，批量填写数量并一次性添加到订单明细；第一版不读取、不展示、不校验库存；重复 `skuId` 自动合并数量且不覆盖已改单价/成本价。SOW 见 [2026-06-11-quick-order-product-batch-entry-sow.md](./superpowers/plans/2026-06-11-quick-order-product-batch-entry-sow.md)。
- **个人中心已完成**：个人中心页面（用户信息展示、修改密码）、头部下拉菜单（BA-704）
- **统一文件存储第一版已完成**：新增 `file_storage` 表、统一上传/预览/软删除/绑定接口，本地存储落地；订单图片、PC/移动端入库凭证、商品主图均已改为上传后保存 fileId；浏览器原生 `<img>`/新窗口预览通过 `/api/files/{id}/preview?previewToken=...` 进入统一权限校验，后续可切七牛云/NAS。
- **图片派生图第一版已完成**：V38 新增 `file_derivative`；上传图片后生成 `thumb`（长边 320px）和 `card`（长边 800px），生成失败不影响原图；`/api/files/{id}/variant` 继承原图权限并缺失回退原图。PC 商品/订单/文件中心与 Catalog 已分层接入，Catalog IndexedDB 缓存按 `original/thumb/card` 隔离。历史图片需通过当前租户批量接口分批补生成。
- **文件中心/数字资产中心后端底座已完成 Phase 6.6**：新增 [12-FILE_CENTER_ASSET_DESIGN.md](./12-FILE_CENTER_ASSET_DESIGN.md)，明确文件中心不是单纯图片/视频相册，而是通用数字资产中心；BE-1001~BE-1011 已完成到资产表结构、分页/详情、文件夹、多业务绑定、批量操作、有效绑定删除保护、未绑定治理、第一版安全清理调度、商品/SKU 图片绑定、基础视频上传分类、私有预览权限和文件中心回归测试。清理调度默认关闭，按配置 tenant-id 处理单租户，仅软删除/标记元数据，不做真实物理删除。
- **后端测试基线已修复**：独立分支 `fix/backend-test-baseline` 已完成 BE-1030~BE-1033；`cd blade-backend && mvn test` 通过（Tests run 244, Failures 0, Errors 0, Skipped 0），修复范围包含测试认证夹具、订单状态机断言和商品/文件实体显式列映射。
- **NAS 生产环境已初步部署完成**：生产目录 `/volume2/blade`，前端入口 `http://192.168.1.10:8899/catalog`；容器为 `blade-mysql`、`blade-redis`、`blade-backend`、`blade-web`；NAS 数据库已从本机生产库 `blade_project_prod` 迁移并将主租户 code 调整为 `dwy_jiajiadress`。后续发布/备份/回滚按 [13-NAS_PRODUCTION_OPS.md](./13-NAS_PRODUCTION_OPS.md) 执行。

### 仍在进行或未完成的事项

- `TEST-ORDER-INV-001` 已完成：MySQL 8 临时库 V1-V40 累计迁移通过；后端全量 `mvn test` 383 项通过；PC `npm run build` 通过；浏览器关键路径覆盖 UI 登录、订单创建、定金、追加收款、抹零结清、配货计划、确认调整、发货和详情页渲染。
- 仪表盘数据权限尚未实现。
- 外部 Agent Gateway 默认只读；安全边界、认证审计、款式趋势和颜色尺码结构已完成。2026-08-27 新增唯一已批准的窄范围写入：本机纸单识别 Agent 可用 `agent:orders:write` 批量创建订单草稿，但不能确认正式订单、调整库存或确认收款。客户跟进/风险、库存建议、周期分析、统一搜索和限流验证尚未完成。
- 文件中心/数字资产中心后端 BE-1001~BE-1011 已完成；PC `/files` 页面 BA-1001~BA-1006 已完成（路由菜单、虚拟入口文件夹树、网格列表视图筛选分页、上传移动删除、商品SKU绑定弹窗、未绑定清理管理），V36 已补齐 `menu:file` 与文件中心按钮权限；Catalog 聚合接口和 `/catalog` 展示页第一版已完成，V37 已补齐 `menu:catalog` 与 `data:catalog:view` 权限。
- 图片派生图/缩略图性能优化 `BE-1012`、`BA-1007`、`BA-1028` 已完成第一版；本机测试环境 tenant 1 的 89 张历史图片已补齐 178 个 `thumb/card` 派生文件，0 失败、0 缺失。生产环境补生成及后续异步队列、自动重试、视频封面和 NAS/七牛云/CDN Provider 尚未执行。
- 商品管理 v2 已完成：`BE-1013` 商品素材查询 API、`BE-1014` 删除引用保护与 SKU 精细更新、`BA-407~BA-410` 商品编辑页 v2/SKU 明细/商品素材/删除禁用交互均已落地；ROM/SOW 见 [2026-06-14-product-management-v2-rom-sow.md](./superpowers/plans/2026-06-14-product-management-v2-rom-sow.md)。
- 文件预览权限补强已完成：PRIVATE 文件仍由后端校验登录、租户、业务权限；前端所有 fileId 预览必须走 `filePreviewUrl(fileId)`，不要手写 `/api/files/{id}/preview`，否则浏览器 `<img>` 不会带认证信息。
- 移动端页面开发仍在继续。
- OCR 拍照录单等任务仍未完成。

---

## 当前阻塞与风险

| 问题 | 优先级 | 状态 | 说明 |
|------|--------|------|------|
| 仪表盘数据权限 | P2 | 🔴 未实现 | 后端统计接口尚未按权限过滤数据 |
| 文档状态漂移 | P1 | 🟡 持续治理 | 2026-06-18 已修正商品 v2、看板和 Agent Gateway 的已知入口状态；后续每轮交接继续以 TASKS、CHANGELOG 和代码交叉核对 |
| 文件中心边界漂移 | P1 | 🟡 已设边界 | 第一版不得漂移到视频转码、分片上传、七牛云/NAS、公开分享链接；以 `12-FILE_CENTER_ASSET_DESIGN.md` 为准 |

**说明**：
- 订单与库存开发前，优先阅读 [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md) 和 [06-ORDER_INVENTORY_DESIGN.md](./06-ORDER_INVENTORY_DESIGN.md)。
- 当前代码真相优先于过时文档；若发现冲突，以 `TASKS + CHANGELOG + 代码实现` 交叉核对。

---

## 最近完成的代表性能力

### 配货计划与订单状态扩展

- 后端已实现 `OrderDeliveryPlanService`、配货计划 CRUD、调整记录、确认/取消调整。
- 订单状态已扩展到包含 `ADJUSTMENT_PENDING` 和 `READY_TO_SHIP`。
- `blade-admin` 订单详情页已支持创建、编辑、确认、取消配货计划和查看调整记录。

### 订单编辑与追加收款

- 订单列表页新增编辑按钮，弹窗顶部显示订单上下文摘要（订单号/状态/金额），支持编辑客户信息/送货方式/备注/图片 fileId。
- 订单详情页追加收款支持普通收款与标记结清；结清原因和核销金额可追溯，已结清订单不再显示追加收款按钮。
- 后端 `GlobalExceptionHandler` 补充 `RuntimeException` 专项处理，业务校验错误不再返回 500。

### 跨仓总量预留

- `inventory` 表已增加 `global_reserved_qty`。
- 已实现 `globalReserve`、`globalRelease`、`getGlobalAvailableQty`。
- 历史跨仓总量预留结构继续保留，但当前生产订单流程的确认收款、追加收款、取消和减配均不再创建或释放硬预留。

### 权限基础能力

- 后端权限表、角色权限关系、权限判断逻辑已完成。
- `blade-admin` 已落地系统管理页和菜单权限过滤。

### 客户模块国际化与优化计划

- 国家区号选择器（WhatsApp 风格可搜索下拉，约 140 个国家/地区，支持中英文+区号筛选）
- 客户详情页（3 个独立 Tab：基本信息/订单记录/商品偏好，支持颜色/尺码/品类偏好柱状图）
- E2E 测试全部通过（12/12 测试用例）
- **Phase 4.6 M1+M2+M3+M4 全部完成 ✅**：
  - M1：电话重复检查（唯一索引+应用层校验）、删除客户订单保护（进行中订单拦截）、N+1查询优化
  - M2：订单分页（page/size参数）、常用国家置顶（localStorage）、国家选择器键盘导航（↑↓/Enter/Esc）
  - M3：客户标签功能（crm_customer_tag + crm_customer_tag_rel，完整CRUD+分配接口）、沉默客户预警（GET /api/dashboard/silent-customers?days=90）、偏好时间范围筛选（startDate/endDate参数）
  - M4：客户数据权限（create_by 字段 + mine 筛选）、操作审计日志（crm_customer_operation_log 表）、偏好数据 Redis 缓存（1小时 TTL）

### 库存统计与订单导出

- 仪表盘新增库存周转分析卡片：周转率、库存总量、库存积压预警（BA-603），第一行统计卡片随日期范围动态展示周期订单、周期销售额、周期毛利和周期销量
- 数据分析页新增销售+商品分析：经营汇总、趋势图、商品/SKU/颜色/尺码排行、商品详情抽屉
- 订单列表页新增导出按钮，支持筛选条件导出 Excel（BA-204）

### PC 文件中心 BA-1001~BA-1006

- 新增 `/files` 路由和侧边栏菜单（`menu:file` 权限），固定页面标题和优先页面映射。
- 左侧快捷入口：全部文件、未绑定、商品素材、SKU 图片、订单图片、入库凭证、视频、回收站，各入口映射到后端 FilePageDTO 查询参数。
- 集成真实文件夹树 API（`GET /api/file-folders/tree`），支持多层级缩进展示。
- 网格视图：图片卡片缩略图、视频占位、类型角标、绑定标记、多选 checkbox；列表视图：el-table 含 selection 列、预览/文件名/类型/大小/业务/绑定/来源/时间/状态列。
- 筛选栏：keyword 搜索、fileType 下拉、businessType 下拉、网格/列表切换。
- 分页（prev/pager/next）、loading 状态、空状态提示、图片预览弹窗（大图 + 元数据）。
- 上传：隐藏多文件 input，逐个调用 uploadFile(file, 'temp')，loading 态，上传后自动刷新。
- 预览：网格缩略展示使用 `fileVariantUrl(fileId, 'card')`，列表小图使用 `fileVariantUrl(fileId, 'thumb')`；预览弹窗和“打开原文件”继续使用 `filePreviewUrl(fileId)`，两类 URL 都由 `previewToken` 补齐浏览器原生资源请求的认证信息。
- 批量操作：选中后显示工具栏（移动/绑定/删除/取消选择），移动弹窗 radio-group 选文件夹或未归档。
- 删除保护：删除前并行查询 getFileBindings，展示绑定风险详情弹窗，仅未绑定文件可被 batch-delete 删除。
- 商品绑定弹窗：FileBindDialog.vue，remote 搜索商品→选角色 main/gallery/sku_image→sku_image 时显示 SKU 多选→PUT /api/products/{id}/file-bindings。
- 清理面板：FileCleanupPanel.vue，清理说明/保留天数/候选统计刷新/软删除确认/回收站快捷入口。
- 扩展 `blade-admin/src/api/file.ts`：完整 batch 操作/绑定/清理/文件夹创建 API；扩展 `blade-admin/src/api/product.ts`：ProductFileBindingDTO/SkuImageBindingDTO + setProductFileBindings()。
- `npm run build`（Node v22）通过，无 TypeScript 错误。

### 前端图标本地 fallback

- PC 管理端已移除对 Google Material Symbols 字体的强依赖。
- `blade-admin/src/utils/materialIconFallback.ts` 会把现有 `material-symbols-outlined` 图标名转换为本地内联 SVG，避免网络字体加载失败时显示 `dashboard`、`download`、`edit` 等英文。

---

## 快捷索引

| 你想做的事 | 看这个 |
|-----------|--------|
| 了解项目入口与阅读顺序 | [01-README.md](./01-README.md) |
| 了解业务与技术规则 | [02-PRD.md](./02-PRD.md) |
| 查看任务状态 | [03-TASKS.md](./03-TASKS.md) |
| 一眼看清项目进度（自动生成） | [STATUS.md](./STATUS.md)，可视化看板 `outputs/status.html` |
| 查看最近变更 | [05-CHANGELOG.md](./05-CHANGELOG.md) |
| 看项目目录结构 | [reference/PROJECT_STRUCTURE.md](./reference/PROJECT_STRUCTURE.md) |
| 查订单/库存设计 | [06-ORDER_INVENTORY_DESIGN.md](./06-ORDER_INVENTORY_DESIGN.md) |
| 查订单状态、收款和历史迁移方案 | [14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md](./14-ORDER_LIFECYCLE_REFACTOR_DESIGN.md) |
| 查订单金额、结清和统计口径 | [15-ORDER_FINANCE_ANALYTICS_DESIGN.md](./15-ORDER_FINANCE_ANALYTICS_DESIGN.md) |
| 查客户模块优化计划 | [08-CUSTOMER_OPTIMIZATION.md](./08-CUSTOMER_OPTIMIZATION.md) |
| 查图片/附件上传与存储设计 | [09-FILE_STORAGE_DESIGN.md](./09-FILE_STORAGE_DESIGN.md) |
| 查文件中心/数字资产/客户展示页设计 | [12-FILE_CENTER_ASSET_DESIGN.md](./12-FILE_CENTER_ASSET_DESIGN.md) |
| 查 NAS 生产运维发布 | [13-NAS_PRODUCTION_OPS.md](./13-NAS_PRODUCTION_OPS.md) |
| 查 Git 分支、GitHub 同步和上线流程 | [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md) |
| 查外部 AI Agent 对接设计 | [10-AGENT_INTEGRATION_DESIGN.md](./10-AGENT_INTEGRATION_DESIGN.md) |
| 查双 Agent 协作同步协议 | [reference/AGENT_COLLABORATION.md](./reference/AGENT_COLLABORATION.md) |
| 查已知问题和历史坑 | [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md) |
| 排查常见环境问题 | [reference/TROUBLESHOOTING.md](./reference/TROUBLESHOOTING.md) |

---

## 接手建议

如果你是新接手的 AI，推荐阅读顺序：

1. [SESSION_CONTEXT.md](./SESSION_CONTEXT.md)
2. [STATUS.md](./STATUS.md)（自动生成，一眼看清进度）
3. [01-README.md](./01-README.md)
4. [02-PRD.md](./02-PRD.md)
5. [03-TASKS.md](./03-TASKS.md)
6. 开发/合并/上线前补读 [reference/GIT_BRANCH_WORKFLOW.md](./reference/GIT_BRANCH_WORKFLOW.md)
7. 订单/库存相关开发再补读 [reference/ORDER_SYSTEM_ISSUES.md](./reference/ORDER_SYSTEM_ISSUES.md)

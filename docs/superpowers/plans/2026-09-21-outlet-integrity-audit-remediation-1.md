# 档口完整性审计整改报告（第一批）

> 分支：`feature/outlet-access-control`　基线：`0f89527`
> 执行：DeepSeek（`[dsh]`）
> 范围：只读完整性审计后第一批整改（P0-1 发货旁路、P0-2 占位拆分旁路、P0-3 客户统计泄露、P1 角色变更必绑）；不 push/deploy/NAS/生产。
> 依据：`docs/20-OUTLET_ACCESS_CONTROL_DESIGN.md`、ROM/SOW、`DECISIONS_LOG.md` DEC-009、PRD 档口部分、初次完整性审计结论。

---

## 0. 初次审计结论摘要

初次只读审计以设计文档为权威、不采信 `03-TASKS` 完成标签，发现 **3 个 P0 旁路** 与若干 P1：

| 编号 | 问题 | 位置 | 本批处理 |
|---|---|---|---|
| P0-1 | 发货未鉴权：可跨档口发货并扣库存 | `OrderActionService.shipOrder` | ✅ 修复 |
| P0-2 | 占位拆分未鉴权：可跨档口改订单明细 | `OrderPlaceholderSplitService.splitPlaceholderItem` | ✅ 修复 |
| P0-3 | 客户订单/统计/偏好未接档口×人员范围 + 缓存键无范围 + 无鉴权 | `CustomerServiceImpl`/`CustomerController` | ✅ 修复 |
| P1 | 角色改为 SALES 且 `outletIds=null` 时跳过必绑 | `UserServiceImpl.update` | ✅ 修复 |
| P1 | `TenantLineHandler` / 用户/角色/权限写入 tenant=1 兜底 | 多处 | ⏳ 未在本批（见 §4） |
| P1/P2 | 跨租户用户绑定/Agent Key 真实 DB 反例、NONE/负责人/改名快照、回填报告映射明细、移动端范围等 | 多处 | ⏳ 第二批 |

## 1. P0-1 发货范围旁路

- **修复**：`OrderActionService.shipOrder` 改为先 `lockOrder(orderId)`（内部 `selectByIdForUpdate` + `requireAccess`），再做幂等返回、`requireMigrated`、状态、占位明细、配货计划、库存出库与状态变更。保持原状态机、库存与幂等契约不变；`tenantId` 取自锁定订单。
- **测试**：`OrderShipScopeGuardTest`（真实 `OrderAccessPolicy` + `OutletAccessPolicy`，mock 数据层，**不使用 allScope 替身**）：
  - `noneScopeCannotShipOtherOutletOrderAndNothingChanges`：NONE 用户对 B 档口 READY_TO_SHIP 订单发货 → 403；订单状态不变，`inventoryService.outByPlan`/`orderMapper.updateById`/状态日志均未调用。
  - `idempotentAlreadyShippedStillRequiresAccessFirst`：已 SHIPPED 也先鉴权 → 403，绝不以"幂等成功"越过范围。
  - `authorizedScopeStillShipsAndCallsInventory`：`data:outlet:all` + `data:order:peopleAll` 路径仍发货并调用 `outByPlan`。
- **V64 兼容核对**：`V55__order_warehouse_scope.sql:9` 给 ROLE_WAREHOUSE `btn:order:viewAll`，`V64__outlet_permissions.sql:64-76` 将其迁移为 `data:outlet:all`+`data:order:peopleAll`。因此真实迁移库中的仓库角色有 ALL 范围；自定义无范围角色仍 403，**代码未给角色名特权**。

## 2. P0-2 占位 SKU 拆分旁路

- **修复**：`OrderPlaceholderSplitService` 构造器注入 `OrderAccessPolicy`；`splitPlaceholderItem` 在 `selectByIdForUpdate` 之后、读取/删除/插入任何明细之前调用 `accessPolicy.requireAccess(order)`。
- **测试**：`OrderPlaceholderSplitScopeGuardTest`：NONE 用户拆 B 档口占位订单 → 403，`orderItemMapper.selectOne/delete`、`adjustmentLogMapper.insert` 均未调用。
- **回归**：`OrderPlaceholderSplitTest` 构造参数补 `OrderAccessPolicy`（allScope 夹具），原有守恒/审计用例全部保持通过。

## 3. P0-3 客户订单/统计/偏好泄露

- **修复**：
  - `CustomerServiceImpl` 注入 `OrderAccessPolicy`；`getStats`/`getCustomerOrders`/`getPreference` 在查询/计数/分页之前各解析一次 `resolveReadScope(null, false)` 并对 `Order` wrapper 应用 `scope.applySalesPredicate`（档口范围 + 人员范围，默认排除历史 NULL，禁止先分页后过滤）。
  - `getPreference` 缓存键由 `customer:preference:{customerId}:{dates}` 改为 `customer:preference:{scope.cacheFingerprint()}:{customerId}:{dates}`，避免不同租户/档口/人员/绑定复用。
  - `CustomerController` 的 `/{id}/stats`、`/{id}/orders`、`/{id}/preference` 增加 `@PreAuthorize("hasAuthority('btn:customer:viewOrders')")`；客户主档列表/详情/搜索/新增/编辑/删除权限契约不变。
- **测试**：`CustomerOrderScopeIntegrationTest`（真实登录/JWT filter + 真实 DB + Redis）：
  - SELF：仅本人 A 档口订单（stats.totalOrders=1、金额 100、orders.total=1、偏好 1 类）。
  - 负责人（A + `data:order:peopleAll`）：A 档口全人员 2 单，看不到 B。
  - Owner（ALL+peopleAll）：全部已归档 3 单，历史 NULL 默认排除。
  - NONE：全空。
  - 缺 `btn:customer:viewOrders`：三个出口均业务码 403。
  - 缓存隔离：同一客户 self 与 lead 先后取偏好分别为 1/2 类，self 再次命中自身缓存仍为 1。
- **口径**：`CustomerStatsVO.totalOrders` 保持既有口径（可见订单数含未完成）；仅范围收紧，未改金额/状态口径。既有 `OrderFactConsistencyTest` 的客户消费额断言同步为"经营订单且已归档（排除 NULL）"。

## 4. P1 角色变更必绑

- **修复**：`UserServiceImpl.update` 解析最终角色后，无论 `outletIds` 是否为 null 都执行必绑校验：
  - `outletIds != null`：沿用 `validateAndSaveOutletBindings`（含去重、默认属于集合、档口存在且启用）。
  - `outletIds == null`：保留现有绑定（旧客户端兼容），新增 `validateRetainedOutletBindings` 校验最终角色为 SALES 时至少存在一个"启用且未删除"的保留绑定；否则 400 并整体回滚角色变更。
  - `validateAndSaveOutletBindings` 对 `outletIds` 去重且保持首次顺序，重复 ID 不再触发 `uk_user_outlet_tenant` 500。
- **测试**：`UserRoleChangeOutletBindingIntegrationTest`（真实 DB，非 `@Transactional` 以观察提交/回滚）：
  - 非销售无绑定 → 改 SALES/`outletIds=null` → 400 且角色保持 WAREHOUSE。
  - 已有启用绑定 → 改 SALES 成功且绑定保留。
  - 绑定指向禁用档口 → 400 且角色不变。
  - `outletIds=[A,A]` 显式保存 → 去重为 1 行，无唯一键异常。

## 5. 验证

| 项 | 结果 |
|---|---|
| targeted | `OrderShipScopeGuardTest` 3、`OrderPlaceholderSplitScopeGuardTest` 1、`CustomerOrderScopeIntegrationTest` 6、`UserRoleChangeOutletBindingIntegrationTest` 4、`OrderPlaceholderSplitTest` 9、`OrderFactConsistencyTest` 2，全部通过 |
| 后端全量 `mvn test` | **791/791**，Failures 0 / Errors 0 / Skipped 0（上一轮 777，新增 14 例） |
| PC 构建 | 本轮无前端代码变更，沿用上一轮通过结果 |
| 关键 E2E | 本轮无前端变更，沿用上一轮 16 passed；后端范围由上述真实 DB/JWT 测试覆盖 |
| `git diff --check` | 无输出；workspace clean |

## 6. 提交

| commit | 内容 |
|---|---|
| `43a3f5e` | `fix(outlet): enforce outlet scope on shipping, split and customer order exits [dsh]`（代码 + 14 例测试） |
| 本文档所在 commit | `docs(outlet): record outlet integrity audit remediation batch 1 [dsh]` |

## 7. 剩余外部/后续

- P1：`TenantLineHandler` 与用户/角色/权限写入的 tenant=1 兜底改为 fail-closed；跨租户用户绑定/Agent Key 的真实 DB 反例；NONE 用户/负责人 ALL_USERS/档口改名快照测试；回填报告补映射明细与 operator。
- P2：软删语义、租户默认唯一并发、列表 N+1、索引核对补全、`lockForFinancialAction` 幂等短路先鉴权、Agent 草稿 403→200 契约、文件列表 `peopleAll` 的 `create_by=null`、Dashboard/Analytics 页面权限、移动端档口范围声明。
- 生产/外部：真实生产副本回填预演、初始授权确认、备份/灰度/回滚、生产规模性能、Series G。

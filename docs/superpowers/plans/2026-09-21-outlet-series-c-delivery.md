# Series C 交付报告：统一后端数据访问策略与授权矩阵

> 分支：`feature/outlet-access-control`　基线：`28909c6`
> 执行：DeepSeek（`[dsh]`）
> 范围：BE-OUTLET-004/005/006 后端；不进入 Series D UI/订单档口选择器，不 push/deploy/NAS/生产。
> 依据：设计文档第 5/9.2/10 章、ROM/SOW Series C。

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
- `create`（手工）记录 `created_by_user_id`；Agent 草稿保留 `created_by_agent_key_id`。
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

- Series D 之前手工快速录单仍产生 `source_outlet_id = NULL` 草稿；此类草稿按遗留/待归档处理，只有 `data:outlet:unassigned` 可读，且**确认被阻断**（`请先归档档口`）。Series D 接入档口选择器后写非空档口。
- 未实现统计/导出/文件/Agent 全出口的档口范围（Series E）。
- 缓存键尚未纳入档口范围摘要（Series E）。
- 未建独立 `data:outlet:unassigned` 的跨租户/越权专项用例矩阵（已在 `OutletAccessPolicyTest` 覆盖 NONE/跨维度；Series E 全出口回归补齐）。

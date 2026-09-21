# Series E3 交付报告：Agent Key 档口范围、capabilities/outlets 与全出口反泄露

> 分支：`feature/outlet-access-control`　E3 基线：`95f0154`（E2 第二轮终审整改完成）
> 执行：DeepSeek（`[dsh]`）
> 范围：BE-OUTLET-010、BA-OUTLET-006、TEST-OUTLET-003；不进入 Series F/G，不 push/deploy/NAS/生产。
> 依据：[20-OUTLET_ACCESS_CONTROL_DESIGN.md](../../20-OUTLET_ACCESS_CONTROL_DESIGN.md)、ROM/SOW Series E、Series E2 交付报告。

---

## 0. 提交

| commit | 内容 |
|---|---|
| `be2ed13` | `feat(outlet): add agent key outlet scope management and agent outlets API [dsh]`（BE-OUTLET-010 后端、capabilities/outlets、154 例 agent/outlet 测试） |
| `1e1fef9` | `test(outlet): assert agent never gets unassigned null access [dsh]`（Agent 不得访问未归档 NULL） |
| `65f38e1` | `feat(outlet): add agent key outlet scope editor to key manager [dsh]`（BA-OUTLET-006 前端 + Playwright 2 例） |
| 本文档所在 commit | `docs(outlet): record Series E3 delivery [dsh]`（TASKS/CHANGELOG/SESSION_CONTEXT/API_SPEC/ROM-SOW/本报告/STATUS） |

## 1. 契约

### 1.1 Key 管理（JWT / `agent-key:manage`）

`AgentKeyManagementDTO` 扩展：

| DTO | 新增字段 |
|---|---|
| `CreateRequest` | `outletScopeType`(String, 可空)、`outletIds`(List<Long>, 可空)、`defaultOutletId`(Long, 可空) |
| `RotateRequest` | 同上；三字段为 `null` 时分别继承旧 Key |
| `View` | `outletScopeType`、`defaultOutletId`、`outlets: OutletSummary[]`（id/code/name/status/isDefault） |
| `Credential` | `outletScopeType`、`defaultOutletId`、`outlets` |
| `AdminOutletOption` | 管理者可配置档口：id/code/name/status/tenantDefault |

接口：

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/system/agent-keys` | 列表含档口范围摘要 |
| GET | `/api/system/agent-keys/scopes` | 白名单含 `outlets:read` |
| GET | `/api/system/agent-keys/outlets` | 本租户可配置档口（含禁用，不受管理账号自身 ASSIGNED 范围限制） |
| POST | `/api/system/agent-keys` | 签发 Key + 同事务写 `agent_key_outlet` |
| POST | `/api/system/agent-keys/{id}/rotate` | 单事务签发新 Key、写绑定、停旧 Key |

### 1.2 Agent API

| Method | Path | scope | 说明 |
|---|---|---|---|
| GET | `/api/agent/capabilities` | 有效 Key，无需额外业务 scope | `keyPrefix`/`name`/`scopes`/`expiresAt`/`serverTime`/`outletScopeType`/`defaultOutletCode`/`readableOutlets`/`usableOutlets`；只暴露 code/name/status |
| GET | `/api/agent/outlets` | `agent:outlets:read` | 仅启用且可用档口（code/name/default）；NONE 为空；缺 scope 403 |

`AgentCapabilitiesDTO.OutletBrief(code,name,status)`、`AgentOutletsDTO.OutletItem(code,name,defaultOutlet)` 均不含内部 outlet id。

## 2. 权限矩阵

| 主体 | 端点授权 | 档口范围来源 | 说明 |
|---|---|---|---|
| JWT 用户 | `btn:*` / `menu:*` | `OutletAccessPolicy.resolveUserScope` | `data:outlet:all` 全部启用档口，否则 `sys_user_outlet` 绑定；`data:outlet:unassigned` 才可读/写 NULL |
| Agent | `agent:<scope>`（`agent-key:manage` 管理） | `OutletAccessPolicy.resolveAgentScope` | `ALL`=租户全部未删档口；`ASSIGNED`=绑定∩本租户；`NONE`=空；`unassignedAllowed` 恒为 false |

| scope（存储名） | authority | 用途 |
|---|---|---|
| `orders:read` | `agent:orders:read` | Agent 订单列表/详情（数据范围由档口范围决定） |
| `orders:write` | `agent:orders:write` | Agent 草稿创建 |
| `analytics:read` | `agent:analytics:read` | Agent 款式趋势/SKU 结构（经 `OrderReadScope`） |
| `outlets:read` | `agent:outlets:read` | Agent 查询可用档口选项 |

端点授权（能不能做）与档口范围（能在哪些档口做）分离；`outlets:read` 缺省不放行，但 `capabilities` 无需该 scope。

## 3. Key 轮换事务

- `create` 与 `rotate` 均为 `@Transactional`。
- `rotate` 顺序：校验旧 Key ACTIVE → 归一化并校验目标档口配置 → 插入新 Key → 写新 `agent_key_outlet`（仅新配置，不复制旧行）→ 停用旧 Key。
- 字段继承：`scopes`/`outletScopeType`/`outletIds`/`defaultOutletId` 为 `null` 时分别继承旧值；`outletScopeType` 显式改变时不继承旧绑定/默认，避免 ALL 携带 ASSIGNED 脏绑定。
- 任一步失败：整事务回滚，旧 Key 仍 ACTIVE，无半成品新 Key/绑定（单测 `rotateFailureKeepsPreviousKeyActiveAndDoesNotDisable` 覆盖 mock 写入失败路径）。
- `ALL` 不写逐档口冗余绑定，仅在显式默认档口时写一条 `is_default=1` 标记供读取解析；`ASSIGNED` 每个档口一行；`NONE` 无绑定。

## 4. 缓存结论（B4）

`AgentKeyAuthenticationService.authenticate` 每次请求按 `key_prefix + status=1` 直查数据库并用 BCrypt 校验，无缓存。`OutletAccessPolicy.resolveAgentScope` 每次调用都重新 `selectById` 读取 `agent_key` 状态/有效期/`outlet_scope_type`，并重新读取 `agent_key_outlet` 有效绑定，因此：

- Key 停用/过期 → 认证层 401（下一请求立即生效）；
- 档口绑定变更 / 档口禁用 / Key 轮换 → 下一请求立即反映，不依赖 principal 构造时快照。

没有任何 cache key，也不存在跨租户/跨 scope 复用；报告如实说明“无缓存，无需失效逻辑”。

## 5. 校验规则

- 所有 `outletIds` 必须同租户、未删除；去重并按 id 稳定排序。
- `NONE`：禁止 `outletIds` 与 `defaultOutletId`；`null` 范围安全解释为 NONE，绝不默认 ALL。
- `ALL`：不保存逐档口绑定；显式 default 必须为本租户启用档口。
- `ASSIGNED`：至少一个档口；default 必须在集合内且启用（仅一个启用档口时可自动默认）；禁用档口可作为历史读绑定保留但不能设默认。
- 跨租户/已删除/不存在档口统一 400；Agent 写入口显式内部 `sourceOutletId` → 400，越权/禁用 `sourceOutletCode` → 403。
- 管理者可配置档口选项含禁用档口，供“历史绑定展示但不可新选/默认”。

## 6. 前端（BA-OUTLET-006）

- `src/api/agentKey.ts`：新增 `AgentKeyOutletScopeType`、共享 `AgentKeyOutletSummary`、`AgentOutletOption`；View/Credential/Create/Rotate 类型补齐档口字段；新增 `getAgentKeyOutletOptions()`（`GET /system/agent-keys/outlets`）。
- 新增 `src/views/system/AgentKeyOutletScopeEditor.vue`（create/rotate 共用同一 v-model 编辑器）：
  - `ALL` 全部档口 / `ASSIGNED` 指定档口 / `NONE` 不开放档口数据；
  - `ASSIGNED` 多选展示 `code+name`；停用档口仅在“已是当前选择（历史绑定）”时可保留，不能新选；默认档口单选限“已选且启用”，仅一个启用档口时自动默认；
  - `ALL` 默认档口限启用档口；`NONE` 不显示选择器并清空非法值。
- `AgentKeysPanel.vue`：列表新增“档口范围”列（ALL 标签 / ASSIGNED 档口标签含已停用灰显 / NONE 信息标签 + 默认档口）；新建默认 NONE；rotate 预载旧范围并提交完整配置；订单/分析权限 + NONE 显示非阻塞警告但仍可保存；`outlets:read` 标签“读取可用档口”；凭证弹窗增加“先 capabilities 再 outlets”说明与可复制 curl 片段（`<KEY>` 占位，绝不展示内部 outlet ID）；明文 Key 仍只显示一次。
- 新增 `e2e/e2e-agent-key-outlet-scope.spec.ts`（mock 路由）：新建弹窗范围联动/告警/停用档口不可新选/单启用自动默认；rotate 弹窗预载旧范围。

## 7. 测试与验证

后端（真实隔离库 + mock 策略）：

- `AgentKeyManagementServiceTest`（23 例）：create ALL/ASSIGNED/NONE、跨租户/删除/禁用、default 非集合/未启用、重复 ID 去重、ALL 冗余绑定拒绝、rotate 继承/显式替换/改范围不继承脏绑定/失败保留旧 Key、`outlets:read` 白名单、可配置档口含禁用。
- `AgentOutletScopeIntegrationTest`（8 例，真实 MVC + Redis 会话）：capabilities 返回档口摘要且不含内部 id、档口禁用下一请求即时反映、Key 停用 401、outlets 缺 scope 403/缺 Key 401、ALL 只含启用、NONE 为空、ASSIGNED 只含绑定启用且 default 正确。
- `AgentDataAccessIntegrationTest`（10 例）：新增 `assignedOutletCannotReachOtherOutletThroughAnyAgentOutlet`，串联 orders list/detail、capabilities、outlets、draft create（code/内部 ID）反例，确认 A 档口 Key 无法推断 B 档口数据。
- `AgentDataAccessContractTest`（4 例）：`AgentOutletsController` 必须 `hasAuthority('agent:outlets:read')`；capabilities/outlets DTO 不含内部 id。
- `OutletScopeMatrixTest`（14 例）：新增 `agentNeverGetsUnassignedNullAccess`（ALL/ASSIGNED/NONE 均不可访问未归档 NULL）与 `agentAnalyticsReadScopeOnlyAllowsAssignedOutlets`（统计读范围只用 ASSIGNED 集合，越权/跨租户/待归档 403）。
- 全量后端 `mvn test`：**735/735**，Failures 0 / Errors 0 / Skipped 0（E3 基线 708，新增 27 例）。

前端：

- `npm run build`（`vue-tsc -b && vite build`）：通过（仅既有 chunk 体积告警）；`npx vue-tsc --noEmit` 无输出。
- Playwright（真实本地后端 + Vite）：
  - 新增 `e2e/e2e-agent-key-outlet-scope.spec.ts` **2 passed**（mock 路由）。
  - 档口/订单/草稿/分析回归 6 个 spec **14 passed**：`e2e-outlet.spec.ts`、`e2e-order-outlet.spec.ts`、`e2e-analytics-outlet.spec.ts`、`e2e-file-outlet-filter.spec.ts`、`e2e-quick-order-draft.spec.ts`、`e2e-manual-draft-confirm.spec.ts`。
  - Agent 业务出口（orders/draft/capabilities/outlets）由后端真实 MVC 集成测试覆盖（见 §7），无独立 Playwright。
- 命令：
```bash
cd blade-backend && mvn test                                   # 735/735
cd blade-admin && npm run build                                # 通过
npx playwright test e2e/e2e-agent-key-outlet-scope.spec.ts \
  e2e-outlet.spec.ts e2e-order-outlet.spec.ts e2e-analytics-outlet.spec.ts \
  e2e-file-outlet-filter.spec.ts e2e-quick-order-draft.spec.ts \
  e2e-manual-draft-confirm.spec.ts --project=chromium          # 16 passed
```

## 8. 已知问题与未完成边界

- 本机 `AgentKeyManagementService.list()` 对每把 Key 额外读取绑定与档口，属可接受 N+1；Key 数量级小。
- 本地 MCP 工具集（docs 16/19）未在仓库源码内；E3 只保证 HTTP `capabilities`/`outlets` 与 scope 契约，本机是否暴露独立档口工具由 Key Manager 版本决定。
- Series F 未做：历史 `source_shop` → `source_outlet_id` 回填、生产副本预演与对账、发布切换。
- Series G 未做：官方 `btn:order:viewAll` 下线评估、E2E 全矩阵与发布收口。
- 未 push/部署/NAS/生产。

## 9. TEST-OUTLET-003 反例映射

| 出口 | 覆盖 |
|---|---|
| Agent orders list/detail | `AgentDataAccessIntegrationTest.assignedOutletCannotReachOtherOutletThroughAnyAgentOutlet` |
| Agent draft create（code/内部 ID） | 同上（`sourceOutletCode` B → ERROR；`sourceOutletId` → ERROR） |
| Agent capabilities / outlets | `AgentOutletScopeIntegrationTest`（NONE 空、ASSIGNED 受限、ALL 只含启用、缺 scope 403、不泄露 id） |
| Agent 统计/分析范围 | `OutletScopeMatrixTest.agentAnalyticsReadScopeOnlyAllowsAssignedOutlets`（越权/跨租户/待归档 403） |
| Agent 未归档 NULL | `OutletScopeMatrixTest.agentNeverGetsUnassignedNullAccess` |
| JWT 销售员/统计 | 沿用 E1 `OutletAnalyticsScopeTest`、E2 `FileOutletAccessPolicyTest`、`OrderOutletWriteRulesTest` 等 |
| 缓存/轮换不串 scope | `AgentOutletScopeIntegrationTest`（停用/禁用即时 401/空）+ §4 无缓存结论 |

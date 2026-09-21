# Series E1 交付报告：统计/仪表盘/订单导出档口隔离与统计筛选

> 分支：`feature/outlet-access-control`　基线：`ec3bbbf`
> 执行：DeepSeek（`[dsh]`）
> 范围：BE-OUTLET-008、BE-OUTLET-009（本轮先完成订单导出）、BA-OUTLET-005；不进入 Series E2/E3，不 push/deploy/NAS/生产。
> 依据：[20-OUTLET_ACCESS_CONTROL_DESIGN.md](../../20-OUTLET_ACCESS_CONTROL_DESIGN.md)、ROM/SOW Series E。

---

## 0. 提交

| commit | 内容 |
|---|---|
| `aad6b88` | `fix(outlet): scope analytics/dashboard/export to order read scope [dsh]`（后端 `OrderReadScope` + Dashboard/Analytics/导出范围接入、DTO、测试） |
| `9c1a481` | `feat(outlet): add analytics/dashboard outlet filter [dsh]`（前端筛选与序列化、e2e） |
| 本文档所在 commit | `docs(outlet): record Series E1 delivery [dsh]`（TASKS/CHANGELOG/SESSION_CONTEXT/API_SPEC/ROM-SOW/本报告/STATUS） |

## 1. 统一统计读取范围（OrderReadScope）

新增不可变 `com.blade.order.service.OrderReadScope`，由 `OrderAccessPolicy.resolveReadScope(selectedOutletIds, pendingArchive)` 从当前 actor（JWT 用户或 Agent Key）解析：

- 字段：`tenantId / actorId / outletScopeType / peopleAll / unassignedAllowed / readableOutletIds / selectedOutletIds / pendingArchive`。
- `applySalesPredicate`：档口范围（ALL→`IS NOT NULL`；ASSIGNED→`IN readable`；NONE→`1=0`）× 显式档口 `IN` × 人员范围（SELF→`salesman_id=actor`）；**永不包含未归档 NULL**。
- `applyPendingArchivePredicate`：仅 `unassigned` 权限 + `pendingArchive=true` 时用于待归档数量。
- `cacheFingerprint()`：`tenantId + outletScope + peopleScope + actor + allowed(sorted) + selected(sorted) + pendingArchive` 的稳定摘要。
- fail closed：租户为空 403；用户主体无法解析 actorId 403；NONE 返回空范围（`1=0`）不回落全租户；显式档口不在 readable 集合整体 403；`pendingArchive` 与显式档口互斥 400。

`OrderFactsService` 增加范围化查询：`ordersByOrderDate(scope,...)`、`businessOrdersByOrderDate(scope,...)`、`paidBusinessOrdersByOrderDate(scope,...)`、`ordersForScope(scope)`、`pendingArchiveOrders(scope,...)`；保留原 tenant-only 方法供未纳入本轮的消费者（Agent E3 等）使用。

## 2. BE-OUTLET-008 接入点

Dashboard（`DashboardServiceImpl`）：
- `getStats`：period 当前/上一周期、week/last-week、pending 计数全部使用同一 `scope`；新增 `pendingArchiveCount`（仅显式请求时非空）。
- `getOrderTrend`：一次请求解析一次范围，逐日复用。
- `getTopProducts`、`getOrderStatusDistribution`：范围化。
- `getSilentCustomers`：范围化（含每客户最近订单查询）。
- `getInventoryStats`：库存总量等仍为租户全局库存事实；其中 90 天销量（周转率分子）按范围裁剪，避免用范围外订单推断。

Analytics（`AnalyticsServiceImpl`）：
- `getSummary / getTrend / getProductRanking / getProductDetail` 全部改用 `resolveScope`，一次请求解析一次，趋势逐日复用；明细查询使用 `scope.tenantId()`。
- 未归档 NULL 不进入 summary/trend/ranking/product detail。

Agent 边界：`resolveCurrentScope()` 本身识别 `AgentPrincipal`，因此改动后 Agent 的 analytics 也按 Key 档口范围裁剪（安全增强，非回归）；`AgentAnalyticsController` / `AgentStyleTrendService` / `AgentSkuMixService` 的独立出口收口与 capability 展示仍属 **Series E3**，本轮未实现。

## 3. BE-OUTLET-009 订单导出

`OrderServiceImpl.exportOrders` 在 `selectCount` 与 `selectPage` 之前应用：
1. `accessPolicy.applyReadPredicate(wrapper)`（与订单列表完全一致的档口 × 人员范围）；
2. `accessPolicy.applyExplicitOutletFilter(wrapper, dto.getSourceOutletId(), dto.getUnassignedOnly())`（显式档口/待归档校验，越权 403）。

禁止事后内存过滤。导出可见集合与同参数列表一致（测试断言 orderNo 集合相等）。

## 4. BA-OUTLET-005 统计档口筛选

- `DashboardQueryDTO` 新增 `sourceOutletIds: List<Long>` + `pendingArchive: Boolean`（向后兼容，旧请求不变）。
- 前端 `DateRangeFilter` 扩展 `sourceOutletIds?/pendingArchive?`；`buildFilterParams` 将多选序列化为 `sourceOutletIds=1,2`（Spring 可直接绑定 `List<Long>`），空选择不发送（表示当前完整可见范围）。
- 分析页、仪表盘新增档口多选（选项来自 `GET /api/outlets/options`，销售员只会拿到绑定档口）；仪表盘在 `data:outlet:unassigned` 下显示“待归档档口”开关，勾选时清空档口选择并只发送 `pendingArchive=true`，待归档计数单独展示。
- 前端只展示后端返回的数据，后端逐项校验授权；前端隐藏不是安全边界。

## 5. 缓存结论

- 盘点：`com.blade.dashboard` 与 `com.blade.analytics` **当前没有任何缓存**（无 `@Cacheable`/Redis/本地缓存），本轮**不新增缓存**。
- 为防未来错误缓存：`OrderReadScope.cacheFingerprint()` 覆盖 tenant + 人员范围 + 档口范围 + allowed + 显式选择 + pendingArchive；新增测试 `scopeFingerprintChangesWithActorAndSelection` 断言不同 actor/选择/待归档指纹必须不同，同 actor 同选择稳定。
- 交付报告如实说明：本轮没有可被跨用户复用的统计缓存。

## 6. 统计口径与全局指标

- 财务口径**未修改**：继续经 `OrderFactsService` 的已收款/净销售额/净毛利规则（`netSales = max(total - sales_return - write_off, 0)`；毛利同理；已收款 = `gross_received > 0` 或 `SETTLED`）。
- 未归档 `source_outlet_id IS NULL`：默认不纳入销售额、排行、档口对比与待处理订单；仅 `data:outlet:unassigned` + `pendingArchive=true` 时通过独立 `pendingArchiveCount` 查看待归档数量，不混入销售。
- 非订单型全局指标：`totalProducts`、`lowStockAlerts` 以及库存统计的总量/低库存/积压为租户级商品/库存事实，不伪装成档口统计，继续返回；档口对比仅针对订单型指标。`getInventoryStats` 的 90 天销量分子已按范围裁剪。

## 7. 权限矩阵（本轮行为）

| 主体 | 档口范围 | 人员范围 | 统计行为 |
|---|---|---|---|
| Owner/Admin | ALL | ALL_USERS | 默认=全部可读（非归档）；可多选/对比任意可访问档口；可查待归档数量 |
| 档口负责人 | 绑定集合 | ALL_USERS | 仅绑定档口汇总；显式越权 403 |
| 销售员 | 绑定集合 | SELF | 仅本人订单；显式其他档口 403；不含未归档 |
| 无绑定/无范围 | NONE | SELF | 统计为空（`1=0`），不回落全租户 |
| Agent Key | Key scope | ALL_USERS | 按 Key 档口范围裁剪；profit 隐藏；NONE 为空 |
| 跨租户 | — | — | 租户条件独立，跨租户不可见 |

## 8. 测试

后端 `OutletAnalyticsScopeTest`（9 例，真实隔离库）：
- 单档口销售员 summary/trend/ranking/product detail/dashboard 只含 A，不含 B 与未归档；
- 销售员显式 B、批量混入未授权 ID → 403；
- Owner 多档口汇总与按选择对比；
- 待归档权限：无权限 403，授权者 `pendingArchiveCount` 可见且不混入销售（与基线对比）；
- 导出可见集合与列表一致，销售员导不出 B/未归档，越权档口导出 403；
- 租户隔离；范围指纹随 actor/选择/待归档变化。

回归：`OrderFactConsistencyTest` 调整为 Owner 范围 + 真实档口样本，并与范围化事实服务对比；`AnalyticsServiceTest`/`DashboardServiceTest` 注入 mock 范围。

前端 `e2e-analytics-outlet.spec.ts`（2 例）：分析页多选序列化 `sourceOutletIds=1` → `1,2` → 清空不发；仪表盘档口与待归档互斥、待归档计数展示。

验证命令与结果：
```bash
cd blade-backend && mvn test                                   # 659/659
cd blade-admin && npm run build                                # vue-tsc + vite 通过
npx playwright test e2e-analytics-outlet.spec.ts e2e-order-outlet.spec.ts \
  e2e-outlet.spec.ts e2e-quick-order-draft.spec.ts e2e-manual-draft-confirm.spec.ts   # 13 passed
```

## 9. 未完成边界（Series E2/E3）

- E2：订单/草稿图片绑定与预览、文件中心范围接入。
- E3：`GET /api/agent/outlets` 与 capability 档口摘要、Agent Key 签发/轮换档口配置、Agent analytics 独立出口收口与全出口防泄漏回归。
- 统计缓存仍不存在；若未来引入，必须使用 `OrderReadScope.cacheFingerprint()` 纳入 key，并在权限/绑定变化时失效。

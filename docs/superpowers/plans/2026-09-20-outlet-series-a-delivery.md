# Series A 交付报告：档口数据模型与兼容迁移

> 分支：`feature/outlet-access-control`　基线：`191f0d8`（含规划提交）
> 执行：DeepSeek（`[dsh]`）
> 范围：仅 Series A 数据模型，不含 CRUD、权限策略、前端、生产部署。
> 设计依据：[20-OUTLET_ACCESS_CONTROL_DESIGN.md](../../20-OUTLET_ACCESS_CONTROL_DESIGN.md)、[ROM/SOW](./2026-09-20-outlet-access-control-rom-sow.md)

---

## 0. Codex 审核整改记录（2026-09-21）

Codex 独立审核后不放行，本轮（同一分支，未 amend 前三笔 `de5bd89`/`dbe938c`/`f685a98`）补齐以下 Series A 缺口：

- **P0-1 消除档口范围歧义**：V63 为 `agent_key` 增加 `outlet_scope_type varchar(20) NOT NULL DEFAULT 'NONE'`（`ALL`/`ASSIGNED`/`NONE`，不用 `outlet_id=0` 哨兵）。`ALL` 不依赖关联行、新档口自动可见；`ASSIGNED` 必须至少一条有效 `agent_key_outlet`；`NONE` 拒绝档口业务，历史 Key 迁移后默认 NONE 不静默扩权。`AgentKey` 实体新增 `outletScopeType`；契约测试覆盖默认 NONE。
- **P0-2 Key 轮换复制规则**：`AgentKeyManagementService.rotate` 在同一事务内复制旧 Key 的 `outletScopeType` 与当前有效 `agent_key_outlet` 绑定（保留 `is_default`、带 `tenant_id`、不跨租户），旧 Key 仍按原逻辑停用；新建 Key（create）在 Series B/E UI 接入前保持 NONE。新增真实单元测试：轮换复制 scope type、两条绑定与默认标记；复制失败时旧 Key 状态不被错误更新；构造器适配。
- **P1-1 审计脚本映射能力**：`scripts/outlet-source-shop-audit.sql` 新增可由发布人员编辑的名称映射 CTE（`name_mapping`），输出可自动映射/未映射/疑似批次·纯数字（空值见 A1/B1、冲突见 C1），全程只 SELECT、无写语句/DDL/临时表。新增静态测试 `OutletAuditSqlReadOnlyTest` 断言脚本不含写语句且含映射输出。
- **P1-2 文档状态真相**：统一 03-TASKS.md（Series A 摘要区与 Phase 7.1 均已标 ✅）、ROM/SOW Series A 四行标 ✅；DATABASE.md 修正「忽略租户的表」为 `TenantLineHandler` 实际口径（未改拦截行为），并补充 `agent_key.outlet_scope_type` 说明。

本次整改以独立提交记录（见本分支 git log），未 push、未部署、未触碰 NAS/生产、未回填数据。

---

## 1. 只读基线审计结论

| 项 | 结论 |
|---|---|
| 最高 Flyway 版本 | `V62__repair_draft_source_shop_fallback.sql`（迁移目录共 64 个文件，无并行未合入迁移），本轮占用 `V63` |
| sale_order | 已有 `source_shop varchar(100)`（V30）；无 `source_outlet_id`。实体 `Order` 无 `sourceOutletId` |
| order_draft | 已有 `source_shop`（V59）、`source_batch_no`（V48）；无 `source_outlet_id`。实体 `OrderDraft` 无 `sourceOutletId` |
| agent_key | 表已存在（V33 基础 + V58 签发/轮换字段）；实体 `AgentKey` 无档口关联 |
| sys_user | 表已存在（V1）；实体 `User` 无档口关联 |
| 租户拦截 | `TenantLineInnerInterceptor` + `TenantLineHandler`；忽略表 = `sys_tenant/sys_permission/product_color_rel/product_size_rel/sys_role_permission/sys_user_role`；`tenant_id` 缺失时回退 `1L`。新四表不在忽略表，自动受租户过滤 |
| 测试基线 | 项目已有静态 SQL 契约测试（`OrderV51SchemaTest`/`OrderDraftV59SchemaTest`）+ 实体反射测试（`FileAssetSchemaTest`）+ 空库 Flyway 预演模式（`OrderMigrationRehearsalTest`，连接本地 MySQL） |

**与文档不一致点（未扩大范围）**：`DATABASE.md` 6.3 写的忽略表为 `sys_dict/sys_param/sys_tenant`，与代码实际忽略表（见上）不一致；本轮未改动租户拦截配置，仅按代码现状验证新表自动纳入租户过滤。`application.yml` 的 `tenant-line.ignore-tables` 配置是历史残留（真实拦截由 `TenantLineHandler.IGNORE_TABLES` 决定），未改动。

---

## 2. 变更清单（新增/修改文件）

**Flyway（新增 1）**
- `blade-backend/src/main/resources/db/migration/V63__outlet_access_control.sql`

**实体（新增 4）**
- `com/blade/outlet/entity/SalesOutlet.java`
- `com/blade/outlet/entity/SysUserOutlet.java`
- `com/blade/outlet/entity/AgentKeyOutlet.java`
- `com/blade/outlet/entity/OrderOutletChangeLog.java`

**Mapper（新增 4）**
- `com/blade/outlet/mapper/SalesOutletMapper.java`
- `com/blade/outlet/mapper/SysUserOutletMapper.java`
- `com/blade/outlet/mapper/AgentKeyOutletMapper.java`
- `com/blade/outlet/mapper/OrderOutletChangeLogMapper.java`

**实体字段（修改 2）**
- `com/blade/order/entity/Order.java`：新增 `sourceOutletId`（`source_outlet_id`）
- `com/blade/order/draft/entity/OrderDraft.java`：新增 `sourceOutletId`

**审计 SQL（新增 1）**
- `scripts/outlet-source-shop-audit.sql`（只读 SELECT，无 UPDATE/DELETE）

**测试（新增 3）**
- `blade-backend/src/test/java/com/blade/outlet/OutletAccessControlSchemaTest.java`
- `blade-backend/src/test/java/com/blade/outlet/OutletEntityMappingTest.java`
- `blade-backend/src/test/java/com/blade/outlet/OutletFlywayMigrationTest.java`

**文档（修改 3 + 新增 1）**
- `docs/architecture/DATABASE.md`（新增档口模块章节 + sale_order.source_outlet_id + V63 版本）
- `docs/03-TASKS.md`（Series A 任务状态）
- `docs/05-CHANGELOG.md`（变更记录）
- `docs/superpowers/plans/2026-09-20-outlet-series-a-delivery.md`（本报告）

---

## 3. 迁移版本及表/字段

| 迁移 | 对象 | 说明 |
|---|---|---|
| V63 | `sales_outlet` | 新表；`tenant_id`、`outlet_code(30)`、`outlet_name(100)`、`outlet_type DEFAULT 'STORE'`、`is_tenant_default`、`status`、`deleted` 等；`uk_outlet_code_tenant(tenant_id, outlet_code)`、`idx_outlet_tenant_status(tenant_id, status, deleted)` |
| V63 | `sys_user_outlet` | 新表；`tenant_id/user_id/outlet_id/is_default/status/deleted`；`uk_user_outlet_tenant(tenant_id, user_id, outlet_id)`、`idx_user_outlet_user`、`idx_user_outlet_outlet` |
| V63 | `agent_key_outlet` | 新表；`tenant_id/agent_key_id/outlet_id/is_default/status`；`uk_agent_key_outlet(tenant_id, agent_key_id, outlet_id)`、`idx_agent_key_outlet_key` |
| V63 | `order_outlet_change_log` | 新表（只追加）；`idx_outlet_change_order(tenant_id, order_id, create_time)` |
| V63 | `sale_order.source_outlet_id` | 可空 `bigint`；`idx_so_source_outlet(tenant_id, source_outlet_id)`。**未加 NOT NULL** |
| V63 | `order_draft.source_outlet_id` | 可空 `bigint`；`idx_order_draft_source_outlet(tenant_id, source_outlet_id)` |

约束遵守：正式订单 `source_outlet_id` 本轮保持可空；草稿允许为空；`source_shop` 保留不改写；档口不等同于仓库；未创建/回填生产档口；未改金额/状态/明细/图片绑定/source_shop。

---

## 4. 测试命令与结果

```bash
cd blade-backend
mvn test -Dtest='OutletAccessControlSchemaTest,OutletEntityMappingTest'
# → Tests run: 11, Failures: 0, Errors: 0, Skipped: 0

mvn test -Dtest='OutletFlywayMigrationTest'
# → 空库 V1→V63 连续迁移成功（65 个迁移文件，pending=0），临时 schema 自动删除
# → Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

mvn test -Dtest='OrderV51SchemaTest,OrderDraftV48SchemaTest,OrderDraftV59SchemaTest,OrderCompatAdapterTest,OrderActionStateMachineTest,OrderFactConsistencyTest,FileAssetSchemaTest'
# → Tests run: 51, Failures: 0, Errors: 0, Skipped: 0（相关后端回归）

mvn test -Dtest='AgentKeyManagementServiceTest,OutletAccessControlSchemaTest,OutletEntityMappingTest,OutletAuditSqlReadOnlyTest,OutletFlywayMigrationTest'
# → Tests run: 24, Failures: 0, Errors: 0, Skipped: 0（整改后定向回归，含空库 Flyway V1→V63）

mvn test
# → 全量后端测试 Tests run: 550, Failures: 0, Errors: 0, Skipped: 0（含本 Series A 新增 18 项）
```

整改前新增测试 12 项 + 相关回归 51 项；整改后新增 6 项（AgentKey 轮换复制 ×2、审计脚本只读契约 ×2、schema/实体契约各 ×1），合计新增 18 项，全量后端 550/550 全部通过。`OutletFlywayMigrationTest` 在本地 MySQL 8.3 上创建一次性空库执行 V1→V63，验证后删除；未触碰 `blade_project_prod`（生产副本）等库（回归/全量测试按既有约定连接本地 `blade_project` 开发库，属正常测试行为，非生产/NAS）。

---

## 5. 未完成 / 风险项

- **正式订单 `source_outlet_id` 为空的历史数据**：回填前只能由 Owner/Admin 或显式迁移权限查看/归档，由 Series C/F 处理，本轮未实现任何回填。
- **同租户唯一约束靠数据库索引兜底**：`is_tenant_default` / `is_default` 的“每租户最多一个有效默认档口”由 Series B 服务层事务保证，本轮仅提供字段与唯一键，未伪造服务层保障。
- **租户隔离前提**：新四表未加入 `TenantLineHandler.IGNORE_TABLES`，自动受租户过滤；但 `sys_user_outlet` 与 `sys_user_role`（忽略表）不同源，跨租户一致性由 Series B 服务层校验。
- **`source_shop=source_batch_no`、纯数字等疑似批次值**：仅进入审计报告，不自动创建档口。
- **DATABASE.md 与代码忽略表口径的历史不一致**未在本轮修正（见第 1 节），建议 Codex 在 Series B 统一。

---

## 6. 建议 Codex 进入 Series B 前重点审核

1. **迁移版本号**：确认 V63 未被其他 worktree/分支占用，且与本分支提交顺序一致。
2. **实体字段映射**：`Order`/`OrderDraft` 的 `sourceOutletId` 是否与 `OrderCompatAdapter`/VO 投影一致（本轮未接 VO/DTO，Series D 才写入）。
3. **索引口径**：`idx_so_source_outlet` / `idx_order_draft_source_outlet` 采用租户前缀复合索引，是否满足 Series C `IN`/`EXISTS` 查询计划预期。
4. **DATA-OUTLET-001 审计 SQL**：确认报表口径（`SUBSTRING_INDEX(source_doc_no,'_',1)` 的批次段判断）符合真实纸单号格式，再用于生产副本预演。
5. **默认档口唯一性事务边界**：`sales_outlet.is_tenant_default` 与 `sys_user_outlet.is_default` 的服务层唯一保证需在 Series B 落事务与测试，勿依赖仅索引。

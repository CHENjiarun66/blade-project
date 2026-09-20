# Series B1 交付报告：档口主数据与用户授权（后端）

> 分支：`feature/outlet-access-control`　基线：`a02100d`（Series A 收尾）
> 执行：DeepSeek（`[dsh]`）
> 范围：仅后端（档口主数据服务/API、用户多档口绑定、权限迁移 V64），不做前端、不进入 Series C 数据过滤。
> 设计依据：[20-OUTLET_ACCESS_CONTROL_DESIGN.md](../../20-OUTLET_ACCESS_CONTROL_DESIGN.md)、[ROM/SOW](./2026-09-20-outlet-access-control-rom-sow.md)

---

## 1. 交付内容

### BE-OUTLET-001 档口主数据服务/API
- `GET /api/outlets`：分页、关键词（编码/名称）、状态筛选、租户隔离。
- `GET /api/outlets/{id}`：详情，跨租户/不存在返回 404。
- `POST /api/outlets`：创建（稳定编码唯一、租户默认唯一）。
- `PUT /api/outlets/{id}`：更新（编码唯一、默认档口必须启用）。
- `PATCH /api/outlets/{id}/status`：启用/禁用；禁用默认档口时清除默认标记。
- `GET /api/outlets/options`：仅返回调用者可用且启用的档口；`data:outlet:all` → 本租户全部；否则 `sys_user_outlet` 有效绑定；无绑定返回空（不回落全部）。
- VO 输出绑定用户数、订单引用数、草稿引用数（历史 NULL 不归入）；禁用不删除。

### BE-OUTLET-002 用户多档口绑定
- `UserCreateDTO/UserUpdateDTO` 增 `outletIds`、`defaultOutletId`；`UserVO` 增 `outletIds`、`defaultOutletId`、`outletScope`（ALL/ASSIGNED/NONE）、`peopleScope`（ALL_USERS/SELF）。
- 与角色同事务保存；校验同租户、档口启用、`defaultOutletId ∈ outletIds`、每用户单默认。
- 销售员（`role_code = ROLE_SALES`）无 `data:outlet:all` 时至少绑定一个档口；OWNER/ADMIN/FINANCE 可不绑定。
- 旧客户端：更新未携带 `outletIds`（null）不清空既有绑定；显式空集合按角色规则校验并清空。
- 用户停用不物理删除关联；删除用户时同步清理（与 `sys_user_role` 一致）。

### BE-OUTLET-003 权限迁移（V64）
- 新增权限码：`menu:outlet`(1)、`btn:outlet:create/edit/disable`(2)、`data:outlet:all`(2)、`data:order:peopleAll`(2)、`agent:outlets:read`(4)。
- 赋权：OWNER/ADMIN 全部（菜单+按钮+数据范围）；FINANCE 只读（菜单+数据范围，无管理按钮）；SALES 无 `data:outlet:all`。
- 兼容 `btn:order:viewAll`：已有该按钮的角色同步获得 `data:outlet:all + data:order:peopleAll`（INSERT IGNORE，不删除旧权限）。
- `agent:outlets:read` 仅建码，默认不给任何角色（Series E 消费）。
- 若仓库无某角色则 INSERT 不产生该角色行（不杜撰角色）。

---

## 2. API / 权限矩阵

| 端点 | 方法 | 权限 |
|---|---|---|
| /api/outlets | GET | `menu:outlet` |
| /api/outlets/options | GET | 登录即可（按 `data:outlet:all` 或绑定裁剪） |
| /api/outlets/{id} | GET | `menu:outlet` |
| /api/outlets | POST | `btn:outlet:create` |
| /api/outlets/{id} | PUT | `btn:outlet:edit` |
| /api/outlets/{id}/status | PATCH | `btn:outlet:disable` |

| 角色 | menu:outlet | btn:create/edit/disable | data:outlet:all | data:order:peopleAll |
|---|---|---|---|---|
| ROLE_OWNER | ✅ | ✅ | ✅ | ✅ |
| ROLE_ADMIN | ✅ | ✅ | ✅ | ✅ |
| ROLE_FINANCE | ✅ | ❌ | ✅ | ✅ |
| ROLE_SALES | ❌ | ❌ | ❌ | ❌ |

---

## 3. 测试命令与结果（实际执行）

```bash
cd blade-backend
mvn test -Dtest='OutletServiceImplTest,UserOutletBindingTest,OutletV64PermissionSchemaTest,OutletAccessControlSchemaTest,OutletEntityMappingTest,OutletAuditSqlReadOnlyTest,OutletFlywayMigrationTest'
# → 31/31（含空库 Flyway V1→V64，66 个迁移文件）

mvn test -Dtest='OutletPermissionMigrationIntegrationTest'
# → 5/5

mvn test
# → 全量后端 570/570，Failures 0, Errors 0, Skipped 0
```

新增后端测试 20 项（OutletServiceImplTest 6、UserOutletBindingTest 6、OutletV64PermissionSchemaTest 3、OutletPermissionMigrationIntegrationTest 5）。覆盖：默认唯一、禁用清除默认、options（ALL/单/多/NONE、无绑定不回落）、跨租户 404、销售员至少一档口、OWNER 可不绑定、default∈ids、旧客户端不清空、V64 权限码唯一与角色赋权。

---

## 4. 已知限制 / 未完成项

- 未做前端（BA-OUTLET-001/002 保持 TODO，Series B2）。
- 未接订单/草稿/统计/导出/文件/Agent 数据过滤（Series C-E）；未写 `source_outlet_id`。
- `peopleScope=ALL_USERS` 当前仅由 OWNER/ADMIN/FINANCE（持 `data:order:peopleAll`）推导；「档口负责人」角色不存在，未硬造语义。
- 档口引用计数为逐档口 `selectCount`（档口数量小可接受）。
- 未提供物理删除 API（设计即禁物理删除，用禁用代替）。

## 5. 建议 Codex 进入 Series B2/C 前重点审核

1. V64 赋权语义是否与「实际角色」（当前仅 OWNER/ADMIN/FINANCE/SALES/WAREHOUSE 五种 role_code）一致。
2. `GET /api/outlets/options` 无 `@PreAuthorize`、仅登录即可访问并按权限裁剪，是否符合预期（设计 6.1 未写权限码）。
3. 用户 `update` 中「未携带 `outletIds` 不修改」与「显式空数组清空」的语义。
4. 引用计数 `selectCount` 逐档口，量级增大后建议分组。
5. 销售员绑定校验仅按 role_code（`ROLE_SALES`）与 OWNER/ADMIN/FINANCE。

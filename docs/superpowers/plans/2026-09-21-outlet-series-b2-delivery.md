# Series B2 交付报告：档口管理与用户档口授权（前端）

> 分支：`feature/outlet-access-control`　基线：`ed18e39`
> 执行：DeepSeek（`[dsh]`）
> 范围：仅 BA-OUTLET-001/002 前端；不进入 Series C 订单/草稿数据过滤，不 push/deploy/NAS/生产。

---

## 1. 交付内容

### BA-OUTLET-001 档口管理页
- typed API：`src/api/outlet.ts`（`getOutletPage`/`getOutletOptions`/`getOutletById`/`createOutlet`/`updateOutlet`/`updateOutletStatus`）。
- `/outlets` 页面 + 侧栏入口，受 `menu:outlet` 控制；按钮按 `btn:outlet:create/edit/disable` 显隐。
- 紧凑列表：搜索、状态筛选、分页、编码、名称、类型、默认、状态、绑定用户数、正式订单数、草稿数、操作。
- 新建/编辑表单：编码创建时可填、编辑时只读并提示「档口编码创建后不可修改」；名称、类型、联系人、电话、地址、排序、默认、备注。
- 启停二次确认，显示绑定用户/订单/草稿引用数；禁用非删除；禁用默认提示默认将清除。
- 异步 loading、防重复提交、错误就近反馈；图标用 `material-symbols-outlined`，无 emoji；关键按钮 `min-height: 44px`；表单 label 与键盘可达。

### BA-OUTLET-002 用户管理档口授权
- 现有用户新建/编辑表单接入 `outletIds`/`defaultOutletId`。
- options 来自后端 `GET /api/outlets/options`，不自行构造全量。
- 「可访问档口」多选；「默认档口」只从已选档口选择；移除档口时自动清掉非法默认（watch）。
- 只读权限摘要来自后端 `UserVO.outletScope/peopleScope`（全部档口/指定档口/无档口；全部人员/仅本人），前端不按角色名推导。
- `ROLE_SALES`（真实 roleCode）无档口时前端阻止保存；后端仍是最终门禁；角色下拉展示 `roleName（roleCode）`。
- 仅状态启停的独立操作只提交 `{id, status}`，不提交 `outletIds/defaultOutletId`；完整编辑表单才明确提交档口。
- 兼容旧用户：字段缺失用空数组/null；切换角色不清空已选档口；不增加全局档口切换器。

---

## 2. 页面/接口清单

| 页面/文件 | 说明 |
|---|---|
| `src/views/outlets/index.vue` | 档口管理页 |
| `src/api/outlet.ts` | 档口 typed API |
| `src/views/system/index.vue` | 用户档口授权 + 状态切换 |
| `src/api/user.ts` | `UserVO`/DTO 增档口字段与 `RoleSimple` 导出 |
| `src/router/index.ts` | `/outlets` 路由 + 权限映射 |
| `src/views/layout/index.vue` | 侧栏「档口管理」+ 标题 |

| 前端调用 | 后端端点 | 权限 |
|---|---|---|
| 列表/详情 | GET `/api/outlets`、`/api/outlets/{id}` | `menu:outlet` |
| 选项 | GET `/api/outlets/options` | 登录 + 服务裁剪 |
| 新建/编辑/启停 | POST / PUT / PATCH `/api/outlets` | `btn:outlet:create/edit/disable` |

---

## 3. 测试与验收

```bash
cd blade-admin
npm run build     # vue-tsc 类型检查 + vite build 通过（产出 outlets-*.js）
npx playwright test e2e-outlet.spec.ts --reporter=line
# → 2 passed（BA-OUTLET-001 / BA-OUTLET-002）
```

浏览器验收（本地 dev：后端 8080 + 前端 5777，均连本地开发库，未连生产）：
- BA-OUTLET-001：菜单入口可见 → `/outlets` 列表 → 新建档口出现 → 编辑时编码只读+提示 → 禁用二次确认含「历史数据仍保留」引用提示 → 状态变禁用。
- BA-OUTLET-002：选 `ROLE_SALES` 不选档口 → 提示「销售员至少绑定一个档口」并阻止；选档口+默认后保存成功；状态切换发出的 `PUT /api/system/users` payload 断言 `status=0` 且**不含** `outletIds/defaultOutletId`。
- 截图证据：`blade-admin/test-screenshots/e2e-outlet-01..07-*.png`（test-screenshots 已在 .gitignore）。
- 测试数据用后可回收：E2E 档口 `E2E*`、用户 `e2eoutlet*`，验收后已从本地开发库清理（0/0）。

无可用前端单测框架（无 vitest/vue-test-utils），本轮以前端类型检查/构建 + Playwright 等价浏览器验收替代。

---

## 4. 安全与兼容说明

- 前端仅展示后端返回的 `outletScope/peopleScope` 与 `/api/outlets/options`，不自行假定全档口。
- 销售员无档口仅前端拦截，后端 `UserServiceImpl` 仍是最终门禁。
- 状态启停不提交档口字段，避免误重写 `sys_user_outlet`。
- 沿用现有 Blade 主题/Element Plus/Tailwind，未引入新字体或紫色模板；未进入 Series C 数据过滤。

## 5. 限制

- 未做前端单测（无框架）；Playwright 依赖本地后端+前端服务，未纳入 CI。
- 未提供档口物理删除（设计禁物理删除，前端以禁用代替）。
- 未接订单/草稿/统计/导出/文件/Agent 档口过滤（Series C-E）。
- Element Plus 未配置 zh-CN locale，`ElMessageBox` 确认按钮显示为英文「OK/Cancel」（既有全局行为，本轮未改）。

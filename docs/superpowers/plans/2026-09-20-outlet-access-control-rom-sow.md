# ROM/SOW：档口主数据、用户多档口与数据权限边界

> 状态：Series A–D 已完成；Series E1（统计/仪表盘/导出 + 统计筛选）与 E2（文件中心/图片档口闭环）完成（DeepSeek，2026-09-21）；E3、F、G 待开发
>
> 基线：`master` / `8918b7c` 及其后续修复提交
>
> 设计依据：[20-OUTLET_ACCESS_CONTROL_DESIGN.md](../../20-OUTLET_ACCESS_CONTROL_DESIGN.md)
>
> 本任务是跨模块 P0 权限改造，不能以“只加前端下拉框”方式交付。

---

## 1. 目标与交付边界

交付完整闭环：

- 档口 CRUD、启停、默认档口和稳定编码。
- 用户绑定一个或多个档口，并指定个人默认档口。
- 档口范围与人员范围二维权限。
- 快速录单、草稿和正式订单保存 `source_outlet_id`。
- 订单、草稿、统计、导出、文件和 Agent API 后端统一裁剪。
- 历史 `source_shop` 映射、异常报告、生产副本预演和可回滚发布。

不在本轮交付：仓库自动绑定、一个订单多档口、全局档口切换器、完整组织树。

---

## 2. 开发前只读基线审计

实现 Agent 开始写代码前必须提交基线审计，至少确认：

1. 当前最高 Flyway 版本和是否已有并行迁移。
2. `OrderAccessPolicy`、订单列表/详情/动作的全部调用点。
3. `OrderDraft` 查询、确认、图片绑定和删除路径。
4. `DashboardServiceImpl`、`AnalyticsServiceImpl`、订单导出和客户订单查询的 SQL 来源。
5. `FileService` 的订单/草稿预览权限映射。
6. 用户 CRUD 的 DTO、事务边界和角色保存逻辑。
7. Agent Key 签发、轮换、`/api/agent/capabilities` 与草稿写入路径。
8. 生产副本中 `source_shop` 值分布、疑似批次值、空值和订单/草稿数量。

只读审计不能修改生产数据。Flyway 版本必须在创建迁移前重新确认，禁止预先占用已被其他分支使用的版本号。

---

## 3. 任务拆分

### Series A：数据模型与兼容迁移

| 任务 ID | 任务 | 状态 | 交付物 |
|---|---|---|---|
| ARCH-OUTLET-001 | 档口和数据范围契约冻结 | ✅ 完成 | 设计文档、角色矩阵、字段和发布边界 |
| DB-OUTLET-001 | 档口与用户关联表 | ✅ 完成（DeepSeek，2026-09-20） | `sales_outlet`、`sys_user_outlet` 实体/Mapper/Flyway V63/租户与索引/契约测试 |
| DB-OUTLET-002 | 订单与草稿档口 ID及变更审计 | ✅ 完成（DeepSeek，2026-09-20） | `sale_order/order_draft.source_outlet_id` 可空加法迁移、`order_outlet_change_log`，保留 `source_shop` |
| DB-OUTLET-003 | Agent Key 档口关联 | ✅ 完成（DeepSeek，2026-09-20） | `agent_key_outlet`、`agent_key.outlet_scope_type`（ALL/ASSIGNED/NONE 默认 NONE）与 Key 轮换同事务复制规则 |
| DATA-OUTLET-001 | 历史档口审计工具 | ✅ 完成（DeepSeek，2026-09-20） | 只读分布报告 + 可编辑名称映射 CTE（可自动映射/未映射/疑似批次/空值/冲突），不直接改生产 |

验收门禁：迁移在空库和当前生产副本上可执行、可幂等验证；不得改变订单金额、状态、商品明细和文件绑定。

### Series B：档口主数据与用户授权

| 任务 ID | 任务 | 状态 | 交付物 |
|---|---|---|---|
| BE-OUTLET-001 | 档口主数据服务/API | ⏳ TODO | CRUD、启停、默认档口、引用保护、`/api/outlets/options` |
| BE-OUTLET-002 | 用户多档口绑定 | ⏳ TODO | 用户创建/更新/详情增加 `outletIds/defaultOutletId`，角色和档口同事务保存 |
| BE-OUTLET-003 | 权限编码与角色迁移 | ⏳ TODO | `data:outlet:all`、`data:order:peopleAll` 和档口管理权限；兼容 `btn:order:viewAll` |
| BA-OUTLET-001 | 档口管理页面 | ⏳ TODO | 列表、搜索、新建/编辑、启停、租户默认档口和引用提示 |
| BA-OUTLET-002 | 用户管理档口授权 | ⏳ TODO | 多选可访问档口、默认档口、权限摘要和销售员必选校验 |

验收门禁：单档口、多档口、全部档口、无档口和跨租户五种用户均有后端测试；前端只能展示后端返回的授权选项。

### Series C：统一数据访问策略

| 任务 ID | 任务 | 状态 | 交付物 |
|---|---|---|---|
| BE-OUTLET-004 | `OutletAccessPolicy` | ⏳ TODO | 用户/Agent 范围解析、默认档口、授权检查和统一选项服务 |
| BE-OUTLET-005 | `OrderAccessPolicy` 二维范围重构 | ⏳ TODO | 档口范围 × 人员范围；列表、详情、动作、`allowedActions` 一致 |
| BE-OUTLET-006 | 草稿权限接入 | ⏳ TODO | 草稿列表/详情/写入/删除/确认统一档口范围，确认时二次校验 |
| TEST-OUTLET-001 | 后端越权矩阵 | ⏳ TODO | URL 直连、请求伪造、跨租户、禁用档口、无绑定、同档口他人订单测试 |

验收门禁：禁止在 Controller 中以角色名散落判断；`btn:order:viewAll` 不得继续单独绕过新的档口范围。

### Series D：订单和草稿交互

| 任务 ID | 任务 | 状态 | 交付物 |
|---|---|---|---|
| BE-OUTLET-007 | 订单/草稿 DTO 与写入规则 | ✅ 完成（DeepSeek，2026-09-21） | 正式订单必须有具体档口；`sourceOutletId/code/name`；服务端主数据名称快照；改档口高权限+原因+审计（V66）；列表结构化筛选 |
| BA-OUTLET-003 | 订单和草稿档口选择器 | ✅ 完成（DeepSeek，2026-09-21） | 快速录单、新建订单、草稿详情、编辑弹窗；单档口锁定、多档口授权选择、待归档提示 |
| BA-OUTLET-004 | 列表筛选与历史待归档提示 | ✅ 完成（DeepSeek，2026-09-21） | 订单/草稿按档口筛选；授权者可查看“待归档档口”并显示标签 |
| TEST-OUTLET-002 | 录单与草稿 E2E | ✅ 完成（DeepSeek，2026-09-21） | 后端 17 例 + Playwright 5 例：手工草稿、Agent 草稿、草稿确认、正式订单、列表筛选和越权选择 |

验收门禁：每张正式订单必须有具体档口；`source_shop` 只作为服务端快照，不接受批次或客户端自由文本覆盖。

### Series E：统计、导出、文件与 Agent

| 任务 ID | 任务 | 状态 | 交付物 |
|---|---|---|---|
| BE-OUTLET-008 | 看板与分析范围接入 | ✅ 完成（DeepSeek，2026-09-21） | 汇总、趋势、排行、商品详情、待处理/周月同期统一档口 × 人员范围；未归档默认排除；无缓存并加范围指纹 |
| BE-OUTLET-009 | 导出与文件权限接入 | ✅ 完成（DeepSeek，2026-09-21） | 订单导出统一读范围；文件中心/订单/草稿图片全出口 `FileBusinessAccessPolicy`、PUBLIC/previewToken 业务校验、多绑定 ALL、SQL 预分页 |
| BE-OUTLET-010 | Agent Key 档口范围 | ⏳ TODO | Key 签发/轮换、capabilities、档口查询和草稿/订单接口校验（E3） |
| BA-OUTLET-005 | 统计档口筛选 | ✅ 完成（DeepSeek，2026-09-21） | 分析页/仪表盘档口多选与对比；选项仅授权集合；待归档独立开关 |
| BA-OUTLET-006 | Agent Key 档口配置 | ⏳ TODO | 全部/指定档口、默认档口和服务器能力同步展示（E3） |
| TEST-OUTLET-003 | 全出口防泄漏回归 | 🚧 文件出口完成（DeepSeek，2026-09-21） | 文件全出口 + previewToken 已覆盖；Agent/缓存跨角色回归留 E3 |

验收门禁：销售员不能通过统计总数、导出、图片地址或 Agent 接口推断其他档口数据。

### Series F：历史迁移与生产发布准备

| 任务 ID | 任务 | 状态 | 交付物 |
|---|---|---|---|
| DATA-OUTLET-002 | 生产副本映射预演 | ⏳ TODO | 自动映射、待人工、冲突和抽样报告；正式订单/草稿逐项对账 |
| DATA-OUTLET-003 | 用户档口初始授权清单 | ⏳ TODO | Owner/财务/负责人/销售员的初始范围，经用户确认后再迁移 |
| TEST-OUTLET-004 | 全量回归与性能 | ⏳ TODO | 后端全量、PC 构建、关键 E2E；大列表 `IN`/EXISTS 查询计划和缓存隔离 |
| DEPLOY-OUTLET-001 | NAS 灰度发布与回滚 | ⏳ TODO | 双份备份、迁移预演、先双读后切写、验收、观察和回滚证据 |

生产门禁：待人工档口数量、处理人和处理结果必须明确；任何历史订单丢失、金额变化、图片解绑或跨档口泄漏都阻断发布。

### Series G：收口与兼容字段评估

| 任务 ID | 任务 | 状态 | 交付物 |
|---|---|---|---|
| ARCH-OUTLET-002 | 兼容期复盘 | ⏳ TODO | 观察至少一个发布周期，评估 `btn:order:viewAll` 和空档口兼容路径 |
| DB-OUTLET-004 | 非空约束评估 | ⏳ TODO | 存量归档完成后评估正式订单 `source_outlet_id NOT NULL`，不是首发必做 |
| DOC-OUTLET-001 | 文档与 Agent 手册收口 | ⏳ TODO | PRD、数据库、权限、API、发布记录和 Agent 连接手册同步真实实现 |

---

## 4. 固定实施顺序

```text
Series A 数据模型
  → Series B 主数据与用户授权
  → Series C 后端统一策略
  → Series D 订单/草稿写入与页面
  → Series E 所有数据出口
  → Series F 生产副本迁移和发布
  → Series G 观察期收口
```

允许并行：

- A 完成后，`BA-OUTLET-001/002` 可以与 C 的后端策略并行。
- C 的接口契约冻结后，D 的前端和 E 的统计前端可以并行。

禁止并行：

- 未完成 C 时，不得只上线 D 的档口下拉框。
- 未完成 E 的全出口回归时，不得上线销售员档口隔离。
- 未完成生产副本预演时，不得在生产直接运行历史回填。

---

## 5. ROM 预估

| 系列 | 复杂度 | 预估 | 主要风险 |
|---|---|---|---|
| A 数据模型与迁移工具 | L | 2～4 天 | 历史自由文本、Flyway 并行版本、错误批次值 |
| B 主数据与用户授权 | L | 3～5 天 | 用户角色与档口事务一致性、默认档口唯一性 |
| C 统一访问策略 | XL | 4～7 天 | 当前 `viewAll` 散布、详情与列表范围不一致 |
| D 订单/草稿页面 | L | 3～5 天 | 快速录单、手工草稿、Agent 草稿三条链路 |
| E 统计/导出/文件/Agent | XL | 4～7 天 | 聚合泄漏、缓存串权、图片直链权限 |
| F 迁移、回归与发布 | L/XL | 3～6 天 | 生产映射确认和回滚窗口 |
| G 观察期收口 | M | 1～2 天 + 观察期 | 旧权限和空档口兼容路径仍被使用 |

整体 ROM：约 20～36 个有效开发日。可以由多个 Agent 分系列实现，但 Series C 的访问策略和 Series F 的生产迁移必须由同一审核人统一把关。

---

## 6. 提交与审核要求

- 新功能从最新 `master` 创建独立 `feature/outlet-access-control` worktree。
- 每个 Series 至少一个独立提交，提交信息带任务 ID。
- 不修改或删除已有生产迁移；只追加新的 Flyway 文件。
- 不直接连接生产写数据；生产迁移由发布流程执行。
- 每个 Series 完成后更新 `docs/03-TASKS.md`，全部完成后提交最终交付报告。
- 最终报告必须列出：提交范围、迁移版本、测试命令、测试数量、生产副本数据对账、已知限制和回滚点。

---

## 7. 最小测试矩阵

| 主体 | 档口配置 | 人员范围 | 预期 |
|---|---|---|---|
| Owner | ALL | ALL_USERS | 可查看和选择全部档口 |
| 财务 | ALL | ALL_USERS | 可看全部但只执行财务授权动作 |
| 档口负责人 | A、B | ALL_USERS | 可看 A/B 全部人员，不能看 C |
| 销售甲 | A | SELF | A 自动锁定，只看本人 |
| 销售乙 | A、B | SELF | 可选 A/B，只看本人 |
| 普通用户 | 无 | SELF | 订单列表为空，创建返回明确错误 |
| Agent Key | A | 不适用 | 可在 A 建草稿，传 B 返回 403 |
| 跨租户用户/Key | 伪造其他租户 ID | 任意 | 始终 403/404，不泄露对象存在性 |

每种主体至少覆盖订单列表、详情、草稿、统计、导出、文件预览和直接 API 伪造。

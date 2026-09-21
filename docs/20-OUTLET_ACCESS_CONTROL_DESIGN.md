# 档口主数据与跨档口数据权限设计

> 状态：方案已确认，待开发
>
> 更新日期：2026-09-20
>
> 适用范围：PC 管理端、后端、订单草稿、正式订单、统计、导出、文件中心和 Agent Gateway

---

## 一、背景与目标

当前订单仅保存自由文本 `source_shop`。自由文本既无法保证“42”这类单据批次不会误写成档口，也无法支撑用户与档口绑定、按档口筛选统计和后端数据隔离。

本次改造把“来源档口/店铺”升级为正式主数据和数据权限边界，达到以下目标：

1. 档口像商品、客户一样具备独立主数据管理。
2. 一个用户可以绑定一个或多个档口，并指定个人默认档口。
3. 老板可使用全部档口、切换档口并查看跨档口汇总。
4. 销售员只能使用绑定档口，不能查看、录入或统计其他档口数据。
5. 档口范围和人员范围彼此独立，支持“档口负责人看本档口全部人员”和“销售员只看本人”。
6. 正式订单保存稳定的档口 ID，同时保留名称快照，档口改名不影响历史订单表达。
7. 前端隐藏不是安全边界；列表、详情、导出、统计、文件、Agent API 均由后端统一裁剪。

本模块中的“档口”是订单归属和经营分析维度，不等同于库存仓库。选择档口不得自动选择仓库，也不得直接改变库存。

---

## 二、核心业务规则

### 2.1 用户与档口

- 用户与档口是多对多关系。
- 普通销售员至少绑定一个启用档口。
- 只绑定一个档口时，录单自动带出且控件锁定。
- 绑定多个档口时，只能在授权集合内切换；个人默认档口优先自动带出。
- 拥有“全部档口数据”权限的老板、管理员可以访问本租户全部启用档口，不需要逐条建立用户关联。
- “全部档口”只能作为查询和统计条件；每一张正式订单必须归属于一个具体档口。草稿允许在历史迁移或 Owner 待补资料场景暂时处于“待归档档口”，但确认正式订单前必须补齐。
- 没有全部档口权限且没有任何有效档口绑定的用户，不得创建订单，也不得静默退化为全租户或仅本人数据。

### 2.2 档口范围与人员范围

订单访问由三个维度共同决定：

```text
可访问订单 = 当前租户
           ∩ 当前用户可访问档口
           ∩ 当前用户可访问人员范围
           ∩ 业务动作权限
```

档口范围：

- `ALL`：当前租户全部有效档口。
- `ASSIGNED`：`sys_user_outlet` 中绑定的有效档口。
- `NONE`：没有可访问档口，拒绝订单与统计数据。

人员范围：

- `ALL_USERS`：在可访问档口内查看全部人员订单。
- `SELF`：在可访问档口内只查看 `salesman_id = 当前用户` 的订单。

业务动作权限继续由现有 `btn:*`、`field:*`、API 权限控制。拥有档口数据不代表可以收款、退款、回退或修改订单。

### 2.3 默认角色矩阵

| 角色 | 档口范围 | 人员范围 | 录单选择 | 默认能力 |
|---|---|---|---|---|
| 老板 `ROLE_OWNER` | 全部档口 | 全部人员 | 可选择任意启用档口 | 管理档口、跨档口查询、统计和导出 |
| 系统管理员 `ROLE_ADMIN` | 全部档口 | 全部人员 | 可选择任意启用档口 | 系统维护；是否查看金额仍受字段权限控制 |
| 财务 `ROLE_FINANCE` | 全部档口 | 全部人员 | 默认只读，不自动获得录单能力 | 跨档口订单和财务数据，只执行已授权动作 |
| 档口负责人 | 已绑定档口 | 档口内全部人员 | 只能选择已绑定档口 | 管理和分析负责档口 |
| 销售员 `ROLE_SALES` | 已绑定档口 | 仅本人 | 单档口锁定；多档口可在授权集合内切换 | 创建和查看本人订单 |
| 仓库/采购 | 按实际授权 | 默认仅业务必要范围 | 默认不录单 | 档口权限不替代仓库或采购权限 |

第一期不在 `sys_user_outlet` 中重复保存“档口内角色”。档口负责人通过角色或权限组合表达，避免同时维护两套角色真相。

### 2.4 默认档口优先级

创建草稿或订单时按以下顺序确定默认值：

1. 用户个人默认档口；
2. 租户默认档口；
3. 当前用户唯一可访问档口；
4. 无法确定时要求人工选择，不能使用单据批次、仓库或历史文本兜底。

任何兜底结果都必须再次通过后端 `canUseOutlet` 校验。

---

## 三、数据结构

### 3.1 `sales_outlet` 档口主表

```sql
CREATE TABLE sales_outlet (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  outlet_code VARCHAR(30) NOT NULL,
  outlet_name VARCHAR(100) NOT NULL,
  outlet_type VARCHAR(20) NOT NULL DEFAULT 'STORE',
  contact_name VARCHAR(50) DEFAULT NULL,
  phone VARCHAR(30) DEFAULT NULL,
  address VARCHAR(255) DEFAULT NULL,
  sort INT NOT NULL DEFAULT 0,
  is_tenant_default TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(500) DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  create_by BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_outlet_code_tenant (tenant_id, outlet_code),
  KEY idx_outlet_tenant_status (tenant_id, status, deleted)
);
```

V67 追加的数据库不变量（第二批B）：

```sql
-- 生成的守卫列：未删除且是默认时为 1，否则 NULL；MySQL 唯一索引忽略 NULL
ALTER TABLE sales_outlet
  ADD COLUMN tenant_default_guard TINYINT GENERATED ALWAYS AS (
    CASE WHEN deleted = 0 AND is_tenant_default = 1 THEN 1 ELSE NULL END
  ) STORED,
  ADD UNIQUE KEY uk_outlet_tenant_default (tenant_id, tenant_default_guard);
```

规则：

- `outlet_code` 是 API、导入和 Agent 使用的稳定标识，档口改名不改编码。
- 档口发生历史引用后不能物理删除，只允许禁用。
- **一个租户最多一个有效租户默认档口，由数据库唯一索引 `uk_outlet_tenant_default` 兜底**；服务层在设置默认时先取租户级串行锁（`SELECT id FROM sys_tenant WHERE id=? FOR UPDATE`），再清同租户其它默认、最后标记目标，所有默认相关 SQL 显式带 `tenant_id` + `deleted`。
- **V67 是 fail-closed 迁移**：若历史已存在同租户多条未删除默认档口，单一 `ALTER TABLE` 会因唯一键冲突失败，不会自动保留或清理任何一条。部署前必须执行下方重复检查；发现重复必须由人工确认保留哪一条、将其余显式置 0 后重跑迁移。
- 禁用档口不再出现在新建订单选项中，但历史订单仍可显示其名称快照；禁用租户默认档口会同步清除其默认标记。

### 3.2 `sys_user_outlet` 用户档口关联

```sql
CREATE TABLE sys_user_outlet (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  outlet_id BIGINT NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  create_by BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_outlet_tenant (tenant_id, user_id, outlet_id),
  KEY idx_user_outlet_user (tenant_id, user_id, status, deleted),
  KEY idx_user_outlet_outlet (tenant_id, outlet_id, status, deleted)
);
```

规则：

- 用户默认档口必须属于该用户的有效绑定集合。
- 每个用户最多一个有效默认档口。
- 用户、档口和关联记录必须属于同一租户。
- 用户停用不删除历史关联；重新启用后按现行授权恢复或重新配置。

### 3.3 订单与草稿字段

`sale_order` 与 `order_draft` 增加：

| 字段 | 类型 | 说明 |
|---|---|---|
| `source_outlet_id` | bigint | 档口主数据 ID，作为权限和统计依据 |
| `source_shop` | varchar(100) | 保留为档口名称快照，不再作为权限依据 |

正式订单确认时：

- 必须有有效 `source_outlet_id`。
- `source_shop` 从档口主数据复制当时名称，不接受客户端任意写入覆盖。
- 草稿转正式订单沿用草稿的 `source_outlet_id` 并再次校验当前操作者权限。
- 已完成订单修改档口会影响统计，只允许老板/管理员执行，必须填写原因并写审计日志。

### 3.4 `agent_key_outlet` Agent Key 档口关联

```sql
CREATE TABLE agent_key_outlet (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  agent_key_id BIGINT NOT NULL,
  outlet_id BIGINT NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_key_outlet (tenant_id, agent_key_id, outlet_id),
  KEY idx_agent_key_outlet_key (tenant_id, agent_key_id, status)
);
```

Agent Key 的业务 scope 决定“能做什么”，档口关联决定“可以在哪些档口做”。两者缺一不可。

`agent_key` 增加 `outlet_scope_type varchar(20) NOT NULL DEFAULT 'NONE'`，合法取值与语义：

| 取值 | 语义 | 依赖关联行 |
|---|---|---|
| `ALL` | 可访问当前租户全部启用档口 | 否；不依赖 `agent_key_outlet` 行，新档口自动可见 |
| `ASSIGNED` | 只可访问 `agent_key_outlet` 中绑定的启用档口 | 是；必须至少一条有效关联，新档口不自动扩权 |
| `NONE` | 拒绝档口业务 | 否；默认值，历史 Key 迁移后不得静默获得全档口权限 |

使用 `outlet_scope_type` 显式表达范围，禁止用 `outlet_id=0` 等哨兵值。Key 轮换时在同一事务内复制旧 Key 的 `outlet_scope_type` 与当前有效 `agent_key_outlet` 绑定（保留默认档口标记，不跨租户）；新建 Key 在管理 UI 接入前保持 `NONE`。

### 3.5 `order_outlet_change_log` 订单档口变更审计

```sql
CREATE TABLE order_outlet_change_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  old_outlet_id BIGINT DEFAULT NULL,
  old_outlet_name VARCHAR(100) DEFAULT NULL,
  new_outlet_id BIGINT NOT NULL,
  new_outlet_name VARCHAR(100) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_outlet_change_order (tenant_id, order_id, create_time)
);
```

该表只追加不更新。历史归档和已确认/已完成订单改档口必须写入；草稿在确认前普通编辑不要求逐次写本表，但仍保留自身更新时间和操作来源。

---

## 四、权限编码与兼容策略

第一期继续复用当前四类权限模型，不新增权限类型。数据范围权限作为 `type=2` 的授权能力保存，但编码统一使用 `data:*`，避免和页面按钮语义混淆。

| 权限码 | 作用 |
|---|---|
| `menu:outlet` | 显示档口管理菜单 |
| `btn:outlet:create` | 新建档口 |
| `btn:outlet:edit` | 编辑档口 |
| `btn:outlet:disable` | 启用/禁用档口 |
| `data:outlet:all` | 可访问当前租户全部档口 |
| `data:order:peopleAll` | 在允许档口内查看全部人员订单 |
| `agent:outlets:read` | Agent 查询已授权档口选项 |

现有 `btn:order:viewAll` 进入兼容期：

1. 新迁移把现有拥有者映射为 `data:outlet:all + data:order:peopleAll`。
2. 后端切换到二维范围后，`btn:order:viewAll` 不再单独代表绕过全部数据范围。
3. 保留一个发布周期供旧客户端和旧角色配置兼容。
4. 稳定后再评估下线，不在首轮迁移中删除。

---

## 五、后端统一访问策略

### 5.1 `OutletAccessPolicy`

建立唯一档口范围服务，至少提供：

```text
resolveCurrentScope()            解析当前用户或 Agent 的档口范围
allowedOutletIds()              返回可访问档口 ID 集合
canUseOutlet(outletId)          判断是否可使用指定档口
requireUseOutlet(outletId)      越权时返回 403
resolveDefaultOutletId()        解析个人/租户默认档口
listAvailableOptions()          返回经过权限裁剪的选项
```

禁止各 Controller 自行拼装角色名判断。Owner、财务、销售等差异由权限与关联数据共同计算。

### 5.2 `OrderAccessPolicy` 重构

现有策略只支持 `btn:order:viewAll` 或 `salesman_id = 当前用户`。目标策略改为：

```sql
WHERE tenant_id = :tenantId
  AND source_outlet_id IN (:allowedOutletIds)
  AND (
    :peopleScope = 'ALL_USERS'
    OR salesman_id = :currentUserId
  )
```

Owner 的 `ALL` 范围可在 SQL 中省略 `IN`，但不能省略租户条件。`NONE` 范围必须返回空列表或 403，禁止回落到全部数据。

列表、详情、动作和 `allowedActions` 必须共用同一策略；不能出现“列表看不到但直连详情能打开”或相反情况。

### 5.3 必须接入的出口

- 正式订单：列表、详情、新建、编辑、取消、收款、退款、回退、配货、发货、导出。
- 订单草稿：列表、详情、新建、编辑、删除、确认转订单、图片预览。
- 看板与数据分析：汇总、趋势、排行、客户贡献、导出。
- 文件中心：订单/草稿图片绑定和预览沿用对应业务对象的档口范围。
- Agent Gateway：订单、草稿、分析、档口选项接口。
- 移动端和后续打印/分享：使用相同后端策略，不能各自维护第二套过滤规则。

### 5.4 缓存与防推断

- 统计缓存键必须包含 `tenantId + outletScope + peopleScope + allowedOutletIds` 的稳定摘要。
- 无权用户不仅不能看到明细，也不能通过总数、金额、排行、导出任务状态推断其他档口数据。
- 权限或用户档口绑定变化后，必须失效相关用户的统计和选项缓存。

---

## 六、API 规划

### 6.1 档口管理

```text
GET    /api/outlets
GET    /api/outlets/{id}
POST   /api/outlets
PUT    /api/outlets/{id}
PATCH  /api/outlets/{id}/status
GET    /api/outlets/options
```

`GET /api/outlets/options` 只返回当前调用主体真正可用的选项：

```json
{
  "scopeType": "ASSIGNED",
  "peopleScope": "SELF",
  "locked": true,
  "defaultOutletId": 12,
  "items": [
    { "id": 12, "code": "YL", "name": "御龙", "status": 1 }
  ]
}
```

### 6.2 用户档口授权

用户创建、更新和详情 DTO 增加：

```json
{
  "outletIds": [12, 15],
  "defaultOutletId": 12
}
```

后端在同一事务中保存角色和档口关系。角色/权限要求绑定档口而请求为空时，返回明确校验错误，不允许先生成一个无范围销售员。

### 6.3 订单和草稿

创建、更新、查询参数新增稳定字段：

```json
{
  "sourceOutletId": 12,
  "sourceOutletCode": "YL",
  "sourceShop": "御龙"
}
```

- PC/JWT 接口写入时使用 `sourceOutletId`。
- Agent 接口优先使用稳定的 `sourceOutletCode`，服务端解析为 ID。
- 返回值同时提供 ID、编码和名称快照。
- 服务端忽略客户端伪造的 `sourceShop`，以主数据名称生成快照。

---

## 七、前端交互

### 7.1 档口管理页

建议放在“系统管理”或独立“基础资料”下，提供：

- 档口列表、搜索、排序和状态筛选。
- 新建/编辑档口。
- 设置租户默认档口。
- 启用/禁用；有历史引用时禁止删除。
- 查看绑定用户数和历史订单数，作为停用前提示。

### 7.2 用户管理

用户新建/编辑页增加：

- “可访问档口”多选。
- “默认档口”单选，候选来自已选档口。
- 权限摘要：全部档口/指定档口、档口内全部人员/仅本人。
- 销售员无档口时阻止保存。

### 7.3 订单与草稿

- 单档口销售员：展示只读档口名称，不显示可切换下拉框。
- 多档口用户：只显示授权档口，默认选中个人默认档口。
- Owner/Admin：显示全部启用档口，但每张订单仍只选一个。
- 列表和统计筛选中的“全部档口”表示“当前用户有权访问的全部档口”。
- 页面可以隐藏越权选项，但后端仍必须校验请求参数。

第一期不建立全局档口切换器，避免用户在一个页面切换后无意影响其他页面。订单、草稿和统计各自保存明确筛选条件；后续确有高频跨页面切换需求再增加全局上下文。

---

## 八、Agent Key 档口权限

- Key Manager 签发/轮换界面增加“全部档口”或“指定档口”配置。
- `GET /api/agent/capabilities` 返回档口范围摘要和允许的档口编码，不返回数据库内部无关信息。
- `GET /api/agent/outlets` 需要 `agent:outlets:read`，只返回当前 Key 可用档口。
- Agent 创建商品不需要档口；创建客户默认不需要档口；创建草稿和查询订单必须应用 Key 的档口范围。
- 只有一个授权档口的 Key 可省略 `sourceOutletCode`，服务器使用 Key 默认档口；多个档口时必须显式传入。
- Key 传入未授权档口时返回 403，并区分“缺少业务 scope”和“档口范围拒绝”。

---

## 九、历史数据迁移

### 9.1 迁移原则

- 先建立主数据和映射报告，再回填订单；不得根据任意文本自动创建大量档口。
- `source_shop='御龙'` 这类已确认名称可映射到指定档口。
- `source_shop='42'`、空字符串、与 `source_batch_no` 相同等疑似错误值进入异常清单，不自动当作档口。
- 迁移必须同时覆盖 `sale_order` 和仍可编辑的 `order_draft`。
- 回填后保留原 `source_shop` 审计快照；人工修复时记录旧值、新档口、原因、操作人和时间。

### 9.2 遗留空档口数据

- 正式订单 `source_outlet_id IS NULL` 的历史记录只允许 Owner/Admin 或显式迁移权限查看和归档。
- 草稿 `source_outlet_id IS NULL` 时标记为“待归档档口”；默认只允许创建者、Owner/Admin 查看，不能确认成正式订单。
- 普通销售员不能通过历史空值绕过档口范围。
- 空档口历史订单不计入单档口排名；可以单列“待归档”供 Owner 修复。
- 正式切换前必须生成：总数、已自动映射数、待人工数、冲突数和抽样明细。

### 9.3 发布顺序

1. 加法迁移：建档口表、关联表和可空 `source_outlet_id`。
2. 部署双读能力：优先 ID，历史记录兼容名称快照。
3. 创建生产档口主数据和用户绑定。
4. 生产副本预演回填并人工确认异常。
5. 发布写入切换：新草稿和订单必须写 ID。
6. 回填存量并完成对账。
7. 观察一个发布周期后，再评估正式订单字段非空约束和旧权限下线。

---

## 十、验收标准

### 10.1 权限与越权

- Owner 能查看全部档口并按一个/多个档口统计。
- 单档口销售员录单时档口锁定，API 伪造其他档口返回 403。
- 多档口用户只能在绑定集合内切换。
- 档口负责人能看绑定档口内全部人员，但看不到其他档口。
- 销售员默认只能看本人订单；同档口同事订单不能通过详情 URL、导出或统计泄露。
- 无档口绑定用户不能创建或读取订单数据。
- 跨租户档口 ID、用户绑定和 Agent Key 一律拒绝。

### 10.2 功能一致性

- 快速录单、草稿、草稿转订单和正式订单编辑使用同一档口选项规则。
- 订单列表、详情、统计、导出和文件图片访问范围一致。
- 档口改名后历史订单继续显示保存时名称，新订单使用新名称。
- 禁用档口不影响历史展示，但不能新建订单。
- 修改已完成订单档口需要高权限、原因和审计记录。

### 10.3 数据与发布

- 生产副本迁移可重复执行且不新增重复档口/关联。
- 历史订单总数、金额、收款、状态、明细、图片绑定不变。
- 所有疑似批次误写档口的数据都有报告，不静默修正。
- 新旧字段双读期间，统计总额与改造前基线对账一致。
- 发布具备数据库、uploads、镜像和配置备份及可验证回滚路径。

---

## 十一、明确不在第一期实施

- 不把档口等同于仓库，也不自动绑定发货仓。
- 不在关联表中建立第二套“档口内角色”。
- 不允许一个订单同时归属多个档口；跨档口业务应拆成多单。
- 不删除 `source_shop` 历史快照字段。
- 不在第一期建设全局档口切换器和复杂组织树。
- 不因增加档口权限而自动扩大 Agent Key、财务、成本或毛利权限。

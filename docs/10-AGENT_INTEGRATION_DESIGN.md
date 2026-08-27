# Agent 对接设计

> 本文档定义 BladeProject 对接外部 AI Agent 的需求边界、接口分层、安全约束和实施顺序。
> 当前决策：Agent Gateway 默认只读；纸单识别流程已批准一个受 scope 约束的窄范围写入，只允许创建可人工复核的订单草稿，不允许 Agent 直接确认正式订单、调整库存或确认收款。

---

## 一、背景与定位

BladeProject 已具备订单、商品、SKU 库存、客户、看板和数据分析能力。外部 Agent 接入的目标不是替代 PC 管理端和移动端，而是在现有业务数据之上提供运营辅助能力：

| 场景 | 示例 |
|------|------|
| 款式趋势判断 | 哪些款持续向好、哪些款开始走弱、哪些款不建议继续做 |
| 客户跟进提醒 | 哪些客户距离上次拿货已超过预期，应主动联系 |
| 周期经营复盘 | 月度、季度、年度销售结构、商品表现、客户贡献和建议 |
| 经营巡检 | 低库存、库存积压、待处理订单和异常波动 |
| 统一搜索 | 按客户名、电话、订单号、商品编码或 SKU 查业务对象 |
| 后续沟通上下文 | 接入 WhatsApp 信息后，结合聊天、订单和客户历史做跟进分析 |
| 后续扩展 | 经过授权后追加窄范围动作，如记录跟进结果或订单备注 |

### 1.1 Agent 的系统边界

Agent 是 BladeProject 的外部消费者，通过 API 获取业务答案，不直接连接 MySQL、Redis 或文件存储。

```text
Hermes / 其他 Agent / 定时任务
          |
          | Agent API
          v
Agent Gateway
          |
          | 复用领域服务和聚合服务
          v
Dashboard / Analytics / Order / Customer / Product / Inventory
          |
          v
MySQL + Redis + File Storage
```

### 1.2 第一期开与不开

| 范围 | 决策 |
|------|------|
| 款式趋势、客户跟进、周期分析、搜索 | 第一期开 |
| 复用现有看板和数据分析能力 | 第一期开 |
| Agent 独立鉴权、租户绑定、权限范围 | 第一期开 |
| Agent API 调用审计与限流基础 | 第一期设计并落地最小版本 |
| 定时提醒结果输出 | 第一期开，先输出待跟进列表和报告数据，提醒渠道后续选型 |
| WhatsApp 数据接入 | 本地归档、客户绑定、完整性诊断和混合 Agent 人工跟进链路已完成；自动发送继续禁止 |
| `/agent/query` 后端自然语言问答 | 暂缓，先由外部 Agent 选择结构化工具 |
| `/agent/action` 泛化写操作 | 暂缓，避免 Agent 直接触发高风险业务写入 |
| 创建订单草稿 | 已开放窄范围能力；`agent:orders:write`，只写草稿，不产生库存/财务影响 |
| 确认正式订单、库存调整、收款确认 | Agent 阶段禁止，必须由 JWT 登录用户人工执行 |
| 增量变更订阅 `/agent/changes` | 待统一业务事件日志后再做 |

### 1.3 能力地图

Agent 能力不局限于当前已提出的款式趋势、客户跟进和周期报告。完整规划按经营问题拆成以下方向：

| 方向 | 能力示例 | 价值 |
|------|----------|------|
| 商品经营 | 新品观察、爆款风险、滞销原因、补货/减做/停做建议 | 帮助决定做什么款、做多少 |
| 颜色尺码结构 | 识别同款不同颜色/尺码的热卖和积压差异 | 避免只看商品总量而忽略 SKU 结构 |
| 客户经营 | 客户分层、偏好变化、流失风险、跟进内容建议 | 提升复购和重点客户维护效率 |
| 库存与供应 | 低库存优先级、库存积压、库存错配、跨仓调拨事实 | 区分该补的货和不该补的货 |
| 订单运营 | 待处理过久、付款后未配货、尾款拖延、退款异常 | 发现流程和履约风险 |
| 利润分析 | 高销售低毛利、客户利润贡献、运费侵蚀利润 | 避免只看销售额做决策 |
| 沟通反馈 | WhatsApp 问价未成交、客户反馈主题、聊天热度与下单差异 | 把沟通信号纳入经营判断 |
| 异常检测 | 款式、客户、退款、价格、库存的异常波动 | 让系统主动提示不寻常变化 |
| 经营记忆 | 记录停做原因、客户特殊偏好、决策结果回看 | 让后续分析能参考历史判断 |

### 1.4 分层路线

| 层级 | 重点 | 说明 |
|------|------|------|
| L1 | 事实数据包 | 先把统计口径、权限、多租户和稳定接口做对 |
| L2 | 趋势、预警、建议依据 | 第一阶段核心，覆盖款式、客户、库存结构和周期复盘 |
| L3 | 外部沟通上下文 | WhatsApp 等外部数据进入客户与商品反馈分析 |
| L4 | 授权动作与经营记忆 | 仅在审计、权限和人机边界清楚后开放 |

---

## 二、现有系统基础

### 2.1 可直接复用的能力

| 现有能力 | 当前接口/服务 | Agent 用途 |
|----------|---------------|------------|
| 看板统计 | `/api/dashboard/stats` | 今日/周期经营概览 |
| 订单趋势 | `/api/dashboard/trend` | 摘要和趋势回答 |
| 热销商品 | `/api/dashboard/top-products` | 热销巡检 |
| 库存预警 | `/api/dashboard/inventory-alerts` | 低库存任务 |
| 沉默客户 | `/api/dashboard/silent-customers` | 回访建议 |
| 库存周转统计 | `/api/dashboard/inventory-stats` | 库存概览 |
| 经营分析 | `/api/analytics/*` | 汇总、趋势、排行、商品拆解 |
| 客户统计与偏好 | `/api/customers/{id}/stats`、`/orders`、`/preference` | 客户画像分析 |

### 2.2 需要补齐的业务数据

| 目标 | 当前基础 | 需要补齐 |
|------|----------|----------|
| 判断款式持续向好/走弱 | 商品排行、趋势、订单明细、库存 | 按商品或 SKU 的多周期对比、连续趋势标签、补货/停做建议依据 |
| 判断客户该不该跟进 | 客户订单日期、消费统计、沉默客户 | 跟进规则、跟进周期、上次提醒/跟进记录，后续叠加沟通信息 |
| 输出周期经营建议 | 数据分析汇总和趋势 | 月报/季报/年报数据包、同比/环比与建议输入 |
| 结合 WhatsApp 理解客户状态 | 当前无聊天数据 | 合规接入、客户身份映射、消息摘要/标签、授权与脱敏策略 |
| 分析库存取舍 | 库存预警、库存周转、订单销售 | 积压、缺货影响、补货优先级和跨仓事实 |
| 识别颜色尺码结构 | SKU、订单明细、库存 | 同款颜色/尺码热卖、缺货和积压对比 |
| 识别客户流失风险 | 客户订单历史、客户偏好 | 客户分层、历史拿货节奏变化和跟进优先级 |

### 2.3 BladeProject 特有约束

外部参考文档中的通用订单系统模型不能直接照搬到本项目，Agent 设计必须遵守当前系统事实：

1. 数据库已锁定为 MySQL 8 + Redis 7，不按 SQLite 或 PostgreSQL 路线设计。
2. 库存不是单商品库存，而是 `SKU + 仓库` 库存，并存在 `reserved_qty` 与 `global_reserved_qty`。
3. 订单存在配货计划、跨仓总量预留、调整状态、支付状态和配送流程。
4. 所有业务访问都要进入多租户边界，禁止 Agent 绕开 `tenant_id` 隔离。
5. 毛利、成本、毛利率属于受控数据，Agent 不得因为“分析”场景默认读取。

---

## 三、接口分层

### 3.1 已有 CRUD API

PC 管理端和移动端继续使用现有 `/api/orders`、`/api/products`、`/api/customers`、`/api/inventory` 等接口。Agent 不以 CRUD 列表分页作为主要入口，避免多次抓取原始记录后自行拼业务规则。

### 3.2 已有业务聚合 API

`/api/dashboard/*` 和 `/api/analytics/*` 是当前的“答案型”接口。Agent Gateway 优先复用这些聚合口径，缺能力时在对应领域补充业务 API，不复制一套相互漂移的统计逻辑。

### 3.3 Agent Gateway API

Agent API 面向外部 Agent 的工作流，第一期路径统一放在 `/api/agent` 下：

| 接口 | 方法 | 第一版职责 |
|------|------|------------|
| `/api/agent/tasks/follow-up` | GET | 返回需跟进客户和依据 |
| `/api/agent/search` | GET | 跨客户、订单、商品、SKU 搜索 |
| `/api/agent/analytics/style-trends` | GET | 多周期款式趋势和建议依据 |
| `/api/agent/analytics/sku-mix` | GET | 款式颜色/尺码结构事实 |
| `/api/agent/inventory/recommendations` | GET | 库存积压、缺货和补货优先级事实 |
| `/api/agent/customers/risk` | GET | 客户流失风险与分层事实 |
| `/api/agent/reports/periodic` | GET | 月度、季度、年度经营分析数据包 |

Agent Gateway 的返回必须结构稳定、字段少而明确，不向外部暴露实体内部字段和数据库实现细节。

### 3.4 纸单订单草稿 API

本机识别 Agent 使用绑定租户的 Agent Key 调用 NAS 生产环境：

| 接口 | scope | 用途 |
|------|-------|------|
| `GET /api/agent/catalog/skus` | `agent:catalog:read` | 按款号、SKU、名称、颜色查询候选；系统售价仅作参考 |
| `POST /api/agent/order-drafts/source-files` | `agent:orders:write` | 上传纸单原图并返回文件 ID |
| `POST /api/agent/order-drafts/batch` | `agent:orders:write` | 批量创建草稿；每单隔离结果，按 externalRefNo 幂等 |

管理端使用 JWT 调用 `/api/order-drafts` 读取、编辑和确认。确认动作不属于 Agent API；只有用户确认后才调用既有订单领域服务创建正式订单。

数据优先级固定为：纸单数量、纸单销售价、纸单金额和总额优先；商品主档只负责识别 SKU 与提供参考价。未匹配 SKU、金额不一致和字段歧义以警告形式保留，不阻止草稿落库。

---

## 四、第一期接口草案

### 4.1 款式趋势

```http
GET /api/agent/analytics/style-trends?period=MONTH&comparePeriods=3
X-Agent-Key: {agent_key}
```

第一版趋势判断应返回“事实 + 标签 + 解释依据”，不直接只给一句结论：

| 字段 | 说明 |
|------|------|
| `trend` | `GROWING` / `STABLE` / `DECLINING` / `INSUFFICIENT_DATA` |
| `recommendation` | `KEEP` / `WATCH` / `REDUCE`，表达继续做、观察或减少投入 |
| `periodSeries` | 多周期销量、销售额、订单数、客户数 |
| `inventoryFacts` | 当前库存、可用库存、积压风险 |
| `reasons` | 支撑趋势和建议的事实列表 |

第一版不能仅凭短期销量下结论，至少要结合多周期变化、客户覆盖面和库存状态。

### 4.2 客户跟进任务

```http
GET /api/agent/tasks/follow-up?days=30
X-Agent-Key: {agent_key}
```

返回建议：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "customers": [
      {
        "type": "FOLLOW_UP_DUE",
        "title": "客户达到跟进周期",
        "entityId": "42",
        "facts": {
          "customerName": "张老板",
          "lastOrderDate": "2026-04-10",
          "daysSinceLastOrder": 42,
          "recentOrderCount": 5,
          "recentAmount": 38600.00
        },
        "reasons": [
          "距离上次订单已超过 30 天",
          "该客户近 12 个月有重复拿货记录"
        ]
      }
    ]
  }
}
```

定时提醒应以该数据包为基础。第一版先产出可调用的跟进清单；发送到哪一个提醒渠道在接入验证后决定。

### 4.3 周期经营报告数据包

```http
GET /api/agent/reports/periodic?period=QUARTER&date=2026-05-22
X-Agent-Key: {agent_key}
```

第一版给 Agent 返回月度、季度、年度复盘所需的结构化数据：

- 销售额、销量、订单数、客户数和毛利权限可见字段。
- Top 款式与走弱款式。
- 客户贡献、复购、沉默客户变化。
- 库存周转、低库存和积压事实。
- 与上一周期的对比数据。

### 4.4 颜色尺码结构

```http
GET /api/agent/analytics/sku-mix?productId=123&period=MONTH
X-Agent-Key: {agent_key}
```

返回同一款式下颜色、尺码、SKU 的销售和库存差异，让 Agent 能区分：

- 商品整体卖得好，但某些颜色或尺码压货。
- 商品总库存看似足够，但热卖 SKU 已缺货。
- 哪些颜色/尺码更适合下一轮补货或减量。

### 4.5 库存建议事实

```http
GET /api/agent/inventory/recommendations?period=MONTH
X-Agent-Key: {agent_key}
```

第一版先返回库存判断事实，不自动下采购单：

- 积压款和积压 SKU。
- 低库存但销售速度高的补货候选。
- 低库存但销售弱的观察项。
- 缺货可能影响销售的款式事实。
- 多仓库存差异和后续调拨判断所需事实。

### 4.6 客户风险与分层事实

```http
GET /api/agent/customers/risk?period=YEAR
X-Agent-Key: {agent_key}
```

用于补充跟进清单，第一版先输出：

- 核心客户、增长客户、新客户、低活跃客户和流失风险客户事实。
- 历史拿货频率明显下降的客户。
- 拿货金额或商品偏好出现变化的客户。
- 建议跟进优先级和原因。

### 4.7 统一搜索

```http
GET /api/agent/search?q={keyword}&scope=all&limit=10
X-Agent-Key: {agent_key}
```

第一期支持的 `scope`：

| scope | 说明 |
|-------|------|
| `all` | 同时查订单、客户、商品/SKU |
| `orders` | 订单号、客户名 |
| `customers` | 客户名、电话 |
| `products` | 商品名、商品编码、SKU 编码 |

搜索结果只返回跳转和进一步查询所需的最小字段，不直接塞入完整订单详情。

---

## 五、安全设计

### 5.1 Agent 凭证

Agent 不复用前端登录态。建议新增 Agent 凭证模型：

| 字段 | 说明 |
|------|------|
| `id` | 凭证主键 |
| `name` | Agent 名称 |
| `key_hash` | API Key 哈希值，不保存明文 |
| `tenant_id` | 绑定租户 |
| `scopes` | 权限范围，如 `agent:analytics:read` |
| `allow_profit_data` | 是否允许读取成本、毛利、毛利率 |
| `status` | 启用/禁用 |
| `last_used_at` | 最近调用时间 |
| `create_time` | 创建时间 |

请求经 Agent 认证过滤器通过后，必须把租户写入 `TenantContext`，并把 Agent scope 映射到接口权限判断。

### 5.2 第一阶段权限建议

| scope | 能力 |
|-------|------|
| `agent:followup:read` | 客户跟进任务 |
| `agent:search:read` | 统一搜索 |
| `agent:analytics:read` | 款式趋势和经营分析包装接口 |
| `agent:inventory:read` | 库存建议事实 |
| `agent:customers:risk:read` | 客户风险与分层事实 |
| `agent:reports:read` | 月度、季度、年度报告数据包 |
| `agent:analytics:profit` | 成本、毛利、毛利率 |

### 5.3 数据与接口规则

1. 所有 Agent API 默认只读。
2. 不把客户完整电话、地址、图片和无关备注塞进摘要接口。
3. 成本、毛利等字段沿用现有数据权限思想，不因 Agent 场景放宽。
4. API Key 日志不得输出明文。
5. 限流按 Agent 凭证维度执行，避免循环调用拖垮聚合接口。
6. 对外开放前要重新收紧客户 API 的认证边界，不能依赖公开 CRUD 接口给 Agent 取数。

---

## 六、WhatsApp 数据接入

### 6.1 定位

WhatsApp 信息不是订单真相来源，而是客户沟通上下文来源。接入后 Agent 可以把“客户最近沟通内容”和“系统里的订单、拿货周期、偏好、欠款/发货状态”合并分析，提升跟进建议质量。

### 6.2 已锁定的本地归档 v1 边界（2026-08-24）

1. 当前 WhatsApp Business Mac App 作为只读源，原号码不变；后续 Business Platform 是可替换数据源，不是 v1 前置条件。
2. 原始 SQLite 快照与媒体保存在 Git 外的加密本地目录；Blade 只保存结构化事实和受控媒体资产。
3. v1 导入 1:1 联系人、会话、文本和已下载媒体，排除群聊、状态、频道、广播、通话和发送能力。
4. 通过规范化号码生成 CRM 唯一精确匹配候选，由人工确认；不自动创建或覆盖 `crm_customer`。
5. Collector 使用独立写入凭证；Agent 不读原始快照或数据库，只通过租户/scoped Gateway 读取必要事实。
6. 第一阶段不让 Agent 代表用户自动发送 WhatsApp 消息，不自动修改人工标签或执行营销。
7. 详细字段、幂等算法、安全边界和 SOW 见 [WhatsApp Mac 本地归档 ROM/SOW](./superpowers/plans/2026-08-24-whatsapp-local-archive-rom-sow.md)。

### 6.3 第二阶段能力草案

| 能力 | 说明 |
|------|------|
| 客户沟通时间线 | 把订单、跟进、WhatsApp 摘要按时间合并 |
| 跟进建议增强 | 区分“长期未下单但最近有沟通”和“长期沉默且无沟通” |
| 沟通摘要 | 提取客户关注的款、颜色、交付问题和意向 |
| 问价未成交 | 识别有咨询或报价但暂未下单的客户和款式 |
| 沟通热度对比 | 识别聊天热度高但下单转化低的款式或反馈主题 |
| 周期复盘 | 把客户反馈主题纳入月报、季报、年报建议 |

### 6.4 已锁定的混合分析链路（2026-08-24）

1. Mac Collector 只执行确定性解析、去重、哈希、媒体检查和上传，不在终端运行模型。
2. Blade/NAS 在确认客户绑定后，把新增消息与订单/商品事实组成版本化分析任务。
3. Agent Worker 只能通过 `agent:whatsapp:analyze` scope 领取任务；上下文默认 90 天、最多 200 条，并用稳定客户别名替代姓名/电话。
4. 正文在出 Gateway 前移除电话号码、邮箱和 URL；默认不返回地址、媒体、客户备注、付款敏感信息和全量历史。
5. Worker 可以部署在 NAS 并连接本地模型，也可以连接云端模型；ERP 不保存第三方模型 API Key。
6. 输出必须引用当前任务内的消息 ID；服务端拒绝跨任务、跨客户或跨租户证据。
7. 推荐只进入 ERP 待处理队列，由用户采纳、忽略或标记完成；不自动发送 WhatsApp。

---

## 七、审计与事件

### 7.1 当前基础

当前系统已有：

- `inventory_log`：库存变动记录。
- `crm_customer_operation_log`：客户创建、修改、删除操作日志。
- 订单配货调整日志。

这些日志能支撑部分历史查询，但还不足以稳定提供跨模块 `/api/agent/changes`。

### 7.2 后续统一事件日志

在 Agent 需要增量变化、动作回执和跨模块审计前，建议新增统一业务事件或审计表，记录：

| 字段 | 说明 |
|------|------|
| `entity_type` | `order` / `customer` / `inventory` / `product` 等 |
| `entity_id` | 业务主键 |
| `event_type` | `created` / `updated` / `status_changed` / `payment_added` 等 |
| `operator_type` | `user` / `agent` / `system` |
| `operator_id` | 操作来源 |
| `before_data` | 必要的变更前 JSON |
| `after_data` | 必要的变更后 JSON |
| `description` | 面向追溯的简要说明 |
| `tenant_id` | 租户 |
| `create_time` | 事件时间 |

统一事件日志落地后，再设计 `/api/agent/changes?since=...` 和窄范围 Agent 写动作。

---

## 八、实施顺序

| 阶段 | 内容 | 说明 |
|------|------|------|
| Phase A | 需求与安全边界锁定 | PRD、任务、鉴权策略、客户接口认证边界 |
| Phase B | Agent 只读鉴权 | Agent Key、租户绑定、scope、调用审计、限流基础 |
| Phase C | Agent Gateway v1 | 款式趋势、客户跟进、客户风险、颜色尺码结构、库存建议事实、周期报告数据包、统一搜索 |
| Phase D | 验证接入 | 用 Hermes 或等价 Agent 在真实接口上验证工具调用和返回稳定性 |
| Phase E | WhatsApp 本地归档 v1 | Mac 只读快照、结构化事实、CRM 绑定、导入权限和完整性工作台已完成 |
| Phase F | 事件与动作扩展 | 统一业务事件日志、变化订阅、窄范围授权写动作 |

### 8.1 后续能力路线

| 阶段 | 候选能力 | 前置条件 |
|------|----------|----------|
| Phase G | 订单运营异常、退款异常、尾款拖延 | 订单事件和支付口径收敛 |
| Phase H | 利润解释、客户利润贡献、高销售低毛利预警 | 毛利与成本权限稳定 |
| Phase I | WhatsApp 客户摘要、偏好、意向、风险与跟进建议 | V45 队列、scoped 脱敏上下文、可替换 Worker、证据校验和 ERP 人工工作流已完成；自动营销仍禁止 |
| Phase J | 经营记忆、决策结果回看 | 统一事件日志和人工确认规则 |

---

## 九、验收标准

### 9.1 第一版验收

1. 外部 Agent 仅持有 Agent Key 就能在绑定租户内读取款式趋势、客户跟进清单、客户风险、颜色尺码结构、库存建议事实、周期报告数据包和搜索结果。
2. 不同租户的 Agent Key 不能读到彼此数据。
3. 无 `agent:analytics:profit` scope 时，报告和分析结果不返回成本、毛利、毛利率。
4. 款式趋势结果能显示多周期事实和建议依据，能区分持续向好、观察和走弱。
5. 客户跟进清单能基于订单日期和跟进规则给出提醒依据。
6. 客户风险数据能支持流失风险和跟进优先级判断。
7. 颜色尺码结构和库存建议事实能支持 Agent 区分补货、观察和积压风险。
8. 月度、季度、年度报告数据包能支持 Agent 生成周期分析和建议。
9. Agent Gateway 的统计口径与现有看板/数据分析口径一致。
10. 搜索返回限制条数，接口有可验证的限流或防滥用策略。
11. 对 Agent 对接做一次真实环境调用验证，记录结果后再认定任务完成。

### 9.2 明确非验收项

- 第一版不要求后端理解自然语言问题。
- 第一版不要求 Agent 创建或编辑订单。
- Agent Gateway 第一版不要求 WhatsApp 分析接口完成；WhatsApp 本地归档由独立 BE-566～BE-571 分阶段验收。
- 第一版不要求消息推送、微信提醒或长轮询事件流。
- 第一版不要求新增 Agent 管理前端页面；可先由后端管理流程生成测试凭证。

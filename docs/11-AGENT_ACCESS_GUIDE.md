# 外部 Agent 接入指南

> 本文档面向接入 BladeProject 的外部 Agent、Agent 工具开发者和自动化任务。
> 本文档只描述可执行的接入约定。需求边界和后续路线见 [10-AGENT_INTEGRATION_DESIGN.md](./10-AGENT_INTEGRATION_DESIGN.md)。
> 第一次配置 Codex、ZCode、DeepSeek 或其他本机 Agent 时，先完整阅读 [19-AGENT_CONNECTION_PLAYBOOK.md](./19-AGENT_CONNECTION_PLAYBOOK.md)。

---

## 一、当前接入状态

截至 2026-09-18，BladeProject 生产环境已在 release `20260916_104900`、Flyway V60 落地 Agent Gateway 鉴权、商品/订单读取、客户敏感资料读取、商品/客户新增、订单草稿窄写入、纸单原图关联和 Owner 凭证管理：

| 能力 | 状态 | 接口 |
|------|------|------|
| Agent Key 独立鉴权 | 已实现 | 请求头 `X-Agent-Key` |
| 租户绑定和 scope 鉴权 | 已实现 | Agent Key 认证后写入租户上下文 |
| 调用审计和最近使用信息 | 已实现 | 成功请求记录路径、状态、耗时、IP、User-Agent |
| Owner 签发、轮换与停用 | 已实现 | 系统管理 → Agent Key；完整密钥仅显示一次 |
| 纸单原图上传 | 已实现 | `POST /api/agent/order-drafts/source-files`；本机工具 `blade_order_draft_source_upload`，使用 `agent:orders:write` |
| 纸单批量订单草稿 | 已实现 | `POST /api/agent/order-drafts/batch`；每单通过 `sourceFileIds` 关联最多 10 张原图 |
| 款式趋势数据包 | 已实现 | `GET /api/agent/analytics/style-trends` |
| 多周期趋势标签、建议依据 | 已实现 | `GROWING` / `STABLE` / `DECLINING` / `INSUFFICIENT_DATA` |
| 颜色尺码结构事实包 | 已实现 | `GET /api/agent/analytics/sku-mix` |
| 商品主档查询 | 已实现 | `GET /api/agent/products`、`/{id}`、`/options`；每页最多 100 条，不含成本价 |
| 正式订单查询 | 已实现 | `GET /api/agent/orders`、`/{id}`；不含电话、地址、成本和毛利 |
| 新增商品 | 已实现 | `POST /api/agent/products`；同编码不覆盖，不产生库存，不支持修改/删除 |
| 客户列表与详情 | 已实现 | `GET /api/agent/customers`、`/{id}`；包含电话、地址、备注，需独立敏感只读 scope |
| 新增客户 | 已实现 | `POST /api/agent/customers`；重复电话不覆盖，不支持修改/删除 |
| 客户跟进、客户风险、周期报告、搜索 | 规划中 | 不可按已上线接口调用 |
| WhatsApp 分析 Worker | 已实现的专用通道 | `claim/complete/fail`；普通商品/订单 Agent 不应默认勾选 |

外部 Agent 当前只能把已实现接口当成稳定调用入口。规划中的接口可用于工具设计预留，但不能假定已经可访问。生产入口是 `https://www.chenjianas.asia:33294`，当前 Mac 已安装本机授权程序 `/Users/chenjiarun/Library/Application Support/Blade Agent Key Manager/bin/blade-agent-request`。

系统管理页面按 Key 勾选 scope。调整既有 Key 权限会签发替代 Key 并立即停用旧 Key；新增权限不会自动授予历史 Key。页面中显示的是不带技术前缀的 scope（例如 `products:read`），Controller 校验的完整 authority 为 `agent:products:read`。

---

## 二、接入前准备

### 2.1 获取基础信息

接入方需要从 BladeProject 管理方拿到：

| 信息 | 说明 |
|------|------|
| `baseUrl` | BladeProject 可访问入口；由 Agent 运行环境配置，不得写死。当前外网生产入口为 `https://www.chenjianas.asia:33294` |
| `agentKey` | 绑定租户的 Agent Key 原文，只在创建时交付 |
| `scopes` | 该 key 可访问的 Agent 权限范围 |
| 调用频率约束 | 避免 Agent 循环重试或高频轮询聚合接口 |

### 2.2 Agent Key 约定

Agent API 不复用 PC 管理端或移动端的 JWT 登录态。所有 Agent 请求使用：

```http
X-Agent-Key: {agent_key}
```

当前 Agent Key 原文格式为：

```text
{prefix}.{secret}
```

系统只保存 `secret` 哈希，不保存可回显的完整 key。接入方必须把 Agent Key 当作密钥处理：

1. 不把原始 key 写进 Agent 提示词、日志、截图或错误回显。
2. 不把原始 key 放到前端浏览器环境。
3. 不把某个租户的 key 用于另一个租户的数据请求。
4. key 失效、禁用或过期时，向 BladeProject 管理方申请替换。

当前代码已支持 Agent Key 表、后端认证和 Owner 管理入口。创建或轮换时应立即保存一次性返回的完整 Key；关闭窗口后只能看到公开前缀，无法恢复原密钥。

### 2.3 Mac 与 NAS 不同网络时的地址配置

纸单识别 Agent 通常运行在用户的 Mac，而 BladeProject 运行在 NAS。Agent 客户端必须从运行环境读取入口：

```bash
BLADE_AGENT_API_BASE_URL=https://www.chenjianas.asia:33294
BLADE_AGENT_KEY=your_agent_key_here
```

约定：

1. `BLADE_AGENT_API_BASE_URL` 只填写协议、主机和端口，不包含 `/api`，保存时移除末尾 `/`。
2. Agent 在该地址后拼接 `/api/agent/...`，例如 `${BLADE_AGENT_API_BASE_URL}/api/agent/order-drafts/batch`。
3. 当前外网生产默认配置为 `https://www.chenjianas.asia:33294`；地址变化时只改运行配置，不改代码、提示词或 Excel 模板。
4. 本地开发、测试、局域网生产和外网生产使用不同配置文件及不同 Agent Key，不得共用生产密钥。
5. 外网入口只暴露 Nginx HTTPS 网关，不开放后端容器、MySQL、Redis 或 NAS 管理端口。
6. 发布前必须从实际运行 Agent 的 Mac 验证 DNS、TLS 证书、健康检查、批量写入和幂等重试；不得通过关闭证书校验长期运行。

推荐配置示例：

```text
开发：BLADE_AGENT_API_BASE_URL=http://127.0.0.1:8080
局域网生产：BLADE_AGENT_API_BASE_URL=https://192.168.1.10:8899
外网生产：BLADE_AGENT_API_BASE_URL=https://www.chenjianas.asia:33294
```

Agent Key 应存放在 macOS 钥匙串或受保护的进程环境中；URL 可以进入普通配置，但密钥不能写入仓库、提示词、Excel 或日志。

对于当前 Mac 使用场景，首选 [Blade Agent Key Manager 本机管理与授权方案](./16-AGENT_LOCAL_KEY_MANAGER.md)：用户通过桌面应用粘贴一次完整 Key，应用将密钥写入 macOS 钥匙串；外部 Agent 通过本机 MCP/调用工具请求授权，由本机工具注入 `X-Agent-Key`，模型只获得 API 结果。上面的环境变量方式保留给无人值守服务端进程，不再作为普通用户首选。

### 2.4 Key 命名、有效期和多 Agent 隔离

1. 一把 Key 只分配给一个 Agent 和一个环境，例如“DeepSeek 生产纸单录入”；不要让 DeepSeek、ZCode、Codex 共用同一把 Key。
2. 本机管理器记录 Key 名称、Agent、公开前缀、scope、API 地址和到期日期，完整 Key 只存钥匙串。
3. 本机显示的剩余时间是提醒；服务器 `expires_time/status` 是最终真相。轮换、停用后要同步清理本机记录。
4. Agent 需要调用时由用户在系统弹窗中选择 Key 并授权，可对同一 Agent + scope 临时授权 1 小时。
5. 纯网页聊天没有本机 MCP/命令能力时不能调用本机 Key，不得把生产 Key 粘贴进聊天窗口。

---

## 三、调用规则

### 3.1 请求示例

```bash
curl -s \
  -H "X-Agent-Key: ${BLADE_AGENT_KEY}" \
  "${BLADE_AGENT_API_BASE_URL%/}/api/agent/analytics/style-trends?periodType=MONTH&comparePeriods=3&limit=20"
```

### 3.2 统一响应格式

Agent API 复用 BladeProject 统一响应结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1779427200
}
```

接入方应先判断 HTTP 状态和 `code`，再消费 `data`。

### 3.3 失败处理

| 情况 | 接入方处理 |
|------|------------|
| 缺少或无效 Agent Key | 视为认证失败，不自动改用用户 JWT |
| scope 不足 | 视为权限不足，不继续尝试同类高权限接口 |
| 参数不合法 | 修正参数后重试，不做无限循环 |
| 5xx 或网络失败 | 做有限次数退避重试，并保留错误上下文 |

外部 Agent 不应根据失败结果绕开 Gateway 去访问 CRUD API、数据库、Redis 或文件存储。

本机授权弹窗显示“没有未过期且包含某权限的 Key”时，说明 MCP 已启动，但当前工具所需 scope 与已保存 Key 不匹配。纸单录入 Key 通常只有 `catalog:read`、`orders:write` 和按需的 `analytics:read`，应使用 `blade_catalog_search` 验证连接；`blade_products_list` 需要另行授权 `products:read`。

### 3.4 商品与订单数据工具

| 本机工具 | scope | HTTP 接口 | 说明 |
|------|------|------|------|
| `blade_products_list` | `agent:products:read` | `GET /api/agent/products` | 分页读取商品；`size` 最大 100 |
| `blade_product_get` | `agent:products:read` | `GET /api/agent/products/{id}` | 商品与 SKU 详情，不含成本价 |
| `blade_product_options` | `agent:products:read` | `GET /api/agent/products/options` | 可用分类、颜色、尺码 |
| `blade_orders_list` | `agent:orders:read` | `GET /api/agent/orders` | 分页读取正式订单；草稿不在其中 |
| `blade_order_get` | `agent:orders:read` | `GET /api/agent/orders/{id}` | 订单商品明细，不含隐私/成本/毛利 |
| `blade_product_create` | `agent:products:create` | `POST /api/agent/products` | 新增商品；重复款号返回 `DUPLICATE` |
| `blade_customers_list` | `agent:customers:read` | `GET /api/agent/customers` | 分页读取客户名称、电话、地址和备注 |
| `blade_customer_get` | `agent:customers:read` | `GET /api/agent/customers/{id}` | 读取单个客户敏感资料 |
| `blade_customer_create` | `agent:customers:create` | `POST /api/agent/customers` | 新增客户；重复电话返回 `DUPLICATE` |

“获取所有”表示按页循环，不能把 `size` 改成无限值。新增商品的 `colorCodes`、`sizeCodes` 必须引用 `blade_product_options` 返回的已启用编码；接口不会顺带创建新颜色/尺码。无规格商品由现有商品服务生成 `DEFAULT/NA-NA`，有规格商品生成真实组合并自动维护 `PLACEHOLDER/UNSPECIFIED-UNSPEC`，Agent 不得直接传系统保留编码。

新增商品请求示例：

```json
{
  "productCode": "7000#",
  "name": "7000#",
  "categoryId": 12,
  "unit": "件",
  "wholesalePrice": 45.00,
  "colorCodes": ["BLACK", "WHITE"],
  "sizeCodes": ["S", "M"]
}
```

允许字段还包括 `weight`、`description`、`remark`。接口不接收供应商、成本价、库存数量、商品状态、SKU 编码或保留颜色尺码；创建结果为 `CREATED` 或 `DUPLICATE`。需要新建颜色/尺码时，仍由用户在系统中确认后新增，避免 Agent 生成大量重复字典值。

客户读取与订单读取必须分开授权。`orders:read` 不包含电话和地址；`customers:read` 明确允许读取客户名称、电话、地址和备注，属于敏感只读权限。需要全量客户时按 `current`、`size` 分页读取，单页最多 100 条。

新增客户请求示例：

```json
{
  "name": "客户名称",
  "phones": ["+86 138-0000-0000"],
  "countryCode": "+86",
  "address": "客户地址",
  "remark": "人工确认后的备注"
}
```

电话号码会移除空格、横杠和加号后保存并查重；同租户任一号码已存在时返回 `DUPLICATE` 和冲突号码，不覆盖原客户。只有同一 Key 还拥有 `customers:read` 时才返回原客户 ID/名称，避免只新增权限绕过敏感读取控制。新增成功返回 `CREATED`，并记录发起操作的 Agent Key。接口不提供客户修改、删除、合并和标签调整。

---

## 四、当前已实现接口

### 4.1 款式趋势数据包

```http
GET /api/agent/analytics/style-trends
X-Agent-Key: {agent_key}
```

**所需 scope**：

```text
agent:analytics:read
```

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `periodType` | string | 否 | `TODAY` / `WEEK` / `MONTH` / `QUARTER` / `YEAR` / `CUSTOM`，默认 `WEEK` |
| `startDate` | date | 否 | `periodType=CUSTOM` 时的开始日期，格式 `yyyy-MM-dd` |
| `endDate` | date | 否 | `periodType=CUSTOM` 时的结束日期，格式 `yyyy-MM-dd` |
| `comparePeriods` | int | 否 | 对比周期数量，默认 `3`，当前限制 1-6 |
| `limit` | int | 否 | 返回款式数量，默认 `20` |

**请求示例**：

```http
GET /api/agent/analytics/style-trends?periodType=MONTH&comparePeriods=3&limit=20
X-Agent-Key: {agent_key}
```

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "dimension": "PRODUCT",
    "sortBy": "SALES",
    "periodType": "MONTH",
    "comparePeriods": 3,
    "rows": [
      {
        "key": "624-1#",
        "label": "624-1#",
        "productName": "624-1#",
        "orderCount": 5,
        "salesQuantity": 16,
        "salesAmount": 1200.00,
        "trend": "GROWING",
        "recommendation": "KEEP",
        "periodSeries": [
          {
            "periodLabel": "2026-05",
            "startDate": "2026-05-01",
            "endDate": "2026-05-31",
            "orderCount": 5,
            "salesQuantity": 16,
            "salesAmount": 1200.00
          },
          {
            "periodLabel": "2026-04",
            "startDate": "2026-04-01",
            "endDate": "2026-04-30",
            "orderCount": 3,
            "salesQuantity": 10,
            "salesAmount": 800.00
          }
        ],
        "reasons": [
          "连续 3 个周期销量增长"
        ]
      }
    ]
  }
}
```

**当前解释边界**：

1. 当前返回的是款式多周期趋势事实包，可用于 Agent 初步判断继续做、观察或减少投入。
2. 当前结果不包含成本、毛利、毛利率。
3. 当前趋势依据为多周期销量变化，尚未叠加库存、客户覆盖面和利润事实。
4. 是否补货、停做或减量仍需结合库存建议接口和人工判断。

### 4.2 颜色尺码结构事实包

```http
GET /api/agent/analytics/sku-mix
X-Agent-Key: {agent_key}
```

**所需 scope**：

```text
agent:analytics:read
```

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `productName` | string | 是 | 款式名称，例如 `624-1#` |
| `periodType` | string | 否 | `TODAY` / `WEEK` / `MONTH` / `QUARTER` / `YEAR` / `CUSTOM`，默认 `WEEK` |
| `startDate` | date | 否 | `periodType=CUSTOM` 时的开始日期，格式 `yyyy-MM-dd` |
| `endDate` | date | 否 | `periodType=CUSTOM` 时的结束日期，格式 `yyyy-MM-dd` |
| `limit` | int | 否 | 每组返回数量，默认 `20` |

**请求示例**：

```http
GET /api/agent/analytics/sku-mix?productName=624-1%23&periodType=MONTH&limit=20
X-Agent-Key: {agent_key}
```

**响应示例**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "productName": "624-1#",
    "periodType": "MONTH",
    "skus": [
      {
        "key": "624-1#-BLK-L",
        "label": "624-1# / 黑 / L",
        "skuCode": "624-1#-BLK-L",
        "colorName": "黑",
        "sizeName": "L",
        "orderCount": 3,
        "salesQuantity": 16,
        "salesAmount": 1200.00,
        "signal": "HOT"
      }
    ],
    "colors": [],
    "sizes": [],
    "reasons": [
      "SKU 624-1#-BLK-L 销量最高，销售 16 件"
    ]
  }
}
```

**当前解释边界**：

1. 当前返回的是同款 SKU、颜色、尺码销售结构事实。
2. `signal` 当前只表示销售结构：`HOT` / `NORMAL` / `LOW`。
3. 当前结果不包含成本、毛利、毛利率。
4. 当前结果还不判断缺货或积压；库存缺货、积压和补货优先级由库存建议接口承接。

---

## 五、外部 Agent 工具封装建议

外部 Agent 应把每个 Agent API 封装成结构化工具，而不是让模型自行拼接任意 URL。

第一版可以先提供一个工具：

| 工具字段 | 建议 |
|----------|------|
| tool name | `blade_get_style_trends` |
| purpose | 获取指定周期内款式销售事实，作为趋势分析输入 |
| inputs | `periodType`、`startDate`、`endDate`、`limit` |
| auth | 服务端注入 `X-Agent-Key`，不暴露给模型 |
| output | 原样保留统一响应的 `data` 字段和必要错误信息 |

工具描述建议明确写出：

```text
Use this tool to fetch BladeProject style sales facts for a period.
Do not use it alone to claim long-term growth or discontinuation decisions
until multi-period trend facts and inventory reasons are available.
```

---

## 六、规划中的 Agent 接口

以下接口来自已锁定需求和设计文档，当前用于后续实现对齐：

| 接口 | 预期 scope | 说明 |
|------|------------|------|
| `/api/agent/tasks/follow-up` | `agent:followup:read` | 待跟进客户清单 |
| `/api/agent/customers/risk` | `agent:customers:risk:read` | 客户流失风险与分层事实 |
| `/api/agent/inventory/recommendations` | `agent:inventory:read` | 库存积压、缺货和补货优先级事实 |
| `/api/agent/reports/periodic` | `agent:reports:read` | 月度、季度、年度经营分析数据包 |
| `/api/agent/search` | `agent:search:read` | 客户、订单、商品和 SKU 搜索 |

以下能力当前不开放：

| 能力 | 原因 |
|------|------|
| `/api/agent/query` 自然语言问答 | 第一阶段先由外部 Agent 调结构化工具 |
| `/api/agent/action` 泛化写操作 | 订单、库存和收款写操作风险高 |
| WhatsApp 消息接口 | 接入方式、客户映射、权限和保留策略尚未锁定 |
| 数据库直连 | 破坏租户隔离、权限和统计口径 |

---

## 七、接入检查清单

接入完成前至少确认：

1. Agent Key 保存在服务端密钥环境或 macOS 钥匙串中，模型和前端不可见。
2. 使用 `X-Agent-Key` 调用，不混用用户 JWT。
3. key 只能访问绑定租户的数据。
4. 工具只暴露当前已实现接口。
5. 工具描述写清当前事实边界，不把规划能力说成已上线能力。
6. 对认证失败、权限失败、参数失败和 5xx 做区分处理。
7. 有测试用例验证成功调用和失败路径。
8. Mac 用户优先通过本机授权代理调用；每个 Agent 使用独立 Key，Key 选择和授权由用户完成。
9. 涉及纸单或 Excel 草稿时，Agent 已完整阅读 [17-AGENT_ORDER_DRAFT_RUNBOOK.md](./17-AGENT_ORDER_DRAFT_RUNBOOK.md)。

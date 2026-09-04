# 外部 Agent 接入指南

> 本文档面向接入 BladeProject 的外部 Agent、Agent 工具开发者和自动化任务。
> 本文档只描述可执行的接入约定。需求边界和后续路线见 [10-AGENT_INTEGRATION_DESIGN.md](./10-AGENT_INTEGRATION_DESIGN.md)。

---

## 一、当前接入状态

截至 2026-09-05，BladeProject 已落地 Agent Gateway 鉴权、订单草稿窄写入和 Owner 凭证管理：

| 能力 | 状态 | 接口 |
|------|------|------|
| Agent Key 独立鉴权 | 已实现 | 请求头 `X-Agent-Key` |
| 租户绑定和 scope 鉴权 | 已实现 | Agent Key 认证后写入租户上下文 |
| 调用审计和最近使用信息 | 已实现 | 成功请求记录路径、状态、耗时、IP、User-Agent |
| Owner 签发、轮换与停用 | 已实现 | 系统管理 → Agent Key；完整密钥仅显示一次 |
| 纸单批量订单草稿 | 已实现 | `POST /api/agent/order-drafts/batch`，不要求上传原图 |
| 款式趋势数据包 | 已实现 | `GET /api/agent/analytics/style-trends` |
| 多周期趋势标签、建议依据 | 已实现 | `GROWING` / `STABLE` / `DECLINING` / `INSUFFICIENT_DATA` |
| 颜色尺码结构事实包 | 已实现 | `GET /api/agent/analytics/sku-mix` |
| 客户跟进、客户风险、周期报告、搜索 | 规划中 | 不可按已上线接口调用 |
| WhatsApp 信息接入 | 方案验证阶段 | 当前没有可调用接口 |

外部 Agent 当前只能把已实现接口当成稳定调用入口。规划中的接口可用于工具设计预留，但不能假定已经可访问。

---

## 二、接入前准备

### 2.1 获取基础信息

接入方需要从 BladeProject 管理方拿到：

| 信息 | 说明 |
|------|------|
| `baseUrl` | BladeProject 可访问入口；由 Agent 运行环境配置，不得写死。当前外网生产入口为 `https://frp-pen.com:33294` |
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
BLADE_AGENT_API_BASE_URL=https://frp-pen.com:33294
BLADE_AGENT_KEY=prefix.secret
```

约定：

1. `BLADE_AGENT_API_BASE_URL` 只填写协议、主机和端口，不包含 `/api`，保存时移除末尾 `/`。
2. Agent 在该地址后拼接 `/api/agent/...`，例如 `${BLADE_AGENT_API_BASE_URL}/api/agent/order-drafts/batch`。
3. 当前外网生产默认配置为 `https://frp-pen.com:33294`；地址变化时只改运行配置，不改代码、提示词或 Excel 模板。
4. 本地开发、测试、局域网生产和外网生产使用不同配置文件及不同 Agent Key，不得共用生产密钥。
5. 外网入口只暴露 Nginx HTTPS 网关，不开放后端容器、MySQL、Redis 或 NAS 管理端口。
6. 发布前必须从实际运行 Agent 的 Mac 验证 DNS、TLS 证书、健康检查、批量写入和幂等重试；不得通过关闭证书校验长期运行。

推荐配置示例：

```text
开发：BLADE_AGENT_API_BASE_URL=http://127.0.0.1:8080
局域网生产：BLADE_AGENT_API_BASE_URL=https://192.168.1.10:8899
外网生产：BLADE_AGENT_API_BASE_URL=https://frp-pen.com:33294
```

Agent Key 应存放在 macOS 钥匙串或受保护的进程环境中；URL 可以进入普通配置，但密钥不能写入仓库、提示词、Excel 或日志。

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

1. Agent Key 保存在服务端密钥环境中，模型和前端不可见。
2. 使用 `X-Agent-Key` 调用，不混用用户 JWT。
3. key 只能访问绑定租户的数据。
4. 工具只暴露当前已实现接口。
5. 工具描述写清当前事实边界，不把规划能力说成已上线能力。
6. 对认证失败、权限失败、参数失败和 5xx 做区分处理。
7. 有测试用例验证成功调用和失败路径。

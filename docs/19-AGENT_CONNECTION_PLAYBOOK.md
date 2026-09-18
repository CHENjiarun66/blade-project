# 让外部 Agent 连接 BladeProject

> 本手册用于让 Codex、ZCode、DeepSeek 或其他本机 Agent 在不接触密钥原文的前提下连接 BladeProject。完成本页后，Agent 应能发现工具、请求用户授权，并按所授权限调用生产系统。

## 一、先确认接入事实

当前生产环境已经部署 Agent Gateway。接入时使用以下固定事实：

| 项目 | 当前值 |
|------|--------|
| 生产入口 | `https://www.chenjianas.asia:33294` |
| Agent 路径前缀 | `/api/agent/` |
| Agent 鉴权 | `X-Agent-Key` |
| 当前生产数据库版本 | Flyway V60 |
| 当前生产发布 | `20260916_104900` |
| 推荐凭证方式 | Blade Agent Key Manager + 本机 MCP 授权代理 |
| 本机调用程序 | `/Users/chenjiarun/Library/Application Support/Blade Agent Key Manager/bin/blade-agent-request` |

Agent Key 已绑定租户和权限。请求体、查询参数和提示词都不得自行传入 `tenantId`。系统根据 Key 恢复租户上下文。

## 二、选择连接方式

普通本机 Agent 使用 MCP。没有 Model Context Protocol（MCP）能力、但能执行本机命令的编码 Agent 使用一次性命令。纯网页聊天不能连接本机钥匙串。

| 运行方式 | 接入方式 | 是否推荐 |
|----------|----------|----------|
| Codex、ZCode 或支持本机 MCP 的桌面 Agent | stdio MCP | 推荐 |
| 能执行本机命令的编码 Agent | `blade-agent-request` 一次性命令 | 可用 |
| DeepSeek 等纯网页聊天 | 不支持直接连接 | 禁止粘贴生产 Key |
| 受控服务器自动任务 | 独立服务账号与密钥管理系统 | 需单独审批 |

## 三、由用户准备 Agent Key

Agent 不能自行签发或读取 Key。用户按以下步骤准备凭证：

1. 登录 BladeProject，进入 **系统管理 → Agent Key**
2. 为当前 Agent 新建一把独立 Key
3. 只勾选任务需要的 scope
4. 设置有效期并保存一次性显示的完整 Key
5. 双击桌面的 `Blade Agent Key Manager.app`
6. 录入 Key、Agent 名称、到期日期和生产入口
7. 确认 API 地址是 `https://www.chenjianas.asia:33294`，不能包含 `/api`

每个 Agent、环境和用途使用独立 Key。例如“ZCode 生产商品查询”和“订单识别 Agent 生产草稿录入”应使用两把 Key。

## 四、为本机 Agent 配置 MCP

支持 stdio MCP 的客户端增加以下配置。`command` 必须使用绝对路径，`--agent` 必须固定为当前 Agent 名称。

```json
{
  "mcpServers": {
    "blade-project": {
      "command": "/Users/chenjiarun/Library/Application Support/Blade Agent Key Manager/bin/blade-agent-request",
      "args": ["--mcp", "--agent", "ZCode"]
    }
  }
}
```

为其他 Agent 配置时，只替换 `ZCode`。不要把 Key、账号密码、JWT 或 API 地址写进 MCP 配置。

保存配置后重启 Agent。客户端应发现以下 14 个工具：

| 工具 | 所需 scope | 作用 |
|------|------------|------|
| `blade_catalog_search` | `catalog:read` | 查询商品与 SKU 候选 |
| `blade_products_list` | `products:read` | 分页查询商品主档 |
| `blade_product_get` | `products:read` | 查询商品与 SKU 详情 |
| `blade_product_options` | `products:read` | 查询新增商品可用选项 |
| `blade_product_create` | `products:create` | 新增商品 |
| `blade_orders_list` | `orders:read` | 分页查询正式订单 |
| `blade_order_get` | `orders:read` | 查询正式订单详情 |
| `blade_customers_list` | `customers:read` | 分页查询客户敏感资料 |
| `blade_customer_get` | `customers:read` | 查询客户详情 |
| `blade_customer_create` | `customers:create` | 新增客户 |
| `blade_order_draft_source_upload` | `orders:write` | 上传纸单原图 |
| `blade_order_drafts_create` | `orders:write` | 批量创建订单草稿 |
| `blade_style_trends` | `analytics:read` | 查询款式趋势事实 |
| `blade_sku_mix` | `analytics:read` | 查询颜色尺码结构事实 |

页面中显示的 scope 不带 `agent:` 前缀。本机管理器使用 `products:read`，后端权限表达式使用 `agent:products:read`，两者代表同一权限。

## 五、完成第一次连接验证

第一次验证只执行当前 Key 已授权的只读请求。不要用新增商品、客户或订单草稿作为连通性测试。

1. 根据当前 Key 的 scope 选择下表中的只读工具
2. 在 Mac 弹窗中确认 Agent 名称、接口和 scope
3. 选择这次任务对应的 Key
4. 点击 **允许一次** 或 **允许 1 小时**
5. 检查返回体的 HTTP 状态和业务 `code`

| 当前 Key 的只读 scope | 第一次验证工具 | 最小参数 |
|------------------------|----------------|----------|
| `catalog:read` | `blade_catalog_search` | `limit=1` |
| `products:read` | `blade_products_list` | `current=1`、`size=1` |
| `orders:read` | `blade_orders_list` | `current=1`、`size=1` |
| `customers:read` | `blade_customers_list` | `current=1`、`size=1` |
| `analytics:read` | `blade_style_trends` | `periodType=MONTH`、`limit=1` |

纸单录入 Key 通常只有 `catalog:read`、`orders:write`，因此第一次验证应调用 `blade_catalog_search`。如果调用 `blade_products_list`，本机代理会要求额外的 `products:read`，这不是网络故障。

成功响应使用统一结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

如果当前 Agent 不支持 MCP，可执行本机授权命令。命令不会接收或输出 Key：

```bash
"/Users/chenjiarun/Library/Application Support/Blade Agent Key Manager/bin/blade-agent-request" \
  --agent ZCode \
  --method GET \
  --path "/api/agent/catalog/skus?limit=1"
```

授权 1 小时只在当前 MCP 进程中有效。一次性命令每次都会创建新进程，因此每次请求都需要授权。

## 六、根据任务选择最小权限

Key 只授予完成当前任务需要的 scope：

| 任务 | 建议 scope |
|------|------------|
| 纸单款号匹配并创建草稿 | `catalog:read`、`orders:write` |
| 商品和正式订单分析 | `products:read`、`orders:read` |
| 款式趋势和规格结构分析 | `analytics:read` |
| 新增商品 | `products:read`、`products:create` |
| 查询客户 | `customers:read` |
| 新增客户 | `customers:create`，按需再加 `customers:read` |
| WhatsApp 专用 Worker | `whatsapp:analyze`，不得与普通业务 Key 共用 |

`customers:read` 会返回电话、地址和备注。只有任务确实需要客户隐私时才授予。

## 七、遵守写操作边界

当前 Agent Gateway 只开放两类窄写入：新增主数据和创建待人工确认的订单草稿。

- `products:create` 只能新增商品，同款号返回 `DUPLICATE`，不修改旧商品，不写库存
- `customers:create` 只能新增客户，重复电话返回 `DUPLICATE`，不覆盖旧资料
- `orders:write` 只能上传纸单原图和创建草稿，不确认正式订单
- Agent 不能修改或删除商品、客户
- Agent 不能收款、退款、短款核销、配货、出库或调整库存
- Agent 不能执行订单回退、审批或冲销
- Agent 不能访问后台 JWT 接口、MySQL、Redis、NAS 文件路径或通用 CRUD 接口

涉及纸单、Excel 或订单草稿时，必须完整阅读 [Agent 纸单与 Excel 批量订单草稿操作手册](./17-AGENT_ORDER_DRAFT_RUNBOOK.md)。该手册定义 SPU/SKU 匹配、金额优先级、图片上传、幂等键和警告保留规则。

## 八、分页读取全部数据

商品、订单和客户列表每页最多 100 条。需要完整数据时，从 `current=1` 开始读取，根据响应中的 `pages` 逐页递增，直到 `current >= pages`。

不要把 `size` 设置成超过 100 的数值，不要并发抓取大量页面，也不要在认证或参数失败后循环重试。

## 九、处理连接错误

| 现象 | 原因 | 处理 |
|------|------|------|
| Agent 看不到 `blade_*` 工具 | MCP 配置未加载或路径错误 | 使用绝对路径并重启 Agent |
| 弹窗没有可选 Key | Key 已过期或缺少当前工具要求的 scope | 在 Key Manager 检查到期日和 scope；也可改用当前 Key 已授权的只读工具 |
| 用户拒绝，命令退出码为 77 | 本次授权未通过 | 停止调用并等待用户重新授权 |
| HTTP 401，命令退出码为 78 | Key 原文无效、已过期或已停用 | 让 Owner 轮换 Key并更新 Key Manager |
| HTTP 403 | 当前 Key 缺少接口所需 scope | 停止重试，让 Owner 按最小权限重新签发 |
| HTTP 400 | 请求字段、分页或日期格式错误 | 按接口文档修正参数后重试 |
| 返回 HTML 或 `needsLogin` | 调用了网页/JWT 路径，而不是 Agent Gateway | 改用 `blade_*` 工具或 `/api/agent/...` |
| TLS 或连接失败 | 地址、DNS、端口或证书异常 | 核对生产入口，不得关闭证书校验 |
| HTTP 5xx 或网络中断 | 服务暂时不可用 | 最多有限次数退避重试，写操作复用原幂等键 |

本机删除 Key 不会撤销服务器权限。真正撤销访问必须在 **系统管理 → Agent Key** 中停用 Key。

## 十、交给其他 Agent 的标准指令

把下面这段文字交给需要接入的 Agent：

```text
开始前完整阅读 docs/19-AGENT_CONNECTION_PLAYBOOK.md。
只通过 blade-project MCP 中的 blade_* 工具连接 BladeProject。
不得索要、读取、输出或保存 Agent Key、账号密码或 JWT。
第一次调用必须选择当前 Key 已授权的只读工具。纸单录入 Key 使用 blade_catalog_search，limit=1；拥有 products:read 时才使用 blade_products_list。
写操作前先向我说明将调用的工具、所需 scope、数据范围和幂等策略，得到确认后再执行。
不得调用商品/客户修改删除、正式订单确认、收付款、库存、回退、审批或数据库接口。
涉及纸单、Excel 或订单草稿时，再完整阅读 docs/17-AGENT_ORDER_DRAFT_RUNBOOK.md。
```

## 十一、接入完成标准

接入完成必须同时满足：

1. Agent 能发现 14 个 `blade_*` 工具
2. 第一次只读调用会显示本机 Key 选择和授权窗口
3. 用户拒绝后不发出业务请求
4. 授权后返回 `code=200`，且响应中没有 Key 原文
5. scope 不足时请求被拒绝，不会改用其他鉴权方式
6. 生产地址通过有效 TLS 证书访问，没有关闭证书校验
7. 写操作只使用文档列出的窄范围接口

接口字段、参数和响应细节以 [外部 Agent 接入参考](./11-AGENT_ACCESS_GUIDE.md) 和 [API 接口规范](./reference/API_SPEC.md) 为准。本机 Key 生命周期和安全边界以 [本机 Agent Key 管理器与授权代理](./16-AGENT_LOCAL_KEY_MANAGER.md) 为准。

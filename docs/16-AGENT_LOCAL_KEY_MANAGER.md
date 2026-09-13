# 本机 Agent Key 管理器与授权代理

> 本文档定义 Mac 上 Agent Key 的录入、保存、到期提醒、选择与授权方式。
> API 鉴权和接口定义见 [11-AGENT_ACCESS_GUIDE.md](./11-AGENT_ACCESS_GUIDE.md)；纸单草稿字段与执行步骤见 [17-AGENT_ORDER_DRAFT_RUNBOOK.md](./17-AGENT_ORDER_DRAFT_RUNBOOK.md)。

---

## 一、目标与结论

用户不需要在终端中设置环境变量，也不把完整 Agent Key 交给 DeepSeek、ZCode、Codex 或其他模型。统一使用本机工具：

```text
BladeProject 系统管理签发 Key
              |
              | 用户复制一次完整 Key
              v
Blade Agent Key Manager.app
  ├─ 完整 Key → macOS 钥匙串
  └─ 名称/Agent/前缀/地址/scope/到期日 → 本机元数据
              |
              | Agent 通过本机 MCP/调用工具请求
              v
选择 Key + 用户授权弹窗
              |
              | 本机工具注入 X-Agent-Key
              v
BladeProject /api/agent/*
              |
              └─ 只把 API 结果返回 Agent，不返回 Key 原文
```

当前工具源码位于 `tools/blade-agent-key-manager/`，应用构建产物名称为 `Blade Agent Key Manager.app`。

## 二、用户操作流程

### 2.1 系统端签发

1. 登录 BladeProject，进入“系统管理 → Agent Key”。
2. 每个 Agent 单独签发一把 Key，不共用。名称建议写成“Agent + 环境 + 用途”，例如“DeepSeek 生产纸单录入”。
3. 纸单录入只选 `catalog:read`、`orders:write`；经营分析另签 `analytics:read`、`products:read`、`orders:read`；只有确实需要建商品或客户的 Agent 才增加 `products:create`、`customers:create`。`customers:read` 会暴露电话、地址和备注，应单独签发并标记用途，不要为了方便把所有 scope 放在一把 Key 上。
4. 选择有效天数并创建。完整 Key 只显示一次，立即复制。

### 2.2 Mac 端录入

1. 双击桌面的 `Blade Agent Key Manager.app`。
2. 点击“录入 Key”。
3. 填写 Key 名称、使用 Agent、API 地址和到期日期，选择实际 scope。
4. 点击“从剪贴板粘贴”，再点击“保存到钥匙串”。
5. 保存成功后，如果剪贴板仍是刚录入的完整 Key，应用会清空剪贴板。

完整 Key 只进入 macOS 钥匙串。普通元数据文件不包含完整 Key，位置为：

```text
~/Library/Application Support/Blade Agent Key Manager/keys-v1.json
```

列表展示 Key 名称、公开前缀、预定使用它的 Agent、API 环境地址、scope、到期日期、剩余天数和到期状态。

本机到期日是提醒信息，服务器上的 `expires_time/status` 仍是最终鉴权真相。录入时应与系统签发结果保持一致。v1 不使用 Owner JWT 自动同步服务器状态。

### 2.3 轮换、停用和删除

- 服务器轮换 Key 后，旧 Key 会立即失效；在本机管理器中删除旧记录，再录入新 Key。
- “删除本机 Key”只删除本机钥匙串和元数据，不会停用服务器 Key。
- 要彻底撤销访问，必须回到 BladeProject“系统管理 → Agent Key”停用该 Key。
- Key 到期或收到 401 时，Agent 不得自动改用账号密码、JWT 或另一把 Key，应停止并提示用户轮换。

## 三、其他 Agent 如何使用

应用首次启动后会把无密钥调用工具安装到：

```text
~/Library/Application Support/Blade Agent Key Manager/bin/blade-agent-request
```

在应用详情页点击“复制 Agent 接入说明”，即可得到当前 Mac 的完整工具路径和 MCP 启动参数；复制内容不含 Key。

### 3.1 MCP 模式（推荐）

支持本机 stdio MCP 的 Agent 使用如下逻辑配置：

```json
{
  "mcpServers": {
    "blade-project": {
      "command": "/Users/当前用户名/Library/Application Support/Blade Agent Key Manager/bin/blade-agent-request",
      "args": ["--mcp", "--agent", "DeepSeek"]
    }
  }
}
```

`--agent` 应固定写在本机 Agent 配置中，例如 `DeepSeek`、`ZCode` 或 `Codex`，不能让模型在每次调用时随意改名。MCP 暴露：

| 工具 | scope | 用途 |
|------|-------|------|
| `blade_catalog_search` | `catalog:read` | 查询商品/SKU 候选 |
| `blade_products_list` | `products:read` | 分页查询脱敏商品主档 |
| `blade_product_get` | `products:read` | 查询单个商品和 SKU |
| `blade_product_options` | `products:read` | 查询新增商品可用分类/颜色/尺码 |
| `blade_product_create` | `products:create` | 新增商品，不覆盖旧商品或库存 |
| `blade_orders_list` | `orders:read` | 分页查询脱敏正式订单 |
| `blade_order_get` | `orders:read` | 查询单个订单和商品明细 |
| `blade_customers_list` | `customers:read` | 分页查询客户敏感资料 |
| `blade_customer_get` | `customers:read` | 查询单个客户敏感资料 |
| `blade_customer_create` | `customers:create` | 新增客户，重复电话不覆盖 |
| `blade_order_draft_source_upload` | `orders:write` | 上传 JPG/PNG/WEBP 纸单原图并返回 fileId |
| `blade_order_drafts_create` | `orders:write` | 批量创建订单草稿 |
| `blade_style_trends` | `analytics:read` | 查询款式趋势 |
| `blade_sku_mix` | `analytics:read` | 查询颜色尺码结构 |

第一次调用或授权失效后，Mac 会先弹出 Key 选择窗口，显示声明的 Agent、HTTP 方法、接口、所需 scope 和符合条件的 Key；随后由独立的系统确认窗口提供“拒绝”“允许一次”和“允许 1 小时”。授权 UI 与持有 Key、执行网络请求的主进程相互隔离，系统窗口只接收非敏感的 Key 名称、Agent 名称、剩余天数和请求说明。

1 小时授权只保存在当前 MCP 进程内；Agent 退出后立即消失，不写入磁盘。完整 Key 始终不会通过 MCP 返回给模型。

### 3.2 一次性命令模式

拥有本机命令工具能力、但不支持 MCP 的 Agent，可以调用：

```text
blade-agent-request --agent <Agent名称> --method GET --path </api/agent/...>
blade-agent-request --agent <Agent名称> --method POST --path </api/agent/order-drafts/source-files> --file <本机图片绝对路径>
blade-agent-request --agent <Agent名称> --method POST --path </api/agent/order-drafts/batch> --body-file <JSON文件>
```

每个独立进程都重新请求用户授权。标准输出只有 API 响应，错误写入标准错误，Key 不进入参数、环境变量或输出。

### 3.3 Web 聊天的限制

纯网页聊天无法直接读取 Mac 钥匙串，也无法自动使用本机调用工具。要在 DeepSeek 等产品中使用本方案，必须满足至少一种条件：

1. 对应桌面 Agent 支持本机 MCP；
2. 对应编码 Agent 有权执行本机调用工具；
3. 后续部署一个经认证的本机桥接插件。

不得把生产 Key 粘贴进网页聊天来绕过限制。

## 四、本机授权代理的安全边界

当前 v1 同时执行以下限制：

1. 只允许保存 `https://` 远程地址；`http://` 仅允许 localhost。
2. 只允许访问明确白名单中的 `/api/agent/...` 接口。
3. 根据接口自动判断所需 scope，只显示符合条件且未过期的 Key。
4. 只允许 GET 和已登记的受控 POST；不提供任意 URL、任意 Header 或通用数据库工具。
5. 跨协议、主机或端口重定向不会继续携带 Key。
6. 请求体上限 10 MB。
7. Key 不写入 Git、项目文档、Excel、提示词、截图、日志或环境变量。
8. 元数据目录/文件权限固定为 `700/600`，且代理每次发请求前重新校验保存的 API 地址，不能只依赖录入时校验。
9. 商品、订单、客户查询只能分页；商品新增只接受既有字典编码，客户新增遇到重复电话不得覆盖。本机白名单不存在商品/客户修改删除、库存、收款或正式订单动作。

当前已知边界：`--agent` 是本机配置声明，v1 尚未对调用进程做代码签名或配对证明。因此只应给用户主动安装并信任的本机 Agent 授权。进程身份签名、持久授权中心和服务器状态同步属于后续增强，不影响当前“Key 不交给模型”的主要安全目标。

## 五、Agent 必读指令

给其他 Agent 的任务描述至少包含：

```text
开始前完整阅读：
1. docs/11-AGENT_ACCESS_GUIDE.md
2. docs/16-AGENT_LOCAL_KEY_MANAGER.md
3. docs/17-AGENT_ORDER_DRAFT_RUNBOOK.md（涉及纸单/Excel/订单草稿时）

不得索要、读取、输出或保存 Agent Key 原文；不得使用用户 JWT、账号密码、数据库或通用 CRUD API 替代 Agent Gateway。只通过 Blade Agent 本机 MCP/调用工具执行。纸单数量、销售价、金额和总额优先；商品主档价格仅作参考。只创建草稿，不确认订单、不收款、不改库存。
```

## 六、构建与验证

开发者在工具目录执行：

```bash
swift test
./scripts/build-macos-app.sh
```

也可指定桌面为输出目录：

```bash
./scripts/build-macos-app.sh --output-dir "$HOME/Desktop"
```

验收至少覆盖：

1. 应用可双击启动、录入和删除 Key；
2. 完整 Key 不出现在 `keys-v1.json`；
3. 剩余天数和到期状态正确；
4. scope 不足或过期 Key 不出现在选择列表；
5. 用户拒绝后不发出 API 请求；
6. Agent 只能得到 API 响应，不能得到 Key；
7. MCP 初始化、工具列表和五个工具契约可被客户端识别；
8. 非白名单路径、远程 HTTP 和跨主机重定向被拒绝。

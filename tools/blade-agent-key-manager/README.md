# Blade Agent Key Manager

BladeProject 的本机 macOS Agent Key 管理与授权工具。

第一次为 Codex、ZCode、DeepSeek 或其他 Agent 配置连接时，先阅读 [让外部 Agent 连接 BladeProject](../../docs/19-AGENT_CONNECTION_PLAYBOOK.md)。本文只说明本机工具的构建和运行方式。

## 能力

- 通过桌面应用录入完整 Agent Key，不要求用户操作终端。
- 完整 Key 只存 macOS 钥匙串；普通 JSON 元数据只包含名称、Agent、前缀、地址、scope 和有效期。
- 录入时通过 `/api/agent/capabilities` 自动校验并同步服务器真实 scope 和到期时间，不再手工重复选择。
- 已保存 Key 支持“同步服务器权限”，并显示剩余天数、即将到期和已过期状态。
- 随应用安装 `blade-agent-request` 本机调用工具。
- 其他 Agent 调用 API 前弹出选择与授权窗口，API 响应返回给 Agent，但 Key 原文不返回。
- 提供 MCP stdio 模式和一次性命令模式；只允许当前白名单 Agent API。

## 构建

```bash
./scripts/build-macos-app.sh
```

构建产物位于：

```text
dist/Blade Agent Key Manager.app
```

如需直接生成到桌面：

```bash
./scripts/build-macos-app.sh --output-dir "$HOME/Desktop"
```

首次打开应用后，它会把调用工具安装到：

```text
~/Library/Application Support/Blade Agent Key Manager/bin/blade-agent-request
```

## MCP 启动方式

把调用工具作为 stdio MCP server 配置给支持本机 MCP 的 Agent：

```text
blade-agent-request --mcp --agent DeepSeek
```

当前这台 Mac 的已安装绝对路径是：

```text
/Users/chenjiarun/Library/Application Support/Blade Agent Key Manager/bin/blade-agent-request
```

MCP 客户端的 `command` 应填写绝对路径，`args` 固定声明当前 Agent 名称。配置中不要保存 Agent Key、账号密码或 JWT。

MCP 暴露以下受控工具：

- `blade_catalog_search`
- `blade_products_list`
- `blade_product_get`
- `blade_product_options`
- `blade_product_create`
- `blade_orders_list`
- `blade_order_get`
- `blade_customers_list`
- `blade_customer_get`
- `blade_customer_create`
- `blade_order_draft_source_upload`
- `blade_order_drafts_create`
- `blade_style_trends`
- `blade_sku_mix`

调用时用户必须从弹窗选择符合 scope 的 Key 并授权。可选择对同一 Agent、同一 scope 记住 1 小时；授权只保存在当前 MCP 进程内，退出 Agent 后自动失效。

纸单草稿的标准顺序是：Agent 对每张 JPG/PNG/WEBP 调用 `blade_order_draft_source_upload`，收集返回的 `fileId`，再把同一订单的 fileId 按页序写入 `blade_order_drafts_create` 的 `sourceFileIds`（最多 10 张）。图片上传与草稿创建都会要求 `agent:orders:write` 授权；Key 原文不会返回给 Agent。

## 安全边界

- Agent 名称由本机 MCP 配置声明，v1 尚未对调用进程做密码学身份校验。
- 生产环境只允许 HTTPS；HTTP 只允许 localhost。
- 请求只能访问明确白名单中的 `/api/agent/...` 接口。
- 每次实际请求前重新校验保存的 API 地址，防止元数据被误改后把 Key 发往非 HTTPS 主机。
- 跨主机重定向不会继续携带 Key。
- 本机 scope 未同步与服务器实际返回 403 使用不同提示，避免把本地元数据偏差误判为生产权限失败。
- 本机删除不等于服务器停用；真正撤销访问必须在 BladeProject 系统管理中停用 Key。
- Web 聊天页面本身不能访问本机钥匙串；必须使用支持本机 MCP/命令工具的桌面 Agent。

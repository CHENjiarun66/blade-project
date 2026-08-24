# ROM/SOW：WhatsApp 混合 Agent 分析与客户推荐 v1

日期：2026-08-24  
分支：`feature/whatsapp-local-archive`  
任务：`BE-571`、`BE-573`～`BE-576`、`BA-1102`

## 一、目标

把 Mac 已标准化的 WhatsApp 事实与 NAS 上的客户、订单和商品事实结合，由可替换 Analysis Agent 自动生成客户摘要、商品偏好、意向阶段、风险和跟进建议。用户只在 ERP 查看和处理建议，不运行脚本、不让 Agent 自动发消息。

## 二、锁定数据流

`WhatsApp Mac 只读源 → Mac Collector 确定性预处理 → Blade/NAS 事实层 → 分析任务队列 → NAS 本地或云端 Worker → 结构化画像/推荐 → ERP 人工处理`

- 原始 SQLite、WAL、完整联系人和媒体原件不发送到云端模型。
- Mac Engine 只做确定性工作；语义分析集中在可审计的 Worker。
- ERP 保存业务事实、任务、结果和人工处理状态，不保存第三方模型 API Key。

## 三、数据最小化

Agent 单任务只允许：稳定客户别名、最近 90 天且最多 200 条消息、方向/时间/脱敏正文，以及必要的订单日期、状态、金额区间和商品名称/颜色/尺码汇总。

必须移除：客户姓名、完整电话、邮箱、URL、地址、CRM 私密备注、原始 JID、本机路径、媒体文件、Collector/Agent Key。正则无法可靠识别的自由文本地址是剩余风险，云端 Worker 启用前需由租户明确允许；NAS 本地 Worker 不受此出站限制。

## 四、V45 数据模型

### `wa_analysis_job`

- 租户/客户/联系人、触发批次、上下文版本 SHA-256、状态、优先级、尝试次数、可领取时间、租约、领取 Agent Key、错误码和时间戳。
- 唯一键 `(tenant_id, customer_id, context_version_hash)`；相同上下文不得重复排队。
- 状态：`PENDING/CLAIMED/SUCCEEDED/FAILED/CANCELLED`；租约过期可以重领，超过最大次数转 `FAILED`。

### `wa_customer_analysis`

- 每次成功分析 append-only 保存摘要、偏好 JSON、意向阶段、情绪、流失风险、建议时间、建议动作、置信度、证据消息 ID、provider/model/prompt version 和分析时间。
- 唯一键 `(tenant_id, analysis_job_id)`；结果不能跨任务覆盖。

### `wa_followup_recommendation`

- 保存当前用户工作流：`PENDING/ADOPTED/DISMISSED/COMPLETED`、处理人、处理时间和备注。
- 唯一键 `(tenant_id, analysis_id)`；新分析产生新建议，历史结果可追溯。

## 五、Agent API

- `POST /api/agent/whatsapp/analysis-jobs:claim`：领取任务和最小化上下文。
- `POST /api/agent/whatsapp/analysis-jobs/{id}:complete`：提交固定结构结果。
- `POST /api/agent/whatsapp/analysis-jobs/{id}:fail`：提交脱敏错误码并按策略重试。
- 全部要求 `agent:whatsapp:analyze`，复用 Agent Key 租户隔离与调用审计。
- 响应不得返回姓名、电话、JID、地址、媒体 URL 或数据库主键以外的源标识。

## 六、排队规则

- CRM 绑定由用户确认后立即排队。
- 成功导入批次中出现该客户的新逻辑消息后排队；完整重扫但无新消息不排队。
- 上下文版本由客户、最后消息时间/ID和ERP订单事实更新时间生成；相同版本幂等。
- 已领取任务租约 10 分钟；失败采用退避重试，最多 3 次。

## 七、结果验证

- `evidenceMessageIds` 必须全部属于当前任务上下文和同一租户/客户。
- `confidence` 范围 0～1；建议时间采用 UTC；枚举值白名单校验。
- Agent 输出不直接写 CRM 人工标签、订单、库存或 WhatsApp；只新增分析和推荐。
- ERP 用户可以采纳、忽略、完成；所有动作记录用户与时间。

## 八、ERP 工作台

- 客户卡片显示别名对应的 ERP 客户名、最近沟通、摘要、偏好、意向、风险、建议时间、置信度和分析时间。
- 支持状态筛选、逾期优先、查看证据摘录、采纳、忽略和完成。
- 证据正文只在 ERP JWT 权限下按需显示，Agent 列表默认不回传全量正文。

## 九、测试门禁

- 相同上下文只生成一个任务；新消息生成新版本。
- 租约到期可重领，未到期不能被第二 Agent 领取；第三次失败进入终态。
- 跨租户 customer/message/job/result 一律拒绝。
- 电话、邮箱、URL和JID不出现在 Agent context、日志和错误信息。
- 超过 90 天或 200 条消息不返回；证据 ID 不属于 context 时拒绝完成。
- 同一 complete 请求幂等；推荐人工状态不能被后续 Agent 静默覆盖。
- Flyway V1→V46、后端定向/全量测试、前端构建和合成端到端通过。

## 十、明确不做

- 自动发送 WhatsApp、自动营销、自动创建订单或客户。
- 把原始 SQLite、完整电话、地址、媒体文件或全量历史发送到云端。
- 在 migration 中预置模型密钥或真实聊天。
- 本轮生产/NAS 发布；部署和模型凭证按单独发布任务执行。

## 十一、ROM

数据模型/队列 1～2 人日；Agent context/API 2～4 人日；结果/工作台 2～4 人日；Worker 接入与安全回归 2～4 人日。总计约 7～14 人日，生产签名、模型费用和历史全量回算不包含在内。

## 十二、实施结果（2026-08-24）

- V45 已实现分析任务、append-only 客户分析和人工跟进推荐三张表；V46 保存领取时的上下文消息 ID 快照，避免分析期间新消息改变旧任务的证据边界；绑定确认和成功新消息批次会按上下文版本幂等排队。
- Agent Gateway 已实现独立 `whatsapp:analyze` Worker Key、10 分钟租约、最多 3 次重试、90 天/200 条上下文、正文脱敏、订单商品汇总、证据归属校验和 complete 幂等。
- 独立 Collector 项目新增 OpenAI-compatible Analysis Worker，可部署到 NAS 并切换本地或云端模型；ERP 不保存模型密钥，业务用户无需运行终端。
- ERP 工作台已支持摘要、偏好、意向、情绪、流失风险、建议时间、置信度、证据摘录，以及采纳/忽略/完成。
- 安全验证修复了认证前租户未知时 Agent Key 被 tenant=1 回落过滤的问题；认证查询仅按全局唯一 prefix 绕过租户拦截，成功后立即绑定 Key 自身 tenant，后续数据和审计继续强制租户条件。
- 验证：Flyway 空库 V1→V46 共 48 个 migration；后端 390 项测试、Collector/Worker 11 项测试和前端生产构建全部通过；合成端到端验证脱敏内容不含电话/邮箱/URL/JID、重复 complete 幂等、非法证据返回 400、失败回到重试队列、租户 2 Worker 只看到租户 2 空队列且审计 tenant=2。
- 未执行生产/NAS 发布，未配置真实模型密钥，未读取或上传真实聊天；部署继续作为独立发布动作。

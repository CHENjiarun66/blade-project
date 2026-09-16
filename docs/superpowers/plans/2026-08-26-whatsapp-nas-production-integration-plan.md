# WhatsApp Mac 采集器接入 NAS 生产 ERP 方案（后续实施）

日期：2026-08-26  
状态：已记录，暂缓实施  
当前分支：`feature/whatsapp-local-archive`

## 一、结论

生产环境采用“Mac 边缘采集 + NAS 集中事实与分析”的单向推送架构：Mac 上的 Blade WhatsApp Assistant 只读 WhatsApp 数据库，在本地完成一致性快照、标准化、去重、哈希和媒体完整性检查，再由 Mac 主动通过受信任 HTTPS 上传到 NAS 上的 Blade ERP。NAS 不反向访问 Mac，不挂载 WhatsApp 目录，也不开放 MySQL 给采集器。

```text
WhatsApp Business Mac App
        ↓ 只读快照
Blade WhatsApp Assistant
        ↓ 本地解析 / 幂等 / 完整性检查
        ↓ WireGuard 内受信任 HTTPS（主动推送）
NAS Blade ERP
        ├─ MySQL：联系人、会话、消息、绑定、分析结果
        ├─ /volume2/blade/uploads：图片、视频、音频、文档
        └─ Analysis Worker：结合 ERP 订单和商品生成建议
```

## 二、网络与安全边界

1. 优先使用现有 WireGuard 私有网络；Mac 只需能主动访问 NAS ERP，NAS 不需要访问 Mac。
2. 生产 Collector URL 必须使用固定域名和受信任 HTTPS 证书。不得依赖浏览器可忽略的 NAS 自签名 IP 证书。
3. 如暂时不开放公网，采用“WireGuard + 内部域名 + 受信任私有 CA”，并在 Mac 首次安装根证书；后续业务用户无需操作。
4. `/api/internal/whatsapp/**` 只接受独立 Collector Key；生产 Key 与本机测试 Key、管理员密码、Agent Worker Key全部分离。
5. Collector Key 保存在 macOS 钥匙串；日志不得记录完整电话、正文、JID、媒体内容或密钥。
6. 不开放 NAS MySQL、Redis、uploads 共享目录给 Mac；所有数据和媒体只通过 ERP API 写入。
7. NAS 的 MySQL 与 `/volume2/blade/uploads` 必须成对备份；WhatsApp 媒体不能只备份数据库元数据。

## 三、数据与控制链路

### 3.1 自动同步

- Mac App 登录自动启动。
- 常规任务使用增量扫描，只上传新增或发生变化的联系人、消息和媒体。
- 每天低峰期执行一次账号全盘一致性校验，防止旧消息媒体后下载但未被增量水位发现。
- 重复批次、逻辑消息和媒体使用现有自然键与哈希幂等写入，不产生重复记录。

### 3.2 ERP 定向重扫

- 用户在 ERP 客户详情页点击“仅扫描此客户”。
- NAS 创建 `CONTACT` 范围扫描任务。
- Mac App 主动轮询并领取任务，按真实号码覆盖该客户的 phone JID/LID 会话。
- 只补传该客户尚未导入的媒体，并仅恢复该客户的问题记录。

### 3.3 离线与恢复

- Mac 不在局域网、WireGuard 断开或 NAS 暂停时，不影响 WhatsApp 使用。
- 待上传批次和媒体保留在 Git 外本地队列，网络恢复后从失败位置继续。
- ERP 显示采集器最后心跳、最近成功同步、待上传数量和脱敏错误摘要。

## 四、生产切换规则

1. 生产/NAS 只部署经 release 验收后合入 `master` 的版本，不直接部署 feature 分支。
2. 发布前备份 NAS `blade_project_prod` 和 `/volume2/blade/uploads`；日常发布只更新 `blade-backend`、`blade-web`，不重建 MySQL/Redis。
3. WhatsApp 表由 Flyway 从当前生产版本累计迁移到最新版本；不得复制本机测试数据库中的 `wa_*` 行到 NAS。
4. 在 NAS 生产 ERP 新建独立 Collector Key，并绑定正确租户和 WhatsApp 账号。
5. Mac Assistant 当前一次只连接一个 ERP 地址；正式切换后自动同步只写 NAS 生产环境，本机测试环境停止自动接收真实聊天。
6. 首次验收先选择一个已核对客户做定向同步，确认文字、图片、视频、音频、缺失媒体和打开聊天均正确后，再启动账号全量初始导入。
7. 初次导入后在生产环境重新确认 CRM 绑定；不得复用测试环境 customerId 或绑定记录。

## 五、上线前必须补强

- 真正的增量扫描与每日全盘校验调度。
- 可恢复的本地上传 Outbox；媒体失败后从断点/单文件继续，而不是重扫完整账号。
- 采集器心跳、离线、积压和最近错误的 ERP 状态页面。
- 大媒体分批上传、超时和退避重试；Nginx/后端请求体与超时保持一致。
- 本地快照和失败批次的自动保留/清理策略。
- 生产 Key 吊销、轮换和丢失 Mac 的应急流程。
- NAS MySQL/uploads 备份与恢复演练。

## 六、建议运行频率

| 任务 | 建议频率 |
|---|---:|
| Mac 增量同步 | 每 30 分钟 |
| ERP 扫描任务领取 | 每 15 秒 |
| 客户定向重扫 | 用户点击后立即领取 |
| 账号全盘一致性校验 | 每天低峰期一次 |
| 失败上传重试 | 网络恢复后自动退避重试 |
| 本地旧快照清理 | 每天一次，保留期限上线前确认 |

## 七、明确暂缓

本方案当前只记录，不执行 NAS 发布、不创建生产密钥、不修改生产数据库、不上传真实聊天到 NAS。当前优先完成 WhatsApp 与 ERP 客户详情页的整合；生产接入需另行建立发布任务并按 `docs/13-NAS_PRODUCTION_OPS.md` 执行。

# 代码评估与后续修复验收

> 代码基线 `132c9e6`，2026-11-02。以下缺陷/优化尚未在本轮修改业务代码；对应待办 `BE-STAB-*`。优先级按现有订单、库存稳定性排序。

## 1. P1：真实 JWT 操作者丢失或回退到管理员（BE-STAB-001）

`UserDetailsServiceImpl.java:62` 返回 `AuthenticatedUserDetails`，它继承 Spring Security User，而非业务实体 User。以下消费者只识别业务 User：

| 代码（相对 blade-backend/src/main/java/com/blade/） | 触发与结果 |
|---|---|
| `inventory/controller/InventoryController.java:108` | 库存出入库等请求的用户 4 变为 1；实际流水会使用错误 operatorId |
| `customer/service/impl/CustomerServiceImpl.java:756` | “我的客户”按创建人 1 过滤，新增/修改/删除审计使用 1 |
| `order/service/OrderPlaceholderSplitService.java:267` | 占位拆分操作者变为 null，丢失审计归属 |
| `whatsapp/controller/WhatsappAdminController.java:82` | 绑定和推荐决策的同类身份解析返回 null；静态确认，未做此接口运行探针 |

已用实际 `AuthenticatedUserDetails(userId=4, tenantId=1)` 运行三个隔离探针：库存预期 4 实际 1，客户预期 4 实际 1，拆分预期 4 实际 null；3 项失败、0 项运行错误，均为刻意断言正确身份后复现缺陷。测试不连接数据库，使用 JDK Proxy 捕获库存 service 参数。临时缺陷探针未放入正常测试树；源码与结果保存在本轮审计附件。

修复：抽出一致的当前用户访问器，支持实际认证 principal；无法解析时拒绝处理，删除默认管理员兜底；租户仍由受信上下文确定。验收：实际登录 JWT 的非 1 用户贯穿各入口；我的客户隔离准确；操作者正确；缺失身份返回稳定错误且无写入。

## 2. P1：库存写入口缺动作权限（BE-STAB-002）

`inventory/controller/InventoryController.java:55–92` 的入库、出库、调整、预留、释放没有 `@PreAuthorize`；`config/SecurityConfig.java` 对普通 API 仅要求认证，InventoryServiceImpl 也没有相应动作授权。已有租户过滤不等于有权修改该租户库存。

修复：为写入口及敏感读取定义/复用权限，与权限种子、角色和前端对齐，保留订单内部服务调用的受控路径。验收：用无库存权限的真实 JWT 直接请求五类接口均 403，库存和流水均无变化；有对应权限的账号正常；并发/库存事务回归通过。本轮未在生产执行越权写请求。

## 3. P1：生产 SQL 标准输出与敏感结果（BE-STAB-003）

`blade-backend/src/main/resources/application.yml:35` 使用 `StdOutImpl`，部署配置未提供明确关闭覆盖。认证/客户查询可能输出完整行，涉及密码散列、电话等；高流量查询也增加日志和 I/O。此项是配置与查询路径评估，没有复制生产敏感日志。

修复：生产关闭 SQL 结果标准输出，按需开启参数脱敏后的诊断日志，保留请求追踪与必要审计。验收：真实登录和客户查询后，生产等价配置的日志不含散列、Token、手机号完整值；异常仍可定位请求。

## 4. P2：异常处理丢失诊断并返回底层消息（BE-STAB-004）

`common/exception/GlobalExceptionHandler.java:81–82` 对 RuntimeException 只记录 message，并把 message 直接交给响应。遇到空消息或深层数据库异常时，前端提示无效，服务端缺少可追踪上下文。

修复：返回稳定错误码/安全提示和请求 ID；服务器保留经脱敏的异常堆栈及请求关联，不记录认证秘密；业务异常保留可理解的提示。验收：空 message、嵌套异常和已知业务异常各自得到可诊断且安全的结果。

## 5. P1：普通发布与历史迁移耦合（BE-STAB-005）

`deploy/nas/deploy_app_from_local.sh:17` 默认目标 V58，`:40` 要求 V42 迁移证据，`:183/187` 执行 OrderLegacyMigrator；当前生产已 V69。该脚本的原场景是历史订单重构首发，直接套用普通应用热修会误跑旧流程或被旧门禁阻塞。

修复：显式区分纯应用、schema 变更、业务数据迁移三种模式；迁移默认禁止，必须指定版本、预演、备份与回退证据。构建跳过测试不能代表回归通过。验收：dry-run 普通热修不调用历史迁移、不降级目标 schema；迁移模式缺批准证据时拒绝；发布前后清单与实际镜像一致。

## 6. P2：回归未覆盖真实认证链，自动化入口不足（BE-STAB-006）

仓库已有大量 Java 测试和部分 Playwright 用例，但根目录没有统一 CI workflow，两个前端 package 只有 dev/build/preview 脚本。本次真实 principal 契约仍能漏过原回归，说明只用业务 User 模拟身份不足。

修复：把真实登录 JWT、租户/档口/动作权限矩阵加入隔离测试；提供独立测试库的一键检查入口，再接 CI。验收：普通开发可重复执行，不接生产库；测试覆盖正确用户、无权拒绝、幂等及失败零副作用。无需为了文档变更反复运行整个业务库测试。

## 7. P2：大页面和大服务增加改动风险（BE-STAB-007）

`blade-admin/src/views/orders/detail.vue` 2298 行，`products/index.vue` 2229 行、`orders/drafts.vue` 1658 行；`OrderServiceImpl` 1201 行、`ProductServiceImpl` 1190 行（基线快照）。业务规则、表单和网络状态集中后，编辑入口与动作规则容易重复。

在前六项和主线验收后，按订单动作、金额展示、配货、商品素材等职责提取组件/服务；先保留外部行为并增加关键场景保护。验收以行为、类型检查和构建为准，不用“文件变短”代替质量证明。暂不做跨模块大重写。

## 实施边界

任务顺序与 [项目范围](./PROJECT_SCOPE.md) 一致。上述修复需独立代码提交、适当回归和受控发布。本轮仅登记发现、文档和任务，没有宣称七项问题已修复。

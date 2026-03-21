# 项目目录结构

> 记录 BladeProject 的完整目录结构和各部分职责。

---

## 一、整体结构

```
/Users/chenjiarun/Documents/
├── BladeProject/                 # 项目文档中心
│   ├── docs/                     # 核心文档
│   │   ├── 01-README.md         # 入口
│   │   ├── 02-PRD.md            # 产品需求文档
│   │   ├── 03-TASKS.md          # 开发任务清单
│   │   ├── 04-REQUISITION_LOG.md # 需求讨论
│   │   ├── 05-CHANGELOG.md      # 变更记录
│   │   ├── architecture/         # 架构文档
│   │   └── reference/            # 参考文档
│   │
│   ├── blade-mobile/             # 新移动端 PWA
│   │   ├── CLAUDE.md
│   │   └── src/
│   │
│   └── blade-backend/            # 新后端
│       ├── CLAUDE.md
│       └── src/
│
├── Blade/                        # 原项目（参考/迁移用）
│   ├── Saber/                    # Vue 3 管理后台
│   ├── SpringBlade/             # SpringBlade 后端（待废弃）
│   └── app/                      # 旧移动端（已废弃）
```

---

## 二、关键文件说明

### 移动端关键文件

| 文件 | 说明 |
|------|------|
| `packages/core/src/api/request.ts` | axios 请求拦截器，含 Token 自动注入 |
| `packages/core/src/auth/token.ts` | JWT Token 管理 |
| `apps/order/src/views/OrderList.vue` | 订单列表页（最先开发） |

### 后端关键文件

| 文件 | 说明 |
|------|------|
| `config/SecurityConfig.java` | Spring Security 过滤器链 |
| `config/MybatisPlusConfig.java` | MyBatis-Plus + TenantLineInnerInterceptor |
| `auth/service/JwtTokenProvider.java` | JWT 签发与验证 |
| `common/tenant/TenantContext.java` | 租户 ThreadLocal 上下文 |
| `common/result/R.java` | 统一响应封装 |

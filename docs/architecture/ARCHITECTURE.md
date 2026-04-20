# 系统架构文档

> 本文档记录 BladeProject 的完整系统架构，包括技术架构、模块设计、数据流、安全方案等。

---

## 一、系统概述

### 1.1 项目定位

BladeProject 是一套**服装批发订单管理系统**，服务于服装批发商家的日常运营管理，包含订单处理、库存管理、商品管理、客户管理等核心功能。

### 1.2 系统边界

```
┌─────────────────────────────────────────────────────────────┐
│                        BladeProject                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   用户层                                                     │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │
│   │  管理员 PC  │  │  员工手机   │  │  客户微信   │      │
│   │ blade-admin │  │blade-mobile │  │   (后续)    │      │
│   └─────────────┘  └─────────────┘  └─────────────┘      │
│          │                │                  │              │
│          └────────────────┼──────────────────┘              │
│                           ▼                                 │
│   ┌─────────────────────────────────────────────────┐      │
│   │                   API 网关层                      │      │
│   │              (Spring Security)                   │      │
│   └─────────────────────────────────────────────────┘      │
│                           │                                 │
│   ┌─────────────────────────────────────────────────┐      │
│   │                  业务服务层                      │      │
│   │  订单服务  │  库存服务  │  商品服务  │  客户    │      │
│   └─────────────────────────────────────────────────┘      │
│                           │                                 │
│   ┌───────────┐  ┌───────────┐  ┌───────────┐           │
│   │   MySQL   │  │   Redis   │  │   文件    │           │
│   │  (数据)   │  │  (缓存)   │  │  (OSS)   │           │
│   └───────────┘  └───────────┘  └───────────┘           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、技术架构

### 2.1 技术栈总览

| 层级 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **前端 PC** | Vue3 | 3.4+ | 框架 |
| | Vite | 5.x | 构建工具 |
| | TypeScript | 5.x | 类型系统 |
| | Element Plus | 2.5+ | PC 组件库 |
| | TailwindCSS | 4.x | 样式方案 |
| | Pinia | 2.x | 状态管理 |
| **前端移动** | Vue3 | 3.4+ | 框架 |
| | Vuetify | 3.5+ | 移动组件库 |
| | PWA | - | 离线能力 |
| **后端** | Spring Boot | 3.2.4 | 核心框架 |
| | Spring Security | 6.x | 安全认证 |
| | MyBatis-Plus | 3.5.7 | ORM |
| | JWT (jjwt) | 0.12.5 | Token |
| **数据库** | MySQL | 8.0 | 主数据库 |
| | Redis | 7.x | 缓存/会话/锁 |
| **工具** | Flyway | - | 数据库迁移 |
| | Swagger | 2.5.0 | API 文档 |

### 2.2 前端架构（blade-admin）

```
blade-admin/
├── src/
│   ├── api/                    # API 请求层
│   │   ├── client.ts          # Axios 实例（拦截器、代理）
│   │   ├── auth.ts           # 认证 API
│   │   ├── order.ts          # 订单 API
│   │   ├── inventory.ts      # 库存 API
│   │   ├── product.ts        # 商品 API
│   │   └── customer.ts       # 客户 API
│   │
│   ├── router/
│   │   └── index.ts          # 路由配置 + 路由守卫
│   │
│   ├── stores/
│   │   └── auth.ts           # Pinia 认证状态
│   │
│   ├── views/                 # 页面组件
│   │   ├── login/            # 登录页（独立）
│   │   ├── layout/           # 主布局（侧边栏 + 顶部）
│   │   ├── dashboard/        # 仪表盘
│   │   ├── orders/           # 订单管理
│   │   ├── inventory/        # 库存管理
│   │   ├── products/         # 商品管理
│   │   └── clients/          # 客户管理
│   │
│   ├── styles/                # 样式
│   │   ├── main.css          # 主样式
│   │   └── glassmorphism.css # 玻璃拟态效果
│   │
│   ├── App.vue
│   └── main.ts
│
├── package.json
└── vite.config.ts            # Vite 配置（含 API 代理）
```

**API 代理配置**：
```typescript
// vite.config.ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

### 2.3 前端架构（blade-mobile）

```
blade-mobile/
├── src/
│   ├── api/                    # API 请求层
│   │   ├── client.ts          # Axios 实例
│   │   ├── auth.ts
│   │   ├── order.ts
│   │   ├── inventory.ts
│   │   └── product.ts
│   │
│   ├── router/
│   │   └── index.ts
│   │
│   ├── stores/
│   │   └── auth.ts
│   │
│   ├── views/                 # 页面组件
│   │   ├── Layout.vue         # 主布局（AppBar + BottomNav）
│   │   ├── Login.vue
│   │   ├── dashboard/
│   │   ├── order/
│   │   ├── inventory/
│   │   └── product/
│   │
│   ├── types/                 # 类型定义（从 @blade/types 导入）
│   │
│   ├── plugins/
│   │   └── vuetify.ts         # Vuetify 配置
│   │
│   ├── App.vue
│   └── main.ts
│
├── package.json
└── vite.config.ts
```

### 2.4 后端架构

```
blade-backend/
├── src/main/java/com/blade/
│   ├── auth/                   # 认证模块
│   │   ├── controller/
│   │   │   └── LoginController.java
│   │   ├── service/
│   │   │   ├── AuthService.java
│   │   │   ├── JwtTokenProvider.java
│   │   │   └── UserDetailsServiceImpl.java
│   │   └── dto/
│   │       ├── LoginRequest.java
│   │       └── LoginResponse.java
│   │
│   ├── system/                 # 系统模块
│   │   └── user/
│   │       ├── entity/         # User, Role
│   │       ├── dto/            # UserVO, UserCreateDTO
│   │       ├── mapper/
│   │       ├── controller/
│   │       └── service/
│   │
│   ├── product/                # 商品模块
│   │   ├── entity/             # Product, ProductSku, ProductColor, ProductSize
│   │   ├── dto/
│   │   ├── mapper/
│   │   ├── controller/
│   │   └── service/
│   │
│   ├── order/                  # 订单模块
│   │   ├── entity/             # Order, OrderItem
│   │   ├── dto/
│   │   ├── mapper/
│   │   ├── controller/
│   │   └── service/
│   │
│   ├── inventory/              # 库存模块
│   │   ├── entity/             # Inventory, InventoryLog, Warehouse
│   │   ├── dto/
│   │   ├── mapper/
│   │   ├── controller/
│   │   └── service/
│   │
│   ├── customer/                # 客户模块
│   │   ├── entity/
│   │   ├── mapper/
│   │   ├── controller/
│   │   └── service/
│   │
│   ├── common/                  # 公共模块
│   │   ├── result/             # R.java, PageResult
│   │   ├── exception/          # BusinessException, GlobalExceptionHandler
│   │   └── tenant/             # TenantContext, TenantLineHandler
│   │
│   └── config/                  # 配置模块
│       ├── SecurityConfig.java
│       ├── MybatisPlusConfig.java
│       ├── CorsConfig.java
│       └── RedisConfig.java
│
├── src/main/resources/
│   ├── application.yml          # 主配置
│   └── db/migration/            # Flyway 迁移脚本
│       ├── V1__init_schema.sql
│       ├── V2__product_order.sql
│       └── ...
│
└── pom.xml
```

---

## 三、模块依赖关系

### 3.1 后端模块依赖

```
┌─────────────────────────────────────────────────────────────┐
│                     com.blade.common                         │
│              (Result / Exception / TenantContext)            │
└─────────────────────────────────────────────────────────────┘
                              ▲
         ┌────────────────────┼────────────────────┐
         │                    │                    │
    ┌────┴────┐        ┌────┴────┐        ┌────┴────┐
    │  auth   │        │ system  │        │customer  │
    │         │        │         │        │          │
    └────┬────┘        └────┬────┘        └────┬────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
               ┌────┴────┐      ┌────┴────┐
               │ product │      │  order  │
               │         │      │         │
               └────┬────┘      └────┬────┘
                    │                 │
                    │          ┌──────┴──────┐
                    │          │             │
                    │     ┌────┴────┐  ┌────┴────┐
                    │     │inventory│  │ customer │
                    │     │         │  │         │
                    │     └─────────┘  └─────────┘
                    │
               ┌────┴────┐
               │  system  │
               │  (user)  │
               └──────────┘
```

**依赖规则**：
- `common` 是基础模块，所有模块依赖它
- `auth` 依赖 `common`，被其他模块引用（认证）
- `system` 依赖 `common`（用户/角色管理）
- `product`、`order`、`inventory`、`customer` 横向平级
- `order` 依赖 `inventory`（库存联动）
- `customer` 独立，不依赖其他业务模块

### 3.2 前端模块依赖

```
┌─────────────────────────────────────────────────────────────┐
│                        api/client.ts                         │
│                   (Axios 实例 + 拦截器)                      │
└─────────────────────────────────────────────────────────────┘
                              ▲
         ┌────────────────────┼────────────────────┐
         │                    │                    │
    ┌────┴────┐        ┌────┴────┐        ┌────┴────┐
    │auth.api │        │order.api│        │inventory│
    │         │        │         │        │  .api   │
    └─────────┘        └─────────┘        └─────────┘
         │                   │                    │
         │                   │                    │
    ┌────┴────┐        ┌────┴────┐        ┌────┴────┐
    │  stores │        │  views  │        │  views  │
    │  auth   │        │ orders  │        │inventory│
    └─────────┘        └─────────┘        └─────────┘
```

---

## 四、数据流架构

### 4.1 订单创建数据流

```
┌─────────────────────────────────────────────────────────────┐
│                        订单创建数据流                           │
└─────────────────────────────────────────────────────────────┘

  前端                     后端                      数据库
   │                        │                         │
   │  POST /api/orders     │                         │
   │  ────────────────────►│                         │
   │                       │  1. 校验请求参数         │
   │                       │  2. 查询 SKU 价格         │
   │                       │  ─────────────────────►│
   │                       │◄──────────────────────│
   │                       │  3. 计算订单总额         │
   │                       │  4. 生成订单号           │
   │                       │  5. 保存订单             │
   │                       │  ─────────────────────►│ INSERT
   │                       │◄──────────────────────│
   │                       │  6. 保存订单明细         │
   │                       │  ─────────────────────►│ INSERT
   │                       │◄──────────────────────│
   │                       │  7. 返回订单信息         │
   │  ◄──────────────────│                         │
   │                       │                         │
```

### 4.2 订单付款数据流

```
┌─────────────────────────────────────────────────────────────┐
│                      订单付款数据流                            │
└─────────────────────────────────────────────────────────────┘

  前端                     后端                      数据库
   │                        │                         │
   │  PUT /api/orders/     │                         │
   │       {id}/confirm-payment                          │
   │  ────────────────────►│                         │
   │                       │  1. 获取分布式锁          │
   │                       │     (inventory:lock:{skuId})│
   │                       │  ─────────────────────►│
   │                       │◄──────────────────────│
   │                       │  2. 查询库存可用数量      │
   │                       │  ─────────────────────►│ SELECT
   │                       │◄──────────────────────│
   │                       │  3. 校验库存是否充足     │
   │                       │  4. 锁定库存 (reserved) │
   │                       │  ─────────────────────►│ UPDATE
   │                       │◄──────────────────────│
   │                       │  5. 记录库存变动日志     │
   │                       │  ─────────────────────►│ INSERT
   │                       │◄──────────────────────│
   │                       │  6. 更新订单状态         │
   │                       │  ─────────────────────►│ UPDATE
   │                       │◄──────────────────────│
   │                       │  7. 释放分布式锁         │
   │                       │  ─────────────────────►│
   │                       │◄──────────────────────│
   │                       │  8. 返回结果            │
   │  ◄──────────────────│                         │
```

---

## 五、安全架构

### 5.1 认证流程

```
┌─────────────────────────────────────────────────────────────┐
│                        JWT 认证流程                           │
└─────────────────────────────────────────────────────────────┘

  客户端                    后端                      Redis
   │                        │                         │
   │  POST /api/auth/login │                         │
   │  ────────────────────►│                         │
   │                       │  1. 查询租户             │
   │                       │  ─────────────────────►│
   │                       │◄──────────────────────│
   │                       │  2. 验证密码            │
   │                       │  3. 生成 JWT            │
   │                       │  4. 生成 Refresh Token  │
   │                       │  ─────────────────────►│ SET
   │                       │◄──────────────────────│
   │                       │  5. 返回 Token         │
   │  ◄──────────────────│                         │
   │                        │                         │
   │  (后续请求)             │                         │
   │  Authorization:        │                         │
   │  Bearer {token}       │                         │
   │  ────────────────────►│                         │
   │                       │  验证 Token             │
   │                       │  ─────────────────────►│ GET
   │                       │◄──────────────────────│
   │                       │  验证通过，处理请求      │
   │  ◄──────────────────│                         │
```

### 5.2 JWT Token 结构

```json
{
  "sub": "user_id",
  "tenantId": "tenant_code",
  "roles": ["ROLE_USER"],
  "exp": 1742572800,
  "iat": 1742486400
}
```

### 5.3 公开接口配置

```java
// SecurityConfig.java
public SecurityFilterChain filterChain(HttpSecurity http) {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/api/auth/login",
                "/api/auth/logout",
                "/api/auth/refresh",
                "/api/customers/**",
                "/swagger-ui/**",
                "/v3/api-docs/**"
            ).permitAll()
            .anyRequest().authenticated()
        )
        // ...
}
```

---

## 六、多租户架构

### 6.1 租户隔离策略

```
┌─────────────────────────────────────────────────────────────┐
│                      多租户数据隔离                           │
└─────────────────────────────────────────────────────────────┘

     Tenant A 数据                   Tenant B 数据
   ┌─────────────────┐           ┌─────────────────┐
   │ tenant_id = 1   │           │ tenant_id = 2   │
   │                 │           │                 │
   │ sale_order:     │           │ sale_order:     │
   │   - id: 1       │           │   - id: 100     │
   │   - tenant_id:1 │           │   - tenant_id:2 │
   │                 │           │                 │
   │ inventory:      │           │ inventory:      │
   │   - id: 10     │           │   - id: 200    │
   │   - tenant_id:1 │           │   - tenant_id:2 │
   │                 │           │                 │
   └─────────────────┘           └─────────────────┘

   ┌─────────────────────────────────────────────────┐
   │              TenantLineInnerInterceptor           │
   │                                                   │
   │  所有查询自动追加: WHERE tenant_id = ?            │
   │  所有新增自动填充: tenant_id = 当前租户            │
   │  所有更新自动追加: WHERE tenant_id = ?            │
   └─────────────────────────────────────────────────┘
```

### 6.2 超级管理员

- `tenant_id = 0` 为超级管理员
- 可访问所有租户数据
- TenantLineInnerInterceptor 特殊处理 `sys_tenant` 表不过滤

---

## 七、部署架构

### 7.1 开发环境

```
┌─────────────────────────────────────────────────────────────┐
│                      开发环境部署                            │
└─────────────────────────────────────────────────────────────┘

  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
  │ blade-admin  │    │blade-mobile  │    │blade-backend │
  │  localhost   │    │  localhost   │    │  localhost   │
  │   :5777     │    │   :5173     │    │   :8080     │
  └──────────────┘    └──────────────┘    └──────────────┘
         │                   │                   │
         │    Vite Proxy     │                   │
         │───────────────────┼───────────────────┤
                            │
                    ┌───────┴───────┐
                    │    MySQL 8    │
                    │   localhost   │
                    └───────────────┘
                            │
                    ┌───────┴───────┐
                    │    Redis 7   │
                    │   localhost   │
                    └───────────────┘
```

### 7.2 生产环境（规划）

```
┌─────────────────────────────────────────────────────────────┐
│                      生产环境部署（规划）                      │
└─────────────────────────────────────────────────────────────┘

                          ┌─────────────┐
                          │   Nginx     │
                          │  (反向代理)  │
                          └──────┬──────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
       ┌──────┴──────┐   ┌──────┴──────┐   ┌──────┴──────┐
       │ blade-admin  │   │blade-mobile  │   │blade-backend │
       │   (静态)     │   │   (PWA)     │   │   :8080     │
       │   :80       │   │    :80      │   │             │
       └─────────────┘   └─────────────┘   └──────┬──────┘
                                                   │
                                 ┌─────────────────┼─────────────────┐
                                 │                 │                 │
                          ┌──────┴──────┐   ┌──────┴──────┐
                          │   MySQL 8   │   │   Redis 7   │
                          │  (主从)     │   │  (集群)     │
                          └─────────────┘   └─────────────┘
```

---

## 八、核心决策

### 8.1 移动端：纯 Vue3 PWA

**决策**：采用 **Vue3 + Vite + TypeScript** 构建移动端 PWA，放弃 Flutter / UniApp / React Native。

**理由**：

| 维度 | 评分 | 说明 |
|------|------|------|
| AI 生成质量 | ⭐⭐⭐⭐⭐ | Vue3 + TS 是 Claude Code 最擅长的组合 |
| Bundle 大小 | ⭐⭐⭐⭐⭐ | 100-200KB，Flutter Web 在 1.5-2MB+ |
| 苹果 Safari PWA 兼容性 | ⭐⭐⭐⭐⭐ | 原生支持 |
| iPad 适配 | ⭐⭐⭐⭐ | Vuetify3 组件库已做好响应式适配 |
| 工具链成熟度 | ⭐⭐⭐⭐⭐ | Vite + Vue3 + TS 是最成熟组合 |

### 8.2 后端：Spring Boot 3 迁移

**决策**：将后端从 SpringBlade 微服务框架迁移到 **Spring Boot 3 单体架构**。

**理由**：

| 问题 | SpringBlade 现状 | Spring Boot 3 新方案 |
|------|----------------|---------------------|
| AI 开发友好度 | 低（封装框架，文档缺失） | 高（标准框架，Google 资料丰富） |
| 代码量 | ~28000 行 | ~5500 行（少 4/5） |
| 多租户实现 | 手动拼接，容易出错 | MyBatis-Plus 插件，配置即搞定 |
| 鉴权 | 自定义，非标准 | Spring Security OAuth2，标准实现 |

---

## 九、已废弃的方案

| 方案 | 废弃原因 |
|------|---------|
| Flutter Web | Bundle 太大（1.5-2MB+），PWA 缓存受限 |
| UniApp + Vue 2 | AI 生成质量差，条件编译是重灾区；且必须升级 Vue 3 |
| SpringBlade 微服务 | 对 AI 开发不友好，每次启动都有问题，文档缺失 |
| 微服务架构（Nacos/Seata） | 移动端 MVP 阶段不需要，单体足以支撑 |

---

## 十、相关文档

| 文档 | 说明 |
|------|------|
| [02-PRD.md](../02-PRD.md) | 产品需求文档（功能需求依据） |
| [03-TASKS.md](../03-TASKS.md) | 开发任务清单 |
| [05-CHANGELOG.md](../05-CHANGELOG.md) | 变更记录 |
| [reference/DECISIONS_LOG.md](../reference/DECISIONS_LOG.md) | 技术决策记录 |
| [reference/TROUBLESHOOTING.md](../reference/TROUBLESHOOTING.md) | 问题排查指南 |

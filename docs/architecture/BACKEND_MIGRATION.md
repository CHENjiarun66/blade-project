# 后端 Spring Boot 3 迁移方案

> 记录从 SpringBlade 微服务迁移到 Spring Boot 3 单体架构的完整方案。

---

## 一、迁移背景

### 1.1 为什么要迁移

| 问题 | SpringBlade 现状 | 新框架方案 |
|------|----------------|-----------|
| AI 开发友好度 | 低（封装框架，文档缺失） | 高（标准框架，资料丰富） |
| 代码量 | ~28000 行 | ~5500 行（少 4/5） |
| 多租户 | 手动拼接，容易出错 | MyBatis-Plus 插件，配置即搞定 |
| 鉴权 | 自定义，非标准 OAuth2 | Spring Security OAuth2，标准实现 |
| 启动问题 | 每次都有 Nacos/网关问题 | 无 Nacos，单体简单 |

### 1.2 迁移范围

| 模块 | 处理方式 |
|------|---------|
| blade-auth | 重写（Spring Security OAuth2） |
| blade-system（用户/角色/菜单/租户） | 重写（复用表结构） |
| blade-product | 重写（复用 MySQL 表结构） |
| blade-gateway | 不需要（Nacos 网关废弃） |
| Nacos 配置中心 | 不需要（单体应用） |

---

## 二、技术栈

```
Spring Boot 3.2+
├── Spring Security 6 (OAuth2 Authorization Server)
├── MyBatis-Plus 3.5+ (含 TenantLineInnerInterceptor)
├── JWT (jjwt 0.12+)
├── MySQL 8
├── Redis 7
└── Flyway (数据库版本管理)
```

---

## 三、多租户方案（关键）

### 3.1 为什么不用 SpringBlade 的方案

SpringBlade 的多租户是**手动的**，容易遗漏 `tenant_id` 条件，导致数据串租户。

### 3.2 MyBatis-Plus TenantLineInnerInterceptor

**配置即可，零代码改动**：

```yaml
mybatis-plus:
  tenant-line:
    enabled: true
    tenant-table: sys_user
    ignore-tables:
      - sys_dict
      - sys_param
```

---

## 四、认证方案

### 4.1 Spring Security OAuth2 Authorization Server

SpringBlade 的认证是**自定义实现**，非标准 OAuth2。

新方案采用 **Spring Security OAuth2 Authorization Server + JWT**：

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/oauth2/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(Customizer.withDefaults())
            .jwt(Customizer.withDefaults());
        return http.build();
    }
}
```

### 4.2 JWT Token 结构

```json
{
  "sub": "user@tenant.com",
  "tenant_id": 1,
  "user_id": 100,
  "roles": ["ROLE_USER", "ROLE_ORDER_ADMIN"],
  "exp": 1742572800
}
```

---

## 五、API 设计

### 5.1 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1742572800
}
```

### 5.2 核心接口

```
认证：
POST   /api/auth/login          # 账号密码登录
POST   /api/auth/logout         # 登出
POST   /api/auth/refresh        # 刷新 Token

订单：
GET    /api/orders              # 订单列表
POST   /api/orders              # 创建订单
GET    /api/orders/{id}        # 订单详情
PUT    /api/orders/{id}/status  # 更新状态

库存：
GET    /api/inventory            # 库存列表
POST   /api/inventory/in        # 入库
POST   /api/inventory/out       # 出库

看板：
GET    /api/dashboard/stats     # 统计概览
```

---

## 六、迁移步骤

### Phase 1: 骨架搭建（Day 1）
- 创建 blade-backend 项目
- 配置 pom.xml 依赖
- 搭通信安全 + JWT 认证
- 验证登录接口通

### Phase 2: 用户 + 鉴权 + 多租户（Day 2-3）
- 配置 MyBatis-Plus TenantLineInnerInterceptor
- 实现用户 CRUD + 角色权限
- 实现租户管理

### Phase 3: 业务模块迁移（Day 4-10）
- 订单模块开发
- 库存模块开发
- 看板模块开发

---

## 七、风险与注意事项

| 风险 | 应对 |
|------|------|
| 社交登录需要重新对接 | 复用 JustAuth，只需调整回调地址 |
| 两套系统并行期间数据一致性 | 约定接口规范，分阶段切换 |
| 管理后台 Saber 需要同步调整 | Saber 迁移接口到新后端 |

# Blade Backend

> BladeProject 后端服务，基于 Spring Boot 3.2 + Spring Security + MyBatis-Plus

---

## 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot 3.2 | 基础框架 |
| Spring Security 6 | 认证授权 |
| MyBatis-Plus 3.5 | ORM（含多租户插件） |
| JWT | Token 管理 |
| Flyway | 数据库版本管理 |
| Redis | 缓存/会话 |
| Swagger | API 文档 |

---

## 快速开始

### 1. 环境要求

- JDK 17+
- MySQL 8+
- Redis 7+
- Maven 3.8+

### 2. 创建数据库

```sql
CREATE DATABASE blade DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 修改配置

编辑 `src/main/resources/application.yml`，修改数据库和 Redis 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blade?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: your_password

  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password
```

### 4. 启动

```bash
# 开发环境启动
./mvnw spring-boot:run

# 或用 IDEA 直接运行 BladeApplication
```

### 5. 访问

- API 文档：http://localhost:8080/swagger-ui.html
- 默认账号：admin
- 默认密码：123456

---

## 项目结构

```
src/main/java/com/blade/
├── BladeApplication.java     # 启动类
├── config/                  # 配置类
│   ├── SecurityConfig.java  # Spring Security 配置
│   ├── MybatisPlusConfig.java # MyBatis-Plus + 多租户
│   ├── CorsConfig.java      # 跨域配置
│   └── RedisConfig.java     # Redis 配置
├── auth/                    # 认证模块
│   ├── controller/
│   ├── service/
│   └── dto/
├── system/                  # 系统模块
│   ├── user/
│   ├── role/
│   └── menu/
├── common/                  # 公共模块
│   ├── result/             # 统一响应
│   ├── exception/          # 异常处理
│   └── tenant/             # 多租户
```

---

## API 接口

详见：[API 接口规范](../docs/reference/API_SPEC.md)

---

## 多租户说明

本系统采用 MyBatis-Plus TenantLineInnerInterceptor 自动处理多租户：

- 所有业务表通过 `tenant_id` 字段隔离
- **禁止手动拼接 tenant_id**
- 需要放行的表（如字典表）在 `application.yml` 的 `ignore-tables` 中配置

---

## 开发规范

详见：[BladeProject 开发规范](../docs/CLAUDE.md)

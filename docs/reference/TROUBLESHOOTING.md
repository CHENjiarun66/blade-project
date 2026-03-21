# 问题排查指南

> 记录项目中已知问题及其解决方案，新 AI 可快速查阅。

---

## 一、常见问题

### 1.1 后端 CORS 问题

**症状**：前端请求后端报错 `Access-Control-Allow-Origin` missing

**排查**：
```java
// 检查 CorsConfig.java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

### 1.2 JWT Token 过期处理

**症状**：Token 未过期但接口返回 401

**排查**：检查 JwtTokenProvider 的校验逻辑，确认 Token 在有效期内。

### 1.3 MyBatis-Plus 多租户不生效

**症状**：查询结果未按 tenant_id 过滤

**排查**：
```yaml
mybatis-plus:
  tenant-line:
    enabled: true
    tenant-table: sys_user
    ignore-tables:
      - sys_dict
```

### 1.4 PWA 无法离线

**症状**：Service Worker 已注册，但离线后页面打不开

**排查**：检查 vite.config.ts PWA 配置中的 workbox 策略。

---

## 二、快速诊断清单

### 后端启动失败
```
[ ] 检查 Java 版本（需要 17+）
[ ] 检查 MySQL/Redis 是否运行
[ ] 检查 application.yml 配置
[ ] 查看启动日志具体报错
[ ] 检查端口是否被占用
```

### 前端 PWA 问题
```
[ ] 检查 vite.config.ts PWA 配置
[ ] 检查 manifest.json 是否正确
[ ] 检查 Service Worker 是否注册
[ ] 检查 HTTPS（生产环境）
```

### 接口 401/403
```
[ ] 检查 Token 是否携带
[ ] 检查 Token 是否过期
[ ] 检查用户权限
[ ] 检查接口路径是否在白名单
```

---

## 三、项目实际问题记录（2026-03-21）

### 3.1 Flyway 迁移未执行

**症状**：启动后数据库表未创建

**原因**：Flyway 配置正确，但首次启动时数据库为空，迁移脚本未自动执行

**解决**：
```bash
# 手动执行迁移脚本
mysql -u root -proot blade_project < src/main/resources/db/migration/V1__init_schema.sql
mysql -u root -proot blade_project < src/main/resources/db/migration/V2__product_order.sql
mysql -u root -proot blade_project < src/main/resources/db/migration/V3__product_module.sql
```

**预防**：确保数据库已创建，Flyway 会自动执行未执行的迁移

---

### 3.2 UserDetailsServiceImpl 角色加载 null

**症状**：登录时报错 `Cannot invoke "java.util.List.stream()" because the return value of "getRoles()" is null`

**原因**：User 实体中 roles 字段未填充，MyBatis 不会自动关联查询

**解决**：修改 UserDetailsServiceImpl，手动查询用户角色
```java
// 错误写法
return new User(user.getUsername(), user.getPassword(), ...,
    user.getRoles().stream()...);  // user.getRoles() 为 null

// 正确写法
List<Role> roles = roleMapper.selectByUserId(user.getId());
List<SimpleGrantedAuthority> authorities = roles.stream()
    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()))
    .collect(Collectors.toList());
return new User(user.getUsername(), user.getPassword(), ..., authorities);
```

---

### 3.3 sys_user_role 表缺少 tenant_id

**症状**：`Unknown column 'ur.tenant_id' in 'on clause'`

**原因**：TenantLineInnerInterceptor 会自动给所有表添加 tenant_id 条件，但 sys_user_role 表没有此字段

**解决**：
```sql
ALTER TABLE sys_user_role ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1 AFTER role_id;
```

---

### 3.4 admin 用户密码错误

**症状**：登录报错 `Bad credentials`

**原因**：V1__init_schema.sql 中的 BCrypt 密码哈希不正确

**解决**：
```java
// 生成新密码
// 123456 的正确 BCrypt 哈希
$2a$10$AoUAmyTu7vC0KUq5rizrMepG0s8fWWMzV027S/mNiQbiGuHc1F8la

UPDATE sys_user SET password='$2a$10$AoUAmyTu7vC0KUq5rizrMepG0s8fWWMzV027S/mNiQbiGuHc1F8la' WHERE username='admin';
```

---

### 3.5 product 表缺少 price 字段

**症状**：`Unknown column 'price' in 'field list'`

**原因**：V3__product_module.sql 中 product 表定义缺少 price 字段

**解决**：
```sql
ALTER TABLE product ADD COLUMN price decimal(10,2) DEFAULT 0 COMMENT '单价' AFTER image_url;
```

---

### 3.6 关联表缺少 tenant_id

**症状**：`Unknown column 'tenant_id' in 'field list'`

**原因**：product_color_rel、product_size_rel 等关联表缺少 tenant_id 字段

**解决**：
```sql
ALTER TABLE product_color_rel ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
ALTER TABLE product_size_rel ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
```

---

### 3.7 PUT 请求路径注解错误

**症状**：PUT 请求返回 `Request method 'PUT' is not supported`

**原因**：`@PutMapping` 没有路径参数时，不能用 `/{id}` 路径

**解决**：
```java
// 错误
@PutMapping("/{id}")

// 正确 - DTO 中包含 id
@PutMapping
public R<Void> update(@RequestBody @Valid ProductUpdateDTO dto) {
    productService.update(dto);
    return R.ok();
}
```

---

### 3.8 Spring Boot 需要 Java 17+

**症状**：`Fatal error compiling: 无效的标记: --release`

**原因**：Spring Boot 3.2+ 需要 Java 17+，系统只有 Java 8

**解决**：
```bash
# 使用 SDKMAN 安装 Java 17
source ~/.sdkman/bin/sdkman-init.sh
sdk install java 17.0.18-amzn
sdk default java 17.0.18-amzn
java -version  # 确认版本为 17.x
```

# Blade Backend

服装批发业务 API：Java 17、Spring Boot 3.2.4、Spring Security/JWT、MyBatis-Plus、MySQL 8、Redis 7、Flyway。精确依赖见 pom.xml，当前 schema 最高 V69。

启动与独立测试库配置统一见 [环境配置](../docs/00-SETUP.md)，在本目录执行 `mvn spring-boot:run`。本仓库没有 Maven wrapper，不使用旧文档的 ./mvnw。数据库连接由 BLADE_DB_URL/USERNAME/PASSWORD 配置；不要修改生产连接或运行测试种子到已有业务库。

认证 principal 是 AuthenticatedUserDetails；租户过滤白名单在 TenantLineHandler 代码，不在旧 ignore-tables YAML。缺少可信租户必须拒绝，不存在 tenant_id=0 自动绕过。实际身份与库存授权的待修复项见 [代码评估](../docs/CODE_REVIEW_2026-11-02.md)。

[开发规范](./CLAUDE.md)、[架构](../docs/architecture/ARCHITECTURE.md)、[API](../docs/reference/API_SPEC.md)、[schema 来源](../docs/architecture/SCHEMA_MIGRATIONS.md)、[当前主线](../docs/PROJECT_SCOPE.md)。账号来自具体环境配置，本文不再发布通用默认登录凭据。

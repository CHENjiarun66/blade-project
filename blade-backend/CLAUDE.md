# BladeProject 后端开发规范

> 本目录是 BladeProject 的后端开发目录。
> 新会话必须阅读上级目录的 `../CLAUDE.md`。
> 本文件是后端的补充规范。

---

## 一、技术栈

| 层级 | 技术 |
|------|------|
| 框架 | Spring Boot 3.2+ |
| 安全 | Spring Security 6 (OAuth2) |
| ORM | MyBatis-Plus 3.5+ |
| 数据库 | MySQL 8 |
| 缓存 | Redis 7 |
| 迁移 | Flyway |
| 构建 | Maven |

**禁止使用**：SpringBlade、Nacos、JPA

---

## 二、项目结构

```
BladeProject/
├── blade-backend/              # 后端项目
│   ├── src/main/java/com/blade/
│   │   ├── auth/              # 认证模块
│   │   ├── system/            # 系统模块（用户/角色/菜单/租户）
│   │   ├── product/           # 商品模块
│   │   ├── order/             # 订单模块
│   │   ├── inventory/          # 库存模块
│   │   ├── client/             # 客户模块
│   │   ├── dashboard/          # 看板模块
│   │   └── common/             # 公共模块
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/      # Flyway 迁移脚本
│   └── pom.xml
├── blade-mobile/              # 移动端项目
└── docs/                      # 文档中心
```

---

## 三、多租户规则（最核心）

### 3.1 唯一正确方式：TenantLineInnerInterceptor

**所有租户隔离通过 MyBatis-Plus 插件自动处理，禁止手动拼接 tenant_id。**

```yaml
# application.yml
mybatis-plus:
  tenant-line:
    enabled: true
    tenant-table: sys_user
    ignore-tables:
      - sys_dict
      - sys_param
```

### 3.2 禁止

- ❌ 禁止在代码中手动加 `.eq("tenant_id", xxx)`
- ❌ 禁止在 Service 层手动判断租户
- ❌ 禁止用 AOP 自己实现租户拦截

### 3.3 允许的情况

- 在 `ignore-tables` 中的表可以不加租户条件
- 超级管理员（tenant_id = 0）可以访问所有数据

---

## 四、代码规范

### 4.1 Controller 规范

```java
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public R<PageResult<OrderVO>> list(OrderPageDTO dto) {
        return R.ok(orderService.pageList(dto));
    }

    @GetMapping("/{id}")
    public R<OrderVO> getById(@PathVariable Long id) {
        return R.ok(orderService.getById(id));
    }

    @PostMapping
    public R<Long> create(@RequestBody @Valid OrderCreateDTO dto) {
        return R.ok(orderService.create(dto));
    }
}
```

### 4.2 必须

- ✅ 所有 Controller 返回 `R<T>`
- ✅ 所有 Entity 加 `@TableName`
- ✅ 所有接口加 Swagger 注解 `@Operation`
- ✅ 所有 DTO 加 `@Valid` + 校验注解
- ✅ 所有 Service 方法加事务 `@Transactional`

### 4.3 禁止

- ❌ 禁止返回 `Map` / `HashMap`，必须定义 VO
- ❌ 禁止省略 `@Valid`
- ❌ 禁止不加 `@Transactional`

---

## 五、API 设计规范

### 5.1 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1742572800
}
```

### 5.2 分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

### 5.3 错误码

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 500 | 服务器错误 |

---

## 六、数据库规范

### 6.1 Flyway 迁移

- 所有数据库变更必须通过 Flyway 迁移脚本
- 脚本命名：`V{version}__{description}.sql`
- 示例：`V1__init_schema.sql`、`V2__sys_user.sql`

### 6.2 表设计

- 表名用下划线：`sys_user`、`product_order`
- 必须有 `id`、`tenant_id`、`create_time`、`update_time`、`deleted` 字段
- 索引命名：`idx_{table}_{column}`

---

## 七、认证规范

### 7.1 JWT Token

- Token 放在 `Authorization: Bearer {token}` 请求头
- Token 过期时间：30 分钟
- Refresh Token 过期时间：7 天

### 7.2 权限校验

```java
// 角色校验
@PreAuthorize("hasRole('ADMIN')")

// 权限码校验
@PreAuthorize("hasAuthority('order:create')")
```

---

## 八、文档更新检查清单

> ⚠️ **每次完成开发任务后，必须检查并更新以下文档**

### 任务完成必检清单

| 文档 | 检查项 | 是否需要更新 |
|------|--------|-------------|
| 03-TASKS.md | 任务状态是否标记完成？执行记录是否追加？ | ☐ |
| 05-CHANGELOG.md | 变更记录是否追加？ | ☐ |
| SESSION_CONTEXT.md | 当前阶段和进度是否更新？ | ☐ |
| API_SPEC.md | 是否有新增/修改接口？是则更新 | ☐ |
| 02-PRD.md | 是否有设计变更？是则更新 | ☐ |

### 需求讨论必检清单

| 文档 | 检查项 |
|------|--------|
| 04-REQUISITION_LOG.md | 需求讨论是否记录？ |
| 02-PRD.md | 讨论确认后的需求是否更新到 PRD？ |
| 03-TASKS.md | 新需求是否拆解为任务？ |

### 检查原则

1. **不遗漏**：每次变更后立即检查清单
2. **不猜测**：不能确定是否需要更新时，答案是"是"
3. **主动告知**：完成后主动告知用户更新了哪些文档

---

## 九、文档位置

所有文档在：`../docs/`

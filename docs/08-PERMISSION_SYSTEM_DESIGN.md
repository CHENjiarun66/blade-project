# 权限系统设计文档

> 本文档描述权限系统当前实现和补充说明。
> 若与 [architecture/DATABASE.md](./architecture/DATABASE.md) 或 Flyway 迁移脚本冲突，以迁移脚本累计结果为准。
> 更新日期：2026-04-10

---

## 一、设计原则

| 原则 | 说明 |
|------|------|
| 多租户 | 权限相关业务表带 `tenant_id`，由租户拦截器隔离 |
| 可审计 | 关联表保留 `deleted`、`create_time` |
| 防重复 | 关联表使用唯一索引避免重复授权 |
| 类型明确 | 权限分菜单、按钮、字段、API 四类 |
| 松耦合 | 菜单渲染与权限判断分离 |

---

## 二、当前权限模型

### 2.1 权限类型

| type | 名称 | 示例 |
|------|------|------|
| 1 | 菜单权限 | `menu:order` |
| 2 | 按钮权限 | `btn:order:create` |
| 3 | 字段权限 | `field:cost_price` |
| 4 | API权限 | `user:create`、`role:update` |

### 2.2 脱敏类型

| mask_type | 名称 | 说明 |
|-----------|------|------|
| 0 | 不脱敏 | 正常返回 |
| 1 | 置空 | 返回 `null` |
| 2 | 脱星 | 局部脱敏 |
| 3 | 替换 | 用固定值替换 |

---

## 三、数据库结构（当前实现）

### 3.1 sys_permission

**来源迁移**：`V12__permission_system.sql`、`V15__add_api_permissions.sql`

```sql
CREATE TABLE sys_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '权限ID',
  name VARCHAR(50) NOT NULL COMMENT '权限名称',
  code VARCHAR(100) NOT NULL COMMENT '权限编码，全局唯一',
  type INT NOT NULL COMMENT '权限类型: 1菜单 2按钮 3字段 4API',
  module VARCHAR(50) DEFAULT NULL COMMENT '所属模块',
  parent_id BIGINT DEFAULT 0 COMMENT '父权限ID',
  path VARCHAR(200) DEFAULT NULL COMMENT '路由路径或接口路径',
  method VARCHAR(10) DEFAULT NULL COMMENT 'HTTP方法',
  icon VARCHAR(50) DEFAULT NULL COMMENT '图标',
  sort INT DEFAULT 0 COMMENT '排序',
  status INT DEFAULT 1 COMMENT '状态',
  mask_type INT DEFAULT NULL COMMENT '脱敏类型',
  mask_value VARCHAR(100) DEFAULT NULL COMMENT '脱敏替换值',
  description VARCHAR(200) DEFAULT NULL COMMENT '权限描述',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  deleted INT DEFAULT 0 COMMENT '删除标记',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_code (code),
  KEY idx_type (type),
  KEY idx_module (module),
  KEY idx_parent_id (parent_id),
  KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限定义表';
```

### 3.2 sys_role_permission

**来源迁移**：`V12__permission_system.sql`、`V16__fix_role_permission_tenant.sql`

```sql
CREATE TABLE sys_role_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  deleted INT DEFAULT 0 COMMENT '软删除标记',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_role_permission (role_id, permission_id),
  KEY idx_role_id (role_id),
  KEY idx_permission_id (permission_id),
  KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';
```

### 3.3 sys_user_role

**来源迁移**：`V1__init_schema.sql`、`V13__sys_user_role_fix.sql`

```sql
CREATE TABLE sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  deleted INT DEFAULT 0 COMMENT '软删除标记',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_user_role_tenant (user_id, role_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';
```

**说明**：`sys_user_role` 修复已落地，不再是提案状态。

### 3.4 sys_role_menu

**来源迁移**：`V1__init_schema.sql`

```sql
CREATE TABLE sys_role_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  menu_id BIGINT NOT NULL COMMENT '菜单ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';
```

**状态**：历史兼容表仍存在；当前权限主链路以 `sys_permission + sys_role_permission` 为主，不依赖本表做权限判断。

---

## 四、当前预置角色

| role_code | 角色名称 | 描述 |
|-----------|---------|------|
| ROLE_OWNER | 老板/经理 | 查看所有数据 |
| ROLE_SALES | 销售员 | 负责订单和销售 |
| ROLE_WAREHOUSE | 仓库管理员 | 管理库存和配送 |
| ROLE_FINANCE | 财务 | 负责收款和金额相关 |
| ROLE_PURCHASE | 采购 | 负责采购和商品入库 |
| ROLE_ADMIN | 系统管理员 | 系统运维和用户管理 |

---

## 五、当前预置权限编码示例

### 5.1 菜单权限

- `menu:dashboard`
- `menu:order`
- `menu:inventory`
- `menu:product`
- `menu:customer`
- `menu:system`
- `menu:product:list`
- `menu:product:colors`
- `menu:product:sizes`
- `menu:product:categories`

### 5.2 按钮权限

- `btn:order:create`
- `btn:order:edit`
- `btn:order:delete`
- `btn:order:confirmPayment`
- `btn:order:deliver`
- `btn:order:cancel`
- `btn:order:view`
- `btn:inventory:in`
- `btn:inventory:out`
- `btn:inventory:adjust`
- `btn:inventory:viewLog`
- `btn:product:create`
- `btn:product:edit`
- `btn:product:delete`
- `btn:customer:create`
- `btn:customer:edit`
- `btn:customer:delete`
- `btn:customer:viewOrders`
- `btn:system:user`
- `btn:system:role`
- `btn:system:permission`

### 5.3 字段权限

- `field:cost_price`
- `field:sale_price`
- `field:profit`
- `field:delivery_qty`
- `field:paid_amount`
- `field:deposit_amount`

### 5.4 API权限

- `user:create`
- `user:update`
- `user:delete`
- `user:password:reset`
- `role:create`
- `role:update`
- `role:delete`
- `role:assign`
- `permission:create`
- `permission:update`
- `permission:delete`

**说明**：当前迁移中的 API 权限编码使用 `user:create`、`role:update` 这类编码，不使用早期示例中的 `api:order:confirmPayment` 风格。

---

## 六、实现要点

### 6.1 后端权限判断

- 通过 `sys_user_role` 查询用户角色。
- 通过 `sys_role_permission` + `sys_permission` 查询权限码。
- 菜单过滤、按钮显隐、字段脱敏、API 权限校验都基于权限码集合实现。

### 6.2 前端使用方式

- 菜单：依据 `menu:*` 权限过滤路由和导航。
- 按钮：依据 `btn:*` 权限控制操作项显示。
- 字段：依据 `field:*` 权限决定是否显示或脱敏。

---

## 七、迁移脚本清单

| 脚本 | 作用 |
|------|------|
| `V12__permission_system.sql` | 创建 `sys_permission`、`sys_role_permission` |
| `V13__sys_user_role_fix.sql` | 修复 `sys_user_role` |
| `V14__permission_data.sql` | 预置角色和权限数据 |
| `V15__add_api_permissions.sql` | 补充 API 权限 |
| `V16__fix_role_permission_tenant.sql` | 修复租户字段为空问题 |

---

## 八、注意事项

1. `sys_role_menu` 当前不作为权限判断主路径。
2. 租户隔离依赖 `TenantLineInnerInterceptor`，权限数据必须保证 `tenant_id` 正确。
3. 若后续新增权限编码或权限类型，必须同时更新迁移脚本、`DATABASE.md` 和本文档。

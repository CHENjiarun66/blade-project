# blade-admin PC 管理端开发规范

> **所有 AI 开发必须遵守本文档。**
> 新会话、新 AI 必须首先阅读本文档。

---

## 一、项目概述

| 项目 | 值 |
|------|---|
| 项目名称 | blade-admin（PC 管理端） |
| 技术栈 | Vue3 + Vite + TypeScript + Element Plus + TailwindCSS v4 |
| 端口 | 5777（开发）、代理到 localhost:8080（后端） |
| 包管理器 | npm |
| Node 版本 | Node 22（已验证兼容） |

**与 blade-mobile 的区别**：
- blade-mobile：移动端 PWA，Vuetify3
- blade-admin：PC 端管理后台，Element Plus

---

## 二、项目结构

```
blade-admin/src/
├── api/                    # API 请求
│   ├── client.ts          # Axios 实例（拦截器、代理配置）
│   └── auth.ts            # 认证相关 API
├── assets/                # 静态资源
├── components/           # 公共组件（待建设）
├── router/
│   └── index.ts          # 路由配置
├── stores/                # Pinia 状态管理
│   └── auth.ts           # 认证状态
├── styles/
│   └── main.css          # 全局样式（TailwindCSS 入口）
├── views/                 # 页面
│   ├── login/            # 登录页
│   ├── layout/           # 主布局（侧边栏 + 头部）
│   ├── dashboard/       # 仪表盘
│   ├── orders/           # 订单管理
│   ├── inventory/       # 库存管理
│   ├── products/        # 商品管理
│   └── clients/         # 客户管理
├── App.vue
└── main.ts               # 入口文件
```

---

## 三、开发工作流：边开发边封装

### 核心原则

**不预先封装，先做业务，在开发过程中识别重复模式并抽取。**

### 阶段一：开发第一个业务模块

从订单管理开始，完成所有功能：
- 订单列表页（表格 + 筛选 + 分页）
- 订单详情页
- 订单状态操作（确认/发货/完成/取消）
- 新建/编辑订单表单

### 阶段二：识别重复模式

完成订单模块后，主动识别以下重复模式：

| 重复模式 | 抽取为组件 |
|---------|-----------|
| 表格 + 筛选 + 分页 | `DataTable` |
| 状态标签显示 | `StatusTag` |
| 状态操作按钮组 | `StatusActions` |
| 弹窗表单 | `DialogForm` |
| 图片上传 | `ImageUploader` |
| 高级筛选面板 | `AdvancedFilter` |

### 阶段三：抽取组件

1. 在 `src/components/` 下创建组件
2. 用抽取的组件重构订单模块
3. 用组件快速搭建其他模块

### 阶段四：开发其他模块

使用已封装的组件，快速开发：
- 库存管理
- 商品管理
- 客户管理

---

## 四、组件设计规范

### 4.1 DataTable 组件（待抽取）

**触发时机**：发现多个页面有相同的表格 + 筛选 + 分页模式

**预期 props**：
```typescript
interface DataTableProps {
  api: () => Promise<any>  // 数据加载函数
  columns: Column[]          // 列配置
  searchFields?: SearchField[] // 搜索字段
  // ...其他配置
}
```

### 4.2 StatusTag 组件（待抽取）

**触发时机**：多个页面有相同的状态 Badge 显示逻辑

**预期用法**：
```vue
<StatusTag :status="order.status" type="order" />
```

### 4.3 StatusActions 组件（待抽取）

**触发时机**：多个页面有相同的状态操作按钮组

**预期用法**：
```vue
<StatusActions :status="order.status" :actions="orderActions" @action="handleAction" />
```

---

## 五、API 设计规范

### 5.1 请求格式

所有 API 通过 `/api` 代理到后端 `http://localhost:8080`

```typescript
// 示例：订单列表
GET /api/orders?page=1&pageSize=20&status=pending
```

### 5.2 响应格式

后端返回统一格式：

```typescript
interface R<T> {
  code: number      // 状态码
  data: T           // 数据
  message: string   // 消息
}
```

### 5.3 API 模块划分

```
api/
├── client.ts       # Axios 实例 + 拦截器
├── auth.ts         # 认证 API
├── order.ts        # 订单 API（待创建）
├── inventory.ts    # 库存 API（待创建）
├── product.ts      # 商品 API（待创建）
└── client.ts       # 客户 API（待创建）
```

---

## 六、路由规范

### 6.1 路由配置

在 `src/router/index.ts` 中配置，使用动态导入：

```typescript
{
  path: '/orders',
  name: 'Orders',
  component: () => import('@/views/orders/index.vue'),
  meta: { title: '订单管理' },
}
```

### 6.2 页面标题

通过 `route.meta.title` 设置页面标题，布局组件读取并显示。

### 6.3 权限控制（待实现）

未来加入路由守卫，校验登录状态和权限。

---

## 七、状态管理规范

### 7.1 Auth Store

当前已实现 `stores/auth.ts`：
- `token`：登录令牌
- `userInfo`：用户信息
- `setToken()`：设置令牌
- `setUserInfo()`：设置用户信息
- `logout()`：登出

### 7.2 Store 模块划分（待实现）

| Store | 用途 |
|-------|------|
| auth.ts | 认证状态 |
| order.ts | 订单相关状态（待创建） |
| inventory.ts | 库存相关状态（待创建） |

---

## 八、样式规范

### 8.1 TailwindCSS v4

使用 TailwindCSS v4，PostCSS 配置：

```javascript
// postcss.config.js
export default {
  plugins: {
    '@tailwindcss/postcss': {},
    autoprefixer: {},
  },
}
```

### 8.2 Element Plus 样式

Element Plus 组件样式直接使用，无需额外配置。

### 8.3 页面容器

所有业务页面使用 `.page-container` 类：

```vue
<div class="page-container">
  <el-card>...</el-card>
</div>
```

---

## 九、常见问题与经验

### 9.1 Vue 响应式警告

**问题**：将组件对象放入 `ref()` 会导致警告：
> "Vue received a Component that was made a reactive object"

**原因**：`ref()` 会对对象进行深层响应式转换，组件不需要。

**解决**：对组件使用 `shallowRef()` 或 `markRaw()`：

```typescript
import { shallowRef } from 'vue'

// 错误
const icon = ref(ShoppingCart)

// 正确
const icon = shallowRef(ShoppingCart)
```

### 9.2 TailwindCSS v4 PostCSS 配置

**问题**：
> "It looks like you're trying to use `tailwindcss` directly as a PostCSS plugin"

**解决**：使用 `@tailwindcss/postcss`：

```javascript
plugins: {
  '@tailwindcss/postcss': {},
  autoprefixer: {},
}
```

### 9.3 图标组件导入

Element Plus 图标需要手动导入并在全局注册：

```typescript
// main.ts
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
```

---

## 十、开发检查清单

> **⚠️ 重要声明：所有功能模块开发必须执行测试用例，验证通过才能交付。**
> 新 AI 或后续接手者必须按照 TEST_TEMPLATE.md 模板为每个模块编写测试用例。

每次完成模块开发后：

- [ ] 功能正常运行，无 console 错误
- [ ] 登录/登出流程正常
- [ ] API 代理正常（前后端联调）
- [ ] 组件抽取机会识别
- [ ] 文档同步（03-TASKS.md、05-CHANGELOG.md）
- [ ] **必须**为当前模块编写测试用例（复制 TEST_TEMPLATE.md）
- [ ] **必须**执行测试用例并记录结果
- [ ] **必须**截图测试（需浏览器测试的功能模块）
- [ ] 测试记录更新

---

## 十一、相关文档

| 文档 | 说明 |
|------|------|
| [../docs/01-README.md](../docs/01-README.md) | 项目整体结构和工作流程 |
| [../docs/02-PRD.md](../docs/02-PRD.md) | 产品需求文档 |
| [../docs/03-TASKS.md](../docs/03-TASKS.md) | 开发任务清单 |
| [../docs/05-CHANGELOG.md](../docs/05-CHANGELOG.md) | 变更记录 |
| [TEST_CASES.md](./TEST_CASES.md) | 测试用例（登录页示例） |
| [TEST_TEMPLATE.md](./TEST_TEMPLATE.md) | 测试用例模板（包含浏览器截图测试规范） |

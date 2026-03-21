# Blade Mobile 前端开发规范

> 本目录是 BladeProject 的移动端前端目录。
> 新会话必须阅读上级目录的 `../CLAUDE.md`。
> 本文件是前端的补充规范。

---

## 一、技术栈

| 层级 | 技术 |
|------|------|
| 框架 | Vue 3.4+ (Composition API) |
| 构建 | Vite 8 |
| 语言 | TypeScript 5 |
| UI 库 | Vuetify 3 + Naive UI |
| 路由 | Vue Router 4 |
| 状态 | Pinia |
| HTTP | Axios |
| PWA | vite-plugin-pwa |

---

## 二、项目结构

```
blade-mobile/
├── src/
│   ├── api/               # API 调用
│   │   ├── client.ts      # Axios 实例（拦截器）
│   │   ├── auth.ts        # 认证 API
│   │   ├── order.ts       # 订单 API
│   │   ├── inventory.ts   # 库存 API
│   │   └── product.ts     # 商品 API
│   ├── router/
│   │   └── index.ts       # 路由配置
│   ├── stores/            # Pinia 状态管理
│   │   └── auth.ts        # 认证状态
│   ├── views/             # 页面组件
│   │   ├── Layout.vue     # 主布局（底部导航）
│   │   ├── Login.vue       # 登录页
│   │   ├── order/          # 订单模块
│   │   ├── inventory/      # 库存模块
│   │   ├── product/        # 商品模块
│   │   └── dashboard/      # 看板模块
│   ├── types/             # TypeScript 类型定义
│   ├── plugins/           # 插件配置
│   │   └── vuetify.ts     # Vuetify 配置
│   ├── utils/             # 工具函数
│   └── main.ts            # 入口文件
├── index.html
├── vite.config.ts        # Vite 配置（含 PWA）
└── package.json
```

---

## 三、代码规范

### 3.1 组件规范

```vue
<template>
  <v-container>
    <div class="text-h6 mb-4">页面标题</div>
    <!-- 内容 -->
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
// ...
</script>
```

### 3.2 API 调用规范

```typescript
// api/order.ts
import client from './client'
import type { R, PageResult } from '@/types/auth'
import type { OrderVO, OrderPageDTO } from '@/types/order'

export function getOrderList(params: OrderPageDTO): R<PageResult<OrderVO>> {
  return client.get('/orders', { params })
}
```

### 3.3 状态管理规范

```typescript
// stores/auth.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const isLoggedIn = computed(() => !!token.value)

  return { token, isLoggedIn }
})
```

---

## 四、路由规则

| 路径 | 页面 | 说明 |
|------|------|------|
| /login | Login.vue | 登录页 |
| / | Layout.vue | 主布局（含底部导航） |
| /orders | OrderList.vue | 订单列表 |
| /orders/create | OrderCreate.vue | 创建订单 |
| /orders/:id | OrderDetail.vue | 订单详情 |
| /inventory | InventoryList.vue | 库存列表 |
| /inventory/in | InventoryIn.vue | 入库 |
| /inventory/out | InventoryOut.vue | 出库 |
| /products | ProductList.vue | 商品列表 |
| /dashboard | Dashboard.vue | 数据看板 |

---

## 五、PWA 配置

vite.config.ts 中已配置 vite-plugin-pwa：
- 自动更新 Service Worker
- Web App Manifest
- 离线缓存（Google Fonts）
- iOS 支持（apple-touch-icon）

---

## 六、运行命令

```bash
# 安装依赖
npm install

# 开发
npm run dev

# 构建
npm run build

# 预览
npm run preview
```

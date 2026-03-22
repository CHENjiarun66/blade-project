# Blade Admin - PC 管理端

服装订单管理系统的 PC 管理后台。

## 技术栈

- **框架**：Vue 3 + Vite + TypeScript
- **UI 组件**：Element Plus
- **样式**：TailwindCSS v4
- **状态管理**：Pinia
- **路由**：Vue Router
- **HTTP 客户端**：Axios

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev
# 访问 http://localhost:5777

# 构建生产版本
npm run build
```

## 项目结构

```
src/
├── api/            # API 请求封装
├── router/         # 路由配置
├── stores/         # Pinia 状态管理
├── views/          # 页面组件
│   ├── login/      # 登录页
│   ├── layout/     # 主布局
│   ├── dashboard/  # 仪表盘
│   ├── orders/     # 订单管理
│   ├── inventory/  # 库存管理
│   ├── products/   # 商品管理
│   └── clients/    # 客户管理
└── main.ts        # 入口文件
```

## 开发规范

**重要**：开发前请阅读 [CLAUDE.md](./CLAUDE.md)，了解：
- 边开发边封装的组件模式
- API 设计规范
- 常见问题与经验

## 功能模块

- [ ] 登录/登出
- [ ] 仪表盘（统计卡片）
- [ ] 订单管理
- [ ] 库存管理
- [ ] 商品管理
- [ ] 客户管理

## 相关文档

- [CLAUDE.md](./CLAUDE.md) - AI 开发规范
- [../docs/](../docs/) - 项目文档中心

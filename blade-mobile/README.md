# Blade Mobile

服装订单管理系统移动端前端。

## 技术栈

- Vue 3.5
- TypeScript 5
- Vite 8
- Vuetify 4
- Vue Router 4
- Pinia
- PWA
- `@blade/types` 共享类型

## 说明

- 当前代码中使用的是 `vuetify@^4.0.3`，本文档已按实际依赖对齐。
- 依赖中存在 `naive-ui`，但当前源码未见实际使用；暂视为疑似残留依赖，后续可单独清理。

当前以存量兼容维护为主，新增页面后置；完整移动发布与真实验收未闭环，见 [范围](../docs/PROJECT_SCOPE.md)。共享类型需先构建，步骤见 [环境配置](../docs/00-SETUP.md)。

## 开发

```bash
npm ci
npm run dev
npm run build
```

## 目录结构

- `src/api/`：API 调用
- `src/router/`：路由配置
- `src/stores/`：状态管理
- `src/views/`：页面组件
- `src/types/`：本地类型
- `src/plugins/`：插件配置

## 文档

- [CLAUDE.md](./CLAUDE.md)：开发规范
- [../docs/02-PRD.md](../docs/02-PRD.md)：业务规则与技术栈依据

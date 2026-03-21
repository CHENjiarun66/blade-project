# 技术架构决策

> 本文档记录 BladeProject 的核心技术架构决策及其背景。

---

## 一、核心决策

### 1.1 移动端：纯 Vue3 PWA

**决策**：采用 **Vue3 + Vite + TypeScript** 构建移动端 PWA，放弃 Flutter / UniApp / React Native。

**理由**：

| 维度 | 评分 | 说明 |
|------|------|------|
| AI 生成质量 | ⭐⭐⭐⭐⭐ | Vue3 + TS 是 Claude Code 最擅长的组合 |
| Bundle 大小 | ⭐⭐⭐⭐⭐ | 100-200KB，Flutter Web 在 1.5-2MB+ |
| 苹果 Safari PWA 兼容性 | ⭐⭐⭐⭐⭐ | 原生支持 |
| iPad 适配 | ⭐⭐⭐⭐ | Vuetify3 组件库已做好响应式适配 |
| 工具链成熟度 | ⭐⭐⭐⭐⭐ | Vite + Vue3 + TS 是最成熟组合 |

### 1.2 后端：Spring Boot 3 迁移

**决策**：将后端从 SpringBlade 微服务框架迁移到 **Spring Boot 3 单体架构**。

**理由**：

| 问题 | SpringBlade 现状 | Spring Boot 3 新方案 |
|------|----------------|---------------------|
| AI 开发友好度 | 低（封装框架，文档缺失） | 高（标准框架，Google 资料丰富） |
| 代码量 | ~28000 行 | ~5500 行（少 4/5） |
| 多租户实现 | 手动拼接，容易出错 | MyBatis-Plus 插件，配置即搞定 |
| 鉴权 | 自定义，非标准 | Spring Security OAuth2，标准实现 |

### 1.3 AI 开发工具链

- **主力**：Claude Code
- **辅助**：Windsurf（Agent 模式）

---

## 二、已废弃的方案

| 方案 | 废弃原因 |
|------|---------|
| Flutter Web | Bundle 太大（1.5-2MB+），PWA 缓存受限 |
| UniApp + Vue 2 | AI 生成质量差，条件编译是重灾区；且必须升级 Vue 3 |
| SpringBlade 微服务 | 对 AI 开发不友好，每次启动都有问题，文档缺失 |
| 微服务架构（Nacos/Seata） | 移动端 MVP 阶段不需要，单体足以支撑 |

---

## 三、完整技术栈

详见：[02-PRD.md](../02-PRD.md)

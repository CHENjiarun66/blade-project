# 技术决策记录

> 记录项目中重要的技术决策及其背景、理由。

---

## 决策清单

| ID | 标题 | 日期 | 状态 |
|----|------|------|------|
| DEC-001 | 移动端采用 Vue3 PWA | 2026-03-21 | ✅ 已采纳 |
| DEC-002 | 后端迁移到 Spring Boot 3 单体 | 2026-03-21 | ✅ 已采纳 |
| DEC-003 | 多租户采用 MyBatis-Plus 插件 | 2026-03-21 | ✅ 已采纳 |
| DEC-004 | 认证采用 Spring Security OAuth2 | 2026-03-21 | ✅ 已采纳 |
| DEC-005 | 移动端采用 Monorepo 结构 | 2026-03-21 | ✅ 已采纳 |
| DEC-006 | Stitch → Gemini → Claude Code 工作流 | 2026-03-21 | ✅ 已采纳 |
| DEC-007 | 订单系统先开发（P0） | 2026-03-21 | ✅ 已采纳 |

---

## DEC-001: 移动端采用 Vue3 PWA

**日期**：2026-03-21
**状态**：✅ 已采纳

**最终选择**：Vue3 + Vite + TypeScript + Vuetify3 + PWA

**核心理由**：
- AI 生成质量最高
- Bundle 最小（100-200KB）
- PWA 苹果 Safari 原生支持

---

## DEC-002: 后端迁移到 Spring Boot 3 单体

**日期**：2026-03-21
**状态**：✅ 已采纳

**最终选择**：Spring Boot 3.2 + Spring Security OAuth2 + MyBatis-Plus

**核心理由**：
- AI 开发友好度提升 3-5 倍
- 代码量少 4/5
- 多租户更安全

---

## DEC-003: 多租户采用 MyBatis-Plus TenantLineInnerInterceptor

**日期**：2026-03-21
**状态**：✅ 已采纳

**核心问题**：SpringBlade 的多租户是手动的，容易遗漏导致数据串租户

**解决方案**：TenantLineInnerInterceptor 配置，零代码改动

---

## DEC-004: 认证采用 Spring Security OAuth2

**日期**：2026-03-21
**状态**：✅ 已采纳

**背景**：SpringBlade 使用自定义 TokenGranter，非标准 OAuth2

**方案**：Spring Security OAuth2 Authorization Server + JWT

---

## DEC-007: 订单系统先开发（P0）

**日期**：2026-03-21
**状态**：✅ 已采纳

**最终优先级**：订单 > 库存 > 看板

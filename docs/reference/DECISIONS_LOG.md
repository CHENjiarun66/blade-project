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
| DEC-008 | 文件中心升级为数字资产中心 | 2026-06-03 | ✅ 已采纳 |

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

---

## DEC-008: 文件中心升级为数字资产中心

**日期**：2026-06-03
**状态**：✅ 已采纳

**最终选择**：文件中心按通用数字资产中心设计，图片和基础视频只是第一版资产类型。

**核心理由**：
- 现有统一文件存储已经具备上传、预览、软删除和 fileId 保存底座。
- 后续需要支持商品/SKU 图片绑定、未绑定文件清理和客户 iPad 现货展示页。
- 大系统通用做法是资产、文件夹、标签、绑定关系和业务对象分离，避免商品、订单、库存各自维护文件逻辑。
- 业务表继续保存 fileId，不保存物理路径，后续可切七牛云、NAS 或 CDN。

**边界约束**：
- 第一版不做视频转码、分片上传、七牛云/NAS 切换、客户公开分享链接和文件版本管理。
- 新增业务关系必须进入 `file_business_bind`，不得继续用单一 `business_type/business_id` 承接所有关系。
- 详细方案以 [../12-FILE_CENTER_ASSET_DESIGN.md](../12-FILE_CENTER_ASSET_DESIGN.md) 为准。

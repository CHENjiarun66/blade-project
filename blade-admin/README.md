# Blade Admin：PC 管理端

当前主力管理界面，使用 Vue 3、Vite 8、TypeScript、Element Plus 2、Tailwind 4、Pinia 3、Router 5。精确版本以 package-lock.json 为准。

在本目录执行 `npm ci`、`npm run dev`；验证构建执行 `npm run build`。依赖与后端连接见 [环境配置](../docs/00-SETUP.md)。

订单、配货、库存、商品、客户、文件、统计、用户/角色/权限、档口和 Agent Key 页面已实现；订单导出、库存统计、个人中心也已完成，不再列为待开发。功能实现不等于所有角色和真实业务验收通过。

当前先修复存量身份/授权并完成订单、库存和档口验收，新增扩展后置。具体 [方向](../docs/PROJECT_SCOPE.md)、[任务](../docs/03-TASKS.md)、[代码评估](../docs/CODE_REVIEW_2026-11-02.md) 和 [开发规范](./CLAUDE.md)。旧 TEST_CASES 是当时手工/截图记录，不能代替当前版本回归结果。

# 档口模块生产副本预演 / 备份 / 灰度 / 回滚 checklist（只写不执行）

> 状态：**checklist 草案，未在任何生产/NAS/副本环境执行**。
> 依据：`docs/20-OUTLET_ACCESS_CONTROL_DESIGN.md` 第九章、Series F 本地交付报告。
> 约束：本分支禁止连接 NAS/生产；以下命令仅为生产副本（isolated copy）人工执行清单，执行前必须获得书面批准。

## 0. 前置条件（全部满足才可开始）

- [ ] 已获得业务负责人 + 技术负责人对「回填范围、租户、处理人、验收人」的书面批准。
- [ ] 目标为生产库的**隔离副本**，不是生产库本身；连接串、库名不含生产/NAS 特征。
- [ ] 副本副本已确认与生产同一迁移版本（Flyway `V63`–`V66` 已应用，`source_outlet_id` 全部可空）。
- [ ] 已产出并人工确认 `source_shop` 映射决策 CSV（`MAP`/`SKIP`/`REVIEW` 全部有 reason）。
- [ ] 已确认本次不删除 `btn:order:viewAll`、不加 `source_outlet_id NOT NULL`。
- [ ] 变更窗口、回滚窗口、负责人和联系链已明确。

## 1. 备份（执行前）

- [ ] 数据库全量备份：`mysqldump --single-transaction --routines --triggers <copy_db> > backup_YYYYMMDD.sql`。
- [ ] `sale_order`、`order_draft`、`sales_outlet`、`sys_user_outlet`、`agent_key`、`agent_key_outlet` 单表导出。
- [ ] `uploads/` 文件目录与文件元数据（`file_storage`/`file_business_bind`）快照。
- [ ] 应用配置、Flyway schema history、当前部署镜像 tag 记录。
- [ ] 备份可 restore 演练（至少在副本内验证一次）。

## 2. 事实审计（只读，副本）

- [ ] 运行 `scripts/outlet-source-shop-audit.sql`，导出分布/空值/纯数字/批次/冲突报告。
- [ ] 运行 `scripts/outlet-user-outlet-authorization-suggestions.sql`，导出用户授权建议报告。
- [ ] 运行 `scripts/outlet-scope-explain.sql`，记录 single/multi/all/none/unassigned 的 EXPLAIN 与索引清单。
- [ ] 人工复核疑似批次/纯数字清单，确认不进入映射。

## 3. 回填预演（dry-run，副本）

- [ ] 准备映射 CSV（模板 `scripts/outlet-backfill-plan-template.csv`，列 `tenant_id,legacy_source_shop,outlet_code,decision,reason`）。
- [ ] 执行 dry-run（不写库）：
  ```bash
  java -jar blade-backend.jar \
    --blade.outlet.backfill.mapping-file=/secure/path/outlet-mapping.csv \
    --blade.outlet.backfill.tenant-id=<tenant>
  ```
- [ ] 核对 JSON 报告：`mapRows/skipRows/reviewRows`、`ordersCandidates/draftsCandidates`、
      `ordersConflict/draftsConflict`、`ordersSuspect/draftsSuspect`、`confirmedDraftsSkipped`、`warnings`。
- [ ] 确认无 `errors`，且冲突/疑似清单已人工判定。
- [ ] 记录回填前基线：行数、金额、收款、状态、明细、文件绑定、`source_shop` 摘要。

## 4. 副本 apply（显式闸门）

- [ ] 执行 apply（仅在副本，需四重显式确认）：
  ```bash
  java -jar blade-backend.jar \
    --blade.outlet.backfill.mapping-file=/secure/path/outlet-mapping.csv \
    --blade.outlet.backfill.tenant-id=<tenant> \
    --blade.outlet.backfill.apply=true \
    --blade.outlet.backfill.copy-environment-ack=true
  ```
- [ ] 确认安全闸门拒绝任何生产/NAS 特征连接（命中即 403）。
- [ ] 核对 apply 报告 `reconciliationConsistent=true`；否则事务已回滚，立即停止。
- [ ] 再次执行 dry-run，确认 `ordersUpdated=0/draftsUpdated=0`、`alreadyApplied` 增加（幂等）。

## 5. 对账与验收

- [ ] 回填前后对账：行数、`total_amount`、`gross_received_amount`、`net_received_amount`、
      `cash_refund_amount`、`sales_return_amount`、`write_off_amount`、状态分布、明细数、
      文件绑定数、`source_shop` 分布全部一致。
- [ ] 抽查 `source_outlet_id` 已填、`source_shop` 原值未变。
- [ ] 确认未删除 `btn:order:viewAll`、未加 NOT NULL。
- [ ] 用户初始授权：人工按建议包确认 CSV，通过控制台逐项授权；确认无自动授权。

## 6. 灰度发布（先双读后切写）

- [ ] 部署双读版本：优先 `source_outlet_id`，历史记录兼容名称快照。
- [ ] 观察一个发布周期：越权 403、待归档、统计口径、导出、文件、Agent 出口。
- [ ] 切写：新草稿/订单必须写 `source_outlet_id`；旧客户端兼容期保留。
- [ ] 监控：跨档口泄漏告警、`/api/agent/*` 401/403 比例、查询耗时。

## 7. 回滚

- [ ] 应用回滚到上一镜像 tag（档口字段为加法迁移，可安全回退应用）。
- [ ] 数据回滚：`source_outlet_id` 为加法回填，如需回退按备份 restore 或按备份哈希定向还原。
- [ ] 回滚后复核：行数/金额/状态/明细/文件绑定与回滚前基线一致。
- [ ] 记录回滚原因、时间、影响面与后续动作。

## 8. 明确不做（本分支）

- [ ] 不连接 NAS/生产，不执行上面任何 apply/部署命令。
- [ ] 不删除 `btn:order:viewAll`，不加 `source_outlet_id NOT NULL`。
- [ ] 不虚报生产副本预演、用户授权或部署已完成。

# 多 Agent 联合开发协作规范

> 本文件规定 Codex、DeepSeek 和其他 Agent 工具在同一仓库协作时的信息同步协议。
> 所有 Agent 开工前必须阅读本文档、`SESSION_CONTEXT.md`、`03-TASKS.md` 和当前任务的设计/SOW。
> 当前仓库没有根目录 `AGENTS.md`，因此本文件和 Git 工作流是现行协作规则；如后续新增统一入口，必须同步更新本文档。

---

## 一、协作模型

```
同一仓库、独立 worktree/分支
   ↓
Git = 唯一交付同步通道（代码与文档的最终一致来源）
   ↓
文档 = 状态板（SESSION_CONTEXT / TASKS / CHANGELOG）
   ↓
协作规范 + Git 工作流 = 当前规则源
```

### 信息流三层结构

| 层 | 载体 | 作用 |
|----|------|------|
| 规则层 | `docs/reference/AGENT_COLLABORATION.md` + `GIT_BRANCH_WORKFLOW.md` | 规定必须做什么及如何合并发布 |
| 状态层 | `docs/SESSION_CONTEXT.md`、`docs/03-TASKS.md`、`docs/05-CHANGELOG.md` | 记录"现在是什么状态、谁在做" |
| 事实层 | Git（同一工作区） | 代码、文档、历史的最终一致来源 |

**核心原则**：
- 同一仓库不等于共享同一工作目录；并发修改必须使用独立 worktree 和分支。
- GitHub 保存可交接提交，NAS 只承载已发布的 `master`，两者不能互相替代。
- 冲突主要来自任务未认领、契约未锁定、修改了同一文件或没有及时形成可回滚提交。
- 任何一方开工前必须能回答三个问题：**现在在哪个分支？谁在做什么任务？上一次交接状态是什么？**

---

## 二、执行人标识

| Agent | 标识 | commit message 后缀 |
|-------|------|---------------------|
| Codex | Codex | `[codex]` |
| DeepSeek（DSH） | DeepSeek | `[dsh]` |
| Z Code | Z Code | `[zcode]` |

**所有 commit message 末尾必须带执行人标识**，例如：

```text
feat(order): add write-off flow [codex]
fix(catalog): pinch zoom jitter [dsh]
docs(collab): add dual-agent protocol [dsh]
```

> 说明：项目 git author 统一为 `Blade Developer`，因此用 commit message 后缀区分执行人，不改动全局 git 配置，简单可靠。

---

## 三、五条同步协议

### 协议 1：任务认领（防撞车）

- 开工前先读 `docs/03-TASKS.md`，检查目标任务是否已被认领。
- **认领**：把任务状态从 `⏳ TODO` 改为 `⏳ 进行中（执行人：Codex / DeepSeek）`，并 commit。
- **完成**：改为 `✅ 完成`，补上执行记录。
- **一个任务同一时刻只允许一个 Agent 认领。**
- 若发现对方认领后长期无进展（超过 1 个工作日），在 CHANGELOG 注明后可接替，但必须先与用户确认。

### 协议 2：开工 / 收工仪式

**开工（必做）**：

```bash
git status --short --branch           # 1. 先确认工作区和当前分支
git fetch origin                      # 2. 只更新远端引用，不覆盖本地工作
git branch -vv                        # 3. 核对 ahead/behind 和 upstream
# 4. 工作区干净时，才按目标分支执行 fast-forward 或 rebase
# 5. 读 SESSION_CONTEXT、TASKS 和当前 SOW，然后认领任务
```

**收工（任务完成或会话结束，必做）**：

0. 运行 `node scripts/gen-status.mjs` 刷新状态看板（更新 `docs/STATUS.md`）
1. 更新 `docs/03-TASKS.md`：任务状态 + 执行记录
2. 更新 `docs/05-CHANGELOG.md`：变更内容 + 原因 + 影响范围 + **验证结果** + 执行人
3. 更新 `docs/SESSION_CONTEXT.md`：当前摘要、下一步、未完成事项
4. `git add` + `git commit`（message 带 `[codex]` / `[dsh]` 后缀）
5. `git push`

> 任务没做完时也应形成可回滚节点并 push；如果因测试失败等原因不能提交，必须在交接中列出未提交文件、原因和恢复方法，不能让另一 Agent 猜测工作区归属。

### 协议 3：会话快照维护

`docs/SESSION_CONTEXT.md` 是另一个 Agent 接手时的**唯一状态入口**，必须保持新鲜：

- 每次会话结束或任务里程碑完成时更新。
- 只写摘要：当前阶段、下一步、未完成事项、阻塞与风险。
- 不重复 CHANGELOG 细节（CHANGELOG 是明细，SESSION_CONTEXT 是摘要）。

### 协议 4：提交与分支纪律

- 分支规范沿用 `docs/reference/GIT_BRANCH_WORKFLOW.md`（`feature/*` → `develop` → `release/*` → `master`）。
- 跨模块大重构使用一个集成分支和多个独立 worktree/子分支；不得让多个 Agent 并发写同一工作区。
- 每个可回滚节点一个 commit，按功能边界拆分，message 带执行人后缀。
- **多个无关功能禁止混在一个 commit。**
- 禁止 `git reset --hard` / `git checkout -- .` 清理工作区。
- 合并前先 `git fetch`，确认对方是否已推进；不要在对方未 push 的情况下基于旧状态大改。
- NAS 不用于同步开发中代码。只有 release 验收、合入并推送 `master` 后，才按生产手册部署。

### 协议 5：验证结果必填

- 所有变更记录必须包含**实际执行的验证结果**（命令 + 输出结论），只写"已测试"不算。
- 示例：

  ```text
  **验证结果**：
  - cd blade-backend && mvn test：Tests run 383, Failures 0, Errors 0
  - cd blade-admin && npm run build：通过
  - npx playwright test e2e-catalog-infinite-cache.spec.ts：9/9 通过
  ```

- 好处：对方接手时无需重新猜测验证方式，直接复用命令确认。

---

## 四、冲突处理

### 4.1 同一文件同时修改

1. 先 `git pull` 拉取对方提交。
2. 解决冲突时以"最新业务事实"为准：代码以 PRD + 实际运行结果为准，文档以时间新者为准。
3. 解决后 commit 并标注执行人后缀。

### 4.2 任务重叠

- 若发现对方已认领同一任务，**停止并改认领其他任务**。
- 若必须协作同一任务：在 TASKS.md 认领行注明 `（协作）`，并按模块 / 文件划分边界，明确各自负责范围。

### 4.3 会话上下文过期

- 若 SESSION_CONTEXT.md 明显过期（超过 1 天未更新且代码有推进），先交叉核对 `03-TASKS.md` + `05-CHANGELOG.md` + `git log`，确认事实后更新 SESSION_CONTEXT.md。

### 4.4 文档状态漂移

- 文档与代码冲突时，以 `TASKS + CHANGELOG + 代码实现` 三方交叉核对为准，并把核对结果记入 CHANGELOG。

---

## 五、交接检查清单

交给另一个 Agent 前逐项确认：

- [ ] `docs/03-TASKS.md` 任务状态最新（认领 / 完成）
- [ ] `docs/05-CHANGELOG.md` 本次变更已记录（含验证结果 + 执行人后缀）
- [ ] `docs/SESSION_CONTEXT.md` 当前摘要 / 下一步已更新
- [ ] 代码已 commit（message 带执行人后缀）并 push
- [ ] 未提交的临时文件已说明（或在 CHANGELOG 记录原因）

---

## 六、日常问答速查

| 问题 | 答案 |
|------|------|
| 我在哪个分支、有没有未提交变更？ | `git status --short --branch` |
| 对方做到哪了？ | 读 `docs/SESSION_CONTEXT.md` + `docs/03-TASKS.md` |
| 上次变更验证结果是什么？ | 读 `docs/05-CHANGELOG.md` 对应条目 |
| 这个任务我能接吗？ | 查 TASKS.md 是否已被认领（`进行中（执行人：xxx）`） |
| 提交信息怎么写？ | `<type>(<scope>): <description> [codex|dsh]` |

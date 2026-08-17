# 双 Agent 联合开发协作规范

> 本文件规定 **Codex（主力）** 与 **DeepSeek（DSH 协同）** 在同一台 Mac、同一工作目录下联合开发时的信息同步协议。
> 两个 Agent 开工前都必须阅读本文档，并遵守根目录 `AGENTS.md` 中对应的强制规则。
> 规则以 `AGENTS.md` 为唯一强制入口，本文档是详细说明和操作手册。

---

## 一、协作模型

```
同一工作目录共享
   ↓
Git = 唯一事实同步通道（代码与文档的最终一致来源）
   ↓
文档 = 状态板（SESSION_CONTEXT / TASKS / CHANGELOG）
   ↓
AGENTS.md = 规则源（两个 Agent 都会自动读取）
```

### 信息流三层结构

| 层 | 载体 | 作用 |
|----|------|------|
| 规则层 | 根目录 `AGENTS.md` | 两个 Agent 启动时自动读取，规定"必须做什么" |
| 状态层 | `docs/SESSION_CONTEXT.md`、`docs/03-TASKS.md`、`docs/05-CHANGELOG.md` | 记录"现在是什么状态、谁在做" |
| 事实层 | Git（同一工作区） | 代码、文档、历史的最终一致来源 |

**核心原则**：
- 同一目录共享，**Git 状态天然一致**，不需要跨设备同步。
- 冲突主要来自"信息没写进文档"或"没及时 commit"，而不是文件本身。
- 任何一方开工前必须能回答三个问题：**现在在哪个分支？谁在做什么任务？上一次交接状态是什么？**

---

## 二、执行人标识

| Agent | 标识 | commit message 后缀 |
|-------|------|---------------------|
| Codex | Codex | `[codex]` |
| DeepSeek（DSH） | DeepSeek | `[dsh]` |

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
git fetch origin && git pull          # 1. 确认与远程一致
git status --short --branch           # 2. 确认工作区状态（干净或有明确未提交内容）
# 3. 读 docs/SESSION_CONTEXT.md 确认最新状态
# 4. 确认目标任务未被认领，然后认领
```

**收工（任务完成或会话结束，必做）**：

0. 运行 `node scripts/gen-status.mjs` 刷新状态看板（更新 `docs/STATUS.md`，见 AGENTS.md 规则 8）
1. 更新 `docs/03-TASKS.md`：任务状态 + 执行记录
2. 更新 `docs/05-CHANGELOG.md`：变更内容 + 原因 + 影响范围 + **验证结果** + 执行人
3. 更新 `docs/SESSION_CONTEXT.md`：当前摘要、下一步、未完成事项
4. `git add` + `git commit`（message 带 `[codex]` / `[dsh]` 后缀）
5. `git push`

> 即使任务没做完，会话结束前也要 commit 一个可回滚节点并 push，避免对方接手时拿到半个工作区。

### 协议 3：会话快照维护

`docs/SESSION_CONTEXT.md` 是另一个 Agent 接手时的**唯一状态入口**，必须保持新鲜：

- 每次会话结束或任务里程碑完成时更新。
- 只写摘要：当前阶段、下一步、未完成事项、阻塞与风险。
- 不重复 CHANGELOG 细节（CHANGELOG 是明细，SESSION_CONTEXT 是摘要）。

### 协议 4：提交与分支纪律

- 分支规范沿用 `docs/reference/GIT_BRANCH_WORKFLOW.md`（`feature/*` → `develop` → `release/*` → `master`）。
- 每个可回滚节点一个 commit，按功能边界拆分，message 带执行人后缀。
- **多个无关功能禁止混在一个 commit。**
- 禁止 `git reset --hard` / `git checkout -- .` 清理工作区。
- 合并前先 `git fetch`，确认对方是否已推进；不要在对方未 push 的情况下基于旧状态大改。

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

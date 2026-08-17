# 多 Agent 联合开发协作规范

> 本文件规定 Codex、DeepSeek（DSH）及其他 Agent 工具在同一项目中联合开发时的信息同步协议。
> 所有 Agent 开工前都必须阅读本文档，并遵守根目录 `AGENTS.md` 中对应的强制规则。
> 规则以 `AGENTS.md` 为唯一强制入口，本文档是详细说明和操作手册。

---

## 一、协作模型

```
独立 worktree 开发
   ↓
Git 分支/commit/push = 唯一代码同步通道
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
| 事实层 | Git（分支、commit、远程） | 代码、文档、历史的最终一致来源 |

**核心原则**：
- 默认每个 Agent 使用独立 worktree；不要依赖同一目录中的未提交改动进行交接。
- Git commit 是可交接的最小单位，push 是让其他 Agent 可见的必要步骤。
- 冲突主要通过任务范围、文件范围和单一集成人预防；不能把文档状态当作文件锁。
- 任何一方开工前必须能回答三个问题：**现在在哪个分支？谁在做什么任务？上一次交接状态是什么？**

### 1.1 Worktree 示例

```bash
git fetch origin
git worktree add ../BladeProject-codex -b feature/order-export origin/develop
git worktree add ../BladeProject-dsh -b feature/customer-tags origin/develop
```

共享同一目录仅作为例外模式。若确需使用，必须先由集成人明确约定文件边界，并禁止两个 Agent 同时修改同一核心文件。

---

## 二、执行人标识

| Agent | 标识 | commit message 后缀 |
|-------|------|---------------------|
| Codex | Codex | `[codex]` |
| DeepSeek（DSH） | DeepSeek | `[dsh]` |

其他工具使用稳定、可识别的短标识，例如 Claude 使用 `[claude]`，不得省略执行人标识。

**所有 commit message 末尾必须带执行人标识**，例如：

```text
feat(order): add write-off flow [codex]
fix(catalog): pinch zoom jitter [dsh]
docs(collab): add dual-agent protocol [dsh]
```

> 说明：项目 git author 统一为 `Blade Developer`，因此用 commit message 后缀区分执行人，不改动全局 git 配置，简单可靠。

---

## 三、五条同步协议

### 协议 1：任务与文件范围认领（防撞车）

- 开工前先读 `docs/03-TASKS.md`，检查目标任务是否已被认领。
- **认领**：把任务状态改为 `⏳ 进行中（执行人：Codex / DeepSeek）`，并注明主要修改目录/文件，再 commit。
- **完成**：改为 `✅ 完成`，补上执行记录。
- **一个任务同一时刻只允许一个主 Agent 认领。**
- **一个核心文件同一时刻只允许一个主 Agent 修改。**共享 API 类型、路由、迁移索引等文件需提前分配所有权。
- 若发现对方认领后长期无进展（超过 1 个工作日），在 CHANGELOG 注明后可接替，但必须先与用户确认。

### 协议 2：开工 / 收工仪式

**开工（必做）**：

```bash
git fetch origin                     # 1. 更新远程引用，不覆盖本地工作
git status --short --branch           # 2. 确认工作区状态（干净或有明确未提交内容）
git branch -vv
git log --oneline --decorate -n 10
# 3. 读 docs/SESSION_CONTEXT.md 确认最新状态
# 4. 确认目标任务和文件范围未被认领，然后认领
# 5. 在独立 worktree 创建/切换 feature/* 分支
```

确认工作区有他人未提交内容时，停止写入并先向集成人确认，不得用 pull、reset 或 checkout 覆盖。

**收工（任务完成或会话结束，必做）**：

0. 更新 `docs/03-TASKS.md`：任务状态、执行人和文件范围
1. 更新 `docs/05-CHANGELOG.md`：变更内容 + 原因 + 影响范围 + **验证结果** + 执行人
2. 任务里程碑或会话结束时更新 `docs/SESSION_CONTEXT.md`
3. 运行 `node scripts/gen-status.mjs` 刷新状态看板（见 AGENTS.md 规则 8）
4. `git add` + `git commit`（message 带执行人后缀）
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
- 执行 Agent 不直接合并 `develop`、`release/*` 或 `master`；由指定集成人统一操作。
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

1. 暂停其中一个 Agent 对该文件的修改，由集成人确认文件所有权。
2. 集成人 fetch 对方分支，在集成分支中合并或 rebase，不在对方未提交工作区解决冲突。
3. 解决冲突时以"最新业务事实"为准：代码以 PRD + 实际运行结果为准，文档以时间新者为准。
4. 解决后运行受影响测试，commit 并标注执行人后缀。

### 4.2 任务重叠

- 若发现对方已认领同一任务，**停止并改认领其他任务**。
- 若必须协作同一任务：在 TASKS.md 认领行注明 `（协作）`，并按模块 / 文件划分边界，明确各自负责范围。

### 4.2.1 集成角色

- 执行 Agent：实现任务并提交功能分支。
- 集成人：合并到 `develop`、解决冲突、运行集成测试。
- 发布 Agent：管理 `release/*`、`master` 和 NAS 发布。
- 同一轮集成只能有一个集成人；角色可由 Codex、DeepSeek 或其他工具承担，但必须在交接信息中写明。

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
- [ ] 交接信息包含分支、commit、修改文件范围、验证命令和 push 状态
- [ ] 未提交的临时文件已说明（或在 CHANGELOG 记录原因）

### 5.1 标准交接模板

```text
任务：<任务 ID / 名称>
执行人：<Codex / DeepSeek / 其他>
分支：<feature/* / fix/*>
提交：<commit hash + subject>
修改范围：<目录或文件>
验证：<命令>：<实际结果>
状态：<已 push / 未 push；是否可合入 develop>
未提交文件：<无，或列出原因>
风险/后续：<集成测试、数据库迁移、人工验收等>
```

---

## 六、日常问答速查

| 问题 | 答案 |
|------|------|
| 我在哪个分支、有没有未提交变更？ | `git status --short --branch` |
| 对方做到哪了？ | 读 `docs/SESSION_CONTEXT.md` + `docs/03-TASKS.md` |
| 上次变更验证结果是什么？ | 读 `docs/05-CHANGELOG.md` 对应条目 |
| 这个任务我能接吗？ | 查 TASKS.md 是否已被认领（`进行中（执行人：xxx）`） |
| 提交信息怎么写？ | `<type>(<scope>): <description> [codex|dsh|其他标识]` |

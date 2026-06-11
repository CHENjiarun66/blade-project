# Git 分支与发布工作流

> 本文档规定 BladeProject 的 Git 分支、GitHub 同步、测试集成和 NAS 生产部署流程。
> 所有开发者和 AI Agent 在开始编码、合并、发布前必须阅读本文档。

---

## 一、目标

本规范解决以下问题：

- 多个功能模块并行开发时，避免互相污染。
- 功能未测试前不得进入生产分支。
- NAS 生产环境只部署稳定代码。
- GitHub 作为远程同步和备份通道，优先复用已有 `origin`。
- Agent 接手时必须能判断当前分支、提交、发布状态。

---

## 二、当前远程仓库

当前项目已配置 GitHub 远程仓库：

```text
origin = https://github.com/CHENjiarun66/blade-project.git
```

规则：

- 复用该 `origin` 作为默认上传通道。
- 推送前必须确认当前分支和待推送提交。
- 如果 GitHub 访问失败，先检查代理、网络或认证，不要擅自更换远程仓库地址。
- 如需更换远程仓库，必须先获得用户确认，并记录到 `docs/reference/DECISIONS_LOG.md` 和 `docs/05-CHANGELOG.md`。

---

## 三、分支职责

### 3.1 `master`

生产主分支。

规则：

- NAS 生产环境只部署 `master`。
- 只能放已经测试通过、准备上线或已经上线的稳定代码。
- 禁止直接在 `master` 上开发新功能。
- 禁止把未测试完成的功能合入 `master`。

### 3.2 `develop`

集成测试分支。

规则：

- 用于多个功能合并后的联调测试。
- 功能分支完成后，先合入 `develop`。
- `develop` 可以不等于生产版本，允许包含待测功能。
- 如果项目暂时没有 `develop`，第一次需要集成测试时从 `master` 创建。

### 3.3 `feature/*`

单功能开发分支。

命名建议：

```text
feature/file-center
feature/ipad-catalog
feature/order-export
feature/customer-tags
feature/nas-deploy
```

规则：

- 一个功能模块一个 `feature/*` 分支。
- Agent 默认应在 `feature/*` 分支开发，不应直接改 `master`。
- 功能拆得太大时，应继续拆成更小的 feature 分支。
- 功能未完成时，不合入 `master`。

### 3.4 `release/*`

上线候选分支。

命名建议：

```text
release/2026-06-11
release/1.2.0
```

规则：

- 用于本次上线前的最终测试。
- 可以从 `develop` 创建，也可以从 `master` 创建后挑选指定功能合入。
- 测试通过后合入 `master`。
- NAS 正式部署前，应确认 release 分支内容已经合入 `master`。

### 3.5 `hotfix/*`

生产紧急修复分支。

命名建议：

```text
hotfix/login-session
hotfix/file-preview-403
```

规则：

- 从 `master` 拉出。
- 修复完成并测试通过后，合回 `master`。
- 如果存在 `develop`，也要同步合回 `develop`，避免后续开发覆盖修复。

### 3.6 `snapshot/*`

阶段性快照分支。

用途：

- 用于保存一次大范围开发或整理后的完整状态。
- 不建议长期在 snapshot 分支上继续开发。
- snapshot 分支可以作为后续拆分 feature/release 的来源。

当前已有：

```text
snapshot/current-all-work-20260611
```

该分支是 2026-06-11 对当前大范围开发成果做分类提交后的完整快照。

---

## 四、标准开发流程

### 4.1 新功能开发

推荐流程：

```text
master
  ↓ 创建
feature/功能名
  ↓ 开发与提交
  ↓ 本地验证
develop
  ↓ 集成测试
release/日期或版本
  ↓ 验收通过
master
  ↓ NAS 部署
生产环境
```

执行规则：

1. 开发前确认当前工作区干净。
2. 从 `master` 或 `develop` 创建 `feature/*`。
3. 在 `feature/*` 中提交代码。
4. 功能完成后合入 `develop` 做集成测试。
5. 准备上线时创建 `release/*`。
6. release 验收通过后合入 `master`。
7. NAS 从 `master` 部署。

### 4.2 多功能并行开发

场景示例：

```text
feature/file-center-v1.2       开发中
feature/ipad-catalog-v1.1      准备上线
feature/order-export-v1.0      准备上线
feature/customer-follow-v1.3   开发中
```

上线时不要把所有 feature 一次性合入 `master`。

正确做法：

```text
master
  ↓ 创建 release/2026-06-11
release/2026-06-11
  ← 合入 feature/ipad-catalog-v1.1
  ← 合入 feature/order-export-v1.0
  ↓ 测试通过
master
```

未准备上线的分支继续保留：

```text
feature/file-center-v1.2
feature/customer-follow-v1.3
```

### 4.3 当前快照分支处理建议

当前分支：

```text
snapshot/current-all-work-20260611
```

建议用途：

- 作为当前完整开发成果的候选快照。
- 先在本地或测试环境完整验证。
- 如果决定整体上线，可从它创建 `release/2026-06-11`，测试通过后合入 `master`。
- 如果只想上线其中一部分功能，应从 `master` 创建 release 分支，再用 cherry-pick 或按功能分支挑选提交。

---

## 五、NAS 生产部署规则

NAS 生产环境只部署：

```text
master
```

规则：

- 不直接部署 `feature/*`。
- 不直接部署 `develop`。
- 不直接部署未经确认的 `snapshot/*`。
- 紧急情况下可以临时部署 `release/*` 做验收，但正式生产记录仍应以合入 `master` 后的提交为准。

NAS 当前生产信息见：

- [../13-NAS_PRODUCTION_OPS.md](../13-NAS_PRODUCTION_OPS.md)

---

## 六、GitHub 同步规则

### 6.1 何时 push

以下情况建议 push 到 GitHub：

- 功能分支完成一个可回滚节点。
- 准备让 NAS 从远程拉取。
- 准备创建 release 或合并 master。
- 需要让其他 Agent 或设备接手。

### 6.2 push 前检查

必须执行：

```bash
git status --short --branch
git branch -vv
git log --oneline --decorate -n 15
```

确认：

- 当前分支正确。
- 工作区没有未提交变更，或明确说明未提交内容。
- 要推送的提交符合当前分支职责。

### 6.3 远程异常处理

如果 push/fetch 失败：

1. 先检查远程地址：

   ```bash
   git remote -v
   ```

2. 再检查代理配置：

   ```bash
   git config --show-origin --get-regexp '^(http|https)\..*proxy|^http\.proxy|^https\.proxy'
   ```

3. 如果是代理端口不可用，先向用户说明，不要擅自删除全局代理。
4. 不要擅自改 `origin` 到其他仓库。

---

## 七、Agent 执行规则

### 7.1 开发前必须检查

Agent 开发前必须执行并汇报摘要：

```bash
git status --short --branch
git branch -vv
git log --oneline --decorate -n 10
```

必须说明：

- 当前分支是什么。
- 是否有未提交变更。
- 本次任务应该在哪个分支做。
- 是否需要新建 `feature/*` 分支。

### 7.2 禁止事项

Agent 不得：

- 未经确认直接在 `master` 上开发。
- 未经确认把 `feature/*` 合入 `master`。
- 未经确认 force push。
- 未经确认删除分支。
- 为了省事把多个无关功能提交到一个 commit。
- 为了清理工作区执行 `git reset --hard` 或 `git checkout -- .`。

### 7.3 提交粒度

提交应按功能边界拆分。

推荐类型：

```text
feat(file): add file center cleanup
feat(catalog): improve iPad gallery swipe
fix(auth): preserve login redirect
docs: update NAS operations guide
deploy: update NAS compose config
test(order): add quick order coverage
```

不推荐：

```text
update
fix bug
misc changes
all in one
```

### 7.4 完成后必须汇报

Agent 完成任务后必须汇报：

- 当前分支。
- 新增提交列表。
- 修改文件范围。
- 是否运行测试及结果。
- 是否已 push。
- 是否需要合入 `develop`、`release/*` 或 `master`。
- 是否存在未提交文件。

---

## 八、推荐命名

### 功能分支

```text
feature/file-center
feature/ipad-catalog
feature/order-finance
feature/customer-profile
feature/agent-gateway
feature/nas-ops
```

### 修复分支

```text
fix/file-preview-auth
fix/catalog-cache
fix/login-remember
```

### 热修复分支

```text
hotfix/prod-login-expire
hotfix/prod-file-403
```

### 发布分支

```text
release/2026-06-11
release/1.2.0
```

### 快照分支

```text
snapshot/current-all-work-20260611
snapshot/before-nas-deploy-20260611
```

---

## 九、最小可执行流程

如果不确定用完整流程，按这个最小流程执行：

```text
1. 从 master 创建 feature/xxx
2. 在 feature/xxx 开发并提交
3. 测试通过后合入 develop
4. 准备上线时合入 release/yyyy-mm-dd
5. release 验收通过后合入 master
6. NAS 只部署 master
```

如果暂时没有 `develop`：

```text
1. 从 master 创建 feature/xxx
2. 功能完成后从 master 创建 release/yyyy-mm-dd
3. 把要上线的 feature 合入 release
4. release 测试通过后合入 master
5. NAS 部署 master
```

---

## 十、维护要求

- 本文档变更必须同步记录到 [../05-CHANGELOG.md](../05-CHANGELOG.md)。
- 如果 NAS 部署分支策略变化，必须同步更新 [../13-NAS_PRODUCTION_OPS.md](../13-NAS_PRODUCTION_OPS.md)。
- 如果 Agent 开发规范变化，必须同步更新根目录 [../../AGENTS.md](../../AGENTS.md)。
- 如果文档入口变化，必须同步更新 [../01-README.md](../01-README.md) 和 [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md)。

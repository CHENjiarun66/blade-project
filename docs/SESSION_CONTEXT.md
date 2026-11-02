# 当前交接快照

> 2026-11-02。这里只保留当前状态；原阶段记录见 [归档快照](./archive/snapshots/SESSION_CONTEXT_BEFORE_2026-11-02.md)。

## 当前主线

用户最新确认：本轮优先完成客户选款改版接入；原订单、库存稳定性与真实验收暂缓，存量故障继续维护。本轮以已确认静态 v4.1 替换现有 `/catalog`，完成真实接口接入。[范围](./PROJECT_SCOPE.md)、[项目评估](./PROJECT_REVIEW_2026-11-02.md)、[代码评估](./CODE_REVIEW_2026-11-02.md) 已对齐。任务的代码完成和生产真实验收分开记录。

## 运行基线

| 项目 | 最近确认值 |
|---|---|
| Git 整理前正式/测试基线 | 盘点时 master/develop 同为 `132c9e6`；本轮选款代码从 develop `8b5fb91` 开始集成 |
| 后端生产 | `blade-backend:20261101_upload_user_hotfix`，代码 `9e0470c` |
| Web 生产 | `blade-web:20260925_draft_customer_83312c4`，代码 `83312c4` |
| NAS schema | V69 |
| 最近回归证据 | 2026-11-01 后端 880 项通过；不是本轮重新全量执行 |
| 最近发布清单 | `/volume2/blade/releases/20261101_upload_user_hotfix/release-manifest.txt` |
| TLS | 2026-11-26 11:59:59 北京时间到期；11/19 前完成续期并验证公网 |

文件列表失败和商品图片上传“无法解析用户”已修复上线，记录见 [CHANGELOG](./05-CHANGELOG.md)。本轮没有重启/发布生产应用。

## 客户选款本轮交付

- 独立工作区 `BladeProject-worktrees/catalog-wholesale-ui`，分支 `codex/catalog-wholesale-ui`，基于 develop `8b5fb91`；验证完成后集成 develop，不进入 master。
- 原 `/catalog` 已替换为批发选款页，读取现有商品/图片/库存标记，新增可选精确款号/选中 ID 查询；移交进入现有订单草稿，颜色说明保存为 rawColor，客户单价不回写商品。
- 本机隔离 MySQL/Redis，47 项前端与 123 项后端测试通过，正式构建通过；IAB 真 API 验证添加回列表、手机/iPad 尺寸、图片手势和两款 180 件/5490 元草稿，店员保存后字段仍在。Playwright 回归源码已同步，未另跑浏览器 CLI。
- 无新增 migration、生产部署或生产数据操作。生产保持上表版本；真机 Safari/软键盘和 NAS 配套发布仍待办。详细契约见 [选款说明](./21-CATALOG_WHOLESALE_DESIGN.md)。

## 暂缓的稳定性任务

`BE-STAB-001/002` 优先：身份解析和库存动作权限；随后 SQL 日志、异常诊断、发布模式和真实 JWT 回归；大页面拆分最后。三个隔离身份探针已复现库存/客户用户 4→1、拆分用户 4→null，尚未修改业务代码。

验收继续跟踪：订单/资金/库存一致性、档口用户授权与全出口矩阵、30 张真实纸单、Mac Collector→NAS 联调。待办不代表有人正在开发；认领以任务账本为准。

## 分支和工作区

分支治理后保留六条业务/工具分支及远端归档标签，见 [Git 盘点](./reference/GIT_INVENTORY_2026-11-02.md)。草稿删除分支仍有独有提交待迁移；内部驾驶舱在旧 V50 基线，不能直接合回最新应用。原 `/Users/chenjiarun/Documents/BladeProject` 工作区有用户未提交文件，本轮未修改。

## 文档使用

从 [文档中心](./01-README.md) 进入；当前规则看 PRD/现行契约，精确 API 看 Controller/DTO，精确 schema 看累计 Flyway。历史 SOW 与迁移过程保留在 [archive](./archive/README.md)，不是当前发布指令。变更和去向在 [文档目录](./DOCUMENT_CATALOG.md) 和 `document-actions-2026-11-02.json`。

交接前运行 `node scripts/check-docs.mjs` 和 `node scripts/gen-status.mjs`，同步 TASKS、CHANGELOG、STATUS 并提交。后续代码修复按任务自己的验收条件执行。

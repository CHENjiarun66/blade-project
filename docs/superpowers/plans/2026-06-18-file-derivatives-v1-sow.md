# 图片派生图第一版开发 SOW

> 日期：2026-06-18
> 分支：`feature/file-derivatives-v1`
> 任务：`BE-1012`、`BA-1007`、`BA-1028`
> 协作方式：Claude Code 负责受限范围实现和测试，Codex 负责方案锁定、Diff 审查、独立测试和最终验收。

## 一、目标

在不修改现有业务表 `fileId` 引用、不迁移或删除原图的前提下，为图片文件增加 `thumb` 和 `card` 两种派生图：

- `thumb`：长边 320px，用于小缩略图、订单图片墙、列表预览和 Catalog 胶片条。
- `card`：长边 800px，用于商品卡片、文件中心网格和 Catalog 商品卡片。
- 全屏大图、打开原文件和下载继续使用原图。

历史商品、SKU、订单和文件中心图片通过幂等补生成接口处理，不要求重新上传。

## 二、锁定架构

### 2.1 数据边界

1. 原图仍保存在 `file_storage`，业务表和绑定表仍只保存原始 `fileId`。
2. 新增 `file_derivative`，一条记录表示一个原图的一种派生版本。
3. 唯一约束为 `file_id + variant_type`；查询和写入必须同时受当前 `tenant_id` 约束。
4. 状态使用 `PENDING`、`READY`、`FAILED`，失败原因写入 `error_message`。
5. 不对派生图建立独立业务绑定，不允许业务模块保存派生图物理路径。

### 2.2 服务边界

1. 新增统一派生图服务和图片生成器边界，Controller、商品、订单和 Catalog 不直接处理图片文件。
2. 派生图文件通过现有 `FileStorageService` 抽象写入和读取，业务代码不得拼接本机绝对路径。
3. 本地存储第一版可同步生成；服务接口和状态模型必须允许后续替换为异步任务和失败重试。
4. 图片处理必须保持宽高比并处理 EXIF 方向。
5. 支持当前允许上传的 JPEG、PNG、WebP。无法解码的图片只将派生状态记为失败，原图上传仍成功。

### 2.3 上传与失败边界

1. 原图成功写入并建立 `file_storage` 记录后，再生成派生图。
2. 派生图生成异常不得回滚原图上传。
3. 同一个 `fileId + variantType` 重复生成必须幂等：READY 可跳过，FAILED/PENDING 可重试更新。
4. 禁止物理删除原图或派生图。

### 2.4 访问与权限边界

新增：

```text
GET /api/files/{id}/variant?type=thumb
GET /api/files/{id}/variant?type=card
```

要求：

1. 只允许 `thumb`、`card`，非法类型返回 400。
2. 先通过现有 `FileService.getActiveFile(id)` 校验当前租户和原图有效状态。
3. 完整复用原图预览的公开性、登录、业务权限和创建人权限逻辑。
4. `previewToken` 必须适用于 `/variant`，供浏览器 `<img>` 和 Catalog `fetch` 使用。
5. 派生图不存在、非 READY 或读取失败时，服务端返回原图；前端无需在每个页面重复实现派生图回退。
6. 原图无权访问时，派生图同样返回 403。

### 2.5 历史补生成边界

第一版提供受权限保护的手动批处理接口：

```text
POST /api/files/derivatives/backfill?limit=100
```

要求：

1. 仅处理当前登录租户、`status=1`、`file_type=IMAGE` 的原图。
2. 默认单批 100，服务端限制最大 500。
3. 已有 READY 的 `thumb/card` 跳过；缺失、PENDING、FAILED 可生成或重试。
4. 单文件失败不能中断整批，返回处理数、成功数、失败数和跳过数。
5. 使用文件中心管理权限，不新增公开调用能力。
6. 第一版不加自动定时任务，也不做全租户循环。

## 三、数据库设计

新增 Flyway `V38__file_derivative.sql`：

```sql
CREATE TABLE `file_derivative` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '派生文件ID',
  `file_id` bigint NOT NULL COMMENT '原图 file_storage.id',
  `variant_type` varchar(32) NOT NULL COMMENT 'thumb/card',
  `storage_type` varchar(32) NOT NULL DEFAULT 'local' COMMENT '存储类型',
  `storage_path` varchar(500) DEFAULT NULL COMMENT '派生文件物理路径',
  `content_type` varchar(128) DEFAULT NULL COMMENT 'MIME类型',
  `file_size` bigint NOT NULL DEFAULT 0 COMMENT '文件大小(bytes)',
  `width` int DEFAULT NULL COMMENT '宽度(px)',
  `height` int DEFAULT NULL COMMENT '高度(px)',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/READY/FAILED',
  `error_message` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_variant` (`file_id`, `variant_type`),
  KEY `idx_tenant_status` (`tenant_id`, `status`),
  KEY `idx_tenant_file` (`tenant_id`, `file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件派生图表';
```

第一版不增加 `deleted` 字段。派生图跟随原图访问状态；真实清理留给后续独立运维任务。

## 四、前端调用规则

在 `blade-admin/src/api/file.ts` 新增：

```ts
type FileVariantType = 'thumb' | 'card'
fileVariantUrl(fileId, variantType)
parseImageVariantSources(images, variantType)
```

业务接口继续返回 `fileId` 或现有原图 URL，不新增 `thumbUrl/cardUrl` DTO 字段。

| 场景 | 版本 |
|------|------|
| 商品列表、商品编辑主图区域 | `card` 或按实际显示尺寸使用 `thumb` |
| 商品图集、SKU 行缩略图 | `thumb` |
| 订单列表、订单详情、订单编辑/录入图片墙 | `thumb` |
| 文件中心网格 | `card` |
| 文件中心列表 | `thumb` |
| Catalog 商品卡片、详情主轮播 | `card` |
| Catalog 胶片条 | `thumb` |
| 全屏大图、打开原文件、下载 | 原图 `/preview` |

Catalog IndexedDB 缓存键必须区分：

```text
file:{fileId}:original
file:{fileId}:thumb
file:{fileId}:card
```

旧的 `file:{fileId}` 缓存视为历史原图缓存，不需要数据库升级或清理。

## 五、实施拆分

### SOW-1：后端底座 `BE-1012`

1. V38 表、实体、Mapper。
2. 派生图配置、类型常量/枚举。
3. 图片生成器和派生图服务。
4. 本地存储 Provider 的派生文件写入能力。
5. 上传后生成且失败不影响原图。
6. `/variant` 接口、权限复用和 `previewToken`。
7. 当前租户历史补生成接口。
8. 后端定向测试和全量测试。

### SOW-2：PC 接入 `BA-1007`

1. `fileVariantUrl` 和解析工具。
2. 商品、订单、文件中心按场景切换 `thumb/card`。
3. 原图预览和下载保持不变。
4. 前端构建验证。

### SOW-3：Catalog 接入 `BA-1028`

1. 卡片/详情主图使用 `card`。
2. 胶片条使用 `thumb`。
3. 全屏大图使用原图。
4. IndexedDB 缓存键区分版本并保持旧缓存兼容。
5. 前端构建及浏览器验证。

## 六、禁止范围

- 不修改商品、订单、SKU 业务表的图片字段结构。
- 不给业务 DTO 批量增加 `thumbUrl/cardUrl`。
- 不接七牛云、NAS、CDN。
- 不做异步消息队列、定时补生成或多租户循环。
- 不做视频封面、视频转码、WebP/AVIF 输出格式。
- 不物理删除任何原图或派生图。
- 不重构无关文件上传、绑定、清理和 Catalog 业务逻辑。

## 七、验收标准

### 后端

- 图片上传后有 READY 的 `thumb/card`；视频上传不生成。
- 派生失败时上传接口仍成功，并记录 FAILED。
- `thumb` 长边不超过 320，`card` 长边不超过 800，宽高比正确。
- EXIF 方向测试通过。
- `/variant` 对 PUBLIC、PRIVATE、业务权限、viewAll、viewOwn、跨租户和 `previewToken` 的行为与 `/preview` 一致。
- 派生缺失或失败时返回原图。
- 历史补生成接口当前租户隔离、限制批量、幂等且单文件失败不中断。
- `mvn test` 全量通过。

### 前端

- 商品、订单、文件中心、Catalog 的列表/卡片请求 `/variant`。
- 全屏大图和打开原文件仍请求 `/preview`。
- Catalog 缓存不会让 thumb/card/original 互相覆盖。
- `npm run build` 通过。

### 最终报告

Claude Code 每轮必须报告：

- 读取的文档。
- 实际修改文件。
- 实际执行命令与结果。
- `git status --short`。
- `git diff --stat`。
- 阻塞点、失败测试和超范围风险。

Codex 必须独立复核 Diff、租户过滤、权限路径、测试结果和实际文件状态后才能验收。

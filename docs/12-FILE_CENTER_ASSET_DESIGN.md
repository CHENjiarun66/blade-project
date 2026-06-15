# 文件中心与数字资产设计

> 本文档定义 BladeProject 下一阶段“文件中心 / 相册池 / 数字资产中心”的落地方案。
> 本文档是文件中心、商品/SKU 图片绑定、未绑定文件清理、客户 iPad 展示页的唯一设计入口。

---

## 一、定位

现有 [09-FILE_STORAGE_DESIGN.md](./09-FILE_STORAGE_DESIGN.md) 已完成统一上传底座：上传、预览、软删除、简单业务绑定和本地存储。

本设计在该底座上新增“资产管理层”：

```text
文件存储底座
  ↓
数字资产中心
  ↓
商品 / SKU 图片绑定
  ↓
库存聚合
  ↓
客户 iPad 展示页
```

核心原则：

```text
文件中心管资产
业务模块管业务
绑定表管关系
展示页管消费
存储层管物理位置
```

---

## 二、目标

第一阶段目标：

1. 所有上传图片和基础视频统一进入文件中心。
2. 支持自建文件夹，按业务和用户习惯管理素材。
3. 支持查看未绑定文件，手动清理或定期清理。
4. 支持将文件绑定到商品、SKU、订单、入库日志等业务对象。
5. 支持商品/SKU 图片作为客户 iPad 展示页的数据源。
6. 客户展示页可按实时库存筛选“全部 / 现货”。

长期目标：

1. 文件中心升级为通用数字资产中心，支持图片、视频、文档、压缩包等类型。
2. 后续可接七牛云、NAS、CDN、AI 识图、OCR 和分享链接。
3. 后续大视频能力可独立扩展分片上传、转码、封面和 Range 播放。

---

## 三、严格边界

### 3.1 第一版必须做

| 能力 | 范围 |
|------|------|
| 文件中心页面 | PC 后台 `/files`，网格/列表视图 |
| 文件类型 | 图片 + 基础视频文件 |
| 上传大小 | 第一版默认单文件 200MB，支持环境变量覆盖；超过该范围的大视频后续走分片上传 |
| 文件夹 | 单文件夹归属，支持新建、重命名、移动文件 |
| 未绑定管理 | 未绑定文件筛选、批量软删除 |
| 商品绑定 | 商品主图、商品图集 |
| SKU 绑定 | SKU 图片关系，不直接改 SKU 表结构展示字段 |
| 订单/入库绑定 | 支持查看和追加绑定 |
| 清理 | 未绑定临时文件软删除，软删除后延迟物理删除 |
| 客户展示页 | iPad 只读页面，展示商品/SKU 图片和库存状态 |

### 3.2 第一版不做

| 不做项 | 原因 |
|--------|------|
| 视频转码 | 需要异步任务、封面、码率和失败重试，单独立项 |
| 分片上传 | 大视频阶段再做 |
| AI 自动打标签 | 先保留标签表和接口空间，后续接入 |
| 文档在线预览 | 文件类型预留，暂不实现预览器 |
| 客户公开分享链接 | 第一版采用 iPad 登录只读账号 |
| 七牛云/NAS 切换 | 存储抽象预留，第一版继续本地存储 |
| 多文件夹同属 | 第一版一个文件只属于一个文件夹，复杂归类用标签后续承接 |
| 文件版本管理 | 后续需要替换历史素材时再做 |

### 3.3 禁止漂移规则

1. 不允许把客户展示页直接读取群晖相册或 iPad 本地相册。
2. 不允许新增业务表直接保存物理路径。
3. 不允许用 `business_type + business_id` 继续承接所有关系；新关系必须进入 `file_business_bind`。
4. 不允许为商品、SKU、订单各自再造上传模块。
5. 不允许第一版引入转码、CDN、分片上传等非必要复杂度。
6. 不允许未校验绑定关系就物理删除文件。
7. 不允许私有业务文件通过可枚举 fileId 长期公开访问。

---

## 四、数据模型

### 4.1 扩展 `file_storage`

现有 `file_storage` 继续作为资产主表，新增通用资产字段。

建议新增字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| folder_id | bigint | 文件夹 ID，可为空 |
| file_type | varchar(20) | `IMAGE` / `VIDEO` / `DOCUMENT` / `ARCHIVE` / `OTHER` |
| file_ext | varchar(20) | 扩展名 |
| file_hash | varchar(64) | 文件 hash，用于去重和迁移校验 |
| source | varchar(30) | `admin` / `mobile` / `ocr` / `import` |
| purpose | varchar(30) | `product` / `sku` / `order` / `inventory` / `temp` |
| bind_count | int | 有效绑定数量冗余 |
| visibility | varchar(20) | `PUBLIC` / `PRIVATE` |
| deleted_time | datetime | 软删除时间 |
| purged_time | datetime | 物理删除时间 |

媒体元数据第一版可先放在主表或后续拆表：

| 字段 | 类型 | 说明 |
|------|------|------|
| image_width | int | 图片宽度 |
| image_height | int | 图片高度 |
| duration_seconds | int | 视频时长 |
| cover_file_id | bigint | 视频封面 fileId |

### 4.1.1 后续扩展：图片派生图 `file_derivative`

> 状态：待开发。用于解决商品列表、订单图片墙、文件中心网格、Catalog 卡片等场景直接加载原图导致页面慢的问题。

保留原图不动，上传图片后生成派生图。业务模块仍只保存原始 `fileId`，不得保存缩略图路径。

建议新增表：

```sql
file_derivative (
  id,
  file_id,
  variant_type,
  storage_type,
  storage_path,
  content_type,
  file_size,
  width,
  height,
  status,
  tenant_id,
  create_time,
  update_time
)
```

`variant_type` 第一版建议：

| 类型 | 建议尺寸 | 使用场景 |
|------|----------|----------|
| thumb | 长边 320px 或 480px | 订单图片墙、文件中心列表小图、缩略图胶片条 |
| card | 长边 800px 或 960px | 商品列表、文件中心网格、Catalog 商品卡片 |
| original | 不入派生表 | 点击大图、全屏查看、下载原文件 |

生成规则：

1. 上传 `image/*` 原图后生成 `thumb` 和 `card`。
2. 原图保存成功后，派生图生成失败不应导致上传整体失败。
3. 派生图缺失时，前端或接口可兜底原图，保证页面可用。
4. 历史图片通过后续批量任务补生成，不在第一版上传链路中强制处理。
5. 生成缩略图时需限制超大像素图片，避免内存过高。
6. 需要处理 EXIF 方向，避免 iPhone 图片缩略图旋转错误。

接口建议：

```text
GET /api/files/{id}/variant?type=thumb
GET /api/files/{id}/variant?type=card
```

权限规则：

1. 派生图必须继承原图权限，不得因为是缩略图而公开。
2. `previewToken` 机制需要同样支持派生图接口。
3. 原图无权访问时，派生图也无权访问。

前端使用规则：

| 场景 | 图片来源 |
|------|----------|
| 商品列表主图 | `card` 或 `thumb` |
| 订单列表/编辑弹窗图片墙 | `thumb` |
| 文件中心网格 | `card` |
| 文件中心列表小图 | `thumb` |
| Catalog 商品卡片 | `card` |
| Catalog 缩略图条 | `thumb` |
| 点击大图 / 全屏查看 | 原图 `/preview` |
| 下载原文件 | 原图 `/preview` |

后续可扩展：

- 视频封面 `video_cover`。
- WebP/AVIF 派生格式。
- CDN 缓存和对象存储直出。
- 异步队列生成和失败重试。

### 4.2 新增 `file_folder`

用于用户自建文件夹。

```sql
file_folder (
  id,
  parent_id,
  folder_name,
  sort,
  tenant_id,
  create_by,
  deleted,
  create_time,
  update_time
)
```

默认虚拟入口：

| 入口 | 说明 |
|------|------|
| 全部文件 | 全部正常文件 |
| 未绑定文件 | 无有效业务绑定的文件 |
| 商品素材 | 商品主图和图集 |
| SKU 图片 | 绑定到 SKU 的图片 |
| 订单图片 | 订单相关图片 |
| 入库凭证 | 入库日志图片 |
| OCR 原图 | 后续 OCR 单据图片 |
| 视频 | `file_type=VIDEO` |
| 回收站 | 软删除未物理清理文件 |

### 4.3 新增 `file_business_bind`

核心绑定表，支持一个文件绑定多个业务对象。

```sql
file_business_bind (
  id,
  file_id,
  business_type,
  business_id,
  bind_role,
  sort,
  is_primary,
  tenant_id,
  create_by,
  create_time,
  deleted
)
```

`business_type` 建议值：

| 值 | 说明 |
|----|------|
| product | 商品 |
| sku | SKU |
| order | 订单 |
| inventory_log | 入库日志 |
| ocr_document | OCR 单据 |

`bind_role` 建议值：

| 值 | 说明 |
|----|------|
| main | 主图 |
| gallery | 图集 |
| sku_image | SKU 图片 |
| receipt | 凭证 |
| source | 原始单据 |
| attachment | 普通附件 |

商品主图兼容规则：

1. `product.image_url` 继续保存商品主图 fileId，兼容现有页面。
2. 同时写入 `file_business_bind`：`business_type=product`、`bind_role=main`、`is_primary=1`。
3. 新商品图集、SKU 图集不新增业务字段，统一走绑定表。

### 4.4 新增操作与清理日志

建议新增：

```sql
file_operation_log
file_cleanup_log
```

至少记录：

| 操作 | 是否记录 |
|------|----------|
| 上传 | 是 |
| 绑定 | 是 |
| 解绑 | 是 |
| 移动文件夹 | 是 |
| 软删除 | 是 |
| 恢复 | 是 |
| 物理删除 | 是 |
| 迁移 | 后续 |

---

## 五、后端 API

### 5.1 文件中心 API

| API | 方法 | 说明 |
|-----|------|------|
| `/api/files` | GET | 文件分页列表 |
| `/api/files/{id}` | GET | 文件详情 |
| `/api/files/{id}` | PATCH | 修改文件名、文件夹、用途、可见性 |
| `/api/files/batch-delete` | POST | 批量软删除 |
| `/api/files/{id}/restore` | POST | 恢复软删除 |
| `/api/files/{id}/move` | POST | 移动文件夹 |
| `/api/files/stats` | GET | 文件数量、容量、未绑定数量 |

列表筛选参数：

| 参数 | 说明 |
|------|------|
| keyword | 文件名、fileId、业务对象关键字 |
| folderId | 文件夹 |
| fileType | IMAGE / VIDEO / DOCUMENT |
| businessType | product / sku / order / inventory_log |
| bound | true / false |
| purpose | 文件用途 |
| createBy | 上传人 |
| startDate/endDate | 上传时间 |
| status | 正常 / 回收站 |

### 5.2 文件夹 API

| API | 方法 | 说明 |
|-----|------|------|
| `/api/file-folders/tree` | GET | 文件夹树 |
| `/api/file-folders` | POST | 新建文件夹 |
| `/api/file-folders/{id}` | PUT | 重命名、移动、排序 |
| `/api/file-folders/{id}` | DELETE | 删除空文件夹或移动文件到未归档 |

### 5.3 绑定 API

| API | 方法 | 说明 |
|-----|------|------|
| `/api/files/{id}/bindings` | GET | 文件绑定关系 |
| `/api/files/bindings` | POST | 批量绑定 |
| `/api/files/bindings/{id}` | DELETE | 解除绑定 |
| `/api/products/{id}/files` | GET | 商品主图/图集 |
| `/api/products/{id}/files` | PUT | 设置商品主图、图集排序 |
| `/api/products/skus/{skuId}/files` | GET | SKU 图片 |
| `/api/products/skus/{skuId}/files` | PUT | 设置 SKU 图片 |

### 5.4 客户展示 API

客户展示页不直接读文件表，而读聚合后的 catalog API。

| API | 方法 | 说明 |
|-----|------|------|
| `/api/catalog/products` | GET | 商品/SKU 展示列表 |
| `/api/catalog/products/{id}` | GET | 商品展示详情 |
| `/api/catalog/filters` | GET | 分类、颜色、尺码筛选项 |

列表参数：

| 参数 | 说明 |
|------|------|
| keyword | 商品编码、商品名、SKU |
| categoryId | 商品分类 |
| colorId | 颜色 |
| sizeId | 尺码 |
| stockMode | `all` / `in_stock` |
| hasImage | 是否有图 |
| page/size | 分页 |

库存口径：

```text
availableQty = quantity - reservedQty - globalReservedQty
```

客户展示第一版只展示：

```text
有现货 / 暂无现货
```

不展示真实库存数量，避免向客户暴露库存细节。

---

## 六、PC 文件中心页面

路由：

```text
/files
```

菜单名称：

```text
文件中心
```

页面布局：

```text
左侧：文件夹树 + 虚拟入口
右侧顶部：搜索、筛选、上传、批量绑定、批量删除、视图切换
右侧主体：网格视图 / 列表视图
右侧抽屉：文件详情、绑定关系、操作日志
```

第一版组件建议：

| 组件 | 说明 |
|------|------|
| FileGrid | 图片/视频网格、选择态 |
| FileListTable | 文件表格 |
| FileUploadTile | 上传入口 |
| FilePreviewDialog | 大图/视频预览 |
| BusinessBindDialog | 绑定商品/SKU/订单/入库日志 |
| ProductSkuPicker | 商品 + SKU 矩阵选择 |

---

## 七、iPad 客户展示页

页面定位：

```text
现货选款相册
```

建议路由：

```text
/catalog
```

也可命名：

```text
/showroom
```

第一版访问模式：

1. iPad 登录只读账号。
2. 只授予 catalog 相关权限。
3. 页面为静态前端 + 动态 API 数据。

页面结构：

```text
顶部：搜索款号 / 商品名 / SKU
筛选：全部 / 现货 / 有图 / 分类 / 颜色 / 尺码
主体：商品相册网格
详情：大图轮播 + SKU 矩阵 + 有现货/暂无现货
```

已锁定第一版视觉与交互方向：

| 项目 | 设计要求 |
|------|----------|
| 视觉氛围 | 参考 Stitch `Modest Wholesale Excellence` 的 quiet luxury 方向：米白背景、深炭黑文字、少量金色点缀、轻边框、低阴影 |
| 页面属性 | 客户选款相册，不是后台管理页；不得出现后台侧边栏、图表、管理入口 |
| 横屏布局 | 商品网格 + 右侧固定详情面板并排；商品网格优先 3 列，详情面板展示大图、标签和 SKU 现货矩阵 |
| 竖屏布局 | 商品网格优先 2 列；商品详情使用底部抽屉或全屏详情，不强行挤压为左右并排 |
| 三层浏览 | 第一层商品网格；第二层商品详情；第三层全屏大图看图模式 |
| 图片源分层 | 商品网格只展示商品主图；详情面板/抽屉顶部轮播展示商品图 + 所有 SKU 图片全集；点击详情大图进入全屏时只浏览商品图片集，不把 SKU 图片混入商品大图集 |
| 全屏大图 | 深色背景覆盖全屏，支持左右切图、关闭、缩略图胶片条、款号/图片序号、现货状态；预留双指缩放看花色细节 |
| 库存展示 | 只显示“有现货 / 暂无现货”，不显示真实库存数量 |
| 第一版身份 | 默认游客/散客模式；客户选择、扫码识别、行为埋点和选款清单进入后续阶段 |

iPad 体验要求：

| 要求 | 说明 |
|------|------|
| 横屏优先 | 商品网格适配 iPad 横屏 |
| 触控友好 | 大按钮、大间距、卡片易点 |
| 图片懒加载 | 避免一次加载过多图片 |
| PWA 主屏幕体验 | `/catalog` 已配置 manifest、iOS 主屏 meta 和应用图标；iPad Safari 可添加到主屏幕后以独立窗口打开 |
| 不展示后台信息 | 不展示成本、毛利、真实库存数量 |

真机调试方式：

1. Mac 运行后端和 `blade-admin` 前端，前端监听 `0.0.0.0:5777`。
2. iPad 与 Mac 处于同一 Wi-Fi，访问 `http://<Mac局域网IP>:5777/catalog`。
3. Safari 分享菜单选择“添加到主屏幕”，桌面图标名称为“现货选款”。
4. 从桌面图标进入后，页面按 standalone Web App 方式打开，不显示 Safari 标签栏和地址栏。

第一版边界：本地 HTTP 局域网调试只要求主屏独立窗口体验；离线缓存、推送、正式 HTTPS 域名、MDM 部署和客户公开分享链接不在本轮范围。

---

## 八、权限与安全

新增权限建议：

| 权限码 | 说明 |
|--------|------|
| menu:file | 文件中心菜单 |
| btn:file:upload | 上传文件 |
| btn:file:delete | 删除文件 |
| btn:file:bind | 绑定业务对象 |
| btn:file:unbind | 解除绑定 |
| btn:file:batch | 批量操作 |
| btn:file:viewAll | 查看全部文件 |
| btn:file:viewOwn | 只看自己上传 |
| btn:file:cleanup | 文件清理 |
| menu:catalog | 客户展示页 |
| data:catalog:view | 查看客户展示数据 |

安全要求：

1. 商品公开图可设置 `PUBLIC`。
2. 订单图片、入库凭证、OCR 原图默认 `PRIVATE`。
3. 私有文件预览必须校验登录、租户和业务权限。
4. 绑定接口必须校验文件归属、业务对象归属、租户一致。
5. 删除前必须检查有效绑定关系。
6. 后续分享链接必须使用短期 token，不直接暴露永久 fileId。

---

## 九、清理策略

文件状态建议：

| 状态 | 说明 |
|------|------|
| NORMAL | 正常 |
| TEMP | 临时上传 |
| DELETED | 软删除 |
| PURGED | 已物理删除 |

清理规则：

```text
未绑定 + 未归档 + 上传超过 7 天
  → 自动软删除

软删除超过 30 天 + 无有效绑定
  → 物理删除

业务凭证类文件
  → 默认不自动物理删除，除非管理员手动确认或保留期策略明确
```

未绑定判定必须基于 `file_business_bind`：

```sql
NOT EXISTS (
  SELECT 1
  FROM file_business_bind b
  WHERE b.file_id = file_storage.id
    AND b.deleted = 0
)
```

不能只用 `business_id IS NULL`。

---

## 十、视频与其他文件类型

第一版：

| 类型 | 支持方式 |
|------|----------|
| 图片 | 上传、缩略图/预览、绑定、展示 |
| 视频 | 上传、基础预览、文件类型筛选 |
| 文档 | 数据模型预留，不做在线预览 |
| 压缩包 | 数据模型预留，不做业务入口 |

视频后续单独立项：

1. 分片上传。
2. 断点续传。
3. 视频封面。
4. Range 请求。
5. 转码队列。
6. 低码率预览版本。

---

## 十一、外部存储兼容

继续沿用 `FileStorageService` 抽象。

| 存储 | 第一版状态 | 后续扩展 |
|------|------------|----------|
| local | 使用中 | 继续作为默认 |
| qiniu | 预留 | 新增 QiniuFileStorageService |
| nas | 预留 | 新增 NasFileStorageService |
| cdn | 预留 | 签名 URL + 缓存刷新 |

业务模块只保存 `fileId`，不得保存物理路径。

迁移策略：

1. `storage_type` 区分旧文件和新文件。
2. 支持读旧写新。
3. 通过 `file_hash` 校验迁移结果。
4. 迁移完成后再物理清理旧存储。

---

## 十二、开发阶段

### Phase A：文件中心基础

1. 扩展 `file_storage`。
2. 新增 `file_folder`。
3. 新增文件分页、详情、移动、批量删除 API。
4. 新增 PC `/files` 页面。
5. 支持网格/列表、上传、预览、删除、未绑定筛选。

### Phase B：业务绑定

1. 新增 `file_business_bind`。
2. 改造绑定服务，现有 `business_type/business_id` 作为兼容主归属。
3. 支持商品主图、商品图集、SKU 图片绑定。
4. 支持订单图片和入库凭证追加绑定。

### Phase C：治理与清理

1. 已新增操作日志和清理日志基础表。
2. 已新增未绑定临时文件治理后端入口：统计候选、软删除候选、标记过期软删除元数据。
3. 已新增第一版配置控制清理调度：默认关闭，按 `blade.file.cleanup.tenant-id` 处理单租户；不做真实物理删除。
4. 待新增回收站和恢复。
5. 待补全租户遍历、物理删除审批/保护策略和私有文件预览权限收口。

### Phase D：客户展示页 MVP

1. 新增 catalog 聚合接口。
2. 新增 iPad `/catalog` 或 `/showroom` 页面。
3. 支持全部/现货筛选。
4. 商品相册网格 + 详情大图 + SKU 矩阵。
5. 只展示“有现货 / 暂无现货”。

### Phase E：增强能力

1. 分类、颜色、尺码筛选。
2. 展示排序。
3. PWA 主屏幕体验。
4. 视频基础展示。
5. 后续七牛云/NAS/CDN。

### Phase F：图片派生图性能优化（待开发）

1. 新增 `file_derivative` 表和实体/Mapper。
2. 图片上传后生成 `thumb`、`card` 两种派生图。
3. 新增 `/api/files/{id}/variant?type=thumb|card` 接口，权限复用原图预览权限。
4. PC 文件中心、商品列表、订单图片墙优先使用派生图。
5. Catalog 卡片和缩略图条优先使用派生图，全屏大图仍使用原图。
6. 历史图片提供批量补生成任务或管理入口。
7. 派生图缺失时兜底原图，避免页面不可用。

---

## 十三、与现有模块关系

| 模块 | 关系 |
|------|------|
| 09 文件存储 | 上传和存储底座，继续保留 |
| 商品模块 | 继续保留 `product.image_url`，新增图集和 SKU 绑定关系 |
| 订单模块 | 订单图片继续保存 fileId JSON，文件中心可查看和追加 |
| 库存模块 | 入库凭证继续保存 fileId JSON，文件中心可查看和追加 |
| OCR | 后续 OCR 原图进入文件中心 |
| 客户展示页 | 只读消费商品/SKU 图片和库存聚合 |
| Agent | 后续可读取商品/SKU 图片事实，但第一版不纳入 |

---

## 十四、验收标准

文件中心 MVP 完成标准：

1. 上传文件后可在文件中心列表看到。
2. 创建订单/商品/入库时上传的文件也能进入文件中心。
3. 未绑定文件能被筛选出来。
4. 文件可移动到用户自建文件夹。
5. 文件可绑定到商品和 SKU。
6. 商品主图兼容现有商品列表展示。
7. 删除文件前能识别是否存在有效绑定。
8. 自动清理只处理未绑定、未归档、超过保留期的临时文件。

客户展示页 MVP 完成标准：

1. iPad 可打开展示页。
2. 展示商品主图、商品图集和 SKU 图片。
3. 支持“全部 / 现货”筛选。
4. 现货口径使用系统可用库存。
5. 不展示成本、毛利、真实库存数量。
6. 图片来自文件中心绑定关系，不读取外部相册。

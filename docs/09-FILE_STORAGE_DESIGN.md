# 统一文件存储设计

> 本文档定义 BladeProject 图片/附件上传、存储、访问和业务绑定的统一方案。
> 当前决策：先搭建统一入口和业务 fileId 保存机制，第一版使用本地存储，后续可切换七牛云或 NAS。
>
> 后续“文件中心 / 相册池 / 数字资产中心 / 客户 iPad 展示页”的落地方案见：[12-FILE_CENTER_ASSET_DESIGN.md](./12-FILE_CENTER_ASSET_DESIGN.md)。本文只定义上传与存储底座，不承接文件夹、资产治理、多业务绑定和客户展示页范围。

---

## 一、目标

当前订单、库存入库、OCR 拍照录单都需要图片能力。已有代码只具备图片字段和部分前端预览，尚未形成真正的上传和长期保存链路。

本方案目标：

1. 前端统一通过后端上传文件，不直接对接本地目录、七牛云或 NAS。
2. 业务表只保存 `fileId` 列表，不保存具体物理路径。
3. 第一版先存本地，降低开发和部署复杂度。
4. 后续切换七牛云、NAS 或其他对象存储时，不改订单、库存、OCR 等业务模块。

---

## 二、总体架构

```text
PC 管理端 / 移动端
    ↓
POST /api/files/upload
    ↓
FileController
    ↓
FileService
    ↓
FileStorageService 接口
    ↓
第一版：LocalFileStorageService
后续：QiniuFileStorageService / NasFileStorageService
    ↓
file_storage 元数据表
    ↓
订单 / 入库 / 商品 / OCR 只保存 fileId
```

业务保存示例：

```json
["101", "102", "103"]
```

### 2.1 当前统一接入范围

| 端 | 业务入口 | 业务字段 | 保存形式 | 状态 |
|----|----------|----------|----------|------|
| PC 管理端 | 新建订单图片 | `sale_order.images` | fileId JSON 数组字符串 | ✅ 已接入 |
| PC 管理端 | 编辑订单图片 | `sale_order.images` | fileId JSON 数组字符串 | ✅ 已接入 |
| PC 管理端 | 订单详情图片预览 | `/api/files/{id}/preview` | fileId 预览地址 | ✅ 已接入 |
| PC 管理端 | 入库凭证图片 | `inventory_log.images` | fileId JSON 数组字符串 | ✅ 已接入 |
| PC 管理端 | 商品主图 | `product.image_url` | 单个 fileId 字符串 | ✅ 已接入 |
| 移动端 | 入库凭证图片 | `inventory_log.images` | fileId JSON 数组字符串 | ✅ 已接入 |
| 后端 | OCR 原始单据图片 | `file_storage` + 后续 OCR 业务表 | fileId | ⏳ OCR 页面/识别服务未开发，统一入口已可复用 |

---

## 三、数据库设计

新增 `file_storage` 表：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键，业务表保存的 fileId |
| file_key | varchar | 逻辑文件 key，如 `order/2026/05/20/uuid.jpg` |
| original_name | varchar | 用户上传时的原始文件名 |
| file_name | varchar | 系统生成后的文件名 |
| content_type | varchar | MIME 类型，如 `image/jpeg` |
| file_size | bigint | 文件大小，单位 byte |
| storage_type | varchar | 存储类型：`local` / `qiniu` / `nas` |
| storage_path | varchar | 实际存储路径或对象 key |
| access_url | varchar | 可选访问地址；本地存储可为空或保存预览地址 |
| business_type | varchar | 业务类型：`order` / `inventory` / `ocr` / `product` |
| business_id | bigint | 业务 ID，可为空；订单创建前上传时先为空 |
| status | tinyint | 状态：1 正常，0 禁用/软删除 |
| tenant_id | bigint | 租户 ID |
| create_by | bigint | 上传人 ID |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

业务表保存规则：

| 业务表/字段 | 保存内容 | 说明 |
|-------------|----------|------|
| sale_order.images | JSON 数组字符串 | 保存订单图片 fileId |
| inventory_log.images | JSON 数组字符串 | 保存入库凭证 fileId |
| product.image_url | 单个 fileId 字符串 | 保存商品主图 fileId，历史 URL 可继续展示 |

---

## 四、后端接口设计

### 4.1 上传文件

```http
POST /api/files/upload
Content-Type: multipart/form-data
```

参数：

| 参数 | 必填 | 说明 |
|------|------|------|
| file | 是 | 上传文件 |
| businessType | 是 | `order` / `inventory` / `ocr` / `product` |
| businessId | 否 | 业务 ID，创建业务前上传时可为空 |

返回：

```json
{
  "id": 101,
  "originalName": "receipt.jpg",
  "contentType": "image/jpeg",
  "fileSize": 238120,
  "url": "/api/files/101/preview"
}
```

### 4.2 预览文件

```http
GET /api/files/{id}/preview
```

后端根据 `fileId` 查询 `file_storage`，再由当前存储实现读取文件并输出响应。

### 4.3 删除文件

```http
DELETE /api/files/{id}
```

第一版采用软删除：`status=0`，实际文件暂不物理删除，避免误删订单和入库凭证。

### 4.4 绑定业务

```http
PUT /api/files/bind
```

用于订单创建成功后，将创建前上传、`business_id` 为空的文件绑定到订单：

```json
{
  "businessType": "order",
  "businessId": 123,
  "fileIds": [101, 102]
}
```

也可以由订单服务在创建成功后内部调用文件服务完成绑定。

---

## 五、本地存储第一版

配置建议：

```yaml
blade:
  file:
    storage-type: local
    local-base-path: ./uploads
    preview-url-prefix: /api/files
    max-size-mb: 200
    allowed-types:
      - image/jpeg
      - image/png
      - image/webp
      - video/mp4
      - video/webm
      - video/quicktime
```

Spring multipart 上限需与业务上限保持一致，默认配置为：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 200MB
      max-request-size: 220MB
```

若通过 NAS Nginx 或其他反向代理访问，代理层也必须放开请求体大小：

```nginx
client_max_body_size 220m;
```

开发期目录：

```text
blade-backend/uploads/
  order/
    2026/05/20/uuid.jpg
  inventory/
    2026/05/20/uuid.jpg
  ocr/
    2026/05/20/uuid.jpg
```

生产部署建议改为项目外目录：

```text
/data/blade/uploads
```

这样代码更新、打包和服务重启不会影响已上传图片。

当前默认配置为：

```yaml
blade.file.local-base-path: ${BLADE_FILE_LOCAL_BASE_PATH:uploads}
```

如果从 `blade-backend` 目录启动服务，默认实际目录为：

```text
blade-backend/uploads/
```

---

## 六、存储接口抽象

后端实现统一接口：

```java
public interface FileStorageService {
    StoredFile store(MultipartFile file, String businessType);
    Resource load(String storagePath);
    void delete(String storagePath);
    String getStorageType();
}
```

第一版实现：

```text
LocalFileStorageService
```

后续可扩展：

```text
QiniuFileStorageService
NasFileStorageService
```

切换存储时只改配置和新增实现，业务模块继续使用 `fileId`。

---

## 七、业务接入顺序

1. 新增 `file_storage` 表和文件模块。
2. 实现本地上传、预览、软删除、业务绑定接口。
3. PC 新建订单页改为真实上传图片，订单保存 `fileId` 数组。
4. 订单详情页和订单编辑页按 `fileId` 预览、追加和删除图片。
5. PC/移动端入库页接入文件上传，入库接口保存 `fileId` 数组。
6. PC 商品主图接入文件上传，商品保存单个 `fileId`。
7. OCR 拍照录单复用同一上传接口保存原始单据图片。

---

## 八、历史数据兼容

现有图片字段可能存在三类数据：

| 数据形式 | 处理方式 |
|----------|----------|
| `["101","102"]` | 按 fileId 生成预览地址 |
| `"101"` | 单图字段按 fileId 生成预览地址，主要用于商品主图 |
| `["https://..."]` | 临时按原 URL 展示 |
| `"https://..."` | 单图字段临时按原 URL 展示 |
| `["blob:..."]` | 浏览器临时地址，视为无效历史数据，可忽略或提示 |

功能稳定后，可清理历史 `blob:` 数据。

---

## 九、当前开发进度

本方案纳入新的开发进度，第一阶段任务如下：

| 任务 ID | 任务 | 状态 | 说明 |
|---------|------|------|------|
| BE-901 | 统一文件表与迁移脚本 | ✅ 完成 | 新增 `file_storage` 表 |
| BE-902 | 本地文件上传/预览/删除接口 | ✅ 完成 | `POST /api/files/upload`、`GET /api/files/{id}/preview`、`DELETE /api/files/{id}` |
| BE-903 | 文件业务绑定接口 | ✅ 完成 | 支持上传后绑定订单、入库、OCR 等业务 |
| BE-904 | 订单图片 fileId 保存改造 | ✅ 完成 | `sale_order.images` 改为保存 fileId JSON 数组 |
| BE-905 | 入库凭证 fileId 保存改造 | ✅ 完成 | `inventory_log.images` 改为保存 fileId JSON 数组 |
| BE-906 | 商品主图 fileId 保存改造 | ✅ 完成 | `product.image_url` 改为保存单个 fileId，兼容历史 URL |
| BA-901 | PC 订单图片上传接入 | ✅ 完成 | 新建/编辑/详情页接入统一文件接口 |
| BA-902 | PC 商品主图上传接入 | ✅ 完成 | 商品新建/编辑/列表接入统一文件接口 |
| BA-903 | PC 入库凭证上传接入 | ✅ 完成 | 入库弹窗接入统一文件接口并保存 fileId |
| FE-901 | 移动端入库图片上传接入 | ✅ 完成 | 入库页接入统一文件接口并保存 fileId |

import client from './client'

/** 统一分页响应 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 文件上传响应 */
export interface FileUploadVO {
  id: number
  originalName: string
  contentType: string
  fileSize: number
  url: string
  fileType?: string
  fileExt?: string
}

/** 文件中心 VO (与后端 FileVO 对齐) */
export interface FileVO {
  id: number
  fileKey: string
  originalName: string
  fileName: string
  contentType: string
  fileSize: number
  accessUrl: string
  folderId: number | null
  fileType: string
  fileExt: string
  source: string
  purpose: string
  businessType: string
  businessId: number | null
  bindCount: number
  visibility: string
  imageWidth: number | null
  imageHeight: number | null
  durationSeconds: number | null
  coverFileId: number | null
  status: number
  bound: boolean
  createBy: number
  createTime: string
  updateTime: string
  deletedTime: string | null
}

/** 文件分页查询参数 */
export interface FilePageParams {
  current?: number
  size?: number
  keyword?: string
  folderId?: number | null
  fileType?: string
  businessType?: string
  bound?: boolean
  purpose?: string
  createBy?: number
  startDate?: string
  endDate?: string
  status?: number
}

/** 文件夹 VO */
export interface FileFolderVO {
  id: number
  parentId: number | null
  folderName: string
  sort: number
  children: FileFolderVO[]
}

/** 文件绑定 VO */
export interface FileBindingVO {
  id: number
  fileId: number
  businessType: string
  businessId: number
  bindRole: string
  isPrimary: number
  sort: number
  createTime: string
}

/** 文件绑定创建 DTO */
export interface FileBindingCreateDTO {
  fileIds: number[]
  businessType: string
  businessId: number
  bindRole: string
  isPrimary?: number
}

/** 批量删除 DTO */
export interface FileBatchDeleteDTO {
  fileIds: number[]
}

/** 批量移动 DTO */
export interface FileBatchMoveDTO {
  fileIds: number[]
  folderId: number | null
}

/** 文件夹创建 DTO */
export interface FileFolderCreateDTO {
  folderName: string
  parentId?: number | null
  sort?: number
}

/** 未绑定候选文件 VO */
export interface UnboundCandidateVO {
  candidateCount: number
  retentionDays: number
}

export function filePreviewUrl(id: number | string) {
  const token = localStorage.getItem('token')
  const query = token ? `?previewToken=${encodeURIComponent(token)}` : ''
  return `/api/files/${id}/preview${query}`
}

// ========== 文件中心 API ==========

/** 分页查询文件列表 */
export function getFilePage(params: FilePageParams) {
  return client.get('/files', { params }) as Promise<{ code: number; data: PageResult<FileVO>; message: string }>
}

/** 获取文件详情 */
export function getFileDetail(id: number) {
  return client.get(`/files/${id}`) as Promise<{ code: number; data: FileVO }>
}

// ========== 批量操作 API ==========

/** 批量软删除文件（仅 POST /api/files/batch-delete） */
export function batchDeleteFiles(fileIds: number[]) {
  return client.post('/files/batch-delete', { fileIds } as FileBatchDeleteDTO) as Promise<{ code: number; data: void; message: string }>
}

/** 批量移动文件到文件夹或未归档 */
export function batchMoveFiles(fileIds: number[], folderId: number | null) {
  return client.post('/files/batch-move', { fileIds, folderId } as FileBatchMoveDTO) as Promise<{ code: number; data: void; message: string }>
}

// ========== 绑定 API ==========

/** 获取文件的有效绑定关系 */
export function getFileBindings(fileId: number) {
  return client.get(`/files/${fileId}/bindings`) as Promise<{ code: number; data: FileBindingVO[]; message: string }>
}

/** 批量创建文件绑定 */
export function createFileBindings(data: FileBindingCreateDTO) {
  return client.post('/files/bindings', data) as Promise<{ code: number; data: void; message: string }>
}

/** 删除单个文件绑定（软删除） */
export function deleteFileBinding(bindingId: number) {
  return client.delete(`/files/bindings/${bindingId}`) as Promise<{ code: number; data: void; message: string }>
}

// ========== 清理 API ==========

/** 获取未绑定候选文件数量 */
export function getUnboundCandidates(days: number = 7) {
  return client.get('/files/cleanup/unbound-candidates', { params: { days } }) as Promise<{ code: number; data: UnboundCandidateVO; message: string }>
}

/** 软删除未绑定超期文件 */
export function softDeleteUnbound(days: number = 7) {
  return client.post('/files/cleanup/soft-delete-unbound', null, { params: { days } }) as Promise<{ code: number; data: { processedCount: number; retentionDays: number }; message: string }>
}

// ========== 文件夹 API ==========

/** 获取文件夹树 */
export function getFileFoldersTree() {
  return client.get('/file-folders/tree') as Promise<{ code: number; data: FileFolderVO[] }>
}

/** 创建文件夹 */
export function createFileFolder(data: FileFolderCreateDTO) {
  return client.post('/file-folders', data) as Promise<{ code: number; data: FileFolderVO; message: string }>
}

// ========== 文件大小格式化 ==========

export function formatFileSize(bytes: number | null | undefined): string {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

// ========== 文件类型判断 ==========

export function isImageFile(fileType: string | null | undefined): boolean {
  return fileType === 'IMAGE'
}

export function isVideoFile(fileType: string | null | undefined): boolean {
  return fileType === 'VIDEO'
}

export function getFileTypeLabel(fileType: string | null | undefined): string {
  switch (fileType) {
    case 'IMAGE': return '图片'
    case 'VIDEO': return '视频'
    case 'DOCUMENT': return '文档'
    case 'ARCHIVE': return '压缩包'
    default: return '其他'
  }
}

export function getBusinessTypeLabel(businessType: string | null | undefined): string {
  switch (businessType) {
    case 'product': return '商品'
    case 'sku': return 'SKU'
    case 'order': return '订单'
    case 'inventory_log': return '入库'
    case 'ocr_document': return 'OCR'
    default: return businessType || '-'
  }
}

// ========== 以下为原有工具函数 (兼容业务模块) ==========

function toImageSource(value: unknown) {
  const source = String(value || '')
  if (!source || source.startsWith('blob:')) return ''
  return /^\d+$/.test(source) ? filePreviewUrl(source) : source
}

function parseLegacyImageValues(images: string) {
  return images
    .split(',')
    .map((value) => value.trim())
    .filter((value) => value && !value.startsWith('blob:'))
}

export function parseImageSources(images?: string): string[] {
  if (!images) return []
  try {
    const values = JSON.parse(images)
    const sources = Array.isArray(values) ? values : [values]
    return sources.map(toImageSource).filter(Boolean)
  } catch {
    return parseLegacyImageValues(images).map(toImageSource).filter(Boolean)
  }
}

export function parseImageValues(images?: string): string[] {
  if (!images) return []
  try {
    const values = JSON.parse(images)
    const imageValues = Array.isArray(values) ? values : [values]
    return imageValues.map((value) => String(value || '')).filter((value) => value && !value.startsWith('blob:'))
  } catch {
    return parseLegacyImageValues(images)
  }
}

export function parseFileIds(images?: string): string[] {
  if (!images) return []
  try {
    const values = JSON.parse(images)
    const fileIds = Array.isArray(values) ? values : [values]
    return fileIds.map((value) => String(value)).filter((value) => /^\d+$/.test(value))
  } catch {
    return /^\d+$/.test(images) ? [images] : []
  }
}

export async function uploadFile(file: File, businessType: string, businessId?: number) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('businessType', businessType)
  if (businessId) {
    formData.append('businessId', String(businessId))
  }
  return client.post<FileUploadVO>('/files/upload', formData) as any
}

import client from './client'

export interface ApiResponse<T> {
  code: number
  data: T
  message: string
}

export interface InventoryVO {
  id: number
  skuId: number
  skuCode: string
  productName: string
  categoryId: number
  categoryName: string
  price: number
  colorName: string
  sizeName: string
  warehouseId: number
  warehouseName: string
  quantity: number
  reservedQty: number
  availableQty: number
  alertThreshold: number
  alertStatus: string
  updateTime: string
}

export interface InventoryPageResponse {
  records: InventoryVO[]
  total: number
  size: number
  current: number
  pages: number
}

export interface InventoryPageDTO {
  current?: number
  size?: number
  keyword?: string
  warehouseId?: number
  alertStatus?: string
}

// 入库项
export interface InventoryInItemDTO {
  skuId: number
  quantity: number
  remark?: string
}

// 入库请求
export interface InventoryInDTO {
  warehouseId: number
  items: InventoryInItemDTO[]
  remark?: string
  images?: string[]
}

// 出库项
export interface InventoryOutItemDTO {
  skuId: number
  quantity: number
  reason?: string
}

// 出库请求
export interface InventoryOutDTO {
  warehouseId: number
  source?: string
  items: InventoryOutItemDTO[]
  remark?: string
}

// 调整项
export interface InventoryAdjustItemDTO {
  skuId: number
  quantity: number
  reason?: string
}

// 调整请求
export interface InventoryAdjustDTO {
  warehouseId: number
  reason: string
  items: InventoryAdjustItemDTO[]
  remark?: string
}

// 库存记录
export interface InventoryLogVO {
  id: number
  skuId: number
  skuCode: string
  warehouseId: number
  warehouseName: string
  changeType: string
  changeTypeName: string
  changeQty: number
  beforeQty: number
  afterQty: number
  orderId?: number
  referenceNo?: string
  supplierId?: number
  supplierName?: string
  operatorId: number
  operatorName: string
  remark?: string
  images?: string
  createTime: string
}

// 仓库
export interface WarehouseVO {
  id: number
  warehouseName: string
  address?: string
  contact?: string
  phone?: string
  remark?: string
  status: number
  createTime: string
}

// 库存列表
export function getInventoryPage(params?: InventoryPageDTO) {
  return client.get<ApiResponse<InventoryPageResponse>>('/inventory', { params }) as any
}

// 库存详情
export function getInventoryById(id: number) {
  return client.get<ApiResponse<InventoryVO>>(`/inventory/${id}`) as any
}

// 按仓库查询库存
export function getInventoryByWarehouse(warehouseId: number) {
  return client.get<ApiResponse<InventoryVO[]>>(`/inventory/warehouse/${warehouseId}`) as any
}

// 库存预警列表
export function getInventoryAlerts(warehouseId?: number) {
  return client.get<ApiResponse<InventoryVO[]>>('/inventory/alerts', { params: { warehouseId } }) as any
}

// 入库
export function stockIn(data: InventoryInDTO) {
  return client.post('/inventory/in', data) as any
}

// 出库
export function stockOut(data: InventoryOutDTO) {
  return client.post('/inventory/out', data) as any
}

// 库存调整
export function adjustInventory(data: InventoryAdjustDTO) {
  return client.post('/inventory/adjust', data) as any
}

// 预留锁定
export function reserveInventory(data: { warehouseId: number; items: { skuId: number; quantity: number }[] }) {
  return client.post('/inventory/reserve', data) as any
}

// 预留释放
export function releaseInventory(data: { warehouseId: number; items: { skuId: number; quantity: number }[] }) {
  return client.post('/inventory/release', data) as any
}

// 库存记录列表
export function getInventoryLogPage(params?: { current?: number; size?: number; skuId?: number; warehouseId?: number; changeType?: string }) {
  return client.get<ApiResponse<{ records: InventoryLogVO[]; total: number; size: number; current: number; pages: number }>>('/inventory/logs', { params }) as any
}

// 仓库列表
export function getWarehousePage(params?: { current?: number; size?: number }) {
  return client.get<ApiResponse<{ records: WarehouseVO[]; total: number; size: number; current: number; pages: number }>>('/warehouse', { params }) as any
}

// 所有仓库列表
export function getAllWarehouses() {
  return client.get<WarehouseVO[]>('/warehouse/all') as any
}

// 仓库详情
export function getWarehouseById(id: number) {
  return client.get<ApiResponse<WarehouseVO>>(`/warehouse/${id}`) as any
}

// 创建仓库
export function createWarehouse(data: { name: string; address?: string; contact?: string; phone?: string; remark?: string }) {
  return client.post<ApiResponse<number>>('/warehouse', data) as any
}

// 更新仓库
export function updateWarehouse(data: { id: number; name: string; address?: string; contact?: string; phone?: string; remark?: string }) {
  return client.put('/warehouse', data) as any
}

// 删除仓库
export function deleteWarehouse(id: number) {
  return client.delete(`/warehouse/${id}`) as any
}

// 获取所有SKU（用于入库/出库选择）
export function getAllSkus() {
  return client.get<ApiResponse<{ id: number; skuCode: string; productName: string; colorName: string; sizeName: string; price: number; stock: number }[]>>('/products/skus') as any
}

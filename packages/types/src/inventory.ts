// ==================== Inventory 类型 ====================

export interface InventoryVO {
  id: number
  skuId: number
  skuCode: string
  productName: string
  colorName: string
  sizeName: string
  warehouseId: number
  warehouseName: string
  quantity: number
  reservedQty: number
  availableQty: number
  alertThreshold: number
}

export interface InventoryInDTO {
  warehouseId: number
  supplierId?: number
  supplierName?: string
  remark?: string
  images?: string[]
  items: InventoryItemDTO[]
}

export interface InventoryItemDTO {
  skuId: number
  quantity: number
  price?: number
}

export interface InventoryOutDTO {
  warehouseId: number
  source: 'ORDER' | 'OTHER'
  orderId?: number
  reason?: string
  items: InventoryOutItemDTO[]
}

export interface InventoryOutItemDTO {
  skuId: number
  quantity: number
}

export interface InventoryAdjustDTO {
  skuId: number
  warehouseId: number
  adjustQty: number
  reason: string
}

export interface InventoryPageDTO {
  current?: number
  size?: number
  warehouseId?: number
  skuCode?: string
  productName?: string
  alertOnly?: boolean
}

export interface WarehouseVO {
  id: number
  name: string
  address: string
  contact: string
  phone: string
  remark?: string
}

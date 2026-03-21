// ==================== Order 类型 ====================

export interface OrderVO {
  id: number
  orderNo: string
  customerId: number
  customerName: string
  customerPhone: string
  customerAddress: string
  warehouseId: number
  warehouseName?: string
  status: number
  statusName: string
  totalAmount: number
  paidAmount: number
  remark?: string
  payTime?: string
  confirmTime?: string
  deliverTime?: string
  completeTime?: string
  createTime: string
  updateTime: string
  items: OrderItemVO[]
}

export interface OrderItemVO {
  id: number
  skuId: number
  skuCode: string
  productName: string
  colorName: string
  sizeName: string
  price: number
  quantity: number
  subtotal: number
}

export interface OrderCreateDTO {
  customerId?: number
  customerName: string
  customerPhone: string
  customerAddress: string
  warehouseId: number
  remark?: string
  items: OrderItemDTO[]
}

export interface OrderItemDTO {
  skuId: number
  price?: number
  quantity: number
}

export interface OrderPageDTO {
  current?: number
  size?: number
  orderNo?: string
  customerName?: string
  status?: number
}

export interface PaymentConfirmDTO {
  orderId: number
  paidAmount: number
}

export interface CancelOrderDTO {
  reason: string
}

// ==================== Order 类型 ====================

export interface OrderVO {
  id: number
  orderNo: string
  orderDate?: string
  sourceDocNo?: string
  sourceShop?: string
  orderType?: 'SPOT' | 'PREORDER'
  orderTypeName?: string
  customerId: number
  customerName: string
  customerPhone: string
  customerAddress: string
  warehouseId?: number
  warehouseName?: string
  status: number
  statusName: string
  totalAmount: number
  totalCostAmount?: number
  grossProfit?: number
  paidAmount: number
  balanceAmount?: number
  freightAmount?: number
  freightCost?: number
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
  costPrice?: number
  quantity: number
  subtotal: number
  costAmount?: number
  grossProfit?: number
}

export interface OrderCreateDTO {
  customerId?: number
  orderDate?: string
  sourceDocNo?: string
  sourceShop?: string
  orderType?: 'SPOT' | 'PREORDER'
  customerName: string
  customerPhone: string
  customerAddress: string
  paymentStatus?: number
  paidAmount?: number
  depositAmount?: number
  freightAmount?: number
  freightCost?: number
  warehouseId?: number
  remark?: string
  items: OrderItemDTO[]
}

export interface OrderItemDTO {
  skuId: number
  price?: number
  costPrice?: number
  quantity: number
}

export interface OrderPageDTO {
  current?: number
  size?: number
  orderNo?: string
  customerName?: string
  status?: number
  paymentStatus?: number
  orderType?: 'SPOT' | 'PREORDER'
  hasBalance?: boolean
}

export interface PaymentConfirmDTO {
  orderId: number
  paidAmount: number
}

export interface CancelOrderDTO {
  reason: string
}

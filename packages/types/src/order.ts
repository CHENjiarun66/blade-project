// ==================== Order 类型 ====================

// ==================== 新订单生命周期与财务事实（系列 D 契约） ====================

export type FulfillmentStatus =
  | 'CONFIRMED'
  | 'WAITING_ALLOCATION'
  | 'ALLOCATING'
  | 'READY_TO_SHIP'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELLED'

export type CollectionStatus = 'UNPAID' | 'PARTIAL' | 'SETTLED'

export type FulfillmentMode = 'UNDECIDED' | 'STOCK_LINKED' | 'RECORD_ONLY'

export type SettlementMethod = 'FULL_RECEIPT' | 'WRITE_OFF' | 'MIGRATION_CONFIRMED'

export type OrderAction =
  | 'confirmDraft'
  | 'recordPayment'
  | 'settleWithWriteOff'
  | 'refundPayment'
  | 'reverseFinancialRecord'
  | 'chooseFulfillmentMode'
  | 'startAllocation'
  | 'confirmAllocation'
  | 'shipOrder'
  | 'completeOrder'
  | 'cancelOrder'

export interface FinancialRecordVO {
  id: number
  orderId: number
  recordType: 'RECEIPT' | 'WRITE_OFF' | 'REFUND' | 'REVERSAL' | 'MIGRATION_OPENING'
  amount: number
  paymentMethod?: string
  occurredAt: string
  operatorName?: string
  reason?: string
  source: string
  reversedRecordId?: number
}

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
  // 新生命周期与财务快照（历史未迁移行为空）
  fulfillmentStatus?: FulfillmentStatus
  collectionStatus?: CollectionStatus
  fulfillmentMode?: FulfillmentMode
  settledAt?: string
  settlementMethod?: SettlementMethod
  grossReceivedAmount?: number
  cashRefundAmount?: number
  salesReturnAmount?: number
  netReceivedAmount?: number
  /** 历史未迁移行：展示值来自旧字段回退，禁止参与动作与统计 */
  legacyUnmigrated?: boolean
  /** 后端按状态+权限计算的可用动作白名单 */
  allowedActions?: OrderAction[]
  /** 旧收款状态兼容字段（0未付款 1部分收款 2已结清），新行请读 collectionStatus */
  paymentStatus?: number
  financialRecords?: FinancialRecordVO[]
}

export interface OrderItemVO {
  id: number
  skuId: number
  skuCode: string
  skuType?: 'NORMAL' | 'DEFAULT' | 'PLACEHOLDER'
  variantUnresolved?: boolean
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

export interface OrderRefundDTO {
  amount: number
  reason: string
  idempotencyKey?: string
}

export interface OrderReverseDTO {
  recordId: number
  reason: string
  idempotencyKey?: string
}

export interface OrderFulfillmentModeDTO {
  mode: FulfillmentMode
}

export interface OrderItemSplitTarget {
  skuId: number
  quantity: number
}

export interface OrderItemSplitDTO {
  targets: OrderItemSplitTarget[]
  reason?: string
}

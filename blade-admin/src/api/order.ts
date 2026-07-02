import client from './client'

export interface OrderItemVO {
  id: number
  skuId: number
  warehouseId?: number
  warehouseName?: string
  skuCode: string
  productName: string
  colorName: string
  sizeName: string
  price: number
  costPrice?: number
  quantity: number
  plannedQuantity?: number
  allocatedQuantity?: number
  outQuantity?: number
  adjustmentRemark?: string
  subtotal: number
  costAmount?: number
  grossProfit?: number
}

export interface OrderVO {
  id: number
  orderNo: string
  orderDate?: string
  sourceDocNo?: string
  sourceShop?: string
  orderType?: string
  orderTypeName?: string
  customerId: number
  customerName: string
  customerPhone: string
  customerAddress: string
  warehouseId?: number
  warehouseName: string
  salesmanId: number
  salesmanName: string
  totalAmount: number
  totalCostAmount?: number
  grossProfit?: number
  originalAmount?: number
  refundAmount?: number
  paidAmount: number
  balanceAmount?: number
  depositAmount: number
  freightAmount?: number
  freightCost?: number
  status: number
  statusName: string
  paymentStatus: number
  paymentStatusName: string
  paymentStatusText?: string
  writeOffAmount?: number
  writeOffReason?: string
  adjustmentStatus: string  // NONE / PENDING / APPROVED / COMPLETED
  needDelivery: number
  deliveryAddress: string
  isDelivered: number
  deliveredAt: string
  remark: string
  images: string
  createTime: string
  updateTime: string
  payTime: string
  confirmTime: string
  deliverTime: string
  completeTime: string
  items?: OrderItemVO[]
}

export interface OrderPageDTO {
  current: number
  size: number
  orderNo?: string
  customerName?: string
  status?: number
  paymentStatus?: number
  orderType?: string
  hasBalance?: boolean
}

export interface OrderPageResponse {
  records: OrderVO[]
  total: number
  size: number
  current: number
  pages: number
}

export function getOrderPage(params: OrderPageDTO) {
  return client.get<OrderPageResponse>('/orders', { params }) as any
}

export function getOrderById(id: number) {
  return client.get<OrderVO>(`/orders/${id}`) as any
}

// 创建订单
export interface OrderItemDTO {
  skuId: number
  quantity: number
  price?: number
  costPrice?: number
  warehouseId?: number
}

export interface OrderCreateDTO {
  customerId?: number
  orderDate?: string
  sourceDocNo?: string
  sourceShop?: string
  orderType?: string
  customerName: string
  customerPhone?: string
  customerAddress?: string
  paymentStatus: number
  depositAmount?: number
  paidAmount?: number
  freightAmount?: number
  freightCost?: number
  needDelivery: number
  deliveryAddress?: string
  warehouseId?: number
  remark?: string
  images?: string
  items: OrderItemDTO[]
}

export function createOrder(data: OrderCreateDTO) {
  return client.post<number>('/orders', data) as any
}

// 更新订单基础信息
export interface OrderUpdateDTO {
  id?: number
  orderDate?: string
  sourceDocNo?: string
  sourceShop?: string
  orderType?: string
  customerName?: string
  customerPhone?: string
  customerAddress?: string
  needDelivery?: number
  deliveryAddress?: string
  freightAmount?: number
  freightCost?: number
  remark?: string
  images?: string
  items?: OrderItemDTO[]
}

export function updateOrder(id: number, data: OrderUpdateDTO) {
  return client.put(`/orders/${id}`, { ...data, id }) as any
}

// 确认付款
export function confirmPayment(orderId: number, paidAmount: number) {
  return client.post('/orders/confirm-payment', { orderId, paidAmount }) as any
}

// 发货
export function deliverOrder(orderId: number) {
  return client.post(`/orders/${orderId}/deliver`) as any
}

// 完成订单
export function completeOrder(orderId: number) {
  return client.post(`/orders/${orderId}/complete`) as any
}

// 取消订单
export function cancelOrder(orderId: number, reason: string) {
  return client.post(`/orders/${orderId}/cancel`, { reason }) as any
}

// 删除订单
export function deleteOrder(id: number) {
  return client.delete(`/orders/${id}`) as any
}

// 追加收款
export interface AddPaymentDTO {
  additionalAmount: number
  markAsSettled?: boolean
  writeOffReason?: string
}
export function addPayment(orderId: number, data: AddPaymentDTO) {
  return client.post(`/orders/${orderId}/add-payment`, data) as any
}

// ============ 出库单相关 ============

export interface OrderDeliveryItemDTO {
  orderItemId: number
  skuId: number
  quantity: number
}

export interface OrderDeliveryCreateDTO {
  orderId: number
  warehouseId: number
  remark?: string
  items: OrderDeliveryItemDTO[]
}

export interface OrderDeliveryItemVO {
  id: number
  orderItemId: number
  skuId: number
  skuCode: string
  productName: string
  colorName: string
  sizeName: string
  quantity: number
}

export interface OrderDeliveryVO {
  id: number
  deliveryNo: string
  orderId: number
  orderNo: string
  warehouseId: number
  warehouseName: string
  status: number
  statusName: string
  totalQuantity: number
  deliverer: string
  deliverTime: string
  remark: string
  createTime: string
  items: OrderDeliveryItemVO[]
}

// 创建出库单
export function createDelivery(data: OrderDeliveryCreateDTO) {
  return client.post<number>('/order-deliveries', data) as any
}

// 根据订单ID查询出库单列表
export function getDeliveriesByOrderId(orderId: number) {
  return client.get<OrderDeliveryVO[]>(`/order-deliveries/order/${orderId}`) as any
}

// 确认发货
export function confirmDelivery(deliveryId: number) {
  return client.post(`/order-deliveries/${deliveryId}/confirm`) as any
}

// ============ 配货计划相关 ============

// 配货计划项请求DTO
export interface DeliveryPlanItemDTO {
  orderItemId?: number
  skuId: number
  warehouseId?: number
  plannedQty?: number
  allocatedQty?: number
  remark?: string
}

// 配货计划请求DTO
export interface DeliveryPlanDTO {
  orderId: number
  items: DeliveryPlanItemDTO[]
}

// 配货计划响应VO
export interface DeliveryPlanVO {
  id: number
  orderId: number
  orderNo: string
  orderItemId: number
  skuId: number
  skuCode: string
  productName: string
  colorName: string
  sizeName: string
  warehouseId: number
  warehouseName: string
  plannedQty: number
  allocatedQty: number
  outQty: number
  status: string  // PENDING / ALLOCATED / OUT
  remark: string
  createTime: string
}

// 调整记录DTO
export interface AdjustmentLogDTO {
  orderId: number
  adjustmentType: string  // REDUCE / REPLACE / REFUND
  originalSkuId?: number
  originalQuantity?: number
  newSkuId?: number
  newQuantity?: number
  reason?: string
  operatorName?: string
  createTime?: string
}

// 创建配货计划
export function createDeliveryPlan(orderId: number) {
  return client.post<DeliveryPlanVO[]>(`/orders/${orderId}/delivery-plan`) as any
}

// 更新配货计划
export function updateDeliveryPlan(orderId: number, data: DeliveryPlanDTO) {
  return client.put<DeliveryPlanVO[]>(`/orders/${orderId}/delivery-plan`, data) as any
}

// 获取配货计划
export function getDeliveryPlan(orderId: number) {
  return client.get<DeliveryPlanVO[]>(`/orders/${orderId}/delivery-plan`) as any
}

// 删除配货计划
export function deleteDeliveryPlan(orderId: number) {
  return client.delete(`/orders/${orderId}/delivery-plan`) as any
}

// 确认调整方案
export function confirmAdjustment(orderId: number) {
  return client.post(`/orders/${orderId}/confirm-adjustment`) as any
}

// 取消调整
export function cancelAdjustment(orderId: number) {
  return client.post(`/orders/${orderId}/cancel-adjustment`) as any
}

// 获取调整记录
export function getAdjustmentLogs(orderId: number) {
  return client.get<AdjustmentLogDTO[]>(`/orders/${orderId}/adjustment`) as any
}

// 记录调整
export function recordAdjustment(data: AdjustmentLogDTO) {
  return client.post(`/orders/${data.orderId}/adjustment-log`, data) as any
}

// 导出订单列表
export function exportOrders(params: OrderPageDTO) {
  return client.get('/orders/export', { params, responseType: 'blob' }) as Promise<Blob>
}

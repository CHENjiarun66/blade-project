import client from './client'

export type DraftStatus = 'EDITING' | 'CONFIRMED' | 'VOID'
export type MatchStatus = 'MATCHED' | 'AMBIGUOUS' | 'UNMATCHED'

export interface DraftCandidate {
  skuId: number
  skuCode: string
  skuType?: 'NORMAL' | 'DEFAULT' | 'PLACEHOLDER'
  placeholder?: boolean
  productId: number
  productCode: string
  productName: string
  colorCode?: string
  colorName?: string
  sizeCode?: string
  systemReferencePrice?: number
  matchScore?: number
  matchReasons?: string[]
}

export interface OrderDraftItem {
  id?: number
  sourceRowNo?: number
  rawProductCode?: string
  rawDescription?: string
  rawColor?: string
  rawQuantity?: string
  rawSalePrice?: string
  rawAmount?: string
  productId?: number
  skuId?: number
  quantity?: number
  salePrice?: number
  paperAmount?: number
  systemReferencePrice?: number
  matchStatus?: MatchStatus
  matchCandidates?: DraftCandidate[]
  warnings?: string[]
}

export interface OrderDraftView {
  id: number
  externalRefNo: string
  sourceBatchNo?: string
  sourceOrderNo?: string
  sourceFileId?: number
  rawCustomerName?: string
  rawCustomerPhone?: string
  customerId?: number
  customerName: string
  customerPhone?: string
  rawOrderDate?: string
  orderDate?: string
  deliveryDate?: string
  rawDeposit?: string
  deposit?: number
  paperTotalAmount?: number
  calculatedTotalAmount?: number
  note?: string
  warnings: string[]
  status: DraftStatus
  confirmedOrderId?: number
  createTime: string
  updateTime: string
  items: OrderDraftItem[]
}

export interface OrderDraftSummary {
  id: number
  externalRefNo: string
  sourceOrderNo?: string
  sourceFileId?: number
  customerName: string
  orderDate?: string
  paperTotalAmount?: number
  status: DraftStatus
  itemCount: number
  unresolvedCount: number
  warningCount: number
  updateTime: string
}

export interface DraftSaveRequest {
  externalRefNo: string
  sourceBatchNo?: string
  sourceOrderNo?: string
  sourceFileId?: number
  rawCustomerName?: string
  rawCustomerPhone?: string
  customerId?: number
  customerName?: string
  customerPhone?: string
  rawOrderDate?: string
  orderDate?: string
  deliveryDate?: string
  rawDeposit?: string
  deposit?: number
  paperTotalAmount?: number
  note?: string
  warnings?: string[]
  items: OrderDraftItem[]
}

export function getOrderDraftPage(params: {
  current?: number
  size?: number
  status?: DraftStatus
  keyword?: string
}) {
  return client.get('/order-drafts', { params }) as Promise<{
    code: number
    data: {
      records: OrderDraftSummary[]
      total: number
      size: number
      current: number
      pages: number
    }
  }>
}

export function getOrderDraft(id: number) {
  return client.get(`/order-drafts/${id}`) as Promise<{ code: number; data: OrderDraftView }>
}

export function saveOrderDraft(id: number, data: DraftSaveRequest) {
  return client.put(`/order-drafts/${id}`, data) as Promise<{ code: number; data: void }>
}

export function confirmOrderDraft(id: number, acknowledgeWarnings: boolean) {
  return client.post(`/order-drafts/${id}/confirm`, { acknowledgeWarnings }) as Promise<{
    code: number
    data: { draftId: number; orderId: number; alreadyConfirmed: boolean }
  }>
}

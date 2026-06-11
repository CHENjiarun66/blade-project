import client from './client'

export interface CustomerVO {
  id: number
  name: string
  address: string
  remark: string
  countryCode?: string
  countryName?: string
  phones: string[]
  orderCount: number
  createTime: string
  countryFlag?: string
}

export interface CustomerPageDTO {
  current: number
  size: number
  keyword?: string
}

export interface CustomerPageResponse {
  records: CustomerVO[]
  total: number
  size: number
  current: number
  pages: number
}

export interface CustomerCreateDTO {
  name: string
  phones: string[]
  address?: string
  remark?: string
  countryCode?: string
}

export interface CustomerUpdateDTO {
  id: number
  name: string
  phones: string[]
  address?: string
  remark?: string
  countryCode?: string
}

export interface CustomerStatsVO {
  customerId: number
  customerName: string
  totalOrders: number
  completedOrders: number
  totalSpending: number
  lastOrderTime?: string
  firstOrderTime?: string
}

export interface CustomerOrderVO {
  id: number
  orderNo: string
  status: number
  statusName: string
  paymentStatus: number
  totalAmount: number
  paidAmount: number
  totalAmountText: string
  paidAmountText: string
  createTime: string
  totalQuantity: number
  items: CustomerOrderItemVO[]
}

export interface CustomerOrderItemVO {
  productName: string
  skuDesc: string
  quantity: number
  price: number
}

export interface CustomerPreferenceVO {
  customerId: number
  productTypeCount: number
  categories: CategoryPref[]
  colors: ColorPref[]
  sizes: SizePref[]
}

export interface CategoryPref {
  categoryName: string
  count: number
  percentage: number
}

export interface ColorPref {
  colorName: string
  count: number
  percentage: number
}

export interface SizePref {
  sizeName: string
  count: number
  percentage: number
}

// 获取客户分页列表
export function getCustomerPage(params: CustomerPageDTO) {
  return client.get<CustomerPageResponse>('/customers', { params })
}

// 获取客户详情
export function getCustomerById(id: number) {
  return client.get<CustomerVO>(`/customers/${id}`)
}

// 根据电话搜索客户
export function searchCustomerByPhone(phone: string) {
  return client.get<CustomerVO>(`/customers/search`, { params: { phone } })
}

// 创建客户
export function createCustomer(data: CustomerCreateDTO) {
  return client.post<number>('/customers', data)
}

// 更新客户
export function updateCustomer(data: CustomerUpdateDTO) {
  return client.put('/customers', data)
}

// 删除客户
export function deleteCustomer(id: number) {
  return client.delete(`/customers/${id}`)
}

// 客户基础统计
export function getCustomerStats(id: number) {
  return client.get<CustomerStatsVO>(`/customers/${id}/stats`)
}

// 客户历史订单
export function getCustomerOrders(id: number) {
  return client.get<{ records: CustomerOrderVO[]; total: number; size: number; current: number; pages: number }>(`/customers/${id}/orders`)
}

// 客户商品偏好
export function getCustomerPreference(id: number) {
  return client.get<CustomerPreferenceVO>(`/customers/${id}/preference`)
}

import client from './client'

export interface CustomerVO {
  id: number
  name: string
  address: string
  remark: string
  phones: string[]
  orderCount: number
  createTime: string
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
}

export interface CustomerUpdateDTO {
  id: number
  name: string
  phones: string[]
  address?: string
  remark?: string
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

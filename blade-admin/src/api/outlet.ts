import client from './client'

export interface OutletVO {
  id: number
  outletCode: string
  outletName: string
  outletType?: string
  contactName?: string
  phone?: string
  address?: string
  sort?: number
  isTenantDefault?: number
  status: number
  remark?: string
  boundUserCount?: number
  orderCount?: number
  draftCount?: number
  createTime?: string
  updateTime?: string
}

export interface OutletOptionVO {
  id: number
  outletCode: string
  outletName: string
  status: number
}

export interface OutletPageDTO {
  current: number
  size: number
  keyword?: string
  status?: number
}

export interface OutletCreateDTO {
  outletCode: string
  outletName: string
  outletType?: string
  contactName?: string
  phone?: string
  address?: string
  sort?: number
  isTenantDefault?: number
  remark?: string
}

export interface OutletUpdateDTO {
  outletCode: string
  outletName: string
  outletType?: string
  contactName?: string
  phone?: string
  address?: string
  sort?: number
  isTenantDefault?: number
  remark?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export function getOutletPage(params: OutletPageDTO) {
  return client.get<PageResult<OutletVO>>('/outlets', { params })
}

export function getOutletOptions() {
  return client.get<OutletOptionVO[]>('/outlets/options')
}

export function getOutletById(id: number) {
  return client.get<OutletVO>(`/outlets/${id}`)
}

export function createOutlet(data: OutletCreateDTO) {
  return client.post<number>('/outlets', data)
}

export function updateOutlet(id: number, data: OutletUpdateDTO) {
  return client.put(`/outlets/${id}`, data)
}

export function updateOutletStatus(id: number, status: number) {
  return client.patch(`/outlets/${id}/status`, null, { params: { status } })
}

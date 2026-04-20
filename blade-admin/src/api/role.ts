import client from './client'

export interface RoleVO {
  id: number
  roleName: string
  roleCode: string
  description?: string
  status: number
  permissionIds?: number[]
  createTime: string
}

export interface RoleCreateDTO {
  roleName: string
  roleCode: string
  description?: string
  status?: number
  permissionIds?: number[]
}

export interface RoleUpdateDTO {
  id: number
  roleName?: string
  roleCode?: string
  description?: string
  status?: number
  permissionIds?: number[]
}

export function getRolePage(params: { current: number; size: number; keyword?: string }) {
  return client.get<{ records: RoleVO[]; total: number; size: number; current: number; pages: number }>('/system/roles', { params })
}

export function getAllRoles() {
  return client.get<RoleVO[]>('/system/roles/all')
}

export function getRoleById(id: number) {
  return client.get<RoleVO>(`/system/roles/${id}`)
}

export function createRole(data: RoleCreateDTO) {
  return client.post<number>('/system/roles', data)
}

export function updateRole(data: RoleUpdateDTO) {
  return client.put('/system/roles', data)
}

export function deleteRole(id: number) {
  return client.delete(`/system/roles/${id}`)
}

export function getRolePermissions(roleId: number) {
  return client.get<number[]>(`/system/roles/${roleId}/permissions`)
}

import client from './client'

export interface PermissionVO {
  id: number
  name: string
  code: string
  type: number
  typeName?: string
  module?: string
  parentId?: number
  parentName?: string
  path?: string
  method?: string
  icon?: string
  sort: number
  status: number
  maskType?: number
  maskValue?: string
  description?: string
  createTime?: string
  children?: PermissionVO[]
}

export interface PermissionCreateDTO {
  name: string
  code: string
  type: number
  module?: string
  parentId?: number
  path?: string
  method?: string
  icon?: string
  sort?: number
  status?: number
  maskType?: number
  maskValue?: string
  description?: string
}

export interface PermissionUpdateDTO {
  id: number
  name?: string
  code?: string
  type?: number
  module?: string
  parentId?: number
  path?: string
  method?: string
  icon?: string
  sort?: number
  status?: number
  maskType?: number
  maskValue?: string
  description?: string
}

export interface RolePermissionDTO {
  roleId: number
  permissionIds: number[]
}

export function getPermissionPage(params: { current: number; size: number; type?: number; module?: string }) {
  return client.get<{ records: PermissionVO[]; total: number; size: number; current: number; pages: number }>('/permissions', { params })
}

export function getPermissionTree() {
  return client.get<PermissionVO[]>('/permissions/tree')
}

export function getPermissionById(id: number) {
  return client.get<PermissionVO>(`/permissions/${id}`)
}

export function createPermission(data: PermissionCreateDTO) {
  return client.post<number>('/permissions', data)
}

export function updatePermission(data: PermissionUpdateDTO) {
  return client.put('/permissions', data)
}

export function deletePermission(id: number) {
  return client.delete(`/permissions/${id}`)
}

export function getRolePermissions(roleId: number) {
  return client.get<number[]>(`/permissions/role/${roleId}`)
}

export function assignRolePermissions(data: RolePermissionDTO) {
  return client.post('/permissions/role', data)
}

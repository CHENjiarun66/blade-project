import client from './client'

export interface UserVO {
  id: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  status: number
  roles?: RoleSimple[]
  outletIds?: number[]
  defaultOutletId?: number
  outletScope?: 'ALL' | 'ASSIGNED' | 'NONE'
  peopleScope?: 'ALL_USERS' | 'SELF'
  createTime: string
  updateTime?: string
}

export interface RoleSimple {
  id: number
  roleName: string
  roleCode: string
  /** 服务端派生：该角色是否授予 data:outlet:all（用于判断销售员可无绑定） */
  grantsOutletAll?: boolean
}

export interface UserPageDTO {
  current: number
  size: number
  keyword?: string
}

export interface UserCreateDTO {
  username: string
  password: string
  nickname?: string
  email?: string
  phone?: string
  roleIds?: number[]
  outletIds?: number[]
  defaultOutletId?: number
}

export interface UserUpdateDTO {
  id: number
  nickname?: string
  email?: string
  phone?: string
  status?: number
  roleIds?: number[]
  outletIds?: number[]
  defaultOutletId?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export function getUserPage(params: UserPageDTO) {
  return client.get<PageResult<UserVO>>('/system/users', { params })
}

export function getUserById(id: number) {
  return client.get<UserVO>(`/system/users/${id}`)
}

export function createUser(data: UserCreateDTO) {
  return client.post<number>('/system/users', data)
}

export function updateUser(data: UserUpdateDTO) {
  return client.put('/system/users', data)
}

export function deleteUser(id: number) {
  return client.delete(`/system/users/${id}`)
}

export function resetUserPassword(id: number, newPassword: string) {
  return client.put(`/system/users/${id}/password`, null, { params: { newPassword } })
}

export function getAllRoles() {
  return client.get<RoleSimple[]>('/system/roles/all')
}

import client from './client'

export interface LoginDTO {
  tenantCode: string
  username: string
  password: string
}

export interface LoginVO {
  token: string
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export function login(data: LoginDTO) {
  return client.post<LoginVO>('/auth/login', data)
}

export function getUserInfo() {
  return client.get('/user/info')
}

export function getAuthCodes() {
  return client.get<string[]>('/auth/codes')
}

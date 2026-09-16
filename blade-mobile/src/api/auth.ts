import client from './client'
import type { LoginVO, LoginDTO } from '@/types/auth'

export async function login(data: LoginDTO): Promise<LoginVO> {
  const response = await client.post<LoginVO>('/auth/login', data)
  return response.data
}

export async function logout(refresh_token?: string | null): Promise<void> {
  await client.post('/auth/logout', null, {
    headers: refresh_token ? { 'X-Refresh-Token': `Bearer ${refresh_token}` } : undefined
  })
}

export async function refreshToken(refresh_token: string): Promise<LoginVO> {
  const response = await client.post<LoginVO>('/auth/refresh', null, {
    headers: { Authorization: `Bearer ${refresh_token}` }
  })
  return response.data
}

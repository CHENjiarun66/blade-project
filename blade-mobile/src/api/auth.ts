import client from './client'
import type { LoginVO, LoginDTO } from '@/types/auth'

export async function login(data: LoginDTO): Promise<LoginVO> {
  const response = await client.post<LoginVO>('/auth/login', data)
  return response.data
}

export async function logout(): Promise<void> {
  await client.post('/auth/logout')
}

export async function refreshToken(refresh_token: string): Promise<LoginVO> {
  const response = await client.post<LoginVO>('/auth/refresh', { refresh_token })
  return response.data
}

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, logout, refreshToken } from '@/api/auth'
import type { LoginDTO, LoginVO } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const userInfo = ref<LoginVO | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  async function loginAction(loginDTO: LoginDTO) {
    const result = await login(loginDTO)
    // 后端返回 result 直接是 LoginVO
    token.value = result.token
    userInfo.value = result
    localStorage.setItem('token', result.token)
    localStorage.setItem('refresh_token', result.refreshToken)
    return result
  }

  async function logoutAction() {
    try {
      await logout()
    } finally {
      token.value = null
      userInfo.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('refresh_token')
    }
  }

  async function refreshTokenAction() {
    const refresh_token = localStorage.getItem('refresh_token')
    if (!refresh_token) {
      throw new Error('No refresh token')
    }
    const result = await refreshToken(refresh_token)
    token.value = result.token
    localStorage.setItem('token', result.token)
    return result
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    loginAction,
    logoutAction,
    refreshTokenAction
  }
})

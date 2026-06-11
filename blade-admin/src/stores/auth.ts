import { defineStore } from 'pinia'
import { ref } from 'vue'
import { clearCatalogCaches } from '@/utils/catalogCache'

interface UserInfo {
  userId: string
  username: string
  realName: string
  avatar?: string
  roles?: string[]
}

function readJson<T>(key: string, fallback: T): T {
  const value = localStorage.getItem(key)
  if (!value) return fallback
  try {
    return JSON.parse(value) as T
  } catch {
    localStorage.removeItem(key)
    return fallback
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const userInfo = ref<UserInfo | null>(readJson<UserInfo | null>('userInfo', null))
  // 存储用户的权限码列表，如 ['order:create', 'order:update', ...]
  const permissions = ref<string[]>(readJson<string[]>('permissions', []))

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setRefreshToken(newRefreshToken: string) {
    refreshToken.value = newRefreshToken
    localStorage.setItem('refreshToken', newRefreshToken)
  }

  function setTokens(newToken: string, newRefreshToken?: string) {
    setToken(newToken)
    if (newRefreshToken) {
      setRefreshToken(newRefreshToken)
    }
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function setPermissions(codes: string[]) {
    permissions.value = codes
    localStorage.setItem('permissions', JSON.stringify(codes))
  }

  function logout() {
    token.value = null
    refreshToken.value = null
    userInfo.value = null
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('permissions')
    void clearCatalogCaches()
  }

  return {
    token,
    refreshToken,
    userInfo,
    permissions,
    setToken,
    setRefreshToken,
    setTokens,
    setUserInfo,
    setPermissions,
    logout,
  }
})

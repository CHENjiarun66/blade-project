import { defineStore } from 'pinia'
import { ref } from 'vue'

interface UserInfo {
  userId: string
  username: string
  realName: string
  avatar?: string
  roles?: string[]
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const userInfo = ref<UserInfo | null>(null)
  // 存储用户的权限码列表，如 ['order:create', 'order:update', ...]
  const permissions = ref<string[]>([])

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
  }

  function setPermissions(codes: string[]) {
    permissions.value = codes
  }

  function logout() {
    token.value = null
    userInfo.value = null
    permissions.value = []
    localStorage.removeItem('token')
  }

  return {
    token,
    userInfo,
    permissions,
    setToken,
    setUserInfo,
    setPermissions,
    logout,
  }
})

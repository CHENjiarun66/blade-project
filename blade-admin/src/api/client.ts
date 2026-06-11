import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { clearCatalogCaches } from '@/utils/catalogCache'

interface RetryRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

const client = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

const ACCESS_TOKEN_REFRESH_THRESHOLD_SECONDS = 10 * 60

let refreshingToken: Promise<string> | null = null

function getJwtExpiresInSeconds(token: string): number | null {
  try {
    const payloadPart = token.split('.')[1]
    if (!payloadPart) return null
    const payload = JSON.parse(atob(payloadPart.replace(/-/g, '+').replace(/_/g, '/')))
    if (typeof payload.exp !== 'number') return null
    return payload.exp - Math.floor(Date.now() / 1000)
  } catch {
    return null
  }
}

function shouldRefreshBeforeRequest(token: string): boolean {
  const expiresIn = getJwtExpiresInSeconds(token)
  return expiresIn !== null && expiresIn <= ACCESS_TOKEN_REFRESH_THRESHOLD_SECONDS
}

async function refreshAccessToken() {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) {
    throw new Error('缺少 refresh token')
  }

  if (!refreshingToken) {
    refreshingToken = axios.post('/api/auth/refresh', null, {
      headers: { Authorization: `Bearer ${refreshToken}` },
    }).then((response) => {
      const data = response.data
      const newToken = data?.accessToken || data?.token
      const newRefreshToken = data?.refreshToken
      if (!newToken) {
        throw new Error('刷新登录态失败')
      }
      localStorage.setItem('token', newToken)
      if (newRefreshToken) {
        localStorage.setItem('refreshToken', newRefreshToken)
      }
      return newToken
    }).finally(() => {
      refreshingToken = null
    })
  }

  return refreshingToken
}

function clearAuthState() {
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('permissions')
  void clearCatalogCaches()
}

function redirectToLogin() {
  const currentPath = router.currentRoute.value.fullPath
  const query = currentPath && currentPath !== '/login'
    ? { redirect: currentPath }
    : undefined
  router.push({ path: '/login', query })
}

// 请求拦截器
client.interceptors.request.use(
  async (config) => {
    let token = localStorage.getItem('token')
    const refreshToken = localStorage.getItem('refreshToken')
    const isAuthRequest = typeof config.url === 'string' && config.url.startsWith('/auth/')

    if (token && refreshToken && !isAuthRequest && shouldRefreshBeforeRequest(token)) {
      try {
        token = await refreshAccessToken()
      } catch {
        clearAuthState()
        redirectToLogin()
        throw new Error('登录已过期，请重新登录')
      }
    }

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
client.interceptors.response.use(
  (response) => {
    const data = response.data
    // 检查业务状态码（只有明确返回 code 且不等于 200 时才算错误）
    if (data && typeof data.code === 'number' && data.code !== 200) {
      // 创建一个带有 response 属性的错误对象
      const err = new Error(data.message || '请求失败') as Error & { response?: typeof response }
      err.name = 'BusinessError'
      err.response = response
      throw err
    }
    return data
  },
  async (error: AxiosError) => {
    // 如果是业务错误（code !== 200），不再显示额外的错误提示
    if (error.name === 'BusinessError') {
      return Promise.reject(error)
    }
    const originalRequest = error.config as RetryRequestConfig | undefined
    if (error.response) {
      switch (error.response.status) {
        case 401:
        case 403:
          if (error.response.status === 403 && originalRequest?._retry) {
            ElMessage.error('没有权限访问')
            break
          }
          if (originalRequest && !originalRequest._retry && localStorage.getItem('refreshToken')) {
            try {
              originalRequest._retry = true
              const newToken = await refreshAccessToken()
              originalRequest.headers.Authorization = `Bearer ${newToken}`
              return client(originalRequest)
            } catch {
              clearAuthState()
              ElMessage.error('登录已过期，请重新登录')
              redirectToLogin()
              break
            }
          }
          ElMessage.error('登录已过期，请重新登录')
          clearAuthState()
          redirectToLogin()
          break
        default:
          ElMessage.error((error.response.data as any)?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误，请检查连接')
    }
    return Promise.reject(error)
  }
)

export default client

import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const client = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截器
client.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
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
  (error) => {
    // 如果是业务错误（code !== 200），不再显示额外的错误提示
    if (error.name === 'BusinessError') {
      return Promise.reject(error)
    }
    if (error.response) {
      switch (error.response.status) {
        case 401:
          ElMessage.error('登录已过期，请重新登录')
          localStorage.removeItem('token')
          router.push('/login')
          break
        case 403:
          ElMessage.error('没有权限访问')
          break
        default:
          ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误，请检查连接')
    }
    return Promise.reject(error)
  }
)

export default client

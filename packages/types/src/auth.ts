// ==================== Auth 类型 ====================

export interface LoginDTO {
  username: string
  password: string
}

export interface LoginVO {
  token: string
  refreshToken: string
  expiresIn: number
}

export interface R<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

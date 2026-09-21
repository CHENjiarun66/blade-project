import { getOutletOptions, type OutletOptionsVO } from '@/api/outlet'

// 模块级缓存：同一页面上的多个 OutletSelect / 列表筛选共享一次请求。
// 失败时清空，允许下一次重新请求。
let outletOptionsPromise: Promise<OutletOptionsVO> | null = null

export function loadOutletOptions(force = false): Promise<OutletOptionsVO> {
  if (force || !outletOptionsPromise) {
    outletOptionsPromise = getOutletOptions()
      .then(res => res.data)
      .catch(error => {
        outletOptionsPromise = null
        throw error
      })
  }
  return outletOptionsPromise
}

export function resetOutletOptionsCache() {
  outletOptionsPromise = null
}

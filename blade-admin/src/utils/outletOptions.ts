import { getOutletOptions, type OutletOptionsVO } from '@/api/outlet'
import {
  getInflightOutletOptions,
  resetOutletOptionsCache,
  setInflightOutletOptions,
} from '@/utils/outletOptionsCache'

/**
 * 加载当前调用者的档口选项。
 *
 * 只对“并发中的请求”去重，**不缓存已完成结果**：请求 settle 后，仅当槽位仍指向本次
 * Promise 时才清空。因此同账号档口增删/停用、或退出后换账号，下一次加载都会重新向
 * 服务端取真实 scope，不会复用旧账号/旧数据。
 *
 * `force=true` 会先清空槽位再发起新请求；旧请求的 finally 因身份不匹配不会清掉新请求。
 */
export function loadOutletOptions(force = false): Promise<OutletOptionsVO> {
  if (force) {
    resetOutletOptionsCache()
  }
  const existing = getInflightOutletOptions<OutletOptionsVO>()
  if (existing) {
    return existing
  }
  const request = getOutletOptions()
    .then(res => res.data)
    .finally(() => {
      // 只清理自己的槽位；避免旧请求把后来者的新请求一起清掉
      if (getInflightOutletOptions<OutletOptionsVO>() === request) {
        resetOutletOptionsCache()
      }
    })
  setInflightOutletOptions(request)
  return request
}

export { resetOutletOptionsCache }

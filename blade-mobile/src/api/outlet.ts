import client from './client'
import type { R } from '@/types/auth'
import type { OutletOptionsVO } from '@/types/outlet'

/** 当前登录用户可用且启用的档口选项；服务端按档口范围裁剪。 */
export async function getOutletOptions(): Promise<R<OutletOptionsVO>> {
  const response = await client.get<R<OutletOptionsVO>>('/outlets/options')
  return response.data
}

// 档口选项并发去重槽（无任何业务 import，避免 client/auth ↔ api 的循环依赖）。
//
// 只承载“进行中的请求”：请求 settle 后调用方会在确认仍是同一 Promise 时清空，
// 因此不缓存已完成结果，下一次加载一定会重新向服务端取真实 scope。
let inflight: Promise<unknown> | null = null

export function getInflightOutletOptions<T>(): Promise<T> | null {
  return inflight as Promise<T> | null
}

export function setInflightOutletOptions<T>(promise: Promise<T> | null): void {
  inflight = promise
}

export function resetOutletOptionsCache(): void {
  inflight = null
}

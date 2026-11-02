import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { blankLine, MAX_SELECTION, type SelectionLine } from './selectionModel'
import { snapshotProduct } from './catalogImages'
import type { CatalogProductVO, CatalogHandoffResponse } from '@/api/catalog'

interface SavedSelection {
  lines: SelectionLine[]
  request?: { signature: string; id: string }
  receipt?: { signature: string; data: CatalogHandoffResponse }
}
function storageScope(token: string | null, userId?: string) {
  try {
    const encoded = token?.split('.')[1]
    if (!encoded || !userId) return null
    const claims = JSON.parse(atob(encoded.replace(/-/g, '+').replace(/_/g, '/')))
    if (!claims.tenantId) return null
    return `catalog:selection:v1:${claims.tenantId}:${userId}`
  } catch { return null }
}
function requestId() {
  if (crypto.randomUUID) return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16)); bytes[6] = (bytes[6]! & 15) | 64; bytes[8] = (bytes[8]! & 63) | 128
  const hex = [...bytes].map(b => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`
}
export function useCatalogSelection() {
  const auth = useAuthStore()
  const key = storageScope(auth.token, auth.userInfo?.userId)
  const lines = ref<SelectionLine[]>([]), storageError = ref(false)
  const pendingRequest = ref<SavedSelection['request']>(), receipt = ref<SavedSelection['receipt']>()
  if (key) {
    try {
      const saved = JSON.parse(localStorage.getItem(key) || 'null') as SavedSelection | null
      const seen = new Set<number>()
      lines.value = (saved?.lines || []).filter(line => {
        const id = line?.product?.id
        if (!Number.isSafeInteger(id) || id <= 0 || seen.has(id)) return false
        seen.add(id); return true
      }).slice(0, MAX_SELECTION).map(line => ({ ...line, product: snapshotProduct(line.product), checked: line.checked !== false,
        colorNote: String(line.colorNote || '').slice(0,100), qty: String(line.qty ?? '').slice(0,12), unitPrice: String(line.unitPrice ?? '').slice(0,12) }))
      pendingRequest.value = saved?.request && typeof saved.request.signature === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(saved.request.id) ? saved.request : undefined
      receipt.value = saved?.receipt && typeof saved.receipt.signature === 'string' && Number.isSafeInteger(saved.receipt.data?.draftId) && saved.receipt.data.draftId > 0 ? saved.receipt : undefined
    } catch { storageError.value = true }
  }
  const selectedIds = computed(() => new Set(lines.value.map(line => line.product.id)))
  let persistTimer: ReturnType<typeof setTimeout> | undefined
  function persist() {
    clearTimeout(persistTimer)
    if (!key) { storageError.value = true; return }
    // Logout/auth expiry clears selection caches; an unmount must not recreate them.
    if (storageScope(localStorage.getItem('token'), auth.userInfo?.userId) !== key) return
    try { localStorage.setItem(key, JSON.stringify({ lines: lines.value, request: pendingRequest.value, receipt: receipt.value })); storageError.value = false }
    catch { storageError.value = true }
  }
  watch([lines, pendingRequest, receipt], () => { clearTimeout(persistTimer); persistTimer = setTimeout(persist, 180) }, { deep: true })
  window.addEventListener('pagehide', persist)
  onBeforeUnmount(() => { persist(); window.removeEventListener('pagehide', persist) })
  function save(line: SelectionLine) {
    const existing = lines.value.find(item => item.product.id === line.product.id)
    if (!existing && lines.value.length >= MAX_SELECTION) throw new Error(`一次最多选${MAX_SELECTION}款，请先移交本次清单`)
    const value = { ...line, product: snapshotProduct(line.product) }
    if (existing) Object.assign(existing, value)
    else lines.value.push(value)
    persist()
    return !!existing
  }
  function add(product: CatalogProductVO) { if (!selectedIds.value.has(product.id)) save(blankLine(product)) }
  function remove(id: number) {
    const index = lines.value.findIndex(line => line.product.id === id)
    if (index < 0) return
    const [line] = lines.value.splice(index, 1); persist()
    return line ? () => { if (!selectedIds.value.has(id)) { lines.value.splice(index, 0, line); persist() } } : undefined
  }
  function clear() { lines.value = []; pendingRequest.value = undefined; receipt.value = undefined; persist() }
  function idFor(signature: string) {
    if (pendingRequest.value?.signature !== signature) { pendingRequest.value = { signature, id: requestId() }; persist() }
    return pendingRequest.value!.id
  }
  function remember(signature: string, data: CatalogHandoffResponse) { receipt.value = { signature, data }; persist() }
  return { lines, selectedIds, storageError, receipt, save, add, remove, clear, persist, idFor, remember }
}

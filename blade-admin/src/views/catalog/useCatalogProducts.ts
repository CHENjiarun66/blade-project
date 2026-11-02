import { ref, reactive, computed } from 'vue'
import { getCatalogProducts, getCatalogFilters, type CatalogProductVO, type CatalogFiltersVO, type CatalogPageParams } from '@/api/catalog'
import { readCatalogProductsCache, writeCatalogProductsCache } from '@/utils/catalogCache'
export function useCatalogProducts() {
  const filters = reactive<CatalogPageParams>({ keyword: '', stockMode: 'all' })
  const options = reactive<CatalogFiltersVO>({ categories: [], colors: [], sizes: [], stockModes: [] })
  const products = ref<CatalogProductVO[]>([]), total = ref(0), page = ref(1)
  const loading = ref(false), loadingMore = ref(false), error = ref(''), filterError = ref(false)
  let request = 0
  const hasMore = computed(() => products.value.length < total.value)
  function applyCache() {
    const cached = readCatalogProductsCache(filters)
    if (!cached) return
    products.value = cached.products; total.value = cached.total; page.value = cached.current
  }
  async function fetchProducts(reset = true) {
    if (!reset && (loading.value || loadingMore.value || !hasMore.value)) return
    const id = ++request
    if (reset) loading.value = products.value.length === 0
    else loadingMore.value = true
    error.value = ''
    const nextPage = reset ? 1 : page.value + 1
    const query = { ...filters }
    try {
      const response = await getCatalogProducts({ ...query, current: nextPage, size: 20 })
      if (id !== request) return
      const next = response.data.records || []
      const byId = new Map((reset ? [] : products.value).map(p => [p.id, p]))
      next.forEach(p => byId.set(p.id, p))
      products.value = [...byId.values()]; total.value = response.data.total; page.value = response.data.current
      writeCatalogProductsCache(query, response.data, products.value)
    } catch (e) {
      if (id === request) error.value = e instanceof Error ? e.message : '商品加载失败，请重试'
    } finally {
      if (id === request) { loading.value = false; loadingMore.value = false }
    }
  }
  function search() {
    ++request; loadingMore.value = false
    products.value = []; total.value = 0; page.value = 1
    applyCache(); void fetchProducts()
  }
  async function loadFilters() {
    filterError.value = false
    try { const res = await getCatalogFilters(); Object.assign(options, res.data) }
    catch { filterError.value = true }
  }
  function destroy() { ++request }
  return { filters, options, products, total, page, loading, loadingMore, error, filterError, hasMore, search, fetchProducts, loadFilters, applyCache, destroy }
}

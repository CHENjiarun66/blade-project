<template>
  <el-popover
    v-model:visible="visible"
    placement="bottom-start"
    :width="320"
    trigger="click"
    :disabled="disabled"
    :hide-after="0"
    :show-arrow="false"
  >
    <template #reference>
      <el-input
        :model-value="displayValue"
        readonly
        :placeholder="placeholder"
        :disabled="disabled"
        class="country-code-input"
        @click="onInputClick"
      >
        <template #prefix>
          <span v-if="selectedCountry" class="flag-text">{{ getFlag(selectedCountry.iso) }}</span>
          <span v-else class="flag-placeholder">🌐</span>
        </template>
        <template #suffix>
          <span class="material-symbols-outlined text-sm text-gray-400">expand_more</span>
        </template>
      </el-input>
    </template>

    <div class="country-search-box">
      <el-input
        ref="searchInputRef"
        v-model="keyword"
        placeholder="搜索国家名称或区号"
        clearable
        size="large"
        class="country-search-input"
        @input="onSearch"
        @keydown="onKeydown"
      />
      <div class="country-list" @scroll="onScroll" ref="listRef">
        <!-- 常用国家 -->
        <div v-if="recentCountries.length > 0 && !keyword" class="recent-section">
          <div class="recent-title">常用</div>
          <div
            v-for="c in recentCountries"
            :key="'recent-' + c.iso"
            class="country-item"
            :class="{ 'country-item-selected': c.code === modelValue }"
            @click="selectCountry(c)"
          >
            <span class="flag-text">{{ getFlag(c.iso) }}</span>
            <span class="country-name">{{ c.nameZh || c.name }}</span>
            <span class="country-name-en">{{ c.name }}</span>
            <span class="country-code">{{ c.code }}</span>
          </div>
          <div class="recent-divider"></div>
        </div>
        <div
          v-for="c in visibleCountries"
          :key="c.iso"
          class="country-item"
          :class="{
            'country-item-selected': c.code === modelValue,
            'country-item-focused': focusedIndex === getGlobalIndex(c)
          }"
          @click="selectCountry(c)"
          @mouseenter="focusedIndex = getGlobalIndex(c)"
        >
          <span class="flag-text">{{ getFlag(c.iso) }}</span>
          <span class="country-name">{{ c.nameZh || c.name }}</span>
          <span class="country-name-en">{{ c.name }}</span>
          <span class="country-code">{{ c.code }}</span>
        </div>
        <div v-if="filteredCountries.length === 0" class="no-result">
          <span class="material-symbols-outlined text-3xl text-gray-300 mb-2">search_off</span>
          <p class="text-gray-400 text-sm">无匹配结果</p>
        </div>
        <div v-if="hasMore" class="loading-more">
          <el-icon class="is-loading text-gray-400"><Loading /></el-icon>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { countries, getFlag, type Country } from '@/data/countries'

const RECENT_COUNTRIES_KEY = 'recentCountries'
const MAX_RECENT = 5

const props = defineProps<{
  modelValue?: string
  disabled?: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [code: string]
}>()

const visible = ref(false)
const keyword = ref('')
const searchInputRef = ref<any>(null)
const listRef = ref<HTMLElement | null>(null)
const PAGE_SIZE = 50
const visibleCount = ref(PAGE_SIZE)
const recentCountries = ref<Country[]>([])
const focusedIndex = ref(-1)

const selectedCountry = computed(() =>
  countries.find(c => c.code === props.modelValue)
)

const displayValue = computed(() => {
  if (!props.modelValue) return ''
  const c = selectedCountry.value
  if (!c) return props.modelValue
  return `${getFlag(c.iso)} ${c.code}`
})

const filteredCountries = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return countries
  return countries.filter(c =>
    c.name.toLowerCase().includes(kw) ||
    (c.nameZh && c.nameZh.includes(kw)) ||
    c.code.toLowerCase().includes(kw)
  )
})

// 计算可见国家列表（无搜索时排除常用，搜索时包含全部）
const displayListWithoutRecent = computed(() => {
  if (keyword.value.trim()) return filteredCountries.value
  // 无搜索时，排除常用国家（避免重复显示）
  const recentCodes = new Set(recentCountries.value.map(c => c.code))
  return filteredCountries.value.filter(c => !recentCodes.has(c.code))
})

const visibleCountries = computed(() =>
  displayListWithoutRecent.value.slice(0, visibleCount.value)
)

const hasMore = computed(() =>
  displayListWithoutRecent.value.length > visibleCount.value
)

// 获取某国家在整个列表中的全局索引（考虑常用国家在前）
function getGlobalIndex(c: Country): number {
  if (keyword.value.trim()) {
    return filteredCountries.value.findIndex(x => x.iso === c.iso)
  }
  // 有常用国家时，全局索引 = 常用国家数量 + 在非搜索列表中的索引
  const recentCodes = new Set(recentCountries.value.map(x => x.code))
  const inList = filteredCountries.value.findIndex(x => x.iso === c.iso)
  if (!recentCodes.has(c.code)) {
    return recentCountries.value.length + inList
  }
  return recentCountries.value.findIndex(x => x.iso === c.iso)
}

function loadRecentCountries() {
  try {
    const stored = localStorage.getItem(RECENT_COUNTRIES_KEY)
    if (stored) {
      const codes: string[] = JSON.parse(stored)
      recentCountries.value = codes
        .map(code => countries.find(c => c.code === code))
        .filter((c): c is Country => c !== undefined)
    }
  } catch {
    recentCountries.value = []
  }
}

function saveRecentCountry(c: Country) {
  // 移除已有
  recentCountries.value = recentCountries.value.filter(x => x.code !== c.code)
  // 头部插入
  recentCountries.value.unshift(c)
  // 最多保留 MAX_RECENT 个
  if (recentCountries.value.length > MAX_RECENT) {
    recentCountries.value = recentCountries.value.slice(0, MAX_RECENT)
  }
  // 持久化
  localStorage.setItem(RECENT_COUNTRIES_KEY, JSON.stringify(recentCountries.value.map(x => x.code)))
}

watch(visible, async (val) => {
  if (val) {
    keyword.value = ''
    visibleCount.value = PAGE_SIZE
    focusedIndex.value = -1
    await nextTick()
    searchInputRef.value?.focus()
  }
})

function onSearch() {
  visibleCount.value = PAGE_SIZE
  focusedIndex.value = -1
}

function onScroll(e: Event) {
  const el = e.target as HTMLElement
  if (!hasMore.value) return
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 80) {
    visibleCount.value += PAGE_SIZE
  }
}

function onInputClick() {
  visible.value = true
}

function selectCountry(c: Country) {
  emit('update:modelValue', c.code)
  saveRecentCountry(c)
  visible.value = false
}

function onKeydown(e: KeyboardEvent) {
  const total = displayListWithoutRecent.value.length
  if (total === 0) return

  if (e.key === 'ArrowDown') {
    e.preventDefault()
    focusedIndex.value = Math.min(focusedIndex.value + 1, total - 1)
    scrollToFocused()
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    focusedIndex.value = Math.max(focusedIndex.value - 1, 0)
    scrollToFocused()
  } else if (e.key === 'Enter') {
    e.preventDefault()
    if (focusedIndex.value >= 0 && focusedIndex.value < total) {
      const c = displayListWithoutRecent.value[focusedIndex.value]
      selectCountry(c)
    }
  } else if (e.key === 'Escape') {
    visible.value = false
  }
}

function scrollToFocused() {
  nextTick(() => {
    const list = listRef.value
    if (!list) return
    const items = list.querySelectorAll('.country-item')
    if (items[focusedIndex.value]) {
      items[focusedIndex.value].scrollIntoView({ block: 'nearest' })
    }
  })
}

onMounted(() => {
  loadRecentCountries()
})
</script>

<style scoped>
.country-code-input :deep(.el-input__wrapper) {
  cursor: pointer;
}

.country-search-box {
  user-select: none;
}

.country-search-input :deep(.el-input__wrapper) {
  border-radius: 10px;
  margin-bottom: 8px;
}

.country-list {
  max-height: 280px;
  overflow-y: auto;
  border-radius: 10px;
}

.country-list::-webkit-scrollbar {
  width: 4px;
}
.country-list::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 4px;
}

.country-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 8px;
  gap: 8px;
  transition: background 0.15s;
}
.country-item:hover,
.country-item-focused {
  background: #f3f4f6;
}
.country-item-selected {
  background: #eff6ff !important;
}

.recent-section {
  margin-bottom: 4px;
}
.recent-title {
  font-size: 12px;
  color: #9ca3af;
  padding: 4px 12px;
  font-weight: 500;
}
.recent-divider {
  height: 1px;
  background: #e5e7eb;
  margin: 4px 0;
}

.flag-text {
  font-size: 18px;
  line-height: 1;
  flex-shrink: 0;
}

.flag-placeholder {
  font-size: 16px;
}

.country-name {
  flex: 1;
  font-size: 14px;
  color: #111827;
  font-weight: 500;
}

.country-name-en {
  flex: 1;
  font-size: 13px;
  color: #9ca3af;
  display: none;
}

.country-code {
  font-size: 13px;
  color: #6b7280;
  font-weight: 600;
  flex-shrink: 0;
}

.no-result {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px;
}

.loading-more {
  display: flex;
  justify-content: center;
  padding: 8px;
}
</style>

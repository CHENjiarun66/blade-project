<template>
  <div class="outlet-select" :data-testid="testId">
    <el-tag
      v-if="pending"
      type="warning"
      effect="light"
      size="small"
      class="outlet-pending-tag"
      data-testid="pending-outlet-tag"
    >
      {{ allowPending ? '待归档档口（请选择）' : '待归档档口' }}
    </el-tag>

    <el-input
      v-if="readonlyMode"
      :model-value="readonlyText"
      disabled
      :placeholder="placeholder"
      data-testid="outlet-readonly"
    />

    <el-select
      v-else
      :model-value="modelValue ?? undefined"
      :disabled="selectDisabled"
      :loading="loading"
      :placeholder="placeholder || '请选择档口'"
      filterable
      clearable
      class="!w-full"
      data-testid="outlet-select"
      @update:model-value="onSelect"
    >
      <el-option
        v-for="item in displayItems"
        :key="item.id"
        :label="optionLabel(item)"
        :value="item.id"
      >
        <span class="outlet-option-name">{{ item.outletName }}</span>
        <span class="outlet-option-code">{{ item.outletCode }}</span>
      </el-option>
    </el-select>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { OutletOptionVO, OutletOptionsVO } from '@/api/outlet'
import { loadOutletOptions } from '@/utils/outletOptions'

interface Props {
  modelValue?: number | null
  disabled?: boolean
  placeholder?: string
  /** 当前草稿/订单是历史空档口（待归档） */
  pending?: boolean
  /** 是否允许在待归档状态下继续选择档口（拥有 data:outlet:unassigned） */
  allowPending?: boolean
  testId?: string
  /** 当前档口不在可用选项中（已禁用历史档口）时用于只读展示的名称 */
  fallbackLabel?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  disabled: false,
  placeholder: '',
  pending: false,
  allowPending: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | null): void
  (e: 'change', value: number | null): void
}>()

const loading = ref(false)
const options = ref<OutletOptionsVO | null>(null)

const items = computed(() => options.value?.items || [])
const locked = computed(() => options.value?.locked === true)
const readonlyMode = computed(() => locked.value || items.value.length === 1)
const selectDisabled = computed(() => props.disabled || (props.pending && !props.allowPending))

const displayItems = computed<OutletOptionVO[]>(() => {
  const list = [...items.value]
  if (props.modelValue != null && !list.some(item => item.id === props.modelValue)) {
    list.push({
      id: props.modelValue,
      outletCode: '',
      outletName: props.fallbackLabel || `#${props.modelValue}`,
      status: 1,
    })
  }
  return list
})

const currentItem = computed(() => items.value.find(item => item.id === props.modelValue))

const readonlyText = computed(() => {
  const item = currentItem.value
  if (item) return optionLabel(item)
  if (props.modelValue == null) return props.pending ? '待归档档口' : ''
  return props.fallbackLabel || `#${props.modelValue}`
})

function optionLabel(item: OutletOptionVO) {
  return item.outletCode ? `${item.outletName}（${item.outletCode}）` : item.outletName
}

/**
 * 可自动带入的选择：单档口锁定 > 默认档口 > 唯一可用档口。
 * 待归档（历史 NULL）不自动带入，必须人工确认。
 */
function defaultSelection(): number | null {
  const current = options.value
  if (!current) return null
  if (current.locked && current.items.length === 1) return current.items[0].id
  if (props.pending) return null
  if (current.defaultOutletId != null) return current.defaultOutletId
  if (current.items.length === 1) return current.items[0].id
  return null
}

function autoSelectIfNeeded() {
  if (props.modelValue != null) return
  const next = defaultSelection()
  if (next != null) emit('update:modelValue', next)
}

function onSelect(value: unknown) {
  const next = value === undefined || value === null || value === '' ? null : Number(value)
  emit('update:modelValue', next)
  emit('change', next)
}

async function fetchOptions(force = false) {
  loading.value = true
  try {
    options.value = await loadOutletOptions(force)
    autoSelectIfNeeded()
  } catch {
    options.value = null
  } finally {
    loading.value = false
  }
}

function reload() {
  return fetchOptions(true)
}

function selectDefault() {
  emit('update:modelValue', defaultSelection())
}

onMounted(() => fetchOptions())

defineExpose({ reload, selectDefault })
</script>

<style scoped>
.outlet-select {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.outlet-pending-tag {
  align-self: flex-start;
}

.outlet-option-name {
  color: #0f172a;
  font-weight: 600;
}

.outlet-option-code {
  margin-left: 8px;
  color: #94a3b8;
  font-size: 12px;
}
</style>

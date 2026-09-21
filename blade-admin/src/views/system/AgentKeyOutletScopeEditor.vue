<template>
  <div class="w-full" data-testid="agent-key-outlet-scope-editor">
    <el-radio-group :model-value="scopeType" @update:model-value="handleScopeTypeChange">
      <el-radio-button value="ALL">全部档口</el-radio-button>
      <el-radio-button value="ASSIGNED">指定档口</el-radio-button>
      <el-radio-button value="NONE">不开放档口数据</el-radio-button>
    </el-radio-group>

    <div v-if="scopeType === 'ASSIGNED'" class="mt-3 w-full">
      <el-select
        :model-value="outletIds"
        :loading="loading"
        data-testid="agent-key-assigned-outlets"
        multiple
        filterable
        clearable
        class="w-full"
        placeholder="请选择指定档口"
        @update:model-value="handleOutletIdsChange"
      >
        <el-option
          v-for="option in options"
          :key="option.id"
          :label="`${option.outletCode} ${option.outletName}`"
          :value="option.id"
          :disabled="isOptionDisabled(option)"
        >
          <span class="flex w-full items-center justify-between gap-2">
            <span>{{ option.outletCode }} {{ option.outletName }}</span>
            <el-tag v-if="option.status === 0" size="small" type="info">已停用</el-tag>
            <el-tag v-else-if="option.tenantDefault" size="small" type="success">租户默认</el-tag>
          </span>
        </el-option>
      </el-select>
      <p class="mt-1 text-xs text-slate-400">已停用档口只保留历史绑定，不能新选；取消后需重新启用才能再选。</p>

      <div class="mt-3">
        <label class="mb-1 block text-sm font-medium text-slate-700">默认档口</label>
        <el-select
          :model-value="defaultOutletId"
          data-testid="agent-key-assigned-default-outlet"
          clearable
          class="w-full"
          placeholder="从已选且启用的档口中选默认档口"
          @update:model-value="emit('update:defaultOutletId', $event ?? null)"
        >
          <el-option
            v-for="option in assignedDefaultCandidates"
            :key="option.id"
            :label="`${option.outletCode} ${option.outletName}`"
            :value="option.id"
          />
        </el-select>
      </div>
    </div>

    <div v-else-if="scopeType === 'ALL'" class="mt-3 w-full">
      <label class="mb-1 block text-sm font-medium text-slate-700">默认档口（可选）</label>
      <el-select
        :model-value="defaultOutletId"
        data-testid="agent-key-all-default-outlet"
        clearable
        class="w-full"
        placeholder="从启用的档口中选择默认档口"
        @update:model-value="emit('update:defaultOutletId', $event ?? null)"
      >
        <el-option
          v-for="option in enabledOptions"
          :key="option.id"
          :label="`${option.outletCode} ${option.outletName}`"
          :value="option.id"
        />
      </el-select>
      <p class="mt-1 text-xs text-slate-400">Agent 可访问当前租户全部档口，失败时回退到默认档口。</p>
    </div>

    <p v-else class="mt-3 text-xs text-slate-400">
      不向 Agent 开放任何档口数据，订单与分析类接口会因缺少档口范围而受限。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AgentKeyOutletScopeType, AgentOutletOption } from '@/api/agentKey'

const props = defineProps<{
  scopeType: AgentKeyOutletScopeType
  outletIds: number[]
  defaultOutletId: number | null
  options: AgentOutletOption[]
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:scopeType', value: AgentKeyOutletScopeType): void
  (e: 'update:outletIds', value: number[]): void
  (e: 'update:defaultOutletId', value: number | null): void
}>()

const enabledOptions = computed(() => props.options.filter((option) => option.status === 1))

const assignedDefaultCandidates = computed(() =>
  props.options.filter((option) => option.status === 1 && props.outletIds.includes(option.id)),
)

// 停用档口：仅当已经在当前选择中（历史绑定）时可选，避免被新选进来。
function isOptionDisabled(option: AgentOutletOption) {
  return option.status === 0 && !props.outletIds.includes(option.id)
}

function handleScopeTypeChange(value: string | number | boolean | undefined) {
  const next = (value || 'NONE') as AgentKeyOutletScopeType
  emit('update:scopeType', next)
  if (next === 'NONE') {
    emit('update:outletIds', [])
    emit('update:defaultOutletId', null)
    return
  }
  if (next === 'ALL') {
    emit('update:outletIds', [])
    if (props.defaultOutletId != null && !enabledOptions.value.some((option) => option.id === props.defaultOutletId)) {
      emit('update:defaultOutletId', null)
    }
    return
  }
  // ASSIGNED：切过来的默认档口必须仍属于已选集合
  if (props.defaultOutletId != null && !props.outletIds.includes(props.defaultOutletId)) {
    emit('update:defaultOutletId', null)
  }
}

function handleOutletIdsChange(ids: Array<number | string> | undefined) {
  const nextIds = (ids || []).map((id) => Number(id))
  emit('update:outletIds', nextIds)
  const candidates = enabledOptions.value.filter((option) => nextIds.includes(option.id))
  if (props.defaultOutletId != null && candidates.some((option) => option.id === props.defaultOutletId)) {
    return
  }
  // 只有一个可用档口时自动设为默认，其余情况清空非法默认。
  emit('update:defaultOutletId', candidates.length === 1 ? candidates[0].id : null)
}
</script>

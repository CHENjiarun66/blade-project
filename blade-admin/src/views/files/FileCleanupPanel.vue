<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="未绑定文件清理"
    width="520px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="space-y-5">
      <!-- 清理说明 -->
      <div class="bg-blue-50 border border-blue-100 rounded-lg p-4">
        <div class="flex items-start gap-2">
          <span class="material-symbols-outlined text-blue-500 text-xl flex-shrink-0">info</span>
          <div class="text-xs text-blue-800">
            <p class="font-bold mb-1">清理说明</p>
            <ul class="list-disc pl-4 space-y-0.5">
              <li>仅清理<strong>未绑定</strong>且<strong>未归档到文件夹</strong>的文件</li>
              <li>仅清理超过指定天数的文件</li>
              <li>清理采用软删除，可在回收站查看</li>
              <li>已绑定的业务文件（商品图、订单图等）<strong>不会被清理</strong></li>
              <li>归档到文件夹的文件<strong>不会被清理</strong></li>
            </ul>
          </div>
        </div>
      </div>

      <!-- 保留天数 -->
      <div>
        <label class="block text-xs font-bold text-gray-700 mb-2">保留天数</label>
        <div class="flex items-center gap-3">
          <el-input-number
            v-model="retentionDays"
            :min="7"
            :max="365"
            :step="1"
            class="w-[160px]"
          />
          <span class="text-xs text-gray-400">超过此天数的未绑定文件将被清理</span>
        </div>
      </div>

      <!-- 候选文件统计 -->
      <div class="bg-gray-50 rounded-lg p-4">
        <div class="flex items-center justify-between">
          <span class="text-sm text-gray-700">候选未绑定文件</span>
          <div class="flex items-center gap-2">
            <span v-if="candidateCountLoading" class="text-xs text-gray-400">查询中...</span>
            <span
              v-else
              class="text-xl font-bold"
              :class="candidateCount > 0 ? 'text-amber-600' : 'text-emerald-600'"
            >
              {{ candidateCount }}
            </span>
            <span class="text-xs text-gray-400">个</span>
          </div>
        </div>
        <div class="mt-2 flex gap-2">
          <el-button size="small" class="!text-xs" @click="refreshCandidateCount" :disabled="candidateCountLoading">
            <span class="material-symbols-outlined text-sm mr-1">refresh</span>
            刷新统计
          </el-button>
        </div>
      </div>

      <!-- 回收站入口 -->
      <div class="flex items-center justify-between bg-gray-50 rounded-lg p-3">
        <div class="flex items-center gap-2 text-xs text-gray-600">
          <span class="material-symbols-outlined text-gray-400">delete</span>
          <span>查看已删除文件</span>
        </div>
        <el-button size="small" class="!text-xs" @click="handleGoTrash">
          <span class="material-symbols-outlined text-sm mr-1">arrow_forward</span>
          前往回收站
        </el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
      <el-button
        type="danger"
        :disabled="candidateCount === 0 || softDeleting"
        @click="handleSoftDelete"
      >
        <span class="material-symbols-outlined text-sm mr-1">cleaning_services</span>
        {{ softDeleting ? '清理中...' : `软删除 ${candidateCount} 个文件` }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { getUnboundCandidates, softDeleteUnbound } from '@/api/file'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'goTrash'): void
}>()

const retentionDays = ref(7)
const candidateCount = ref(0)
const candidateCountLoading = ref(false)
const softDeleting = ref(false)

async function refreshCandidateCount() {
  candidateCountLoading.value = true
  try {
    const res = await getUnboundCandidates(retentionDays.value)
    candidateCount.value = res.data?.candidateCount ?? 0
  } catch {
    candidateCount.value = 0
  } finally {
    candidateCountLoading.value = false
  }
}

async function handleSoftDelete() {
  if (candidateCount.value === 0) {
    ElMessage.warning('没有可清理的文件')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认软删除 ${candidateCount.value} 个未绑定超期文件？这些文件将被标记为已删除并可在回收站中查看。`,
      '确认清理',
      {
        confirmButtonText: '确认清理',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  } catch {
    return
  }

  softDeleting.value = true
  try {
    const res = await softDeleteUnbound(retentionDays.value)
    ElMessage.success(`已成功清理 ${res.data?.processedCount ?? candidateCount.value} 个文件`)
    refreshCandidateCount()
  } catch (error: any) {
    ElMessage.error(error.message || '清理失败')
  } finally {
    softDeleting.value = false
  }
}

function handleGoTrash() {
  emit('goTrash')
}

// 打开面板时自动刷新
watch(() => props.modelValue, (val) => {
  if (val) {
    refreshCandidateCount()
  }
})
</script>

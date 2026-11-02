<template>
  <dialog ref="dialog" class="modal number-pad-modal" aria-label="数字键盘" @keydown="keyboard" @close="closed">
    <div class="modal-top"><div><p class="number-pad-caption">{{ request?.mode === 'quantity' ? '总数量 · 件' : '本次单价 · 元' }}</p><h2>{{ request?.label }}</h2></div>
      <button type="button" class="icon-button" aria-label="取消数字输入" @click="dialog?.close()"><CatalogIcon name="close" /></button></div>
    <output class="number-pad-value" :class="{ 'will-replace': replacing && !!draft }" aria-live="polite" aria-label="当前输入">{{ draft || (request?.mode === 'quantity' ? '待填数量' : '待报价') }}</output>
    <div class="number-pad-edit"><span>{{ draft && replacing ? '输入新数字即可替换' : '可留空，交由店员补充' }}</span><button type="button" @click="press('clear')">清空</button><button type="button" aria-label="退格" @click="press('backspace')">退格</button></div>
    <div class="number-pad-grid"><button v-for="key in keys" :key="key" type="button" :disabled="key === '.' && request?.mode !== 'price'" :aria-label="key === '.' ? '小数点' : `数字${key}`" @click="press(key)">{{ key }}</button></div>
    <p v-if="!valid" class="number-pad-error" role="alert">{{ request?.mode === 'quantity' ? '数量须为 1–999999 的整数，也可留空' : '单价最多 6 位整数、2 位小数' }}</p>
    <div class="modal-actions"><button type="button" class="secondary-button" @click="dialog?.close()">取消</button><button type="button" class="primary-button" :disabled="!valid" @click="confirm">确定<CatalogIcon name="check" /></button></div>
  </dialog>
</template>
<script setup lang="ts">
import { ref, shallowRef, computed } from 'vue'
import CatalogIcon from './CatalogIcon.vue'
import type { NumberEntryRequest } from './numericEntry'
import { keypadValue, validKeypadValue, committedKeypadValue } from './numericKeypadModel'
const dialog = ref<HTMLDialogElement>(), request = shallowRef<NumberEntryRequest | null>(null)
const draft = ref(''), replacing = ref(true)
const keys = ['7','8','9','4','5','6','1','2','3','00','0','.']
const valid = computed(() => !request.value || validKeypadValue(draft.value, request.value.mode))
function closed() { if (!dialog.value?.open) request.value = null }
function open(value: NumberEntryRequest) { if (dialog.value?.open) return; request.value = value; draft.value = value.value; replacing.value = true; dialog.value?.showModal() }
function press(key: string) {
  if (!request.value) return
  draft.value = keypadValue(draft.value, key, request.value.mode, replacing.value)
  replacing.value = false
}
function confirm() {
  if (!request.value || !valid.value) return
  request.value.commit(committedKeypadValue(draft.value, request.value.mode))
  dialog.value?.close()
}
function keyboard(event: KeyboardEvent) {
  if (/^\d$/.test(event.key) || event.key === '.') { event.preventDefault(); press(event.key) }
  else if (event.key === 'Backspace') { event.preventDefault(); press('backspace') }
  else if (event.key === 'Delete') { event.preventDefault(); press('clear') }
  else if (event.key === 'Enter') { event.preventDefault(); confirm() }
}
defineExpose({ open })
</script>

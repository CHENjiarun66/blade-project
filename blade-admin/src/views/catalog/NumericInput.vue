<template>
  <span class="catalog-number-field">
    <input v-bind="$attrs" :value="modelValue" type="text" :inputmode="touchInput ? 'none' : mode === 'price' ? 'decimal' : 'numeric'"
      :readonly="touchInput" :maxlength="mode === 'quantity' ? 6 : 12" :aria-label="label" :placeholder="placeholder"
      :aria-haspopup="touchInput ? 'dialog' : undefined" autocomplete="off" autocorrect="off" spellcheck="false"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)" @change="emit('change')"
      @click="touchInput && openPad()" @keydown="touchKey">
    <button type="button" class="number-pad-trigger" :aria-label="`使用数字键盘输入${label}`" @click="openPad">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="1"/><path d="M7 9h1m3 0h1m3 0h1M7 12h1m3 0h1m3 0h1M7 15h9"/></svg>
    </button>
  </span>
</template>
<script setup lang="ts">
import { inject, ref, onMounted, onBeforeUnmount } from 'vue'
import { numberEntryKey, type NumberMode } from './numericEntry'
defineOptions({ inheritAttrs: false })
const props = withDefaults(defineProps<{ modelValue: string; mode: NumberMode; label: string; placeholder?: string }>(), { placeholder: '' })
const emit = defineEmits<{ 'update:modelValue': [string]; change: [] }>()
const open = inject(numberEntryKey)
const media = window.matchMedia('(pointer: coarse)')
const touchInput = ref(media.matches || navigator.maxTouchPoints > 0)
function syncTouch() { touchInput.value = media.matches || navigator.maxTouchPoints > 0 }
function openPad() {
  if (!open) return
  if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
  open({ label: props.label, mode: props.mode, value: props.modelValue,
    commit(value) { emit('update:modelValue', value); emit('change') } })
}
function touchKey(event: KeyboardEvent) {
  if (touchInput.value && (event.key === 'Enter' || /^\d$/.test(event.key))) { event.preventDefault(); openPad() }
}
onMounted(() => media.addEventListener('change', syncTouch))
onBeforeUnmount(() => media.removeEventListener('change', syncTouch))
</script>

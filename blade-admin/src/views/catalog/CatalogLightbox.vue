<template>
  <dialog ref="dialog" class="lightbox" aria-label="商品大图" @close="closed" @keydown="keydown" @pointerdown="backdropStart" @pointermove="backdropMove" @pointerup="backdropEnd" @pointercancel="resetBackdrop" @click="backdropClick">
    <div class="lightbox-toolbar"><button type="button" class="lightbox-close icon-button" aria-label="关闭大图" @click="dialog?.close()"><CatalogIcon name="close" :size="20" /></button></div>
    <div class="lightbox-gallery"><PhotoGallery v-model="viewerIndex" :images="images" :product-id="productId" :code="code" :name="name" context="lightbox" /></div>
  </dialog>
</template>
<script setup lang="ts">
import { ref, computed } from 'vue'
import CatalogIcon from './CatalogIcon.vue'
import PhotoGallery from './PhotoGallery.vue'
import { useLightboxScrollLock } from './useLightboxScrollLock'
const props = defineProps<{ images: string[]; modelValue?: number; productId: number; code: string; name: string; background?: HTMLElement }>()
const emit = defineEmits<{ 'update:modelValue': [number] }>()
const dialog = ref<HTMLDialogElement>(), viewerIndex = ref(0)
const { lock, unlock } = useLightboxScrollLock(computed(() => props.background))
let backdrop: { id: number; x: number; y: number; moved: boolean; ended: boolean } | undefined
function outside(event: MouseEvent) {
  const bounds = dialog.value?.getBoundingClientRect()
  return !!bounds && (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom)
}
function resetBackdrop() { backdrop = undefined }
function backdropStart(event: PointerEvent) {
  resetBackdrop()
  if (event.isPrimary && event.button === 0 && event.target === dialog.value && outside(event)) backdrop = { id: event.pointerId, x: event.clientX, y: event.clientY, moved: false, ended: false }
}
function backdropMove(event: PointerEvent) {
  if (backdrop?.id === event.pointerId && Math.hypot(event.clientX - backdrop.x, event.clientY - backdrop.y) > 8) backdrop.moved = true
}
function backdropEnd(event: PointerEvent) {
  if (backdrop?.id !== event.pointerId) return
  backdropMove(event)
  backdrop.ended = event.target === dialog.value && outside(event)
}
function backdropClick(event: MouseEvent) {
  const dismiss = backdrop?.ended && !backdrop.moved && event.target === dialog.value && outside(event)
  resetBackdrop()
  if (dismiss) { event.preventDefault(); event.stopPropagation(); dialog.value?.close() }
}
function open() {
  if (!dialog.value || dialog.value.open || !props.images.length) return
  resetBackdrop()
  viewerIndex.value = Math.max(0, Math.min(props.modelValue || 0, props.images.length - 1))
  lock()
  try { dialog.value.showModal() } catch (error) { unlock(); throw error }
}
function closed() {
  if (dialog.value?.open) return
  resetBackdrop()
  // Keep the underlying photo still while viewing; synchronize only when the viewer closes.
  emit('update:modelValue', viewerIndex.value)
  unlock()
}
function keydown(event: KeyboardEvent) {
  if (event.defaultPrevented || !['ArrowLeft','ArrowRight'].includes(event.key)) return
  viewerIndex.value = Math.max(0, Math.min(props.images.length - 1, viewerIndex.value + (event.key === 'ArrowRight' ? 1 : -1)))
  event.preventDefault(); event.stopPropagation()
}
defineExpose({ open })
</script>

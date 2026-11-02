<template>
  <dialog ref="dialog" class="lightbox" aria-label="商品大图" @close="closed" @keydown="keydown">
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
function open() {
  if (!dialog.value || dialog.value.open || !props.images.length) return
  viewerIndex.value = Math.max(0, Math.min(props.modelValue || 0, props.images.length - 1))
  lock()
  try { dialog.value.showModal() } catch (error) { unlock(); throw error }
}
function closed() {
  if (dialog.value?.open) return
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

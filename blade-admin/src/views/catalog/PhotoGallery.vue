<template>
  <div ref="root" class="photo-gallery" :class="[`gallery-${context}`, { 'is-dragging': dragging }]" :data-active-index="index" :data-gallery-count="images.length" :data-product-id="productId" role="group" aria-roledescription="图片轮播" :aria-label="`${code}商品照片，共${images.length}张`" tabindex="0" :style="trackVars" @pointerdown.passive="start" @click.capture="guardClick" @keydown="onKeydown" @dragstart.prevent>
    <div ref="surface" class="gallery-viewport">
      <div class="gallery-track">
        <div v-for="(url, i) in images" :key="url" class="gallery-slide" :aria-hidden="i !== index" @click="context !== 'lightbox' && emit('open')">
          <CatalogImage v-if="visible && Math.abs(i - index) <= 1" :src="imageUrl(url, context === 'lightbox' ? 'original' : 'card')" :alt="`${name} · 照片${i + 1}`" eager />
        </div>
        <div v-if="!images.length" class="gallery-slide image-placeholder">暂无图片</div>
      </div>
    </div>
    <button v-if="images.length && context !== 'lightbox'" type="button" class="gallery-open-accessible" :aria-label="`放大查看${name}，第${index + 1}/${images.length}张`" @click="emit('open')"><span class="sr-only">放大图片</span></button>
    <template v-if="images.length > 1">
      <button type="button" class="gallery-arrow gallery-prev" :disabled="index === 0" :aria-label="`${code}上一张图片`" @click.stop="setIndex(index - 1)"><CatalogIcon name="arrow" :size="19" /></button>
      <button type="button" class="gallery-arrow gallery-next" :disabled="index === images.length - 1" :aria-label="`${code}下一张图片`" @click.stop="setIndex(index + 1)"><CatalogIcon name="arrow" :size="19" /></button>
      <span class="gallery-counter" aria-live="polite" aria-atomic="true">{{ index + 1 }} / {{ images.length }}</span>
    </template>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import CatalogIcon from './CatalogIcon.vue'
import CatalogImage from './CatalogImage.vue'
import { imageUrl } from './catalogImages'
import { gestureAxis, swipeOffset, swipeTarget, recentVelocity, type GestureAxis } from './galleryGesture'
const props = withDefaults(defineProps<{ images: string[]; modelValue?: number; productId: number; code: string; name: string; context?: 'card' | 'detail' | 'lightbox' }>(), { context: 'card', modelValue: 0 })
const emit = defineEmits<{ 'update:modelValue': [number]; open: [] }>()
const root = ref<HTMLElement>(), surface = ref<HTMLElement>(), visible = ref(props.context !== 'card'), dragging = ref(false), offset = ref(0)
const index = computed(() => Math.max(0, Math.min(props.modelValue, Math.max(0, props.images.length - 1))))
const trackVars = computed(() => ({ '--gallery-index': index.value, '--gallery-offset': `${offset.value}px` }))
let observer: IntersectionObserver | undefined, frame = 0, blockedUntil = 0
let active: { pointerId: number; x: number; y: number; dx: number; dy: number; width: number; index: number; axis: GestureAxis; samples: { x: number; time: number }[] } | null = null
function setIndex(value: number) { emit('update:modelValue', Math.max(0, Math.min(value, props.images.length - 1))) }
function start(event: PointerEvent) {
  if (active && active.pointerId !== event.pointerId) finish(true)
  if (!surface.value || event.isPrimary === false || (event.pointerType === 'mouse' && event.button !== 0)) return
  blockedUntil = 0
  active = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, dx: 0, dy: 0, width: surface.value.getBoundingClientRect().width, index: index.value, axis: 'pending', samples: [{ x: event.clientX, time: event.timeStamp }] }
  document.addEventListener('pointermove', move, { passive: false })
  document.addEventListener('pointerup', end, { passive: true })
  document.addEventListener('pointercancel', cancel, { passive: true })
  document.addEventListener('lostpointercapture', lostCapture)
  document.addEventListener('pointerdown', secondPointer, true)
}
function move(event: PointerEvent) {
  const a = active
  if (!a || event.pointerId !== a.pointerId) return
  a.dx = event.clientX - a.x; a.dy = event.clientY - a.y
  a.samples.push({ x: event.clientX, time: event.timeStamp }); a.samples = a.samples.slice(-12)
  const previous = a.axis
  a.axis = gestureAxis(a.dx, a.dy, a.axis)
  if (a.axis !== 'horizontal' || props.images.length < 2) return
  if (previous !== 'horizontal') { surface.value?.setPointerCapture(event.pointerId); dragging.value = true }
  if (event.cancelable) event.preventDefault()
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(() => { if (active === a) offset.value = swipeOffset(a.dx, a.index, props.images.length, a.width) })
}
function end(event: PointerEvent) {
  if (!active || event.pointerId !== active.pointerId) return
  active.dx = event.clientX - active.x; active.dy = event.clientY - active.y
  active.samples.push({ x: event.clientX, time: event.timeStamp }); finish(false)
}
function cancel(event: PointerEvent) { if (active?.pointerId === event.pointerId) finish(true) }
function secondPointer(event: PointerEvent) { if (active && active.pointerId !== event.pointerId) finish(true) }
function lostCapture(event: PointerEvent) { if (event.target === surface.value) cancel(event) }
function finish(cancelled: boolean) {
  const a = active; active = null
  document.removeEventListener('pointermove', move); document.removeEventListener('pointerup', end)
  document.removeEventListener('pointercancel', cancel); document.removeEventListener('lostpointercapture', lostCapture)
  document.removeEventListener('pointerdown', secondPointer, true)
  cancelAnimationFrame(frame); dragging.value = false; offset.value = 0
  if (!a) return
  if (cancelled || Math.hypot(a.dx, a.dy) >= 8) blockedUntil = performance.now() + 450
  if (surface.value?.hasPointerCapture(a.pointerId)) surface.value.releasePointerCapture(a.pointerId)
  if (!cancelled) setIndex(swipeTarget(a.axis, a.dx, recentVelocity(a.samples, a.samples[a.samples.length - 1]?.time || performance.now()), a.width, a.index, props.images.length))
}
function guardClick(event: MouseEvent) {
  if (event.detail && performance.now() < blockedUntil) { blockedUntil = 0; event.preventDefault(); event.stopImmediatePropagation() }
}
function onKeydown(event: KeyboardEvent) {
  if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
    blockedUntil = 0; event.preventDefault(); event.stopPropagation()
    setIndex(event.key === 'Home' ? 0 : event.key === 'End' ? props.images.length - 1 : index.value + (event.key === 'ArrowRight' ? 1 : -1))
  } else if (event.key === 'Enter' && props.context !== 'lightbox' && event.target === root.value) { emit('open') }
}
function resetGesture() { finish(true) }
function onVisibility() { if (document.hidden) resetGesture() }
onMounted(() => {
  if (!('IntersectionObserver' in window)) visible.value = true
  else if (!visible.value) {
    observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) { visible.value = true; observer?.disconnect() } }, { rootMargin: '180px' })
    if (root.value) observer.observe(root.value)
  }
  window.addEventListener('resize', resetGesture); window.addEventListener('blur', resetGesture)
  document.addEventListener('visibilitychange', onVisibility)
})
watch(() => props.images.join('|'), () => { finish(true); if (props.modelValue !== index.value) emit('update:modelValue', index.value) })
onBeforeUnmount(() => { finish(true); observer?.disconnect(); window.removeEventListener('resize', resetGesture); window.removeEventListener('blur', resetGesture); document.removeEventListener('visibilitychange', onVisibility) })
</script>

<template>
  <div ref="root" class="photo-gallery" :class="[`gallery-${context}`, { 'is-dragging': dragging }]" :data-active-index="index" :data-gallery-count="images.length" :data-product-id="productId" role="group" aria-roledescription="图片轮播" :aria-label="`${code}商品照片，共${images.length}张`" tabindex="0" @pointerdown.passive="pointerStart" @click.capture="guardClick" @keydown="onKeydown" @dragstart.prevent>
    <div class="gallery-viewport">
      <div ref="track" class="gallery-track" :style="trackStyle">
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
import { usePhotoGesture } from './usePhotoGesture'
const props = withDefaults(defineProps<{ images: string[]; modelValue?: number; productId: number; code: string; name: string; context?: 'card' | 'detail' | 'lightbox' }>(), { context: 'card', modelValue: 0 })
const emit = defineEmits<{ 'update:modelValue': [number]; open: [] }>()
const root = ref<HTMLElement>(), track = ref<HTMLElement>(), visible = ref(props.context !== 'card'), dragging = ref(false)
const index = computed(() => Math.max(0, Math.min(props.modelValue, Math.max(0, props.images.length - 1))))
const trackStyle = computed(() => ({ transform: `translate3d(${-index.value * 100}%,0,0)` }))
let observer: IntersectionObserver | undefined
function setIndex(value: number) { emit('update:modelValue', Math.max(0, Math.min(value, props.images.length - 1))) }
const { pointerStart, guardClick, reset: resetGesture } = usePhotoGesture({ root, track, index: () => index.value, count: () => props.images.length, lightbox: () => props.context === 'lightbox', setIndex, dragging })
function onKeydown(event: KeyboardEvent) {
  if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
    event.preventDefault(); event.stopPropagation()
    setIndex(event.key === 'Home' ? 0 : event.key === 'End' ? props.images.length - 1 : index.value + (event.key === 'ArrowRight' ? 1 : -1))
  } else if (event.key === 'Enter' && props.context !== 'lightbox' && event.target === root.value) { emit('open') }
}
onMounted(() => {
  if (!('IntersectionObserver' in window)) visible.value = true
  else if (!visible.value) {
    observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) { visible.value = true; observer?.disconnect() } }, { rootMargin: '180px' })
    if (root.value) observer.observe(root.value)
  }
})
watch(() => props.images.join('|'), () => { resetGesture(); if (props.modelValue !== index.value) emit('update:modelValue', index.value) })
onBeforeUnmount(() => observer?.disconnect())
</script>

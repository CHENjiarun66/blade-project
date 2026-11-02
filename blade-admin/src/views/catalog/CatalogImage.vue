<template>
  <span ref="host" class="catalog-image">
    <CachedImage v-if="visible && src && !failed" :src="src" :alt="alt" image-class="catalog-photo" loading="eager" decoding="async" draggable="false" @error="failed = true" />
    <span v-else class="image-placeholder" :role="failed ? 'status' : undefined">{{ failed ? '图片加载失败' : src ? '' : '暂无图片' }}</span>
    <button v-if="failed" type="button" class="image-retry" aria-label="重试加载图片" @click.stop="failed = false">重试</button>
  </span>
</template>
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import CachedImage from '@/components/CachedImage.vue'
const props = withDefaults(defineProps<{ src: string; alt: string; eager?: boolean }>(), { eager: false })
const host = ref<HTMLElement>(), visible = ref(props.eager), failed = ref(false)
let observer: IntersectionObserver | undefined
onMounted(() => {
  if (props.eager || !('IntersectionObserver' in window)) { visible.value = true; return }
  observer = new IntersectionObserver(entries => {
    if (entries.some(entry => entry.isIntersecting)) { visible.value = true; observer?.disconnect() }
  }, { rootMargin: '180px' })
  if (host.value) observer.observe(host.value)
})
watch(() => props.src, () => { failed.value = false })
onBeforeUnmount(() => observer?.disconnect())
</script>

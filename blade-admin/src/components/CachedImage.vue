<template>
  <img
    :src="displaySrc"
    :alt="alt"
    :class="imageClass"
    :loading="loading"
  />
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { getCachedImageUrl } from '@/utils/catalogCache'

const props = withDefaults(defineProps<{
  src: string
  alt?: string
  imageClass?: string
  loading?: 'eager' | 'lazy'
}>(), {
  alt: '',
  imageClass: '',
  loading: 'lazy',
})

const displaySrc = ref(props.src)
let requestId = 0

async function loadCachedImage(src: string) {
  const currentRequest = ++requestId
  displaySrc.value = src
  try {
    const cachedUrl = await getCachedImageUrl(src)
    if (currentRequest === requestId) {
      displaySrc.value = cachedUrl
    }
  } catch {
    if (currentRequest === requestId) {
      displaySrc.value = src
    }
  }
}

onMounted(() => loadCachedImage(props.src))
watch(() => props.src, (src) => loadCachedImage(src))
</script>

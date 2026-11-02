<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" v-html="paths[name]" />
</template>
<script setup lang="ts">
// Exact icon paths from the customer-approved v4.1 static preview.
const paths = {
  "search": "<circle cx=\"10.5\" cy=\"10.5\" r=\"6.5\"/><path d=\"m16 16 4.5 4.5\"/>",
  "bag": "<path d=\"M5 7h14l1 14H4L5 7Z\"/><path d=\"M8 8V6a4 4 0 0 1 8 0v2\"/>",
  "heart": "<path d=\"M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z\"/>",
  "sliders": "<path d=\"M4 7h6m4 0h6M4 17h2m4 0h10\"/><circle cx=\"12\" cy=\"7\" r=\"2\"/><circle cx=\"8\" cy=\"17\" r=\"2\"/>",
  "close": "<path d=\"m6 6 12 12M6 18 18 6\"/>",
  "arrow": "<path d=\"M4 12h15m-6-6 6 6-6 6\"/>",
  "back": "<path d=\"M20 12H5m6-6-6 6 6 6\"/>",
  "plus": "<path d=\"M12 5v14M5 12h14\"/>",
  "minus": "<path d=\"M5 12h14\"/>",
  "check": "<path d=\"m5 12 4 4L19 6\"/>",
  "grid": "<rect x=\"3\" y=\"3\" width=\"7\" height=\"7\" rx=\"1\"/><rect x=\"14\" y=\"3\" width=\"7\" height=\"7\" rx=\"1\"/><rect x=\"3\" y=\"14\" width=\"7\" height=\"7\" rx=\"1\"/><rect x=\"14\" y=\"14\" width=\"7\" height=\"7\" rx=\"1\"/>",
  "rotate": "<path d=\"M3 10a9 9 0 1 1 2 8M3 4v6h6\"/>",
  "zoom": "<circle cx=\"10\" cy=\"10\" r=\"6\"/><path d=\"m15 15 5 5M10 7v6M7 10h6\"/>"
} as const
withDefaults(defineProps<{ name: keyof typeof paths; size?: number }>(), { size: 20 })
</script>

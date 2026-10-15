<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  content: string | string[]
  width?: number
  height?: number
  rotate?: number
  opacity?: number
  fontSize?: number
  color?: string
  zIndex?: number
  fullscreen?: boolean
}>(), { width: 180, height: 120, rotate: -22, opacity: 0.12, fontSize: 14, color: 'currentColor', zIndex: 10 })
const lines = computed(() => Array.isArray(props.content) ? props.content : [props.content])
const watermark = computed(() => {
  const text = lines.value.map((line, index) => `<text x="50%" y="${50 + index * (props.fontSize + 4)}" text-anchor="middle" dominant-baseline="middle">${escapeXml(line)}</text>`).join('')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${props.width}" height="${props.height}" viewBox="0 0 ${props.width} ${props.height}"><g fill="${escapeXml(props.color)}" fill-opacity="${props.opacity}" font-family="sans-serif" font-size="${props.fontSize}" transform="rotate(${props.rotate} ${props.width / 2} ${props.height / 2})">${text}</g></svg>`
  return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`
})
function escapeXml(value: string) {
  return value.replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', '\'': '&apos;' })[char]!)
}
</script>

<template>
  <div class="yd-watermark" :class="{ 'yd-watermark--fullscreen': fullscreen }" :style="{ backgroundImage: watermark, zIndex }">
    <slot />
  </div>
</template>

<style scoped>
.yd-watermark { position: relative; isolation: isolate; }
.yd-watermark::after { position: absolute; inset: 0; z-index: 1; pointer-events: none; content: ''; background-image: inherit; }
.yd-watermark--fullscreen { position: fixed; inset: 0; }
</style>

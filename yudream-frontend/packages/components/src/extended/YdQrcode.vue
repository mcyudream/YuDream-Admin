<script setup lang="ts">
import QRCode from 'qrcode'
import { computed, onMounted, onScopeDispose, ref, watch } from 'vue'
import './extended.css'

const props = withDefaults(defineProps<{
  value: string
  size?: number
  margin?: number
  errorCorrectionLevel?: 'L' | 'M' | 'Q' | 'H'
  foreground?: string
  background?: string
  label?: string
}>(), { size: 180, margin: 4, errorCorrectionLevel: 'M', foreground: '#000000', background: '#ffffff', label: '二维码' })
const emit = defineEmits<{
  ready: [dataUrl: string]
  error: [error: Error]
}>()
const url = ref('')
const pending = ref(false)
const failed = ref(false)
const size = computed(() => Math.max(64, Math.min(2048, Number.isFinite(props.size) ? props.size : 180)))
let version = 0
let mounted = false
async function generate() {
  if (!mounted) {
    return
  }
  const current = ++version
  pending.value = true
  failed.value = false
  url.value = ''
  try {
    const result = await QRCode.toDataURL(props.value, {
      width: size.value,
      margin: Math.max(0, Number.isFinite(props.margin) ? Math.floor(props.margin) : 4),
      errorCorrectionLevel: props.errorCorrectionLevel,
      color: { dark: props.foreground, light: props.background },
    })
    if (current !== version) {
      return
    }
    url.value = result
    emit('ready', result)
  }
  catch (error) {
    if (current !== version) {
      return
    }
    failed.value = true
    emit('error', error instanceof Error ? error : new Error(String(error)))
  }
  finally {
    if (current === version) {
      pending.value = false
    }
  }
}
function download(filename = 'qrcode.png') {
  if (!url.value || typeof document === 'undefined') {
    return false
  }
  const anchor = document.createElement('a')
  anchor.download = filename
  anchor.href = url.value
  anchor.click()
  return true
}
watch(() => [props.value, props.size, props.margin, props.errorCorrectionLevel, props.foreground, props.background], generate)
onMounted(() => {
  mounted = true
  void generate()
})
onScopeDispose(() => {
  mounted = false
  version++
})
defineExpose({ download, refresh: generate, dataUrl: url })
</script>

<template>
  <div class="yd-qrcode" :style="{ width: `${size}px`, height: `${size}px` }" :aria-busy="pending">
    <img v-if="url" :src="url" :alt="label" :width="size" :height="size">
    <div v-else class="yd-qrcode-status" role="status">
      <slot name="status" :loading="pending" :error="failed">
        {{ failed ? '二维码生成失败' : '正在生成二维码…' }}
      </slot>
    </div>
  </div>
</template>

<style scoped>
.yd-qrcode {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  max-width: 100%;
  overflow: hidden;
  background: oklch(var(--muted));
  border: 1px solid oklch(var(--border));
  border-radius: var(--yd-control-radius);
}

.yd-qrcode img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.yd-qrcode-status {
  padding: 1rem;
  font-size: 0.8rem;
  color: oklch(var(--muted-foreground));
  text-align: center;
}
</style>

<script setup lang="ts">
import type Cropper from 'cropperjs'
import { onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  src: string
  aspectRatio?: number
  outputType?: 'image/png' | 'image/jpeg' | 'image/webp'
  quality?: number
  outputWidth?: number
}>(), { outputType: 'image/png', quality: 0.92 })
const emit = defineEmits<{ ready: [], error: [error: Error], confirm: [file: File, blob: Blob], cancel: [] }>()
const open = defineModel<boolean>('open', { default: false })
const container = ref<HTMLElement>()
const loading = ref(false)
const errorMessage = ref('')
let cropper: Cropper | undefined
let generation = 0
async function initialize() {
  const id = ++generation
  cropper?.destroy()
  cropper = undefined
  if (!open.value || !props.src || !container.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const [{ default: CropperCtor }] = await Promise.all([import('cropperjs')])
    if (id !== generation || !container.value) {
      return
    }
    const image = new Image()
    image.alt = '待裁剪图片'
    image.src = props.src
    await image.decode()
    if (id !== generation || !container.value) {
      return
    }
    cropper = new CropperCtor(image, { container: container.value })
    const selection = cropper.getCropperSelection()
    if (selection && props.aspectRatio) {
      selection.aspectRatio = props.aspectRatio
    }
    emit('ready')
  }
  catch (cause) {
    const error = cause instanceof Error ? cause : new Error(String(cause))
    errorMessage.value = error.message
    emit('error', error)
  }
  finally {
    if (id === generation) {
      loading.value = false
    }
  }
}
async function confirm() {
  const selection = cropper?.getCropperSelection()
  if (!selection) {
    return
  }
  try {
    const canvas = await selection.$toCanvas({ width: props.outputWidth })
    const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error('无法生成裁剪图片')), props.outputType, props.quality))
    const file = new File([blob], 'cropped-image', { type: blob.type })
    emit('confirm', file, blob)
    open.value = false
  }
  catch (cause) {
    emit('error', cause instanceof Error ? cause : new Error(String(cause)))
  }
}
function cancel() {
  emit('cancel')
  open.value = false
}
watch([open, () => props.src], () => void initialize(), { flush: 'post' })
onBeforeUnmount(() => {
  generation++
  cropper?.destroy()
  cropper = undefined
})
defineExpose({ confirm, cancel })
</script>

<template>
  <div v-if="open" class="yd-cropper" role="dialog" aria-modal="true" aria-label="裁剪图片">
    <div class="yd-cropper__panel">
      <div v-if="loading" role="status">
        正在加载裁剪工具…
      </div>
      <p v-else-if="errorMessage" role="alert">
        裁剪失败：{{ errorMessage }}
      </p>
      <div ref="container" class="yd-cropper__canvas" />
      <div class="yd-cropper__actions">
        <button type="button" @click="cancel">
          取消
        </button>
        <button type="button" :disabled="loading || !!errorMessage" @click="confirm">
          裁剪并确认
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.yd-cropper { position: fixed; inset: 0; z-index: 3000; display: grid; place-items: center; padding: 1rem; background: oklch(0 0 0 / 0.55); }
.yd-cropper__panel { display: grid; gap: 1rem; width: min(100%, 52rem); max-height: 90vh; padding: 1rem; background: oklch(var(--background)); border: 1px solid oklch(var(--border)); border-radius: var(--yd-surface-radius); }
.yd-cropper__canvas { min-height: 20rem; max-height: 65vh; overflow: hidden; }
.yd-cropper__actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
</style>

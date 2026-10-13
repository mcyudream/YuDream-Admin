<script setup lang="ts">
import { useClipboard } from '@vueuse/core'
import FaButton from '../basic/button/index.vue'
import FaIcon from '../basic/icon/index.vue'
import './extended.css'

const props = withDefaults(defineProps<{
  text: string
  label?: string
  disabled?: boolean
}>(), { label: '复制' })
const emit = defineEmits<{
  copied: [
  ]
  error: [
        error: unknown,
  ]
}>()
const { copy, copied } = useClipboard({ legacy: true, copiedDuring: 1800 })
async function run() {
  try {
    await copy(props.text)
    emit('copied')
  }
  catch (error) {
    emit('error', error)
  }
}
</script>

<template>
  <FaButton type="button" variant="outline" size="sm" :disabled="disabled" class="yd-control" @click="run">
    <FaIcon :name="copied ? 'morph:check' : 'morph:copy'" /><span aria-live="polite">{{ copied ? '已复制' : label }}</span>
  </FaButton>
</template>

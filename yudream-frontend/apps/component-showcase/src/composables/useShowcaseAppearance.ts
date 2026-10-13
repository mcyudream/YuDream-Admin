import { useStorage } from '@vueuse/core'
import { computed, watch } from 'vue'

const stored = useStorage('component-showcase:appearance', { preset: 'vibrant', motion: 'system' })
const preset = computed({
  get: () => ['vibrant', 'calm', 'compact'].includes(stored.value.preset) ? stored.value.preset : 'vibrant',
  set: (value: string) => { stored.value.preset = value },
})
const motion = computed({
  get: () => stored.value.motion === 'reduced' ? 'reduced' : 'system',
  set: (value: string) => { stored.value.motion = value },
})
watch([preset, motion], ([style, preference]) => {
  document.documentElement.dataset.adminStyle = style
  document.documentElement.dataset.adminMotion = preference
}, { immediate: true })
export function useShowcaseAppearance() {
  return { preset, motion }
}

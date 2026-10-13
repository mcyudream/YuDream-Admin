import { usePreferredReducedMotion } from '@vueuse/core'
import { computed, onMounted, onScopeDispose, ref } from 'vue'
/** 所有外观保留功能动画；雅致、紧凑的减少偏好仅柔化节奏、幅度与密度。 */
export function useYdMotion() {
  const system = usePreferredReducedMotion()
  const preset = ref('vibrant')
  const preference = ref('system')
  let observer: MutationObserver | undefined
  onMounted(() => {
    const root = document.documentElement
    const sync = () => {
      preset.value = root.dataset.adminStyle || 'vibrant'
      preference.value = root.dataset.adminMotion || 'system'
    }
    sync()
    observer = new MutationObserver(sync)
    observer.observe(root, { attributes: true, attributeFilter: ['data-admin-style', 'data-admin-motion'] })
  })
  onScopeDispose(() => observer?.disconnect())
  const reduced = computed(() => preset.value !== 'vibrant' && (preference.value === 'reduced' || system.value === 'reduce'))
  const tempo = computed(() => preset.value === 'compact' ? 0.6 : preset.value === 'calm' ? 0.8 : 1)
  return { preset, reduced, tempo }
}

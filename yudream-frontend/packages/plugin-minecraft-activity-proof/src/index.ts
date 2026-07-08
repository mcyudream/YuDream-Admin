import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import activityProofStyles from './styles.css?inline'
import ActivityProofPlugin from './ActivityProofPlugin.vue'

export const Export = ActivityProofPlugin

export const routes = {
  Export,
  'minecraft-activity-proof/Export': Export,
}

export function install() {
  if (typeof document === 'undefined') {
    return
  }
  const id = 'yudream-plugin-minecraft-activity-proof-style'
  let style = document.getElementById(id) as HTMLStyleElement | null
  if (!style) {
    style = document.createElement('style')
    style.id = id
    document.head.appendChild(style)
  }
  style.textContent = activityProofStyles
}

export default defineYuDreamPlugin({
  routes,
  default: Export,
  install,
})

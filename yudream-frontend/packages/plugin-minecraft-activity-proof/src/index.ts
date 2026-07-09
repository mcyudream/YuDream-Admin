import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import activityProofStyles from './styles.css?inline'
import ActivityProofPlugin from './ActivityProofPlugin.vue'

export const Export = ActivityProofPlugin
export const Records = ActivityProofPlugin
export const Mine = ActivityProofPlugin

export const routes = {
  Export,
  Records,
  Mine,
  'minecraft-activity-proof/Export': Export,
  'minecraft-activity-proof/Records': Records,
  'minecraft-activity-proof/Mine': Mine,
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

import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import skinStyles from './styles.css?inline'
import SkinPlugin from './SkinPlugin.vue'

export const Dashboard = SkinPlugin
export const Players = SkinPlugin
export const Textures = SkinPlugin
export const Closet = SkinPlugin
export const System = SkinPlugin

export const routes = {
  Dashboard,
  Players,
  Textures,
  Closet,
  System,
  'blessing-skin/Home': Dashboard,
  'blessing-skin/Dashboard': Dashboard,
  'blessing-skin/Players': Players,
  'blessing-skin/Textures': Textures,
  'blessing-skin/Closet': Closet,
  'blessing-skin/System': System,
}

export function install() {
  if (typeof document === 'undefined') {
    return
  }
  const id = 'yudream-plugin-blessing-skin-style'
  if (document.getElementById(id)) {
    return
  }
  const style = document.createElement('style')
  style.id = id
  style.textContent = skinStyles
  document.head.appendChild(style)
}

export default defineYuDreamPlugin({
  routes,
  default: Dashboard,
  install,
})

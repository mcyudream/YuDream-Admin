import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import minecraftStyles from './styles.css?inline'
import MinecraftServerPlugin from './MinecraftServerPlugin.vue'

export const List = MinecraftServerPlugin
export const Detail = MinecraftServerPlugin
export const Admin = MinecraftServerPlugin

export const routes = {
  List,
  Detail,
  Admin,
  'minecraft-server/List': List,
  'minecraft-server/Detail': Detail,
  'minecraft-server/Admin': Admin,
}

export function install() {
  if (typeof document === 'undefined') {
    return
  }
  const id = 'yudream-plugin-minecraft-server-style'
  let style = document.getElementById(id) as HTMLStyleElement | null
  if (!style) {
    style = document.createElement('style')
    style.id = id
    document.head.appendChild(style)
  }
  style.textContent = minecraftStyles
}

export default defineYuDreamPlugin({
  routes,
  default: List,
  install,
})

import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import authlibStyles from './styles.css?inline'
import AuthlibPlugin from './AuthlibPlugin.vue'

export const Home = AuthlibPlugin

export const routes = {
  Home,
  'authlib-injector/Home': Home,
}

export function install() {
  if (typeof document === 'undefined') {
    return
  }
  const id = 'yudream-plugin-authlib-injector-style'
  if (document.getElementById(id)) {
    return
  }
  const style = document.createElement('style')
  style.id = id
  style.textContent = authlibStyles
  document.head.appendChild(style)
}

export default defineYuDreamPlugin({
  routes,
  default: Home,
  install,
})

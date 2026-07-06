import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import authlibStyles from './styles.css?inline'
import DashboardEndpointCard from './components/DashboardEndpointCard.vue'

export const EndpointCard = DashboardEndpointCard

export const routes = {
  EndpointCard,
  'authlib-injector/EndpointCard': EndpointCard,
}

export function install() {
  if (typeof document === 'undefined') {
    return
  }
  const id = 'yudream-plugin-authlib-injector-style'
  let style = document.getElementById(id) as HTMLStyleElement | null
  if (!style) {
    style = document.createElement('style')
    style.id = id
    document.head.appendChild(style)
  }
  style.id = id
  style.textContent = authlibStyles
}

export default defineYuDreamPlugin({
  routes,
  default: DashboardEndpointCard,
  install,
})

import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import styles from './styles.css?inline'
import ProjectProgressPlugin from './ProjectProgressPlugin.vue'

export const Dashboard = ProjectProgressPlugin
export const Projects = ProjectProgressPlugin
export const MyTasks = ProjectProgressPlugin
export const CheckIns = ProjectProgressPlugin
export const Acceptance = ProjectProgressPlugin
export const Settings = ProjectProgressPlugin

export const routes = {
  Dashboard,
  Projects,
  MyTasks,
  CheckIns,
  Acceptance,
  Settings,
  'project-progress/Dashboard': Dashboard,
  'project-progress/Projects': Projects,
  'project-progress/MyTasks': MyTasks,
  'project-progress/CheckIns': CheckIns,
  'project-progress/Acceptance': Acceptance,
  'project-progress/Settings': Settings,
}

export function install() {
  if (typeof document === 'undefined') {
    return
  }
  const id = 'yudream-plugin-project-progress-style'
  let style = document.getElementById(id) as HTMLStyleElement | null
  if (!style) {
    style = document.createElement('style')
    style.id = id
    document.head.appendChild(style)
  }
  style.textContent = styles
}

export default defineYuDreamPlugin({
  routes,
  default: Dashboard,
  install,
})

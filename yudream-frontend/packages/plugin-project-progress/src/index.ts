import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import styles from './styles.css?inline'
import ProjectProgressPlugin from './ProjectProgressPlugin.vue'

export const Dashboard = ProjectProgressPlugin
export const Projects = ProjectProgressPlugin
export const Details = ProjectProgressPlugin
export const TaskCenter = ProjectProgressPlugin
export const MyTasks = ProjectProgressPlugin
export const CheckIns = ProjectProgressPlugin
export const Acceptance = ProjectProgressPlugin
export const Members = ProjectProgressPlugin
export const Settings = ProjectProgressPlugin

export const routes = {
  Dashboard,
  Projects,
  Details,
  TaskCenter,
  MyTasks,
  CheckIns,
  Acceptance,
  Members,
  Settings,
  'project-progress/Dashboard': Dashboard,
  'project-progress/Projects': Projects,
  'project-progress/Details': Details,
  'project-progress/TaskCenter': TaskCenter,
  'project-progress/MyTasks': MyTasks,
  'project-progress/CheckIns': CheckIns,
  'project-progress/Acceptance': Acceptance,
  'project-progress/Members': Members,
  'project-progress/Settings': Settings,
}

export {
  Dashboard as 'project-progress/Dashboard',
  Projects as 'project-progress/Projects',
  Details as 'project-progress/Details',
  TaskCenter as 'project-progress/TaskCenter',
  MyTasks as 'project-progress/MyTasks',
  CheckIns as 'project-progress/CheckIns',
  Acceptance as 'project-progress/Acceptance',
  Members as 'project-progress/Members',
  Settings as 'project-progress/Settings',
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

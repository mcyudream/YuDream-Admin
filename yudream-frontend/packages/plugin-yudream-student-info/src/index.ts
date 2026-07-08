import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import studentInfoStyles from './styles.css?inline'
import StudentInfoPlugin from './StudentInfoPlugin.vue'

export const Profile = StudentInfoPlugin
export const AdminProfiles = StudentInfoPlugin

export const routes = {
  Profile,
  AdminProfiles,
  'yudream-student-info/Home': Profile,
  'yudream-student-info/Profile': Profile,
  'yudream-student-info/AdminProfiles': AdminProfiles,
}

export function install() {
  if (typeof document === 'undefined') {
    return
  }
  const id = 'yudream-plugin-student-info-style'
  let style = document.getElementById(id) as HTMLStyleElement | null
  if (!style) {
    style = document.createElement('style')
    style.id = id
    document.head.appendChild(style)
  }
  style.id = id
  style.textContent = studentInfoStyles
}

export default defineYuDreamPlugin({
  routes,
  default: Profile,
  install,
})

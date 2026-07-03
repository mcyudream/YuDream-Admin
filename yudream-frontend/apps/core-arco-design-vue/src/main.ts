// 加载 iconify 图标
import { downloadAndInstall } from '@/iconify'
import icons from '@/iconify/index.json'
// 自定义指令
import directive from '@/utils/directive'

import App from './App.vue'
import router from './router'
import pinia from './store'
import uiProvider from './ui/provider'
import '@/utils/storage'

// UnoCSS
import 'virtual:uno.css'
// 全局样式
import '@/assets/styles/globals.css'

const app = createApp(App)
app.use(pinia)

// 加载站点设置（站点名称等）
const appSettingsStore = useAppSettingsStore(pinia)
appSettingsStore.loadSiteSettings()
appSettingsStore.loadThemeSettings()

app.use(router)
app.use(uiProvider)
directive(app)
// 加载离线图标集合（无论是否离线，都注册到 @iconify/vue，避免在线模式下图标不显示）
for (const info of icons.collections) {
  downloadAndInstall(info)
}

app.mount('#app')

// 加载 iconify 图标
import { downloadAndInstall } from '@/iconify'
import icons from '@/iconify/index.json'
// 自定义指令
import directive from '@/utils/directive'

import App from './App.vue'
import formCreate from '@form-create/arco-design'
import FcDesigner from 'form-create-designer-arco-design'
import { loadingFadeOut } from 'virtual:app-loading'
import { bootstrapPluginThemes, watchPluginThemeScope } from './plugins/theme-runtime'
import router from './router'
import { applyStartupBranding, initializeStartupBranding } from './startup-branding'
import pinia from './store'
import uiProvider from './ui/provider'
import '@/utils/storage'

// UnoCSS
import 'virtual:uno.css'
// 全局样式
import '@/assets/styles/globals.css'
import 'material-symbols/outlined.css'

const app = createApp(App)
app.use(pinia)

// 加载站点设置（站点名称等）
const appSettingsStore = useAppSettingsStore(pinia)

async function bootstrap() {
  const branding = initializeStartupBranding(
    () => appSettingsStore.loadSiteSettings(),
    () => applyStartupBranding(document, appSettingsStore.siteName),
  )
  appSettingsStore.loadThemeSettings()
  // 与站点名并行拉取插件主题：公开站启动页要等 SITE CSS 注入后再淡出，
  // 否则全局换页动画会一直停在宿主彩虹方块上。
  await Promise.all([branding, bootstrapPluginThemes()])

  app.use(router)
  app.use(uiProvider)
  app.use(formCreate)
  app.use(FcDesigner)
  directive(app)
  // 加载离线图标集合（无论是否离线，都注册到 @iconify/vue，避免在线模式下图标不显示）
  for (const info of icons.collections) {
    downloadAndInstall(info)
  }

  app.mount('#app')
  // 等主题 CSS 注入后再淡出启动页：公开站可被 SITE 主题覆盖，未装主题/后台仍是宿主默认动画。
  loadingFadeOut()
  // 启动页还在时不要按路由关掉 SITE media，否则淡出结尾会闪回宿主彩虹方块。
  watchPluginThemeScope(router)
}

void bootstrap()

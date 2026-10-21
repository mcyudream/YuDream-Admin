<script setup lang="ts">
import GlobalAiAssistant from '@/components/GlobalAiAssistant/index.vue'
import GlobalTour from '@/components/GlobalTour/index.vue'
import dayjs from '@/utils/dayjs'
import { ua } from '@/utils/ua'
import { useStudioPublicShell } from './composables/use-published-studio'
import Provider from './ui/provider/index.vue'
import StudioShadowRegion from './views/site/studio-shadow-region.vue'
import 'dayjs/locale/zh-cn'

const route = useRoute()
const studioShell = useStudioPublicShell()

const appSettingsStore = useAppSettingsStore()

const { auth } = useAppAuth()
const { generateTitle } = useAppMenu()

document.body.setAttribute('data-os', ua.getOS().name || '')

const isAuth = computed(() => {
  return route.matched.every((item) => {
    return auth(item.meta.auth ?? '')
  })
})

// 设置网页 title
watch([
  () => appSettingsStore.settings.app.dynamicTitle,
  () => appSettingsStore.title,
  () => appSettingsStore.siteName,
], () => {
  nextTick(() => {
    if (appSettingsStore.settings.app.dynamicTitle && appSettingsStore.title) {
      document.title = `${generateTitle(appSettingsStore.title)} - ${appSettingsStore.siteName}`
    }
    else {
      document.title = appSettingsStore.siteName
    }
  })
}, {
  immediate: true,
  deep: true,
})

onMounted(() => {
  appSettingsStore.setMode(document.documentElement.clientWidth)
  dayjs.locale('zh-cn')
  window.addEventListener('resize', () => {
    appSettingsStore.setMode(document.documentElement.clientWidth)
  })
})
</script>

<template>
  <Provider>
    <!-- 公开站持久外壳（网站工作台已发布文档开启站点外壳时）：页头/页脚跨路由常驻，页面内容在其间切换 -->
    <StudioShadowRegion v-if="studioShell && studioShell.theme.header.length" :document="studioShell" region="header" />
    <RouterView v-slot="{ Component }">
      <AppNotSupportedMobile v-if="!appSettingsStore.settings.app.mobile && appSettingsStore.mode === 'mobile'" />
      <Component :is="Component" v-else-if="isAuth" />
      <AppNotAllowed v-else />
    </RouterView>
    <StudioShadowRegion
      v-if="studioShell && studioShell.theme.footer.length"
      :document="studioShell"
      region="footer"
      :style="{ paddingBottom: studioShell.features?.publicSkin?.bottomOffset }"
    />
    <AppBackToTop />
    <YdToast :theme="appSettingsStore.currentColorScheme" />
    <AppSystemInfo />
    <GlobalAiAssistant />
    <GlobalTour />
  </Provider>
</template>

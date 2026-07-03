<script setup lang="ts">
import dayjs from '@/utils/dayjs'
import { ua } from '@/utils/ua'
import Provider from './ui/provider/index.vue'
import 'dayjs/locale/zh-cn'

const route = useRoute()

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
    <RouterView v-slot="{ Component }">
      <AppNotSupportedMobile v-if="!appSettingsStore.settings.app.mobile && appSettingsStore.mode === 'mobile'" />
      <Component :is="Component" v-else-if="isAuth" />
      <AppNotAllowed v-else />
    </RouterView>
    <AppBackToTop />
    <FaToast :theme="appSettingsStore.currentColorScheme" />
    <AppSystemInfo />
  </Provider>
</template>

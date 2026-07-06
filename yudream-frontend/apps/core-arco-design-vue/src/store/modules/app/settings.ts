import type { SettingsOptions, ThemeSettings } from '@fantastic-admin/settings'
import type { RouteMeta } from 'vue-router'
import { merge } from '@fantastic-admin/settings'
import { cloneDeep } from 'es-toolkit'
import apiSettings from '@/api/modules/settings'
import settingsDefault from '@/settings'
import { toBackendAssetUrl } from '@/utils/backend-url'

export const useAppSettingsStore = defineStore(
  'appSettings',
  () => {
    const settings = ref(cloneDeep(settingsDefault))

    // 站点名称（从后端 sysSetting 加载）
    const siteName = ref(import.meta.env.VITE_APP_TITLE)
    const logo = ref('')
    const favicon = ref('')
    const loginBanner = ref('')

    function normalizeHexColor(value?: string) {
      if (!value) {
        return ''
      }
      const color = value.trim()
      if (/^#[0-9a-f]{6}$/i.test(color)) {
        return color.toLowerCase()
      }
      if (/^#[0-9a-f]{3}$/i.test(color)) {
        return `#${color[1]}${color[1]}${color[2]}${color[2]}${color[3]}${color[3]}`.toLowerCase()
      }
      return ''
    }

    function hexToOklch(hex?: string) {
      const color = normalizeHexColor(hex)
      if (!color) {
        return null
      }
      const r = Number.parseInt(color.slice(1, 3), 16) / 255
      const g = Number.parseInt(color.slice(3, 5), 16) / 255
      const b = Number.parseInt(color.slice(5, 7), 16) / 255
      const toLinear = (value: number) => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
      const lr = toLinear(r)
      const lg = toLinear(g)
      const lb = toLinear(b)
      const l = 0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb
      const m = 0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb
      const s = 0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb
      const lRoot = Math.cbrt(l)
      const mRoot = Math.cbrt(m)
      const sRoot = Math.cbrt(s)
      const okL = 0.2104542553 * lRoot + 0.793617785 * mRoot - 0.0040720468 * sRoot
      const okA = 1.9779984951 * lRoot - 2.428592205 * mRoot + 0.4505937099 * sRoot
      const okB = 0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.808675766 * sRoot
      const chroma = Math.sqrt(okA ** 2 + okB ** 2)
      const hue = (Math.atan2(okB, okA) * 180 / Math.PI + 360) % 360
      return {
        primary: `${okL.toFixed(3)} ${chroma.toFixed(3)} ${hue.toFixed(3)}`,
        foreground: okL > 0.65 ? '0.145 0 0' : '0.985 0 0',
      }
    }

    function applyPrimaryColor(value?: string) {
      const color = hexToOklch(value || settings.value.theme.primaryColor)
      if (!color) {
        return
      }
      document.documentElement.style.setProperty('--primary', color.primary)
      document.documentElement.style.setProperty('--ring', color.primary)
      document.documentElement.style.setProperty('--primary-foreground', color.foreground)
    }

    // 加载公开站点设置
    async function loadSiteSettings() {
      try {
        const res = await apiSettings.publicSettings()
        const data = res.data
        siteName.value = data.siteName || siteName.value
        logo.value = toBackendAssetUrl(data.logo)
        favicon.value = toBackendAssetUrl(data.favicon)
        loginBanner.value = toBackendAssetUrl(data.loginBanner)
        if (data.copyrightCompany || data.copyrightWebsite || data.copyrightDates) {
          settings.value.app.copyright.company = data.copyrightCompany || settings.value.app.copyright.company
          settings.value.app.copyright.website = data.copyrightWebsite || settings.value.app.copyright.website
          settings.value.app.copyright.dates = data.copyrightDates || settings.value.app.copyright.dates
        }
        if (favicon.value) {
          let icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
          if (!icon) {
            icon = document.createElement('link')
            icon.rel = 'icon'
            document.head.appendChild(icon)
          }
          icon.href = favicon.value
        }
      }
      catch {
        // 加载失败时使用默认标题
      }
    }

    async function loadThemeSettings() {
      try {
        const res = await apiSettings.publicTheme()
        if (res.data.config) {
          updateSettings(res.data.config as SettingsOptions)
        }
      }
      catch {
        // 主题配置加载失败时继续使用本地默认配置
      }
    }

    async function saveThemeSettings() {
      await apiSettings.updateTheme({
        config: cloneDeep(settings.value) as Record<string, any>,
      })
    }

    const prefersColorScheme = window.matchMedia('(prefers-color-scheme: dark)')
    watch(() => settings.value.theme.colorScheme, (val) => {
      document.documentElement.classList.add('disable-color-scheme-transition-duration')
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          document.documentElement.classList.remove('disable-color-scheme-transition-duration')
        })
      })
      if (val === '') {
        prefersColorScheme.addEventListener('change', updateTheme)
      }
      else {
        prefersColorScheme.removeEventListener('change', updateTheme)
      }
    }, {
      immediate: true,
    })

    const currentColorScheme = ref<Exclude<NonNullable<ThemeSettings['colorScheme']>, ''>>()
    watch(() => settings.value.theme.colorScheme, updateTheme, {
      immediate: true,
    })
    function updateTheme() {
      let colorScheme = settings.value.theme.colorScheme
      if (colorScheme === '') {
        colorScheme = prefersColorScheme.matches ? 'dark' : 'light'
      }
      currentColorScheme.value = colorScheme
      switch (colorScheme) {
        case 'light':
          document.documentElement.classList.remove('dark')
          document.documentElement.setAttribute('color-scheme', '')
          break
        case 'dark':
          document.documentElement.classList.add('dark')
          document.documentElement.setAttribute('color-scheme', 'dark')
          break
      }
    }

    watch(() => settings.value.theme.radius, (val) => {
      document.documentElement.style.removeProperty('--radius')
      document.documentElement.style.setProperty('--radius', `${val}rem`)
    }, {
      immediate: true,
    })
    watch(() => settings.value.theme.primaryColor, (val) => {
      applyPrimaryColor(val)
    }, {
      immediate: true,
    })
    watch([
      () => settings.value.app.rip,
      () => settings.value.theme.colorAmblyopia,
    ], (val) => {
      document.documentElement.style.removeProperty('filter')
      if (val[0] && val[1]) {
        document.documentElement.style.setProperty('filter', 'grayscale(100%) invert(80%)')
      }
      else if (val[0]) {
        document.documentElement.style.setProperty('filter', 'grayscale(100%)')
      }
      else if (val[1]) {
        document.documentElement.style.setProperty('filter', 'invert(80%)')
      }
    }, {
      immediate: true,
    })

    watch(() => settings.value.menu.mode, (val) => {
      document.body.setAttribute('data-menu-mode', val)
    }, {
      immediate: true,
    })

    // 操作系统
    const os = ref<'mac' | 'windows' | 'linux' | 'other'>('other')
    const agent = navigator.userAgent.toLowerCase()
    switch (true) {
      case agent.includes('mac os'):
        os.value = 'mac'
        break
      case agent.includes('windows'):
        os.value = 'windows'
        break
      case agent.includes('linux'):
        os.value = 'linux'
        break
    }

    // 页面是否刷新
    const isReloading = ref(false)
    // 切换当前页面是否刷新
    function setIsReloading(value?: boolean) {
      isReloading.value = value ?? !isReloading.value
    }

    // 页面标题
    const title = ref<RouteMeta['title']>()
    // 记录页面标题
    function setTitle(_title: RouteMeta['title']) {
      title.value = _title
    }
    // 显示模式
    const mode = ref<'pc' | 'mobile'>('pc')
    // 设置显示模式
    function setMode(width: number) {
      // 先判断 UA 是否为移动端设备（手机&平板）
      if (/Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)) {
        mode.value = 'mobile'
      }
      else {
        // 如果是桌面设备，则根据页面宽度判断是否需要切换为移动端展示
        mode.value = width < 1024 ? 'mobile' : 'pc'
      }
    }

    // 切换侧边栏导航展开/收起
    function toggleSidebarCollapse() {
      settings.value.menu.subMenuCollapse = !settings.value.menu.subMenuCollapse
    }
    // 次导航是否收起（用于记录 pc 模式下最后的状态）
    const subMenuCollapseLastStatus = ref(settingsDefault.menu.subMenuCollapse)
    watch(() => settings.value.menu.subMenuCollapse, (val) => {
      if (mode.value === 'pc') {
        subMenuCollapseLastStatus.value = val
      }
    })
    watch(mode, (val) => {
      switch (val) {
        case 'pc':
          settings.value.menu.subMenuCollapse = subMenuCollapseLastStatus.value
          break
        case 'mobile':
          settings.value.menu.subMenuCollapse = true
          break
      }
      document.body.setAttribute('data-mode', val)
    }, {
      immediate: true,
    })

    // 设置主题颜色模式
    function setColorScheme(color: NonNullable<ThemeSettings['colorScheme']>) {
      settings.value.theme.colorScheme = color
    }

    // 更新应用配置
    function updateSettings(data: SettingsOptions, fromBase = false) {
      const mergedSettings = merge(data, fromBase ? cloneDeep(settingsDefault) : settings.value) as typeof settings.value
      settings.value = mergedSettings
    }

    return {
      settings,
      siteName,
      logo,
      favicon,
      loginBanner,
      loadSiteSettings,
      loadThemeSettings,
      saveThemeSettings,
      currentColorScheme,
      os,
      isReloading,
      setIsReloading,
      title,
      setTitle,
      mode,
      setMode,
      subMenuCollapseLastStatus,
      toggleSidebarCollapse,
      setColorScheme,
      updateSettings,
    }
  },
)

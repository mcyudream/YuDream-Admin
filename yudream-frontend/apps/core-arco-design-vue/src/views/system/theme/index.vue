<script setup lang="ts">
import type { PluginThemeOverview, PluginThemeScope } from '@/api/modules/platform-plugin'
import apiPlugin from '@/api/modules/platform-plugin'
import AppearancePresets from './AppearancePresets.vue'

const route = useRoute()
const router = useRouter()
const toast = useYdToast()
const appSettingsStore = useAppSettingsStore()
const appMenuStore = useAppMenuStore()

const saving = ref(false)
const activeTab = ref('appearance')
const settingTabs = [
  { label: '外观', value: 'appearance', icon: 'morph:palette' },
  { label: '导航与布局', value: 'navigation', icon: 'morph:layout-dashboard' },
  { label: '页面与应用', value: 'page', icon: 'morph:files' },
  { label: '插件主题', value: 'plugin', icon: 'morph:puzzle' },
]
const colorPresets = ['#18181b', '#2563eb', '#16a34a', '#dc2626', '#9333ea', '#ea580c']

const pluginThemeOverview = ref<PluginThemeOverview>({ themes: [], active: {} })

const scopeLabels: Record<PluginThemeScope, string> = {
  SITE: '公开站',
  ADMIN: '管理后台',
}

const siteThemeName = computed(() => {
  const code = pluginThemeOverview.value.active.SITE
  return pluginThemeOverview.value.themes.find(theme => theme.pluginCode === code)?.name || ''
})

function isThemeActive(pluginCode: string, scope: PluginThemeScope) {
  return pluginThemeOverview.value.active[scope] === pluginCode
}

async function loadPluginThemes() {
  try {
    const res = await apiPlugin.themes()
    pluginThemeOverview.value = res.data
  }
  catch {
    pluginThemeOverview.value = { themes: [], active: {} }
  }
}

function goPluginManage() {
  router.push('/platform/plugin')
}

onMounted(loadPluginThemes)

const themeRadius = computed<number[]>({
  get: () => [appSettingsStore.settings.theme.radius],
  set: value => (appSettingsStore.settings.theme.radius = value[0]),
})

watch(() => appSettingsStore.settings.menu.mode, (value) => {
  appMenuStore.setActived(value === 'single' ? 0 : route.fullPath)
})

async function saveTheme() {
  saving.value = true
  try {
    await appSettingsStore.saveThemeSettings()
    toast.success('主题配置已保存')
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <YdPageHeader title="主题配置" description="让工作空间拥有适合你的风格与节奏" class="mb-0">
      <YdButton v-auth="'system:setting:theme:edit'" :loading="saving" @click="saveTheme">
        <YdIcon name="morph:save" />
        保存设置
      </YdButton>
    </YdPageHeader>

    <YdPageMain>
      <YdSettingsLayout v-model="activeTab" :list="settingTabs" width-class="max-w-4xl">
        <template #appearance>
          <YdSettingsSection
            title="外观预设"
            description="设置即时预览，保存后刷新或重新登录仍会生效。"
          >
            <AppearancePresets v-model="appSettingsStore.settings.appearance" />
          </YdSettingsSection>

          <YdSettingsSection
            title="主题"
            description="深浅模式、全局主色与圆角由主题引擎统一转换到各组件，业务不单独定义品牌色。"
          >
            <div class="setting-item">
              <div class="label">
                颜色方案
              </div>
              <YdButtonGroup>
                <YdButton
                  v-for="item in [
                    { icon: 'morph:sun', value: 'light' },
                    { icon: 'morph:moon', value: 'dark' },
                    { icon: 'morph:sun-moon', value: '' },
                  ]" :key="item.icon" :variant="appSettingsStore.settings.theme.colorScheme === item.value ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.theme.colorScheme = (item.value as any)"
                >
                  <YdIcon :name="item.icon" />
                </YdButton>
              </YdButtonGroup>
            </div>
            <div class="setting-item items-start">
              <div class="label pt-2">
                全局主色
              </div>
              <div class="color-control">
                <div class="flex gap-2 items-center">
                  <YdInput v-model="appSettingsStore.settings.theme.primaryColor" type="color" class="color-input border-0 rounded-none bg-transparent shadow-none" input-class="color-input__control" aria-label="选择全局主色" />
                  <YdInput v-model="appSettingsStore.settings.theme.primaryColor" class="font-mono w-32" />
                </div>
                <div class="flex flex-wrap gap-2">
                  <YdButton
                    v-for="color in colorPresets"
                    :key="color"
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    class="color-swatch"
                    :class="{ active: appSettingsStore.settings.theme.primaryColor?.toLowerCase() === color }"
                    :style="{ backgroundColor: color }"
                    :aria-label="color"
                    :aria-pressed="appSettingsStore.settings.theme.primaryColor?.toLowerCase() === color"
                    @click="appSettingsStore.settings.theme.primaryColor = color"
                  />
                </div>
              </div>
            </div>
            <div class="setting-item">
              <div class="label">
                圆角
              </div>
              <YdSlider v-model="themeRadius" :min="0" :max="1" :step="0.25" class="w-1/2" />
            </div>
            <div class="setting-item">
              <div class="label">
                色弱模式
              </div>
              <YdSwitch v-model="appSettingsStore.settings.theme.colorAmblyopia" />
            </div>
          </YdSettingsSection>
        </template>

        <template #navigation>
          <YdSettingsSection
            title="导航菜单"
            description="主导航模式、点击行为与次导航展开策略。"
          >
            <div class="setting-item">
              <div class="label">
                菜单模式
              </div>
              <YdButtonGroup>
                <YdButton :variant="appSettingsStore.settings.menu.mode === 'side' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mode = 'side'">
                  侧边
                </YdButton>
                <YdButton :variant="appSettingsStore.settings.menu.mode === 'head' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mode = 'head'">
                  顶部
                </YdButton>
                <YdButton :variant="appSettingsStore.settings.menu.mode === 'single' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mode = 'single'">
                  单栏
                </YdButton>
              </YdButtonGroup>
            </div>
            <div class="setting-item">
              <div class="label">
                点击主导航菜单
              </div>
              <YdButtonGroup>
                <YdButton :variant="appSettingsStore.settings.menu.mainMenuClickMode === 'switch' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mainMenuClickMode = 'switch'">
                  切换
                </YdButton>
                <YdButton :variant="appSettingsStore.settings.menu.mainMenuClickMode === 'jump' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mainMenuClickMode = 'jump'">
                  跳转
                </YdButton>
                <YdButton :variant="appSettingsStore.settings.menu.mainMenuClickMode === 'smart' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mainMenuClickMode = 'smart'">
                  智能
                </YdButton>
              </YdButtonGroup>
            </div>
            <div class="setting-item">
              <div class="label">
                次导航菜单唯一展开
              </div>
              <YdSwitch v-model="appSettingsStore.settings.menu.subMenuUniqueExpand" />
            </div>
            <div class="setting-item">
              <div class="label">
                次导航菜单收起
              </div>
              <YdSwitch v-model="appSettingsStore.settings.menu.subMenuCollapse" />
            </div>
            <div class="setting-item">
              <div class="label">
                次导航展开收起按钮
              </div>
              <YdSwitch v-model="appSettingsStore.settings.menu.subMenuCollapseButton" />
            </div>
            <div class="setting-item">
              <div class="label">
                快捷键
              </div>
              <YdSwitch v-model="appSettingsStore.settings.menu.hotkeys" :disabled="appSettingsStore.settings.menu.mode === 'single'" />
            </div>
          </YdSettingsSection>

          <YdSettingsSection
            title="顶栏"
            description="顶栏整体与标签栏、工具栏的显隐。"
          >
            <div class="setting-item">
              <div class="label">
                标签栏
              </div>
              <YdSwitch v-model="appSettingsStore.settings.topbar.tabbar" />
            </div>
            <div class="setting-item">
              <div class="label">
                工具栏
              </div>
              <YdSwitch v-model="appSettingsStore.settings.topbar.toolbar" />
            </div>
            <div class="setting-item">
              <div class="label">
                模式
              </div>
              <YdButtonGroup>
                <YdButton :variant="appSettingsStore.settings.topbar.mode === 'static' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.topbar.mode = 'static'">
                  静态
                </YdButton>
                <YdButton :variant="appSettingsStore.settings.topbar.mode === 'fixed' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.topbar.mode = 'fixed'">
                  固定
                </YdButton>
                <YdButton :variant="appSettingsStore.settings.topbar.mode === 'sticky' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.topbar.mode = 'sticky'">
                  粘性
                </YdButton>
              </YdButtonGroup>
            </div>
          </YdSettingsSection>

          <YdSettingsSection
            title="标签栏"
            description="标签栏上的图标与快捷键提示。"
          >
            <div class="setting-item">
              <div class="label">
                显示图标
              </div>
              <YdSwitch v-model="appSettingsStore.settings.tabbar.icon" />
            </div>
            <div class="setting-item">
              <div class="label">
                快捷键
              </div>
              <YdSwitch v-model="appSettingsStore.settings.tabbar.hotkeys" />
            </div>
          </YdSettingsSection>

          <YdSettingsSection
            title="工具栏"
            description="工具栏内各项入口的显隐。"
          >
            <div class="setting-item">
              <div class="label">
                <YdIcon name="morph:chevrons-right" />
                面包屑导航
              </div>
              <YdSwitch v-model="appSettingsStore.settings.toolbar.breadcrumb" />
            </div>
            <div class="setting-item">
              <div class="label">
                <YdIcon name="morph:search" />
                导航搜索
              </div>
              <YdSwitch v-model="appSettingsStore.settings.toolbar.menuSearch.enable" />
            </div>
            <div class="setting-item">
              <div class="label">
                <YdIcon name="morph:maximize" />
                全屏
              </div>
              <YdSwitch v-model="appSettingsStore.settings.toolbar.fullscreen" />
            </div>
            <div class="setting-item">
              <div class="label">
                <YdIcon name="morph:sun" />
                颜色主题
              </div>
              <YdSwitch v-model="appSettingsStore.settings.toolbar.colorScheme" />
            </div>
          </YdSettingsSection>
        </template>

        <template #page>
          <YdSettingsSection
            title="页面与应用"
            description="载入反馈、权限验证与首页承载。"
          >
            <div class="setting-item">
              <div class="label">
                载入进度条
              </div>
              <YdSwitch v-model="appSettingsStore.settings.page.progress" />
            </div>
            <div class="setting-item">
              <div class="label">
                权限验证
              </div>
              <YdSwitch v-model="appSettingsStore.settings.app.account.auth" />
            </div>
            <div class="setting-item">
              <div class="label">
                动态标题
              </div>
              <YdSwitch v-model="appSettingsStore.settings.app.dynamicTitle" />
            </div>
            <div class="setting-item">
              <div class="label">
                移动端访问
              </div>
              <YdSwitch v-model="appSettingsStore.settings.app.mobile" />
            </div>
            <div class="setting-item">
              <div class="label">
                首页启用
              </div>
              <YdSwitch v-model="appSettingsStore.settings.app.home.enable" />
            </div>
            <div class="setting-item items-start">
              <div class="label pt-2">
                首页标题
              </div>
              <YdInput v-model="appSettingsStore.settings.app.home.title" class="max-w-80" />
            </div>
          </YdSettingsSection>
        </template>

        <template #plugin>
          <YdSettingsSection
            title="插件主题"
            description="公开站主题各自保存独立的布局与页面，切换后内容仍会保留，请前往主题中心管理。管理后台主题在插件管理中启用或停用，同一范围同时激活一个主题。"
          >
            <div v-if="siteThemeName" class="text-sm text-muted-foreground">
              公开站主题正由插件「{{ siteThemeName }}」接管，上方的主题配置仅作用于管理后台。
            </div>
            <div v-if="!pluginThemeOverview.themes.length" class="text-sm text-muted-foreground">
              当前没有已启用插件注册的主题。
            </div>
            <div v-else class="gap-3 grid grid-cols-1 md:grid-cols-2">
              <div v-for="theme in pluginThemeOverview.themes" :key="theme.pluginCode" class="plugin-theme-card">
                <div class="flex gap-2 items-center justify-between">
                  <div class="font-medium flex gap-2 items-center">
                    {{ theme.name }}
                    <YdTag v-for="scope in theme.scopes" :key="scope" variant="secondary">
                      {{ scopeLabels[scope] }}
                    </YdTag>
                    <YdTag v-if="theme.homePreset" variant="outline">
                      含首页方案
                    </YdTag>
                  </div>
                  <YdTag v-if="theme.scopes?.some(scope => isThemeActive(theme.pluginCode, scope))" variant="default">
                    当前
                  </YdTag>
                </div>
                <div v-if="theme.description" class="text-sm text-muted-foreground mt-1">
                  {{ theme.description }}
                </div>
                <div class="text-xs text-muted-foreground font-mono mt-1">
                  {{ theme.pluginCode }}
                </div>
              </div>
            </div>
            <template #footer>
              <YdButton variant="outline" size="sm" @click="router.push('/platform/theme-center')">
                <YdIcon name="morph:panels-top-left" />
                公开站主题中心
              </YdButton>
              <YdButton variant="outline" size="sm" @click="goPluginManage">
                <YdIcon name="morph:puzzle" />
                前往插件管理
              </YdButton>
            </template>
          </YdSettingsSection>
        </template>
      </YdSettingsLayout>
    </YdPageMain>
  </div>
</template>

<style scoped>
.setting-item {
  --uno: flex flex-wrap items-center justify-between gap-4;
}

.setting-item .label {
  --uno: flex items-center flex-shrink-0 gap-2 text-sm;
}

.color-control {
  display: grid;
  gap: 8px;
  justify-items: end;
}

.color-input {
  width: 36px;
  height: 36px;
}

.color-input__control {
  width: 36px;
  height: 36px;
  padding: 0;
  background: transparent;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.color-swatch {
  width: 24px;
  height: 24px;
  padding: 0;
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 999px;
}

.color-swatch.active {
  border-color: oklch(var(--foreground));
  box-shadow: 0 0 0 2px oklch(var(--background)), 0 0 0 4px oklch(var(--primary));
}

.plugin-theme-card {
  padding: 12px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}
</style>

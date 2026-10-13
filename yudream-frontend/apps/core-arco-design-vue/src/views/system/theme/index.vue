<script setup lang="ts">
import type { PluginThemeOverview, PluginThemeScope } from '@/api/modules/platform-plugin'
import apiPlugin from '@/api/modules/platform-plugin'
import AppearancePresets from './AppearancePresets.vue'

const route = useRoute()
const router = useRouter()
const toast = useFaToast()
const appSettingsStore = useAppSettingsStore()
const appMenuStore = useAppMenuStore()

const saving = ref(false)
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
    <FaPageHeader title="主题配置" description="让工作空间拥有适合你的风格与节奏" class="mb-0" />

    <FaPageMain>
      <div class="mb-4 flex flex-wrap gap-3 items-center justify-between">
        <div class="text-sm text-muted-foreground">
          设置即时预览，保存后刷新或重新登录仍会生效。
        </div>
        <div class="flex gap-2 items-center">
          <FaButton v-auth="'system:setting:theme:edit'" :loading="saving" @click="saveTheme">
            <FaIcon name="morph:save" />
            保存设置
          </FaButton>
        </div>
      </div>

      <AppearancePresets v-model="appSettingsStore.settings.appearance" />

      <div class="gap-4 grid grid-cols-1 xl:grid-cols-2">
        <section class="theme-section">
          <div class="theme-section__title">
            &#20027;&#39064;
          </div>
          <div class="setting-item">
            <div class="label">
              &#39068;&#33394;&#26041;&#26696;
            </div>
            <FaButtonGroup>
              <FaButton
                v-for="item in [
                  { icon: 'morph:sun', value: 'light' },
                  { icon: 'morph:moon', value: 'dark' },
                  { icon: 'morph:sun-moon', value: '' },
                ]" :key="item.icon" :variant="appSettingsStore.settings.theme.colorScheme === item.value ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.theme.colorScheme = (item.value as any)"
              >
                <FaIcon :name="item.icon" />
              </FaButton>
            </FaButtonGroup>
          </div>
          <div class="setting-item items-start">
            <div class="label pt-2">
              &#20840;&#23616;&#20027;&#33394;
            </div>
            <div class="color-control">
              <div class="flex gap-2 items-center">
                <input v-model="appSettingsStore.settings.theme.primaryColor" type="color" class="color-input">
                <FaInput v-model="appSettingsStore.settings.theme.primaryColor" class="font-mono w-32" />
              </div>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="color in colorPresets"
                  :key="color"
                  type="button"
                  class="color-swatch"
                  :class="{ active: appSettingsStore.settings.theme.primaryColor?.toLowerCase() === color }"
                  :style="{ backgroundColor: color }"
                  :aria-label="color"
                  @click="appSettingsStore.settings.theme.primaryColor = color"
                />
              </div>
            </div>
          </div>
          <div class="setting-item">
            <div class="label">
              &#22278;&#35282;
            </div>
            <FaSlider v-model="themeRadius" :min="0" :max="1" :step="0.25" class="w-1/2" />
          </div>
          <div class="setting-item">
            <div class="label">
              &#33394;&#24369;&#27169;&#24335;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.theme.colorAmblyopia" />
          </div>
        </section>

        <section class="theme-section order-last xl:col-span-2">
          <div class="theme-section__title">
            插件主题
          </div>
          <div class="text-sm text-muted-foreground">
            公开站主题各自保存独立的布局与页面，切换后内容仍会保留，请前往主题中心管理。管理后台主题在插件管理中启用或停用，同一范围同时激活一个主题。
          </div>
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
                  <FaTag v-for="scope in theme.scopes" :key="scope" variant="secondary">
                    {{ scopeLabels[scope] }}
                  </FaTag>
                  <FaTag v-if="theme.homePreset" variant="outline">
                    含首页方案
                  </FaTag>
                </div>
                <FaTag v-if="theme.scopes?.some(scope => isThemeActive(theme.pluginCode, scope))" variant="default">
                  当前
                </FaTag>
              </div>
              <div v-if="theme.description" class="text-sm text-muted-foreground mt-1">
                {{ theme.description }}
              </div>
              <div class="text-xs text-muted-foreground font-mono mt-1">
                {{ theme.pluginCode }}
              </div>
            </div>
          </div>
          <div class="flex flex-wrap gap-2 justify-end">
            <FaButton variant="outline" size="sm" @click="router.push('/platform/theme-center')">
              <FaIcon name="morph:panels-top-left" />
              公开站主题中心
            </FaButton>
            <FaButton variant="outline" size="sm" @click="goPluginManage">
              <FaIcon name="morph:puzzle" />
              前往插件管理
            </FaButton>
          </div>
        </section>

        <section class="theme-section">
          <div class="theme-section__title">
            &#23548;&#33322;&#33756;&#21333;
          </div>
          <div class="setting-item">
            <div class="label">
              &#33756;&#21333;&#27169;&#24335;
            </div>
            <FaButtonGroup>
              <FaButton :variant="appSettingsStore.settings.menu.mode === 'side' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mode = 'side'">
                &#20391;&#36793;
              </FaButton>
              <FaButton :variant="appSettingsStore.settings.menu.mode === 'head' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mode = 'head'">
                &#39030;&#37096;
              </FaButton>
              <FaButton :variant="appSettingsStore.settings.menu.mode === 'single' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mode = 'single'">
                &#21333;&#26639;
              </FaButton>
            </FaButtonGroup>
          </div>
          <div class="setting-item">
            <div class="label">
              &#28857;&#20987;&#20027;&#23548;&#33322;&#33756;&#21333;
            </div>
            <FaButtonGroup>
              <FaButton :variant="appSettingsStore.settings.menu.mainMenuClickMode === 'switch' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mainMenuClickMode = 'switch'">
                &#20999;&#25442;
              </FaButton>
              <FaButton :variant="appSettingsStore.settings.menu.mainMenuClickMode === 'jump' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mainMenuClickMode = 'jump'">
                &#36339;&#36716;
              </FaButton>
              <FaButton :variant="appSettingsStore.settings.menu.mainMenuClickMode === 'smart' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.menu.mainMenuClickMode = 'smart'">
                &#26234;&#33021;
              </FaButton>
            </FaButtonGroup>
          </div>
          <div class="setting-item">
            <div class="label">
              &#27425;&#23548;&#33322;&#33756;&#21333;&#21807;&#19968;&#23637;&#24320;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.menu.subMenuUniqueExpand" />
          </div>
          <div class="setting-item">
            <div class="label">
              &#27425;&#23548;&#33322;&#33756;&#21333;&#25910;&#36215;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.menu.subMenuCollapse" />
          </div>
          <div class="setting-item">
            <div class="label">
              &#27425;&#23548;&#33322;&#23637;&#24320;&#25910;&#36215;&#25353;&#38062;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.menu.subMenuCollapseButton" />
          </div>
          <div class="setting-item">
            <div class="label">
              &#24555;&#25463;&#38190;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.menu.hotkeys" :disabled="appSettingsStore.settings.menu.mode === 'single'" />
          </div>
        </section>

        <section class="theme-section">
          <div class="theme-section__title">
            &#39030;&#26639;
          </div>
          <div class="setting-item">
            <div class="label">
              &#26631;&#31614;&#26639;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.topbar.tabbar" />
          </div>
          <div class="setting-item">
            <div class="label">
              &#24037;&#20855;&#26639;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.topbar.toolbar" />
          </div>
          <div class="setting-item">
            <div class="label">
              &#27169;&#24335;
            </div>
            <FaButtonGroup>
              <FaButton :variant="appSettingsStore.settings.topbar.mode === 'static' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.topbar.mode = 'static'">
                &#38745;&#24577;
              </FaButton>
              <FaButton :variant="appSettingsStore.settings.topbar.mode === 'fixed' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.topbar.mode = 'fixed'">
                &#22266;&#23450;
              </FaButton>
              <FaButton :variant="appSettingsStore.settings.topbar.mode === 'sticky' ? 'default' : 'outline'" size="sm" @click="appSettingsStore.settings.topbar.mode = 'sticky'">
                &#31896;&#24615;
              </FaButton>
            </FaButtonGroup>
          </div>
        </section>

        <section class="theme-section">
          <div class="theme-section__title">
            &#26631;&#31614;&#26639;
          </div>
          <div class="setting-item">
            <div class="label">
              &#26174;&#31034;&#22270;&#26631;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.tabbar.icon" />
          </div>
          <div class="setting-item">
            <div class="label">
              &#24555;&#25463;&#38190;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.tabbar.hotkeys" />
          </div>
        </section>

        <section class="theme-section">
          <div class="theme-section__title">
            &#24037;&#20855;&#26639;
          </div>
          <div class="setting-item">
            <div class="label">
              <FaIcon name="morph:chevrons-right" />
              &#38754;&#21253;&#23633;&#23548;&#33322;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.toolbar.breadcrumb" />
          </div>
          <div class="setting-item">
            <div class="label">
              <FaIcon name="morph:search" />
              &#23548;&#33322;&#25628;&#32034;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.toolbar.menuSearch.enable" />
          </div>
          <div class="setting-item">
            <div class="label">
              <FaIcon name="morph:maximize" />
              &#20840;&#23631;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.toolbar.fullscreen" />
          </div>
          <div class="setting-item">
            <div class="label">
              <FaIcon name="morph:sun" />
              &#39068;&#33394;&#20027;&#39064;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.toolbar.colorScheme" />
          </div>
        </section>

        <section class="theme-section">
          <div class="theme-section__title">
            &#39029;&#38754;&#19982;&#24212;&#29992;
          </div>
          <div class="setting-item">
            <div class="label">
              &#36733;&#20837;&#36827;&#24230;&#26465;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.page.progress" />
          </div>
          <div class="setting-item">
            <div class="label">
              &#26435;&#38480;&#39564;&#35777;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.app.account.auth" />
          </div>
          <div class="setting-item">
            <div class="label">
              &#21160;&#24577;&#26631;&#39064;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.app.dynamicTitle" />
          </div>
          <div class="setting-item">
            <div class="label">
              &#31227;&#21160;&#31471;&#35775;&#38382;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.app.mobile" />
          </div>
          <div class="setting-item">
            <div class="label">
              &#39318;&#39029;&#21551;&#29992;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.app.home.enable" />
          </div>
          <div class="setting-item items-start">
            <div class="label pt-2">
              &#39318;&#39029;&#26631;&#39064;
            </div>
            <FaInput v-model="appSettingsStore.settings.app.home.title" class="max-w-80" />
          </div>
        </section>
      </div>
    </FaPageMain>
  </div>
</template>

<style scoped>
.theme-section {
  display: grid;
  gap: 16px;
  align-content: start;
  padding: 20px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius, 12px);
  box-shadow: var(--yd-surface-shadow, none);
}

.theme-section__title {
  padding-bottom: 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--color-border-1);
}

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
  padding: 0;
  background: transparent;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.color-swatch {
  width: 24px;
  height: 24px;
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

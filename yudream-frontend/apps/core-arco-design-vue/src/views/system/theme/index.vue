<script setup lang="ts">
const route = useRoute()
const toast = useFaToast()
const appSettingsStore = useAppSettingsStore()
const appMenuStore = useAppMenuStore()

const saving = ref(false)
const loading = ref(false)
const colorPresets = ['#18181b', '#2563eb', '#16a34a', '#dc2626', '#9333ea', '#ea580c']

const themeRadius = computed<number[]>({
  get: () => [appSettingsStore.settings.theme.radius],
  set: value => (appSettingsStore.settings.theme.radius = value[0]),
})

watch(() => appSettingsStore.settings.menu.mode, (value) => {
  appMenuStore.setActived(value === 'single' ? 0 : route.fullPath)
})

async function refreshTheme() {
  loading.value = true
  try {
    await appSettingsStore.loadThemeSettings()
    toast.success('\u4e3b\u9898\u914d\u7f6e\u5df2\u5237\u65b0')
  }
  finally {
    loading.value = false
  }
}

async function saveTheme() {
  saving.value = true
  try {
    await appSettingsStore.saveThemeSettings()
    toast.success('\u4e3b\u9898\u914d\u7f6e\u5df2\u4fdd\u5b58')
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <FaPageHeader title="&#20027;&#39064;&#37197;&#32622;" class="mb-0" />

    <FaPageMain>
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="text-sm text-muted-foreground">
          &#20445;&#23384;&#21518;&#20889;&#20837;&#21518;&#31471;&#20027;&#39064;&#37197;&#32622;&#65292;&#21047;&#26032;&#25110;&#37325;&#26032;&#30331;&#24405;&#21518;&#20173;&#20250;&#29983;&#25928;&#12290;
        </div>
        <div class="flex items-center gap-2">
          <FaButton variant="outline" :loading="loading" @click="refreshTheme">
            <FaIcon name="i-ri:refresh-line" />
            &#21047;&#26032;
          </FaButton>
          <FaButton :loading="saving" @click="saveTheme">
            <FaIcon name="i-ri:save-3-line" />
            &#20445;&#23384;&#35774;&#32622;
          </FaButton>
        </div>
      </div>

      <div v-loading="loading" class="grid grid-cols-1 gap-4 xl:grid-cols-2">
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
                  { icon: 'i-ri:sun-line', value: 'light' },
                  { icon: 'i-ri:moon-line', value: 'dark' },
                  { icon: 'i-codicon:color-mode', value: '' },
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
              <div class="flex items-center gap-2">
                <input v-model="appSettingsStore.settings.theme.primaryColor" type="color" class="color-input">
                <FaInput v-model="appSettingsStore.settings.theme.primaryColor" class="w-32 font-mono" />
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
              <FaIcon name="i-ic:twotone-double-arrow" />
              &#38754;&#21253;&#23633;&#23548;&#33322;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.toolbar.breadcrumb" />
          </div>
          <div class="setting-item">
            <div class="label">
              <FaIcon name="i-ri:search-line" />
              &#23548;&#33322;&#25628;&#32034;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.toolbar.menuSearch.enable" />
          </div>
          <div class="setting-item">
            <div class="label">
              <FaIcon name="i-ri:fullscreen-line" />
              &#20840;&#23631;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.toolbar.fullscreen" />
          </div>
          <div class="setting-item">
            <div class="label">
              <FaIcon name="i-iconoir:refresh-double" />
              &#39029;&#38754;&#21047;&#26032;
            </div>
            <FaSwitch v-model="appSettingsStore.settings.toolbar.pageReload" />
          </div>
          <div class="setting-item">
            <div class="label">
              <FaIcon name="i-ri:sun-line" />
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
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.theme-section__title {
  font-weight: 600;
}

.setting-item {
  --uno: flex items-center justify-between gap-4;
}

.setting-item .label {
  --uno: flex items-center flex-shrink-0 gap-2 text-sm;
}

.color-control {
  display: grid;
  justify-items: end;
  gap: 8px;
}

.color-input {
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: transparent;
}

.color-swatch {
  width: 24px;
  height: 24px;
  border: 2px solid transparent;
  border-radius: 999px;
  cursor: pointer;
}

.color-swatch.active {
  border-color: oklch(var(--foreground));
  box-shadow: 0 0 0 2px oklch(var(--background)), 0 0 0 4px oklch(var(--primary));
}
</style>

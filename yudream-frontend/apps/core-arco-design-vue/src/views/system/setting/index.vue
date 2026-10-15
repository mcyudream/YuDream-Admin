<script setup lang="ts">
import type { SiteSetting } from '@/api/modules/settings'
import apiSettings from '@/api/modules/settings'
import apiSecurity from '@/api/modules/system-security'
import { useAppFeatureStore } from '@/store/modules/app/features'
import { toBackendAssetUrl } from '@/utils/backend-url'
import IntegrationSettings from './IntegrationSettings.vue'

const toast = useYdToast()
const appSettingsStore = useAppSettingsStore()
const appFeatureStore = useAppFeatureStore()

const loading = ref(false)
const saving = ref(false)
const activeSettingTab = ref('basic')
const settingTabs = [
  { icon: 'morph:settings', label: '基础设置', value: 'basic' },
  { icon: 'morph:image', label: '品牌素材', value: 'brand' },
  { icon: 'morph:log-in', label: '登录方式', value: 'login' },
  { icon: 'morph:plug', label: '集成配置', value: 'integration' },
]
const integrationRef = ref<InstanceType<typeof IntegrationSettings>>()
const integrationSaving = ref(false)
const logoInput = ref<InstanceType<typeof YdFilePicker>>()
const faviconInput = ref<InstanceType<typeof YdFilePicker>>()
const loginBannerInput = ref<InstanceType<typeof YdFilePicker>>()
/** 可作为默认登录方式的插件登录入口（只列已启用且以登录 Tab 呈现的） */
const externalLoginOptions = ref<{ label: string, value: string }[]>([])
/** 默认登录方式下拉项：内置两种 + 可用的插件登录入口 */
const loginMethodOptions = computed(() => [
  { label: '账号密码登录', value: 'password' },
  ...(appFeatureStore.passkeyEnabled ? [{ label: 'Passkey 登录', value: 'passkey' }] : []),
  ...externalLoginOptions.value,
])

const form = reactive<SiteSetting>({
  siteName: '',
  siteDescription: '',
  logo: '',
  favicon: '',
  loginBanner: '',
  copyrightCompany: '',
  copyrightWebsite: '',
  copyrightDates: '',
  loginDefaultMethod: 'password',
})

onMounted(() => {
  loadSettings()
  appFeatureStore.load()
  loadExternalLoginOptions()
})

async function loadExternalLoginOptions() {
  try {
    const providers = (await apiSecurity.externalLoginProviders()).data
    externalLoginOptions.value = providers
      .filter(provider => provider.enabled && provider.presentation === 'TAB')
      .flatMap(provider => (provider.supportedTypes || '')
        .split(',')
        .map(item => item.trim().toLowerCase())
        .filter(Boolean)
        .map(type => ({ label: `${provider.name || provider.code}（${type}）`, value: `external:${provider.code}:${type}` })))
  }
  catch {
    externalLoginOptions.value = []
  }
}

async function loadSettings() {
  loading.value = true
  try {
    const res = await apiSettings.site()
    assignForm(res.data)
  }
  finally {
    loading.value = false
  }
}

async function saveSettings() {
  saving.value = true
  try {
    const res = await apiSettings.updateSite(form)
    assignForm(res.data)
    await appSettingsStore.loadSiteSettings()
    toast.success('系统设置已保存')
  }
  finally {
    saving.value = false
  }
}

/** 集成配置的保存按钮统一放在页头，由这里转发到子组件 */
async function saveIntegration() {
  integrationSaving.value = true
  try {
    await integrationRef.value?.save()
  }
  finally {
    integrationSaving.value = false
  }
}

function assignForm(data: SiteSetting) {
  Object.assign(form, {
    siteName: data.siteName || '',
    siteDescription: data.siteDescription || '',
    logo: toBackendAssetUrl(data.logo),
    favicon: toBackendAssetUrl(data.favicon),
    loginBanner: toBackendAssetUrl(data.loginBanner),
    copyrightCompany: data.copyrightCompany || '',
    copyrightWebsite: data.copyrightWebsite || '',
    copyrightDates: data.copyrightDates || '',
    loginDefaultMethod: data.loginDefaultMethod || 'password',
  })
}

async function uploadAsset(files: File[], type: 'logo' | 'favicon' | 'loginBanner') {
  const file = files[0]
  const picker = type === 'logo' ? logoInput.value : type === 'favicon' ? faviconInput.value : loginBannerInput.value
  picker?.reset()
  if (!file) {
    return
  }
  const data = new FormData()
  data.append('file', file)
  const res = type === 'logo'
    ? await apiSettings.uploadLogo(data)
    : type === 'favicon'
      ? await apiSettings.uploadFavicon(data)
      : await apiSettings.uploadLoginBanner(data)
  assignForm(res.data)
  await appSettingsStore.loadSiteSettings()
  toast.success(type === 'logo' ? 'Logo 已更新' : type === 'favicon' ? '站点图标已更新' : '登录页 Banner 已更新')
}
</script>

<template>
  <div class="bg-card min-h-full">
    <YdPageHeader title="系统设置" class="mb-0">
      <YdButton v-if="activeSettingTab === 'basic' || activeSettingTab === 'login'" v-auth="'system:setting:edit'" :loading="saving" @click="saveSettings">
        <YdIcon name="morph:save" />
        保存设置
      </YdButton>
      <YdButton v-if="activeSettingTab === 'integration'" v-auth="'system:setting:edit'" :loading="integrationSaving" @click="saveIntegration">
        <YdIcon name="morph:save" />
        保存集成配置
      </YdButton>
    </YdPageHeader>

    <YdLoadingOverlay :loading="loading">
      <YdSettingsLayout v-model="activeSettingTab" :list="settingTabs" surface width-class="max-w-[960px]">
        <template #basic>
          <YdSettingsSection
            title="基础信息"
            description="站点名称、描述与版权信息用于页脚、浏览器标题与对外展示。"
          >
            <YdForm :model="form" layout="vertical">
              <div class="flex flex-col gap-4 max-w-xl">
                <YdFormItem label="站点名称" required>
                  <YdInput v-model="form.siteName" class="w-full" />
                </YdFormItem>
                <YdFormItem label="站点描述">
                  <YdInput v-model="form.siteDescription" class="w-full" />
                </YdFormItem>
                <YdFormItem label="版权公司">
                  <YdInput v-model="form.copyrightCompany" class="w-full" />
                </YdFormItem>
                <YdFormItem label="版权网站">
                  <YdInput v-model="form.copyrightWebsite" class="w-full" />
                </YdFormItem>
                <YdFormItem label="版权年份">
                  <YdInput v-model="form.copyrightDates" placeholder="2026" class="w-full" />
                </YdFormItem>
              </div>
            </YdForm>
          </YdSettingsSection>
        </template>

        <template #brand>
          <YdSettingsSection
            title="品牌素材"
            description="选择文件后立即上传并生效，无需单独保存。"
          >
            <div class="flex flex-col gap-4">
              <div class="asset-panel">
                <div class="asset-panel__title">
                  Logo
                </div>
                <div class="asset-preview">
                  <img v-if="form.logo" :src="form.logo" alt="Logo">
                  <YdIcon v-else name="morph:image" class="text-muted-foreground size-8" />
                </div>
                <YdButton variant="outline" class="w-full" @click="logoInput?.open()">
                  <YdIcon name="morph:cloud-upload" />
                  上传 Logo
                </YdButton>
                <YdFilePicker ref="logoInput" accept="image/*" label="选择Logo 图片" class="hidden" @select="files => uploadAsset(files, 'logo')">
                  <template #default />
                </YdFilePicker>
              </div>

              <div class="asset-panel">
                <div class="asset-panel__title">
                  Favicon
                </div>
                <div class="asset-preview asset-preview--small">
                  <img v-if="form.favicon" :src="form.favicon" alt="Favicon">
                  <YdIcon v-else name="morph:globe" class="text-muted-foreground size-7" />
                </div>
                <YdButton variant="outline" class="w-full" @click="faviconInput?.open()">
                  <YdIcon name="morph:cloud-upload" />
                  上传 Favicon
                </YdButton>
                <YdFilePicker ref="faviconInput" accept="image/*" label="选择站点图标" class="hidden" @select="files => uploadAsset(files, 'favicon')">
                  <template #default />
                </YdFilePicker>
              </div>

              <div class="asset-panel">
                <div class="asset-panel__title">
                  登录页 Banner
                </div>
                <div class="asset-preview">
                  <img v-if="form.loginBanner" :src="form.loginBanner" alt="登录页 Banner">
                  <YdIcon v-else name="morph:image" class="text-muted-foreground size-8" />
                </div>
                <YdButton variant="outline" class="w-full" @click="loginBannerInput?.open()">
                  <YdIcon name="morph:cloud-upload" />
                  上传登录页 Banner
                </YdButton>
                <YdFilePicker ref="loginBannerInput" accept="image/*" label="选择登录页 Banner" class="hidden" @select="files => uploadAsset(files, 'loginBanner')">
                  <template #default />
                </YdFilePicker>
              </div>
            </div>
          </YdSettingsSection>
        </template>

        <template #login>
          <YdSettingsSection
            title="登录方式"
            description="控制打开登录页时默认选中的登录方式。"
          >
            <YdForm :model="form" layout="vertical" class="max-w-md">
              <YdFormItem label="默认登录方式">
                <YdSelect v-model="form.loginDefaultMethod" :options="loginMethodOptions" class="w-full" />
                <template #extra>
                  <span class="text-xs text-muted-foreground">
                    第三方登录入口需已启用且以登录 Tab 呈现（插件声明 TAB），否则不出现在此列表。
                  </span>
                </template>
              </YdFormItem>
            </YdForm>
          </YdSettingsSection>
        </template>

        <template #integration>
          <IntegrationSettings ref="integrationRef" />
        </template>
      </YdSettingsLayout>
    </YdLoadingOverlay>
  </div>
</template>

<style scoped>
.asset-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.asset-panel__title {
  font-weight: 600;
}

.asset-preview {
  display: grid;
  place-items: center;
  height: 120px;
  border: 1px dashed var(--color-border-2);
  border-radius: 6px;
  background: var(--color-fill-1);
}

.asset-preview img {
  max-width: 80%;
  max-height: 72px;
  object-fit: contain;
}

.asset-preview--small img {
  max-width: 48px;
  max-height: 48px;
}
</style>

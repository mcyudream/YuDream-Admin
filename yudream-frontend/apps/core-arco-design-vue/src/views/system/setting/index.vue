<script setup lang="ts">
import type { SiteSetting } from '@/api/modules/settings'
import apiSettings from '@/api/modules/settings'
import { toBackendAssetUrl } from '@/utils/backend-url'

const toast = useFaToast()
const appSettingsStore = useAppSettingsStore()

const loading = ref(false)
const saving = ref(false)
const logoInput = ref<HTMLInputElement>()
const faviconInput = ref<HTMLInputElement>()
const loginBannerInput = ref<HTMLInputElement>()

const form = reactive<SiteSetting>({
  siteName: '',
  siteDescription: '',
  logo: '',
  favicon: '',
  loginBanner: '',
  copyrightCompany: '',
  copyrightWebsite: '',
  copyrightDates: '',
})

onMounted(loadSettings)

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
  })
}

async function uploadAsset(event: Event, type: 'logo' | 'favicon' | 'loginBanner') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
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
  <div>
    <FaPageHeader title="系统设置" class="mb-0" />

    <FaPageMain>
      <div v-loading="loading" class="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <a-form :model="form" layout="vertical">
          <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            <a-form-item label="站点名称" required>
              <FaInput v-model="form.siteName" class="w-full" />
            </a-form-item>
            <a-form-item label="站点描述">
              <FaInput v-model="form.siteDescription" class="w-full" />
            </a-form-item>
            <a-form-item label="版权公司">
              <FaInput v-model="form.copyrightCompany" class="w-full" />
            </a-form-item>
            <a-form-item label="版权网站">
              <FaInput v-model="form.copyrightWebsite" class="w-full" />
            </a-form-item>
            <a-form-item label="版权年份">
              <FaInput v-model="form.copyrightDates" placeholder="2026" class="w-full" />
            </a-form-item>
          </div>
          <div class="mt-2 flex justify-end">
            <FaButton :loading="saving" @click="saveSettings">
              <FaIcon name="i-ri:save-3-line" />
              保存设置
            </FaButton>
          </div>
        </a-form>

        <div class="space-y-4">
          <div class="asset-panel">
            <div class="asset-panel__title">
              Logo
            </div>
            <div class="asset-preview">
              <img v-if="form.logo" :src="form.logo" alt="Logo">
              <FaIcon v-else name="i-ri:image-line" class="size-8 text-muted-foreground" />
            </div>
            <FaButton variant="outline" class="w-full" @click="logoInput?.click()">
              <FaIcon name="i-ri:upload-cloud-2-line" />
              上传 Logo
            </FaButton>
            <input ref="logoInput" type="file" accept="image/*" hidden @change="uploadAsset($event, 'logo')">
          </div>

          <div class="asset-panel">
            <div class="asset-panel__title">
              Favicon
            </div>
            <div class="asset-preview asset-preview--small">
              <img v-if="form.favicon" :src="form.favicon" alt="Favicon">
              <FaIcon v-else name="i-ri:global-line" class="size-7 text-muted-foreground" />
            </div>
            <FaButton variant="outline" class="w-full" @click="faviconInput?.click()">
              <FaIcon name="i-ri:upload-cloud-2-line" />
              上传 Favicon
            </FaButton>
            <input ref="faviconInput" type="file" accept="image/*" hidden @change="uploadAsset($event, 'favicon')">
          </div>

          <div class="asset-panel">
            <div class="asset-panel__title">
              登录页 Banner
            </div>
            <div class="asset-preview">
              <img v-if="form.loginBanner" :src="form.loginBanner" alt="登录页 Banner">
              <FaIcon v-else name="i-ri:image-line" class="size-8 text-muted-foreground" />
            </div>
            <FaButton variant="outline" class="w-full" @click="loginBannerInput?.click()">
              <FaIcon name="i-ri:upload-cloud-2-line" />
              上传登录页 Banner
            </FaButton>
            <input ref="loginBannerInput" type="file" accept="image/*" hidden @change="uploadAsset($event, 'loginBanner')">
          </div>
        </div>
      </div>
    </FaPageMain>
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

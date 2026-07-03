<script setup lang="ts">
import type { ApiDocSettings } from '@/api/modules/platform-docs'
import apiDocs from '@/api/modules/platform-docs'

const toast = useFaToast()

const loading = ref(false)
const saving = ref(false)

const form = reactive<ApiDocSettings>({
  enabled: false,
  apiKeyAccessEnabled: false,
  title: 'YuDream Admin API',
  description: 'YuDream Admin 系统接口文档',
  version: '1.0.0',
  openApiPath: '/v3/api-docs',
  swaggerUiPath: '/swagger-ui/index.html',
})

const swaggerUrl = computed(() => backendEndpoint(form.swaggerUiPath))
const openApiUrl = computed(() => backendEndpoint(form.openApiPath))

onMounted(loadSettings)

async function loadSettings() {
  loading.value = true
  try {
    const res = await apiDocs.settings()
    assignForm(res.data)
  }
  finally {
    loading.value = false
  }
}

async function saveSettings() {
  saving.value = true
  try {
    const res = await apiDocs.update(form)
    assignForm(res.data)
    toast.success('API 文档配置已保存')
  }
  finally {
    saving.value = false
  }
}

function assignForm(data: ApiDocSettings) {
  Object.assign(form, {
    ...data,
    title: data.title || 'YuDream Admin API',
    description: data.description || '',
    version: data.version || '1.0.0',
    openApiPath: data.openApiPath || '/v3/api-docs',
    swaggerUiPath: data.swaggerUiPath || '/swagger-ui/index.html',
  })
}

function openSwagger() {
  window.open(swaggerUrl.value, '_blank', 'noopener,noreferrer')
}

function openOpenApi() {
  window.open(openApiUrl.value, '_blank', 'noopener,noreferrer')
}

function backendEndpoint(path: string) {
  const normalized = path.startsWith('/') ? path : `/${path}`
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) {
    return `/proxy${normalized}`
  }
  const base = import.meta.env.VITE_APP_API_BASEURL || window.location.origin
  return `${base.replace(/\/$/, '')}${normalized}`
}
</script>

<template>
  <div>
    <FaPageHeader title="API 文档" class="mb-0">
      <FaButton variant="outline" :disabled="!form.enabled" @click="openOpenApi">
        <FaIcon name="i-ri:braces-line" />
        OpenAPI
      </FaButton>
      <FaButton variant="outline" :disabled="!form.enabled" @click="openSwagger">
        <FaIcon name="i-ri:external-link-line" />
        Swagger UI
      </FaButton>
      <FaButton v-auth="'platform:docs:config'" :loading="saving" @click="saveSettings">
        <FaIcon name="i-ri:save-3-line" />
        保存配置
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div v-loading="loading" class="docs-layout">
        <section class="settings-panel">
          <a-form :model="form" layout="vertical">
            <div class="switch-grid">
              <div class="switch-item">
                <div class="switch-label">
                  <FaIcon name="i-ri:file-list-2-line" />
                  <span>启用文档</span>
                </div>
                <FaSwitch v-model="form.enabled" />
              </div>
              <div class="switch-item">
                <div class="switch-label">
                  <FaIcon name="i-ri:key-2-line" />
                  <span>允许 API Key</span>
                </div>
                <FaSwitch v-model="form.apiKeyAccessEnabled" />
              </div>
            </div>

            <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
              <a-form-item label="文档标题" required>
                <FaInput v-model="form.title" class="w-full" />
              </a-form-item>
              <a-form-item label="版本">
                <FaInput v-model="form.version" class="w-full" />
              </a-form-item>
              <a-form-item label="OpenAPI 地址">
                <FaInput v-model="form.openApiPath" class="w-full" />
              </a-form-item>
              <a-form-item label="Swagger UI 地址">
                <FaInput v-model="form.swaggerUiPath" class="w-full" />
              </a-form-item>
              <a-form-item label="描述" class="md:col-span-2">
                <a-textarea v-model="form.description" :auto-size="{ minRows: 3, maxRows: 5 }" />
              </a-form-item>
            </div>
          </a-form>
        </section>

        <section class="preview-panel">
          <div class="preview-toolbar">
            <div class="preview-title">
              <FaIcon name="i-ri:window-line" />
              <span>文档预览</span>
            </div>
            <FaTag :variant="form.enabled ? 'default' : 'secondary'">
              {{ form.enabled ? '已启用' : '未启用' }}
            </FaTag>
          </div>
          <iframe v-if="form.enabled" :src="swaggerUrl" title="Swagger UI" />
          <div v-else class="empty-preview">
            <FaIcon name="i-ri:file-lock-line" />
            <span>API 文档未启用</span>
          </div>
        </section>
      </div>
    </FaPageMain>
  </div>
</template>

<style scoped>
.docs-layout {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.settings-panel,
.preview-panel {
  display: grid;
  gap: 16px;
}

.switch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.switch-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.switch-label,
.preview-title,
.preview-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.preview-toolbar {
  justify-content: space-between;
}

.preview-panel iframe,
.empty-preview {
  width: 100%;
  min-height: 680px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-fill-1);
}

.empty-preview {
  display: grid;
  place-content: center;
  gap: 10px;
  color: var(--color-text-3);
}

.empty-preview :deep(.fa-icon) {
  margin: 0 auto;
  font-size: 32px;
}

@media (max-width: 1180px) {
  .docs-layout {
    grid-template-columns: 1fr;
  }

  .preview-panel iframe,
  .empty-preview {
    min-height: 560px;
  }
}
</style>

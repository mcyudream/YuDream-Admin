<script setup lang="ts">
import type { ApiDocSettings } from '@/api/modules/platform-docs'
import apiDocs from '@/api/modules/platform-docs'

const toast = useFaToast()

const loading = ref(false)
const saving = ref(false)
const docTicket = ref('')
const ticketExpireAt = ref(0)

const form = reactive<ApiDocSettings>({
  enabled: false,
  apiKeyAccessEnabled: false,
  title: 'YuDream Admin API',
  description: 'YuDream Admin 系统接口文档。',
  version: '1.0.0',
  openApiPath: '/v3/api-docs',
  swaggerUiPath: '/swagger-ui/index.html',
})

const openApiUrl = computed(() => withDocTicket(backendEndpoint(form.openApiPath)))
const swaggerAssetBase = computed(() => backendEndpoint('/swagger-ui').replace(/\/$/, ''))
const swaggerPreviewReady = computed(() => form.enabled && Boolean(docTicket.value))
const swaggerPreviewHtml = computed(() => createSwaggerPreviewHtml(openApiUrl.value, swaggerAssetBase.value))

onMounted(loadSettings)

async function loadSettings() {
  loading.value = true
  try {
    const res = await apiDocs.settings()
    assignForm(res.data)
    if (form.enabled) {
      await ensureDocTicket()
    }
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
    if (form.enabled) {
      await refreshDocTicket()
    }
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

async function openSwagger() {
  const popup = window.open('', '_blank')
  await ensureDocTicket(true)
  if (!popup) {
    toast.error('无法打开 Swagger UI 窗口')
    return
  }
  popup.document.open()
  popup.document.write(swaggerPreviewHtml.value)
  popup.document.close()
}

async function openOpenApi() {
  await ensureDocTicket(true)
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

function createSwaggerPreviewHtml(openApi: string, assetBase: string) {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>YuDream Admin API</title>
  <link rel="stylesheet" href="${assetBase}/swagger-ui.css">
  <style>
    html, body, #swagger-ui { margin: 0; min-height: 100%; background: #fff; }
    .swagger-ui .topbar { display: none; }
  </style>
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="${assetBase}/swagger-ui-bundle.js"><\/script>
  <script src="${assetBase}/swagger-ui-standalone-preset.js"><\/script>
  <script>
    window.onload = function () {
      window.ui = SwaggerUIBundle({
        url: ${JSON.stringify(openApi)},
        dom_id: '#swagger-ui',
        deepLinking: true,
        validatorUrl: null,
        presets: [
          SwaggerUIBundle.presets.apis,
          SwaggerUIStandalonePreset
        ],
        plugins: [
          SwaggerUIBundle.plugins.DownloadUrl
        ],
        layout: 'StandaloneLayout'
      })
    }
  <\/script>
</body>
</html>`
}

async function ensureDocTicket(force = false) {
  if (!force && docTicket.value && ticketExpireAt.value > Date.now() + 60_000) {
    return
  }
  await refreshDocTicket()
}

async function refreshDocTicket() {
  try {
    const res = await apiDocs.accessTicket()
    docTicket.value = res.data.ticket
    ticketExpireAt.value = Date.now() + Math.max(res.data.expiresIn - 30, 30) * 1000
  }
  catch {
    docTicket.value = ''
    ticketExpireAt.value = 0
  }
}

function withDocTicket(url: string) {
  if (!docTicket.value) {
    return url
  }
  return appendQuery(url, {
    doc_ticket: docTicket.value,
  })
}

function appendQuery(url: string, params: Record<string, string>) {
  const [target, hash = ''] = url.split('#')
  const query = Object.entries(params)
    .filter(([, value]) => value)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&')
  if (!query) {
    return url
  }
  const separator = target.includes('?') ? '&' : '?'
  return `${target}${separator}${query}${hash ? `#${hash}` : ''}`
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
          <iframe v-if="swaggerPreviewReady" :srcdoc="swaggerPreviewHtml" title="Swagger UI" />
          <div v-else-if="form.enabled" class="empty-preview">
            <FaIcon name="i-ri:loader-4-line" />
            <span>正在获取 API 文档访问票据</span>
          </div>
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

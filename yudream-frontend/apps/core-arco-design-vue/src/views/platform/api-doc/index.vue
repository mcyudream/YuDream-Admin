<script setup lang="ts">
import type { ApiDocSettings } from '@/api/modules/platform-docs'
import apiDocs from '@/api/modules/platform-docs'

const toast = useYdToast()
const appSettingsStore = useAppSettingsStore()
const defaultDocTitle = computed(() => {
  const siteName = appSettingsStore.siteName || ''
  return siteName ? `${siteName} API` : 'API 文档'
})

const loading = ref(false)
const saving = ref(false)
const ticketLoading = ref(false)
const docTicket = ref('')
const ticketExpireAt = ref(0)
/** 设置读取失败时保留现场并禁用保存，避免用默认值覆盖线上配置。 */
const settingsError = ref(false)
/** 票据刷新后自增，强制 iframe 重新加载 srcdoc。 */
const previewRevision = ref(0)

const form = reactive<ApiDocSettings>({
  enabled: false,
  apiKeyAccessEnabled: false,
  title: '',
  description: '',
  version: '1.0.0',
  openApiPath: '/v3/api-docs',
  swaggerUiPath: '/swagger-ui/index.html',
})

/** 浏览器外框地址栏：只展示保存的文档入口，不携带访问票据，也不会被请求。 */
const previewAddress = ref(backendEndpoint(form.swaggerUiPath))

const resolvedOpenApiUrl = computed(() => backendEndpoint(form.openApiPath))
const resolvedSwaggerUiUrl = computed(() => backendEndpoint(form.swaggerUiPath))
const openApiUrl = computed(() => withDocTicket(resolvedOpenApiUrl.value))
const swaggerAssetBase = computed(() => backendEndpoint('/swagger-ui').replace(/\/$/, ''))
const swaggerPreviewHtml = computed(() => createSwaggerPreviewHtml(openApiUrl.value, swaggerAssetBase.value))

/** 预览区状态机：设置失败 / 未启用 / 取票据中 / 票据失败 / 可渲染。 */
const previewState = computed<'unavailable' | 'disabled' | 'loading' | 'failed' | 'ready'>(() => {
  if (settingsError.value) {
    return 'unavailable'
  }
  if (!form.enabled) {
    return 'disabled'
  }
  if (docTicket.value) {
    return 'ready'
  }
  return ticketLoading.value ? 'loading' : 'failed'
})

onMounted(loadSettings)

async function loadSettings() {
  loading.value = true
  try {
    const res = await apiDocs.settings()
    assignForm(res.data)
    settingsError.value = false
    if (form.enabled) {
      await ensureDocTicket()
    }
  }
  catch {
    settingsError.value = true
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
    title: data.title || defaultDocTitle.value,
    description: data.description || '',
    version: data.version || '1.0.0',
    openApiPath: data.openApiPath || '/v3/api-docs',
    swaggerUiPath: data.swaggerUiPath || '/swagger-ui/index.html',
  })
  previewAddress.value = backendEndpoint(form.swaggerUiPath)
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

/** 外框刷新与失败重试共用：强制续期票据并重载预览。 */
async function refreshPreview() {
  const fallbackTicket = docTicket.value
  const fallbackExpireAt = ticketExpireAt.value
  await ensureDocTicket(true)
  if (docTicket.value) {
    previewRevision.value += 1
    return
  }
  if (!fallbackTicket) {
    return
  }
  // 续期失败时保留尚未过期的旧票据，不让正在显示的预览被失败态替换。
  docTicket.value = fallbackTicket
  ticketExpireAt.value = fallbackExpireAt
  toast.warning('访问票据续期失败', {
    description: '预览保持当前内容，可稍后再次刷新。',
  })
}

function handleAddressNavigate(url: string) {
  toast.info('地址栏仅驱动本地演示状态', {
    description: `预览不会访问 ${url}，始终加载当前配置的 OpenAPI 文档。`,
  })
}

function backendEndpoint(path: string) {
  const normalized = path.startsWith('/') ? path : `/${path}`
  const base = import.meta.env.VITE_APP_API_BASEURL || window.location.origin
  return `${base.replace(/\/$/, '')}${normalized}`
}

function createSwaggerPreviewHtml(openApi: string, assetBase: string) {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${defaultDocTitle.value}</title>
  <link rel="stylesheet" href="${assetBase}/swagger-ui.css">
  <style>
    /* Swagger UI 在独立 iframe 文档中渲染，无法继承宿主主题变量，有意使用固定浅色背景 */
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
  ticketLoading.value = true
  try {
    const res = await apiDocs.accessTicket()
    docTicket.value = res.data.ticket
    ticketExpireAt.value = Date.now() + Math.max(res.data.expiresIn - 30, 30) * 1000
  }
  catch {
    docTicket.value = ''
    ticketExpireAt.value = 0
  }
  finally {
    ticketLoading.value = false
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
    <YdPageHeader title="API 文档" description="维护文档入口与访问控制，并确认真实渲染效果" class="mb-0">
      <YdButton variant="outline" :disabled="!form.enabled" @click="openOpenApi">
        <YdIcon name="morph:braces" />
        OpenAPI
      </YdButton>
      <YdButton variant="outline" :disabled="!form.enabled" @click="openSwagger">
        <YdIcon name="morph:external-link" />
        Swagger UI
      </YdButton>
      <YdButton v-auth="'platform:docs:config'" :loading="saving" :disabled="settingsError" @click="saveSettings">
        <YdIcon name="morph:save" />
        保存配置
      </YdButton>
    </YdPageHeader>

    <YdPageMain>
      <YdLoadingOverlay :loading="loading">
        <div class="docs-layout">
          <section class="settings-panel" aria-label="API 文档设置">
            <YdAlert
              v-if="settingsError"
              variant="destructive"
              icon="morph:circle-alert"
              title="设置加载失败"
              description="当前展示的是默认值；重新加载成功前保存被禁用，避免覆盖线上配置。"
            />

            <YdForm :model="form" layout="vertical" class="settings-form">
              <div class="settings-group">
                <h2 class="group-title">
                  <YdIcon name="morph:file-text" />
                  公开配置
                </h2>
                <YdFormItem label="文档标题" required extra="留空时使用站点名称推导的默认标题。">
                  <YdInput v-model="form.title" class="w-full" :placeholder="defaultDocTitle" />
                </YdFormItem>
                <YdFormItem label="版本">
                  <YdInput v-model="form.version" class="w-full" placeholder="1.0.0" />
                </YdFormItem>
                <YdFormItem label="描述">
                  <YdTextarea v-model="form.description" rows="3" class="w-full" placeholder="用于说明文档用途与访问方式" />
                </YdFormItem>
              </div>

              <div class="settings-group">
                <h2 class="group-title">
                  <YdIcon name="morph:key-round" />
                  访问控制
                </h2>
                <YdFormItem
                  label="启用文档"
                  :extra="form.enabled ? '文档入口与预览当前可用。' : '关闭后文档入口与预览都不可访问。'"
                >
                  <YdSwitch v-model="form.enabled" />
                </YdFormItem>
                <YdFormItem label="允许 API Key 访问" extra="开启后允许携带 API Key 调用文档接口。">
                  <YdSwitch v-model="form.apiKeyAccessEnabled" />
                </YdFormItem>
              </div>

              <div class="settings-group">
                <h2 class="group-title">
                  <YdIcon name="morph:link" />
                  文档入口
                </h2>
                <YdFormItem label="OpenAPI 地址" :extra="resolvedOpenApiUrl">
                  <YdInput v-model="form.openApiPath" class="w-full" placeholder="/v3/api-docs" />
                </YdFormItem>
                <YdFormItem label="Swagger UI 地址" :extra="resolvedSwaggerUiUrl">
                  <YdInput v-model="form.swaggerUiPath" class="w-full" placeholder="/swagger-ui/index.html" />
                </YdFormItem>
              </div>
            </YdForm>
          </section>

          <section class="preview-panel" aria-label="文档预览">
            <header class="preview-head">
              <div class="preview-heading">
                <h2>文档预览</h2>
                <p>确认当前配置对应的实际文档效果。</p>
              </div>
              <div class="preview-status">
                <YdTag v-if="previewState === 'ready'" variant="secondary" icon="morph:circle-check">
                  已启用
                </YdTag>
                <YdTag v-else-if="previewState === 'loading'" variant="outline" icon="morph:loader-circle">
                  正在获取票据
                </YdTag>
                <YdTag v-else-if="previewState === 'failed'" variant="outline" icon="morph:triangle-alert">
                  票据获取失败
                </YdTag>
                <YdTag v-else-if="previewState === 'unavailable'" variant="outline" icon="morph:cloud-off">
                  设置不可用
                </YdTag>
                <YdTag v-else variant="outline" icon="morph:file-lock">
                  未启用
                </YdTag>
              </div>
            </header>

            <YdMockupBrowser
              v-model:url="previewAddress"
              class="preview-browser"
              @navigate="handleAddressNavigate"
              @refresh="refreshPreview"
            >
              <iframe
                v-if="previewState === 'ready'"
                :key="previewRevision"
                class="preview-frame"
                :srcdoc="swaggerPreviewHtml"
                title="Swagger UI 预览"
              />
              <div v-else-if="previewState === 'loading'" class="preview-state">
                <YdLoading text="正在获取访问票据" />
              </div>
              <div v-else-if="previewState === 'unavailable'" class="preview-state">
                <YdResult
                  status="error"
                  title="设置加载失败"
                  description="未能读取当前 API 文档配置，预览与保存暂不可用。"
                  illustration-size="md"
                >
                  <YdButton variant="outline" size="sm" :loading="loading" @click="loadSettings">
                    <YdIcon name="morph:refresh-cw" />
                    重新加载设置
                  </YdButton>
                </YdResult>
              </div>
              <div v-else-if="previewState === 'failed'" class="preview-state">
                <YdResult
                  status="error"
                  title="访问票据获取失败"
                  description="文档可能已关闭或票据接口暂不可用；确认设置后重试。"
                  illustration-size="md"
                >
                  <YdButton variant="outline" size="sm" :loading="ticketLoading" @click="refreshPreview">
                    <YdIcon name="morph:refresh-cw" />
                    重试获取票据
                  </YdButton>
                </YdResult>
              </div>
              <div v-else class="preview-state">
                <YdEmpty
                  illustration="empty"
                  title="API 文档未启用"
                  description="在设置栏开启「启用文档」并保存后，这里会加载真实的 Swagger UI 预览。"
                >
                  <YdButton variant="outline" size="sm" :loading="loading" @click="loadSettings">
                    <YdIcon name="morph:refresh-cw" />
                    重新加载设置
                  </YdButton>
                </YdEmpty>
              </div>
            </YdMockupBrowser>

            <p class="preview-hint">
              外框地址栏与前进后退只驱动本地演示状态，不会请求该地址；预览始终加载当前配置的 OpenAPI 文档，Swagger UI 在独立文档中渲染并保持固定浅色外观。
            </p>
          </section>
        </div>
      </YdLoadingOverlay>
    </YdPageMain>
  </div>
</template>

<style scoped>
/* 安静文档工作台：紧凑设置栏 + 占主导的真实预览，窄屏整体转为单列全宽。 */
.docs-layout {
  --preview-min-height: 680px;

  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: var(--yd-workspace-gap);
  align-items: start;
}

.settings-panel {
  display: grid;
  gap: var(--yd-content-gap);
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius);
  background: var(--color-fill-1);
}

.settings-group {
  display: grid;
  min-width: 0;
}

.settings-group + .settings-group {
  padding-top: 16px;
  border-top: 1px solid var(--color-border-2);
}

.group-title {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 0 0 12px;
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

/* 入口地址可能很长：帮助文本换行显示，不撑破设置栏。 */
.settings-form :deep(.arco-form-item-extra) {
  font-size: 11px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.preview-panel {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.preview-head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.preview-heading h2 {
  margin: 0;
  color: var(--color-text-1);
  font-size: 16px;
  font-weight: 650;
}

.preview-heading p {
  margin: 4px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
}

.preview-status {
  display: flex;
  gap: 8px;
  align-items: center;
}

.preview-browser {
  min-width: 0;
}

.preview-frame {
  display: block;
  width: 100%;
  height: var(--preview-min-height);
  border: 0;
  animation: preview-frame-in var(--yd-motion-duration-normal) var(--yd-motion-ease) both;
}

.preview-state {
  display: grid;
  min-height: var(--preview-min-height);
  padding: var(--yd-surface-padding);
  place-items: center;
}

.preview-state > * {
  max-width: 440px;
}

.preview-hint {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

@keyframes preview-frame-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .preview-frame {
    animation: none;
  }
}

@media (max-width: 1180px) {
  .docs-layout {
    --preview-min-height: 520px;

    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .docs-layout {
    --preview-min-height: 420px;
  }

  .preview-state {
    padding: var(--yd-content-gap);
  }
}
</style>

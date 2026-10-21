<script setup lang="ts">
// 站点草稿预览：与公开站 /site 使用同一份整页 HTML（buildStudioSiteHtml）与同一渲染容器，所见即发布后所得。
// 工作台标签页打开时实时显示其内存文档（含未保存更改）；否则显示服务端已保存草稿。只对有 CMS 查看权限的登录用户开放。
import type { SiteDocument } from '@/types/site-studio'
import type { StudioPreviewMessage } from '@/utils/site-studio-preview'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import apiStudio from '@/api/modules/platform-cms-studio'
import { buildStudioSiteHtml, validateStudioDocument } from '@/utils/site-studio'
import { normalizeStudioPreviewPath, openStudioPreviewChannel, studioPagePathToSlug } from '@/utils/site-studio-preview'
import StudioFrame from './studio-frame.vue'

defineOptions({ name: 'SiteStudioPreview' })

const route = useRoute()
const router = useRouter()
const themeCode = computed(() => String(route.params.themeCode || 'default'))
const document = ref<SiteDocument | null>(null)
const source = ref<'live' | 'draft' | ''>('')
const liveDirty = ref(false)
const revision = ref('')
const loading = ref(true)
const error = ref('')
const device = ref<'desktop' | 'tablet' | 'mobile'>('desktop')
const deviceWidths = { desktop: '100%', tablet: '992px', mobile: '390px' }

const page = computed(() => {
  const target = normalizeStudioPreviewPath(route.params.slug as string[] | string | undefined)
  return document.value?.pages.find(item => item.path.toLowerCase() === target) ?? null
})
const pageOptions = computed(() => (document.value?.pages ?? []).map(item => ({ label: `${item.name}（${item.path}）`, value: item.id })))
const srcdoc = computed(() => document.value && page.value ? buildStudioSiteHtml(document.value, page.value.id) : '')
const basePath = computed(() => `/site-preview/${encodeURIComponent(themeCode.value)}`)

function accept(json: string | null | undefined) {
  if (!json) {
    document.value = null
    return
  }
  const parsed: unknown = JSON.parse(json)
  validateStudioDocument(parsed)
  document.value = parsed
}

async function loadDraft() {
  loading.value = true
  error.value = ''
  try {
    const state = (await apiStudio.get(themeCode.value)).data
    if (source.value !== 'live') {
      accept(state.documentJson)
      source.value = 'draft'
      revision.value = state.revision
    }
  }
  catch (cause) {
    if (source.value !== 'live') {
      error.value = cause instanceof Error ? cause.message : '草稿加载失败'
    }
  }
  finally {
    loading.value = false
  }
}

let channel: BroadcastChannel | null = null
function connect() {
  channel?.close()
  channel = openStudioPreviewChannel(themeCode.value)
  if (!channel) {
    return
  }
  channel.onmessage = (event: MessageEvent<StudioPreviewMessage>) => {
    const message = event.data
    if (message?.type !== 'document' || message.themeCode !== themeCode.value) {
      return
    }
    try {
      accept(message.documentJson)
      source.value = 'live'
      liveDirty.value = Boolean(message.dirty)
      revision.value = message.revision || ''
      error.value = ''
      loading.value = false
    }
    catch {
      // 实时文档未通过校验时保留当前显示，不渲染不可信内容
    }
  }
  channel.postMessage({ type: 'request', themeCode: themeCode.value } satisfies StudioPreviewMessage)
}

watch(themeCode, () => {
  source.value = ''
  connect()
  void loadDraft()
}, { immediate: true })
onBeforeUnmount(() => channel?.close())

function selectPage(pageId: unknown) {
  const target = document.value?.pages.find(item => item.id === pageId)
  if (target) {
    void router.push({ name: 'siteStudioPreview', params: { themeCode: themeCode.value, slug: studioPagePathToSlug(target.path) } })
  }
}

const statusText = computed(() => source.value === 'live'
  ? (liveDirty.value ? '实时 · 含未保存更改' : '实时 · 与已保存草稿一致')
  : `已保存草稿${revision.value ? ` · 版本 ${revision.value}` : ''}`)
</script>

<template>
  <div class="site-preview">
    <header class="site-preview__bar" aria-label="预览工具栏">
      <strong class="text-sm">草稿预览</strong>
      <span class="site-preview__status">{{ statusText }} · 未发布内容，仅你可见</span>
      <span class="flex-1" />
      <YdSelect
        v-if="pageOptions.length"
        :model-value="page?.id"
        :options="pageOptions"
        aria-label="预览页面"
        class="w-56"
        @update:model-value="selectPage"
      />
      <YdSegmented v-model="device" size="sm" :options="[{ label: '桌面', value: 'desktop' }, { label: '平板', value: 'tablet' }, { label: '手机', value: 'mobile' }]" aria-label="预览宽度" />
    </header>
    <main class="site-preview__stage">
      <YdLoading v-if="loading && !document" />
      <YdResult v-else-if="error && !document" status="error" title="草稿预览加载失败" :description="error" />
      <YdEmpty v-else-if="!document" description="该主题还没有站点草稿，先在网站工作台创建并保存" />
      <YdEmpty v-else-if="!page" description="草稿中没有这个路径的页面，从右上角选择要预览的页面" />
      <div v-else class="site-preview__viewport" :style="{ width: deviceWidths[device] }">
        <StudioFrame :srcdoc="srcdoc" :base-path="basePath" height="calc(100svh - 96px)" :runtime="document && page ? { document, page } : undefined" />
      </div>
    </main>
  </div>
</template>

<style scoped>
.site-preview {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: oklch(var(--muted));
}
.site-preview__bar {
  position: sticky;
  z-index: 10;
  top: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  padding: 8px 16px;
  border-bottom: 1px solid oklch(var(--border));
  background: oklch(var(--background));
}
.site-preview__status {
  font-size: 12px;
  color: oklch(var(--muted-foreground));
}
.site-preview__stage {
  display: flex;
  flex: 1;
  justify-content: center;
  padding: 16px 0;
}
.site-preview__viewport {
  max-width: 100%;
  background: #ffffff;
  box-shadow: 0 1px 4px rgb(0 0 0 / 12%);
  transition: width var(--yd-motion-duration-normal, 200ms);
}
</style>

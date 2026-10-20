<script setup lang="ts">
// 主题视图：站点主题变量（tokens）编辑与页头/页脚入口。
// 这里的变量是「站点输出变量」（编译时注入公开站文档作用域），可以任意取值，
// 但它们只影响公开站内容，不影响后台界面主题；后台主题仍在「主题中心」管理。
// 所有修改统一经 store.execute 走 set-theme 命令；高级 JSON 为受控草稿，校验通过后才应用。
import type { SiteDocument, StudioNode } from '@/types/site-studio'
import { computed, ref, watch } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { serializeStudioPackage } from '@/utils/site-studio'
import { isStudioEmbeddedAsset, validateStudioDocument } from '@/utils/site-studio-validation'

defineOptions({ name: 'StudioThemeView' })

const emit = defineEmits<{ editRegion: [] }>()

const store = useSiteStudioStore()
const toast = useYdToast()

// ===== 站点主题变量 =====

interface TokenPreset {
  key: string
  label: string
  kind: 'color' | 'value'
  placeholder: string
}

const tokenPresets: TokenPreset[] = [
  { key: 'accent', label: '强调色', kind: 'color', placeholder: '如 #4f46e5' },
  { key: 'background', label: '页面背景', kind: 'color', placeholder: '如 #ffffff' },
  { key: 'surface', label: '卡片表面', kind: 'color', placeholder: '如 #f6f7fb' },
  { key: 'foreground', label: '正文文字', kind: 'color', placeholder: '如 #1b1d22' },
  { key: 'muted', label: '次要文字', kind: 'color', placeholder: '如 #6b7280' },
  { key: 'border', label: '边框', kind: 'color', placeholder: '如 #e5e7eb' },
  { key: 'radius', label: '圆角', kind: 'value', placeholder: '如 14px' },
  { key: 'space', label: '间距', kind: 'value', placeholder: '如 24px' },
]

const tokens = computed<Record<string, string>>(() => store.doc?.theme.tokens || {})
const customEntries = computed(() =>
  Object.entries(tokens.value).filter(([key]) => !tokenPresets.some(preset => preset.key === key)),
)

/** 应用完整 tokens（set-theme 是整体替换） */
function applyTokens(next: Record<string, string>) {
  try {
    store.execute({ type: 'set-theme', tokens: next })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '主题变量应用失败')
  }
}

function setToken(key: string, raw: string) {
  const value = raw.trim()
  if (!value) {
    return
  }
  applyTokens({ ...tokens.value, [key]: value })
}

/** 输入框 change 事件是原生 Event，从 target.value 取值 */
function onTokenChange(key: string, event: Event) {
  const target = event.target as HTMLInputElement | null
  setToken(key, target?.value ?? '')
}

function removeToken(key: string) {
  const next = { ...tokens.value }
  delete next[key]
  applyTokens(next)
}

/** 新增自定义变量：提交键名与值（键名做本地预检，最终以文档校验为准） */
const newToken = ref({ key: '', value: '' })

function addCustomToken() {
  const key = newToken.value.key.trim()
  const value = newToken.value.value.trim()
  if (!key || !value) {
    toast.error('变量名与值不能为空')
    return
  }
  if (!/^(?:--)?[a-z][\w-]*$/i.test(key)) {
    toast.error('变量名需以字母开头，只能包含字母、数字、下划线与连字符')
    return
  }
  applyTokens({ ...tokens.value, [key.replace(/^--/, '')]: value })
  newToken.value = { key: '', value: '' }
}

// ===== 页头 / 页脚入口 =====

function openRegion(region: 'header' | 'footer') {
  store.editRegion = region
  store.selectedNodeId = ''
  emit('editRegion')
}

// ===== 导出主题包 =====

const embeddedCount = computed(() => (store.doc?.assets || []).filter(asset => isStudioEmbeddedAsset(asset)).length)
const referencedCount = computed(() => (store.doc?.assets.length ?? 0) - embeddedCount.value)

function downloadJson(json: string, filename: string) {
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  // 延迟回收，避免浏览器尚未开始下载时对象 URL 被撤销
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

function exportThemePackage() {
  const doc = store.doc
  if (!doc) {
    return
  }
  try {
    downloadJson(serializeStudioPackage(doc, 'theme'), `theme-${store.themeCode}.json`)
    toast.success('主题包已导出')
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '主题包导出失败')
  }
}

// ===== 高级：完整主题 JSON（受控草稿） =====

const themeDraft = ref('')
const draftError = ref('')

function themeJsonText(): string {
  const doc = store.doc
  return JSON.stringify({
    tokens: doc?.theme.tokens || {},
    header: doc?.theme.header || [],
    footer: doc?.theme.footer || [],
  }, null, 2)
}

/** 工作文档每次变化（命令/撤销/导入/加载）都把草稿同步为最新主题 */
watch(() => store.editRevision, () => {
  themeDraft.value = themeJsonText()
  draftError.value = ''
}, { immediate: true })

/**
 * 校验草稿主题：解析 JSON → 构造替换 theme 后的完整文档 → 走完整文档校验。
 * dryRun 为 true 时只校验不应用。
 */
function validateThemeDraft(value: string): { tokens?: Record<string, string>, header?: StudioNode[], footer?: StudioNode[] } {
  const doc = store.doc
  if (!doc) {
    throw new Error('尚未创建站点文档')
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(value)
  }
  catch {
    throw new Error('不是合法 JSON')
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('主题必须是对象')
  }
  const record = parsed as Record<string, unknown>
  const unknownKeys = Object.keys(record).filter(key => !['tokens', 'header', 'footer'].includes(key))
  if (unknownKeys.length) {
    throw new Error(`未知字段：${unknownKeys.join('、')}（仅允许 tokens/header/footer）`)
  }
  const applied: { tokens?: Record<string, string>, header?: StudioNode[], footer?: StudioNode[] } = {}
  for (const key of ['tokens', 'header', 'footer'] as const) {
    if (record[key] !== undefined) {
      applied[key] = record[key] as any
    }
  }
  const scratch = JSON.parse(JSON.stringify({ ...doc, theme: { ...doc.theme, ...applied } })) as SiteDocument
  validateStudioDocument(scratch)
  return applied
}

function onDraftInput(value: string) {
  themeDraft.value = value
  try {
    validateThemeDraft(value)
    draftError.value = ''
  }
  catch (error) {
    draftError.value = error instanceof Error ? error.message : '草稿校验失败'
  }
}

function applyThemeDraft() {
  try {
    const applied = validateThemeDraft(themeDraft.value)
    store.execute({ type: 'set-theme', ...applied })
    draftError.value = ''
    toast.success('主题 JSON 已应用')
  }
  catch (error) {
    draftError.value = error instanceof Error ? error.message : '主题应用失败'
    toast.error(draftError.value)
  }
}
</script>

<template>
  <div class="h-full min-h-0 overflow-auto">
    <div class="mx-auto p-4 flex flex-col gap-5 max-w-3xl sm:p-6">
      <YdAlert>
        这里的变量是站点输出变量（编译后注入公开站文档作用域，如 <code class="font-mono">--accent</code>），只影响公开站内容，可按站点需要取任意颜色；它们不会改变后台界面主题，后台主题仍在「主题中心」管理。
      </YdAlert>

      <section class="flex flex-col gap-3" aria-label="站点主题变量">
        <h2 class="text-sm font-semibold m-0">
          站点主题变量
        </h2>
        <p class="text-xs text-muted-foreground m-0">
          变量以 <code class="font-mono">--名称</code> 注入站点，页面节点可用 <code class="font-mono">var(--名称)</code> 引用；清空输入即移除该变量。
        </p>
        <div class="flex flex-col gap-2">
          <div v-for="preset in tokenPresets" :key="preset.key" class="flex flex-wrap gap-2 items-center">
            <span class="text-sm shrink-0 w-20">{{ preset.label }}</span>
            <code class="text-xs text-muted-foreground font-mono shrink-0 w-24">--{{ preset.key }}</code>
            <YdInput
              v-auth="'platform:cms:edit'"
              :model-value="tokens[preset.key] ?? ''"
              :placeholder="preset.placeholder"
              class="flex-1 min-w-40"
              :aria-label="`${preset.label}变量值`"
              @change="onTokenChange(preset.key, $event)"
            />
            <YdColorPicker
              v-if="preset.kind === 'color'"
              v-auth="'platform:cms:edit'"
              :model-value="tokens[preset.key] || '#000000'"
              format="hex"
              show-alpha
              :aria-label="`选择${preset.label}`"
              @update:model-value="(value) => setToken(preset.key, String(value))"
            />
          </div>
        </div>

        <div v-if="customEntries.length" class="pt-3 border-t flex flex-col gap-2">
          <div v-for="[key, value] in customEntries" :key="key" class="flex flex-wrap gap-2 items-center">
            <span class="text-sm shrink-0 w-20">自定义</span>
            <code class="text-xs font-mono shrink-0 w-24">--{{ key }}</code>
            <YdInput
              :model-value="value"
              class="flex-1 min-w-40"
              :aria-label="`自定义变量 ${key} 的值`"
              @change="onTokenChange(key, $event)"
            />
            <YdButton
              v-auth="'platform:cms:edit'"
              variant="ghost"
              size="sm"
              aria-label="删除该变量"
              @click="removeToken(key)"
            >
              <YdIcon name="morph:trash-2" />
            </YdButton>
          </div>
        </div>

        <div v-auth="'platform:cms:edit'" class="pt-3 border-t flex flex-wrap gap-2 items-center">
          <YdInput v-model="newToken.key" class="w-40" placeholder="变量名，如 shadow" aria-label="新变量名" />
          <YdInput v-model="newToken.value" class="flex-1 min-w-40" placeholder="变量值，如 0 2px 8px rgba(0,0,0,.1)" aria-label="新变量值" />
          <YdButton v-auth="'platform:cms:edit'" variant="outline" @click="addCustomToken">
            <YdIcon name="morph:plus" /> 添加变量
          </YdButton>
        </div>
      </section>

      <section class="flex flex-col gap-2" aria-label="页头页脚">
        <h2 class="text-sm font-semibold m-0">
          页头 / 页脚
        </h2>
        <p class="text-xs text-muted-foreground m-0">
          页头与页脚是主题的公共区域，在所有页面之间共享；进入设计画布编辑后，记得回来保存草稿。
        </p>
        <div class="flex flex-wrap gap-2">
          <YdButton v-auth="'platform:cms:edit'" variant="outline" @click="openRegion('header')">
            <YdIcon name="morph:panel-top" /> 编辑页头 header
          </YdButton>
          <YdButton v-auth="'platform:cms:edit'" variant="outline" @click="openRegion('footer')">
            <YdIcon name="morph:panel-bottom" /> 编辑页脚 footer
          </YdButton>
        </div>
      </section>

      <section class="flex flex-col gap-2" aria-label="导出主题包">
        <h2 class="text-sm font-semibold m-0">
          导出主题包
        </h2>
        <p class="text-xs text-muted-foreground m-0">
          导出 kind=theme 的站点便携包（JSON）。内嵌资源 {{ embeddedCount }} 项以 data URL 打进包内；引用型资源 {{ referencedCount }} 项不会打包，导入方需保证这些地址可访问——主题包并非全离线。
          导入后可在主题中心激活该主题（本工作台不会自动激活任何插件主题）。
        </p>
        <div class="flex flex-wrap gap-2">
          <YdButton variant="outline" :disabled="!store.doc" @click="exportThemePackage">
            <YdIcon name="morph:download" /> 导出主题包
          </YdButton>
          <RouterLink to="/platform/theme-center" class="studio-inline-link text-xs self-center">
            前往主题中心
          </RouterLink>
        </div>
      </section>

      <section class="flex flex-col gap-2" aria-label="高级主题 JSON">
        <h2 class="text-sm font-semibold m-0">
          高级：完整主题 JSON
        </h2>
        <p class="text-xs text-muted-foreground m-0">
          直接编辑 tokens / header / footer 的完整 JSON 草稿；每次输入实时校验，校验通过后才会应用，不会绕过统一命令管道。
        </p>
        <YdCodeEditor
          v-auth="'platform:cms:edit'"
          :model-value="themeDraft"
          language="json"
          height="360px"
          aria-label="主题 JSON 草稿"
          :error="draftError"
          @update:model-value="onDraftInput"
        />
        <div class="flex flex-wrap gap-2">
          <YdButton v-auth="'platform:cms:edit'" :disabled="Boolean(draftError) || !store.doc" @click="applyThemeDraft">
            <YdIcon name="morph:check" /> 应用主题
          </YdButton>
          <YdButton variant="ghost" :disabled="!store.doc" @click="themeDraft = themeJsonText(); draftError = ''">
            重置为当前主题
          </YdButton>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.studio-inline-link {
  color: var(--color-text-2);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.studio-inline-link:hover {
  color: var(--color-text-1);
}
</style>

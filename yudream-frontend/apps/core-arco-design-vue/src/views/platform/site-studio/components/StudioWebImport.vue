<script setup lang="ts">
// 网页导入：选择一个网站文件夹（HTML + CSS + 图片/字体/音频），把其中一个网页转换为站点页面，
// 保留网页的类结构为主题样式类。先「分析」只做本地转换与报告；确认「导入」后才上传被引用的资源
// （按相对路径去重，已上传过的同路径资源直接复用），并以一次 batch 写入工作台（可撤销、不自动保存）。
// 导入的是静态结构：网页脚本不会被执行或保留，音乐/粒子/数据等行为改用「站点功能」与数据绑定配置。
import type { StudioAsset, StudioCommand } from '@/types/site-studio'
import type { StudioWebImportResult } from '@/utils/site-studio-import'
import { computed, ref } from 'vue'
import apiFiles from '@/api/modules/files'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { importStudioWebPage, mergeStudioClasses, mergeStudioFonts, rasterizeInlineSvg } from '@/utils/site-studio-import'
import { studioKindOf } from './studio-upload'

defineOptions({ name: 'StudioWebImport' })

const store = useSiteStudioStore()
const toast = useYdToast()
const picker = useYdFilePicker()

const files = ref<Map<string, File>>(new Map())
const folderName = ref('')
const htmlPath = ref('')
const form = ref({
  page: 'body > *',
  header: '',
  footer: '',
  remove: 'script, noscript',
  currentClasses: 'is-active, active, current',
  target: '__new__',
  name: '导入页面',
  path: '/imported',
  replaceChrome: false,
})
const analysis = ref<StudioWebImportResult | null>(null)
const pendingAssets = ref<string[]>([])
const busy = ref(false)

const htmlOptions = computed(() => [...files.value.keys()].filter(path => /\.html?$/i.test(path)).sort().map(path => ({ label: path, value: path })))
const targetOptions = computed(() => [{ label: '新建页面', value: '__new__' }, ...(store.doc?.pages ?? []).map(page => ({ label: `替换：${page.name}（${page.path}）`, value: page.id }))])
const skippedSummary = computed(() => Object.entries(analysis.value?.report.skipped ?? {}).map(([reason, count]) => `${reason} ${count}`).join('；'))
const droppedSummary = computed(() => Object.entries(analysis.value?.report.droppedElements ?? {}).map(([tag, count]) => `${tag} ×${count}`).join('、'))
const nodeCount = computed(() => {
  const count = (nodes: StudioWebImportResult['page']): number => nodes.reduce((sum, node) => sum + 1 + count(node.children), 0)
  const value = analysis.value
  return value ? count(value.page) + count(value.header) + count(value.footer) : 0
})

async function chooseFolder() {
  const picked = await picker.open({ multiple: true, directory: true })
  if (!picked.length) {
    return
  }
  const map = new Map<string, File>()
  for (const file of picked) {
    // webkitRelativePath = 顶层文件夹/子路径；网页根即顶层文件夹
    const relative = (file.webkitRelativePath || file.name).split('/').slice(file.webkitRelativePath ? 1 : 0).join('/')
    map.set(`/${relative}`, file)
  }
  files.value = map
  folderName.value = picked[0]!.webkitRelativePath.split('/')[0] || ''
  htmlPath.value = map.has('/index.html') ? '/index.html' : htmlOptions.value[0]?.value ?? ''
  analysis.value = null
}

/** 被引用的站内路径 → 已有文档资产（资产名记录相对路径，跨页面导入复用） */
function existingAsset(path: string): StudioAsset | undefined {
  return store.doc?.assets.find(asset => asset.name === path.slice(1))
}

async function readSources() {
  const htmlFile = files.value.get(htmlPath.value)
  if (!htmlFile) {
    throw new Error('先选择网页文件')
  }
  const html = await rasterizeInlineSvg(await htmlFile.text())
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const stylesheets: { path: string, text: string }[] = []
  for (const element of doc.querySelectorAll('link[rel~="stylesheet"][href], style')) {
    if (element.tagName === 'STYLE') {
      stylesheets.push({ path: htmlPath.value, text: element.textContent ?? '' })
      continue
    }
    const href = element.getAttribute('href') || ''
    if (/^(?:https?:)?\/\//i.test(href)) {
      continue
    }
    const path = href.startsWith('/') ? href : `${htmlPath.value.replace(/[^/]*$/, '')}${href}`
    const file = files.value.get(path.replace(/[?#].*$/, '').replace(/\/\.\//g, '/'))
    if (file) {
      stylesheets.push({ path, text: await file.text() })
    }
  }
  return { html, stylesheets }
}

function runImport(sources: { html: string, stylesheets: { path: string, text: string }[] }, resolveAsset: (path: string) => string) {
  return importStudioWebPage({
    ...sources,
    regions: { page: form.value.page, header: form.value.header || undefined, footer: form.value.footer || undefined },
    remove: form.value.remove || undefined,
    currentClasses: form.value.currentClasses.split(/[\s,]+/).filter(Boolean),
    resolveAsset,
  })
}

async function analyze() {
  busy.value = true
  try {
    const sources = await readSources()
    const pending = new Set<string>()
    analysis.value = runImport(sources, (path) => {
      const existing = existingAsset(path)
      if (existing) {
        return existing.url
      }
      if (!files.value.has(path)) {
        return ''
      }
      pending.add(path)
      return `/__pending__${path}`
    })
    pendingAssets.value = [...pending]
  }
  catch (error) {
    analysis.value = null
    toast.error(error instanceof Error ? error.message : '网页分析失败')
  }
  finally {
    busy.value = false
  }
}

async function upload(path: string): Promise<StudioAsset> {
  const file = files.value.get(path)!
  const type = file.type || (/\.woff2$/i.test(path) ? 'font/woff2' : /\.woff$/i.test(path) ? 'font/woff' : /\.ogg$/i.test(path) ? 'audio/ogg' : '')
  if (!type || /svg|html|xml|javascript/i.test(type)) {
    throw new Error(`「${path}」类型不受支持`)
  }
  const data = new FormData()
  data.append('file', new File([file], file.name, { type }))
  data.append('module', 'cms')
  data.append('publicAccess', 'true')
  const uploaded = (await apiFiles.upload(data)).data
  const mimeType = uploaded.contentType || type
  return { id: crypto.randomUUID(), name: path.slice(1), url: toBackendAssetUrl(uploaded.url || ''), mimeType, kind: studioKindOf(mimeType, file.name) }
}

async function confirmImport() {
  const doc = store.doc
  if (!doc || !analysis.value) {
    return
  }
  busy.value = true
  try {
    const uploaded: StudioAsset[] = []
    for (const path of pendingAssets.value) {
      uploaded.push(await upload(path))
    }
    const assets = [...doc.assets, ...uploaded]
    const byPath = new Map(assets.map(asset => [`/${asset.name}`, asset]))
    const result = runImport(await readSources(), path => byPath.get(path)?.url ?? '')
    const fonts = result.fonts.flatMap((font) => {
      const asset = assets.find(item => item.url === font.url && item.kind === 'font')
      return asset ? [{ family: font.family, assetId: asset.id, ...(font.weight ? { weight: font.weight } : {}), ...(font.style ? { style: font.style } : {}) }] : []
    })
    const commands: StudioCommand[] = [
      { type: 'set-assets', assets },
      {
        type: 'set-theme',
        tokens: { ...doc.theme.tokens, ...result.tokens },
        classes: mergeStudioClasses(doc.theme.classes ?? [], result.classes),
        keyframes: [...(doc.theme.keyframes ?? []).filter(item => !result.keyframes.some(next => next.name === item.name)), ...result.keyframes],
        fonts: mergeStudioFonts(doc.theme.fonts ?? [], fonts),
        ...(form.value.replaceChrome ? { header: result.header, footer: result.footer } : {}),
      },
    ]
    if (form.value.target === '__new__') {
      const id = crypto.randomUUID()
      commands.push({ type: 'add-page', page: { id, name: form.value.name.trim() || '导入页面', path: form.value.path.trim() || '/imported', root: result.page } })
    }
    else {
      commands.push({ type: 'update-page', pageId: form.value.target, patch: { root: result.page } })
    }
    store.execute(commands)
    toast.success(`网页已导入工作台：${uploaded.length} 个新资源，${result.classes.length} 个样式类（未保存，可撤销）`)
    analysis.value = null
    pendingAssets.value = []
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '网页导入失败')
  }
  finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="flex flex-col gap-3" aria-label="网页导入">
    <h3 class="text-sm font-semibold m-0">
      网页导入
    </h3>
    <p class="text-xs text-muted-foreground m-0">
      选择包含网页与其资源的文件夹，把其中一个 HTML 页面转换为站点页面：网页的 class 样式转为主题样式类（悬停/当前页等状态与断点一并保留），
      图片、字体、音频按相对路径上传为站点资源。脚本、表单、伪元素等无法安全表达的部分会被跳过并在报告中列出。
      动态网页请先保存渲染后的 HTML 再导入。
    </p>
    <div class="flex flex-wrap gap-2 items-center">
      <YdButton v-auth="'platform:cms:edit'" variant="outline" :disabled="!store.doc || busy" @click="chooseFolder">
        <YdIcon name="morph:folder-open" /> 选择网页文件夹
      </YdButton>
      <span v-if="folderName" class="text-xs text-muted-foreground">{{ folderName }} · {{ files.size }} 个文件</span>
    </div>
    <template v-if="files.size">
      <div class="gap-2 grid grid-cols-1 md:grid-cols-2">
        <label class="text-xs flex flex-col gap-1">
          <span>网页文件</span>
          <YdSelect v-model="htmlPath" :options="htmlOptions" aria-label="网页文件" />
        </label>
        <label class="text-xs flex flex-col gap-1">
          <span>导入到</span>
          <YdSelect v-model="form.target" :options="targetOptions" aria-label="导入目标" />
        </label>
        <label v-if="form.target === '__new__'" class="text-xs flex flex-col gap-1">
          <span>页面名称</span>
          <YdInput v-model="form.name" aria-label="页面名称" />
        </label>
        <label v-if="form.target === '__new__'" class="text-xs flex flex-col gap-1">
          <span>页面路径</span>
          <YdInput v-model="form.path" placeholder="/ 或 /about" aria-label="页面路径" />
        </label>
        <label class="text-xs flex flex-col gap-1">
          <span>页面内容选择器</span>
          <YdInput v-model="form.page" placeholder="如 #view 或 body > *" aria-label="页面内容选择器" />
        </label>
        <label class="text-xs flex flex-col gap-1">
          <span>移除元素选择器</span>
          <YdInput v-model="form.remove" aria-label="移除元素选择器" />
        </label>
        <label class="text-xs flex flex-col gap-1">
          <span>页头选择器（可选）</span>
          <YdInput v-model="form.header" placeholder="如 #topbar" aria-label="页头选择器" />
        </label>
        <label class="text-xs flex flex-col gap-1">
          <span>页脚选择器（可选）</span>
          <YdInput v-model="form.footer" placeholder="如 #footer, #hotbar" aria-label="页脚选择器" />
        </label>
        <label class="text-xs flex flex-col gap-1">
          <span>表示“当前页”的类名</span>
          <YdInput v-model="form.currentClasses" aria-label="当前页类名" />
        </label>
        <label class="text-xs flex gap-2 items-center self-end">
          <YdSwitch v-model="form.replaceChrome" aria-label="同时替换页头页脚" />
          <span>同时用导入结果替换站点页头/页脚</span>
        </label>
      </div>
      <div class="flex flex-wrap gap-2">
        <YdButton v-auth="'platform:cms:edit'" variant="outline" :disabled="!htmlPath || busy" @click="analyze">
          <YdIcon name="morph:scan-search" /> 分析
        </YdButton>
        <YdButton v-auth="'platform:cms:edit'" :disabled="!analysis || busy" @click="confirmImport">
          <YdIcon name="morph:download" /> 导入到工作台
        </YdButton>
      </div>
      <YdAlert v-if="analysis">
        <template #description>
          <div class="text-xs flex flex-col gap-1">
            <span>节点 {{ nodeCount }} 个（页面 {{ analysis.page.length }} / 页头 {{ analysis.header.length }} / 页脚 {{ analysis.footer.length }} 个顶层），
              样式类 {{ analysis.report.classes }} 个（其中按选择器生成 {{ analysis.report.generatedClasses }} 个），关键帧 {{ analysis.keyframes.length }}，字体 {{ analysis.fonts.length }}，
              主题变量 {{ Object.keys(analysis.tokens).length }}；待上传资源 {{ pendingAssets.length }} 个。</span>
            <span v-if="skippedSummary">跳过：{{ skippedSummary }}</span>
            <span v-if="droppedSummary">移除元素：{{ droppedSummary }}</span>
            <span v-if="analysis.report.unresolvedAssets.length">文件夹中找不到的资源：{{ analysis.report.unresolvedAssets.slice(0, 8).join('、') }}{{ analysis.report.unresolvedAssets.length > 8 ? ' 等' : '' }}</span>
          </div>
        </template>
      </YdAlert>
    </template>
  </section>
</template>

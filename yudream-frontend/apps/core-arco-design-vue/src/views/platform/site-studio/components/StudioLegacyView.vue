<script setup lang="ts">
// 旧版与迁移视图：旧主题中心/旧 CMS 工作台的真实入口（旧版数据全部保留）；
// 站点便携包与当前页面静态 HTML 导出；便携包导入先预览再显式确认——站点包整体
// 替换工作台文档（不自动保存），主题包仅替换主题（tokens/页头/页脚/样式类/字体/关键帧/站点功能），节点 id
// 整树重生成、资产全部换新 UUID 后按地址去重合并，避免与现有文档任何 id 冲突。
// 旧版 CMS 内容的定向复制迁移由 CmsMigrationPanel 承载：读取当前主题首页/文章，
// 预检后一次 batch 只新增页面副本，不删除旧数据、不保存不发布。
import type { SiteDocument, StudioCommand, StudioNode, StudioPackage } from '@/types/site-studio'
import { computed, ref } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { exportStudioStaticHtml, importStudioPackage, serializeStudioPackage } from '@/utils/site-studio'
import CmsMigrationPanel from './CmsMigrationPanel.vue'
import StudioWebImport from './StudioWebImport.vue'

defineOptions({ name: 'StudioLegacyView' })

const store = useSiteStudioStore()
const toast = useYdToast()
const modal = useYdModal()

const REFERENCE_ATTRS = ['src', 'poster', 'href'] as const

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  // 延迟回收，避免浏览器尚未开始下载时对象 URL 被撤销
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

// ===== 导出 =====

function exportSitePackage() {
  if (!store.doc) {
    return
  }
  try {
    downloadBlob(new Blob([serializeStudioPackage(store.doc, 'site')], { type: 'application/json' }), `site-${store.themeCode}.json`)
    toast.success('站点便携包已导出：内嵌资源随包携带；引用型资源不打包，导入方需保证其地址可访问')
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '站点包导出失败')
  }
}

const exportPageId = ref('')

const pageOptions = computed(() => (store.doc?.pages || []).map(page => ({
  label: `${page.name}（${page.path}）`,
  value: page.id,
})))

function exportPageHtml() {
  const doc = store.doc
  if (!doc || !exportPageId.value) {
    return
  }
  try {
    const page = doc.pages.find(item => item.id === exportPageId.value)
    downloadBlob(new Blob([exportStudioStaticHtml(doc, exportPageId.value)], { type: 'text/html' }), `${page?.name || 'page'}.html`)
    toast.success('静态 HTML 已导出：含页头/页脚与断点样式，不含脚本与外部样式表')
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '静态 HTML 导出失败')
  }
}

// ===== 便携包导入 =====

/** 导入主题包前整树重生成节点 id：同源主题包导回已有页面时绝不与现有 id 重复 */
function freshThemeNode(node: StudioNode): StudioNode {
  const attributes = { ...node.attributes }
  delete attributes['data-studio-id']
  delete attributes.id
  const copy: StudioNode = {
    ...node,
    id: crypto.randomUUID(),
    attributes,
    styles: { ...node.styles },
    children: node.children.map(freshThemeNode),
  }
  if (node.responsive) {
    copy.responsive = { ...node.responsive }
  }
  return copy
}

/** 收集主题区域引用的地址：src/href/poster 属性 + 样式里的 url(...) 背景 */
function collectThemeUrls(nodes: StudioNode[], into: Set<string>) {
  nodes.forEach((node) => {
    for (const key of REFERENCE_ATTRS) {
      const value = node.attributes[key]
      if (value) {
        into.add(value)
      }
    }
    for (const value of Object.values(node.styles)) {
      for (const match of value.matchAll(/url\(([^)]+)\)/g)) {
        const url = match[1]!.trim().replace(/^['"]|['"]$/g, '').trim()
        if (url) {
          into.add(url)
        }
      }
    }
    collectThemeUrls(node.children, into)
  })
}

/** 主题包应用命令：节点 id 已重排；资产全部换新 UUID，只按地址对既有条目去重 */
function importThemeCommands(source: SiteDocument): { commands: StudioCommand[], mergedCount: number } {
  const header = source.theme.header.map(freshThemeNode)
  const footer = source.theme.footer.map(freshThemeNode)
  const used = new Set<string>()
  collectThemeUrls(header, used)
  collectThemeUrls(footer, used)
  const current = store.doc?.assets || []
  const byUrl = new Map(current.map(item => [item.url, item.id]))
  // 旧资产 id → 当前文档中的 id（同地址复用既有条目，其余换新 UUID）
  const idMap = new Map<string, string>()
  const merged = source.assets.flatMap((asset) => {
    const existing = byUrl.get(asset.url)
    if (existing) {
      idMap.set(asset.id, existing)
      return []
    }
    const id = crypto.randomUUID()
    idMap.set(asset.id, id)
    return [{ ...asset, id }]
  })
  const remap = (id?: string) => (id ? idMap.get(id) : undefined)
  const commands: StudioCommand[] = []
  if (merged.length) {
    commands.push({ type: 'set-assets', assets: [...current, ...merged] })
  }
  commands.push({
    type: 'set-theme',
    tokens: source.theme.tokens,
    header,
    footer,
    classes: source.theme.classes ?? [],
    keyframes: source.theme.keyframes ?? [],
    fonts: (source.theme.fonts ?? []).flatMap(font => remap(font.assetId) ? [{ ...font, assetId: remap(font.assetId)! }] : []),
  })
  const audio = source.features?.audio
  if (source.features) {
    commands.push({
      type: 'set-features',
      features: {
        ...(source.features.particles ? { particles: source.features.particles } : {}),
        ...(audio
          ? {
              audio: {
                ...audio,
                tracks: audio.tracks.flatMap(track => remap(track.assetId) ? [{ ...track, assetId: remap(track.assetId)! }] : []),
                clickSoundAssetId: remap(audio.clickSoundAssetId),
                buttonSoundAssetId: remap(audio.buttonSoundAssetId),
                toastSoundAssetId: remap(audio.toastSoundAssetId),
              },
            }
          : {}),
      },
    })
  }
  return { commands, mergedCount: merged.length }
}

function captureContext() {
  const doc = store.doc
  return doc ? { themeCode: store.themeCode, documentId: doc.id } : null
}

function applyImport(pkg: StudioPackage) {
  try {
    if (pkg.kind === 'site') {
      store.loadDocumentLocally(pkg.document)
      toast.success('站点包已导入工作台（仅本地草稿，需手动保存）')
      return
    }
    if (!store.doc) {
      toast.error('先在「概览」创建站点文档，再导入主题包')
      return
    }
    const { commands, mergedCount } = importThemeCommands(pkg.document)
    store.execute(commands)
    toast.success(`主题包已应用${mergedCount ? `；合并 ${mergedCount} 项资源（id 已全部重排）` : ''}`)
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '站点包导入失败')
  }
}

async function importPackage() {
  // 打开文件对话框前捕获上下文，确认时复验：切主题/换文档后放弃，不误覆盖
  const captured = captureContext()
  try {
    const picker = useYdFilePicker()
    const files = await picker.open({ accept: 'application/json,.json', multiple: false })
    const file = files[0]
    if (!file) {
      return
    }
    const pkg = importStudioPackage(await file.text())
    const external = pkg.assets.filter(asset => asset.mode === 'reference').length
    modal.confirm({
      title: `导入${pkg.kind === 'theme' ? '主题' : '站点'}包？`,
      content: `「${pkg.document.name}」：${pkg.document.pages.length} 个页面，外部资产引用 ${external} 项（不随包携带，需保证这些地址在导入后仍可访问；内嵌资源已打进包内）。${pkg.kind === 'theme'
        ? '主题包只替换当前工作台的主题（tokens/页头/页脚），被主题引用且未登记的资源会合并进资产库。'
        : '站点包整体替换当前工作台文档；只进入工作台，不会自动保存。'}`,
      confirmButtonText: '导入',
      onConfirm: () => {
        if (captured && (store.themeCode !== captured.themeCode || store.doc?.id !== captured.documentId)) {
          toast.error('站点上下文已切换，本次导入已放弃')
          return
        }
        applyImport(pkg)
      },
    })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '站点包导入失败')
  }
}
</script>

<template>
  <div class="h-full min-h-0 overflow-auto">
    <div class="mx-auto p-4 flex flex-col gap-5 max-w-3xl sm:p-6">
      <YdAlert>
        <template #description>
          旧版主题中心与旧 CMS 工作台完整保留。旧版 CMS 内容支持定向复制迁移：读取当前主题的首页与文章，预检通过后一次性把所选页面副本新增到工作台（不删除旧数据、不自动保存/发布）；便携包导入导出继续可用。
        </template>
      </YdAlert>

      <CmsMigrationPanel />

      <section class="flex flex-col gap-2" aria-label="旧版入口">
        <h2 class="text-sm font-semibold m-0">
          旧版入口
        </h2>
        <div class="gap-2 grid sm:grid-cols-2">
          <RouterLink :to="{ name: 'themeCenterLegacy' }" class="studio-nav-card" aria-label="打开旧版主题中心">
            <YdIcon name="morph:archive" class="text-lg" />
            <span class="flex flex-col min-w-0">
              <strong>旧版主题中心</strong>
              <span class="text-xs text-muted-foreground truncate">/platform/theme-center/legacy</span>
            </span>
          </RouterLink>
          <RouterLink to="/platform/cms/workspace" class="studio-nav-card" aria-label="打开旧版 CMS 工作台">
            <YdIcon name="morph:layout-template" class="text-lg" />
            <span class="flex flex-col min-w-0">
              <strong>旧版 CMS 工作台</strong>
              <span class="text-xs text-muted-foreground truncate">/platform/cms/workspace</span>
            </span>
          </RouterLink>
        </div>
      </section>

      <section class="flex flex-col gap-2" aria-label="导出">
        <h2 class="text-sm font-semibold m-0">
          导出
        </h2>
        <p class="text-xs text-muted-foreground m-0">
          站点便携包为 kind=site 的 JSON（内嵌资源随包；引用型资源不打包，不伪装全离线）；静态 HTML 仅当前页面，含页头/页脚与断点样式。
        </p>
        <div class="flex flex-wrap gap-2 items-center">
          <YdButton variant="outline" :disabled="!store.doc" @click="exportSitePackage">
            <YdIcon name="morph:download" /> 导出站点便携包
          </YdButton>
          <YdSelect
            v-model="exportPageId"
            :options="pageOptions"
            placeholder="选择要导出的页面"
            class="w-56"
            aria-label="选择导出页面"
          />
          <YdButton variant="outline" :disabled="!store.doc || !exportPageId" @click="exportPageHtml">
            <YdIcon name="morph:file-code" /> 导出页面静态 HTML
          </YdButton>
        </div>
      </section>

      <StudioWebImport />

      <section class="flex flex-col gap-2" aria-label="站点包导入">
        <h2 class="text-sm font-semibold m-0">
          站点包导入
        </h2>
        <p class="text-xs text-muted-foreground m-0">
          选择便携包 JSON，先展示名称/页数/外部资产引用，显式确认后才进工作台；站点包整体替换、主题包仅替换主题，都不自动保存。
        </p>
        <div>
          <YdButton v-auth="'platform:cms:edit'" variant="outline" @click="importPackage">
            <YdIcon name="morph:upload" /> 导入站点包
          </YdButton>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.studio-nav-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid oklch(var(--border));
  border-radius: var(--yd-control-radius, 10px);
  background: oklch(var(--card));
  color: inherit;
  text-decoration: none;
}
.studio-nav-card:hover {
  background: oklch(var(--muted) / 0.5);
}
</style>

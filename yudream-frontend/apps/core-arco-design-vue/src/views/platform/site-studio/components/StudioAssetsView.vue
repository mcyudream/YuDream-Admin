<script setup lang="ts">
// 资源视图：上传（走既有文件服务 apiFiles.upload，module=cms、公开访问）或内嵌小
// 资源（data URL），登记进站点文档资产库（set-assets 统一命令，整体替换必须传全
// 量数组），并可把图片/视频/音频插入当前页面或选中容器。
//
// 与 studio-upload helper 的差异（按协调约定在视图内处理）：
// - 先验后传：选择文件后立刻预检 MIME（禁止 SVG/HTML/XML 等活动内容、拒绝无类型
//   文件），不合格的文件不会发请求，用户不会在上传之后才被告知不支持；
// - kind 由 MIME 前缀推导（image/ video/ audio/ font/），不做后缀猜测，避免
//   kind 与 MIME 不符被模型校验拒绝；
// - 登记前按服务端 id 与地址对既有资产去重，同一文件/同一地址不会重复登记。
//
// 所有异步动作先捕获「主题码 + 文档 id + 本地版本号」：回来后主题或文档已切换的
// 结果一律丢弃，绝不写错上下文；删除被引用的资源会先明确提示，不默默破图。
import type { StudioAsset, StudioNode } from '@/types/site-studio'
import { computed, ref } from 'vue'
import apiFiles from '@/api/modules/files'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { createStudioNode, findStudioNode } from '@/utils/site-studio'
import { isStudioEmbeddedAsset, STUDIO_LIMITS, validateStudioUrl } from '@/utils/site-studio-validation'

defineOptions({ name: 'StudioAssetsView' })

const emit = defineEmits<{ openDesign: [] }>()

const store = useSiteStudioStore()
const toast = useYdToast()
const modal = useYdModal()

const VOID_TAGS = new Set(['br', 'col', 'hr', 'img', 'source', 'track', 'wbr'])
const MEDIA_ACCEPT = 'image/*,video/*,audio/*'
/** 站点媒体禁止的活动内容类型（模型校验同样会拒绝，这里前置拦截） */
const FORBIDDEN_MIME = /svg|html|xml|javascript|script/i

function strictKindOf(mimeType: string): StudioAsset['kind'] {
  if (mimeType.startsWith('image/')) {
    return 'image'
  }
  if (mimeType.startsWith('video/')) {
    return 'video'
  }
  if (mimeType.startsWith('audio/')) {
    return 'audio'
  }
  if (mimeType.startsWith('font/')) {
    return 'font'
  }
  return 'file'
}

// ===== 上下文捕获 =====

interface ContextCapture {
  themeCode: string
  documentId: string
  revision: string
}

function captureContext(): ContextCapture | null {
  const doc = store.doc
  if (!doc) {
    return null
  }
  return { themeCode: store.themeCode, documentId: doc.id, revision: store.editRevision }
}

/** 主题或文档已切换（换上下文）→ 丢弃；同文档内版本号变了（期间发生过编辑）→ 仍可用 */
function contextAlive(captured: ContextCapture | null): boolean {
  return Boolean(captured) && store.themeCode === captured!.themeCode && store.doc?.id === captured!.documentId
}

// ===== 资产登记 =====

/** 按既有资产的 id 与地址去重后统一登记（set-assets 是整体替换） */
function registerAssets(incoming: StudioAsset[]) {
  const current = store.doc?.assets || []
  const seenIds = new Set(current.map(item => item.id))
  const seenUrls = new Set(current.map(item => item.url))
  const fresh = incoming.filter(asset => !seenIds.has(asset.id) && !seenUrls.has(asset.url))
  if (!fresh.length) {
    toast.success('所选资源此前已登记过，未重复添加')
    return
  }
  try {
    store.execute({ type: 'set-assets', assets: [...current, ...fresh] })
    toast.success(fresh.length === incoming.length
      ? `已登记 ${fresh.length} 项资源`
      : `已登记 ${fresh.length} 项资源（其余与既有条目重复，已按 id/地址去重）`)
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '资产登记失败')
  }
}

// ===== 上传到文件服务 =====

const uploading = ref(false)

async function uploadAssets() {
  const captured = captureContext()
  if (!captured) {
    return
  }
  uploading.value = true
  try {
    const picker = useYdFilePicker()
    const files = await picker.open({ accept: MEDIA_ACCEPT, multiple: true })
    if (!files.length) {
      return
    }
    // 上传前预检：MIME 不合格的不发请求，避免上传完才告诉用户不支持
    const accepted: { file: File, mimeType: string }[] = []
    for (const file of files) {
      const mimeType = (file.type || '').toLowerCase()
      if (!mimeType) {
        toast.error(`「${file.name}」未携带可识别的类型，已跳过`)
        continue
      }
      if (FORBIDDEN_MIME.test(mimeType)) {
        toast.error(`「${file.name}」类型为 ${mimeType}，站点媒体禁止 SVG/HTML 等活动内容，已跳过`)
        continue
      }
      accepted.push({ file, mimeType })
    }
    if (!accepted.length) {
      return
    }
    const uploaded: StudioAsset[] = []
    const failed: string[] = []
    for (const { file, mimeType } of accepted) {
      const data = new FormData()
      data.append('file', file)
      data.append('module', 'cms')
      data.append('publicAccess', 'true')
      // 逐个上传：单个失败（原因已由全局请求提示说明）不丢弃其余已上传的文件
      let response: Awaited<ReturnType<typeof apiFiles.upload>>
      try {
        response = await apiFiles.upload(data)
      }
      catch {
        failed.push(file.name)
        continue
      }
      const result = response.data
      const url = result.url || ''
      if (!url) {
        toast.error(`上传「${file.name}」后接口未返回可用的媒体地址`)
        continue
      }
      uploaded.push({
        id: result.id,
        name: result.originalName || file.name,
        url: toBackendAssetUrl(url),
        mimeType: result.contentType || mimeType,
        kind: strictKindOf(result.contentType || mimeType),
      })
    }
    if (failed.length) {
      toast.error(`${failed.map(name => `「${name}」`).join('、')}上传失败${uploaded.length ? `，其余 ${uploaded.length} 项已登记` : ''}`)
    }
    if (!uploaded.length) {
      return
    }
    if (!contextAlive(captured)) {
      toast.error('上下文已切换，本次上传结果已丢弃')
      return
    }
    registerAssets(uploaded)
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '资源上传失败')
  }
  finally {
    uploading.value = false
  }
}

// ===== 内嵌小资源（data URL，受 2 MiB 总文档限制） =====

const EMBED_ACCEPT = 'image/png,image/jpeg,image/gif,image/webp,image/avif,audio/mpeg,audio/ogg,audio/wav,audio/webm,video/mp4,video/webm,video/ogg,font/woff,font/woff2,font/ttf,font/otf'

function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error('读取文件失败'))
    reader.readAsDataURL(file)
  })
}

async function embedAssets() {
  const captured = captureContext()
  if (!captured) {
    return
  }
  try {
    const picker = useYdFilePicker()
    const files = await picker.open({ accept: EMBED_ACCEPT, multiple: true })
    if (!files.length) {
      return
    }
    const prepared: StudioAsset[] = []
    for (const file of files) {
      const dataUrl = await readAsDataUrl(file)
      const match = /^data:([^;,]+);base64,/.exec(dataUrl)
      const mimeType = (match?.[1] || file.type || '').toLowerCase()
      if (!mimeType || FORBIDDEN_MIME.test(mimeType)) {
        toast.error(`「${file.name}」不允许内嵌（禁止 SVG/HTML 等活动内容，且需可识别类型）`)
        continue
      }
      const asset: StudioAsset = {
        id: crypto.randomUUID(),
        name: file.name,
        url: dataUrl,
        mimeType,
        kind: strictKindOf(mimeType),
      }
      validateStudioUrl(asset.url, true)
      prepared.push(asset)
    }
    if (!prepared.length) {
      return
    }
    if (!contextAlive(captured)) {
      toast.error('上下文已切换，本次内嵌结果已丢弃')
      return
    }
    // 先做文档预算预检，超出 2 MiB 时整体拒绝，不挤占既有内容
    const nextAssets = [...(store.doc?.assets || []), ...prepared]
    const projected = new TextEncoder()
      .encode(JSON.stringify({ ...(store.doc as NonNullable<typeof store.doc>), assets: nextAssets }))
      .length
    if (projected > STUDIO_LIMITS.bytes) {
      toast.error(`内嵌后文档约 ${(projected / 1024 / 1024).toFixed(2)} MiB，将超过 2 MiB 限制：请减少文件或改用「上传资源」`)
      return
    }
    registerAssets(prepared)
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '资源内嵌失败')
  }
}

// ===== 资产列表 =====

const assets = computed<StudioAsset[]>(() => store.doc?.assets || [])

const kindLabels: Record<StudioAsset['kind'], string> = {
  image: '图片',
  video: '视频',
  audio: '音频',
  font: '字体',
  file: '文件',
}

const kindIcons: Partial<Record<StudioAsset['kind'], string>> = {
  video: 'morph:video',
  audio: 'morph:music',
  font: 'morph:type',
  file: 'morph:file',
}

const REFERENCE_ATTRS = ['src', 'poster', 'href'] as const

function countAssetReferences(url: string): number {
  let count = 0
  const visit = (nodes: StudioNode[]) => nodes.forEach((node) => {
    for (const key of REFERENCE_ATTRS) {
      if (node.attributes[key] === url) {
        count++
      }
    }
    visit(node.children)
  })
  const doc = store.doc
  if (doc) {
    doc.pages.forEach(page => visit(page.root))
    visit(doc.theme.header)
    visit(doc.theme.footer)
  }
  return count
}

function removeAsset(asset: StudioAsset) {
  const references = countAssetReferences(asset.url)
  modal.confirm({
    title: references > 0 ? '删除正在使用的资源？' : '删除资源',
    content: references > 0
      ? `「${asset.name}」被站点内容引用了 ${references} 处（src/href/poster）。删除后这些位置会失去资源、出现破图；建议先在设计里移除或替换引用，或确认接受后再删除。`
      : `「${asset.name}」当前没有被引用，可安全删除；删除只改当前草稿，未保存前可撤销。`,
    confirmButtonText: references > 0 ? '仍要删除' : '删除',
    cancelButtonText: '取消',
    onConfirm: () => {
      try {
        store.execute({ type: 'set-assets', assets: assets.value.filter(item => item.id !== asset.id) })
        toast.success('资源已删除')
      }
      catch (error) {
        toast.error(error instanceof Error ? error.message : '资源删除失败')
      }
    },
  })
}

// ===== 插入到页面 / 选中容器 =====

function collectionOf(doc: NonNullable<typeof store.doc>, nodeId: string): { pageId?: string, region?: 'header' | 'footer' } | null {
  const contains = (nodes: StudioNode[]): boolean => {
    let found = false
    const visit = (list: StudioNode[]) => list.forEach((item) => {
      if (item.id === nodeId) {
        found = true
      }
      visit(item.children)
    })
    visit(nodes)
    return found
  }
  for (const page of doc.pages) {
    if (contains(page.root)) {
      return { pageId: page.id }
    }
  }
  if (contains(doc.theme.header)) {
    return { region: 'header' }
  }
  if (contains(doc.theme.footer)) {
    return { region: 'footer' }
  }
  return null
}

function mediaNode(asset: StudioAsset): StudioNode | null {
  if (asset.kind === 'image') {
    const node = createStudioNode('image')
    node.attributes.src = asset.url
    node.attributes.alt = asset.name
    node.styles.width = '100%'
    return node
  }
  if (asset.kind === 'video') {
    const node = createStudioNode('video')
    node.attributes.src = asset.url
    node.attributes.controls = ''
    // iOS 需要 playsinline 才能在页内播放（否则强制全屏）；只预取元数据，不预载整段视频
    node.attributes.playsinline = ''
    node.attributes.preload = 'metadata'
    node.styles.width = '100%'
    return node
  }
  if (asset.kind === 'audio') {
    const node = createStudioNode('audio')
    node.attributes.src = asset.url
    node.attributes.controls = ''
    node.styles.width = '100%'
    return node
  }
  return null
}

const insertable = (asset: StudioAsset) => ['image', 'video', 'audio'].includes(asset.kind)

function insertAsset(asset: StudioAsset) {
  const doc = store.doc
  const node = doc ? mediaNode(asset) : null
  if (!doc || !node) {
    return
  }
  try {
    const selected = store.selectedNodeId ? findStudioNode(doc, store.selectedNodeId) : undefined
    const parentUsable = Boolean(selected) && !VOID_TAGS.has(selected!.tag) && selected!.text === undefined
    const target = parentUsable && selected
      ? { parentId: selected.id }
      : store.editRegion === 'page'
        ? { pageId: store.activePageId }
        : { region: store.editRegion }
    store.execute({ type: 'insert', target, node })
    // 落点与设计视图对齐：选中容器 → 该容器所在页面/主题区域；否则沿用当前编辑目标
    const landed = parentUsable && selected
      ? collectionOf(doc, selected.id)
      : (store.editRegion === 'page' ? { pageId: store.activePageId } : { region: store.editRegion })
    if (landed?.pageId) {
      store.activePageId = landed.pageId
      store.editRegion = 'page'
    }
    else if (landed?.region) {
      store.editRegion = landed.region
    }
    store.selectedNodeId = node.id
    emit('openDesign')
    toast.success(`已把「${asset.name}」插入页面`)
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '插入失败')
  }
}
</script>

<template>
  <div class="h-full min-h-0 overflow-auto">
    <div class="mx-auto p-4 flex flex-col gap-4 max-w-4xl sm:p-6">
      <div class="flex flex-wrap gap-2 items-center">
        <div class="flex-1 min-w-0">
          <h2 class="text-sm font-semibold m-0">
            媒体资源（{{ assets.length }}）
          </h2>
          <p class="text-xs text-muted-foreground m-0">
            上传走既有文件服务（module=cms、公开访问）并登记进站点文档；内嵌资源以 data URL 存进文档，受 2 MiB 总文档限制。
          </p>
        </div>
        <YdButton v-auth="'platform:cms:edit'" :loading="uploading" :disabled="!store.doc" @click="uploadAssets">
          <YdIcon name="morph:cloud-upload" /> 上传资源
        </YdButton>
        <YdButton v-auth="'platform:cms:edit'" variant="outline" :disabled="!store.doc" @click="embedAssets">
          <YdIcon name="morph:paperclip" /> 内嵌小资源
        </YdButton>
      </div>

      <YdEmpty v-if="!assets.length" description="还没有资源。上传图片/视频/音频登记到资产库，或把小文件内嵌进站点文档。">
        <template #action>
          <YdButton v-auth="'platform:cms:edit'" variant="outline" :disabled="!store.doc" @click="uploadAssets">
            <YdIcon name="morph:cloud-upload" /> 上传第一个资源
          </YdButton>
        </template>
      </YdEmpty>

      <div v-else class="gap-2 grid sm:grid-cols-2">
        <article v-for="asset in assets" :key="asset.id" class="studio-asset-card">
          <span class="studio-asset-thumb">
            <img
              v-if="asset.kind === 'image'"
              :src="toBackendAssetUrl(asset.url)"
              :alt="asset.name"
              loading="lazy"
              class="size-full object-cover"
            >
            <YdIcon v-else :name="kindIcons[asset.kind] || 'morph:file'" class="text-xl text-muted-foreground" />
          </span>
          <div class="flex flex-1 flex-col gap-1 min-w-0">
            <div class="flex gap-1.5 min-w-0 items-center">
              <strong class="truncate">{{ asset.name }}</strong>
              <span class="yd-status-tone shrink-0" data-tone="neutral">{{ kindLabels[asset.kind] }}</span>
            </div>
            <span class="text-xs text-muted-foreground font-mono truncate">{{ asset.mimeType }}</span>
            <span class="text-xs text-muted-foreground font-mono truncate">
              {{ isStudioEmbeddedAsset(asset) ? '内嵌 data URL' : asset.url }}
            </span>
          </div>
          <div class="flex shrink-0 flex-col gap-1">
            <YdButton
              v-if="insertable(asset)"
              v-auth="'platform:cms:edit'"
              variant="ghost"
              size="sm"
              :aria-label="`插入${asset.name}`"
              @click="insertAsset(asset)"
            >
              <YdIcon name="morph:arrow-down-to-line" /> 插入
            </YdButton>
            <YdTooltip v-else content="仅图片/视频/音频可直接插入；文件类资源可在设计里把它的地址填入链接引用">
              <YdButton variant="ghost" size="sm" disabled aria-label="该类型不支持直接插入">
                <YdIcon name="morph:arrow-down-to-line" /> 插入
              </YdButton>
            </YdTooltip>
            <div class="flex gap-1 items-center">
              <YdCopyButton :text="asset.url" label="复制地址" />
              <YdButton
                v-auth="'platform:cms:edit'"
                variant="ghost"
                size="sm"
                :aria-label="`删除${asset.name}`"
                @click="removeAsset(asset)"
              >
                <YdIcon name="morph:trash-2" />
              </YdButton>
            </div>
          </div>
        </article>
      </div>

      <YdAlert icon="morph:info">
        <template #description>
          删除资源前会检查站点内容里的引用（src/href/poster）：被引用的资源会先明确提示引用数量，不会默默造成破图。
        </template>
      </YdAlert>
    </div>
  </div>
</template>

<style scoped>
.studio-asset-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid oklch(var(--border));
  border-radius: var(--yd-control-radius, 10px);
  background: oklch(var(--card));
}
.studio-asset-thumb {
  display: flex;
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid oklch(var(--border));
  border-radius: calc(var(--yd-control-radius, 10px) * 0.75);
  background: oklch(var(--muted) / 0.35);
}
</style>

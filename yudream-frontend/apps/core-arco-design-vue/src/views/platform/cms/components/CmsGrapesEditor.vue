<script setup lang="ts">
import type grapesjs from 'grapesjs'
import type { Editor } from 'grapesjs'
import type { FileObject } from '@/api/modules/files'
import apiFiles from '@/api/modules/files'
import { toBackendAssetUrl } from '@/utils/backend-url'
import 'grapesjs/dist/css/grapes.min.css'

interface GrapesSavePayload {
  htmlContent: string
  cssContent: string
  builderProjectJson: string
}

const props = defineProps<{
  htmlContent?: string
  cssContent?: string
  builderProjectJson?: string
  title?: string
}>()

const emit = defineEmits<{
  close: []
  save: [payload: GrapesSavePayload]
}>()

const toast = useFaToast()
const editorEl = ref<HTMLElement>()
const blocksEl = ref<HTMLElement>()
const layersEl = ref<HTMLElement>()
const traitsEl = ref<HTMLElement>()
const stylesEl = ref<HTMLElement>()
const mediaInput = ref<HTMLInputElement>()
const mediaItems = ref<FileObject[]>([])
const loadingMedia = ref(false)
let editor: Editor | null = null

onMounted(async () => {
  const { default: grapes } = await import('grapesjs')
  editor = grapes.init({
    container: editorEl.value!,
    height: '100%',
    width: 'auto',
    fromElement: false,
    storageManager: false,
    undoManager: { trackSelection: false },
    selectorManager: { componentFirst: true },
    blockManager: { appendTo: blocksEl.value! },
    layerManager: { appendTo: layersEl.value! },
    traitManager: { appendTo: traitsEl.value! },
    styleManager: {
      appendTo: stylesEl.value!,
      sectors: styleSectors(),
    },
    deviceManager: {
      devices: [
        { id: 'desktop', name: '桌面', width: '' },
        { id: 'tablet', name: '平板', width: '768px' },
        { id: 'mobile', name: '手机', width: '390px' },
      ],
    },
    canvas: {
      styles: [],
    },
    panels: { defaults: [] },
  })
  registerDynamicTypes(editor)
  registerBlocks(editor)
  loadInitialContent(editor)
  await loadMedia()
})

onBeforeUnmount(() => {
  editor?.destroy()
  editor = null
})

function loadInitialContent(instance: Editor) {
  if (props.builderProjectJson) {
    try {
      instance.loadProjectData(JSON.parse(props.builderProjectJson))
      return
    }
    catch {
      toast.warning('构建器源数据解析失败，已回退 HTML 内容')
    }
  }
  instance.setComponents(props.htmlContent || starterHtml())
  if (props.cssContent) {
    instance.setStyle(props.cssContent)
  }
}

function save() {
  if (!editor) {
    return
  }
  emit('save', {
    htmlContent: editor.getHtml(),
    cssContent: editor.getCss(),
    builderProjectJson: JSON.stringify(editor.getProjectData()),
  })
}

function command(command: string) {
  editor?.runCommand(command)
}

function setDevice(device: 'desktop' | 'tablet' | 'mobile') {
  editor?.setDevice(device)
}

async function loadMedia() {
  loadingMedia.value = true
  try {
    const res = await apiFiles.page({ page: 1, size: 48, module: 'cms', publicAccess: true })
    mediaItems.value = res.data.records
  }
  finally {
    loadingMedia.value = false
  }
}

function pickMedia() {
  mediaInput.value?.click()
}

async function uploadMedia(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    return
  }
  const data = new FormData()
  data.append('file', file)
  data.append('module', 'cms')
  data.append('publicAccess', 'true')
  await apiFiles.upload(data)
  toast.success('媒体已上传')
  await loadMedia()
}

function insertImage(item: FileObject) {
  if (!editor) {
    return
  }
  const url = toBackendAssetUrl(item.url)
  editor.addComponents(`<img src="${escapeAttr(url)}" alt="${escapeAttr(item.originalName || 'CMS 图片')}" style="max-width:100%; border-radius:12px;">`)
}

function registerDynamicTypes(instance: Editor) {
  instance.DomComponents.addType('yb-repeat', {
    isComponent: el => el.hasAttribute?.('data-yb-repeat'),
    model: {
      defaults: {
        traits: [
          {
            type: 'select',
            name: 'data-yb-repeat',
            label: '动态数据',
            options: [
              { id: 'pages', name: '公开页面' },
              { id: 'categories', name: '分类' },
              { id: 'tags', name: '标签' },
              { id: 'navigation', name: '导航' },
              { id: 'navUsers', name: '头像用户' },
            ],
          },
        ],
      },
    },
  })
  instance.DomComponents.addType('yb-visible', {
    isComponent: el => el.hasAttribute?.('data-visible-when'),
    model: {
      defaults: {
        traits: [
          {
            type: 'select',
            name: 'data-visible-when',
            label: '可见条件',
            options: [
              { id: 'guest', name: '游客' },
              { id: 'logged-in', name: '已登录' },
            ],
          },
        ],
      },
    },
  })
}

function registerBlocks(instance: Editor) {
  blocks().forEach(block => instance.BlockManager.add(block.id, block))
}

function blocks(): grapesjs.BlockProperties[] {
  return [
    {
      id: 'yb-hero',
      label: '首页首屏',
      category: '首页',
      media: '<span class="gjs-block-icon">H</span>',
      content: `<section style="padding:88px 48px; border-radius:24px; background:linear-gradient(135deg,#0f172a,#0f766e); color:#fff;">
  <p style="margin:0 0 18px; color:rgba(255,255,255,.72); font-weight:700;">{{site.name}} · {{auth.welcome}}</p>
  <h1 style="max-width:820px; margin:0; font-size:64px; line-height:1;">把内容、数据和业务能力组合成真正可发布的网站</h1>
  <p style="max-width:680px; margin:22px 0 0; color:rgba(255,255,255,.82); font-size:18px;">使用 GrapesJS、媒体库和动态变量搭建页面。</p>
</section>`,
    },
    {
      id: 'yb-post-list',
      label: '动态文章列表',
      category: '内容',
      media: '<span class="gjs-block-icon">P</span>',
      content: `<section style="padding:56px 0;">
  <h2 style="margin:0 0 20px; font-size:40px;">最新发布 · {{pages.count}}</h2>
  <div data-yb-repeat="pages" style="display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:18px;">
    <article style="display:grid; gap:12px; padding:18px; border:1px solid #e5e7eb; border-radius:18px; background:#fff;">
      <img src="{{item.coverImageUrl}}" alt="{{item.title}}" style="width:100%; aspect-ratio:16/10; object-fit:cover; border-radius:14px; background:#e2e8f0;">
      <small style="color:#0f766e; font-weight:800;">{{item.category}} · {{item.publishedAt}}</small>
      <h3 style="margin:0; font-size:22px;">{{item.title}}</h3>
      <p style="margin:0; color:#64748b;">{{item.excerpt}}</p>
      <a href="{{item.url}}" style="color:#0f766e; font-weight:800;">阅读全文</a>
    </article>
  </div>
</section>`,
    },
    {
      id: 'yb-taxonomy',
      label: '分类标签云',
      category: '内容',
      media: '<span class="gjs-block-icon">#</span>',
      content: `<section style="display:grid; grid-template-columns:1fr 1fr; gap:20px; padding:42px 0;">
  <div style="padding:22px; border:1px solid #e5e7eb; border-radius:18px; background:#fff;">
    <h2>分类</h2>
    <div data-yb-repeat="categories" style="display:flex; gap:8px; flex-wrap:wrap;"><a href="{{item.url}}" style="padding:9px 12px; border-radius:999px; background:#dcfce7; color:#166534;">{{item.label}} · {{item.count}}</a></div>
  </div>
  <div style="padding:22px; border:1px solid #e5e7eb; border-radius:18px; background:#fff;">
    <h2>标签</h2>
    <div data-yb-repeat="tags" style="display:flex; gap:8px; flex-wrap:wrap;"><a href="{{item.url}}" style="padding:9px 12px; border-radius:999px; background:#e0f2fe; color:#075985;">#{{item.label}} · {{item.count}}</a></div>
  </div>
</section>`,
    },
    {
      id: 'yb-navigation',
      label: '导航菜单',
      category: '动态',
      media: '<span class="gjs-block-icon">N</span>',
      content: `<nav style="display:flex; justify-content:space-between; gap:18px; padding:16px 22px; border:1px solid #e5e7eb; border-radius:18px; background:#fff;">
  <a href="/site" style="font-size:20px; font-weight:900; color:#0f172a;">{{site.name}}</a>
  <div data-yb-repeat="navigation" style="display:flex; gap:8px; flex-wrap:wrap;"><a href="{{item.url}}" style="padding:9px 12px; border-radius:999px; color:#475569;">{{item.label}}</a></div>
</nav>`,
    },
    {
      id: 'yb-auth',
      label: '登录态入口',
      category: '动态',
      media: '<span class="gjs-block-icon">A</span>',
      content: `<section style="padding:34px; border-radius:20px; background:#0f172a; color:#fff;">
  <div data-visible-when="guest"><h2>访问更多内容</h2><p>登录后可进入个人中心并查看专属导航。</p><a href="/login" style="color:#fff; font-weight:800;">登录</a></div>
  <div data-visible-when="logged-in"><h2>{{auth.welcome}}</h2><p>账号：{{user.username}}</p><img src="{{user.avatar}}" alt="{{user.nickname}}" style="width:56px; height:56px; border-radius:50%; object-fit:cover;"></div>
</section>`,
    },
    {
      id: 'yb-gallery',
      label: '媒体画廊',
      category: '媒体',
      media: '<span class="gjs-block-icon">M</span>',
      content: `<section style="padding:54px 0;">
  <h2 style="margin:0 0 18px; font-size:38px;">媒体画廊</h2>
  <div style="display:grid; grid-template-columns:1.25fr .75fr; gap:14px;">
    <img src="{{site.logo}}" alt="主图" style="width:100%; height:420px; object-fit:cover; border-radius:18px; background:#e2e8f0;">
    <div style="display:grid; gap:14px;"><img src="{{user.avatar}}" alt="图片" style="width:100%; height:203px; object-fit:cover; border-radius:18px; background:#e2e8f0;"><div style="display:grid; place-items:center; height:203px; border:1px dashed #cbd5e1; border-radius:18px; color:#64748b;">替换为媒体库图片</div></div>
  </div>
</section>`,
    },
  ]
}

function styleSectors() {
  return [
    {
      name: '布局',
      open: true,
      properties: ['display', 'position', 'top', 'right', 'bottom', 'left', 'width', 'height', 'margin', 'padding'],
    },
    {
      name: '文字',
      open: false,
      properties: ['font-family', 'font-size', 'font-weight', 'color', 'line-height', 'text-align'],
    },
    {
      name: '外观',
      open: false,
      properties: ['background-color', 'border', 'border-radius', 'box-shadow', 'opacity'],
    },
  ]
}

function starterHtml() {
  return `<main style="padding:48px; min-height:100vh; background:#f8fafc; color:#0f172a;">
  <section style="padding:72px 48px; border-radius:24px; background:#fff; border:1px solid #e5e7eb;">
    <h1 style="margin:0; font-size:56px;">${escapeHtml(props.title || '新页面')}</h1>
    <p style="margin:18px 0 0; color:#64748b;">从左侧拖入区块，使用右侧面板调整样式和动态属性。</p>
  </section>
</main>`
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeAttr(value: string) {
  return escapeHtml(value)
}
</script>

<template>
  <div class="grapes-shell">
    <header class="grapes-header">
      <div>
        <strong>{{ title || 'CMS 构建器' }}</strong>
        <span>GrapesJS 可视化编辑</span>
      </div>
      <div class="grapes-header__actions">
        <FaButton variant="outline" size="sm" @click="command('core:undo')">
          <FaIcon name="i-ri:arrow-go-back-line" />
        </FaButton>
        <FaButton variant="outline" size="sm" @click="command('core:redo')">
          <FaIcon name="i-ri:arrow-go-forward-line" />
        </FaButton>
        <FaButton variant="outline" size="sm" @click="setDevice('desktop')">桌面</FaButton>
        <FaButton variant="outline" size="sm" @click="setDevice('tablet')">平板</FaButton>
        <FaButton variant="outline" size="sm" @click="setDevice('mobile')">手机</FaButton>
        <FaButton size="sm" @click="save">
          <FaIcon name="i-ri:save-3-line" />
          保存
        </FaButton>
        <FaButton variant="outline" size="sm" @click="emit('close')">
          <FaIcon name="i-ri:close-line" />
        </FaButton>
      </div>
    </header>

    <div class="grapes-body">
      <aside class="grapes-sidebar left">
        <section>
          <h3>区块</h3>
          <div ref="blocksEl" />
        </section>
        <section>
          <div class="media-head">
            <h3>媒体</h3>
            <FaButton size="sm" variant="outline" :loading="loadingMedia" @click="loadMedia">
              <FaIcon name="i-ri:refresh-line" />
            </FaButton>
          </div>
          <div class="media-actions">
            <FaButton size="sm" @click="pickMedia">
              <FaIcon name="i-ri:upload-cloud-2-line" />
              上传
            </FaButton>
            <input ref="mediaInput" type="file" accept="image/*" hidden @change="uploadMedia">
          </div>
          <div class="media-grid">
            <button v-for="item in mediaItems" :key="item.id" type="button" @click="insertImage(item)">
              <img :src="toBackendAssetUrl(item.url)" :alt="item.originalName || 'CMS 图片'">
            </button>
          </div>
        </section>
      </aside>

      <main class="grapes-canvas">
        <div ref="editorEl" class="grapes-editor" />
      </main>

      <aside class="grapes-sidebar right">
        <section>
          <h3>图层</h3>
          <div ref="layersEl" />
        </section>
        <section>
          <h3>属性</h3>
          <div ref="traitsEl" />
        </section>
        <section>
          <h3>样式</h3>
          <div ref="stylesEl" />
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.grapes-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  height: 100vh;
  background: #f8fafc;
}

.grapes-header,
.grapes-header__actions,
.media-head,
.media-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.grapes-header {
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}

.grapes-header strong,
.grapes-header span {
  display: block;
}

.grapes-header span {
  color: #64748b;
  font-size: 12px;
}

.grapes-body {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 320px;
  min-height: 0;
}

.grapes-sidebar {
  display: grid;
  align-content: start;
  gap: 14px;
  min-width: 0;
  overflow: auto;
  padding: 12px;
  border-color: #e5e7eb;
  background: #fff;
}

.grapes-sidebar.left {
  border-right: 1px solid #e5e7eb;
}

.grapes-sidebar.right {
  border-left: 1px solid #e5e7eb;
}

.grapes-sidebar section {
  display: grid;
  gap: 10px;
}

.grapes-sidebar h3 {
  margin: 0;
  font-size: 14px;
}

.grapes-canvas {
  min-width: 0;
  min-height: 0;
}

.grapes-editor {
  height: 100%;
}

.media-head {
  justify-content: space-between;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.media-grid button {
  overflow: hidden;
  aspect-ratio: 1;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.media-grid img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

:deep(.gjs-one-bg) {
  background-color: #fff;
}

:deep(.gjs-two-color) {
  color: #475569;
}

:deep(.gjs-three-bg) {
  background-color: #f8fafc;
}

:deep(.gjs-four-color),
:deep(.gjs-four-color-h:hover) {
  color: #0f766e;
}

:deep(.gjs-editor),
:deep(.gjs-pn-panel),
:deep(.gjs-pn-commands),
:deep(.gjs-pn-options),
:deep(.gjs-pn-devices-c),
:deep(.gjs-blocks-c),
:deep(.gjs-sm-sectors),
:deep(.gjs-layers),
:deep(.gjs-traits) {
  background: #fff;
  color: #0f172a;
}

:deep(.gjs-cv-canvas) {
  background: #eef2f7;
}

:deep(.gjs-cv-canvas__frames) {
  background: #f8fafc;
}

:deep(.gjs-cv-canvas__frame) {
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.12);
}

:deep(.gjs-block-category),
:deep(.gjs-sm-sector),
:deep(.gjs-layer),
:deep(.gjs-trt-trait) {
  border-color: #e5e7eb;
  background: #fff;
  color: #0f172a;
}

:deep(.gjs-block-category .gjs-title),
:deep(.gjs-sm-sector .gjs-sm-sector-title),
:deep(.gjs-trt-header),
:deep(.gjs-layer-title) {
  border-radius: 6px;
  background: #f8fafc;
  color: #334155;
  font-weight: 700;
}

:deep(.gjs-block) {
  width: 100%;
  min-height: 78px;
  margin: 0 0 8px;
  padding: 12px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #334155;
  box-shadow: none;
}

:deep(.gjs-block:hover) {
  border-color: #0f766e;
  color: #0f766e;
  box-shadow: 0 8px 22px rgba(15, 118, 110, 0.12);
}

:deep(.gjs-block-label) {
  color: inherit;
  font-size: 12px;
  line-height: 1.35;
}

:deep(.gjs-block-icon) {
  display: inline-grid;
  width: 28px;
  height: 28px;
  margin-bottom: 6px;
  place-items: center;
  border-radius: 7px;
  background: #ecfdf5;
  color: #0f766e;
  font-weight: 800;
}

:deep(.gjs-field),
:deep(.gjs-field input),
:deep(.gjs-field select),
:deep(.gjs-field textarea),
:deep(.gjs-input-holder input),
:deep(.gjs-select select) {
  border-color: #e5e7eb;
  background: #f8fafc;
  color: #0f172a;
}

:deep(.gjs-sm-property),
:deep(.gjs-clm-tags),
:deep(.gjs-layer-caret),
:deep(.gjs-layer-name),
:deep(.gjs-trt-trait__wrp) {
  color: #334155;
}

@media (max-width: 1180px) {
  .grapes-body {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .grapes-sidebar.right {
    display: none;
  }
}
</style>

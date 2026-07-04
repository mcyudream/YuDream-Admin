<script setup lang="ts">
import type { CmsPage, CmsPagePayload, HomePageLayout, HomeSection, HomeSectionType, PageStatus, PageTemplate } from '@/api/modules/platform-cms'
import apiCms from '@/api/modules/platform-cms'

type WorkbenchTab = 'pages' | 'home'
type EditorMode = 'builder' | 'markdown' | 'html'
type BuilderBlockType = 'hero' | 'text' | 'image' | 'cta'

interface BuilderBlock {
  id: string
  type: BuilderBlockType
  title: string
  text: string
  imageUrl: string
  buttonText: string
  buttonUrl: string
}

const toast = useFaToast()
const modal = useFaModal()

const activeTab = ref<WorkbenchTab>('pages')
const editorMode = ref<EditorMode>('builder')
const loading = ref(false)
const saving = ref(false)
const pages = ref<CmsPage[]>([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const search = reactive({ keyword: '' })
const selectedPageId = ref<number | null>(null)

const pageForm = reactive<CmsPagePayload>({
  title: '',
  slug: '',
  summary: '',
  excerpt: '',
  coverImageUrl: '',
  markdownContent: '',
  htmlContent: '',
  seoTitle: '',
  seoDescription: '',
  template: 'DEFAULT',
  status: 'DRAFT',
})

const blocks = ref<BuilderBlock[]>([])

const home = reactive<HomePageLayout>({
  title: 'YuDream',
  subtitle: '自定义首页',
  theme: 'default',
  heroImageUrl: '',
  settings: {},
  sections: [],
  published: false,
})

const templateOptions: { label: string, value: PageTemplate }[] = [
  { label: '默认页面', value: 'DEFAULT' },
  { label: '营销落地页', value: 'LANDING' },
  { label: '文档页面', value: 'DOC' },
  { label: '空白画布', value: 'BLANK' },
]
const statusOptions: { label: string, value: PageStatus }[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
]
const blockPresets: { type: BuilderBlockType, label: string, icon: string }[] = [
  { type: 'hero', label: '首屏', icon: 'i-ri:layout-top-line' },
  { type: 'text', label: '正文', icon: 'i-ri:text' },
  { type: 'image', label: '图片', icon: 'i-ri:image-line' },
  { type: 'cta', label: '行动按钮', icon: 'i-ri:cursor-line' },
]
const sectionPresets: { type: HomeSectionType, label: string, icon: string }[] = [
  { type: 'HERO', label: '首屏', icon: 'i-ri:layout-top-line' },
  { type: 'FEATURE', label: '特性', icon: 'i-ri:sparkling-line' },
  { type: 'CONTENT', label: '内容', icon: 'i-ri:article-line' },
  { type: 'CTA', label: '行动', icon: 'i-ri:cursor-line' },
]

const selectedPage = computed(() => pages.value.find(item => item.id === selectedPageId.value) || null)
const pagePreviewHtml = computed(() => editorMode.value === 'markdown' ? markdownPreview(pageForm.markdownContent) : sanitizeHtml(pageForm.htmlContent || buildHtml(blocks.value)))
const pagePublicUrl = computed(() => pageForm.slug ? `/site/${pageForm.slug}` : '/site')

watch(activeTab, async () => {
  if (activeTab.value === 'pages') {
    await loadPages()
  }
  else {
    await loadHome()
  }
})

watch(blocks, () => {
  if (editorMode.value === 'builder') {
    pageForm.htmlContent = buildHtml(blocks.value)
  }
}, { deep: true })

onMounted(async () => {
  await loadPages()
})

async function loadPages() {
  loading.value = true
  try {
    const res = await apiCms.page({
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
    })
    pages.value = res.data.records
    pagination.total = res.data.total
    if (!selectedPageId.value && pages.value.length) {
      selectPage(pages.value[0])
    }
    else if (selectedPageId.value && !pages.value.some(item => item.id === selectedPageId.value)) {
      resetPageForm()
    }
  }
  finally {
    loading.value = false
  }
}

async function loadHome() {
  loading.value = true
  try {
    const res = await apiCms.home()
    Object.assign(home, {
      title: res.data.title || 'YuDream',
      subtitle: res.data.subtitle || '',
      theme: res.data.theme || 'default',
      heroImageUrl: res.data.heroImageUrl || '',
      settings: res.data.settings || {},
      sections: res.data.sections || [],
      published: Boolean(res.data.published),
    })
  }
  finally {
    loading.value = false
  }
}

function selectPage(page: CmsPage) {
  selectedPageId.value = page.id
  Object.assign(pageForm, {
    title: page.title,
    slug: page.slug,
    summary: page.summary || '',
    excerpt: page.excerpt || '',
    coverImageUrl: page.coverImageUrl || '',
    markdownContent: page.markdownContent || '',
    htmlContent: page.htmlContent || '',
    seoTitle: page.seoTitle || '',
    seoDescription: page.seoDescription || '',
    template: page.template || 'DEFAULT',
    status: page.status || 'DRAFT',
  })
  blocks.value = page.htmlContent ? [] : [defaultBlock('hero'), defaultBlock('text')]
  editorMode.value = page.htmlContent ? 'html' : 'builder'
}

function createPage() {
  selectedPageId.value = null
  resetPageForm()
  blocks.value = [defaultBlock('hero'), defaultBlock('text'), defaultBlock('cta')]
  editorMode.value = 'builder'
}

function resetPageForm() {
  Object.assign(pageForm, {
    title: '新页面',
    slug: `page-${Date.now()}`,
    summary: '',
    excerpt: '',
    coverImageUrl: '',
    markdownContent: '# 新页面\n\n在这里编写 Markdown 内容。',
    htmlContent: '',
    seoTitle: '',
    seoDescription: '',
    template: 'DEFAULT' as PageTemplate,
    status: 'DRAFT' as PageStatus,
  })
  selectedPageId.value = null
}

async function savePage() {
  saving.value = true
  try {
    if (editorMode.value === 'builder') {
      pageForm.htmlContent = buildHtml(blocks.value)
    }
    const payload = { ...pageForm }
    const res = selectedPageId.value
      ? await apiCms.updatePage(selectedPageId.value, payload)
      : await apiCms.createPage(payload)
    selectedPageId.value = res.data.id
    toast.success('页面已保存')
    await loadPages()
  }
  finally {
    saving.value = false
  }
}

function confirmPublish() {
  if (!selectedPage.value) {
    pageForm.status = 'PUBLISHED'
    savePage()
    return
  }
  const published = selectedPage.value.status === 'PUBLISHED'
  modal.confirm({
    title: published ? '取消发布' : '发布页面',
    content: `确认${published ? '取消发布' : '发布'}「${selectedPage.value.title}」吗？`,
    onConfirm: async () => {
      published ? await apiCms.unpublish(selectedPage.value!.id) : await apiCms.publish(selectedPage.value!.id)
      toast.success(published ? '已取消发布' : '页面已发布')
      await loadPages()
    },
  })
}

async function saveHome() {
  saving.value = true
  try {
    await apiCms.saveHome({
      ...home,
      sections: home.sections,
      settings: home.settings || {},
    })
    toast.success('首页已保存')
  }
  finally {
    saving.value = false
  }
}

function addBlock(type: BuilderBlockType) {
  blocks.value.push(defaultBlock(type))
}

function removeBlock(id: string) {
  blocks.value = blocks.value.filter(item => item.id !== id)
}

function addHomeSection(type: HomeSectionType) {
  home.sections.push({
    id: `${type.toLowerCase()}-${Date.now()}`,
    type,
    title: sectionTitle(type),
    subtitle: '',
    mediaUrl: '',
    actionText: type === 'CTA' ? '立即开始' : '',
    actionUrl: '',
    settings: {},
    sort: home.sections.length + 1,
    visible: true,
  })
}

function removeHomeSection(section: HomeSection) {
  home.sections = home.sections.filter(item => item !== section)
}

function defaultBlock(type: BuilderBlockType): BuilderBlock {
  const titleMap: Record<BuilderBlockType, string> = {
    hero: '用一个清晰的标题介绍页面',
    text: '内容区块标题',
    image: '图片展示',
    cta: '行动引导',
  }
  const textMap: Record<BuilderBlockType, string> = {
    hero: '这里适合放置页面价值主张、产品介绍或活动说明。',
    text: '像编辑文章一样组织内容，也可以随时切换到 HTML 模式做更细的布局。',
    image: '为页面加入产品、场景或证书图片，让公开页面更具体。',
    cta: '给访问者一个明确的下一步。',
  }
  return {
    id: `${type}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    type,
    title: titleMap[type],
    text: textMap[type],
    imageUrl: '',
    buttonText: type === 'cta' ? '立即了解' : '',
    buttonUrl: '',
  }
}

function buildHtml(items: BuilderBlock[]) {
  return `<div id="pagebuilder">${items.map(blockHtml).join('')}</div>`
}

function blockHtml(block: BuilderBlock) {
  const title = escapeHtml(block.title)
  const text = escapeHtml(block.text)
  const image = escapeAttr(block.imageUrl)
  const buttonText = escapeHtml(block.buttonText)
  const buttonUrl = escapeAttr(block.buttonUrl)
  if (block.type === 'hero') {
    return `<section class="yb-hero"${image ? ` style="background-image:linear-gradient(90deg,rgba(15,23,42,.76),rgba(15,23,42,.18)),url('${image}')"` : ''}><h1>${title}</h1><p>${text}</p>${buttonUrl ? `<a href="${buttonUrl}">${buttonText || '了解更多'}</a>` : ''}</section>`
  }
  if (block.type === 'image') {
    return `<figure class="yb-image">${image ? `<img src="${image}" alt="${title}">` : ''}<figcaption><strong>${title}</strong><span>${text}</span></figcaption></figure>`
  }
  if (block.type === 'cta') {
    return `<section class="yb-cta"><h2>${title}</h2><p>${text}</p>${buttonUrl ? `<a href="${buttonUrl}">${buttonText || '立即开始'}</a>` : ''}</section>`
  }
  return `<section class="yb-text"><h2>${title}</h2><p>${text}</p></section>`
}

function markdownPreview(markdown?: string) {
  return escapeHtml(markdown || '')
    .split(/\r?\n/)
    .map((line) => {
      if (line.startsWith('# ')) {
        return `<h1>${line.slice(2)}</h1>`
      }
      if (line.startsWith('## ')) {
        return `<h2>${line.slice(3)}</h2>`
      }
      if (line.trim()) {
        return `<p>${line}</p>`
      }
      return ''
    })
    .join('')
}

function sanitizeHtml(value?: string) {
  return (value || '')
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
    .replace(/\son\w+="[^"]*"/gi, '')
    .replace(/\son\w+='[^']*'/gi, '')
    .replace(/javascript:/gi, '')
}

function escapeHtml(value?: string) {
  return (value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeAttr(value?: string) {
  return escapeHtml(value).replace(/`/g, '&#96;')
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '未发布'
}

function sectionTitle(type: HomeSectionType) {
  return {
    HERO: '首页首屏',
    FEATURE: '核心特性',
    CONTENT: '内容区块',
    CTA: '行动引导',
  }[type]
}
</script>

<template>
  <div class="cms-workbench">
    <FaPageHeader title="内容站点" class="cms-header">
      <FaButton variant="outline" :loading="loading" @click="activeTab === 'pages' ? loadPages() : loadHome()">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
      <FaButton v-if="activeTab === 'pages'" v-auth="'platform:cms:edit'" :loading="saving" @click="savePage">
        <FaIcon name="i-ri:save-3-line" />
        保存页面
      </FaButton>
      <FaButton v-else v-auth="'platform:cms:edit'" :loading="saving" @click="saveHome">
        <FaIcon name="i-ri:save-3-line" />
        保存首页
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div class="cms-tabs">
        <button type="button" :class="{ active: activeTab === 'pages' }" @click="activeTab = 'pages'">
          <FaIcon name="i-ri:file-list-3-line" />
          页面库
        </button>
        <button type="button" :class="{ active: activeTab === 'home' }" @click="activeTab = 'home'">
          <FaIcon name="i-ri:home-4-line" />
          首页外观
        </button>
        <a class="public-link" href="/site" target="_blank" rel="noopener noreferrer">
          <FaIcon name="i-ri:external-link-line" />
          访问站点
        </a>
      </div>

      <section v-if="activeTab === 'pages'" class="cms-layout">
        <aside class="page-list">
          <div class="page-list__toolbar">
            <FaInput v-model="search.keyword" clearable placeholder="搜索标题、路径" @keydown.enter="loadPages" @clear="loadPages" />
            <FaButton v-auth="'platform:cms:edit'" @click="createPage">
              <FaIcon name="i-ri:add-line" />
            </FaButton>
          </div>
          <div v-loading="loading" class="page-list__items">
            <button v-for="item in pages" :key="item.id" type="button" :class="{ active: item.id === selectedPageId }" @click="selectPage(item)">
              <strong>{{ item.title }}</strong>
              <span>/site/{{ item.slug }}</span>
              <small>{{ item.status === 'PUBLISHED' ? '已发布' : '草稿' }} · {{ dateText(item.publishedAt) }}</small>
            </button>
            <div v-if="!pages.length" class="empty-state">
              暂无页面
            </div>
          </div>
        </aside>

        <main class="page-editor">
          <div class="editor-top">
            <FaInput v-model="pageForm.title" class="title-input" />
            <div class="editor-mode">
              <button type="button" :class="{ active: editorMode === 'builder' }" @click="editorMode = 'builder'">区块</button>
              <button type="button" :class="{ active: editorMode === 'markdown' }" @click="editorMode = 'markdown'">Markdown</button>
              <button type="button" :class="{ active: editorMode === 'html' }" @click="editorMode = 'html'">HTML</button>
            </div>
          </div>

          <section v-if="editorMode === 'builder'" class="block-editor">
            <div class="block-toolbar">
              <FaButton v-for="preset in blockPresets" :key="preset.type" size="sm" variant="outline" @click="addBlock(preset.type)">
                <FaIcon :name="preset.icon" />
                {{ preset.label }}
              </FaButton>
            </div>
            <article v-for="block in blocks" :key="block.id" class="builder-block">
              <div class="builder-block__head">
                <FaTag variant="secondary">{{ block.type }}</FaTag>
                <FaButton size="sm" variant="ghost" @click="removeBlock(block.id)">
                  <FaIcon name="i-ri:delete-bin-line" />
                </FaButton>
              </div>
              <FaInput v-model="block.title" placeholder="区块标题" />
              <FaTextarea v-model="block.text" rows="4" placeholder="区块内容" />
              <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
                <FaInput v-model="block.imageUrl" placeholder="图片地址" />
                <FaInput v-model="block.buttonUrl" placeholder="按钮链接" />
                <FaInput v-model="block.buttonText" placeholder="按钮文案" />
              </div>
            </article>
          </section>

          <FaTextarea v-else-if="editorMode === 'markdown'" v-model="pageForm.markdownContent" rows="22" input-class="font-mono" />
          <FaTextarea v-else v-model="pageForm.htmlContent" rows="22" input-class="font-mono" placeholder="<div id=&quot;pagebuilder&quot;>...</div>" />
        </main>

        <aside class="publish-panel">
          <section>
            <h3>发布</h3>
            <a-form :model="pageForm" layout="vertical">
              <a-form-item label="路径">
                <FaInput v-model="pageForm.slug" />
              </a-form-item>
              <a-form-item label="状态">
                <FaSelect v-model="pageForm.status" :options="statusOptions" />
              </a-form-item>
              <a-form-item label="模板">
                <FaSelect v-model="pageForm.template" :options="templateOptions" />
              </a-form-item>
            </a-form>
            <div class="publish-actions">
              <FaButton v-auth="'platform:cms:publish'" variant="outline" @click="confirmPublish">
                {{ selectedPage?.status === 'PUBLISHED' ? '取消发布' : '发布' }}
              </FaButton>
              <a :href="pagePublicUrl" target="_blank" rel="noopener noreferrer">预览公开页</a>
            </div>
          </section>

          <section>
            <h3>SEO 与封面</h3>
            <a-form :model="pageForm" layout="vertical">
              <a-form-item label="摘要">
                <FaInput v-model="pageForm.summary" />
              </a-form-item>
              <a-form-item label="摘录">
                <FaTextarea v-model="pageForm.excerpt" rows="3" />
              </a-form-item>
              <a-form-item label="封面图">
                <FaInput v-model="pageForm.coverImageUrl" />
              </a-form-item>
              <a-form-item label="SEO 标题">
                <FaInput v-model="pageForm.seoTitle" />
              </a-form-item>
              <a-form-item label="SEO 描述">
                <FaTextarea v-model="pageForm.seoDescription" rows="3" />
              </a-form-item>
            </a-form>
          </section>

          <section>
            <h3>实时预览</h3>
            <div class="mini-preview" v-html="pagePreviewHtml" />
          </section>
        </aside>
      </section>

      <section v-else v-loading="loading" class="home-layout">
        <main class="home-form">
          <a-form :model="home" layout="vertical">
            <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
              <a-form-item label="首页标题">
                <FaInput v-model="home.title" />
              </a-form-item>
              <a-form-item label="主题">
                <FaInput v-model="home.theme" />
              </a-form-item>
              <a-form-item label="副标题" class="md:col-span-2">
                <FaInput v-model="home.subtitle" />
              </a-form-item>
              <a-form-item label="首屏图片" class="md:col-span-2">
                <FaInput v-model="home.heroImageUrl" />
              </a-form-item>
              <a-form-item label="发布首页">
                <FaSwitch v-model="home.published" />
              </a-form-item>
            </div>
          </a-form>

          <div class="block-toolbar">
            <FaButton v-for="preset in sectionPresets" :key="preset.type" size="sm" variant="outline" @click="addHomeSection(preset.type)">
              <FaIcon :name="preset.icon" />
              {{ preset.label }}
            </FaButton>
          </div>
          <article v-for="section in home.sections" :key="section.id || section.title" class="builder-block">
            <div class="builder-block__head">
              <FaTag variant="secondary">{{ section.type }}</FaTag>
              <FaButton size="sm" variant="ghost" @click="removeHomeSection(section)">
                <FaIcon name="i-ri:delete-bin-line" />
              </FaButton>
            </div>
            <FaInput v-model="section.title" placeholder="区块标题" />
            <FaTextarea v-model="section.subtitle" rows="3" placeholder="区块说明" />
            <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
              <FaInput v-model="section.mediaUrl" placeholder="图片地址" />
              <FaInput v-model="section.actionUrl" placeholder="按钮链接" />
              <FaInput v-model="section.actionText" placeholder="按钮文案" />
              <label class="inline-flex items-center gap-2 text-sm">
                <FaSwitch v-model="section.visible" />
                显示区块
              </label>
            </div>
          </article>
        </main>

        <aside class="home-preview">
          <div class="home-hero" :style="home.heroImageUrl ? { backgroundImage: `linear-gradient(90deg, rgba(15, 23, 42, 0.76), rgba(15, 23, 42, 0.2)), url(${home.heroImageUrl})` } : undefined">
            <h1>{{ home.title }}</h1>
            <p>{{ home.subtitle }}</p>
          </div>
          <div v-for="section in home.sections.filter(item => item.visible !== false)" :key="section.id || section.title" class="home-preview-section">
            <span>{{ section.type }}</span>
            <strong>{{ section.title }}</strong>
            <p>{{ section.subtitle }}</p>
          </div>
        </aside>
      </section>
    </FaPageMain>
  </div>
</template>

<style scoped>
.cms-tabs,
.page-list__toolbar,
.editor-top,
.editor-mode,
.block-toolbar,
.publish-actions,
.builder-block__head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.cms-tabs {
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.cms-tabs button,
.editor-mode button,
.public-link {
  display: inline-flex;
  align-items: center;
  height: 36px;
  gap: 6px;
  padding: 0 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
  color: var(--color-text-2);
}

.cms-tabs button.active,
.editor-mode button.active {
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
}

.public-link {
  margin-left: auto;
  text-decoration: none;
}

.cms-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.page-list,
.page-editor,
.publish-panel,
.home-form,
.home-preview {
  min-width: 0;
}

.page-list__items {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.page-list__items button {
  display: grid;
  gap: 5px;
  width: 100%;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
  color: var(--color-text-1);
  text-align: left;
}

.page-list__items button.active {
  border-color: rgb(var(--primary-6));
  box-shadow: inset 3px 0 0 rgb(var(--primary-6));
}

.page-list__items span,
.page-list__items small {
  color: var(--color-text-3);
}

.title-input :deep(input) {
  height: 44px;
  font-size: 22px;
  font-weight: 800;
}

.editor-top {
  justify-content: space-between;
  margin-bottom: 12px;
}

.editor-mode {
  flex-shrink: 0;
}

.block-editor,
.home-form {
  display: grid;
  gap: 12px;
}

.block-toolbar {
  flex-wrap: wrap;
}

.builder-block,
.publish-panel section,
.mini-preview,
.home-preview-section {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.builder-block__head {
  justify-content: space-between;
}

.publish-panel {
  display: grid;
  gap: 12px;
}

.publish-panel h3 {
  margin: 0;
  font-size: 15px;
}

.publish-actions {
  justify-content: space-between;
}

.publish-actions a {
  color: rgb(var(--primary-6));
  text-decoration: none;
}

.mini-preview {
  max-height: 460px;
  overflow: auto;
  color: var(--color-text-1);
}

.mini-preview :deep(#pagebuilder),
.mini-preview :deep(.pagebuilder) {
  display: grid;
  gap: 12px;
}

.mini-preview :deep(.yb-hero),
.mini-preview :deep(.yb-cta) {
  padding: 18px;
  border-radius: 6px;
  background: #0f766e;
  color: #fff;
}

.mini-preview :deep(.yb-text),
.mini-preview :deep(.yb-image) {
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.mini-preview :deep(img) {
  max-width: 100%;
  border-radius: 6px;
}

.home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 16px;
  align-items: start;
}

.home-preview {
  display: grid;
  gap: 10px;
}

.home-hero {
  display: grid;
  min-height: 260px;
  align-items: end;
  padding: 24px;
  border-radius: 6px;
  background: linear-gradient(135deg, #0f766e, #334155);
  background-position: center;
  background-size: cover;
  color: #fff;
}

.home-hero h1 {
  margin: 0 0 8px;
  font-size: 34px;
  font-weight: 900;
}

.home-hero p,
.home-preview-section p {
  margin: 0;
}

.home-preview-section span {
  color: rgb(var(--primary-6));
  font-size: 12px;
  font-weight: 800;
}

.empty-state {
  padding: 24px;
  color: var(--color-text-3);
  text-align: center;
}

@media (max-width: 1180px) {
  .cms-layout,
  .home-layout {
    grid-template-columns: 1fr;
  }
}
</style>

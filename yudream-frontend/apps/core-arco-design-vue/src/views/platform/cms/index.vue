<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { CmsPage, CmsPagePayload, HomePageLayout, HomeSectionType, PageStatus } from '@/api/modules/platform-cms'
import apiCms from '@/api/modules/platform-cms'

const modal = useFaModal()
const toast = useFaToast()

const activeTab = ref<'home' | 'pages'>('home')
const loading = ref(false)
const savingHome = ref(false)
const rows = ref<CmsPage[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive({ keyword: '' })

const home = reactive<HomePageLayout>({
  title: 'YuDream',
  subtitle: '自定义首页',
  theme: 'default',
  heroImageUrl: '',
  settings: {},
  sections: [],
  published: false,
})
const homeSettingsJson = ref('{}')
const homeSectionsJson = ref('[]')

const formVisible = ref(false)
const editing = ref<CmsPage | null>(null)
const form = reactive<CmsPagePayload>({
  title: '',
  slug: '',
  summary: '',
  markdownContent: '',
  seoTitle: '',
  seoDescription: '',
  status: 'DRAFT',
})

const tabs = [
  { key: 'home', label: '首页定制', icon: 'i-ri:home-4-line' },
  { key: 'pages', label: '单页面', icon: 'i-ri:file-markdown-line' },
] as const
const statusOptions: { label: string, value: PageStatus }[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
]
const sectionPresets: { type: HomeSectionType, label: string, icon: string }[] = [
  { type: 'HERO', label: '首屏', icon: 'i-ri:layout-top-line' },
  { type: 'FEATURE', label: '特性', icon: 'i-ri:sparkling-line' },
  { type: 'CONTENT', label: '内容', icon: 'i-ri:article-line' },
  { type: 'CTA', label: '行动', icon: 'i-ri:cursor-line' },
]
const tableColumns = computed<TableColumn<CmsPage>[]>(() => [
  { accessorKey: 'title', header: '页面标题', width: 200, fixed: 'left' },
  { accessorKey: 'slug', header: '路径', width: 180 },
  { accessorKey: 'summary', header: '摘要', width: 280 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { id: 'publishedAt', header: '发布时间', width: 180 },
  { id: 'operation', header: '操作', width: 240, align: 'center', fixed: 'right' },
])

onMounted(load)

watch(activeTab, () => {
  pagination.page = 1
  load()
})

async function load() {
  if (activeTab.value === 'home') {
    await loadHome()
  }
  else {
    await loadPages()
  }
}

async function loadHome() {
  loading.value = true
  try {
    const res = await apiCms.home()
    assignHome(res.data)
  }
  finally {
    loading.value = false
  }
}

async function loadPages() {
  loading.value = true
  try {
    const res = await apiCms.page({
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
    })
    rows.value = res.data.records
    pagination.total = res.data.total
  }
  finally {
    loading.value = false
  }
}

function assignHome(data: HomePageLayout) {
  Object.assign(home, {
    title: data.title || 'YuDream',
    subtitle: data.subtitle || '',
    theme: data.theme || 'default',
    heroImageUrl: data.heroImageUrl || '',
    settings: data.settings || {},
    sections: data.sections || [],
    published: Boolean(data.published),
  })
  homeSettingsJson.value = JSON.stringify(home.settings || {}, null, 2)
  homeSectionsJson.value = JSON.stringify(home.sections || [], null, 2)
}

async function saveHome() {
  const settings = parseJsonObject(homeSettingsJson.value, '首页设置')
  const sections = parseJsonArray(homeSectionsJson.value, '首页区块')
  if (!settings || !sections) {
    return
  }
  savingHome.value = true
  try {
    const res = await apiCms.saveHome({
      ...home,
      settings,
      sections,
    })
    assignHome(res.data)
    toast.success('首页配置已保存')
  }
  finally {
    savingHome.value = false
  }
}

function addHomeSection(type: HomeSectionType) {
  const sections = parseJsonArray(homeSectionsJson.value, '首页区块')
  if (!sections) {
    return
  }
  const nextSort = sections.length + 1
  sections.push({
    id: `${type.toLowerCase()}-${Date.now()}`,
    type,
    title: sectionTitle(type),
    subtitle: '',
    mediaUrl: '',
    actionText: type === 'CTA' ? '立即开始' : '',
    actionUrl: '',
    settings: {},
    sort: nextSort,
    visible: true,
  })
  home.sections = sections
  homeSectionsJson.value = JSON.stringify(sections, null, 2)
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    title: '',
    slug: '',
    summary: '',
    markdownContent: '# 新页面\n\n在这里编辑 Markdown 内容。',
    seoTitle: '',
    seoDescription: '',
    status: 'DRAFT' as PageStatus,
  })
  formVisible.value = true
}

function openEdit(row: CmsPage) {
  editing.value = row
  Object.assign(form, {
    title: row.title,
    slug: row.slug,
    summary: row.summary || '',
    markdownContent: row.markdownContent || '',
    seoTitle: row.seoTitle || '',
    seoDescription: row.seoDescription || '',
    status: row.status || 'DRAFT',
  })
  formVisible.value = true
}

async function savePage() {
  if (editing.value) {
    await apiCms.updatePage(editing.value.id, form)
  }
  else {
    await apiCms.createPage(form)
  }
  toast.success('页面已保存')
  formVisible.value = false
  await loadPages()
}

function confirmPublish(row: CmsPage) {
  const published = row.status === 'PUBLISHED'
  modal.confirm({
    title: published ? '取消发布' : '发布页面',
    content: `确认${published ? '取消发布' : '发布'}「${row.title}」吗？`,
    onConfirm: async () => {
      published ? await apiCms.unpublish(row.id) : await apiCms.publish(row.id)
      toast.success(published ? '已取消发布' : '页面已发布')
      await loadPages()
    },
  })
}

function resetSearch() {
  search.keyword = ''
  pagination.page = 1
  loadPages()
}

function onPageChange(page: number) {
  pagination.page = page
  loadPages()
}

function onSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  loadPages()
}

function parseJsonObject(value: string, label: string) {
  try {
    const parsed = value ? JSON.parse(value) : {}
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      toast.error(`${label}必须是 JSON 对象`)
      return null
    }
    return parsed as Record<string, string>
  }
  catch {
    toast.error(`${label}格式错误`, { description: '请输入合法 JSON 对象' })
    return null
  }
}

function parseJsonArray(value: string, label: string) {
  try {
    const parsed = value ? JSON.parse(value) : []
    if (!Array.isArray(parsed)) {
      toast.error(`${label}必须是 JSON 数组`)
      return null
    }
    return parsed
  }
  catch {
    toast.error(`${label}格式错误`, { description: '请输入合法 JSON 数组' })
    return null
  }
}

function sectionTitle(type: HomeSectionType) {
  const titles: Record<HomeSectionType, string> = {
    HERO: '首屏展示',
    FEATURE: '核心特性',
    CONTENT: '内容区块',
    CTA: '行动引导',
  }
  return titles[type]
}

function markdownPreview(markdown?: string) {
  const lines = escapeHtml(markdown || '').split(/\r?\n/)
  const html: string[] = []
  let inList = false
  for (const line of lines) {
    const listMatch = line.match(/^\s*[-*]\s+(.+)$/)
    if (listMatch) {
      if (!inList) {
        html.push('<ul>')
        inList = true
      }
      html.push(`<li>${inlineMarkdown(listMatch[1])}</li>`)
      continue
    }
    if (inList) {
      html.push('</ul>')
      inList = false
    }
    if (line.startsWith('### ')) {
      html.push(`<h3>${inlineMarkdown(line.slice(4))}</h3>`)
    }
    else if (line.startsWith('## ')) {
      html.push(`<h2>${inlineMarkdown(line.slice(3))}</h2>`)
    }
    else if (line.startsWith('# ')) {
      html.push(`<h1>${inlineMarkdown(line.slice(2))}</h1>`)
    }
    else if (line.trim()) {
      html.push(`<p>${inlineMarkdown(line)}</p>`)
    }
    else {
      html.push('<br>')
    }
  }
  if (inList) {
    html.push('</ul>')
  }
  return html.join('')
}

function inlineMarkdown(value: string) {
  return value
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/\[([^\]]+)]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function statusText(status: PageStatus) {
  return status === 'PUBLISHED' ? '已发布' : '草稿'
}

function statusVariant(status: PageStatus) {
  return status === 'PUBLISHED' ? 'default' : 'secondary'
}
</script>

<template>
  <div>
    <FaPageHeader title="内容定制" class="mb-0">
      <FaButton v-auth="'platform:cms:view'" variant="outline" :loading="loading" @click="load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
      <FaButton v-if="activeTab === 'home'" v-auth="'platform:cms:edit'" :loading="savingHome" @click="saveHome">
        <FaIcon name="i-ri:save-3-line" />
        保存首页
      </FaButton>
      <FaButton v-if="activeTab === 'pages'" v-auth="'platform:cms:edit'" @click="openCreate">
        <FaIcon name="i-ri:add-line" />
        新增页面
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div class="cms-tabs">
        <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
          <FaIcon :name="tab.icon" />
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <section v-if="activeTab === 'home'" v-loading="loading" class="home-editor">
        <div class="home-form">
          <a-form :model="home" layout="vertical">
            <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
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
                <FaInput v-model="home.heroImageUrl" placeholder="https://..." />
              </a-form-item>
              <a-form-item label="发布首页">
                <FaSwitch v-model="home.published" />
              </a-form-item>
              <a-form-item label="首页设置 JSON" class="md:col-span-2">
                <FaTextarea v-model="homeSettingsJson" rows="7" input-class="font-mono" />
              </a-form-item>
              <a-form-item label="首页区块 JSON" class="md:col-span-2">
                <div class="section-tools">
                  <FaButton v-for="preset in sectionPresets" :key="preset.type" size="sm" variant="outline" type="button" @click="addHomeSection(preset.type)">
                    <FaIcon :name="preset.icon" />
                    {{ preset.label }}
                  </FaButton>
                </div>
                <FaTextarea v-model="homeSectionsJson" rows="14" input-class="font-mono" />
              </a-form-item>
            </div>
          </a-form>
        </div>
        <aside class="home-preview">
          <div class="preview-hero" :style="{ backgroundImage: home.heroImageUrl ? `url(${home.heroImageUrl})` : undefined }">
            <div>
              <h1>{{ home.title || '首页标题' }}</h1>
              <p>{{ home.subtitle || '首页副标题' }}</p>
            </div>
          </div>
          <div class="preview-sections">
            <div v-for="section in home.sections" :key="section.id || section.title" class="preview-section">
              <FaTag variant="secondary">{{ section.type }}</FaTag>
              <strong>{{ section.title || '-' }}</strong>
              <span>{{ section.subtitle || '-' }}</span>
            </div>
          </div>
        </aside>
      </section>

      <template v-else>
        <FaSearchBar>
          <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
            <FaInput v-model="search.keyword" clearable placeholder="页面标题 / 路径 / 摘要" @keydown.enter="loadPages" @clear="loadPages" />
            <div class="flex gap-2 md:justify-end">
              <FaButton variant="outline" @click="resetSearch">
                重置
              </FaButton>
              <FaButton :loading="loading" @click="loadPages">
                <FaIcon name="i-ri:search-line" />
                筛选
              </FaButton>
            </div>
          </div>
        </FaSearchBar>

        <div class="mx--4 my-3 border-t border-t-dashed" />

        <FaTable
          v-loading="loading"
          row-key="id"
          table-root-class="rounded-lg overflow-hidden"
          table-class="min-w-[1120px]"
          border
          stripe
          column-visibility
          :columns="tableColumns"
          :data="rows"
        >
          <template #cell-status="{ row }">
            <FaTag :variant="statusVariant(row.original.status)">
              {{ statusText(row.original.status) }}
            </FaTag>
          </template>
          <template #cell-publishedAt="{ row }">
            {{ dateText(row.original.publishedAt) }}
          </template>
          <template #cell-operation="{ row }">
            <div class="table-actions">
              <FaButton v-auth="'platform:cms:edit'" size="sm" variant="outline" @click="openEdit(row.original)">
                编辑
              </FaButton>
              <FaButton v-auth="'platform:cms:publish'" size="sm" variant="ghost" @click="confirmPublish(row.original)">
                {{ row.original.status === 'PUBLISHED' ? '取消发布' : '发布' }}
              </FaButton>
            </div>
          </template>
        </FaTable>

        <FaPagination
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          class="mt-3"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </template>
    </FaPageMain>

    <FaModal v-model="formVisible" :title="editing ? '编辑页面' : '新增页面'" show-cancel-button class="sm:max-w-6xl" @confirm="savePage">
      <a-form :model="form" layout="vertical">
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <a-form-item label="页面标题" required>
            <FaInput v-model="form.title" />
          </a-form-item>
          <a-form-item label="页面路径" required>
            <FaInput v-model="form.slug" :disabled="!!editing" placeholder="about 或 docs/intro" />
          </a-form-item>
          <a-form-item label="状态">
            <FaSelect v-model="form.status" :options="statusOptions" />
          </a-form-item>
          <a-form-item label="SEO 标题">
            <FaInput v-model="form.seoTitle" />
          </a-form-item>
          <a-form-item label="摘要" class="md:col-span-2">
            <FaInput v-model="form.summary" />
          </a-form-item>
          <a-form-item label="SEO 描述" class="md:col-span-2">
            <FaInput v-model="form.seoDescription" />
          </a-form-item>
        </div>
        <div class="markdown-grid">
          <a-form-item label="Markdown 内容">
            <FaTextarea v-model="form.markdownContent" rows="18" input-class="font-mono" />
          </a-form-item>
          <section class="markdown-preview">
            <div class="section-title">
              预览
            </div>
            <div class="markdown-body" v-html="markdownPreview(form.markdownContent)" />
          </section>
        </div>
      </a-form>
    </FaModal>
  </div>
</template>

<style scoped>
.cms-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.cms-tabs button {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
  color: var(--color-text-2);
}

.cms-tabs button.active,
.cms-tabs button:hover {
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
}

.home-editor {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 420px);
  gap: 16px;
  align-items: start;
}

.home-preview {
  display: grid;
  gap: 12px;
}

.preview-hero {
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

.preview-hero h1 {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 800;
}

.preview-hero p {
  margin: 0;
}

.preview-sections {
  display: grid;
  gap: 8px;
}

.section-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.preview-section,
.markdown-preview {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.preview-section span {
  color: var(--color-text-3);
}

.table-actions {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}

.markdown-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 420px);
  gap: 16px;
}

.section-title {
  color: var(--color-text-1);
  font-weight: 700;
}

.markdown-body {
  min-height: 420px;
  overflow: auto;
  color: var(--color-text-1);
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 0 0 10px;
  font-weight: 800;
}

@media (max-width: 1000px) {
  .home-editor,
  .markdown-grid {
    grid-template-columns: 1fr;
  }
}
</style>

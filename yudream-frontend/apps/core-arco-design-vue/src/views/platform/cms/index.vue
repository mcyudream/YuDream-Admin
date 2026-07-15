<script setup lang="ts">
import type { CmsAgentSelectOption } from './config/cms-agent-options'
import type { FileObject } from '@/api/modules/files'
import type { CmsPage, CmsPagePayload, CmsTemplateContext, HomePageLayout, HomeSection, HomeSectionType, PageStatus, PageTemplate } from '@/api/modules/platform-cms'
import apiFiles from '@/api/modules/files'
import apiAgent from '@/api/modules/platform-agent'
import apiCms from '@/api/modules/platform-cms'
import { hasPublicWikiSpaces } from '@/api/modules/platform-wiki'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { readChromeCss } from '@/utils/cms-chrome'
import CmsBlockLibrary from './components/CmsBlockLibrary.vue'
import CmsGrapesEditor from './components/CmsGrapesEditor.vue'
import CmsMarkdownEditor from './components/CmsMarkdownEditor.vue'
import { toCmsAgentOptions } from './config/cms-agent-options'

type WorkbenchTab = 'pages' | 'home' | 'navigation' | 'media'
type EditorMode = 'builder' | 'markdown' | 'html'
type EditorTarget = 'page' | 'home'
type SiteLayoutMode = 'HEADER_FOOTER' | 'HEADER_COPYRIGHT' | 'ADMIN'
interface CmsNavigationItem {
  id: string
  label: string
  url: string
  parentId?: string
  visible: boolean
  sort: number
  children?: CmsNavigationItem[]
}

const toast = useFaToast()
const modal = useFaModal()
const appSettingsStore = useAppSettingsStore()
const appAccountStore = useAppAccountStore()

const activeTab = ref<WorkbenchTab>('pages')
const editorMode = ref<EditorMode>('builder')
const loading = ref(false)
const saving = ref(false)
const grapesEditorVisible = ref(false)
const blockLibraryVisible = ref(false)
const editorTarget = ref<EditorTarget>('page')
const pages = ref<CmsPage[]>([])
const navigationItems = ref<CmsNavigationItem[]>([])
const wikiEnabled = ref(false)
const templateContext = ref<CmsTemplateContext>(emptyTemplateContext())
const mediaItems = ref<FileObject[]>([])
const mediaInput = ref<HTMLInputElement>()
const aiAgentOptions = ref<CmsAgentSelectOption[]>([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const search = reactive({ keyword: '' })
const mediaSearch = reactive({ keyword: '', page: 1, size: 32, total: 0 })
const selectedPageId = ref<string | null>(null)
const showAllVariables = ref(false)

const pageForm = reactive<CmsPagePayload>({
  title: '',
  slug: '',
  summary: '',
  excerpt: '',
  coverImageUrl: '',
  categories: [],
  tags: [],
  markdownContent: '',
  htmlContent: '',
  cssContent: '',
  jsContent: '',
  builderProjectJson: '',
  seoTitle: '',
  seoDescription: '',
  template: 'DEFAULT',
  status: 'DRAFT',
})

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
const layoutOptions: { label: string, value: SiteLayoutMode }[] = [
  { label: 'Header + Footer', value: 'HEADER_FOOTER' },
  { label: 'Header + 版权', value: 'HEADER_COPYRIGHT' },
  { label: '后台管理布局', value: 'ADMIN' },
]
const sectionPresets: { type: HomeSectionType, label: string, icon: string }[] = [
  { type: 'HERO', label: '首屏', icon: 'i-ri:layout-top-line' },
  { type: 'FEATURE', label: '特色', icon: 'i-ri:sparkling-line' },
  { type: 'CONTENT', label: '内容', icon: 'i-ri:article-line' },
  { type: 'CTA', label: '行动', icon: 'i-ri:cursor-line' },
]

const selectedPage = computed(() => pages.value.find(item => item.id === selectedPageId.value) || null)
const aiEnabled = computed(() => aiAgentOptions.value.length > 0)
const pagePublicUrl = computed(() => pageForm.slug ? `/site/${pageForm.slug}` : '/site')
const previewNavigationItems = computed(() => {
  const items = navigationItems.value.filter(item => item.visible !== false)
  return wikiEnabled.value && !items.some(item => item.url === '/wiki')
    ? [...items, { id: 'capability-wiki', label: '知识库', url: '/wiki', visible: true, sort: Number.MAX_SAFE_INTEGER }]
    : items
})
const templatePreviewContext = computed(() => {
  const siteName = appSettingsStore.siteName || home.title || 'YuDream'
  const siteDescription = appSettingsStore.siteDescription || home.subtitle || ''
  const siteInfo = {
    name: siteName,
    description: siteDescription,
    logo: appSettingsStore.logo || '',
    currentYear: String(new Date().getFullYear()),
  }
  return {
    site: siteInfo,
    system: {
      ...siteInfo,
      copyright: {
        company: appSettingsStore.settings.app.copyright.company || '',
        website: appSettingsStore.settings.app.copyright.website || '',
        dates: appSettingsStore.settings.app.copyright.dates || siteInfo.currentYear,
      },
    },
    page: {
      title: editorTarget.value === 'page' ? pageForm.title : home.title,
      summary: editorTarget.value === 'page' ? pageForm.summary : home.subtitle,
      slug: editorTarget.value === 'page' ? pageForm.slug : '',
    },
    auth: {
      isLoggedIn: appAccountStore.isLogin ? 'true' : 'false',
      welcome: appAccountStore.isLogin ? `欢迎回来，${appAccountStore.account || '用户'}` : '欢迎访问',
    },
    user: {
      username: appAccountStore.account || 'guest',
      nickname: appAccountStore.account || '访客',
      avatar: appAccountStore.avatar || '',
    },
    route: { path: '/site', slug: '' },
    navigation: previewNavigationItems.value,
    chromeNavigation: buildNavigationTree(previewNavigationItems.value),
    navUsers: appAccountStore.isLogin
      ? [{ name: appAccountStore.account || '用户', avatar: appAccountStore.avatar || '', url: '/' }]
      : [{ name: '登录', avatar: appSettingsStore.logo || '', url: '/login' }],
    pages: templateContext.value.cms.pages.latest,
    cms: templateContext.value.cms,
    knowledge: templateContext.value.knowledge,
    categories: [],
    tags: [],
  }
})
const builderContentStatus = computed(() => pageForm.builderProjectJson ? '已有 GrapesJS 源数据' : pageForm.htmlContent ? '已有 HTML 内容' : '尚未生成可视化内容')
const homeHtml = computed({
  get: () => home.settings?.homeHtml || '',
  set: (value: string) => {
    home.settings = {
      ...(home.settings || {}),
      homeHtml: value,
    }
  },
})
const homeCss = computed({
  get: () => [
    home.settings?.homeCss,
    readChromeCss(home.settings, 'header'),
    readChromeCss(home.settings, 'footer'),
  ].filter(Boolean).join('\n'),
  set: (value: string) => {
    home.settings = {
      ...(home.settings || {}),
      homeCss: value,
    }
    delete home.settings.chromeHeaderCss
    delete home.settings.chromeFooterCss
  },
})
const homeJs = computed({
  get: () => home.settings?.homeJs || '',
  set: (value: string) => {
    home.settings = {
      ...(home.settings || {}),
      homeJs: value,
    }
  },
})
const homeProjectJson = computed({
  get: () => home.settings?.homeProjectJson || '',
  set: (value: string) => {
    home.settings = {
      ...(home.settings || {}),
      homeProjectJson: value,
    }
  },
})
const homeLayoutMode = computed<SiteLayoutMode>({
  get: () => (home.settings?.siteLayout as SiteLayoutMode) || 'HEADER_FOOTER',
  set: (value) => {
    home.settings = {
      ...(home.settings || {}),
      siteLayout: value,
    }
  },
})
const rootNavigationItems = computed(() => [...navigationItems.value].filter(item => !item.parentId).sort((a, b) => a.sort - b.sort))
const homeBuilderContentStatus = computed(() => homeProjectJson.value ? '已有 GrapesJS 首页源数据' : homeHtml.value ? '已有动态首页内容' : '尚未生成动态首页内容')
const visibleCmsVariables = computed(() => showAllVariables.value ? cmsVariables : cmsVariables.slice(0, 8))
const cmsVariables = [
  { key: '{{cms.pages.latest.count}}', label: '最新 CMS 页面数' },
  { key: '{{knowledge.spaces.count}}', label: '公开知识库数' },
  { key: '{{knowledge.pages.count}}', label: '已发布知识库页面数' },
  { key: '{{site.name}}', label: '站点名称' },
  { key: '{{site.description}}', label: '站点描述' },
  { key: '{{site.logo}}', label: '站点 Logo' },
  { key: '{{site.favicon}}', label: '站点图标' },
  { key: '{{site.loginBanner}}', label: '登录页 Banner' },
  { key: '{{site.currentYear}}', label: '当前年份' },
  { key: '{{system.name}}', label: '系统名称' },
  { key: '{{system.description}}', label: '系统描述' },
  { key: '{{system.logo}}', label: '系统 Logo' },
  { key: '{{system.currentYear}}', label: '系统当前年份' },
  { key: '{{system.copyright.company}}', label: '版权公司' },
  { key: '{{system.copyright.website}}', label: '版权网站' },
  { key: '{{system.copyright.dates}}', label: '版权年份' },
  { key: '{{page.title}}', label: '页面标题' },
  { key: '{{page.slug}}', label: '页面路径' },
  { key: '{{page.categories}}', label: '页面分类' },
  { key: '{{page.tags}}', label: '页面标签' },
  { key: '{{auth.isLoggedIn}}', label: '是否登录' },
  { key: '{{auth.welcome}}', label: '登录欢迎语' },
  { key: '{{user.username}}', label: '用户名' },
  { key: '{{user.nickname}}', label: '昵称' },
  { key: '{{user.avatar}}', label: '头像地址' },
  { key: '{{route.path}}', label: '当前路径' },
  { key: '{{route.slug}}', label: '当前 Slug' },
  { key: '{{navigation.count}}', label: '导航数量' },
  { key: '{{navUsers.count}}', label: '头像用户数' },
  { key: '{{pages.count}}', label: '公开页面数' },
  { key: '{{categories.count}}', label: '分类数量' },
  { key: '{{tags.count}}', label: '标签数量' },
  { key: '{{archive.title}}', label: '归档标题' },
  { key: '{{archive.category}}', label: '当前分类' },
  { key: '{{archive.tag}}', label: '当前标签' },
  { key: '{{archive.keyword}}', label: '当前搜索词' },
  { key: 'data-visible-when="guest"', label: '仅游客可见' },
  { key: 'data-visible-when="logged-in"', label: '仅登录可见' },
  { key: 'data-yb-repeat="navigation"', label: '循环导航' },
  { key: 'data-yb-repeat="navUsers"', label: '循环头像用户' },
  { key: 'data-yb-repeat="pages"', label: '循环公开页面' },
  { key: 'data-yb-repeat="categories"', label: '循环分类' },
  { key: 'data-yb-repeat="tags"', label: '循环标签' },
  { key: 'data-yb-repeat="cms.pages.latest"', label: '循环最新 CMS 页面' },
  { key: 'data-yb-repeat="knowledge.pages"', label: '循环知识库页面' },
  { key: 'data-yb-repeat="knowledge.latest"', label: '循环最新知识库内容' },
  { key: 'data-yb-repeat="knowledge.spaces"', label: '循环公开知识库' },
]

watch(activeTab, async () => {
  if (activeTab.value === 'pages') {
    await loadPages()
  }
  else if (activeTab.value === 'media') {
    await loadMedia()
  }
  else {
    await loadHome()
  }
})

onMounted(async () => {
  await Promise.all([loadAiApplications(), loadWikiNavigation()])
  await loadPages()
  await loadTemplatePreviewContext()
})

async function loadAiApplications() {
  try {
    const res = await apiAgent.available()
    aiAgentOptions.value = toCmsAgentOptions(res.data)
  }
  catch {
    aiAgentOptions.value = []
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
    pages.value = res.data.records
    pagination.total = res.data.total
    if (!selectedPageId.value && pages.value.length) {
      selectPage(pages.value[0])
    }
    else if (selectedPageId.value && !pages.value.some(item => item.id === selectedPageId.value)) {
      resetPageForm()
    }
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '页面列表加载失败')
    pages.value = []
    pagination.total = 0
    if (!selectedPageId.value) {
      resetPageForm()
    }
  }
  finally {
    loading.value = false
  }
}

async function loadTemplatePreviewContext() {
  try {
    templateContext.value = (await apiCms.publicTemplateContext()).data
  }
  catch {
    templateContext.value = emptyTemplateContext()
  }
}

async function loadWikiNavigation() {
  wikiEnabled.value = await hasPublicWikiSpaces()
}

function emptyTemplateContext(): CmsTemplateContext {
  return {
    cms: { pages: { latest: [] } },
    knowledge: { spaces: [], pages: [], latest: [] },
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
    navigationItems.value = parseNavigationItems(res.data.settings?.navigationJson)
    await Promise.all([loadTemplatePreviewContext(), loadWikiNavigation()])
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '首页配置加载失败')
  }
  finally {
    loading.value = false
  }
}

async function loadMedia() {
  loading.value = true
  try {
    const res = await apiFiles.page({
      page: mediaSearch.page,
      size: mediaSearch.size,
      keyword: mediaSearch.keyword || undefined,
      module: 'cms',
      publicAccess: true,
    })
    mediaItems.value = res.data.records
    mediaSearch.total = res.data.total
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '媒体库加载失败')
    mediaItems.value = []
    mediaSearch.total = 0
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
    categories: page.categories || [],
    tags: page.tags || [],
    markdownContent: page.markdownContent || '',
    htmlContent: page.htmlContent || '',
    cssContent: page.cssContent || '',
    jsContent: page.jsContent || '',
    builderProjectJson: page.builderProjectJson || '',
    seoTitle: page.seoTitle || '',
    seoDescription: page.seoDescription || '',
    template: page.template || 'DEFAULT',
    status: page.status || 'DRAFT',
  })
  editorMode.value = page.builderProjectJson ? 'builder' : page.htmlContent ? 'html' : 'builder'
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
  loading.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    data.append('module', 'cms')
    data.append('publicAccess', 'true')
    await apiFiles.upload(data)
    toast.success('媒体已上传')
    await loadMedia()
  }
  finally {
    loading.value = false
  }
}

async function copyMediaUrl(item: FileObject) {
  const url = toBackendAssetUrl(item.url)
  await navigator.clipboard?.writeText(url)
  toast.success('媒体地址已复制')
}

function useMediaAsCover(item: FileObject) {
  pageForm.coverImageUrl = toBackendAssetUrl(item.url)
  activeTab.value = 'pages'
  toast.success('已设为当前页面封面')
}

function splitTerms(value?: string[]) {
  return value?.join(', ') || ''
}

function updateTerms(key: 'categories' | 'tags', value: string) {
  pageForm[key] = value
    .split(/[,，\n\r]/)
    .map(item => item.trim())
    .filter(Boolean)
}

function mediaUrl(item: FileObject) {
  return toBackendAssetUrl(item.url)
}

function formatFileSize(size?: number) {
  if (!size) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function createPage() {
  selectedPageId.value = null
  resetPageForm()
  editorMode.value = 'builder'
}

function resetPageForm() {
  Object.assign(pageForm, {
    title: '新页面',
    slug: `page-${Date.now()}`,
    summary: '',
    excerpt: '',
    coverImageUrl: '',
    categories: [],
    tags: [],
    markdownContent: '# 新页面\n\n在这里编写 Markdown 内容。',
    htmlContent: '',
    cssContent: '',
    jsContent: '',
    builderProjectJson: '',
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
    const payload = { ...pageForm }
    payload.htmlContent = resolvePageHtmlForSave()
    payload.jsContent = stripScriptTags(pageForm.jsContent || '')
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

function confirmDeletePage(page: CmsPage = selectedPage.value!) {
  if (!page) {
    return
  }
  modal.confirm({
    title: '删除页面',
    content: `确认删除「${page.title}」吗？删除后公开页将无法访问。`,
    onConfirm: async () => {
      await apiCms.deletePage(page.id)
      toast.success('页面已删除')
      if (selectedPageId.value === page.id) {
        selectedPageId.value = null
        resetPageForm()
      }
      await loadPages()
    },
  })
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

async function persistHome(successMessage = '首页已保存') {
  saving.value = true
  try {
    homeHtml.value = resolveHomeHtmlForSave()
    homeJs.value = stripScriptTags(homeJs.value)
    const currentHomeCss = homeCss.value
    home.settings = {
      ...(home.settings || {}),
      homeCss: currentHomeCss,
      navigationJson: JSON.stringify(navigationItems.value),
    }
    delete home.settings.chromeHeaderCss
    delete home.settings.chromeFooterCss
    await apiCms.saveHome({
      ...home,
      sections: home.sections,
      settings: home.settings || {},
    })
    toast.success(successMessage)
  }
  finally {
    saving.value = false
  }
}

async function saveHome() {
  await persistHome()
}

function confirmToggleHomePublish() {
  const nextPublished = !home.published
  modal.confirm({
    title: nextPublished ? '发布首页' : '取消发布首页',
    content: nextPublished
      ? '发布后公开站点首页将可以访问，确认发布首页吗？'
      : '取消发布后公开站点首页将显示“首页未发布”，确认取消发布吗？',
    onConfirm: async () => {
      const previous = home.published
      home.published = nextPublished
      try {
        await persistHome(nextPublished ? '首页已发布' : '已取消发布首页')
      }
      catch (error) {
        home.published = previous
        throw error
      }
    },
  })
}

function openGrapesEditor(target: EditorTarget = 'page') {
  editorTarget.value = target
  grapesEditorVisible.value = true
}

async function saveGrapesEditor(payload: { htmlContent: string, cssContent: string, jsContent: string, builderProjectJson: string }) {
  grapesEditorVisible.value = false
  await nextTick()
  if (editorTarget.value === 'home') {
    homeHtml.value = payload.htmlContent
    homeCss.value = payload.cssContent
    homeJs.value = payload.jsContent
    homeProjectJson.value = payload.builderProjectJson
    await saveHome()
    return
  }
  pageForm.htmlContent = payload.htmlContent
  pageForm.cssContent = payload.cssContent
  pageForm.jsContent = payload.jsContent
  pageForm.builderProjectJson = payload.builderProjectJson
  editorMode.value = 'builder'
  await savePage()
}

function resolvePageHtmlForSave() {
  if (editorMode.value === 'html') {
    return stripLayoutBlocks(pageForm.htmlContent || '')
  }
  const existingHtml = pageForm.htmlContent?.trim()
  if (existingHtml) {
    return stripLayoutBlocks(existingHtml)
  }
  return editorMode.value === 'markdown' ? '' : stripLayoutBlocks(pageForm.htmlContent || '')
}

function resolveHomeHtmlForSave() {
  return stripLayoutBlocks(homeHtml.value?.trim() || '')
}

function stripLayoutBlocks(value?: string) {
  if (!value) {
    return ''
  }
  const doc = new DOMParser().parseFromString(`<div>${value}</div>`, 'text/html')
  doc.querySelectorAll('[data-yb-system-nav]').forEach(el => el.remove())
  return doc.body.firstElementChild?.innerHTML || value
}

function stripScriptTags(value?: string) {
  return String(value || '')
    .replace(/<script[^>]*>/gi, '')
    .replace(/<\/script>/gi, '')
    .trim()
}

function insertVariable(variable: string) {
  if (activeTab.value === 'home') {
    homeHtml.value = `${homeHtml.value || emptyHomeBuilderHtml()}\n${variable}`
  }
  else if (editorMode.value === 'markdown') {
    pageForm.markdownContent = `${pageForm.markdownContent || ''}\n${variable}`
  }
  else {
    pageForm.htmlContent = `${pageForm.htmlContent || emptyBuilderHtml()}\n${variable}`
  }
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

function parseNavigationItems(value?: string): CmsNavigationItem[] {
  if (!value) {
    return [
      { id: 'home', label: '首页', url: '/site', visible: true, sort: 1 },
      { id: 'login', label: '登录', url: '/login', visible: true, sort: 2 },
    ]
  }
  try {
    const parsed = JSON.parse(value) as CmsNavigationItem[]
    return Array.isArray(parsed) ? parsed : []
  }
  catch {
    return []
  }
}

function buildNavigationTree(items: CmsNavigationItem[]) {
  const itemMap = new Map<string, CmsNavigationItem>()
  const roots: CmsNavigationItem[] = []
  items.forEach((item) => {
    itemMap.set(item.id, { ...item, children: [] })
  })
  items.forEach((item) => {
    const current = itemMap.get(item.id)
    if (!current) {
      return
    }
    const parent = item.parentId ? itemMap.get(item.parentId) : undefined
    if (parent) {
      parent.children = [...(parent.children || []), current]
    }
    else {
      roots.push(current)
    }
  })
  const sortItems = (list: CmsNavigationItem[]) => {
    list.sort((a, b) => a.sort - b.sort)
    list.forEach(item => sortItems(item.children || []))
    return list
  }
  return sortItems(roots)
}

function emptyBuilderHtml() {
  return '<main class="yb-empty"><section><h1>打开 GrapesJS 构建器开始设计页面</h1><p>发布后会保存 HTML、CSS 和可继续编辑的 Project JSON。</p></section></main>'
}

function emptyHomeBuilderHtml() {
  return '<main class="yb-empty"><section><h1>打开 GrapesJS 构建器设计首页</h1><p>首页可以自由编排首屏、图文、CTA、表单和动态内容。</p></section></main>'
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '未发布'
}

function sectionTitle(type: HomeSectionType) {
  return {
    HERO: '首页首屏',
    FEATURE: '核心特色',
    CONTENT: '内容区块',
    CTA: '行动引导',
  }[type]
}
</script>

<template>
  <div class="cms-workbench">
    <FaPageHeader title="内容站点" class="cms-header">
      <FaButton v-if="activeTab === 'pages'" v-auth="'platform:cms:edit'" :loading="saving" @click="savePage">
        <FaIcon name="i-ri:save-3-line" />
        保存页面
      </FaButton>
      <FaButton v-else-if="activeTab === 'home'" v-auth="'platform:cms:edit'" :loading="saving" @click="saveHome">
        <FaIcon name="i-ri:save-3-line" />
        保存首页
      </FaButton>
      <FaButton v-if="activeTab === 'home'" v-auth="'platform:cms:edit'" :variant="home.published ? 'outline' : 'default'" :loading="saving" @click="confirmToggleHomePublish">
        <FaIcon :name="home.published ? 'i-ri:inbox-unarchive-line' : 'i-ri:send-plane-line'" />
        {{ home.published ? '取消发布首页' : '发布首页' }}
      </FaButton>
      <FaButton v-if="activeTab === 'media'" v-auth="'platform:cms:edit'" :loading="loading" @click="pickMedia">
        <FaIcon name="i-ri:upload-cloud-2-line" />
        上传媒体
      </FaButton>
      <input ref="mediaInput" type="file" accept="image/*" hidden @change="uploadMedia">
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
        <button type="button" :class="{ active: activeTab === 'navigation' }" @click="activeTab = 'navigation'">
          <FaIcon name="i-ri:menu-search-line" />
          导航菜单
        </button>
        <button type="button" :class="{ active: activeTab === 'media' }" @click="activeTab = 'media'">
          <FaIcon name="i-ri:image-2-line" />
          媒体库
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
            <FaButton v-auth="'platform:cms:edit'" variant="outline" @click="blockLibraryVisible = true">
              <FaIcon name="i-ri:building-2-line" />
              区块库
            </FaButton>
          </div>
          <div v-loading="loading" class="page-list__items">
            <article v-for="item in pages" :key="item.id" class="page-list-card" :class="{ active: item.id === selectedPageId }">
              <button type="button" class="page-list-card__main" @click="selectPage(item)">
                <span class="page-list-card__title">{{ item.title }}</span>
                <span class="page-list-card__path">/site/{{ item.slug }}</span>
                <span class="page-list-card__meta">
                  <FaTag :variant="item.status === 'PUBLISHED' ? 'default' : 'secondary'">
                    {{ item.status === 'PUBLISHED' ? '已发布' : '草稿' }}
                  </FaTag>
                  <span>{{ dateText(item.publishedAt || item.updateTime || item.createTime) || '未发布' }}</span>
                </span>
              </button>
              <div class="page-list-card__actions">
                <a :href="`/site/${item.slug}`" target="_blank" rel="noopener noreferrer" title="预览">
                  <FaIcon name="i-ri:external-link-line" />
                </a>
                <button v-auth="'platform:cms:delete'" type="button" title="删除" @click.stop="confirmDeletePage(item)">
                  <FaIcon name="i-ri:delete-bin-line" />
                </button>
              </div>
            </article>
            <div v-if="!pages.length" class="empty-state">
              暂无页面
            </div>
          </div>
        </aside>

        <main class="page-editor">
          <div class="editor-top">
            <FaInput v-model="pageForm.title" class="title-input" />
            <div class="editor-mode">
              <button type="button" :class="{ active: editorMode === 'builder' }" @click="editorMode = 'builder'">可视化</button>
              <button type="button" :class="{ active: editorMode === 'markdown' }" @click="editorMode = 'markdown'">Markdown</button>
              <button type="button" :class="{ active: editorMode === 'html' }" @click="editorMode = 'html'">HTML</button>
            </div>
          </div>

          <section v-if="editorMode === 'builder'" class="builder-entry">
            <div>
              <span class="builder-entry__status">{{ builderContentStatus }}</span>
              <h2>可视化页面构建器</h2>
              <p>使用 GrapesJS 设计页面，发布时会保存 HTML、CSS、JS 和可继续编辑的 Project JSON。</p>
            </div>
            <div class="builder-entry__actions">
              <FaButton v-auth="'platform:cms:edit'" @click="openGrapesEditor('page')">
                <FaIcon name="i-ri:drag-drop-line" />
                打开构建器
              </FaButton>
              <FaButton variant="outline" @click="editorMode = 'html'">
                <FaIcon name="i-ri:code-s-slash-line" />
                查看 HTML
              </FaButton>
            </div>
          </section>

          <CmsMarkdownEditor v-else-if="editorMode === 'markdown'" v-model="pageForm.markdownContent" />
          <FaTextarea v-else v-model="pageForm.htmlContent" rows="22" input-class="font-mono" placeholder="<main>...</main>" />
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
              <a-form-item label="分类">
                <FaInput
                  :model-value="splitTerms(pageForm.categories)"
                  placeholder="新闻, 产品, 案例"
                  @update:model-value="value => value && updateTerms('categories', value)"
                />
              </a-form-item>
              <a-form-item label="标签">
                <FaInput
                  :model-value="splitTerms(pageForm.tags)"
                  placeholder="低代码, CMS, 首页"
                  @update:model-value="value => value && updateTerms('tags', value)"
                />
              </a-form-item>
            </a-form>
            <div class="term-chips">
              <FaTag v-for="item in pageForm.categories" :key="`cat-${item}`" variant="secondary">
                {{ item }}
              </FaTag>
              <FaTag v-for="item in pageForm.tags" :key="`tag-${item}`">
                #{{ item }}
              </FaTag>
            </div>
            <div class="publish-actions">
              <FaButton v-auth="'platform:cms:publish'" variant="outline" @click="confirmPublish">
                {{ selectedPage?.status === 'PUBLISHED' ? '取消发布' : '发布' }}
              </FaButton>
              <FaButton v-if="selectedPage" v-auth="'platform:cms:delete'" variant="ghost" @click="confirmDeletePage()">
                <FaIcon name="i-ri:delete-bin-line" />
                删除
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
            <h3>页面 JS</h3>
            <FaTextarea
              v-model="pageForm.jsContent"
              rows="8"
              input-class="font-mono"
              placeholder="document.querySelector(...)"
            />
          </section>

          <section>
            <div class="panel-title-row">
              <h3>动态变量</h3>
              <button type="button" @click="showAllVariables = !showAllVariables">
                {{ showAllVariables ? '收起' : `全部 ${cmsVariables.length}` }}
              </button>
            </div>
            <div class="variable-list compact">
              <button v-for="item in visibleCmsVariables" :key="item.key" type="button" @click="insertVariable(item.key)">
                <strong>{{ item.key }}</strong>
                <span>{{ item.label }}</span>
              </button>
            </div>
          </section>

        </aside>
      </section>

      <section v-else-if="activeTab === 'home'" v-loading="loading" class="home-layout">
        <main class="home-form">
          <section class="builder-entry">
            <div>
              <span class="builder-entry__status">{{ homeBuilderContentStatus }}</span>
              <h2>动态首页构建器</h2>
              <p>画布会同时展示固定 Header、首页主体和固定 Footer；首页主体可自由组合内容，Header/Footer 只开放样式调整。</p>
            </div>
            <div class="builder-entry__actions">
              <FaButton v-auth="'platform:cms:edit'" @click="openGrapesEditor('home')">
                <FaIcon name="i-ri:layout-grid-line" />
                打开首页构建器
              </FaButton>
              <FaButton variant="outline" @click="homeHtml = ''; homeCss = ''; homeJs = ''; homeProjectJson = ''">
                <FaIcon name="i-ri:eraser-line" />
                清空动态内容
              </FaButton>
            </div>
          </section>

          <section class="legacy-sections">
            <div class="legacy-sections__head">
              <div>
                <h3>兼容区块</h3>
                <p>没有动态首页内容时，公开站点会回退渲染这些旧区块。</p>
              </div>
              <div class="block-toolbar">
                <FaButton v-for="preset in sectionPresets" :key="preset.type" size="sm" variant="outline" @click="addHomeSection(preset.type)">
                  <FaIcon :name="preset.icon" />
                  {{ preset.label }}
                </FaButton>
              </div>
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
          </section>
        </main>

        <aside class="home-preview">
          <section>
            <h3>首页基础信息</h3>
            <a-form :model="home" layout="vertical">
              <a-form-item label="首页标题">
                <FaInput v-model="home.title" />
              </a-form-item>
              <a-form-item label="主题">
                <FaInput v-model="home.theme" />
              </a-form-item>
              <a-form-item label="站点布局">
                <FaSelect v-model="homeLayoutMode" :options="layoutOptions" />
              </a-form-item>
              <a-form-item label="副标题">
                <FaInput v-model="home.subtitle" />
              </a-form-item>
              <a-form-item label="首屏图片">
                <FaInput v-model="home.heroImageUrl" />
              </a-form-item>
              <a-form-item label="发布首页">
                <div class="home-publish-field">
                  <FaSwitch v-model="home.published" />
                  <FaTag :variant="home.published ? 'default' : 'secondary'">
                    {{ home.published ? '已发布' : '未发布' }}
                  </FaTag>
                </div>
              </a-form-item>
            </a-form>
          </section>
        </aside>
      </section>

      <section v-else-if="activeTab === 'navigation'" v-loading="loading" class="navigation-layout">
        <main class="navigation-panel">
          <div class="legacy-sections__head">
            <div>
              <h3>站点导航</h3>
              <p>导航由系统数据和公开站点上下文提供，此处只读，不能删除、改名、改 URL 或调整菜单层级。</p>
            </div>
          </div>
          <article v-for="item in navigationItems" :key="item.id" class="builder-block">
            <div class="builder-block__head">
              <div class="navigation-item-title">
                <FaTag variant="secondary">{{ item.parentId ? '二级菜单' : '一级菜单' }}</FaTag>
                <span>{{ item.label || '未命名菜单' }}</span>
              </div>
              <div class="navigation-item-actions">
                <FaTag variant="secondary">固定数据</FaTag>
              </div>
            </div>
            <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
              <FaInput :model-value="item.label" disabled placeholder="菜单名称" />
              <FaInput :model-value="item.url" disabled placeholder="/site/about 或 https://..." />
              <label class="cms-field">
                <span>上级菜单</span>
                <select :value="item.parentId" disabled class="cms-native-select">
                  <option value="">一级菜单</option>
                  <option v-for="parent in rootNavigationItems.filter(parent => parent.id !== item.id)" :key="parent.id" :value="parent.id">
                    {{ parent.label }}
                  </option>
                </select>
              </label>
              <FaInput :model-value="item.sort" disabled type="number" placeholder="排序" />
              <label class="inline-flex items-center gap-2 text-sm">
                <FaSwitch :model-value="item.visible" disabled />
                显示菜单
              </label>
            </div>
          </article>
        </main>
        <aside class="home-preview">
          <section>
            <h3>导航预览</h3>
            <nav class="cms-nav-preview">
              <div v-for="item in rootNavigationItems.filter(nav => nav.visible)" :key="item.id" class="cms-nav-preview__item">
                <a :href="item.url">{{ item.label }}</a>
                <div v-if="navigationItems.some(child => child.parentId === item.id && child.visible)" class="cms-nav-preview__children">
                  <a v-for="child in navigationItems.filter(child => child.parentId === item.id && child.visible).sort((a, b) => a.sort - b.sort)" :key="child.id" :href="child.url">
                    {{ child.label }}
                  </a>
                </div>
              </div>
            </nav>
          </section>
          <section>
            <h3>使用说明</h3>
            <p class="cms-help-text">
              导航数据只读；首页构建器中的 Header/Footer 结构、菜单层级、Logo、认证入口和数据绑定均由系统固定，用户与 AI 只能修改整体画布 CSS。
            </p>
          </section>
        </aside>
      </section>

      <section v-else-if="activeTab === 'media'" v-loading="loading" class="media-workspace">
        <div class="media-toolbar-main">
          <div>
            <h3>媒体库</h3>
            <p>用于 CMS 构建器、首页和文章封面的图片资产，上传后会进入 RustFS/S3 对象存储。</p>
          </div>
          <div class="media-actions">
            <FaInput v-model="mediaSearch.keyword" clearable placeholder="搜索文件名" class="w-64" @keydown.enter="loadMedia" @clear="loadMedia" />
            <FaButton v-auth="'platform:cms:edit'" :loading="loading" @click="pickMedia">
              <FaIcon name="i-ri:upload-cloud-2-line" />
              上传
            </FaButton>
          </div>
        </div>

        <div v-if="mediaItems.length" class="media-grid">
          <article v-for="item in mediaItems" :key="item.id" class="media-card">
            <div class="media-card__preview">
              <img :src="mediaUrl(item)" :alt="item.originalName || 'CMS 媒体'">
              <div class="media-card__overlay">
                <div class="media-card__actions">
                  <FaButton size="sm" variant="outline" @click="copyMediaUrl(item)">
                    <FaIcon name="i-ri:file-copy-line" />
                    复制地址
                  </FaButton>
                  <FaButton size="sm" @click="useMediaAsCover(item)">
                    <FaIcon name="i-ri:image-edit-line" />
                    设为封面
                  </FaButton>
                </div>
              </div>
            </div>
            <div class="media-card__body">
              <strong>{{ item.originalName || `文件 #${item.id}` }}</strong>
              <span>{{ item.contentType || 'image/*' }} · {{ formatFileSize(item.size) }}</span>
              <small>{{ dateText(item.createTime) }}</small>
            </div>
          </article>
        </div>
        <div v-else class="empty-state media-empty">
          暂无媒体，点击上传添加第一张 CMS 图片。
        </div>
      </section>
    </FaPageMain>

    <div v-if="grapesEditorVisible" class="grapes-editor-shell">
      <CmsGrapesEditor
        :title="editorTarget === 'home' ? home.title : pageForm.title"
        :html-content="editorTarget === 'home' ? homeHtml : pageForm.htmlContent"
        :css-content="editorTarget === 'home' ? homeCss : pageForm.cssContent"
        :js-content="editorTarget === 'home' ? homeJs : pageForm.jsContent"
        :builder-project-json="editorTarget === 'home' ? homeProjectJson : pageForm.builderProjectJson"
        :chrome-frame="editorTarget === 'home'"
        :chrome-layout="editorTarget === 'home' ? homeLayoutMode : undefined"
        :template-preview-context="templatePreviewContext"
        :ai-enabled="aiEnabled"
        :ai-agent-options="aiAgentOptions"
        :history-target-type="editorTarget"
        :history-target-id="editorTarget === 'home' ? 'home' : (selectedPageId || pageForm.slug || pageForm.title || 'draft')"
        :history-target-label="editorTarget === 'home' ? home.title : pageForm.title"
        @close="grapesEditorVisible = false"
        @save="saveGrapesEditor"
      />
    </div>

    <CmsBlockLibrary v-model:visible="blockLibraryVisible" />
  </div>
</template>

<style scoped>
.cms-tabs,
.page-list__toolbar,
.editor-top,
.editor-mode,
.block-toolbar,
.publish-actions,
.home-publish-field,
.builder-block__head,
.builder-entry__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.block-toolbar {
  align-items: stretch;
  flex-wrap: wrap;
}

.block-toolbar :deep([data-slot="button"]) {
  min-width: 84px;
  justify-content: center;
  padding: 8px 14px;
  border-radius: 8px;
  font-weight: 700;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.block-toolbar :deep([data-slot="button"]:hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.06);
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
  grid-template-columns: 320px minmax(0, 1fr) 300px;
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

.page-list {
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.page-list__toolbar {
  align-items: stretch;
}

.page-list__items {
  display: grid;
  gap: 6px;
  margin-top: 12px;
  max-height: calc(100vh - 270px);
  overflow: auto;
}

.page-list-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-1);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.page-list-card:hover {
  border-color: var(--color-border-3);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.06);
  transform: translateY(-1px);
}

.page-list-card.active {
  border-color: rgb(var(--primary-6));
  box-shadow: inset 3px 0 0 rgb(var(--primary-6)), 0 4px 12px rgba(var(--primary-6), 0.08);
}

.page-list-card__main {
  display: grid;
  min-width: 0;
  gap: 5px;
  color: var(--color-text-1);
  text-align: left;
}

.page-list-card__title,
.page-list-card__path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-list-card__title {
  font-weight: 800;
}

.page-list-card__path,
.page-list-card__meta {
  color: var(--color-text-3);
  font-size: 12px;
}

.page-list-card__meta,
.page-list-card__actions {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.page-list-card__actions {
  align-self: start;
}

.page-list-card__actions a,
.page-list-card__actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: var(--color-text-3);
  text-decoration: none;
}

.page-list-card__actions a:hover,
.page-list-card__actions button:hover {
  background: var(--color-fill-2);
  color: rgb(var(--primary-6));
}

.page-list-card__meta :deep([data-slot="tag"]) {
  font-weight: 700;
}

.page-list-card__meta :deep([data-slot="tag"].bg-primary) {
  background: rgb(var(--success-6));
  border-color: rgb(var(--success-6));
  color: #fff;
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

.builder-entry,
.home-form,
.legacy-sections {
  display: grid;
  gap: 16px;
}

.builder-entry {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 22px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-2);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  transition: box-shadow 0.18s ease;
}

.builder-entry:hover {
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.05);
}

.builder-entry h2,
.builder-entry p {
  margin: 0;
}

.builder-entry h2 {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 800;
}

.builder-entry p {
  color: var(--color-text-2);
}

.builder-entry__status {
  color: rgb(var(--primary-6));
  font-size: 12px;
  font-weight: 800;
}

.builder-entry__actions,
.block-toolbar {
  flex-wrap: wrap;
}

.builder-block,
.publish-panel section {
  display: grid;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-2);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  transition: box-shadow 0.18s ease, border-color 0.18s ease;
}

.builder-block:hover {
  border-color: var(--color-border-3);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.05);
}

.legacy-sections {
  display: grid;
  gap: 18px;
  padding: 20px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-2);
}

.legacy-sections__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.legacy-sections__head h3,
.legacy-sections__head p {
  margin: 0;
}

.legacy-sections__head p {
  margin-top: 4px;
  color: var(--color-text-3);
  font-size: 13px;
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

.panel-title-row {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.panel-title-row button {
  color: rgb(var(--primary-6));
  font-size: 12px;
}

.publish-actions {
  justify-content: space-between;
  flex-wrap: wrap;
}

.publish-actions a {
  color: rgb(var(--primary-6));
  text-decoration: none;
}

.term-chips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  min-height: 24px;
  margin-bottom: 12px;
}

.variable-list {
  display: grid;
  gap: 8px;
}

.variable-list.compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.variable-list button {
  display: grid;
  gap: 2px;
  min-width: 0;
  padding: 7px 8px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
  color: var(--color-text-2);
  text-align: left;
}

.variable-list button:hover {
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
}

.variable-list strong,
.variable-list span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.variable-list strong {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
}

.variable-list span {
  color: var(--color-text-3);
  font-size: 11px;
}

.home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 16px;
  align-items: start;
}

.navigation-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 16px;
  align-items: start;
}

.navigation-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.navigation-item-title,
.navigation-item-actions {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.cms-field {
  display: grid;
  gap: 6px;
  color: var(--color-text-2);
  font-size: 13px;
}

.cms-native-select {
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
  color: var(--color-text-1);
  outline: none;
}

.cms-native-select:focus {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 0 0 2px rgba(var(--primary-6), 0.12);
}

.footer-settings {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.footer-settings h3,
.footer-settings p {
  margin: 0;
}

.footer-settings p {
  margin-top: 4px;
  color: var(--color-text-3);
}

.cms-nav-preview {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.cms-nav-preview__item {
  display: grid;
  gap: 6px;
}

.cms-nav-preview a {
  display: inline-flex;
  padding: 8px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  color: var(--color-text-2);
  text-decoration: none;
}

.cms-nav-preview__children {
  display: grid;
  gap: 4px;
  padding-left: 12px;
}

.cms-nav-preview__children a {
  padding: 6px 10px;
  border-style: dashed;
  font-size: 12px;
}

.cms-footer-preview {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-fill-1);
}

.cms-footer-preview p,
.cms-footer-preview small {
  margin: 0;
  color: var(--color-text-3);
}

.cms-help-text {
  margin: 0;
  color: var(--color-text-3);
  line-height: 1.6;
}

.media-workspace {
  display: grid;
  gap: 16px;
}

.media-toolbar-main {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.media-toolbar-main h3,
.media-toolbar-main p {
  margin: 0;
}

.media-toolbar-main p {
  margin-top: 4px;
  color: var(--color-text-3);
}

.media-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.media-card {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
  transition: box-shadow 0.18s ease, border-color 0.18s ease;
}

.media-card:hover {
  border-color: var(--color-border-3);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.06);
}

.media-card__preview {
  position: relative;
  overflow: hidden;
  aspect-ratio: 4 / 3;
  border-radius: 6px;
  background: var(--color-fill-2);
}

.media-card__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.25s ease;
}

.media-card:hover .media-card__preview img {
  transform: scale(1.04);
}

.media-card__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.2s ease;
  pointer-events: none;
}

.media-card:hover .media-card__overlay {
  opacity: 1;
  pointer-events: auto;
}

.media-card__overlay :deep([data-slot="button"]) {
  background: rgba(255, 255, 255, 0.92);
  border-color: transparent;
  color: var(--color-text-1);
}

.media-card__overlay :deep([data-slot="button"]:hover) {
  background: #fff;
}

.media-card__body {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.media-card__body strong,
.media-card__body span,
.media-card__body small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-card__body span,
.media-card__body small {
  color: var(--color-text-3);
  font-size: 12px;
}

.media-card__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.media-empty {
  border: 1px dashed var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.home-preview {
  display: grid;
  gap: 14px;
}

.home-preview section {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-2);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.home-preview h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 800;
}

.empty-state {
  padding: 24px;
  color: var(--color-text-3);
  text-align: center;
}

.grapes-editor-shell {
  position: fixed;
  inset: 0;
  z-index: 5000;
  overflow: hidden;
  background: var(--color-bg-1);
}

@media (max-width: 1180px) {
  .cms-layout,
  .home-layout,
  .navigation-layout {
    grid-template-columns: 1fr;
  }

  .media-toolbar-main {
    align-items: stretch;
    flex-direction: column;
  }

  .media-actions {
    justify-content: flex-start;
  }
}
</style>

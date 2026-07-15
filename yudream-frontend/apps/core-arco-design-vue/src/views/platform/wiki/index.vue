<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import type { CapabilityItem } from '@/api/modules/platform-capability'
import apiCapability from '@/api/modules/platform-capability'
import apiGraph, { type GraphConnection } from '@/api/modules/platform-graph'
import {
  fetchWikiSpaces,
  fetchWikiIndexSnapshot,
  fetchWikiTree,
  publishWikiNode,
  saveWikiNode,
  saveWikiSpace,
  testWikiSearch,
  unpublishWikiNode,
  wikiPublicationEventsEndpoint,
  type WikiSearchHit,
  type WikiIndexSnapshot,
  type WikiNode,
  type WikiSpace,
} from '@/api/modules/platform-wiki'
import CmsMarkdownEditor from '../cms/components/CmsMarkdownEditor.vue'

type NodeType = WikiNode['nodeType']

interface TreeRow {
  depth: number
  node: WikiNode
}

interface AiProviderOption {
  code: string
  name: string
  defaultModel: string
  embeddingModels: string[]
  models: string[]
}

type PublicationState = 'IDLE' | 'CONNECTING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

interface PublicationLog {
  phase: string
  message: string
  percent: number
  completed: boolean
  time: string
}

const toast = useFaToast()
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const spaces = ref<WikiSpace[]>([])
const tree = ref<WikiNode[]>([])
const activeSpace = ref<WikiSpace | null>(null)
const activeNode = ref<WikiNode | null>(null)
const spaceVisible = ref(false)
const nodeVisible = ref(false)
const spaceForm = reactive<WikiSpace>(emptySpace())
const nodeForm = reactive({ title: '', slug: '', parentId: '', nodeType: 'PAGE' as NodeType })
const aiCapability = ref<CapabilityItem>()
const graphConnections = ref<GraphConnection[]>([])
const publicationLogVisible = ref(false)
const searchTestVisible = ref(false)
const publicationState = ref<PublicationState>('IDLE')
const publicationLogs = ref<PublicationLog[]>([])
const searchKeyword = ref('')
const searchLoading = ref(false)
const searchHits = ref<WikiSearchHit[]>([])
const indexSnapshot = ref<WikiIndexSnapshot>()
const indexSnapshotLoading = ref(false)
let publicationEvents: AbortController | undefined

const treeRows = computed<TreeRow[]>(() => flattenTree(tree.value))
const aiProviders = computed<AiProviderOption[]>(() => parseAiProviders(aiCapability.value?.config || {}))
const embeddingProviders = computed(() => aiProviders.value.filter(provider => provider.embeddingModels.length))
const embeddingProviderOptions = computed(() => providerOptions(embeddingProviders.value))
const embeddingModelOptions = computed(() => modelOptions(embeddingProviders.value, spaceForm.embeddingProviderCode, 'embeddingModels'))
const graphProviderOptions = computed(() => providerOptions(aiProviders.value.filter(provider => provider.models.length)))
const graphModelOptions = computed(() => modelOptions(aiProviders.value, spaceForm.graphProviderCode, 'models'))
const neo4jConnectionOptions = computed(() => graphConnections.value.filter(connection => connection.status === 'ACTIVE').map(connection => ({ label: `${connection.name} (${connection.code})`, value: connection.code })))
const editorContent = computed({
  get: () => activeNode.value?.markdown || '',
  set: value => {
    if (activeNode.value) activeNode.value.markdown = value
  },
})
const activePath = computed(() => activeNode.value?.path || activeNode.value?.slug || '')
const publicUrl = computed(() => activeSpace.value ? `/wiki/${activeSpace.value.slug}` : '')
const activePublicUrl = computed(() => {
  if (!activeSpace.value || !activeNode.value?.publishedVersionId) return ''
  const nodePath = activeNode.value.path || activeNode.value.slug
  return `/wiki/${encodeURIComponent(activeSpace.value.slug)}/${encodeURI(nodePath)}`
})
const latestPublicationLog = computed(() => publicationLogs.value.at(-1))
const publicationStatusText = computed(() => ({
  IDLE: '等待发布',
  CONNECTING: '正在建立日志连接',
  RUNNING: '正在发布与建立索引',
  SUCCESS: '发布与索引完成',
  FAILED: '页面已发布，索引失败',
})[publicationState.value])
const publicationStatusVariant = computed(() => ({
  IDLE: 'outline',
  CONNECTING: 'warning',
  RUNNING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
})[publicationState.value])

onMounted(async () => {
  await Promise.all([loadSpaces(), loadAiCapability(), loadGraphConnections()])
})
onBeforeUnmount(() => publicationEvents?.abort())

watch(() => spaceForm.embeddingProviderCode, syncEmbeddingModel)
watch(() => spaceForm.graphProviderCode, syncGraphModel)
watch(aiProviders, () => {
  if (spaceVisible.value) {
    syncEmbeddingModel()
    syncGraphModel()
  }
})

async function loadSpaces(selectId?: string) {
  loading.value = true
  try {
    const response = await fetchWikiSpaces()
    spaces.value = response.data
    const next = spaces.value.find(space => space.id === selectId)
      || spaces.value.find(space => space.id === activeSpace.value?.id)
      || spaces.value[0]
    if (next) await selectSpace(next)
    else {
      activeSpace.value = null
      tree.value = []
      activeNode.value = null
    }
  }
  finally {
    loading.value = false
  }
}

async function selectSpace(space: WikiSpace) {
  activeSpace.value = space
  activeNode.value = null
  const response = await fetchWikiTree(space.id!)
  tree.value = response.data
}

function selectNode(node: WikiNode) {
  activeNode.value = node
}

function openSpaceForm() {
  Object.assign(spaceForm, activeSpace.value ? { ...activeSpace.value } : emptySpace())
  syncEmbeddingModel()
  syncGraphModel()
  spaceVisible.value = true
}

async function saveSpaceForm() {
  if (!spaceForm.name.trim() || !spaceForm.slug.trim()) {
    toast.warning('请填写知识库名称和路由标识')
    return
  }
  if (!isValidSelection(embeddingProviders.value, spaceForm.embeddingProviderCode, spaceForm.embeddingModelCode, 'embeddingModels')) {
    toast.warning('请选择有效的 Embedding Provider 和模型')
    return
  }
  if (!neo4jConnectionOptions.value.some(option => option.value === spaceForm.neo4jConnectionCode)) {
    toast.warning('请选择有效的 Neo4j 连接')
    return
  }
  if (spaceForm.graphEnabled && !isValidSelection(aiProviders.value, spaceForm.graphProviderCode, spaceForm.graphModelCode, 'models')) {
    toast.warning('请选择有效的图谱 Provider 和模型')
    return
  }
  if (spaceForm.queryExpansionEnabled && !spaceForm.graphEnabled) {
    toast.warning('请先启用图谱增强后再开启查询扩展')
    return
  }
  saving.value = true
  try {
    const response = await saveWikiSpace({ ...spaceForm, name: spaceForm.name.trim(), slug: spaceForm.slug.trim() })
    spaceVisible.value = false
    await loadSpaces(response.data.id)
    toast.success(spaceForm.id ? '知识库配置已更新' : '知识库已创建')
  }
  finally {
    saving.value = false
  }
}

function openNodeForm(type: NodeType) {
  if (!activeSpace.value) return
  const parent = activeNode.value?.nodeType === 'DIRECTORY' ? activeNode.value.id : activeNode.value?.parentId
  Object.assign(nodeForm, { title: '', slug: '', parentId: parent || '', nodeType: type })
  nodeVisible.value = true
}

async function createNode() {
  if (!activeSpace.value || !nodeForm.title.trim() || !nodeForm.slug.trim()) {
    toast.warning('请填写节点标题和路由标识')
    return
  }
  saving.value = true
  try {
    await saveWikiNode(activeSpace.value.id!, {
      parentId: nodeForm.parentId || undefined,
      title: nodeForm.title.trim(),
      slug: nodeForm.slug.trim(),
      nodeType: nodeForm.nodeType,
      sort: treeRows.value.length,
      markdown: '',
    })
    nodeVisible.value = false
    await selectSpace(activeSpace.value)
    toast.success(nodeForm.nodeType === 'PAGE' ? '页面已创建' : '目录已创建')
  }
  finally {
    saving.value = false
  }
}

async function saveNode() {
  if (!activeSpace.value || !activeNode.value) return
  if (!activeNode.value.title.trim() || !activeNode.value.slug.trim()) {
    toast.warning('请填写页面标题和路由标识')
    return
  }
  saving.value = true
  const selectedId = activeNode.value.id
  try {
    await saveWikiNode(activeSpace.value.id!, activeNode.value)
    await selectSpace(activeSpace.value)
    activeNode.value = findNode(tree.value, selectedId) || null
    toast.success('草稿已保存')
  }
  finally {
    saving.value = false
  }
}

async function publishNode() {
  if (!activeNode.value || activeNode.value.nodeType !== 'PAGE') return
  await saveNode()
  publishing.value = true
  try {
    await subscribePublication(activeNode.value.id)
    await publishWikiNode(activeNode.value.id)
    await refreshActiveNode()
    if (activeNode.value?.indexStatus === 'FAILED') {
      toast.warning('页面已发布，但索引失败，请查看发布日志')
    }
    else {
      toast.success('页面已发布并进入检索索引')
    }
  }
  finally {
    publishing.value = false
  }
}

async function reloadIndexSnapshot() {
  if (!activeNode.value?.publishedVersionId) return
  indexSnapshotLoading.value = true
  try {
    const response = await fetchWikiIndexSnapshot(activeNode.value.id)
    indexSnapshot.value = response.data
  }
  finally {
    indexSnapshotLoading.value = false
  }
}

function subscribePublication(nodeId: string) {
  publicationEvents?.abort()
  publicationLogs.value = []
  publicationLogVisible.value = true
  publicationState.value = 'CONNECTING'
  appendPublicationLog('connection', '正在连接发布日志服务', 0, false)

  return new Promise<void>((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      publicationEvents?.abort()
      publicationState.value = 'FAILED'
      appendPublicationLog('connection', '发布日志连接超时，未开始发布', 0, true)
      reject(new Error('发布日志连接超时'))
    }, 8_000)
    let subscribed = false
    publicationEvents = new AbortController()
    const token = localStorage.getItem('token')
    void fetch(wikiPublicationEventsEndpoint(nodeId), {
      headers: token ? { Authorization: token } : {},
      signal: publicationEvents.signal,
    }).then(async (response) => {
      if (!response.ok || !response.body) throw new Error(`发布日志连接失败 (${response.status})`)
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const chunk = await reader.read()
        if (chunk.done) break
        buffer += decoder.decode(chunk.value, { stream: true })
        const blocks = buffer.split(/\r?\n\r?\n/)
        buffer = blocks.pop() || ''
        for (const block of blocks) {
          const eventName = block.split(/\r?\n/).find(line => line.startsWith('event:'))?.slice(6).trim()
          const payload = block.split(/\r?\n/).find(line => line.startsWith('data:'))?.slice(5).trim()
          if (eventName !== 'wiki.progress' || !payload) continue
          const data = JSON.parse(payload)
          if (data.action === 'subscribed') {
            subscribed = true
            window.clearTimeout(timeout)
            publicationState.value = 'RUNNING'
            appendPublicationLog('connection', '发布日志连接已建立，等待任务开始', 0, false)
            resolve()
            continue
          }
          appendPublicationLog(data.phase, data.message, data.percent, data.completed)
          if (data.completed) {
            publicationState.value = data.phase === 'failed' ? 'FAILED' : 'SUCCESS'
            publicationEvents?.abort()
          }
        }
      }
      if (!subscribed) throw new Error('发布日志连接在订阅前关闭')
    }).catch(() => {
      if (subscribed) {
        if (publicationState.value === 'RUNNING') {
          publicationState.value = 'FAILED'
          appendPublicationLog('connection', '发布日志连接意外中断，请检查发布结果', latestPublicationLog.value?.percent || 0, true)
        }
        return
      }
      window.clearTimeout(timeout)
      publicationEvents?.abort()
      publicationState.value = 'FAILED'
      appendPublicationLog('connection', '无法建立发布日志连接，发布未开始', 0, true)
      reject(new Error('无法建立发布日志连接'))
    })
  })
}

function appendPublicationLog(phase: string, message: string, percent: number, completed: boolean) {
  publicationLogs.value.push({
    phase,
    message,
    percent,
    completed,
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
  })
}

async function runSearchTest() {
  if (!activeSpace.value || !searchKeyword.value.trim()) return
  searchLoading.value = true
  try {
    const response = await testWikiSearch({ spaceSlug: activeSpace.value.slug, query: searchKeyword.value.trim(), topK: activeSpace.value.topK, graphExpansion: activeSpace.value.graphEnabled })
    searchHits.value = response.data
  }
  finally {
    searchLoading.value = false
  }
}

async function unpublishNode() {
  if (!activeNode.value) return
  publishing.value = true
  try {
    await unpublishWikiNode(activeNode.value.id)
    await refreshActiveNode()
    toast.success('页面已取消发布')
  }
  finally {
    publishing.value = false
  }
}

async function refreshActiveNode() {
  if (!activeSpace.value || !activeNode.value) return
  const selectedId = activeNode.value.id
  await selectSpace(activeSpace.value)
  activeNode.value = findNode(tree.value, selectedId) || null
}

function flattenTree(nodes: WikiNode[], depth = 0): TreeRow[] {
  return nodes.flatMap(node => [{ node, depth }, ...flattenTree(node.children || [], depth + 1)])
}

function findNode(nodes: WikiNode[], id: string): WikiNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = findNode(node.children || [], id)
    if (found) return found
  }
}

function statusText(status?: string) {
  const labels: Record<string, string> = { DRAFT: '草稿', INDEXING: '索引中', READY: '已发布', FAILED: '索引失败' }
  return labels[status || 'DRAFT'] || status || '草稿'
}

function statusVariant(status?: string) {
  if (status === 'READY') return 'default'
  if (status === 'FAILED') return 'destructive'
  return 'secondary'
}

function copyPublicUrl() {
  if (!publicUrl.value) return
  navigator.clipboard.writeText(`${window.location.origin}${publicUrl.value}`)
  toast.success('公开地址已复制')
}

function openPublishedPage() {
  if (!activePublicUrl.value) return
  window.open(`${window.location.origin}${activePublicUrl.value}`, '_blank', 'noopener,noreferrer')
}

async function loadAiCapability() {
  try {
    const response = await apiCapability.list()
    aiCapability.value = response.data.find(item => item.code === 'ai' && item.enabled)
  }
  catch {
    aiCapability.value = undefined
  }
}

async function loadGraphConnections() {
  try {
    const response = await apiGraph.pageConnections({ page: 1, size: 100 })
    graphConnections.value = response.data.records
  }
  catch {
    graphConnections.value = []
  }
}

function parseAiProviders(config: Record<string, string>): AiProviderOption[] {
  try {
    const rawProviders = JSON.parse(config.providers || '[]')
    if (!Array.isArray(rawProviders)) return []
    return rawProviders
      .filter(provider => provider && provider.enabled !== false)
      .map((provider) => {
        const code = String(provider.code || '').trim()
        const name = String(provider.name || code).trim()
        return {
          code,
          name,
          defaultModel: String(provider.defaultModel || '').trim(),
          embeddingModels: listValues(provider.embeddingModels),
          models: listValues(provider.models).map(model => typeof model === 'string' ? model : String(model?.code || model?.model || model?.name || '').trim()).filter(Boolean),
        }
      })
      .filter(provider => provider.code)
  }
  catch {
    return []
  }
}

function listValues(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function providerOptions(providers: AiProviderOption[]) {
  return providers.map(provider => ({ label: provider.name === provider.code ? provider.code : `${provider.name} (${provider.code})`, value: provider.code }))
}

function modelOptions(providers: AiProviderOption[], providerCode: string | undefined, field: 'embeddingModels' | 'models') {
  const provider = providers.find(item => item.code === providerCode)
  return (provider?.[field] || []).map(model => ({ label: model, value: model }))
}

function isValidSelection(providers: AiProviderOption[], providerCode: string | undefined, modelCode: string | undefined, field: 'embeddingModels' | 'models') {
  const provider = providers.find(item => item.code === providerCode)
  return Boolean(provider && modelCode && provider[field].includes(modelCode))
}

function syncEmbeddingModel() {
  if (!spaceForm.embeddingProviderCode) {
    const defaultProvider = embeddingProviders.value.find(provider => provider.code === aiCapability.value?.config.defaultProvider)
      || embeddingProviders.value.find(provider => provider.defaultModel && provider.embeddingModels.includes(provider.defaultModel))
      || (embeddingProviders.value.length === 1 ? embeddingProviders.value[0] : undefined)
    spaceForm.embeddingProviderCode = defaultProvider?.code
  }
  const models = modelOptions(embeddingProviders.value, spaceForm.embeddingProviderCode, 'embeddingModels')
  if (!models.some(model => model.value === spaceForm.embeddingModelCode)) {
    const provider = embeddingProviders.value.find(item => item.code === spaceForm.embeddingProviderCode)
    spaceForm.embeddingModelCode = provider?.embeddingModels.includes(provider.defaultModel) ? provider.defaultModel : models[0]?.value
  }
}

function syncGraphModel() {
  if (!spaceForm.graphProviderCode) {
    const defaultProvider = aiProviders.value.find(provider => provider.code === aiCapability.value?.config.defaultProvider && provider.models.length)
      || aiProviders.value.find(provider => provider.models.length)
    spaceForm.graphProviderCode = defaultProvider?.code
  }
  const models = modelOptions(aiProviders.value, spaceForm.graphProviderCode, 'models')
  if (!models.some(model => model.value === spaceForm.graphModelCode)) {
    spaceForm.graphModelCode = models[0]?.value
  }
}

function emptySpace(): WikiSpace {
  return {
    name: '', slug: '', description: '', publicReadEnabled: false, externalSearchEnabled: false,
    graphEnabled: false, queryExpansionEnabled: false, rerankEnabled: false,
    chunkSize: 1200, chunkOverlap: 160, topK: 8,
  }
}
</script>

<template>
  <div>
    <FaPageHeader title="Wiki 知识库" class="mb-0">
      <FaButton variant="outline" :loading="loading" @click="loadSpaces()">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
      <FaButton v-auth="'platform:wiki:manage'" variant="outline" :disabled="!activeSpace" @click="openSpaceForm">
        <FaIcon name="i-ri:settings-3-line" />
        知识库设置
      </FaButton>
      <FaButton v-auth="'platform:wiki:manage'" @click="openSpaceForm">
        <FaIcon name="i-ri:add-line" />
        新建知识库
      </FaButton>
    </FaPageHeader>

    <FaPageMain class="wiki-page-main">
      <section class="wiki-shell" :class="{ 'is-empty': !activeSpace }">
        <aside class="wiki-pane wiki-spaces-pane">
          <div class="pane-header">
            <div>
              <span class="pane-kicker">知识库</span>
              <strong>{{ spaces.length }}</strong>
            </div>
            <button v-auth="'platform:wiki:manage'" class="icon-action" title="新建知识库" @click="openSpaceForm"><FaIcon name="i-ri:add-line" /></button>
          </div>
          <div v-if="spaces.length" class="space-list">
            <button v-for="space in spaces" :key="space.id" class="space-item" :class="{ active: activeSpace?.id === space.id }" @click="selectSpace(space)">
              <span class="space-icon"><FaIcon name="i-ri:book-2-line" /></span>
              <span class="space-copy"><strong>{{ space.name }}</strong><small>/wiki/{{ space.slug }}</small></span>
              <FaIcon v-if="space.publicReadEnabled" name="i-ri:global-line" class="space-public" />
            </button>
          </div>
          <div v-else class="pane-empty">
            <FaIcon name="i-ri:book-open-line" />
            <span>尚未创建知识库</span>
          </div>
        </aside>

        <aside class="wiki-pane wiki-tree-pane">
          <div class="pane-header">
            <div class="min-w-0">
              <span class="pane-kicker">目录</span>
              <strong class="truncate">{{ activeSpace?.name || '选择知识库' }}</strong>
            </div>
            <div class="pane-actions">
              <button v-auth="'platform:wiki:edit'" class="icon-action" :disabled="!activeSpace" title="新建目录" @click="openNodeForm('DIRECTORY')"><FaIcon name="i-ri:folder-add-line" /></button>
              <button v-auth="'platform:wiki:edit'" class="icon-action" :disabled="!activeSpace" title="新建页面" @click="openNodeForm('PAGE')"><FaIcon name="i-ri:file-add-line" /></button>
            </div>
          </div>
          <div v-if="activeSpace && treeRows.length" class="tree-list">
            <button v-for="row in treeRows" :key="row.node.id" class="tree-item" :class="{ active: activeNode?.id === row.node.id }" :style="{ paddingLeft: `${12 + row.depth * 18}px` }" @click="selectNode(row.node)">
              <FaIcon :name="row.node.nodeType === 'DIRECTORY' ? 'i-ri:folder-3-line' : 'i-ri:file-text-line'" />
              <span>{{ row.node.title }}</span>
              <i v-if="row.node.nodeType === 'PAGE' && row.node.publishedVersionId" class="published-dot" />
            </button>
          </div>
          <div v-else class="pane-empty">
            <FaIcon :name="activeSpace ? 'i-ri:folder-open-line' : 'i-ri:cursor-line'" />
            <span>{{ activeSpace ? '从这里创建第一篇页面' : '先从左侧选择知识库' }}</span>
          </div>
        </aside>

        <main class="wiki-editor">
          <template v-if="activeNode && activeSpace">
            <header class="editor-header">
              <div class="editor-identity">
                <span class="editor-path">{{ activeSpace.name }} <b>/</b> {{ activePath }}</span>
                <div class="editor-title-row">
                  <FaIcon :name="activeNode.nodeType === 'DIRECTORY' ? 'i-ri:folder-3-line' : 'i-ri:file-text-line'" />
                  <strong>{{ activeNode.title }}</strong>
                  <FaTag :variant="statusVariant(activeNode.indexStatus)">{{ statusText(activeNode.indexStatus) }}</FaTag>
                </div>
              </div>
              <div class="editor-actions">
                <FaButton v-auth="'platform:wiki:edit'" size="sm" variant="outline" :loading="saving" @click="saveNode"><FaIcon name="i-ri:save-3-line" /> 保存</FaButton>
                <FaButton v-if="activeNode.nodeType === 'PAGE' && !activeNode.publishedVersionId" v-auth="'platform:wiki:publish'" size="sm" :loading="publishing" @click="publishNode"><FaIcon name="i-ri:send-plane-line" /> 发布</FaButton>
                <FaButton v-if="activeNode.nodeType === 'PAGE' && activeNode.publishedVersionId" v-auth="'platform:wiki:publish'" size="sm" variant="outline" :loading="publishing" @click="publishNode"><FaIcon name="i-ri:refresh-line" /> 重建索引</FaButton>
                <FaButton v-if="activeNode.nodeType === 'PAGE' && activeNode.publishedVersionId" v-auth="'platform:wiki:publish'" size="sm" variant="outline" :loading="publishing" @click="unpublishNode">取消发布</FaButton>
                <FaButton v-if="activeNode.nodeType === 'PAGE' && activeNode.publishedVersionId && activeSpace?.publicReadEnabled" size="sm" variant="outline" @click="openPublishedPage"><FaIcon name="i-ri:external-link-line" /> 打开页面</FaButton>
                <FaButton v-if="activeNode.nodeType === 'PAGE'" size="sm" variant="outline" @click="searchTestVisible = true"><FaIcon name="i-ri:search-line" /> 检索测试</FaButton>
                <button v-if="publicationLogs.length" class="icon-action" title="查看发布日志" @click="publicationLogVisible = true"><FaIcon name="i-ri:file-list-3-line" /></button>
              </div>
            </header>

            <section class="editor-fields">
              <label><span>标题</span><FaInput v-model="activeNode.title" /></label>
              <label><span>路由标识</span><FaInput v-model="activeNode.slug" /></label>
              <div class="node-meta"><span>节点类型</span><strong>{{ activeNode.nodeType === 'PAGE' ? '页面' : '目录' }}</strong></div>
            </section>

            <section v-if="activeNode.nodeType === 'PAGE' && activeNode.publishedVersionId" class="index-results">
              <div class="search-test__head"><div><strong>索引结果</strong><span>当前已发布版本的向量分块与图谱关系</span></div><FaButton variant="outline" size="sm" :loading="indexSnapshotLoading" @click="reloadIndexSnapshot"><FaIcon name="i-ri:refresh-line" /> 加载</FaButton></div>
              <template v-if="indexSnapshot">
                <div class="index-results__grid"><div><strong>向量分块 {{ indexSnapshot.chunks.length }}</strong><ol><li v-for="chunk in indexSnapshot.chunks" :key="chunk.sequence"><code>#{{ chunk.sequence + 1 }}</code><span>{{ chunk.content }}</span></li></ol></div><div><strong>图谱关系 {{ indexSnapshot.relations.length }}</strong><ol><li v-for="(relation, index) in indexSnapshot.relations" :key="index"><span>{{ relation.source }} <b>{{ relation.relation }}</b> {{ relation.target }}</span><small>{{ relation.confidence.toFixed(2) }}</small></li></ol></div></div>
              </template>
            </section>

            <section v-if="activeNode.nodeType === 'PAGE'" class="markdown-workspace">
              <CmsMarkdownEditor v-model="editorContent" placeholder="# 页面标题&#10;&#10;开始编写内容..." />
            </section>
            <section v-else class="directory-state">
              <FaIcon name="i-ri:folder-3-line" />
              <strong>目录节点</strong>
              <span>可在此目录下继续创建页面或子目录。</span>
            </section>
          </template>

          <section v-else class="editor-empty">
            <div class="editor-empty-icon"><FaIcon name="i-ri:quill-pen-line" /></div>
            <h2>{{ activeSpace ? '从目录开始编辑' : '选择一个知识库' }}</h2>
            <p>{{ activeSpace ? '选择已有页面，或新建一篇 Markdown 页面。' : '左侧知识库会按独立路由和目录树组织内容。' }}</p>
            <div class="editor-empty-actions">
              <FaButton v-if="activeSpace" v-auth="'platform:wiki:edit'" @click="openNodeForm('PAGE')"><FaIcon name="i-ri:file-add-line" /> 新建页面</FaButton>
              <FaButton v-else v-auth="'platform:wiki:manage'" @click="openSpaceForm"><FaIcon name="i-ri:add-line" /> 新建知识库</FaButton>
            </div>
          </section>
        </main>
      </section>
    </FaPageMain>

    <FaModal v-model="spaceVisible" :title="spaceForm.id ? '编辑知识库' : '新建知识库'" show-cancel-button :confirm-loading="saving" class="sm:max-w-3xl" @confirm="saveSpaceForm">
      <a-form :model="spaceForm" layout="vertical">
        <a-grid :cols="2" :col-gap="16">
          <a-grid-item><a-form-item label="名称" required><FaInput v-model="spaceForm.name" placeholder="例如：产品文档" /></a-form-item></a-grid-item>
          <a-grid-item><a-form-item label="公开路由标识" required><FaInput v-model="spaceForm.slug" placeholder="例如：product-docs" /></a-form-item></a-grid-item>
        </a-grid>
        <a-form-item label="简介"><FaTextarea v-model="spaceForm.description" rows="3" /></a-form-item>
        <div class="space-settings">
          <label><span>允许公开阅读</span><small>已发布页面可通过公开地址访问</small><FaSwitch v-model="spaceForm.publicReadEnabled" /></label>
          <label><span>开放外部检索</span><small>允许 API Key 和 Agent 工具调用检索</small><FaSwitch v-model="spaceForm.externalSearchEnabled" /></label>
          <label><span>图谱增强</span><small>发布时提取实体关系并参与检索</small><FaSwitch v-model="spaceForm.graphEnabled" /></label>
          <label><span>查询扩展</span><small>使用图谱模型生成等义查询，扩大召回范围</small><FaSwitch v-model="spaceForm.queryExpansionEnabled" :disabled="!spaceForm.graphEnabled" /></label>
          <label><span>Rerank 重排</span><small>使用 Embedding Provider 配置的重排模型优化结果顺序</small><FaSwitch v-model="spaceForm.rerankEnabled" /></label>
        </div>
        <section class="model-settings">
          <div class="model-settings__head">
            <strong>Embedding 模型</strong>
            <span>从平台能力中已启用的 AI Provider 和 Embedding 模型中选择</span>
          </div>
          <a-alert v-if="!embeddingProviderOptions.length" type="warning" class="mb-4">未发现可用的 Embedding 模型，请先在平台能力的 AI 配置中启用 Provider 并配置 Embedding 模型。</a-alert>
          <a-grid :cols="2" :col-gap="16">
            <a-grid-item><a-form-item label="Embedding Provider" required><FaSelect v-model="spaceForm.embeddingProviderCode" :options="embeddingProviderOptions" placeholder="选择 Embedding Provider" /></a-form-item></a-grid-item>
            <a-grid-item><a-form-item label="Embedding 模型" required><FaSelect v-model="spaceForm.embeddingModelCode" :options="embeddingModelOptions" :disabled="!spaceForm.embeddingProviderCode" placeholder="选择 Embedding 模型" /></a-form-item></a-grid-item>
          </a-grid>
          <a-grid v-if="spaceForm.graphEnabled" :cols="2" :col-gap="16">
            <a-grid-item><a-form-item label="图谱 Provider" required><FaSelect v-model="spaceForm.graphProviderCode" :options="graphProviderOptions" placeholder="选择图谱 Provider" /></a-form-item></a-grid-item>
            <a-grid-item><a-form-item label="图谱模型" required><FaSelect v-model="spaceForm.graphModelCode" :options="graphModelOptions" :disabled="!spaceForm.graphProviderCode" placeholder="选择图谱模型" /></a-form-item></a-grid-item>
          </a-grid>
          <a-alert v-if="!neo4jConnectionOptions.length" type="warning" class="mb-4">没有可用的 Neo4j 连接，请先在图数据库中创建并启用连接。</a-alert>
          <a-form-item label="Neo4j 连接" required class="mt-4"><div class="neo4j-select-row"><FaSelect v-model="spaceForm.neo4jConnectionCode" :options="neo4jConnectionOptions" placeholder="选择图数据库连接" /><RouterLink to="/platform/graph"><FaButton variant="outline" size="sm"><FaIcon name="i-ri:database-2-line" /> 管理连接</FaButton></RouterLink></div></a-form-item>
        </section>
        <a-grid :cols="3" :col-gap="16" class="mt-4">
          <a-grid-item><a-form-item label="Chunk 长度"><a-input-number v-model="spaceForm.chunkSize" :min="200" :max="4000" class="w-full" /></a-form-item></a-grid-item>
          <a-grid-item><a-form-item label="Chunk 重叠"><a-input-number v-model="spaceForm.chunkOverlap" :min="0" :max="800" class="w-full" /></a-form-item></a-grid-item>
          <a-grid-item><a-form-item label="默认 Top K"><a-input-number v-model="spaceForm.topK" :min="1" :max="30" class="w-full" /></a-form-item></a-grid-item>
        </a-grid>
        <div v-if="spaceForm.id" class="public-link-row"><span>公开地址</span><code>{{ publicUrl }}</code><button class="icon-action" title="复制公开地址" @click.prevent="copyPublicUrl"><FaIcon name="i-ri:file-copy-line" /></button></div>
      </a-form>
    </FaModal>

    <FaModal v-model="nodeVisible" :title="nodeForm.nodeType === 'PAGE' ? '新建页面' : '新建目录'" show-cancel-button :confirm-loading="saving" class="sm:max-w-xl" @confirm="createNode">
      <a-form :model="nodeForm" layout="vertical">
        <a-form-item label="节点标题" required><FaInput v-model="nodeForm.title" placeholder="例如：快速开始" /></a-form-item>
        <a-form-item label="路由标识" required><FaInput v-model="nodeForm.slug" placeholder="例如：getting-started" /></a-form-item>
        <a-form-item label="父级目录"><FaSelect v-model="nodeForm.parentId" clearable :options="treeRows.filter(row => row.node.nodeType === 'DIRECTORY').map(row => ({ label: `${'— '.repeat(row.depth)}${row.node.title}`, value: row.node.id }))" placeholder="根目录" /></a-form-item>
      </a-form>
    </FaModal>

    <FaModal v-model="publicationLogVisible" title="发布日志" :footer="false" class="sm:max-w-3xl" content-class="publication-log-modal">
      <section class="publication-progress" aria-live="polite" aria-atomic="false">
        <div class="publication-progress__head">
          <div>
            <strong>{{ publicationStatusText }}</strong>
            <span>{{ latestPublicationLog?.message || '等待发布任务' }}</span>
          </div>
          <FaTag :variant="publicationStatusVariant">{{ latestPublicationLog?.percent || 0 }}%</FaTag>
        </div>
        <a-progress :percent="latestPublicationLog?.percent || 0" :show-text="false" :status="publicationState === 'FAILED' ? 'danger' : publicationState === 'SUCCESS' ? 'success' : 'normal'" />
        <div class="publication-log-list">
          <div v-for="(log, index) in publicationLogs" :key="`${log.phase}-${index}`" class="publication-log" :class="{ 'is-failed': log.phase === 'failed', 'is-complete': log.completed && log.phase !== 'failed' }">
            <time>{{ log.time }}</time>
            <code>{{ log.phase }}</code>
            <span>{{ log.message }}</span>
          </div>
        </div>
      </section>
    </FaModal>

    <FaModal v-model="searchTestVisible" title="检索测试" :footer="false" class="sm:max-w-3xl">
      <section class="search-test">
        <div class="search-test__head"><div><strong>当前知识库检索</strong><span>使用已发布的向量索引、图谱扩展和后续重排流程</span></div></div>
        <div class="search-test__form"><FaInput v-model="searchKeyword" placeholder="输入问题或关键词" @keyup.enter="runSearchTest" /><FaButton :loading="searchLoading" @click="runSearchTest"><FaIcon name="i-ri:search-line" /> 测试</FaButton></div>
        <div v-if="searchHits.length" class="search-hits"><article v-for="hit in searchHits" :key="`${hit.nodeId}-${hit.path}`"><div><strong>{{ hit.title }}</strong><small>{{ hit.path }}</small></div><FaTag variant="outline">{{ hit.score.toFixed(3) }}</FaTag><p>{{ hit.content }}</p></article></div>
        <a-empty v-else-if="!searchLoading" description="输入问题后开始检索" class="mt-8" />
      </section>
    </FaModal>
  </div>
</template>

<style scoped>
.wiki-page-main { padding: 0; overflow: hidden; container-type: inline-size; }
.wiki-shell { display: grid; grid-template-columns: 278px 318px minmax(0, 1fr); min-height: calc(100vh - 164px); border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.wiki-pane { min-width: 0; border-right: 1px solid var(--color-border-2); background: var(--color-bg-2); }
.pane-header { display: flex; min-height: 72px; align-items: center; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--color-border-2); }
.pane-header > div { display: grid; gap: 3px; min-width: 0; }.pane-kicker { color: var(--color-text-3); font-size: 12px; }.pane-header strong { font-size: 15px; }.pane-actions, .editor-actions { display: flex; gap: 7px; align-items: center; }
.icon-action { display: inline-grid; width: 30px; height: 30px; place-items: center; border: 1px solid var(--color-border-2); border-radius: 5px; background: var(--color-bg-1); color: var(--color-text-2); cursor: pointer; }.icon-action:hover, .icon-action.selected { border-color: rgb(var(--primary-6)); color: rgb(var(--primary-6)); }.icon-action:disabled { opacity: .4; cursor: not-allowed; }
.space-list, .tree-list { padding: 10px; }.space-list { display: grid; gap: 5px; }.space-item, .tree-item { position: relative; display: flex; width: 100%; min-width: 0; align-items: center; gap: 9px; border: 1px solid transparent; background: transparent; color: var(--color-text-2); text-align: left; cursor: pointer; }.space-item { padding: 10px; border-radius: 6px; }.tree-item { height: 35px; padding-right: 10px; border-radius: 4px; font-size: 13px; }.space-item:hover, .tree-item:hover { background: var(--color-fill-2); }.space-item.active, .tree-item.active { border-color: rgba(var(--primary-6), .24); background: rgba(var(--primary-6), .10); color: rgb(var(--primary-6)); }
.space-icon { display: grid; width: 32px; height: 32px; place-items: center; border-radius: 5px; background: var(--color-fill-2); color: rgb(var(--primary-6)); }.space-copy { display: grid; flex: 1; gap: 3px; min-width: 0; }.space-copy strong, .space-copy small, .tree-item span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.space-copy small { color: var(--color-text-3); font-size: 11px; }.space-public { color: rgb(var(--primary-6)); }.published-dot { width: 6px; height: 6px; margin-left: auto; border-radius: 50%; background: rgb(var(--success-6)); }
.pane-empty { display: grid; place-items: center; gap: 9px; min-height: 220px; padding: 28px; color: var(--color-text-3); text-align: center; font-size: 13px; }.pane-empty :deep(svg) { font-size: 25px; opacity: .6; }
.wiki-editor { min-width: 0; display: flex; flex-direction: column; background: var(--color-bg-1); }.editor-header { display: flex; min-height: 72px; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 22px; border-bottom: 1px solid var(--color-border-2); }.editor-identity { display: grid; min-width: 0; gap: 6px; }.editor-path { overflow: hidden; color: var(--color-text-3); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.editor-path b { margin: 0 5px; color: var(--color-text-4); }.editor-title-row { display: flex; align-items: center; gap: 8px; }.editor-title-row strong { overflow: hidden; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }.editor-title-row :deep(.icon) { color: rgb(var(--primary-6)); }
.editor-fields { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) 120px; gap: 14px; padding: 18px 22px; border-bottom: 1px solid var(--color-border-2); }.editor-fields label { display: grid; gap: 6px; color: var(--color-text-3); font-size: 12px; }.node-meta { display: grid; align-content: end; gap: 6px; color: var(--color-text-3); font-size: 12px; }.node-meta strong { color: var(--color-text-1); font-size: 14px; }
.markdown-workspace { flex: 1; min-height: 640px; padding: 18px 22px 22px; }.markdown-workspace :deep(.markdown-editor) { height: 100%; }.markdown-workspace :deep(.md-editor) { min-height: 640px; }
.search-test, .index-results { margin: 16px 22px 0; padding: 14px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-2); }.publication-progress__head, .search-test__head, .publication-log, .search-test__form, .search-hits article { display: flex; align-items: center; gap: 10px; }.publication-progress__head, .search-test__head { justify-content: space-between; margin-bottom: 10px; }.publication-progress__head > div { display: grid; min-width: 0; gap: 3px; }.publication-progress__head span, .search-test__head span, .publication-log code, .search-hits small { color: var(--color-text-3); font-size: 12px; }.publication-progress__head span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.publication-log-list { display: grid; max-height: min(52vh, 420px); margin-top: 14px; overflow-y: auto; border: 1px solid var(--color-border-2); border-radius: 5px; }.publication-log { display: grid; grid-template-columns: 66px 86px minmax(0, 1fr); align-items: start; min-height: 42px; padding: 10px 12px; border-bottom: 1px solid var(--color-border-2); color: var(--color-text-2); font-size: 13px; line-height: 1.55; }.publication-log:last-child { border-bottom: 0; }.publication-log time, .publication-log code { color: var(--color-text-3); font-size: 12px; }.publication-log code { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.publication-log span { min-width: 0; }.publication-log.is-failed { background: rgb(var(--danger-1)); color: rgb(var(--danger-6)); }.publication-log.is-complete { background: rgb(var(--success-1)); color: rgb(var(--success-6)); }.search-test__form { align-items: stretch; }.search-test__form :deep(.fa-input) { flex: 1; }.search-hits { display: grid; gap: 8px; margin-top: 12px; }.search-hits article { display: grid; grid-template-columns: 1fr auto; padding: 10px; border: 1px solid var(--color-border-2); border-radius: 5px; }.search-hits article div { display: grid; gap: 3px; }.search-hits article p { grid-column: 1 / -1; margin: 7px 0 0; color: var(--color-text-2); font-size: 13px; line-height: 1.6; white-space: pre-wrap; }.index-results__grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }.index-results ol { display: grid; gap: 6px; max-height: 260px; margin: 8px 0 0; padding: 0; overflow: auto; list-style: none; }.index-results li { display: flex; gap: 8px; justify-content: space-between; padding: 8px; border: 1px solid var(--color-border-2); border-radius: 4px; color: var(--color-text-2); font-size: 12px; line-height: 1.55; }.index-results li span { min-width: 0; }.index-results li b { color: rgb(var(--primary-6)); }.index-results li small { white-space: nowrap; }
.directory-state, .editor-empty { display: grid; flex: 1; place-content: center; justify-items: center; gap: 10px; padding: 38px; color: var(--color-text-3); text-align: center; }.directory-state :deep(svg) { font-size: 34px; color: rgb(var(--primary-6)); }.directory-state strong { color: var(--color-text-1); }.editor-empty h2 { margin: 6px 0 0; color: var(--color-text-1); font-size: 19px; }.editor-empty p { max-width: 360px; margin: 0; line-height: 1.7; }.editor-empty-icon { display: grid; width: 54px; height: 54px; place-items: center; border-radius: 7px; background: rgba(var(--primary-6), .12); color: rgb(var(--primary-6)); font-size: 27px; }.editor-empty-actions { margin-top: 6px; }
.space-settings { display: grid; gap: 1px; border: 1px solid var(--color-border-2); border-radius: 6px; overflow: hidden; }.space-settings label { display: grid; grid-template-columns: 1fr auto; gap: 2px 12px; align-items: center; padding: 12px 14px; background: var(--color-bg-2); }.space-settings label + label { border-top: 1px solid var(--color-border-2); }.space-settings span { color: var(--color-text-1); font-size: 13px; }.space-settings small { color: var(--color-text-3); font-size: 12px; }.space-settings :deep(button) { grid-column: 2; grid-row: 1 / 3; }.public-link-row { display: flex; align-items: center; gap: 10px; margin-top: 12px; padding: 10px 12px; border-radius: 6px; background: var(--color-fill-2); color: var(--color-text-3); font-size: 12px; }.public-link-row code { flex: 1; overflow: hidden; color: var(--color-text-1); text-overflow: ellipsis; white-space: nowrap; }
.model-settings { margin-top: 16px; padding: 14px 14px 2px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-2); }.model-settings__head { display: grid; gap: 3px; margin-bottom: 12px; }.model-settings__head strong { color: var(--color-text-1); font-size: 14px; }.model-settings__head span { color: var(--color-text-3); font-size: 12px; }.model-settings :deep(.arco-alert) { border-radius: 5px; }.neo4j-select-row { display: flex; gap: 8px; }.neo4j-select-row :deep(.fa-select) { flex: 1; }
@container (max-width: 1100px) { .wiki-shell { grid-template-columns: 230px 270px minmax(0, 1fr); }.editor-fields { grid-template-columns: 1fr 1fr; }.node-meta { display: none; } }
@container (max-width: 900px) { .wiki-page-main { overflow: visible; }.wiki-shell { display: block; min-height: auto; }.wiki-pane { border-right: 0; border-bottom: 1px solid var(--color-border-2); }.wiki-spaces-pane { max-height: 220px; }.wiki-tree-pane { max-height: 320px; }.editor-header { align-items: flex-start; flex-direction: column; }.editor-actions { width: 100%; flex-wrap: wrap; }.editor-fields { grid-template-columns: 1fr; }.markdown-workspace { min-height: 480px; } }
</style>

<script setup lang="ts">
import type {WikiNode, WikiSpace} from '@/api/modules/platform-wiki'
import {computed, onMounted, provide, ref, shallowRef, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {fetchWikiSpaces, fetchWikiTree, rebuildWikiIndex, saveWikiSpace, deleteWikiSpace} from '@/api/modules/platform-wiki'
import WikiDirectoryPanel from './components/WikiDirectoryPanel.vue'
import WikiGraphPanel from './components/WikiGraphPanel.vue'
import WikiIngestPanel from './components/WikiIngestPanel.vue'
import WikiLintPanel from './components/WikiLintPanel.vue'
import WikiResearchPanel from './components/WikiResearchPanel.vue'
import WikiReviewPanel from './components/WikiReviewPanel.vue'
import WikiSearchPanel from './components/WikiSearchPanel.vue'
import WikiSettingsPanel from './components/WikiSettingsPanel.vue'
import WikiSourcesPanel from './components/WikiSourcesPanel.vue'
import {flattenTree, wikiWorkbenchKey} from './wiki-utils'

const toast = useFaToast()
const modal = useFaModal()
const route = useRoute()
const router = useRouter()
const workbenchPath = route.path
const spacesLoaded = ref(false)

const spaces = ref<WikiSpace[]>([])
const spaceId = ref('')
const tree = ref<WikiNode[]>([])
const selectedNode = ref<WikiNode | null>(null)
const loadingTree = ref(false)

const space = computed(() => spaces.value.find(item => item.id === spaceId.value) || null)
const flatTree = computed(() => flattenTree(tree.value))

const panels = [
  {key: 'directory', label: 'Wiki 目录', icon: 'i-ri:node-tree', component: shallowRef(WikiDirectoryPanel)},
  {key: 'sources', label: '资料源', icon: 'i-ri:database-2-line', component: shallowRef(WikiSourcesPanel)},
  {key: 'ingest', label: '摄入队列', icon: 'i-ri:stack-line', component: shallowRef(WikiIngestPanel)},
  {key: 'search', label: '检索', icon: 'i-ri:search-eye-line', component: shallowRef(WikiSearchPanel)},
  {key: 'graph', label: '图谱', icon: 'i-ri:mind-map', component: shallowRef(WikiGraphPanel)},
  {key: 'lint', label: 'Lint', icon: 'i-ri:shield-check-line', component: shallowRef(WikiLintPanel)},
  {key: 'review', label: '审核', icon: 'i-ri:checkbox-multiple-line', component: shallowRef(WikiReviewPanel)},
  {key: 'research', label: '深度研究', icon: 'i-ri:flask-line', component: shallowRef(WikiResearchPanel)},
  {key: 'settings', label: '设置', icon: 'i-ri:settings-3-line', component: shallowRef(WikiSettingsPanel)},
]

const panelGroups = [
  {label: '内容管理', keys: ['directory', 'sources', 'ingest']},
  {label: '探索研究', keys: ['search', 'graph', 'research']},
  {label: '质量维护', keys: ['lint', 'review']},
  {label: '知识库设置', keys: ['settings']},
].map(group => ({...group, items: group.keys.map(key => panels.find(panel => panel.key === key)!)}))
const panelOptions = panelGroups.map(group => ({
  label: group.label,
  options: group.items.map(item => ({label: item.label, value: item.key})),
}))
const spaceOptions = computed(() => spaces.value.filter(item => item.id).map(item => ({
  label: item.name,
  value: item.id!,
})))
const activePanel = computed({
  get: () => panels.some(item => item.key === route.query.activePanel) ? route.query.activePanel as string : 'directory',
  set: (value: string) => {
    if (panels.some(item => item.key === value)) {
      void router.push({query: {...route.query, activePanel: value}})
    }
  },
})
const activeItem = computed(() => panels.find(item => item.key === activePanel.value)!)
const activeGroup = computed(() => panelGroups.find(group => group.keys.includes(activePanel.value))!)
const activeComponent = computed(() => activeItem.value.component.value)
const selectedSpaceId = computed({
  get: () => spaceId.value,
  set: (id: string) => {
    if (spaces.value.some(item => item.id === id)) {
      void router.push({query: {...route.query, spaceId: id}})
    }
  },
})

function restoreQuery() {
  if (!spacesLoaded.value || route.path !== workbenchPath) return
  const requestedId = route.query.spaceId
  const nextId = spaces.value.find(item => item.id === requestedId)?.id || spaces.value[0]?.id || ''
  if (spaceId.value !== nextId) {
    spaceId.value = nextId
    selectedNode.value = null
    tree.value = []
    void reloadTree()
  }
  if (route.query.spaceId !== (nextId || undefined) || route.query.activePanel !== activePanel.value) {
    void router.replace({query: {...route.query, spaceId: nextId || undefined, activePanel: activePanel.value}})
  }
}

watch(() => [route.path, route.query.spaceId, route.query.activePanel], restoreQuery)

async function loadSpaces() {
  const res = await fetchWikiSpaces()
  spaces.value = res.data || []
  spacesLoaded.value = true
  const previousId = spaceId.value
  restoreQuery()
  if (previousId === spaceId.value) await reloadTree()
}

let treeRequest = 0
async function reloadTree() {
  const request = ++treeRequest
  const id = spaceId.value
  if (!id) {
    tree.value = []
    loadingTree.value = false
    return
  }
  loadingTree.value = true
  try {
    const res = await fetchWikiTree(id)
    if (request === treeRequest && id === spaceId.value) tree.value = res.data || []
  } finally {
    if (request === treeRequest) loadingTree.value = false
  }
}

function selectNode(node: WikiNode | null) {
  selectedNode.value = node
}

function findNode(ref: { title?: string, nodeId?: string, path?: string }): WikiNode | null {
  const pages = flatTree.value.filter(node => node.nodeType === 'PAGE')
  if (ref.nodeId) {
    const byId = pages.find(node => node.id === ref.nodeId)
    if (byId) return byId
  }
  if (ref.path) {
    const byPath = pages.find(node => node.path === ref.path || node.slug === ref.path)
    if (byPath) return byPath
  }
  return (ref.title && pages.find(node => node.title === ref.title)) || null
}

function openPanel(panel: string) {
  activePanel.value = panel
}

function openPage(ref: { title?: string, nodeId?: string, path?: string }) {
  const node = findNode(ref)
  if (!node) {
    toast.warning(`未找到页面「${ref.title || ref.path || ref.nodeId}」`)
    return
  }
  selectedNode.value = node
  activePanel.value = 'directory'
}

provide(wikiWorkbenchKey, {
  spaces,
  spaceId,
  space,
  tree,
  flatTree,
  selectedNode,
  loadingTree,
  reloadTree,
  reloadSpaces: loadSpaces,
  selectNode,
  openPanel,
  openPage,
  findNode,
})

// 新建知识库
const createVisible = ref(false)
const creating = ref(false)
const createForm = ref({name: '', slug: '', description: ''})

async function createSpace() {
  if (!createForm.value.name.trim() || !createForm.value.slug.trim()) {
    toast.warning('请填写名称与 slug')
    return
  }
  creating.value = true
  try {
    await saveWikiSpace({
      name: createForm.value.name.trim(),
      slug: createForm.value.slug.trim(),
      description: createForm.value.description.trim(),
      publicReadEnabled: false,
      externalSearchEnabled: false,
      graphEnabled: true,
      chunkSize: 800,
      chunkOverlap: 120,
      topK: 8,
      queryExpansionEnabled: true,
      rerankEnabled: false,
      contextWindowTokens: 64000,
      sourceGroundedDefault: false,
      watchEnabled: false,
    })
    toast.success('知识库已创建')
    createVisible.value = false
    createForm.value = {name: '', slug: '', description: ''}
    await loadSpaces()
  } finally {
    creating.value = false
  }
}

async function rebuild() {
  if (!spaceId.value) {
    return
  }
  await rebuildWikiIndex(spaceId.value)
  toast.success('已加入重建索引队列')
  activePanel.value = 'ingest'
}

// 删除知识库：后端级联删除目录/页面、资料源、摄入任务与审核项
const deletingSpaceId = ref('')

function removeSpace(item: WikiSpace) {
  if (!item.id) {
    return
  }
  modal.confirm({
    title: '删除知识库',
    content: `确定删除知识库「${item.name}」吗？其中的目录、页面、资料源、摄入任务与审核项将被一并删除，且不可恢复。`,
    onConfirm: async () => {
      deletingSpaceId.value = item.id!
      try {
        await deleteWikiSpace(item.id!)
        toast.success('知识库已删除')
        await loadSpaces()
      } finally {
        deletingSpaceId.value = ''
      }
    },
  })
}

onMounted(loadSpaces)
</script>

<template>
  <div class="wiki-workbench">
    <FaPageHeader
        title="LLM Wiki 工作台"
        class="mb-0"
        :description="space ? `${space.name} · /wiki/${space.slug}` : '资料源 → 摄入 → 自动维护的知识库'"
    >
      <FaButton variant="outline" :disabled="!space" @click="rebuild">
        <FaIcon name="i-ri:refresh-line"/>
        重建索引
      </FaButton>
    </FaPageHeader>

    <div class="wiki-toolbar">
      <label class="wiki-space-picker">
        <span>当前知识库</span>
        <FaSelect v-model="selectedSpaceId" :options="spaceOptions" :disabled="!spaces.length" placeholder="暂无知识库" class="w-full" />
      </label>
      <div class="wiki-toolbar__actions">
        <FaButton variant="outline" @click="createVisible = true">
          <FaIcon name="i-ri:add-line" />新建知识库
        </FaButton>
        <FaButton variant="ghost" :disabled="!space" :loading="!!deletingSpaceId" @click="space && removeSpace(space)">
          <FaIcon name="i-ri:delete-bin-line" />删除知识库
        </FaButton>
      </div>
      <label class="wiki-mobile-nav">
        <span>工作台功能</span>
        <FaSelect v-model="activePanel" :options="panelOptions" class="w-full" />
      </label>
    </div>

    <div class="wiki-shell">
      <nav class="wiki-nav" aria-label="Wiki 分组导航">
        <section v-for="group in panelGroups" :key="group.label" class="wiki-nav__group" :aria-label="group.label">
          <h2>{{ group.label }}</h2>
          <FaButton
            v-for="item in group.items"
            :key="item.key"
            :variant="activePanel === item.key ? 'secondary' : 'ghost'"
            class="w-full justify-start"
            :aria-current="activePanel === item.key ? 'page' : undefined"
            @click="openPanel(item.key)"
          >
            <FaIcon :name="item.icon" />
            {{ item.label }}
          </FaButton>
        </section>
      </nav>
      <main class="wiki-main" :aria-label="activeItem.label">
        <div class="wiki-main__heading">
          <span>{{ activeGroup.label }}</span>
          <FaIcon name="i-ri:arrow-right-s-line" />
          <h2>{{ activeItem.label }}</h2>
        </div>
        <div v-if="!spacesLoaded" class="wiki-empty" role="status">正在加载知识库…</div>
        <div v-else-if="!space" class="wiki-empty">
          <FaIcon name="i-ri:book-shelf-line" class="text-3xl" />
          <p>暂无知识库，新建后即可管理资料与页面。</p>
          <FaButton @click="createVisible = true">新建知识库</FaButton>
        </div>
        <div v-else class="wiki-main__panel">
          <KeepAlive>
            <component :is="activeComponent" :key="activePanel"/>
          </KeepAlive>
        </div>
      </main>
    </div>

    <FaModal
        v-model="createVisible"
        title="新建知识库"
        show-cancel-button
        :confirm-button-loading="creating"
        @confirm="createSpace"
    >
      <div class="wiki-create-form">
        <label class="wiki-field">
          <span>名称</span>
          <FaInput v-model="createForm.name" placeholder="例如：产品知识库"/>
        </label>
        <label class="wiki-field">
          <span>路径 slug</span>
          <FaInput v-model="createForm.slug" placeholder="例如：product"/>
        </label>
        <label class="wiki-field">
          <span>描述</span>
          <FaTextarea v-model="createForm.description" placeholder="这个知识库用来做什么？" :rows="3"/>
        </label>
      </div>
    </FaModal>
  </div>
</template>

<style scoped>
.wiki-workbench {
  display: flex;
  overflow: hidden;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.wiki-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border-2);
  background: var(--color-bg-1);
}

.wiki-space-picker,
.wiki-mobile-nav {
  display: grid;
  gap: 6px;
  min-width: 220px;
}

.wiki-space-picker > span,
.wiki-mobile-nav > span {
  color: var(--color-text-3);
  font-size: 12px;
}

.wiki-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

.wiki-mobile-nav {
  display: none;
}

.wiki-shell {
  display: flex;
  overflow: hidden;
  flex: 1;
  min-height: 0;
  background: var(--color-fill-1);
}

.wiki-nav {
  display: flex;
  overflow-y: auto;
  flex-direction: column;
  flex-shrink: 0;
  gap: 16px;
  width: 220px;
  min-height: 0;
  padding: 16px 12px;
  border-right: 1px solid var(--color-border-2);
  background: var(--color-bg-1);
}

.wiki-nav__group {
  display: grid;
  gap: 6px;
}

.wiki-nav__group h2 {
  margin: 0 4px 2px;
  color: var(--color-text-3);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.wiki-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.wiki-main__heading {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
  padding: 14px 16px 10px;
  color: var(--color-text-3);
  font-size: 12px;
}

.wiki-main__heading h2 {
  margin: 0;
  color: var(--color-text-1);
  font-size: 16px;
  font-weight: 600;
}

.wiki-main__panel {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.wiki-empty {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 10px;
  min-height: 240px;
  padding: 32px 16px;
  color: var(--color-text-3);
  text-align: center;
}

.wiki-create-form {
  display: grid;
  gap: 14px;
}

.wiki-field {
  display: grid;
  gap: 6px;
}

.wiki-field > span {
  color: var(--color-text-3);
  font-size: 13px;
}

@media (max-width: 900px) {
  .wiki-nav {
    display: none;
  }

  .wiki-mobile-nav {
    display: grid;
    width: 100%;
  }

  .wiki-toolbar {
    align-items: stretch;
  }

  .wiki-space-picker,
  .wiki-toolbar__actions {
    width: 100%;
    margin-left: 0;
  }
}
</style>

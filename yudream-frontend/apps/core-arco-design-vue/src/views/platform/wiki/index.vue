<script setup lang="ts">
import type {WikiNode, WikiSpace} from '@/api/modules/platform-wiki'
import {computed, onMounted, provide, ref, shallowRef} from 'vue'
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

const spaces = ref<WikiSpace[]>([])
const spaceId = ref('')
const tree = ref<WikiNode[]>([])
const selectedNode = ref<WikiNode | null>(null)
const loadingTree = ref(false)
const activePanel = ref('directory')

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

const activeComponent = computed(() => panels.find(item => item.key === activePanel.value)?.component.value || WikiDirectoryPanel)

async function loadSpaces() {
  const res = await fetchWikiSpaces()
  spaces.value = res.data || []
  // 当前知识库被删除后自动回落到第一个可用知识库
  if (spaceId.value && !spaces.value.some(item => item.id === spaceId.value)) {
    spaceId.value = ''
    selectedNode.value = null
  }
  if (!spaceId.value && spaces.value.length) {
    spaceId.value = spaces.value[0].id || ''
  }
  if (spaceId.value) {
    await reloadTree()
  }
  else {
    tree.value = []
  }
}

async function reloadTree() {
  if (!spaceId.value) {
    tree.value = []
    return
  }
  loadingTree.value = true
  try {
    const res = await fetchWikiTree(spaceId.value)
    tree.value = res.data || []
  } finally {
    loadingTree.value = false
  }
}

async function switchSpace(id: string) {
  if (id === spaceId.value) {
    return
  }
  spaceId.value = id
  selectedNode.value = null
  await reloadTree()
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

    <div class="wiki-shell">
      <!-- 图标导航 -->
      <nav class="wiki-rail" aria-label="工作台导航">
        <FaTooltip v-for="item in panels" :key="item.key" :text="item.label" side="right">
          <button
              type="button"
              class="wiki-rail__item"
              :class="{ 'wiki-rail__item--active': activePanel === item.key }"
              @click="activePanel = item.key"
          >
            <FaIcon :name="item.icon" class="wiki-rail__icon"/>
            <span class="wiki-rail__label">{{ item.label.replace('Wiki ', '') }}</span>
          </button>
        </FaTooltip>
      </nav>

      <!-- 知识库列表 -->
      <aside class="wiki-spaces">
        <div class="wiki-spaces__head">
          <span>知识库</span>
          <FaTooltip text="新建知识库" side="right">
            <button type="button" class="wiki-spaces__add" @click="createVisible = true">
              <FaIcon name="i-ri:add-line"/>
            </button>
          </FaTooltip>
        </div>
        <FaScrollArea class="wiki-spaces__list">
          <button
              v-for="item in spaces"
              :key="item.id"
              type="button"
              class="wiki-space-card"
              :class="{ 'wiki-space-card--active': item.id === spaceId }"
              @click="switchSpace(item.id || '')"
          >
            <span class="wiki-space-card__icon">
              <FaIcon name="i-ri:book-shelf-line"/>
            </span>
            <span class="wiki-space-card__body">
              <strong>{{ item.name }}</strong>
              <small>/wiki/{{ item.slug }}</small>
            </span>
            <FaIcon v-if="item.id === spaceId" name="i-ri:check-line" class="wiki-space-card__check"/>
            <span
                class="wiki-space-card__delete"
                :class="{ 'wiki-space-card__delete--busy': deletingSpaceId === item.id }"
                title="删除知识库"
                @click.stop="removeSpace(item)"
            >
              <FaIcon name="i-ri:delete-bin-line"/>
            </span>
          </button>
          <div v-if="!spaces.length" class="wiki-spaces__empty">
            <FaIcon name="i-ri:inbox-line"/>
            <p>暂无知识库</p>
          </div>
        </FaScrollArea>
      </aside>

      <!-- 主面板 -->
      <main class="wiki-main">
        <KeepAlive>
          <component :is="activeComponent" :key="activePanel"/>
        </KeepAlive>
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

.wiki-shell {
  display: flex;
  overflow: hidden;
  flex: 1;
  min-height: 0;
  border-top: 1px solid var(--color-border-2, var(--color-border-2));
  background: var(--color-fill-1, var(--color-fill-1));
}

/* 图标导航（固定宽度、独立纵向滚动，不随主区滚动） */
.wiki-rail {
  display: flex;
  overflow-x: hidden;
  overflow-y: auto;
  flex-direction: column;
  flex-shrink: 0;
  gap: 4px;
  width: 76px;
  min-height: 0;
  padding: 12px 8px;
  border-right: 1px solid var(--color-border-2);
  background: var(--color-bg-1);
}

.wiki-rail__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 9px 2px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
  font: inherit;
  transition: background 0.15s, color 0.15s;
}

.wiki-rail__item:hover {
  background: var(--color-fill-2);
  color: var(--color-text-1);
}

.wiki-rail__item--active {
  background: var(--color-fill-2);
  color: var(--color-text-1);
  font-weight: 600;
}

.wiki-rail__icon {
  font-size: 20px;
}

.wiki-rail__label {
  font-size: 11px;
  line-height: 1;
  white-space: nowrap;
}

/* 知识库列（固定宽度、内部 FaScrollArea 独立滚动） */
.wiki-spaces {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 216px;
  min-height: 0;
  border-right: 1px solid var(--color-border-2);
  background: var(--color-bg-1);
}

.wiki-spaces__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-3);
}

.wiki-spaces__add {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
}

.wiki-spaces__add:hover {
  background: var(--color-fill-2);
  color: var(--color-text-1);
}

.wiki-spaces__list {
  flex: 1;
  min-height: 0;
  padding: 0 10px 12px;
}

.wiki-space-card {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-bottom: 6px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: background 0.15s, border-color 0.15s;
}

.wiki-space-card:hover {
  background: var(--color-fill-2);
}

.wiki-space-card--active {
  border-color: var(--color-border-3);
  background: var(--color-fill-2);
}

.wiki-space-card__icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 9px;
  background: var(--color-fill-2);
  color: var(--color-text-1);
  font-size: 17px;
}

.wiki-space-card__body {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.wiki-space-card__body strong {
  overflow: hidden;
  color: var(--color-text-1);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wiki-space-card__body small {
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wiki-space-card__check {
  margin-left: auto;
  color: var(--color-text-1);
}

.wiki-space-card__delete {
  display: none;
  flex-shrink: 0;
  align-items: center;
  margin-left: auto;
  padding: 3px;
  border-radius: 6px;
  color: var(--color-text-3);
  font-size: 14px;
}

.wiki-space-card__check + .wiki-space-card__delete {
  margin-left: 0;
}

.wiki-space-card:hover .wiki-space-card__delete,
.wiki-space-card__delete--busy {
  display: inline-flex;
}

.wiki-space-card__delete:hover {
  background: var(--color-fill-3);
  color: rgb(var(--danger-6));
}

.wiki-spaces__empty {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 28px 0;
  color: var(--color-text-3);
  font-size: 12px;
}

/* 主区域 */
.wiki-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
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

/* 小屏：左侧图标导航始终保留且不压缩，仅收窄知识库列 */
@media (max-width: 1100px) {
  .wiki-spaces {
    width: 176px;
  }
}

@media (max-width: 860px) {
  .wiki-spaces {
    width: 148px;
  }

  .wiki-space-card {
    gap: 8px;
    padding: 8px;
  }

  .wiki-space-card__icon {
    width: 28px;
    height: 28px;
    font-size: 14px;
  }
}
</style>

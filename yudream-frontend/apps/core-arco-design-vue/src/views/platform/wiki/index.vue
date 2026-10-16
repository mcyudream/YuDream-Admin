<script setup lang="ts">
import type { LocationQuery } from 'vue-router'
import type { WikiWorkbenchView } from './wiki-utils'
import type { WikiNode, WikiSpace } from '@/api/modules/platform-wiki'
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { fetchWikiSpaces, fetchWikiTree } from '@/api/modules/platform-wiki'
import WikiAuthorEditor from './components/WikiAuthorEditor.vue'
import WikiEntryView from './components/WikiEntryView.vue'
import WikiManageView from './components/WikiManageView.vue'
import { defaultWikiManagePanel, flattenTree, isWikiManagePanel, wikiWorkbenchKey } from './wiki-utils'

/**
 * Wiki 工作台编排层：一个路由承载三个工作面（知识库入口 / 作者编辑 / 治理管理）。
 *
 * 状态所有权：工作台状态是本地 ref，URL 只在「进入/离开」两个方向同步——
 * 1. 进入：真实路由导航（深链、前进后退、外部链接）采用 query 作为初始状态；
 * 2. 离开：页面内切换用 `patchQuery` 静默改写地址栏（replaceState），
 *    宿主布局按 `route.fullPath` 重挂载页面，走 router.push 会把整页（含未保存草稿、
 *    目录与滚动位置）销毁重建，因此内部导航绝不触发路由导航。
 * 代价是 history 里不留中间步骤，前进后退会回到最近一次真实导航的位置，属可接受取舍。
 */
const toast = useYdToast()
const route = useRoute()
const workbenchPath = route.path

/** 地址栏查询的本地镜像：静默同步后 route.query 不再更新，它才是合并与深链回填的基底 */
const urlQuery = ref<Record<string, string>>(querySnapshot(route.query))

function querySnapshot(query: LocationQuery): Record<string, string> {
  const snapshot: Record<string, string> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string') {
      snapshot[key] = value
    }
    else if (Array.isArray(value) && typeof value[0] === 'string') {
      snapshot[key] = value[0]
    }
  }
  return snapshot
}

// 知识库列表三态：加载中 / 加载失败 / 空库，失败不得冒充「暂无知识库」
const spacesLoading = ref(true)
const spacesLoaded = ref(false)
const spacesError = ref('')

const spaces = ref<WikiSpace[]>([])
const spaceId = ref('')
const tree = ref<WikiNode[]>([])
const selectedNode = ref<WikiNode | null>(null)
const loadingTree = ref(false)

const view = ref<WikiWorkbenchView>(normalizeView(urlQuery.value.view))
const activePanel = ref<string>(isWikiManagePanel(urlQuery.value.panel) ? urlQuery.value.panel : defaultWikiManagePanel)
const space = computed(() => spaces.value.find(item => item.id === spaceId.value) || null)
const flatTree = computed(() => flattenTree(tree.value))

function normalizeView(raw: unknown): WikiWorkbenchView {
  return raw === 'author' || raw === 'manage' || raw === 'entry' ? raw : 'entry'
}

/** 合并后的查询对象：undefined / 空串表示移除该键 */
function mergedQuery(patch: Record<string, unknown>): Record<string, string> {
  const next: Record<string, string> = { ...urlQuery.value }
  for (const [key, value] of Object.entries(patch)) {
    if (value === undefined || value === null || value === '') {
      delete next[key]
    }
    else {
      next[key] = String(value)
    }
  }
  return next
}

/**
 * 页面内导航：先切本地状态（界面立即响应），再静默同步地址栏。
 * 不走 router.push，避免宿主布局按 fullPath 重挂载整页。
 *
 * spaceId 不是纯地址栏参数：它决定空间选择、目录树与属性栏。只写地址栏会让
 * 「列表点卡片进入编辑区」停在旧空间（表现为进入后显示「未选择」、目录树空白），
 * 随后进治理区时 openManage 又会因为状态里没有空间而把地址栏里的知识库一并抹掉。
 * 因此带 spaceId / view 的补丁统一交给 applyRoute 收敛状态；applyRoute 内部是单次
 * 收敛（补正地址栏时直接写，不再回调 patchQuery），不会互相递归。
 */
function patchQuery(patch: Record<string, unknown>) {
  const next = mergedQuery(patch)
  urlQuery.value = next
  if ('view' in patch) {
    view.value = normalizeView(next.view)
  }
  if ('view' in patch || 'panel' in patch) {
    activePanel.value = isWikiManagePanel(next.panel) ? next.panel : defaultWikiManagePanel
  }
  syncUrl(next)
  if ('spaceId' in patch || 'view' in patch) {
    applyRoute()
  }
}

/** 静默地址栏同步：只替换当前 history 条目，不产生导航，因此不触发页面重挂载 */
function syncUrl(query: Record<string, string>) {
  const search = new URLSearchParams(query).toString()
  const url = search ? `${workbenchPath}?${search}` : workbenchPath
  window.history.replaceState(window.history.state, '', url)
}

let treeRequest = 0
/** 目录树已成功加载的知识库 ID：入口页不预加载，进入作者/治理区时才按需拉取 */
const treeLoadedFor = ref('')

async function reloadTree() {
  const request = ++treeRequest
  const id = spaceId.value
  if (!id) {
    tree.value = []
    treeLoadedFor.value = ''
    loadingTree.value = false
    return
  }
  loadingTree.value = true
  try {
    const res = await fetchWikiTree(id)
    if (request === treeRequest && id === spaceId.value) {
      tree.value = res.data || []
      treeLoadedFor.value = id
      const selectedId = selectedNode.value?.id
      if (selectedId) {
        selectedNode.value = flattenTree(tree.value).find(item => item.id === selectedId) || null
      }
    }
  }
  catch {
    // 失败时保留当前目录，不把请求失败渲染成「暂无页面」
  }
  finally {
    if (request === treeRequest) {
      loadingTree.value = false
    }
  }
}

async function loadSpaces() {
  spacesLoading.value = true
  spacesError.value = ''
  try {
    const res = await fetchWikiSpaces()
    spaces.value = res.data || []
    spacesLoaded.value = true
    applyRoute()
  }
  catch (error) {
    // 刷新失败保留已读内容；首次失败由「加载失败」状态呈现，接口层已给出原因提示
    spacesError.value = error instanceof Error && error.message ? error.message : '知识库列表加载失败，请稍后重试'
  }
  finally {
    spacesLoading.value = false
  }
}

/**
 * 当前查询 → 状态：解析知识库与选中页面。
 * 只在真实差异处写入，避免与「状态 → 地址栏」的同步互相触发。
 */
function applyRoute() {
  if (route.path !== workbenchPath || !spacesLoaded.value) {
    return
  }
  const query = urlQuery.value
  const requestedView = normalizeView(query.view)
  view.value = requestedView
  if (isWikiManagePanel(query.panel)) {
    activePanel.value = query.panel
  }
  const isEntry = requestedView === 'entry'
  const requestedSpaceId = query.spaceId ?? ''
  const validRequested = spaces.value.find(item => item.id === requestedSpaceId)?.id
  const currentValid = spaces.value.find(item => item.id === spaceId.value)?.id
  const nextSpaceId = validRequested || (isEntry ? currentValid || '' : currentValid || spaces.value[0]?.id || '')
  if (nextSpaceId !== spaceId.value) {
    spaceId.value = nextSpaceId
    selectedNode.value = null
    tree.value = []
    treeLoadedFor.value = ''
    treeRequest++
    loadingTree.value = false
  }
  if (!isEntry && nextSpaceId && !validRequested) {
    // 深链缺少或写错 spaceId：补成实际生效的知识库，保证地址可分享。
    // 直接写地址栏而不是回调 patchQuery：这里必须继续往下把目录与选中页面收敛完（单次收敛，不递归）。
    const patched = mergedQuery({ spaceId: nextSpaceId })
    urlQuery.value = patched
    syncUrl(patched)
  }
  if (!isEntry && spaceId.value && treeLoadedFor.value !== spaceId.value && !loadingTree.value) {
    void reloadTree()
    return
  }
  const requestedNodeId = query.nodeId ?? ''
  if (view.value === 'author' && requestedNodeId !== (selectedNode.value?.id || '') && treeLoadedFor.value === spaceId.value) {
    const target = requestedNodeId ? flattenTree(tree.value).find(item => item.id === requestedNodeId) || null : null
    if (requestedNodeId && !target) {
      toast.warning('地址中的页面已不存在，已回到目录选择')
      selectedNode.value = null
      patchQuery({ nodeId: undefined })
      return
    }
    selectedNode.value = target
  }
  if (!requestedNodeId && view.value !== 'author' && selectedNode.value) {
    selectedNode.value = null
  }
}

// 真实路由导航（深链、前进后退、外部跳转）才重新采用 query；内部静默同步不触发路由，不会走到这里
watch(() => route.fullPath, () => {
  if (route.path !== workbenchPath) {
    return
  }
  urlQuery.value = querySnapshot(route.query)
  applyRoute()
})
watch(spacesLoaded, () => applyRoute())
// 目录加载完成后补齐深链里的 nodeId（首帧树还是空的）
watch(tree, () => applyRoute())

// 状态 → 地址栏：作者区里点选页面后静默写回地址，刷新不丢当前页且不重挂载工作台
watch(selectedNode, (value) => {
  if (view.value !== 'author') {
    return
  }
  if ((urlQuery.value.nodeId ?? '') === (value?.id || '')) {
    return
  }
  patchQuery({ nodeId: value?.id || undefined, spaceId: spaceId.value || undefined })
})

let editorGuard: { confirmLeave: () => Promise<boolean>, dirty: () => boolean } | undefined
function registerEditorGuard(guard: NonNullable<typeof editorGuard>) {
  editorGuard = guard
  return () => {
    if (editorGuard === guard) {
      editorGuard = undefined
    }
  }
}

/**
 * 切换编辑目标：多标签下打开/激活标签本身不丢草稿，因此不再询问；
 * 真正会丢草稿的「离开编辑工作区」确认放在下面的导航守卫里。
 */
async function selectNode(node: WikiNode | null): Promise<boolean> {
  selectedNode.value = node
  return true
}

onBeforeRouteLeave(() => editorGuard?.confirmLeave() ?? true)
onBeforeRouteUpdate((to, from) => {
  const leavingAuthor = from.query.view === 'author' && to.query.view !== 'author'
  if (to.query.spaceId !== from.query.spaceId || to.query.nodeId !== from.query.nodeId || leavingAuthor) {
    return editorGuard?.confirmLeave() ?? true
  }
})
function warnUnsaved(event: BeforeUnloadEvent) {
  if (!editorGuard?.dirty()) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}
onMounted(() => window.addEventListener('beforeunload', warnUnsaved))
onBeforeUnmount(() => window.removeEventListener('beforeunload', warnUnsaved))

function findNode(ref: { title?: string, nodeId?: string, path?: string }): WikiNode | null {
  const pages = flatTree.value.filter(node => node.nodeType === 'PAGE')
  if (ref.nodeId) {
    const byId = pages.find(node => node.id === ref.nodeId)
    if (byId) {
      return byId
    }
  }
  if (ref.path) {
    const byPath = pages.find(node => node.path === ref.path || node.slug === ref.path)
    if (byPath) {
      return byPath
    }
  }
  return (ref.title && pages.find(node => node.title === ref.title)) || null
}

/**
 * 内部导航守卫：守卫对象由编辑区注册；只有真正离开编辑工作区（回入口 / 进治理 / 换知识库）
 * 才先确认未保存草稿——页面内切换不再产生路由导航，路由守卫不会替我们拦这一下。
 * 取消确认时原地不动，地址栏与状态都不变。
 */
async function guardedNavigate(patch: Record<string, unknown>) {
  const leavingWorkspace = view.value === 'author'
    && (patch.view !== 'author' || String(patch.spaceId ?? '') !== spaceId.value)
  if (leavingWorkspace && editorGuard && !await editorGuard.confirmLeave()) {
    return
  }
  patchQuery(patch)
}

/**
 * 进入某个工作面时的知识库解析：状态优先，其次地址栏。
 * 兜底是防「状态还没收敛就导航」这类顺序问题把知识库从工作面上抹掉（进治理区曾因此变成「暂无知识库」）。
 * 地址里的无效值不会被带进来：applyRoute 会按可见知识库校验，非法值自动回落到实际生效的知识库。
 */
function effectiveSpaceId(): string {
  return spaceId.value || urlQuery.value.spaceId || ''
}

function openEntry() {
  void guardedNavigate({ view: 'entry', nodeId: undefined, panel: undefined })
}

function openAuthor(target?: { space?: WikiSpace, node?: WikiNode | null }) {
  const nextSpaceId = target?.space?.id || effectiveSpaceId()
  const sameSpace = !target?.space || target.space.id === spaceId.value
  const nodeId = target?.node?.id || (sameSpace ? urlQuery.value.nodeId : undefined)
  // 必须显式传 spaceId，避免从知识库列表进入时丢失上下文
  void guardedNavigate({ view: 'author', spaceId: nextSpaceId || undefined, nodeId, panel: undefined })
}

function openManage(panel?: string, target?: WikiSpace) {
  void guardedNavigate({
    view: 'manage',
    panel: panel || activePanel.value,
    spaceId: target?.id || effectiveSpaceId() || undefined,
    nodeId: undefined,
  })
}

function openPanel(panel: string) {
  openManage(panel)
}

async function openPage(ref: { title?: string, nodeId?: string, path?: string }) {
  const node = findNode(ref)
  if (!node) {
    toast.warning(`未找到页面「${ref.title || ref.path || ref.nodeId}」`)
    return
  }
  if (await selectNode(node)) {
    openAuthor({ node })
  }
}

function clearSpace() {
  spaceId.value = ''
  selectedNode.value = null
  tree.value = []
  treeLoadedFor.value = ''
}

provide(wikiWorkbenchKey, {
  spaces,
  spaceId,
  space,
  tree,
  flatTree,
  selectedNode,
  loadingTree,
  spacesLoading,
  spacesLoaded,
  spacesError,
  view,
  activePanel,
  reloadTree,
  reloadSpaces: loadSpaces,
  clearSpace,
  selectNode,
  registerEditorGuard,
  openEntry,
  openAuthor,
  openManage,
  openPanel,
  openPage,
  findNode,
})

onMounted(loadSpaces)
</script>

<template>
  <div class="wiki-workbench">
    <!-- 知识库列表三态：加载 / 失败 / 内容（入口页承担空库状态与新建） -->
    <div v-if="spacesLoading && !spacesLoaded" class="wiki-workbench__state">
      <YdLoading text="正在加载知识库" />
    </div>
    <div v-else-if="!spacesLoaded && spacesError" class="wiki-workbench__state">
      <YdResult status="error" title="知识库列表加载失败" :description="spacesError">
        <template #action>
          <YdButton variant="outline" :loading="spacesLoading" @click="loadSpaces">
            <YdIcon name="morph:refresh-cw" />
            重新加载
          </YdButton>
        </template>
      </YdResult>
    </div>
    <!-- 入口页是普通内容页，自身滚动；作者/治理是定高工作区，外壳不滚、面板内滚 -->
    <div v-else-if="view === 'entry' || !spaces.length" class="wiki-workbench__entry">
      <WikiEntryView />
    </div>
    <WikiAuthorEditor v-else-if="view === 'author'" />
    <WikiManageView v-else />
  </div>
</template>

<style scoped>
/*
 * 工作台始终铺满宿主内容区并自带背景：宿主 .main 的占位高度来自 flex 拉伸，其计算高度仍是 auto，
 * 子级 height:100% 无法解析，因此用绝对定位铺满（.main 自身 position:relative），
 * 三个工作面共享同一背景与高度来源，避免局部留白或入口页下方露底色。
 */
.wiki-workbench {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--color-bg-1);
}

.wiki-workbench__state {
  display: grid;
  flex: 1;
  align-content: center;
  justify-items: center;
  min-width: 0;
  min-height: 0;
  padding: 24px 16px;
  overflow: auto;
}

.wiki-workbench__entry {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
}
</style>

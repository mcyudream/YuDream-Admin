<script setup lang="ts">
import type { FeishuFailure, FeishuFileItem, FeishuSpaceItem, FeishuWikiNodeItem } from './feishu-contract.ts'
import { computed, onMounted, ref } from 'vue'
import apiFeishu from '@/api/modules/platform-feishu.ts'
import FeishuDocumentDetail from './components/FeishuDocumentDetail.vue'
import FeishuFileBrowser from './components/FeishuFileBrowser.vue'
import FeishuQuotaPanel from './components/FeishuQuotaPanel.vue'
import FeishuSourceNav from './components/FeishuSourceNav.vue'
import FeishuWikiNodeBrowser from './components/FeishuWikiNodeBrowser.vue'
import {
  appendPage,
  fileTypeOptions,
  filterFileItems,
  normalizeFeishuFailure,
  resolveCloudDocPage,
  resolveWikiNodePage,
  resolveWikiSpacePage,
} from './feishu-contract.ts'

const isMobile = useIsMobile()
const quotaPanel = ref<InstanceType<typeof FeishuQuotaPanel>>()
const detailRef = ref<InstanceType<typeof FeishuDocumentDetail>>()

type FeishuView = 'documents' | 'quota'
const activeView = ref<FeishuView>('documents')
const viewOptions = [
  { label: '文档', value: 'documents' as const },
  { label: '额度与限流', value: 'quota' as const },
]

const filesLoading = ref(false)
const filesLoadingMore = ref(false)
const filesFailure = ref<FeishuFailure | null>(null)
const files = ref<FeishuFileItem[]>([])
const filesPageToken = ref<string | undefined>()
const filesHasMore = ref(false)

const spacesLoading = ref(false)
const spacesFailure = ref<FeishuFailure | null>(null)
const spaces = ref<FeishuSpaceItem[]>([])
const spacesPageToken = ref<string | undefined>()
const spacesHasMore = ref(false)

const activeSpaceId = ref('')
const wikiNodes = ref<FeishuWikiNodeItem[]>([])
const wikiNodeTrail = ref<FeishuWikiNodeItem[]>([])
const wikiNodesLoading = ref(false)
const wikiNodesLoadingMore = ref(false)
const wikiNodesFailure = ref<FeishuFailure | null>(null)
const wikiNodesPageToken = ref<string | undefined>()
const wikiNodesHasMore = ref(false)
let wikiRequestGeneration = 0
const unsupportedWikiNode = ref<FeishuWikiNodeItem | null>(null)

/** 目录下钻栈：末项为当前 folderToken；为空表示应用根目录。 */
const folderTrail = ref<{ token: string, name: string }[]>([])
const activeDocumentId = ref('')
const navOpen = ref(false)
const typeFilter = ref('')
const keyword = ref('')

const visibleFiles = computed(() => {
  const byType = typeFilter.value ? files.value.filter(item => item.type === typeFilter.value) : files.value
  return filterFileItems(byType, keyword.value)
})
const typeOptions = computed(() => fileTypeOptions(files.value))
const foldersHere = computed(() => files.value.filter(item => item.isFolder))
const filtered = computed(() => Boolean(keyword.value.trim()) || (!activeSpaceId.value && Boolean(typeFilter.value)))
const filteredWikiNodes = computed(() => {
  const needle = keyword.value.trim().toLocaleLowerCase()
  if (!needle) {
    return wikiNodes.value
  }
  return wikiNodes.value.filter(node => [node.title, node.nodeToken, node.objType].some(value => value?.toLocaleLowerCase().includes(needle)))
})

function currentFolderToken() {
  return folderTrail.value.at(-1)?.token ?? ''
}

async function loadFiles(reset = true) {
  if (reset) {
    filesLoading.value = true
    filesFailure.value = null
  }
  else {
    filesLoadingMore.value = true
  }
  try {
    const { data } = await apiFeishu.listDriveFiles({
      folderToken: currentFolderToken() || undefined,
      pageToken: reset ? undefined : filesPageToken.value,
    })
    const page = resolveCloudDocPage(data)
    files.value = reset ? page.items : appendPage(files.value, page.items, item => item.token)
    filesPageToken.value = page.nextPageToken
    filesHasMore.value = page.hasMore
    filesFailure.value = null
  }
  catch (error) {
    filesFailure.value = normalizeFeishuFailure(error, 'collection')
    if (reset) {
      files.value = []
      filesPageToken.value = undefined
      filesHasMore.value = false
    }
  }
  finally {
    filesLoading.value = false
    filesLoadingMore.value = false
  }
}

async function loadSpaces(reset = true) {
  if (reset) {
    spacesLoading.value = true
    spacesFailure.value = null
  }
  try {
    const { data } = await apiFeishu.listWikiSpaces({ pageToken: reset ? undefined : spacesPageToken.value })
    const page = resolveWikiSpacePage(data)
    spaces.value = reset ? page.items : appendPage(spaces.value, page.items, item => item.spaceId)
    spacesPageToken.value = page.nextPageToken
    spacesHasMore.value = page.hasMore
    spacesFailure.value = null
  }
  catch (error) {
    spacesFailure.value = normalizeFeishuFailure(error, 'collection')
    if (reset) {
      spaces.value = []
    }
  }
  finally {
    spacesLoading.value = false
  }
}

async function loadWikiNodes(reset = true) {
  if (!activeSpaceId.value || (wikiNodesLoading.value && !reset) || (wikiNodesLoadingMore.value && !reset) || (!reset && (!wikiNodesHasMore.value || !wikiNodesPageToken.value))) {
    return
  }
  if (reset) {
    wikiNodesLoading.value = true
    wikiNodesFailure.value = null
  }
  else {
    wikiNodesLoadingMore.value = true
  }
  const spaceId = activeSpaceId.value
  const parentNodeToken = wikiNodeTrail.value.at(-1)?.nodeToken
  const requestGeneration = ++wikiRequestGeneration
  try {
    const { data } = await apiFeishu.listWikiNodes(spaceId, {
      parentNodeToken,
      pageToken: reset ? undefined : wikiNodesPageToken.value,
    })
    if (requestGeneration !== wikiRequestGeneration) {
      return
    }
    const page = resolveWikiNodePage(data)
    wikiNodes.value = reset ? page.items : appendPage(wikiNodes.value, page.items, node => node.nodeToken)
    wikiNodesPageToken.value = page.nextPageToken
    wikiNodesHasMore.value = page.hasMore
    wikiNodesFailure.value = null
  }
  catch (error) {
    if (requestGeneration !== wikiRequestGeneration) {
      return
    }
    wikiNodesFailure.value = normalizeFeishuFailure(error, 'collection')
    if (reset) {
      wikiNodes.value = []
      wikiNodesPageToken.value = undefined
      wikiNodesHasMore.value = false
    }
  }
  finally {
    if (requestGeneration === wikiRequestGeneration) {
      wikiNodesLoading.value = false
      wikiNodesLoadingMore.value = false
    }
  }
}

function resetWikiNodes() {
  wikiRequestGeneration++
  activeSpaceId.value = ''
  wikiNodes.value = []
  wikiNodeTrail.value = []
  wikiNodesPageToken.value = undefined
  wikiNodesHasMore.value = false
  wikiNodesFailure.value = null
  wikiNodesLoading.value = false
  wikiNodesLoadingMore.value = false
  unsupportedWikiNode.value = null
}

function refresh() {
  if (activeView.value === 'quota') {
    quotaPanel.value?.reload()
    return
  }
  if (activeSpaceId.value) {
    void loadWikiNodes(true)
    void loadSpaces(true)
    return
  }
  void Promise.all([loadFiles(true), loadSpaces(true)])
}

/**
 * 离开守卫：切换文档、目录或视图前统一调用。
 * 写入进行中直接阻止；有未保存编辑时给出明确确认，只有确认后才放弃草稿，绝不静默丢弃。
 */
async function leaveGuard(action: () => void) {
  const detail = detailRef.value
  // 未确认前不动任何状态：草稿与当前文档保持不变
  if (detail && !await detail.confirmLeave()) {
    return false
  }
  action()
  return true
}

async function onViewChange(next: FeishuView) {
  if (next !== 'quota' || !detailRef.value) {
    return
  }
  // 切到额度视图会卸载文档详情，必须先守卫再切换
  activeView.value = 'documents'
  await leaveGuard(() => {
    activeView.value = 'quota'
  })
}

function resetFilters() {
  keyword.value = ''
  typeFilter.value = ''
}

async function openFolder(item: FeishuFileItem) {
  await leaveGuard(() => {
    resetWikiNodes()
    folderTrail.value = [...folderTrail.value, { token: item.token, name: item.name || item.token }]
    activeDocumentId.value = ''
    resetFilters()
    navOpen.value = false
    void loadFiles(true)
  })
}

async function openRoot() {
  await leaveGuard(() => {
    resetWikiNodes()
    folderTrail.value = []
    activeDocumentId.value = ''
    resetFilters()
    navOpen.value = false
    void loadFiles(true)
  })
}

async function goToFolder(index: number) {
  await leaveGuard(() => {
    resetWikiNodes()
    folderTrail.value = folderTrail.value.slice(0, index)
    activeDocumentId.value = ''
    resetFilters()
    navOpen.value = false
    void loadFiles(true)
  })
}

async function selectSpace(spaceId: string) {
  if (spaceId === activeSpaceId.value && !activeDocumentId.value && !unsupportedWikiNode.value) {
    navOpen.value = false
    return
  }
  await leaveGuard(() => {
    wikiRequestGeneration++
    activeSpaceId.value = spaceId
    wikiNodes.value = []
    wikiNodeTrail.value = []
    wikiNodesPageToken.value = undefined
    wikiNodesHasMore.value = false
    wikiNodesFailure.value = null
    unsupportedWikiNode.value = null
    folderTrail.value = []
    activeDocumentId.value = ''
    resetFilters()
    navOpen.value = false
    void loadWikiNodes(true)
  })
}

async function openWikiNode(node: FeishuWikiNodeItem) {
  await leaveGuard(() => {
    navOpen.value = false
    if (!node.hasChild) {
      unsupportedWikiNode.value = node
      activeDocumentId.value = ''
      return
    }
    wikiNodeTrail.value = [...wikiNodeTrail.value, node]
    wikiNodes.value = []
    wikiNodesPageToken.value = undefined
    wikiNodesHasMore.value = false
    void loadWikiNodes(true)
  })
}

async function openWikiDocument(node: FeishuWikiNodeItem) {
  await leaveGuard(() => {
    navOpen.value = false
    if (node.objType === 'docx' && node.objToken) {
      unsupportedWikiNode.value = null
      activeDocumentId.value = node.objToken
      return
    }
    unsupportedWikiNode.value = node
    activeDocumentId.value = ''
  })
}

async function goToWikiNodeParent() {
  if (!wikiNodeTrail.value.length) {
    return
  }
  await leaveGuard(() => {
    wikiNodeTrail.value = wikiNodeTrail.value.slice(0, -1)
    wikiNodes.value = []
    wikiNodesPageToken.value = undefined
    wikiNodesHasMore.value = false
    unsupportedWikiNode.value = null
    activeDocumentId.value = ''
    navOpen.value = false
    void loadWikiNodes(true)
  })
}

async function openDocument(id: string) {
  await leaveGuard(() => {
    resetWikiNodes()
    activeDocumentId.value = id
    navOpen.value = false
  })
}

async function closeDocument() {
  await leaveGuard(() => {
    activeDocumentId.value = ''
    if (activeSpaceId.value) {
      void loadWikiNodes(true)
    }
  })
}

/** 侧栏菜单等整页离开路径：同一处守卫，未确认不放行。 */
onBeforeRouteLeave(() => detailRef.value?.confirmLeave() ?? true)
onBeforeRouteUpdate(() => detailRef.value?.confirmLeave() ?? true)

function warnUnsaved(event: BeforeUnloadEvent) {
  if (!detailRef.value?.hasUnsavedEdits()) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  void loadFiles(true)
  void loadSpaces(true)
  window.addEventListener('beforeunload', warnUnsaved)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', warnUnsaved)
})
</script>

<template>
  <div class="feishu-page">
    <YdPageHeader title="飞书集成" class="feishu-page__header mb-0">
      <template #description>
        浏览飞书云空间与知识库、按块编辑文本内容，并管理飞书接口的每用户调用额度；写操作只使用后端已核实的飞书接口。
      </template>
      <YdSegmented v-model="activeView" :options="viewOptions" size="sm" aria-label="飞书视图切换" @change="onViewChange" />
      <YdButton variant="outline" :loading="activeSpaceId ? wikiNodesLoading : filesLoading" @click="refresh">
        <YdIcon name="morph:refresh-cw" />
        刷新
      </YdButton>
    </YdPageHeader>

    <div class="feishu-page__body">
      <div v-if="activeView === 'documents'" class="feishu-documents-view">
        <div class="feishu-workbench">
          <FeishuSourceNav
            v-if="!isMobile"
            class="feishu-workbench__nav"
            :trail="folderTrail"
            :folders="foldersHere"
            :spaces="spaces"
            :active-space-id="activeSpaceId"
            :spaces-loading="spacesLoading"
            :spaces-failure="spacesFailure"
            :spaces-has-more="spacesHasMore"
            @open-root="openRoot"
            @open-folder="openFolder"
            @go-to-folder="goToFolder"
            @select-space="selectSpace"
            @load-more-spaces="loadSpaces(false)"
            @retry-spaces="loadSpaces(true)"
          />
          <section class="feishu-workbench__main">
            <YdListToolbar
              v-model:keyword="keyword"
              :title="activeSpaceId ? (spaces.find(space => space.spaceId === activeSpaceId)?.name || '知识库节点') : '文档工作台'"
              placeholder="搜索名称 / token（仅过滤已加载）"
              :search-width="220"
            >
              <template #views>
                <span class="feishu-toolbar__summary">
                  {{ activeSpaceId ? `${wikiNodes.length} 个节点已加载` : `${files.length} 项已加载` }}
                  <span v-if="activeSpaceId && keyword.trim()"> · 命中 {{ filteredWikiNodes.length }} 条</span>
                  <span v-else-if="filtered"> · 命中 {{ visibleFiles.length }} 条</span>
                </span>
              </template>
              <template #filters>
                <YdFilterSelect v-if="!activeSpaceId" v-model="typeFilter" :options="typeOptions" :width="170" placeholder="全部类型" :disabled="!typeOptions.length" />
                <YdButton v-if="isMobile" variant="outline" @click="navOpen = true">
                  <YdIcon name="morph:panel-left" />
                  站点与目录
                </YdButton>
              </template>
              <template #actions>
                <YdButton v-if="filtered" variant="ghost" @click="resetFilters">
                  重置
                </YdButton>
              </template>
            </YdListToolbar>

            <FeishuDocumentDetail
              v-if="activeDocumentId"
              ref="detailRef"
              :key="activeDocumentId"
              :document-id="activeDocumentId"
              @back="closeDocument"
            />
            <FeishuWikiNodeBrowser
              v-else-if="activeSpaceId && !unsupportedWikiNode"
              :space="spaces.find(space => space.spaceId === activeSpaceId) || { spaceId: activeSpaceId, name: activeSpaceId }"
              :nodes="filteredWikiNodes"
              :trail="wikiNodeTrail"
              :loading="wikiNodesLoading"
              :loading-more="wikiNodesLoadingMore"
              :failure="wikiNodesFailure"
              :has-more="wikiNodesHasMore"
              :filtered="Boolean(keyword.trim())"
              @open-node="openWikiNode"
              @open-document="openWikiDocument"
              @go-parent="goToWikiNodeParent"
              @load-more="loadWikiNodes(false)"
              @retry="loadWikiNodes(true)"
            />
            <YdResult
              v-else-if="unsupportedWikiNode"
              status="info"
              title="此资源类型尚未接入预览"
              :description="`“${unsupportedWikiNode.title || unsupportedWikiNode.nodeToken}”的飞书资源类型为 ${unsupportedWikiNode.objType || '未返回'}。当前仅支持 docx 文档；节点信息保留，未伪装成可打开的文档。`"
            >
              <YdButton variant="outline" @click="unsupportedWikiNode = null">
                返回知识库目录
              </YdButton>
            </YdResult>
            <FeishuFileBrowser
              v-else
              :items="visibleFiles"
              :loaded-total="files.length"
              :loading="filesLoading"
              :loading-more="filesLoadingMore"
              :failure="filesFailure"
              :has-more="filesHasMore"
              :filtered="filtered"
              :folder-name="folderTrail.at(-1)?.name || '应用根目录'"
              @select="openDocument"
              @open-folder="openFolder"
              @retry="loadFiles(true)"
              @load-more="loadFiles(false)"
              @reset="resetFilters"
            />
          </section>
        </div>

        <YdDrawer v-if="isMobile" v-model="navOpen" side="left" title="站点与目录" :bordered="true">
          <FeishuSourceNav
            :trail="folderTrail"
            :folders="foldersHere"
            :spaces="spaces"
            :active-space-id="activeSpaceId"
            :spaces-loading="spacesLoading"
            :spaces-failure="spacesFailure"
            :spaces-has-more="spacesHasMore"
            @open-root="openRoot"
            @open-folder="openFolder"
            @go-to-folder="goToFolder"
            @select-space="selectSpace"
            @load-more-spaces="loadSpaces(false)"
            @retry-spaces="loadSpaces(true)"
          />
        </YdDrawer>
      </div>

      <div v-else class="feishu-page__quota">
        <FeishuQuotaPanel ref="quotaPanel" />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 路由内容区已经扣除宿主页头；此页填满剩余高度，左右栏各自管理滚动。 */
.feishu-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--color-bg-1);
}

.feishu-page__header {
  flex-shrink: 0;
}

.feishu-page__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: var(--color-bg-1);
}

.feishu-documents-view {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.feishu-page__quota {
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}

.feishu-toolbar__summary {
  font-size: 12px;
  color: var(--color-text-3);
}

.feishu-workbench {
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr);
  gap: 0;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  align-items: stretch;
  overflow: hidden;
  background: var(--color-bg-1);
}

.feishu-workbench__nav {
  height: 100%;
  min-height: 0;
  border-right: 1px solid var(--color-border-2);
}

.feishu-workbench__main {
  display: grid;
  gap: 16px;
  align-content: start;
  min-width: 0;
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
  background: var(--color-bg-1);
}

@media (width <= 1023px) {
  .feishu-workbench {
    grid-template-columns: 232px minmax(0, 1fr);
  }
}

@media (width <= 768px) {
  .feishu-workbench {
    grid-template-columns: minmax(0, 1fr);
  }

  .feishu-workbench__main {
    padding: 12px;
  }
}
</style>

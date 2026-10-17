<script setup lang="ts">
import type { WikiNode } from '@/api/modules/platform-wiki'
import { computed, inject, onMounted, ref, watch } from 'vue'
import { fetchWikiIngestTasks, fetchWikiSources, saveWikiNode } from '@/api/modules/platform-wiki'
import AiRunProgress from '@/components/AiRunProgress/index.vue'
import { moveWikiNode } from '../wiki-node-ops'
import { pageTypeColor, pageTypeLabel, wikiWorkbenchKey } from '../wiki-utils'

// 生成内容：一级目录 llm-gen 的真实入口。
// 目录是真实节点（出现在作者目录树里），页面只有被移入后才会归到它下面；
// 按页面类型分组只是同一批页面的阅读视图，不代表它们已经在 llm-gen 里。
// 来源事实只用已存在的链路推导：页面 sources → 产出它的摄入任务 AI 运行；
// 模型与 prompt revision 需要写入时固化的字段，尚未提供，界面不显示「AI 生成」徽标也不猜。
const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const modal = useYdModal()
const account = useAppAccountStore()

// —— 页面来源：资料标题 + 产出该页面的 AI 运行（与摄入面板同口径：只对发起人可见）——
const sourceTitles = ref<Record<string, string>>({})
const runBySourceId = ref<Record<string, { runId: string, requestedByUserId?: string }>>({})
const provenanceRunId = ref('')
const runDrawerOpen = ref(false)

async function loadProvenance() {
  if (!store.spaceId.value) {
    sourceTitles.value = {}
    runBySourceId.value = {}
    return
  }
  try {
    const [sources, tasks] = await Promise.all([
      fetchWikiSources(store.spaceId.value),
      fetchWikiIngestTasks(store.spaceId.value),
    ])
    const titles: Record<string, string> = {}
    for (const source of sources.data || []) {
      titles[source.id] = source.title || source.fileName
    }
    sourceTitles.value = titles
    const runs: Record<string, { runId: string, requestedByUserId?: string }> = {}
    for (const task of [...(tasks.data || [])].sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))) {
      if (task.sourceId && task.aiRunId) {
        runs[task.sourceId] = { runId: task.aiRunId, requestedByUserId: task.requestedByUserId }
      }
    }
    runBySourceId.value = runs
  }
  catch {
    // 来源是只读补充：拉取失败时列表照常展示，不误导成「没有来源」
    sourceTitles.value = {}
    runBySourceId.value = {}
  }
}

function sourceLabels(page: WikiNode): string[] {
  return (page.sources || []).map(id => sourceTitles.value[id] || `资料 ${id}`)
}

function pageRun(page: WikiNode) {
  for (const sourceId of page.sources || []) {
    const run = runBySourceId.value[sourceId]
    if (run) {
      return run
    }
  }
  return undefined
}

function canViewRun(page: WikiNode) {
  const run = pageRun(page)
  return Boolean(run) && account.isLogin && String(run?.requestedByUserId || '') === String(account.userId)
}

function viewRun(page: WikiNode) {
  const run = pageRun(page)
  if (!run || !canViewRun(page)) {
    return
  }
  provenanceRunId.value = run.runId
  runDrawerOpen.value = true
}

onMounted(loadProvenance)
watch(() => store.spaceId.value, loadProvenance)

const keyword = ref('')
const creatingDir = ref(false)
const SYSTEM_PAGE_TYPES = new Set<string>([
  'index',
  'overview',
  'log',
  'source_document',
])
const systemRootSlugs = new Set(['index', 'overview', 'log'])
const movingNodeId = ref('')
const movingAll = ref(false)

/** 一级目录 llm-gen：真实根节点（按 slug / 标题匹配） */
const llmGenDirectory = computed(() => store.tree.value.find(item =>
  item.nodeType === 'DIRECTORY' && (item.slug === 'llm-gen' || item.title === 'llm-gen')) || null)
const llmGenPath = computed(() => llmGenDirectory.value?.path || llmGenDirectory.value?.slug || 'llm-gen')

const pages = computed(() => store.flatTree.value.filter(item => item.nodeType === 'PAGE'))
const generatedOrigin = (page: WikiNode) => /^---\r?\n[\s\S]*?^origin:\s*llm\s*$/m.test(page.markdown || '')

const filteredPages = computed(() => {
  const text = keyword.value.trim().toLocaleLowerCase()
  if (!text) {
    return pages.value
  }
  return pages.value.filter(item => `${item.title} ${item.path || ''}`.toLocaleLowerCase().includes(text))
})

const groups = computed(() => {
  const buckets = new Map<string, WikiNode[]>()
  for (const page of filteredPages.value) {
    const key = page.pageType || ''
    const bucket = buckets.get(key)
    if (bucket) {
      bucket.push(page)
    }
    else {
      buckets.set(key, [page])
    }
  }
  return [...buckets.entries()]
    .map(([key, items]) => ({
      key,
      label: key ? pageTypeLabel(key) : '未标记类型',
      color: key ? pageTypeColor(key) : 'var(--color-text-3)',
      items: items.sort((left, right) => left.title.localeCompare(right.title, 'zh-Hans-CN')),
    }))
    .sort((left, right) => right.items.length - left.items.length)
})

function openPage(page: WikiNode) {
  void store.openPage({ nodeId: page.id, title: page.title, path: page.path })
}

/** 页面是否已经在 llm-gen 目录下（用祖先路径判断，避免只比较 parentId） */
function insideLlmGen(page: WikiNode) {
  const dir = llmGenDirectory.value
  if (!dir) {
    return false
  }
  if (page.parentId === dir.id) {
    return true
  }
  const prefix = `${llmGenPath.value}/`
  return Boolean(page.path && page.path.startsWith(prefix))
}

const pagesOutsideLlmGen = computed(() => filteredPages.value.filter((page) => {
  if (insideLlmGen(page) || SYSTEM_PAGE_TYPES.has(page.pageType || '')) {
    return false
  }
  return !(page.parentId == null && systemRootSlugs.has(page.slug))
}))
const generatedPagesOutsideLlmGen = computed(() => pagesOutsideLlmGen.value.filter(generatedOrigin))
const legacyRootPages = computed(() => filteredPages.value.filter((page) => {
  if (insideLlmGen(page) || SYSTEM_PAGE_TYPES.has(page.pageType || '') || systemRootSlugs.has(page.slug)) {
    return false
  }
  return page.parentId == null && !generatedOrigin(page)
}))

async function createLlmGenDirectory() {
  if (!store.spaceId.value || creatingDir.value || llmGenDirectory.value) {
    return
  }
  creatingDir.value = true
  try {
    await saveWikiNode(store.spaceId.value, {
      title: 'llm-gen',
      slug: 'llm-gen',
      nodeType: 'DIRECTORY',
      sort: 0,
      parentId: undefined,
    })
    toast.success('已创建一级目录 llm-gen')
    await store.reloadTree()
  }
  finally {
    creatingDir.value = false
  }
}

async function moveInto(page: WikiNode) {
  const dir = llmGenDirectory.value
  if (!dir || movingNodeId.value) {
    return
  }
  movingNodeId.value = page.id
  try {
    await moveWikiNode(page.id, dir.id)
    toast.success(`「${page.title}」已移入 ${llmGenPath.value}`)
    await store.reloadTree()
  }
  finally {
    movingNodeId.value = ''
  }
}

function moveAllInto() {
  const dir = llmGenDirectory.value
  const targets = generatedPagesOutsideLlmGen.value
  if (!dir || !targets.length || movingAll.value) {
    return
  }
  modal.confirm({
    title: '移入 llm-gen 目录',
    content: `将把 ${targets.length} 个带 LLM 来源标记的页面移入一级目录「${llmGenPath.value}」下，公开路径会变化。系统页及未标记页面不会批量移动。`,
    onConfirm: async () => {
      movingAll.value = true
      let moved = 0
      try {
        for (const page of targets) {
          await moveWikiNode(page.id, dir.id)
          moved++
        }
        toast.success(`已移入 ${moved} 个页面`)
        await store.reloadTree()
      }
      finally {
        movingAll.value = false
      }
    },
  })
}
</script>

<template>
  <div class="generated">
    <YdScrollArea class="generated__scroll">
      <div class="generated__inner">
        <section class="generated__dir" aria-label="一级目录 llm-gen">
          <div class="generated__dir-main">
            <YdIcon :name="llmGenDirectory ? 'morph:folder-check' : 'morph:folder-plus'" class="generated__dir-icon" />
            <div class="generated__dir-text">
              <span class="generated__dir-title">
                一级目录 llm-gen
                <span v-if="llmGenDirectory" class="generated__dir-state">已创建 · {{ llmGenPath }}</span>
                <span v-else class="generated__dir-state generated__dir-state--missing">尚未创建</span>
              </span>
              <span class="generated__dir-desc">
                {{ llmGenDirectory
                  ? `目录是真实节点，出现在作者目录树里；把页面移入后才会归到 ${llmGenPath} 下。`
                  : '当前还没有这个一级目录：下面的页面只按类型分组展示，并没有归入 llm-gen。' }}
              </span>
            </div>
          </div>
          <div class="generated__dir-actions">
            <YdButton v-if="!llmGenDirectory" :loading="creatingDir" @click="createLlmGenDirectory">
              <YdIcon name="morph:folder-plus" /> 创建一级目录 llm-gen
            </YdButton>
            <YdButton
              v-else
              variant="outline"
              :loading="movingAll"
              :disabled="!generatedPagesOutsideLlmGen.length"
              @click="moveAllInto"
            >
              <YdIcon name="morph:folder-input" /> 移入旧版生成页面（{{ generatedPagesOutsideLlmGen.length }}）
            </YdButton>
          </div>
        </section>

        <YdAlert
          v-if="legacyRootPages.length"
          icon="morph:triangle-alert"
          title="发现旧版根层级页面"
          :description="`有 ${legacyRootPages.length} 个根层级业务页面没有生成来源标记；系统不会猜测其来源或自动移动，以免改变人工页面的公开 URL。请逐条核对后再决定是否移动。`"
        />
        <YdAlert
          icon="morph:info"
          title="按页面类型分组（阅读视图）"
          description="分组只是阅读视图，不代表页面已经在 llm-gen 目录下；「AI 生成来源」标记还需要后端补充生成 run / model / prompt revision 字段。"
        />
        <div class="generated__toolbar">
          <YdInput v-model="keyword" clearable placeholder="搜索标题或路径" aria-label="搜索生成内容" class="generated__search">
            <template #start>
              <YdIcon name="morph:search" />
            </template>
          </YdInput>
          <span class="generated__count">共 {{ filteredPages.length }} 个页面 · {{ groups.length }} 个类型</span>
        </div>

        <div v-if="!pages.length" class="generated__empty">
          <YdEmpty
            icon="morph:sparkles"
            title="还没有页面"
            description="到治理区「资料库」导入资料并摄入后，生成的页面会按类型出现在这里。"
          >
            <template #action>
              <YdButton variant="outline" @click="store.openManage('sources')">
                <YdIcon name="morph:database" /> 打开资料库
              </YdButton>
            </template>
          </YdEmpty>
        </div>
        <div v-else-if="!filteredPages.length" class="generated__empty">
          <YdEmpty illustration="no-result" title="没有匹配的页面" description="换个关键词，或清空筛选后重新查看。">
            <template #action>
              <YdButton variant="outline" @click="keyword = ''">
                清空筛选
              </YdButton>
            </template>
          </YdEmpty>
        </div>

        <template v-else>
          <section v-for="group in groups" :key="group.key || 'untyped'" class="generated__group" :aria-label="group.label">
            <div class="generated__group-head">
              <span class="generated__group-name" :style="{ color: group.color }">{{ group.label }}</span>
              <span class="generated__group-count">{{ group.items.length }}</span>
            </div>
            <YdList divided dense>
              <YdListRow v-for="page in group.items" :key="page.id" wrap>
                <YdIcon name="morph:file-text" class="generated__icon" />
                <div class="generated__main">
                  <span class="generated__title" :title="page.title">{{ page.title }}</span>
                  <span class="generated__path" :title="page.path || page.slug">{{ page.path || page.slug }}</span>
                </div>
                <span class="generated__state">{{ insideLlmGen(page) ? '已在 llm-gen' : page.publishedVersionId ? '已发布' : '未发布' }}</span>
                <span v-if="sourceLabels(page).length" class="generated__origin" :title="sourceLabels(page).join('、')">
                  <YdIcon name="morph:database" /> 资料 {{ sourceLabels(page).length }}
                </span>
                <div class="generated__ops">
                  <YdButton
                    v-if="canViewRun(page)"
                    size="sm"
                    variant="ghost"
                    @click="viewRun(page)"
                  >
                    <YdIcon name="morph:bot" /> 生成运行
                  </YdButton>
                  <YdButton
                    v-if="llmGenDirectory && generatedOrigin(page) && !insideLlmGen(page)"
                    size="sm"
                    variant="ghost"
                    :loading="movingNodeId === page.id"
                    @click="moveInto(page)"
                  >
                    <YdIcon name="morph:folder-input" /> 移入 llm-gen
                  </YdButton>
                  <YdButton size="sm" variant="ghost" @click="openPage(page)">
                    <YdIcon name="morph:pencil" /> 打开编辑
                  </YdButton>
                </div>
              </YdListRow>
            </YdList>
          </section>
        </template>
      </div>
    </YdScrollArea>
    <YdDrawer v-model="runDrawerOpen" title="产出该页面的 AI 运行" :footer="false" content-class="w-[760px] max-w-full">
      <AiRunProgress v-if="provenanceRunId" :run-id="provenanceRunId" />
    </YdDrawer>
  </div>
</template>

<style scoped>
.generated {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.generated__scroll {
  height: 100%;
}

.generated__inner {
  display: grid;
  gap: 12px;
  max-width: 1080px;
  padding: 16px 16px 40px;
  margin: 0 auto;
}

.generated__dir {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.generated__dir-main {
  display: flex;
  flex: 1;
  gap: 10px;
  align-items: flex-start;
  min-width: 240px;
}

.generated__dir-icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 18px;
  color: var(--color-text-2);
}

.generated__dir-text {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.generated__dir-title {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.generated__dir-state {
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  border-radius: 999px;
}

.generated__dir-state--missing {
  color: rgb(var(--warning-6));
}

.generated__dir-desc {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.generated__dir-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.generated__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.generated__search {
  flex: 1 1 240px;
  max-width: 320px;
}

.generated__count {
  font-size: 12px;
  color: var(--color-text-3);
}

.generated__empty {
  display: grid;
  place-content: center;
  padding: 32px 0;
}

.generated__group {
  display: grid;
  gap: 6px;
  padding: 12px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.generated__group-head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.generated__group-name {
  font-size: 13px;
  font-weight: 600;
}

.generated__group-count {
  padding: 0 7px;
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  border-radius: 999px;
}

.generated__icon {
  flex-shrink: 0;
  font-size: 16px;
  color: var(--color-text-3);
}

.generated__main {
  display: grid;
  flex: 1;
  gap: 2px;
  min-width: 0;
}

.generated__title {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--color-text-1);
  white-space: nowrap;
}

.generated__path {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.generated__state {
  flex-shrink: 0;
  padding: 1px 8px;
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  border-radius: 999px;
}

.generated__origin {
  display: inline-flex;
  flex-shrink: 0;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.generated__ops {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
}

@media (width <= 768px) {
  .generated__inner {
    padding: 12px 12px 32px;
  }

  .generated__search {
    max-width: none;
  }
}
</style>

<script setup lang="ts">
import type { AgentApplication, AgentApplicationStatus, AiToolModule } from '@/api/modules/platform-agent'
import apiAgent from '@/api/modules/platform-agent'

const router = useRouter()
const toast = useYdToast()
const modal = useYdModal()
const loading = ref(false)
const actionLoading = ref('')
const rows = ref<AgentApplication[]>([])
const toolModules = ref<AiToolModule[]>([])
const pagination = reactive({ page: 1, size: 12, total: 0 })
const search = reactive<{ keyword: string, status: AgentApplicationStatus | '' }>({ keyword: '', status: '' })
const statuses = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已停用', value: 'DISABLED' },
]
const hasFilter = computed(() => Boolean(search.keyword.trim() || search.status))

onMounted(() => {
  void load()
  void loadToolModules()
})

async function load() {
  loading.value = true
  try {
    const [res, available] = await Promise.all([
      apiAgent.page({ ...pagination, keyword: search.keyword || undefined, status: search.status || undefined }),
      apiAgent.available(),
    ])
    const persisted = res.data.records.filter(row => row.code !== 'builtin-group-chatbot' || Boolean(row.sourcePluginCode))
    const persistedCodes = new Set(persisted.map(row => row.code))
    const runtime = pagination.page === 1
      ? available.data.filter(row => row.id.startsWith('-'))
          .filter(row => !persistedCodes.has(row.code))
          .filter(row => !search.status || row.status === search.status)
          .filter(row => !search.keyword || `${row.name} ${row.code} ${row.description || ''}`.toLowerCase().includes(search.keyword.toLowerCase()))
      : []
    rows.value = [...runtime, ...persisted]
    pagination.total = Math.max(0, Number(res.data.total || 0) - (res.data.records.length - persisted.length) + runtime.length)
  }
  finally {
    loading.value = false
  }
}

/** 工具模块目录只用于卡片摘要，加载失败不影响列表本身。 */
async function loadToolModules() {
  try {
    toolModules.value = (await apiAgent.toolModules()).data.modules || []
  }
  catch {
    toolModules.value = []
  }
}

/**
 * 工具编码 → 模块编码索引。MCP 模块在管理端目录里下发 mcp:<server>.<tool> 展示编码，
 * 运行期可解析的工具名是 mcp.<server>.<tool>（后端 AiToolModules.mcpToolName），两者都要匹配。
 */
const toolModuleIndex = computed(() => {
  const index = new Map<string, string>()
  for (const module of toolModules.value) {
    for (const tool of module.tools || []) {
      index.set(tool.code, module.code)
      if (module.code.startsWith('mcp:')) {
        index.set(tool.code.replace(/^mcp:/, 'mcp.'), module.code)
      }
    }
  }
  return index
})

const rowSummaries = computed(() => {
  const summaries = new Map<string, { toolCount: number, moduleCount: number, unknownCount: number }>()
  for (const row of rows.value) {
    const codes = row.toolCodes || []
    const modules = new Set<string>()
    let unknownCount = 0
    for (const code of codes) {
      const moduleCode = toolModuleIndex.value.get(code)
      if (moduleCode) {
        modules.add(moduleCode)
      }
      else {
        unknownCount += 1
      }
    }
    summaries.set(row.id, { toolCount: codes.length, moduleCount: modules.size, unknownCount })
  }
  return summaries
})

function summaryOf(row: AgentApplication) {
  return rowSummaries.value.get(row.id) || { toolCount: row.toolCodes?.length || 0, moduleCount: 0, unknownCount: 0 }
}

function applyFilters() {
  pagination.page = 1
  void load()
}

function resetFilters() {
  search.keyword = ''
  search.status = ''
  applyFilters()
}

function create() {
  router.push('/platform/agent/editor')
}
async function edit(row: AgentApplication) {
  if (!row.id.startsWith('-')) {
    await router.push({ path: '/platform/agent/editor', query: { id: row.id } })
    return
  }
  actionLoading.value = `import-${row.id}`
  try {
    const imported = (await apiAgent.importRuntime(row.code)).data
    await router.push({ path: '/platform/agent/editor', query: { id: imported.id } })
  }
  finally {
    actionLoading.value = ''
  }
}
async function publish(row: AgentApplication) {
  actionLoading.value = `publish-${row.id}`
  try {
    await apiAgent.publish(row.id)
    toast.success('Agent 应用已发布')
    await load()
  }
  finally {
    actionLoading.value = ''
  }
}
function confirmDelete(row: AgentApplication) {
  modal.confirm({
    title: '删除 Agent 应用',
    content: `确定删除“${row.name}”吗？`,
    onConfirm: async () => {
      await apiAgent.delete(row.id)
      toast.success('已删除')
      await load()
    },
  })
}
function openRunner(row: AgentApplication) {
  if (row.status !== 'PUBLISHED') {
    toast.error('Agent 应用发布后才能正式运行')
    return
  }
  void router.push({
    path: '/platform/chat',
    query: { scopeType: 'AGENT', agentCode: row.code },
  })
}
function statusText(status: AgentApplicationStatus) {
  return ({ DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' } as const)[status]
}
function statusVariant(status: AgentApplicationStatus) {
  return status === 'PUBLISHED' ? 'default' : status === 'DISABLED' ? 'destructive' : 'secondary'
}
</script>

<template>
  <div>
    <YdPageHeader title="Agent 应用" class="mb-0">
      <div class="flex flex-wrap gap-2">
        <YdButton variant="outline" @click="router.push('/platform/agent/tools')">
          <YdIcon name="morph:wrench" /> 系统工具
        </YdButton>
        <YdButton variant="outline" @click="router.push('/platform/mcp')">
          <YdIcon name="morph:plug" /> MCP 服务
        </YdButton>
        <YdButton v-auth="'platform:agent:edit'" @click="create">
          <YdIcon name="morph:plus" /> 新建应用
        </YdButton>
      </div>
    </YdPageHeader>
    <YdPageMain>
      <YdListToolbar
        v-model:keyword="search.keyword"
        title="Agent 应用"
        placeholder="应用名称 / 编码 / 描述"
        :search-width="260"
        @search="applyFilters"
        @clear="applyFilters"
      >
        <template #views>
          <span class="list-summary">共 {{ pagination.total }} 个应用</span>
        </template>
        <template #filters>
          <YdFilterSelect v-model="search.status" :options="statuses" width="168" placeholder="全部状态" />
        </template>
        <template #actions>
          <YdButton variant="outline" @click="resetFilters">
            重置
          </YdButton>
          <YdButton :loading="loading" @click="applyFilters">
            筛选
          </YdButton>
        </template>
      </YdListToolbar>

      <YdLoadingOverlay :loading="loading">
        <div v-if="rows.length" class="agent-grid">
          <article v-for="row in rows" :key="row.id" class="agent-card">
            <div class="agent-card__head">
              <span class="agent-icon"><YdIcon :name="row.icon || 'morph:bot'" /></span>
              <div class="agent-card__tags">
                <YdTag v-if="row.sourcePluginCode" variant="outline">
                  插件
                </YdTag>
                <YdTag :variant="statusVariant(row.status)">
                  {{ statusText(row.status) }}
                </YdTag>
              </div>
            </div>
            <h3>{{ row.name }}</h3>
            <p>{{ row.description || '尚未填写应用描述' }}</p>
            <div class="agent-meta">
              <code>{{ row.code }}</code>
              <span v-if="row.updateTime" class="agent-meta__time">更新于 <YdTimeAgo :date="row.updateTime" /></span>
            </div>
            <div class="agent-meta">
              <span>{{ summaryOf(row).toolCount }} 个工具</span>
              <span v-if="summaryOf(row).moduleCount">· {{ summaryOf(row).moduleCount }} 个模块</span>
              <span v-if="toolModules.length && summaryOf(row).unknownCount" class="agent-meta__warn">
                · {{ summaryOf(row).unknownCount }} 个已下线工具
              </span>
            </div>
            <div class="agent-actions">
              <YdButton v-auth="'platform:chat:use'" size="sm" variant="outline" :disabled="row.status !== 'PUBLISHED'" @click="openRunner(row)">
                运行
              </YdButton><YdButton v-auth="'platform:agent:edit'" size="sm" variant="ghost" title="导入并编辑" :loading="actionLoading === `import-${row.id}`" @click="edit(row)">
                {{ row.id.startsWith('-') ? '导入并编辑' : '编排' }}
              </YdButton><YdButton v-if="!row.id.startsWith('-')" v-auth="'platform:agent:publish'" size="sm" variant="ghost" :loading="actionLoading === `publish-${row.id}`" :disabled="row.status === 'PUBLISHED'" @click="publish(row)">
                发布
              </YdButton><YdButton v-if="!row.id.startsWith('-')" v-auth="'platform:agent:delete'" size="sm" variant="destructive" @click="confirmDelete(row)">
                删除
              </YdButton>
            </div>
          </article>
        </div>
        <div v-else-if="loading" class="agent-grid" aria-hidden="true">
          <div v-for="index in 6" :key="index" class="agent-card agent-card--skeleton">
            <YdSkeleton variant="rect" :width="34" :height="34" :radius="6" />
            <YdSkeleton width="62%" :height="18" :radius="4" />
            <YdSkeleton :height="12" :radius="4" />
            <YdSkeleton width="46%" :height="12" :radius="4" />
          </div>
        </div>
        <YdEmpty
          v-else
          :title="hasFilter ? '没有匹配的 Agent 应用' : '还没有 Agent 应用'"
          :description="hasFilter ? '换一个关键词或状态，或重置筛选查看全部应用。' : '新建应用后，在编排画布中配置模型、工具与流程节点。'"
        >
          <YdButton v-if="hasFilter" variant="outline" @click="resetFilters">
            重置筛选
          </YdButton>
          <YdButton v-else v-auth="'platform:agent:edit'" @click="create">
            <YdIcon name="morph:plus" /> 新建应用
          </YdButton>
        </YdEmpty>
      </YdLoadingOverlay>
      <YdPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" class="mt-4" @page-change="load" @size-change="load" />
    </YdPageMain>
  </div>
</template>

<style scoped>
.list-summary { color: var(--color-text-3); font-size: 12px; font-variant-numeric: tabular-nums; }
.agent-grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr)); }
.agent-card { display: grid; gap: 12px; min-height: 210px; align-content: start; padding: 18px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-1); }
.agent-card--skeleton { gap: 14px; }
.agent-card__head, .agent-actions, .agent-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.agent-card__tags { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 6px; }
.agent-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 6px; background: var(--color-fill-2); color: var(--color-text-1); }
.agent-card h3 { margin: 0; font-size: 17px; }
.agent-card p { min-height: 42px; margin: 0; color: var(--color-text-3); font-size: 13px; line-height: 1.6; }
.agent-meta { justify-content: flex-start; flex-wrap: wrap; gap: 6px; color: var(--color-text-3); font-size: 12px; }
.agent-meta__time { display: inline-flex; align-items: center; gap: 4px; }
.agent-meta__warn { color: rgb(var(--warning-6)); }
.agent-actions { justify-content: flex-start; flex-wrap: wrap; }
</style>

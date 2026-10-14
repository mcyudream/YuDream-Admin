<script setup lang="ts">
import type { AgentApplication, AgentApplicationStatus } from '@/api/modules/platform-agent'
import apiAgent from '@/api/modules/platform-agent'

const router = useRouter()
const toast = useYdToast()
const modal = useYdModal()
const loading = ref(false)
const actionLoading = ref('')
const rows = ref<AgentApplication[]>([])
const pagination = reactive({ page: 1, size: 12, total: 0 })
const search = reactive<{ keyword: string, status: AgentApplicationStatus | '' }>({ keyword: '', status: '' })
const statuses = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已停用', value: 'DISABLED' },
]

onMounted(load)

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
      <div class="flex gap-2">
        <YdButton variant="outline" @click="router.push('/platform/agent/tools')">
          <YdIcon name="morph:wrench" /> 工具
        </YdButton>
        <YdButton v-auth="'platform:agent:edit'" @click="create">
          <YdIcon name="morph:plus" /> 新建应用
        </YdButton>
      </div>
    </YdPageHeader>
    <YdPageMain>
      <YdSearchBar class="mb-4">
        <div class="gap-3 grid md:grid-cols-[minmax(260px,1fr)_180px_auto]">
          <YdInput v-model="search.keyword" clearable placeholder="应用名称 / 编码 / 描述" @keydown.enter="load" />
          <YdSelect v-model="search.status" :options="statuses" placeholder="状态" />
          <div class="flex gap-2">
            <YdButton variant="outline" @click="Object.assign(search, { keyword: '', status: '' }); load()">
              重置
            </YdButton><YdButton :loading="loading" @click="load">
              筛选
            </YdButton>
          </div>
        </div>
      </YdSearchBar>
      <YdLoadingOverlay :loading="loading"><div  class="agent-grid">
        <article v-for="row in rows" :key="row.id" class="agent-card">
          <div class="agent-card__head">
            <span class="agent-icon"><YdIcon :name="row.icon || 'morph:bot'" /></span><YdTag :variant="statusVariant(row.status)">
              {{ statusText(row.status) }}
            </YdTag>
          </div>
          <h3>{{ row.name }}</h3><p>{{ row.description || '尚未填写应用描述' }}</p>
          <div class="agent-meta">
            <code>{{ row.code }}</code><span>{{ row.toolCodes?.length || 0 }} 个工具</span>
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
        <YdEmpty v-if="!loading && !rows.length" class="col-span-full" description="暂无 Agent 应用" />
      </div></YdLoadingOverlay>
      <YdPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" class="mt-4" @page-change="load" @size-change="load" />
    </YdPageMain>
  </div>
</template>

<style scoped>
.agent-grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
.agent-card { display: grid; gap: 12px; min-height: 210px; padding: 18px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-1); }
.agent-card__head, .agent-actions, .agent-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.agent-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 6px; background: var(--color-fill-2); color: var(--color-text-1); }.agent-card h3 { margin: 0; font-size: 17px; }.agent-card p { min-height: 42px; margin: 0; color: var(--color-text-3); font-size: 13px; line-height: 1.6; }.agent-meta { color: var(--color-text-3); font-size: 12px; }.agent-actions { justify-content: flex-start; flex-wrap: wrap; }
</style>

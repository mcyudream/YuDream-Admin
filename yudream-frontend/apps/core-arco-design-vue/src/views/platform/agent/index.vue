<script setup lang="ts">
import type { AgentApplication, AgentApplicationStatus, AgentRunResult } from '@/api/modules/platform-agent'
import apiAgent from '@/api/modules/platform-agent'

const router = useRouter()
const toast = useFaToast()
const modal = useFaModal()
const loading = ref(false)
const actionLoading = ref('')
const rows = ref<AgentApplication[]>([])
const runner = ref<AgentApplication | null>(null)
const runnerVisible = ref(false)
const runInput = ref('')
const runResult = ref<AgentRunResult | null>(null)
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
    const res = await apiAgent.page({ ...pagination, keyword: search.keyword || undefined, status: search.status || undefined })
    rows.value = res.data.records
    pagination.total = Number(res.data.total || 0)
  }
  finally { loading.value = false }
}

function create() { router.push('/platform/agent/editor') }
function edit(row: AgentApplication) { router.push({ path: '/platform/agent/editor', query: { id: row.id } }) }
async function publish(row: AgentApplication) {
  actionLoading.value = `publish-${row.id}`
  try { await apiAgent.publish(row.id); toast.success('Agent 应用已发布'); await load() }
  finally { actionLoading.value = '' }
}
function confirmDelete(row: AgentApplication) {
  modal.confirm({ title: '删除 Agent 应用', content: `确定删除“${row.name}”吗？`, onConfirm: async () => { await apiAgent.delete(row.id); toast.success('已删除'); await load() } })
}
function openRunner(row: AgentApplication) { runner.value = row; runnerVisible.value = true; runInput.value = ''; runResult.value = null }
async function run() {
  if (!runner.value || !runInput.value.trim()) return
  actionLoading.value = 'run'
  try { runResult.value = (await apiAgent.run(runner.value.id, { input: runInput.value })).data }
  finally { actionLoading.value = '' }
}
function statusText(status: AgentApplicationStatus) { return ({ DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' } as const)[status] }
function statusVariant(status: AgentApplicationStatus) { return status === 'PUBLISHED' ? 'default' : status === 'DISABLED' ? 'destructive' : 'secondary' }
</script>

<template>
  <div>
    <FaPageHeader title="Agent 应用" class="mb-0">
      <div class="flex gap-2">
        <FaButton variant="outline" @click="router.push('/platform/agent/tools')"><FaIcon name="i-ri:tools-line" /> 工具</FaButton>
        <FaButton v-auth="'platform:agent:edit'" @click="create"><FaIcon name="i-ri:add-line" /> 新建应用</FaButton>
      </div>
    </FaPageHeader>
    <FaPageMain>
      <FaSearchBar class="mb-4">
        <div class="grid gap-3 md:grid-cols-[minmax(260px,1fr)_180px_auto]">
          <FaInput v-model="search.keyword" clearable placeholder="应用名称 / 编码 / 描述" @keydown.enter="load" />
          <FaSelect v-model="search.status" :options="statuses" placeholder="状态" />
          <div class="flex gap-2"><FaButton variant="outline" @click="Object.assign(search, { keyword: '', status: '' }); load()">重置</FaButton><FaButton :loading="loading" @click="load">筛选</FaButton></div>
        </div>
      </FaSearchBar>
      <div v-loading="loading" class="agent-grid">
        <article v-for="row in rows" :key="row.id" class="agent-card">
          <div class="agent-card__head"><span class="agent-icon"><FaIcon :name="row.icon || 'i-ri:robot-2-line'" /></span><FaTag :variant="statusVariant(row.status)">{{ statusText(row.status) }}</FaTag></div>
          <h3>{{ row.name }}</h3><p>{{ row.description || '尚未填写应用描述' }}</p>
          <div class="agent-meta"><code>{{ row.code }}</code><span>{{ row.toolCodes?.length || 0 }} 个工具</span></div>
          <div class="agent-actions"><FaButton size="sm" variant="outline" @click="openRunner(row)">运行</FaButton><FaButton v-auth="'platform:agent:edit'" size="sm" variant="ghost" @click="edit(row)">编排</FaButton><FaButton v-auth="'platform:agent:publish'" size="sm" variant="ghost" :loading="actionLoading === `publish-${row.id}`" :disabled="row.status === 'PUBLISHED'" @click="publish(row)">发布</FaButton><FaButton v-auth="'platform:agent:delete'" size="sm" variant="destructive" @click="confirmDelete(row)">删除</FaButton></div>
        </article>
        <FaEmpty v-if="!loading && !rows.length" class="col-span-full" description="暂无 Agent 应用" />
      </div>
      <FaPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" class="mt-4" @page-change="load" @size-change="load" />
    </FaPageMain>
    <FaModal v-model="runnerVisible" :title="runner ? `运行 ${runner.name}` : '运行 Agent'" class="sm:max-w-3xl">
      <div class="grid gap-4"><FaTextarea v-model="runInput" :autosize="{ minRows: 4, maxRows: 8 }" placeholder="输入任务或问题" /><FaButton :loading="actionLoading === 'run'" :disabled="!runInput.trim()" @click="run">运行应用</FaButton><section v-if="runResult" class="run-result"><strong>输出</strong><p>{{ runResult.content }}</p><div v-for="tool in runResult.toolResults" :key="`${tool.toolName}-${tool.action}`" class="tool-result">{{ tool.toolName }}: {{ tool.message }}</div></section></div>
    </FaModal>
  </div>
</template>

<style scoped>
.agent-grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
.agent-card { display: grid; gap: 12px; min-height: 210px; padding: 18px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-1); }
.agent-card__head, .agent-actions, .agent-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.agent-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 6px; background: var(--color-fill-2); color: rgb(var(--primary-6)); }.agent-card h3 { margin: 0; font-size: 17px; }.agent-card p { min-height: 42px; margin: 0; color: var(--color-text-3); font-size: 13px; line-height: 1.6; }.agent-meta { color: var(--color-text-3); font-size: 12px; }.agent-actions { justify-content: flex-start; flex-wrap: wrap; }.run-result { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--color-border-2); border-radius: 6px; white-space: pre-wrap; }.run-result p { margin: 0; line-height: 1.7; }.tool-result { padding: 8px; background: var(--color-fill-1); font-size: 12px; }
</style>

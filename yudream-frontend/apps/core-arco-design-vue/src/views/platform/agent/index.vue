<script setup lang="ts">
import type { AgentApplication, AgentApplicationStatus, AgentRunAttachment, AgentRunResult } from '@/api/modules/platform-agent'
import apiAgent from '@/api/modules/platform-agent'
import { agentRunCapabilities, buildAgentRunInput } from './config/agent-run-input'

interface RunnerAttachment extends AgentRunAttachment {
  id: string
  type: string
  text?: string
}

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
const runAttachments = ref<RunnerAttachment[]>([])
const runnerError = ref('')
const runnerFileInput = useTemplateRef<HTMLInputElement>('runnerFileInput')
const pagination = reactive({ page: 1, size: 12, total: 0 })
const search = reactive<{ keyword: string, status: AgentApplicationStatus | '' }>({ keyword: '', status: '' })
const runnerCapabilities = computed(() => agentRunCapabilities(runner.value?.workflowJson || ''))
const runnerAccept = computed(() => [
  runnerCapabilities.value.allowImage ? 'image/*' : '',
  runnerCapabilities.value.allowFiles ? '.txt,.md,.markdown,.json,.csv,.xml,.html,.yaml,.yml,.pdf,.doc,.docx,.rtf,.odt' : '',
].filter(Boolean).join(','))

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
  runner.value = row
  runnerVisible.value = true
  runInput.value = ''
  runResult.value = null
  runAttachments.value = []
  runnerError.value = ''
}
async function run() {
  if (!runner.value || (!runInput.value.trim() && !runAttachments.value.length)) {
    return
  }
  actionLoading.value = 'run'
  const image = runAttachments.value.find(item => item.type.startsWith('image/'))
  try {
    runResult.value = (await apiAgent.run(runner.value.id, {
      input: buildAgentRunInput(runInput.value, runAttachments.value),
      imageDataUrl: image?.dataUrl,
      attachments: runAttachments.value
        .filter(item => !item.type.startsWith('image/'))
        .map(item => ({ name: item.name, contentType: item.type, size: item.size, dataUrl: item.dataUrl })),
    })).data
  }
  finally {
    actionLoading.value = ''
  }
}
function pickRunnerFiles() {
  runnerFileInput.value?.click()
}
async function onRunnerFiles(event: Event) {
  const target = event.target as HTMLInputElement
  runnerError.value = ''
  try {
    for (const file of Array.from(target.files || [])) {
      const image = file.type.startsWith('image/')
      if (image && !runnerCapabilities.value.allowImage) {
        throw new Error('当前工作流不支持图片输入')
      }
      if (!image && !runnerCapabilities.value.allowFiles) {
        throw new Error('当前工作流不支持文档输入')
      }
      if (image && file.size > 5 * 1024 * 1024) {
        throw new Error('单张图片不能超过 5MB')
      }
      if (!image && file.size > 10 * 1024 * 1024) {
        throw new Error('单个文档不能超过 10MB')
      }
      if (image && runAttachments.value.some(item => item.type.startsWith('image/'))) {
        throw new Error('一次运行只支持一张图片')
      }
      runAttachments.value.push({
        id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        name: file.name,
        type: file.type || 'application/octet-stream',
        contentType: file.type || 'application/octet-stream',
        size: file.size,
        dataUrl: await readDataUrl(file),
        text: !image && file.size <= 256 * 1024 && isTextFile(file) ? await file.text() : undefined,
      })
    }
  }
  catch (error) {
    runnerError.value = error instanceof Error ? error.message : '附件读取失败'
  }
  finally {
    target.value = ''
  }
}
function readDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('附件读取失败'))
    reader.readAsDataURL(file)
  })
}
function isTextFile(file: File) {
  return file.type.startsWith('text/') || /\.(?:txt|md|markdown|json|csv|xml|html|yaml|yml)$/i.test(file.name)
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
    <FaPageHeader title="Agent 应用" class="mb-0">
      <div class="flex gap-2">
        <FaButton variant="outline" @click="router.push('/platform/agent/tools')">
          <FaIcon name="i-ri:tools-line" /> 工具
        </FaButton>
        <FaButton v-auth="'platform:agent:edit'" @click="create">
          <FaIcon name="i-ri:add-line" /> 新建应用
        </FaButton>
      </div>
    </FaPageHeader>
    <FaPageMain>
      <FaSearchBar class="mb-4">
        <div class="gap-3 grid md:grid-cols-[minmax(260px,1fr)_180px_auto]">
          <FaInput v-model="search.keyword" clearable placeholder="应用名称 / 编码 / 描述" @keydown.enter="load" />
          <FaSelect v-model="search.status" :options="statuses" placeholder="状态" />
          <div class="flex gap-2">
            <FaButton variant="outline" @click="Object.assign(search, { keyword: '', status: '' }); load()">
              重置
            </FaButton><FaButton :loading="loading" @click="load">
              筛选
            </FaButton>
          </div>
        </div>
      </FaSearchBar>
      <div v-loading="loading" class="agent-grid">
        <article v-for="row in rows" :key="row.id" class="agent-card">
          <div class="agent-card__head">
            <span class="agent-icon"><FaIcon :name="row.icon || 'i-ri:robot-2-line'" /></span><FaTag :variant="statusVariant(row.status)">
              {{ statusText(row.status) }}
            </FaTag>
          </div>
          <h3>{{ row.name }}</h3><p>{{ row.description || '尚未填写应用描述' }}</p>
          <div class="agent-meta">
            <code>{{ row.code }}</code><span>{{ row.toolCodes?.length || 0 }} 个工具</span>
          </div>
          <div class="agent-actions">
            <FaButton v-if="!row.id.startsWith('-')" size="sm" variant="outline" :disabled="row.status !== 'PUBLISHED'" @click="openRunner(row)">
              运行
            </FaButton><FaButton v-auth="'platform:agent:edit'" size="sm" variant="ghost" title="导入并编辑" :loading="actionLoading === `import-${row.id}`" @click="edit(row)">
              {{ row.id.startsWith('-') ? '导入并编辑' : '编排' }}
            </FaButton><FaButton v-if="!row.id.startsWith('-')" v-auth="'platform:agent:publish'" size="sm" variant="ghost" :loading="actionLoading === `publish-${row.id}`" :disabled="row.status === 'PUBLISHED'" @click="publish(row)">
              发布
            </FaButton><FaButton v-if="!row.id.startsWith('-')" v-auth="'platform:agent:delete'" size="sm" variant="destructive" @click="confirmDelete(row)">
              删除
            </FaButton>
          </div>
        </article>
        <FaEmpty v-if="!loading && !rows.length" class="col-span-full" description="暂无 Agent 应用" />
      </div>
      <FaPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" class="mt-4" @page-change="load" @size-change="load" />
    </FaPageMain>
    <FaModal v-model="runnerVisible" :title="runner ? `运行 ${runner.name}` : '运行 Agent'" class="sm:max-w-3xl">
      <div class="gap-4 grid">
        <FaTextarea v-model="runInput" :autosize="{ minRows: 4, maxRows: 8 }" placeholder="输入任务或问题" />
        <div v-if="runAttachments.length" class="runner-attachments">
          <span v-for="item in runAttachments" :key="item.id"><img v-if="item.type.startsWith('image/')" :src="item.dataUrl" :alt="item.name"><FaIcon v-else name="i-ri:file-text-line" /><b>{{ item.name }}</b><button type="button" title="移除附件" @click="runAttachments = runAttachments.filter(current => current.id !== item.id)"><FaIcon name="i-ri:close-line" /></button></span>
        </div>
        <p v-if="runnerError" class="runner-error">
          {{ runnerError }}
        </p>
        <div class="runner-actions">
          <FaButton v-if="runnerAccept" variant="outline" @click="pickRunnerFiles">
            <FaIcon name="i-ri:attachment-2" /> 添加附件
          </FaButton>
          <input ref="runnerFileInput" class="hidden" type="file" multiple :accept="runnerAccept" @change="onRunnerFiles">
          <FaButton :loading="actionLoading === 'run'" :disabled="!runInput.trim() && !runAttachments.length" @click="run">
            <FaIcon name="i-ri:play-line" /> 运行应用
          </FaButton>
        </div>
        <section v-if="runResult" class="run-result">
          <strong>输出</strong><p>{{ runResult.content }}</p><div v-for="tool in runResult.toolResults" :key="`${tool.toolName}-${tool.action}`" class="tool-result">
            {{ tool.toolName }}: {{ tool.message }}
          </div>
        </section>
      </div>
    </FaModal>
  </div>
</template>

<style scoped>
.agent-grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
.agent-card { display: grid; gap: 12px; min-height: 210px; padding: 18px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-1); }
.agent-card__head, .agent-actions, .agent-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.agent-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 6px; background: var(--color-fill-2); color: rgb(var(--primary-6)); }.agent-card h3 { margin: 0; font-size: 17px; }.agent-card p { min-height: 42px; margin: 0; color: var(--color-text-3); font-size: 13px; line-height: 1.6; }.agent-meta { color: var(--color-text-3); font-size: 12px; }.agent-actions { justify-content: flex-start; flex-wrap: wrap; }.run-result { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--color-border-2); border-radius: 6px; white-space: pre-wrap; }.run-result p { margin: 0; line-height: 1.7; }.tool-result { padding: 8px; background: var(--color-fill-1); font-size: 12px; }
.runner-actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.runner-attachments { display: flex; gap: 8px; overflow-x: auto; }.runner-attachments span { display: flex; max-width: 220px; flex: 0 0 auto; align-items: center; gap: 6px; padding: 6px 8px; border: 1px solid var(--color-border-2); border-radius: 5px; }.runner-attachments img { width: 30px; height: 30px; border-radius: 4px; object-fit: cover; }.runner-attachments b { overflow: hidden; font-size: 12px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }.runner-attachments button { display: grid; width: 24px; height: 24px; place-items: center; border: 0; color: var(--color-text-3); background: transparent; cursor: pointer; }.runner-error { margin: 0; color: rgb(var(--danger-6)); font-size: 12px; }
</style>

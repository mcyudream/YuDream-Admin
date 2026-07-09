<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type {
  ConnectorStatus,
  ExecutionStatus,
  HttpConnector,
  HttpConnectorPayload,
  HttpInvocationLog,
  HttpMethodType,
  RuntimeExecutionLog,
  RuntimeLanguage,
  RuntimeScript,
  RuntimeScriptPayload,
} from '@/api/modules/platform-integration'
import apiIntegration from '@/api/modules/platform-integration'

const modal = useFaModal()
const toast = useFaToast()

const activeTab = ref<'connectors' | 'httpLogs' | 'scripts' | 'runtimeLogs'>('connectors')
const loading = ref(false)
const actionLoading = ref('')
const connectorRows = ref<HttpConnector[]>([])
const httpLogRows = ref<HttpInvocationLog[]>([])
const scriptRows = ref<RuntimeScript[]>([])
const runtimeLogRows = ref<RuntimeExecutionLog[]>([])
const search = reactive({ keyword: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const connectorVisible = ref(false)
const connectorEditing = ref<HttpConnector | null>(null)
const connectorJson = reactive({ headers: '{}', queryParams: '{}' })
const connectorForm = reactive<HttpConnectorPayload>({
  name: '',
  code: '',
  url: '',
  method: 'GET',
  headers: {},
  queryParams: {},
  bodyTemplate: '',
  timeoutMillis: 10000,
  retryTimes: 0,
  status: 'ACTIVE',
})

const invokeVisible = ref(false)
const invokingConnector = ref<HttpConnector | null>(null)
const invokeForm = reactive({ headers: '{}', queryParams: '{}', body: '' })
const lastHttpLog = ref<HttpInvocationLog | null>(null)

const scriptVisible = ref(false)
const scriptEditing = ref<RuntimeScript | null>(null)
const scriptEnvJson = ref('{}')
const scriptForm = reactive<RuntimeScriptPayload>({
  name: '',
  code: '',
  language: 'PYTHON',
  scriptContent: 'print("hello yudream")',
  timeoutMillis: 10000,
  env: {},
  status: 'ACTIVE',
})

const executeVisible = ref(false)
const executingScript = ref<RuntimeScript | null>(null)
const executeStdin = ref('')
const lastRuntimeLog = ref<RuntimeExecutionLog | null>(null)

const methodOptions = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map(value => ({ label: value, value }))
const statusOptions: { label: string; value: ConnectorStatus }[] = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
]
const languageOptions: { label: string; value: RuntimeLanguage }[] = [
  { label: 'Python', value: 'PYTHON' },
]
const tabs = [
  { key: 'connectors', label: 'HTTP 连接器', icon: 'i-ri:link-m' },
  { key: 'httpLogs', label: 'HTTP 日志', icon: 'i-ri:file-list-3-line' },
  { key: 'scripts', label: '运行脚本', icon: 'i-ri:terminal-box-line' },
  { key: 'runtimeLogs', label: '运行日志', icon: 'i-ri:bug-line' },
] as const

const connectorColumns = computed<TableColumn<HttpConnector>[]>(() => [
  { accessorKey: 'name', header: '名称', width: 180, fixed: 'left' },
  { accessorKey: 'code', header: '编码', width: 160 },
  { id: 'method', header: '方法', width: 90, align: 'center' },
  { accessorKey: 'url', header: '请求地址', width: 360 },
  { accessorKey: 'timeoutMillis', header: '超时', width: 100, align: 'right' },
  { accessorKey: 'retryTimes', header: '重试', width: 90, align: 'right' },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { id: 'operation', header: '操作', width: 260, align: 'center', fixed: 'right' },
])

const httpLogColumns = computed<TableColumn<HttpInvocationLog>[]>(() => [
  { accessorKey: 'connectorCode', header: '连接器', width: 160, fixed: 'left' },
  { id: 'method', header: '方法', width: 90, align: 'center' },
  { accessorKey: 'url', header: '请求地址', width: 360 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { accessorKey: 'responseStatus', header: '响应码', width: 90, align: 'center' },
  { accessorKey: 'durationMillis', header: '耗时', width: 100, align: 'right' },
  { id: 'invokedAt', header: '调用时间', width: 180 },
  { id: 'errorMessage', header: '异常', width: 260 },
])

const scriptColumns = computed<TableColumn<RuntimeScript>[]>(() => [
  { accessorKey: 'name', header: '名称', width: 180, fixed: 'left' },
  { accessorKey: 'code', header: '编码', width: 160 },
  { id: 'language', header: '语言', width: 100, align: 'center' },
  { accessorKey: 'timeoutMillis', header: '超时', width: 100, align: 'right' },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { id: 'updateTime', header: '更新时间', width: 180 },
  { id: 'operation', header: '操作', width: 240, align: 'center', fixed: 'right' },
])

const runtimeLogColumns = computed<TableColumn<RuntimeExecutionLog>[]>(() => [
  { accessorKey: 'scriptCode', header: '脚本', width: 160, fixed: 'left' },
  { id: 'language', header: '语言', width: 100, align: 'center' },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { accessorKey: 'exitCode', header: '退出码', width: 90, align: 'center' },
  { accessorKey: 'durationMillis', header: '耗时', width: 100, align: 'right' },
  { id: 'executedAt', header: '执行时间', width: 180 },
  { id: 'stdout', header: '输出', width: 320 },
  { id: 'errorMessage', header: '异常', width: 260 },
])

onMounted(load)

watch(activeTab, () => {
  pagination.page = 1
  pagination.total = 0
  load()
})

async function load() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
    }
    if (activeTab.value === 'connectors') {
      const res = await apiIntegration.pageConnectors(params)
      connectorRows.value = res.data.records
      pagination.total = res.data.total
    }
    else if (activeTab.value === 'httpLogs') {
      const res = await apiIntegration.pageHttpLogs(params)
      httpLogRows.value = res.data.records
      pagination.total = res.data.total
    }
    else if (activeTab.value === 'scripts') {
      const res = await apiIntegration.pageScripts(params)
      scriptRows.value = res.data.records
      pagination.total = res.data.total
    }
    else {
      const res = await apiIntegration.pageRuntimeLogs(params)
      runtimeLogRows.value = res.data.records
      pagination.total = res.data.total
    }
  }
  finally {
    loading.value = false
  }
}

function resetSearch() {
  search.keyword = ''
  pagination.page = 1
  load()
}

function onPageChange(page: number) {
  pagination.page = page
  load()
}

function onSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  load()
}

function openCreateConnector() {
  connectorEditing.value = null
  Object.assign(connectorForm, {
    name: '',
    code: '',
    url: '',
    method: 'GET' as HttpMethodType,
    headers: {},
    queryParams: {},
    bodyTemplate: '',
    timeoutMillis: 10000,
    retryTimes: 0,
    status: 'ACTIVE' as ConnectorStatus,
  })
  connectorJson.headers = '{}'
  connectorJson.queryParams = '{}'
  connectorVisible.value = true
}

function openEditConnector(row: HttpConnector) {
  connectorEditing.value = row
  Object.assign(connectorForm, {
    name: row.name,
    code: row.code,
    url: row.url,
    method: row.method,
    headers: row.headers || {},
    queryParams: row.queryParams || {},
    bodyTemplate: row.bodyTemplate || '',
    timeoutMillis: row.timeoutMillis || 10000,
    retryTimes: row.retryTimes || 0,
    status: row.status || 'ACTIVE',
  })
  connectorJson.headers = formatJson(row.headers)
  connectorJson.queryParams = formatJson(row.queryParams)
  connectorVisible.value = true
}

async function saveConnector() {
  const headers = parseJsonObject(connectorJson.headers, '请求头')
  const queryParams = parseJsonObject(connectorJson.queryParams, '查询参数')
  if (!headers || !queryParams) {
    return
  }
  const payload: HttpConnectorPayload = {
    ...connectorForm,
    headers,
    queryParams,
  }
  if (connectorEditing.value) {
    await apiIntegration.updateConnector(connectorEditing.value.id, payload)
  }
  else {
    await apiIntegration.createConnector(payload)
  }
  toast.success('HTTP 连接器已保存')
  connectorVisible.value = false
  await load()
}

function confirmDisableConnector(row: HttpConnector) {
  modal.confirm({
    title: '确认停用',
    content: `确认停用 HTTP 连接器「${row.name}」吗？`,
    onConfirm: async () => {
      await apiIntegration.disableConnector(row.id)
      toast.success('HTTP 连接器已停用')
      await load()
    },
  })
}

function openInvoke(row: HttpConnector) {
  invokingConnector.value = row
  invokeForm.headers = '{}'
  invokeForm.queryParams = '{}'
  invokeForm.body = row.bodyTemplate || ''
  lastHttpLog.value = null
  invokeVisible.value = true
}

async function invokeConnector() {
  if (!invokingConnector.value) {
    return
  }
  const headers = parseJsonObject(invokeForm.headers, '请求头')
  const queryParams = parseJsonObject(invokeForm.queryParams, '查询参数')
  if (!headers || !queryParams) {
    return
  }
  actionLoading.value = 'invoke'
  try {
    const res = await apiIntegration.invokeConnector(invokingConnector.value.id, {
      headers,
      queryParams,
      body: invokeForm.body,
    })
    lastHttpLog.value = res.data
    toast.success('HTTP 调用已执行')
  }
  finally {
    actionLoading.value = ''
  }
}

function openCreateScript() {
  scriptEditing.value = null
  Object.assign(scriptForm, {
    name: '',
    code: '',
    language: 'PYTHON' as RuntimeLanguage,
    scriptContent: 'print("hello yudream")',
    timeoutMillis: 10000,
    env: {},
    status: 'ACTIVE' as ConnectorStatus,
  })
  scriptEnvJson.value = '{}'
  scriptVisible.value = true
}

function openEditScript(row: RuntimeScript) {
  scriptEditing.value = row
  Object.assign(scriptForm, {
    name: row.name,
    code: row.code,
    language: row.language,
    scriptContent: row.scriptContent || '',
    timeoutMillis: row.timeoutMillis || 10000,
    env: row.env || {},
    status: row.status || 'ACTIVE',
  })
  scriptEnvJson.value = formatJson(row.env)
  scriptVisible.value = true
}

async function saveScript() {
  const env = parseJsonObject(scriptEnvJson.value, '环境变量')
  if (!env) {
    return
  }
  const payload: RuntimeScriptPayload = {
    ...scriptForm,
    env,
  }
  if (scriptEditing.value) {
    await apiIntegration.updateScript(scriptEditing.value.id, payload)
  }
  else {
    await apiIntegration.createScript(payload)
  }
  toast.success('运行脚本已保存')
  scriptVisible.value = false
  await load()
}

function confirmDisableScript(row: RuntimeScript) {
  modal.confirm({
    title: '确认停用',
    content: `确认停用运行脚本「${row.name}」吗？`,
    onConfirm: async () => {
      await apiIntegration.disableScript(row.id)
      toast.success('运行脚本已停用')
      await load()
    },
  })
}

function openExecute(row: RuntimeScript) {
  executingScript.value = row
  executeStdin.value = ''
  lastRuntimeLog.value = null
  executeVisible.value = true
}

async function executeScript() {
  if (!executingScript.value) {
    return
  }
  actionLoading.value = 'execute'
  try {
    const res = await apiIntegration.executeScript(executingScript.value.id, executeStdin.value)
    lastRuntimeLog.value = res.data
    toast.success('脚本执行完成')
  }
  finally {
    actionLoading.value = ''
  }
}

function parseJsonObject(value: string, label: string) {
  try {
    const parsed = value ? JSON.parse(value) : {}
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      toast.error(`${label}必须是 JSON 对象`)
      return null
    }
    return parsed as Record<string, string>
  }
  catch {
    toast.error(`${label}格式错误`, { description: '请输入合法 JSON 对象' })
    return null
  }
}

function formatJson(value?: Record<string, string>) {
  return JSON.stringify(value || {}, null, 2)
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function statusText(status: ConnectorStatus) {
  return status === 'ACTIVE' ? '启用' : '停用'
}

function statusVariant(status: ConnectorStatus) {
  return status === 'ACTIVE' ? 'default' : 'secondary'
}

function executionText(status: ExecutionStatus) {
  const map: Record<ExecutionStatus, string> = {
    SUCCESS: '成功',
    FAILED: '失败',
    TIMEOUT: '超时',
  }
  return map[status]
}

function executionVariant(status: ExecutionStatus) {
  return status === 'SUCCESS' ? 'default' : 'destructive'
}
</script>

<template>
  <div>
    <FaPageHeader title="集成调用" class="mb-0">
      <FaButton v-if="activeTab === 'connectors'" v-auth="'platform:integration:edit'" @click="openCreateConnector">
        <FaIcon name="i-ri:add-line" />
        新增连接器
      </FaButton>
      <FaButton v-if="activeTab === 'scripts'" v-auth="'platform:integration:edit'" @click="openCreateScript">
        <FaIcon name="i-ri:add-line" />
        新增脚本
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div class="integration-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <FaIcon :name="tab.icon" />
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <FaTable
        v-if="activeTab === 'connectors'"
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1280px]"
        border
        stripe
        column-visibility
        :columns="connectorColumns"
        :data="connectorRows"
      >
        <template #toolbar>
          <FaSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
              <FaInput v-model="search.keyword" clearable placeholder="名称 / 编码 / 地址 / 日志关键字" @keydown.enter="load" @clear="load" />
              <div class="flex gap-2 md:justify-end">
                <FaButton variant="outline" @click="resetSearch">重置</FaButton>
                <FaButton :loading="loading" @click="load">
                  <FaIcon name="i-ri:search-line" />
                  筛选
                </FaButton>
              </div>
            </div>
          </FaSearchBar>
        </template>
        <template #cell-method="{ row }">
          <FaTag variant="secondary">{{ row.original.method }}</FaTag>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="statusVariant(row.original.status)">{{ statusText(row.original.status) }}</FaTag>
        </template>
        <template #cell-operation="{ row }">
          <div class="table-actions">
            <FaButton v-auth="'platform:integration:invoke'" size="sm" variant="outline" :disabled="row.original.status !== 'ACTIVE'" @click="openInvoke(row.original)">
              调用
            </FaButton>
            <FaButton v-auth="'platform:integration:edit'" size="sm" variant="ghost" @click="openEditConnector(row.original)">
              编辑
            </FaButton>
            <FaButton v-auth="'platform:integration:edit'" size="sm" variant="ghost" :disabled="row.original.status !== 'ACTIVE'" @click="confirmDisableConnector(row.original)">
              停用
            </FaButton>
          </div>
        </template>
      </FaTable>

      <FaTable
        v-else-if="activeTab === 'httpLogs'"
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1320px]"
        border
        stripe
        column-visibility
        :columns="httpLogColumns"
        :data="httpLogRows"
      >
        <template #toolbar>
          <FaSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
              <FaInput v-model="search.keyword" clearable placeholder="名称 / 编码 / 地址 / 日志关键字" @keydown.enter="load" @clear="load" />
              <div class="flex gap-2 md:justify-end">
                <FaButton variant="outline" @click="resetSearch">重置</FaButton>
                <FaButton :loading="loading" @click="load">
                  <FaIcon name="i-ri:search-line" />
                  筛选
                </FaButton>
              </div>
            </div>
          </FaSearchBar>
        </template>
        <template #cell-method="{ row }">
          <FaTag variant="secondary">{{ row.original.method }}</FaTag>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="executionVariant(row.original.status)">{{ executionText(row.original.status) }}</FaTag>
        </template>
        <template #cell-invokedAt="{ row }">
          {{ dateText(row.original.invokedAt) }}
        </template>
        <template #cell-errorMessage="{ row }">
          <span class="line-clamp-1">{{ row.original.errorMessage || '-' }}</span>
        </template>
      </FaTable>

      <FaTable
        v-else-if="activeTab === 'scripts'"
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1080px]"
        border
        stripe
        column-visibility
        :columns="scriptColumns"
        :data="scriptRows"
      >
        <template #toolbar>
          <FaSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
              <FaInput v-model="search.keyword" clearable placeholder="名称 / 编码 / 地址 / 日志关键字" @keydown.enter="load" @clear="load" />
              <div class="flex gap-2 md:justify-end">
                <FaButton variant="outline" @click="resetSearch">重置</FaButton>
                <FaButton :loading="loading" @click="load">
                  <FaIcon name="i-ri:search-line" />
                  筛选
                </FaButton>
              </div>
            </div>
          </FaSearchBar>
        </template>
        <template #cell-language="{ row }">
          <FaTag variant="secondary">{{ row.original.language }}</FaTag>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="statusVariant(row.original.status)">{{ statusText(row.original.status) }}</FaTag>
        </template>
        <template #cell-updateTime="{ row }">
          {{ dateText(row.original.updateTime) }}
        </template>
        <template #cell-operation="{ row }">
          <div class="table-actions">
            <FaButton v-auth="'platform:integration:execute'" size="sm" variant="outline" :disabled="row.original.status !== 'ACTIVE'" @click="openExecute(row.original)">
              执行
            </FaButton>
            <FaButton v-auth="'platform:integration:edit'" size="sm" variant="ghost" @click="openEditScript(row.original)">
              编辑
            </FaButton>
            <FaButton v-auth="'platform:integration:edit'" size="sm" variant="ghost" :disabled="row.original.status !== 'ACTIVE'" @click="confirmDisableScript(row.original)">
              停用
            </FaButton>
          </div>
        </template>
      </FaTable>

      <FaTable
        v-else
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1280px]"
        border
        stripe
        column-visibility
        :columns="runtimeLogColumns"
        :data="runtimeLogRows"
      >
        <template #toolbar>
          <FaSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
              <FaInput v-model="search.keyword" clearable placeholder="名称 / 编码 / 地址 / 日志关键字" @keydown.enter="load" @clear="load" />
              <div class="flex gap-2 md:justify-end">
                <FaButton variant="outline" @click="resetSearch">重置</FaButton>
                <FaButton :loading="loading" @click="load">
                  <FaIcon name="i-ri:search-line" />
                  筛选
                </FaButton>
              </div>
            </div>
          </FaSearchBar>
        </template>
        <template #cell-language="{ row }">
          <FaTag variant="secondary">{{ row.original.language }}</FaTag>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="executionVariant(row.original.status)">{{ executionText(row.original.status) }}</FaTag>
        </template>
        <template #cell-executedAt="{ row }">
          {{ dateText(row.original.executedAt) }}
        </template>
        <template #cell-stdout="{ row }">
          <span class="line-clamp-1">{{ row.original.stdout || row.original.stderr || '-' }}</span>
        </template>
        <template #cell-errorMessage="{ row }">
          <span class="line-clamp-1">{{ row.original.errorMessage || '-' }}</span>
        </template>
      </FaTable>

      <FaPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        class="mt-3"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />
    </FaPageMain>

    <FaModal v-model="connectorVisible" :title="connectorEditing ? '编辑 HTTP 连接器' : '新增 HTTP 连接器'" show-cancel-button class="sm:max-w-4xl" @confirm="saveConnector">
      <a-form :model="connectorForm" layout="vertical">
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <a-form-item label="名称" required>
            <FaInput v-model="connectorForm.name" />
          </a-form-item>
          <a-form-item label="编码" required>
            <FaInput v-model="connectorForm.code" :disabled="!!connectorEditing" />
            <div v-if="connectorEditing" class="field-tip">编码用于日志和调用关联，创建后不可修改。</div>
          </a-form-item>
          <a-form-item label="请求地址" required class="md:col-span-2">
            <FaInput v-model="connectorForm.url" placeholder="https://api.example.com/resource" />
          </a-form-item>
          <a-form-item label="请求方法">
            <FaSelect v-model="connectorForm.method" :options="methodOptions" />
          </a-form-item>
          <a-form-item label="状态">
            <FaSelect v-model="connectorForm.status" :options="statusOptions" />
          </a-form-item>
          <a-form-item label="超时毫秒">
            <FaInput v-model.number="connectorForm.timeoutMillis" type="number" />
          </a-form-item>
          <a-form-item label="重试次数">
            <FaInput v-model.number="connectorForm.retryTimes" type="number" />
          </a-form-item>
          <a-form-item label="请求头 JSON">
            <FaTextarea v-model="connectorJson.headers" rows="6" input-class="font-mono" />
          </a-form-item>
          <a-form-item label="查询参数 JSON">
            <FaTextarea v-model="connectorJson.queryParams" rows="6" input-class="font-mono" />
          </a-form-item>
          <a-form-item label="请求体模板" class="md:col-span-2">
            <FaTextarea v-model="connectorForm.bodyTemplate" rows="6" input-class="font-mono" />
          </a-form-item>
        </div>
      </a-form>
    </FaModal>

    <FaModal v-model="invokeVisible" title="执行 HTTP 调用" show-cancel-button class="sm:max-w-4xl" :confirm-loading="actionLoading === 'invoke'" @confirm="invokeConnector">
      <div class="modal-grid">
        <a-form :model="{}" layout="vertical">
          <a-form-item label="请求头 JSON">
            <FaTextarea v-model="invokeForm.headers" rows="5" input-class="font-mono" />
          </a-form-item>
          <a-form-item label="查询参数 JSON">
            <FaTextarea v-model="invokeForm.queryParams" rows="5" input-class="font-mono" />
          </a-form-item>
          <a-form-item label="请求体">
            <FaTextarea v-model="invokeForm.body" rows="7" input-class="font-mono" />
          </a-form-item>
        </a-form>
        <section class="result-panel">
          <div class="section-title">最近结果</div>
          <pre>{{ lastHttpLog ? JSON.stringify(lastHttpLog, null, 2) : '尚未执行' }}</pre>
        </section>
      </div>
    </FaModal>

    <FaModal v-model="scriptVisible" :title="scriptEditing ? '编辑运行脚本' : '新增运行脚本'" show-cancel-button class="sm:max-w-4xl" @confirm="saveScript">
      <a-form :model="scriptForm" layout="vertical">
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <a-form-item label="名称" required>
            <FaInput v-model="scriptForm.name" />
          </a-form-item>
          <a-form-item label="编码" required>
            <FaInput v-model="scriptForm.code" :disabled="!!scriptEditing" />
            <div v-if="scriptEditing" class="field-tip">编码用于执行记录关联，创建后不可修改。</div>
          </a-form-item>
          <a-form-item label="语言">
            <FaSelect v-model="scriptForm.language" :options="languageOptions" />
          </a-form-item>
          <a-form-item label="状态">
            <FaSelect v-model="scriptForm.status" :options="statusOptions" />
          </a-form-item>
          <a-form-item label="超时毫秒">
            <FaInput v-model.number="scriptForm.timeoutMillis" type="number" />
          </a-form-item>
          <a-form-item label="环境变量 JSON">
            <FaTextarea v-model="scriptEnvJson" rows="5" input-class="font-mono" />
          </a-form-item>
          <a-form-item label="脚本内容" required class="md:col-span-2">
            <FaTextarea v-model="scriptForm.scriptContent" rows="14" input-class="font-mono" />
          </a-form-item>
        </div>
      </a-form>
    </FaModal>

    <FaModal v-model="executeVisible" title="执行运行脚本" show-cancel-button class="sm:max-w-4xl" :confirm-loading="actionLoading === 'execute'" @confirm="executeScript">
      <div class="modal-grid">
        <a-form :model="{}" layout="vertical">
          <a-form-item label="标准输入">
            <FaTextarea v-model="executeStdin" rows="8" input-class="font-mono" />
          </a-form-item>
        </a-form>
        <section class="result-panel">
          <div class="section-title">最近结果</div>
          <pre>{{ lastRuntimeLog ? JSON.stringify(lastRuntimeLog, null, 2) : '尚未执行' }}</pre>
        </section>
      </div>
    </FaModal>
  </div>
</template>

<style scoped>
.integration-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.integration-tabs button {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
  color: var(--color-text-2);
}

.integration-tabs button.active,
.integration-tabs button:hover {
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
}

.table-actions {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}

.modal-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 360px);
  gap: 16px;
  align-items: start;
}

.result-panel {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.section-title {
  color: var(--color-text-1);
  font-weight: 700;
}

.field-tip {
  margin-top: 6px;
  color: var(--color-text-3);
  font-size: 12px;
}

.result-panel pre {
  max-height: 420px;
  min-height: 220px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-fill-1);
  color: var(--color-text-1);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 900px) {
  .modal-grid {
    grid-template-columns: 1fr;
  }
}
</style>

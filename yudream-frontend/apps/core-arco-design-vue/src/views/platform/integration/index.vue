<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
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

const modal = useYdModal()
const toast = useYdToast()

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
  { key: 'connectors', label: 'HTTP 连接器', icon: 'morph:link' },
  { key: 'httpLogs', label: 'HTTP 日志', icon: 'morph:file-text' },
  { key: 'scripts', label: '运行脚本', icon: 'morph:square-terminal' },
  { key: 'runtimeLogs', label: '运行日志', icon: 'morph:bug' },
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

function confirmEnableConnector(row: HttpConnector) {
  modal.confirm({
    title: '确认启用',
    content: `确认启用 HTTP 连接器「${row.name}」吗？`,
    onConfirm: async () => {
      await apiIntegration.enableConnector(row.id)
      toast.success('HTTP 连接器已启用')
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

function confirmEnableScript(row: RuntimeScript) {
  modal.confirm({
    title: '确认启用',
    content: `确认启用运行脚本「${row.name}」吗？`,
    onConfirm: async () => {
      await apiIntegration.enableScript(row.id)
      toast.success('运行脚本已启用')
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
    <YdPageHeader title="集成调用" class="mb-0">
      <YdButton v-if="activeTab === 'connectors'" v-auth="'platform:integration:edit'" @click="openCreateConnector">
        <YdIcon name="morph:plus" />
        新增连接器
      </YdButton>
      <YdButton v-if="activeTab === 'scripts'" v-auth="'platform:integration:edit'" @click="openCreateScript">
        <YdIcon name="morph:plus" />
        新增脚本
      </YdButton>
    </YdPageHeader>

    <YdPageMain>
      <div class="integration-tabs">
        <YdAction
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <YdIcon :name="tab.icon" />
          <span>{{ tab.label }}</span>
        </YdAction>
      </div>

      <YdLoadingOverlay v-if="activeTab === 'connectors'" :loading="loading"><YdResponsiveTable
        
        
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
          <YdSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
              <YdInput v-model="search.keyword" clearable placeholder="名称 / 编码 / 地址 / 日志关键字" @keydown.enter="load" @clear="load" />
              <div class="flex gap-2 md:justify-end">
                <YdButton variant="outline" @click="resetSearch">重置</YdButton>
                <YdButton :loading="loading" @click="load">
                  <YdIcon name="morph:search" />
                  筛选
                </YdButton>
              </div>
            </div>
          </YdSearchBar>
        </template>
        <template #cell-method="{ row }">
          <YdTag variant="secondary">{{ row.original.method }}</YdTag>
        </template>
        <template #cell-status="{ row }">
          <YdTag :variant="statusVariant(row.original.status)">{{ statusText(row.original.status) }}</YdTag>
        </template>
        <template #cell-operation="{ row }">
          <div class="table-actions">
            <YdButton v-auth="'platform:integration:invoke'" size="sm" variant="outline" :disabled="row.original.status !== 'ACTIVE'" @click="openInvoke(row.original)">
              调用
            </YdButton>
            <YdButton v-auth="'platform:integration:edit'" size="sm" variant="ghost" @click="openEditConnector(row.original)">
              编辑
            </YdButton>
            <YdButton v-if="row.original.status === 'ACTIVE'" v-auth="'platform:integration:edit'" size="sm" variant="ghost" @click="confirmDisableConnector(row.original)">
              停用
            </YdButton>
            <YdButton v-else v-auth="'platform:integration:edit'" size="sm" variant="outline" @click="confirmEnableConnector(row.original)">
              启用
            </YdButton>
          </div>
        </template>
        <template #card="{ row }">
          <YdCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 break-words text-base font-semibold">{{ row.name }}</span>
                <YdTag :variant="statusVariant(row.status)">{{ statusText(row.status) }}</YdTag>
              </div>
              <div class="flex flex-col gap-1 text-sm">
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">编码</span>
                  <span>{{ row.code }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">方法</span>
                  <span>{{ row.method }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">地址</span>
                  <span class="break-all">{{ row.url }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">超时</span>
                  <span>{{ row.timeoutMillis }}ms</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">重试</span>
                  <span>{{ row.retryTimes }}</span>
                </div>
              </div>
              <div class="flex flex-wrap gap-2 border-t pt-3">
                <YdButton v-auth="'platform:integration:invoke'" size="sm" variant="outline" :disabled="row.status !== 'ACTIVE'" @click="openInvoke(row)">
                  调用
                </YdButton>
                <YdButton v-auth="'platform:integration:edit'" size="sm" variant="ghost" @click="openEditConnector(row)">
                  编辑
                </YdButton>
                <YdButton v-if="row.status === 'ACTIVE'" v-auth="'platform:integration:edit'" size="sm" variant="ghost" @click="confirmDisableConnector(row)">
                  停用
                </YdButton>
                <YdButton v-else v-auth="'platform:integration:edit'" size="sm" variant="outline" @click="confirmEnableConnector(row)">
                  启用
                </YdButton>
              </div>
            </div>
          </YdCard>
        </template>
      </YdResponsiveTable></YdLoadingOverlay>

      <YdLoadingOverlay v-else-if="activeTab === 'httpLogs'" :loading="loading"><YdResponsiveTable
        
        
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
          <YdSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
              <YdInput v-model="search.keyword" clearable placeholder="名称 / 编码 / 地址 / 日志关键字" @keydown.enter="load" @clear="load" />
              <div class="flex gap-2 md:justify-end">
                <YdButton variant="outline" @click="resetSearch">重置</YdButton>
                <YdButton :loading="loading" @click="load">
                  <YdIcon name="morph:search" />
                  筛选
                </YdButton>
              </div>
            </div>
          </YdSearchBar>
        </template>
        <template #cell-method="{ row }">
          <YdTag variant="secondary">{{ row.original.method }}</YdTag>
        </template>
        <template #cell-status="{ row }">
          <YdTag :variant="executionVariant(row.original.status)">{{ executionText(row.original.status) }}</YdTag>
        </template>
        <template #cell-invokedAt="{ row }">
          {{ dateText(row.original.invokedAt) }}
        </template>
        <template #cell-errorMessage="{ row }">
          <span class="line-clamp-1">{{ row.original.errorMessage || '-' }}</span>
        </template>
        <template #card="{ row }">
          <YdCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 break-words text-base font-semibold">{{ row.connectorCode }}</span>
                <YdTag :variant="executionVariant(row.status)">{{ executionText(row.status) }}</YdTag>
              </div>
              <div class="flex flex-col gap-1 text-sm">
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">方法</span>
                  <span>{{ row.method }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">地址</span>
                  <span class="break-all">{{ row.url }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">响应码</span>
                  <span>{{ row.responseStatus }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">耗时</span>
                  <span>{{ row.durationMillis }}ms</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">调用时间</span>
                  <span>{{ dateText(row.invokedAt) }}</span>
                </div>
                <div v-if="row.errorMessage" class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">异常</span>
                  <span class="break-all">{{ row.errorMessage }}</span>
                </div>
              </div>
            </div>
          </YdCard>
        </template>
      </YdResponsiveTable></YdLoadingOverlay>

      <YdLoadingOverlay v-else-if="activeTab === 'scripts'" :loading="loading"><YdResponsiveTable
        
        
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
          <YdSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
              <YdInput v-model="search.keyword" clearable placeholder="名称 / 编码 / 地址 / 日志关键字" @keydown.enter="load" @clear="load" />
              <div class="flex gap-2 md:justify-end">
                <YdButton variant="outline" @click="resetSearch">重置</YdButton>
                <YdButton :loading="loading" @click="load">
                  <YdIcon name="morph:search" />
                  筛选
                </YdButton>
              </div>
            </div>
          </YdSearchBar>
        </template>
        <template #cell-language="{ row }">
          <YdTag variant="secondary">{{ row.original.language }}</YdTag>
        </template>
        <template #cell-status="{ row }">
          <YdTag :variant="statusVariant(row.original.status)">{{ statusText(row.original.status) }}</YdTag>
        </template>
        <template #cell-updateTime="{ row }">
          {{ dateText(row.original.updateTime) }}
        </template>
        <template #cell-operation="{ row }">
          <div class="table-actions">
            <YdButton v-auth="'platform:integration:execute'" size="sm" variant="outline" :disabled="row.original.status !== 'ACTIVE'" @click="openExecute(row.original)">
              执行
            </YdButton>
            <YdButton v-auth="'platform:integration:edit'" size="sm" variant="ghost" @click="openEditScript(row.original)">
              编辑
            </YdButton>
            <YdButton v-if="row.original.status === 'ACTIVE'" v-auth="'platform:integration:edit'" size="sm" variant="ghost" @click="confirmDisableScript(row.original)">
              停用
            </YdButton>
            <YdButton v-else v-auth="'platform:integration:edit'" size="sm" variant="outline" @click="confirmEnableScript(row.original)">
              启用
            </YdButton>
          </div>
        </template>
        <template #card="{ row }">
          <YdCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 break-words text-base font-semibold">{{ row.name }}</span>
                <YdTag :variant="statusVariant(row.status)">{{ statusText(row.status) }}</YdTag>
              </div>
              <div class="flex flex-col gap-1 text-sm">
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">编码</span>
                  <span>{{ row.code }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">语言</span>
                  <span>{{ row.language }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">超时</span>
                  <span>{{ row.timeoutMillis }}ms</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">更新时间</span>
                  <span>{{ dateText(row.updateTime) }}</span>
                </div>
              </div>
              <div class="flex flex-wrap gap-2 border-t pt-3">
                <YdButton v-auth="'platform:integration:execute'" size="sm" variant="outline" :disabled="row.status !== 'ACTIVE'" @click="openExecute(row)">
                  执行
                </YdButton>
                <YdButton v-auth="'platform:integration:edit'" size="sm" variant="ghost" @click="openEditScript(row)">
                  编辑
                </YdButton>
                <YdButton v-if="row.status === 'ACTIVE'" v-auth="'platform:integration:edit'" size="sm" variant="ghost" @click="confirmDisableScript(row)">
                  停用
                </YdButton>
                <YdButton v-else v-auth="'platform:integration:edit'" size="sm" variant="outline" @click="confirmEnableScript(row)">
                  启用
                </YdButton>
              </div>
            </div>
          </YdCard>
        </template>
      </YdResponsiveTable></YdLoadingOverlay>

      <YdLoadingOverlay v-else :loading="loading"><YdResponsiveTable
        
        
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
          <YdSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
              <YdInput v-model="search.keyword" clearable placeholder="名称 / 编码 / 地址 / 日志关键字" @keydown.enter="load" @clear="load" />
              <div class="flex gap-2 md:justify-end">
                <YdButton variant="outline" @click="resetSearch">重置</YdButton>
                <YdButton :loading="loading" @click="load">
                  <YdIcon name="morph:search" />
                  筛选
                </YdButton>
              </div>
            </div>
          </YdSearchBar>
        </template>
        <template #cell-language="{ row }">
          <YdTag variant="secondary">{{ row.original.language }}</YdTag>
        </template>
        <template #cell-status="{ row }">
          <YdTag :variant="executionVariant(row.original.status)">{{ executionText(row.original.status) }}</YdTag>
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
        <template #card="{ row }">
          <YdCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 break-words text-base font-semibold">{{ row.scriptCode }}</span>
                <YdTag :variant="executionVariant(row.status)">{{ executionText(row.status) }}</YdTag>
              </div>
              <div class="flex flex-col gap-1 text-sm">
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">语言</span>
                  <span>{{ row.language }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">退出码</span>
                  <span>{{ row.exitCode }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">耗时</span>
                  <span>{{ row.durationMillis }}ms</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">执行时间</span>
                  <span>{{ dateText(row.executedAt) }}</span>
                </div>
                <div v-if="row.stdout || row.stderr" class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">输出</span>
                  <span class="break-all">{{ row.stdout || row.stderr }}</span>
                </div>
                <div v-if="row.errorMessage" class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">异常</span>
                  <span class="break-all">{{ row.errorMessage }}</span>
                </div>
              </div>
            </div>
          </YdCard>
        </template>
      </YdResponsiveTable></YdLoadingOverlay>

      <YdPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        class="mt-3"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />
    </YdPageMain>

    <YdModal v-model="connectorVisible" :title="connectorEditing ? '编辑 HTTP 连接器' : '新增 HTTP 连接器'" show-cancel-button class="sm:max-w-4xl" @confirm="saveConnector">
      <YdForm :model="connectorForm" layout="vertical">
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <YdFormItem label="名称" required>
            <YdInput v-model="connectorForm.name" />
          </YdFormItem>
          <YdFormItem label="编码" required>
            <YdInput v-model="connectorForm.code" :disabled="!!connectorEditing" />
            <div v-if="connectorEditing" class="field-tip">编码用于日志和调用关联，创建后不可修改。</div>
          </YdFormItem>
          <YdFormItem label="请求地址" required class="md:col-span-2">
            <YdInput v-model="connectorForm.url" placeholder="https://api.example.com/resource" />
          </YdFormItem>
          <YdFormItem label="请求方法">
            <YdSelect v-model="connectorForm.method" :options="methodOptions" />
          </YdFormItem>
          <YdFormItem label="状态">
            <YdSelect v-model="connectorForm.status" :options="statusOptions" />
          </YdFormItem>
          <YdFormItem label="超时毫秒">
            <YdInput v-model.number="connectorForm.timeoutMillis" type="number" />
          </YdFormItem>
          <YdFormItem label="重试次数">
            <YdInput v-model.number="connectorForm.retryTimes" type="number" />
          </YdFormItem>
          <YdFormItem label="请求头 JSON">
            <YdTextarea v-model="connectorJson.headers" rows="6" input-class="font-mono" />
          </YdFormItem>
          <YdFormItem label="查询参数 JSON">
            <YdTextarea v-model="connectorJson.queryParams" rows="6" input-class="font-mono" />
          </YdFormItem>
          <YdFormItem label="请求体模板" class="md:col-span-2">
            <YdTextarea v-model="connectorForm.bodyTemplate" rows="6" input-class="font-mono" />
          </YdFormItem>
        </div>
      </YdForm>
    </YdModal>

    <YdModal v-model="invokeVisible" title="执行 HTTP 调用" show-cancel-button class="sm:max-w-4xl" :confirm-loading="actionLoading === 'invoke'" @confirm="invokeConnector">
      <div class="modal-grid">
        <YdForm :model="{}" layout="vertical">
          <YdFormItem label="请求头 JSON">
            <YdTextarea v-model="invokeForm.headers" rows="5" input-class="font-mono" />
          </YdFormItem>
          <YdFormItem label="查询参数 JSON">
            <YdTextarea v-model="invokeForm.queryParams" rows="5" input-class="font-mono" />
          </YdFormItem>
          <YdFormItem label="请求体">
            <YdTextarea v-model="invokeForm.body" rows="7" input-class="font-mono" />
          </YdFormItem>
        </YdForm>
        <section class="result-panel">
          <div class="section-title">最近结果</div>
          <pre>{{ lastHttpLog ? JSON.stringify(lastHttpLog, null, 2) : '尚未执行' }}</pre>
        </section>
      </div>
    </YdModal>

    <YdModal v-model="scriptVisible" :title="scriptEditing ? '编辑运行脚本' : '新增运行脚本'" show-cancel-button class="sm:max-w-4xl" @confirm="saveScript">
      <YdForm :model="scriptForm" layout="vertical">
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <YdFormItem label="名称" required>
            <YdInput v-model="scriptForm.name" />
          </YdFormItem>
          <YdFormItem label="编码" required>
            <YdInput v-model="scriptForm.code" :disabled="!!scriptEditing" />
            <div v-if="scriptEditing" class="field-tip">编码用于执行记录关联，创建后不可修改。</div>
          </YdFormItem>
          <YdFormItem label="语言">
            <YdSelect v-model="scriptForm.language" :options="languageOptions" />
          </YdFormItem>
          <YdFormItem label="状态">
            <YdSelect v-model="scriptForm.status" :options="statusOptions" />
          </YdFormItem>
          <YdFormItem label="超时毫秒">
            <YdInput v-model.number="scriptForm.timeoutMillis" type="number" />
          </YdFormItem>
          <YdFormItem label="环境变量 JSON">
            <YdTextarea v-model="scriptEnvJson" rows="5" input-class="font-mono" />
          </YdFormItem>
          <YdFormItem label="脚本内容" required class="md:col-span-2">
            <YdTextarea v-model="scriptForm.scriptContent" rows="14" input-class="font-mono" />
          </YdFormItem>
        </div>
      </YdForm>
    </YdModal>

    <YdModal v-model="executeVisible" title="执行运行脚本" show-cancel-button class="sm:max-w-4xl" :confirm-loading="actionLoading === 'execute'" @confirm="executeScript">
      <div class="modal-grid">
        <YdForm :model="{}" layout="vertical">
          <YdFormItem label="标准输入">
            <YdTextarea v-model="executeStdin" rows="8" input-class="font-mono" />
          </YdFormItem>
        </YdForm>
        <section class="result-panel">
          <div class="section-title">最近结果</div>
          <pre>{{ lastRuntimeLog ? JSON.stringify(lastRuntimeLog, null, 2) : '尚未执行' }}</pre>
        </section>
      </div>
    </YdModal>
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
  border-color: var(--color-text-1);
  color: var(--color-text-1);
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

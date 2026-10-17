<script setup lang="ts">
import type { ActionMenuItem, StatusColumn, TableColumn, YdSegmentedOption, YdTablePageAction } from '@yudream/components'
import type {
  ConnectorStatus,
  ExecutionStatus,
  HttpConnector,
  HttpConnectorPayload,
  HttpInvocationLog,
  HttpMethodType,
  IntegrationPageParams,
  RuntimeExecutionLog,
  RuntimeLanguage,
  RuntimeScript,
  RuntimeScriptPayload,
} from '@/api/modules/platform-integration'
import { useTablePage } from '@yudream/components'
import apiIntegration from '@/api/modules/platform-integration'
import { fetchSearchConfig } from '@/api/modules/system-search-config'

const modal = useYdModal()
const toast = useYdToast()
const { auth } = useAppAuth()
const isMobile = useIsMobile()

type IntegrationTab = 'connectors' | 'httpLogs' | 'scripts' | 'runtimeLogs'
/** 四个标签页共用同一查询类、搜索条件与分页；行数据类型按当前标签页取用 */
type IntegrationRow = HttpConnector | HttpInvocationLog | RuntimeScript | RuntimeExecutionLog

const activeTab = ref<IntegrationTab>('connectors')
const actionLoading = ref('')

/** 标签页切换：数据源随视图切换，版式统一由 YdTablePageLayout 承载 */
function requestPage(tab: IntegrationTab, params: IntegrationPageParams) {
  if (tab === 'connectors') {
    return apiIntegration.pageConnectors(params)
  }
  if (tab === 'httpLogs') {
    return apiIntegration.pageHttpLogs(params)
  }
  if (tab === 'scripts') {
    return apiIntegration.pageScripts(params)
  }
  return apiIntegration.pageRuntimeLogs(params)
}

/** 列表：搜索条件来自 IntegrationPageQuery 的 @Searchable 注解，分页由后端切片 */
const {
  loading,
  rows,
  pagination,
  searchQuery,
  searchConfig,
  load,
  search,
  resetSearch,
  onPageChange,
  onSizeChange,
} = useTablePage<IntegrationRow>({
  queryClass: 'online.yudream.base.application.platform.integration.query.IntegrationPageQuery',
  loadApi: async (query) => {
    const page = await requestPage(activeTab.value, {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    return {
      records: page.data.records,
      total: page.data.total,
      page: page.data.page,
      size: page.data.size,
    }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
})

/** 当前标签页的数据：查询结果只属于当前视图，其余表格拿空数组兜底 */
const connectorRows = computed(() => (activeTab.value === 'connectors' ? rows.value as HttpConnector[] : []))
const httpLogRows = computed(() => (activeTab.value === 'httpLogs' ? rows.value as HttpInvocationLog[] : []))
const scriptRows = computed(() => (activeTab.value === 'scripts' ? rows.value as RuntimeScript[] : []))
const runtimeLogRows = computed(() => (activeTab.value === 'runtimeLogs' ? rows.value as RuntimeExecutionLog[] : []))

watch(activeTab, () => {
  pagination.page = 1
  load()
})

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
const statusOptions: { label: string, value: ConnectorStatus }[] = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
]
const languageOptions: { label: string, value: RuntimeLanguage }[] = [
  { label: 'Python', value: 'PYTHON' },
]
const tabOptions: YdSegmentedOption<IntegrationTab>[] = [
  { label: 'HTTP 连接器', value: 'connectors' },
  { label: 'HTTP 日志', value: 'httpLogs' },
  { label: '运行脚本', value: 'scripts' },
  { label: '运行日志', value: 'runtimeLogs' },
]
const tabIcons: Record<string, string> = {
  connectors: 'morph:link',
  httpLogs: 'morph:file-text',
  scripts: 'morph:square-terminal',
  runtimeLogs: 'morph:bug',
}

function tabIcon(value: string | number) {
  return tabIcons[String(value)] || 'morph:link'
}

/** 启停状态词表：字段值是 ACTIVE / DISABLED，statusMap 的键用字段值字符串 */
const connectorStatusMap = {
  ACTIVE: { label: '启用', variant: 'default' as const, tone: 'success' as const },
  DISABLED: { label: '停用', variant: 'secondary' as const, tone: 'neutral' as const },
}
/** 执行结果词表：成功 / 失败 / 超时共用一套语义色 */
const executionStatusMap = {
  SUCCESS: { label: '成功', variant: 'default' as const, tone: 'success' as const },
  FAILED: { label: '失败', variant: 'destructive' as const, tone: 'danger' as const },
  TIMEOUT: { label: '超时', variant: 'secondary' as const, tone: 'warning' as const },
}

const connectorStatusColumn: StatusColumn<HttpConnector> = {
  statusKey: 'status',
  header: '状态',
  width: 100,
  statusMap: connectorStatusMap,
}

const httpLogStatusColumn: StatusColumn<HttpInvocationLog> = {
  statusKey: 'status',
  header: '状态',
  width: 100,
  statusMap: executionStatusMap,
}

const scriptStatusColumn: StatusColumn<RuntimeScript> = {
  statusKey: 'status',
  header: '状态',
  width: 100,
  statusMap: connectorStatusMap,
}

const runtimeLogStatusColumn: StatusColumn<RuntimeExecutionLog> = {
  statusKey: 'status',
  header: '状态',
  width: 100,
  statusMap: executionStatusMap,
}

// 编码 / 更新时间 / 输出属于技术字段与审计字段，列显隐菜单里可展开
const connectorColumns: TableColumn<HttpConnector>[] = [
  { accessorKey: 'name', header: '名称', width: 180, minWidth: 140, fixed: 'left' },
  { accessorKey: 'code', header: '编码', width: 160, defaultHidden: true },
  { id: 'method', header: '方法', width: 90, align: 'center' },
  { accessorKey: 'url', header: '请求地址', minWidth: 280 },
  { accessorKey: 'timeoutMillis', header: '超时', width: 100, align: 'right' },
  { accessorKey: 'retryTimes', header: '重试', width: 90, align: 'right' },
]

const httpLogColumns: TableColumn<HttpInvocationLog>[] = [
  { accessorKey: 'connectorCode', header: '连接器', width: 160, minWidth: 140, fixed: 'left' },
  { id: 'method', header: '方法', width: 90, align: 'center' },
  { accessorKey: 'url', header: '请求地址', minWidth: 280 },
  { accessorKey: 'responseStatus', header: '响应码', width: 90, align: 'center' },
  { accessorKey: 'durationMillis', header: '耗时', width: 100, align: 'right' },
  { id: 'invokedAt', header: '调用时间', width: 180 },
  { id: 'errorMessage', header: '异常', minWidth: 200 },
]

const scriptColumns: TableColumn<RuntimeScript>[] = [
  { accessorKey: 'name', header: '名称', width: 180, minWidth: 140, fixed: 'left' },
  { accessorKey: 'code', header: '编码', width: 160, defaultHidden: true },
  { id: 'language', header: '语言', width: 100, align: 'center' },
  { accessorKey: 'timeoutMillis', header: '超时', width: 100, align: 'right' },
  { id: 'updateTime', header: '更新时间', width: 180, defaultHidden: true },
]

const runtimeLogColumns: TableColumn<RuntimeExecutionLog>[] = [
  { accessorKey: 'scriptCode', header: '脚本', width: 160, minWidth: 140, fixed: 'left' },
  { id: 'language', header: '语言', width: 100, align: 'center' },
  { accessorKey: 'exitCode', header: '退出码', width: 90, align: 'center' },
  { accessorKey: 'durationMillis', header: '耗时', width: 100, align: 'right' },
  { id: 'executedAt', header: '执行时间', width: 180 },
  { id: 'stdout', header: '输出', minWidth: 240, defaultHidden: true },
  { id: 'errorMessage', header: '异常', minWidth: 200 },
]

/** 连接器行操作：调用 / 编辑 / 启停统一走表格内置操作列，移动端卡片保留原位按钮 */
const connectorRowActions: ActionMenuItem<HttpConnector>[] = [
  {
    label: '调用',
    icon: 'morph:play',
    visible: () => auth('platform:integration:invoke'),
    disabled: row => row.status !== 'ACTIVE',
    onClick: openInvoke,
  },
  {
    label: '编辑',
    icon: 'morph:pencil',
    visible: () => auth('platform:integration:edit'),
    onClick: openEditConnector,
  },
  {
    label: '停用',
    icon: 'morph:ban',
    danger: true,
    visible: row => row.status === 'ACTIVE' && auth('platform:integration:edit'),
    onClick: confirmDisableConnector,
  },
  {
    label: '启用',
    icon: 'morph:circle-check',
    visible: row => row.status !== 'ACTIVE' && auth('platform:integration:edit'),
    onClick: confirmEnableConnector,
  },
]

/** 脚本行操作：执行 / 编辑 / 启停；停用态脚本不可执行，用 disabled 表达 */
const scriptRowActions: ActionMenuItem<RuntimeScript>[] = [
  {
    label: '执行',
    icon: 'morph:square-terminal',
    visible: () => auth('platform:integration:execute'),
    disabled: row => row.status !== 'ACTIVE',
    onClick: openExecute,
  },
  {
    label: '编辑',
    icon: 'morph:pencil',
    visible: () => auth('platform:integration:edit'),
    onClick: openEditScript,
  },
  {
    label: '停用',
    icon: 'morph:ban',
    danger: true,
    visible: row => row.status === 'ACTIVE' && auth('platform:integration:edit'),
    onClick: confirmDisableScript,
  },
  {
    label: '启用',
    icon: 'morph:circle-check',
    visible: row => row.status !== 'ACTIVE' && auth('platform:integration:edit'),
    onClick: confirmEnableScript,
  },
]

/** 页头操作：只有连接器与脚本视图可新建，权限与视图条件在这里收敛 */
const pageActions = computed<YdTablePageAction[]>(() => [
  ...(activeTab.value === 'connectors' && auth('platform:integration:edit')
    ? [{ label: '新增连接器', icon: 'morph:plus', onClick: openCreateConnector }]
    : []),
  ...(activeTab.value === 'scripts' && auth('platform:integration:edit')
    ? [{ label: '新增脚本', icon: 'morph:plus', onClick: openCreateScript }]
    : []),
])

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
    <YdTablePageLayout
      title="集成调用"
      :actions="pageActions"
      :actions-threshold="3"
    >
      <!-- 四个视图共用同一套筛选与分页：Tab 只切换数据源，外壳由布局统一承载 -->
      <template #tabs>
        <YdSegmented v-model="activeTab" :options="tabOptions" :block="isMobile" size="sm" aria-label="集成调用视图切换">
          <template #option="{ option }">
            <span class="inline-flex gap-1.5 items-center">
              <YdIcon :name="tabIcon(option.value)" />
              {{ option.label }}
            </span>
          </template>
        </YdSegmented>
      </template>

      <template #search="{ fold }">
        <YdDynamicSearchForm
          v-model="searchQuery"
          :config="searchConfig"
          :fold="fold"
          @search="search"
          @reset="resetSearch"
        />
      </template>

      <template #table>
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveEnhancedTable
            v-if="activeTab === 'connectors'"
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1080px]"

            stripe column-visibility border
            :status-column="connectorStatusColumn"
            :actions="connectorRowActions"
            :columns="connectorColumns"
            :data="connectorRows"
          >
            <template #cell-method="{ row }">
              <YdTag variant="secondary">
                {{ row.original.method }}
              </YdTag>
            </template>
            <template #cell-url="{ row }">
              <span class="whitespace-normal break-all">{{ row.original.url }}</span>
            </template>
            <template #card="{ row }">
              <YdCard class="w-full">
                <div class="flex flex-col gap-3">
                  <div class="flex gap-2 items-center justify-between">
                    <span class="text-base font-semibold min-w-0 break-words">{{ row.name }}</span>
                    <YdTag :variant="statusVariant(row.status)">
                      {{ statusText(row.status) }}
                    </YdTag>
                  </div>
                  <div class="text-sm flex flex-col gap-1">
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">编码</span>
                      <span>{{ row.code }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">方法</span>
                      <span>{{ row.method }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">地址</span>
                      <span class="break-all">{{ row.url }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">超时</span>
                      <span>{{ row.timeoutMillis }}ms</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">重试</span>
                      <span>{{ row.retryTimes }}</span>
                    </div>
                  </div>
                  <div class="pt-3 border-t flex flex-wrap gap-2">
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
          </YdResponsiveEnhancedTable>

          <YdResponsiveEnhancedTable
            v-else-if="activeTab === 'httpLogs'"
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1080px]"

            stripe column-visibility border
            :status-column="httpLogStatusColumn"
            :columns="httpLogColumns"
            :data="httpLogRows"
          >
            <template #cell-method="{ row }">
              <YdTag variant="secondary">
                {{ row.original.method }}
              </YdTag>
            </template>
            <template #cell-url="{ row }">
              <span class="whitespace-normal break-all">{{ row.original.url }}</span>
            </template>
            <template #cell-invokedAt="{ row }">
              {{ dateText(row.original.invokedAt) }}
            </template>
            <template #cell-errorMessage="{ row }">
              <span class="line-clamp-1" :title="row.original.errorMessage || undefined">{{ row.original.errorMessage || '-' }}</span>
            </template>
            <template #card="{ row }">
              <YdCard class="w-full">
                <div class="flex flex-col gap-3">
                  <div class="flex gap-2 items-center justify-between">
                    <span class="text-base font-semibold min-w-0 break-words">{{ row.connectorCode }}</span>
                    <YdTag :variant="executionVariant(row.status)">
                      {{ executionText(row.status) }}
                    </YdTag>
                  </div>
                  <div class="text-sm flex flex-col gap-1">
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">方法</span>
                      <span>{{ row.method }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">地址</span>
                      <span class="break-all">{{ row.url }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">响应码</span>
                      <span>{{ row.responseStatus }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">耗时</span>
                      <span>{{ row.durationMillis }}ms</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">调用时间</span>
                      <span>{{ dateText(row.invokedAt) }}</span>
                    </div>
                    <div v-if="row.errorMessage" class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">异常</span>
                      <span class="break-all">{{ row.errorMessage }}</span>
                    </div>
                  </div>
                </div>
              </YdCard>
            </template>
          </YdResponsiveEnhancedTable>

          <YdResponsiveEnhancedTable
            v-else-if="activeTab === 'scripts'"
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1080px]"

            stripe column-visibility border
            :status-column="scriptStatusColumn"
            :actions="scriptRowActions"
            :columns="scriptColumns"
            :data="scriptRows"
          >
            <template #cell-language="{ row }">
              <YdTag variant="secondary">
                {{ row.original.language }}
              </YdTag>
            </template>
            <template #cell-updateTime="{ row }">
              {{ dateText(row.original.updateTime) }}
            </template>
            <template #card="{ row }">
              <YdCard class="w-full">
                <div class="flex flex-col gap-3">
                  <div class="flex gap-2 items-center justify-between">
                    <span class="text-base font-semibold min-w-0 break-words">{{ row.name }}</span>
                    <YdTag :variant="statusVariant(row.status)">
                      {{ statusText(row.status) }}
                    </YdTag>
                  </div>
                  <div class="text-sm flex flex-col gap-1">
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">编码</span>
                      <span>{{ row.code }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">语言</span>
                      <span>{{ row.language }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">超时</span>
                      <span>{{ row.timeoutMillis }}ms</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">更新时间</span>
                      <span>{{ dateText(row.updateTime) }}</span>
                    </div>
                  </div>
                  <div class="pt-3 border-t flex flex-wrap gap-2">
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
          </YdResponsiveEnhancedTable>

          <YdResponsiveEnhancedTable
            v-else
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1080px]"

            stripe column-visibility border
            :status-column="runtimeLogStatusColumn"
            :columns="runtimeLogColumns"
            :data="runtimeLogRows"
          >
            <template #cell-language="{ row }">
              <YdTag variant="secondary">
                {{ row.original.language }}
              </YdTag>
            </template>
            <template #cell-executedAt="{ row }">
              {{ dateText(row.original.executedAt) }}
            </template>
            <template #cell-stdout="{ row }">
              <span class="line-clamp-1" :title="row.original.stdout || row.original.stderr || undefined">{{ row.original.stdout || row.original.stderr || '-' }}</span>
            </template>
            <template #cell-errorMessage="{ row }">
              <span class="line-clamp-1" :title="row.original.errorMessage || undefined">{{ row.original.errorMessage || '-' }}</span>
            </template>
            <template #card="{ row }">
              <YdCard class="w-full">
                <div class="flex flex-col gap-3">
                  <div class="flex gap-2 items-center justify-between">
                    <span class="text-base font-semibold min-w-0 break-words">{{ row.scriptCode }}</span>
                    <YdTag :variant="executionVariant(row.status)">
                      {{ executionText(row.status) }}
                    </YdTag>
                  </div>
                  <div class="text-sm flex flex-col gap-1">
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">语言</span>
                      <span>{{ row.language }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">退出码</span>
                      <span>{{ row.exitCode }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">耗时</span>
                      <span>{{ row.durationMillis }}ms</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">执行时间</span>
                      <span>{{ dateText(row.executedAt) }}</span>
                    </div>
                    <div v-if="row.stdout || row.stderr" class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">输出</span>
                      <span class="break-all">{{ row.stdout || row.stderr }}</span>
                    </div>
                    <div v-if="row.errorMessage" class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">异常</span>
                      <span class="break-all">{{ row.errorMessage }}</span>
                    </div>
                  </div>
                </div>
              </YdCard>
            </template>
          </YdResponsiveEnhancedTable>
        </YdLoadingOverlay>
      </template>

      <template #footer>
        <YdPagination
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </template>
    </YdTablePageLayout>

    <YdModal v-model="connectorVisible" :title="connectorEditing ? '编辑 HTTP 连接器' : '新增 HTTP 连接器'" show-cancel-button class="sm:max-w-4xl" @confirm="saveConnector">
      <YdForm :model="connectorForm" layout="vertical">
        <div class="gap-x-4 grid grid-cols-1 md:grid-cols-2">
          <YdFormItem label="名称" required>
            <YdInput v-model="connectorForm.name" />
          </YdFormItem>
          <YdFormItem label="编码" required>
            <YdInput v-model="connectorForm.code" :disabled="!!connectorEditing" />
            <div v-if="connectorEditing" class="field-tip">
              编码用于日志和调用关联，创建后不可修改。
            </div>
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
          <div class="section-title">
            最近结果
          </div>
          <pre>{{ lastHttpLog ? JSON.stringify(lastHttpLog, null, 2) : '尚未执行' }}</pre>
        </section>
      </div>
    </YdModal>

    <YdModal v-model="scriptVisible" :title="scriptEditing ? '编辑运行脚本' : '新增运行脚本'" show-cancel-button class="sm:max-w-4xl" @confirm="saveScript">
      <YdForm :model="scriptForm" layout="vertical">
        <div class="gap-x-4 grid grid-cols-1 md:grid-cols-2">
          <YdFormItem label="名称" required>
            <YdInput v-model="scriptForm.name" />
          </YdFormItem>
          <YdFormItem label="编码" required>
            <YdInput v-model="scriptForm.code" :disabled="!!scriptEditing" />
            <div v-if="scriptEditing" class="field-tip">
              编码用于执行记录关联，创建后不可修改。
            </div>
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
          <div class="section-title">
            最近结果
          </div>
          <pre>{{ lastRuntimeLog ? JSON.stringify(lastRuntimeLog, null, 2) : '尚未执行' }}</pre>
        </section>
      </div>
    </YdModal>
  </div>
</template>

<style scoped>
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

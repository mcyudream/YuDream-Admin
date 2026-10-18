<script setup lang="ts">
import type { ActionMenuItem, SearchOption, SearchOptionsSource, TableColumn, YdTablePageAction } from '@yudream/components'
import type { AgentTool, AiToolCatalogRow, AiToolModule, AiToolModuleCatalog, AiToolTestResult } from '@/api/modules/platform-agent'
import { useTablePage } from '@yudream/components'
import apiAgent from '@/api/modules/platform-agent'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import AgentJsonCodeEditor from './components/AgentJsonCodeEditor.vue'
import { defaultAgentToolInputSchema, defaultAgentToolOutputExample, isJsonObject } from './config/agent-json'

const toast = useYdToast()
const modal = useYdModal()
const { auth } = useAppAuth()

type TagVariant = 'default' | 'destructive' | 'outline' | 'secondary'

/* ------------------------------------------------------------ 词表与搜索选项 */

const sourceLabels: Record<string, string> = { system: '系统', plugin: '插件', mcp: 'MCP', python: 'Python' }
/** 四个来源用四个变体区分，只表达“来源不同”，不表达严重程度 */
const sourceTagVariants: Record<string, TagVariant> = {
  system: 'default',
  plugin: 'outline',
  mcp: 'secondary',
  python: 'destructive',
}

/** 来源词表：取值与后端 AiToolCatalogRowDTO#source 一致 */
const toolSourceOptions: SearchOption[] = [
  { label: '系统', value: 'system' },
  { label: '插件', value: 'plugin' },
  { label: 'MCP', value: 'mcp' },
  { label: 'Python', value: 'python' },
]

/** 状态词表：对应后端 AiToolModuleAppService#matchesStatus 的三档取值 */
const toolStatusOptions: SearchOption[] = [
  { label: '可用', value: 'ENABLED' },
  { label: '工具已停用', value: 'DISABLED' },
  { label: '模块已关闭', value: 'MODULE_CLOSED' },
]

/** 数据源标识必须与 AiToolCatalogPageQuery 上 @Searchable.optionsSource 的取值同名 */
const optionsSources: SearchOptionsSource[] = [
  { id: 'toolSourceOptions', loader: () => toolSourceOptions },
  {
    id: 'toolModuleOptions',
    loader: async () => {
      const res = await apiAgent.toolModules()
      return (res.data.modules ?? []).map(module => ({ label: `${module.name}（${module.code}）`, value: module.code }))
    },
  },
  { id: 'toolStatusOptions', loader: () => toolStatusOptions },
]

/* ------------------------------------------------------------ 统一工具目录表格 */

/** 筛选条件由 AiToolCatalogPageQuery 的 @Searchable 注解生成，分页由后端切片 */
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
} = useTablePage<AiToolCatalogRow>({
  queryClass: 'online.yudream.base.application.platform.ai.query.AiToolCatalogPageQuery',
  loadApi: async (query) => {
    const res = await apiAgent.toolCatalog({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      source: query.source || undefined,
      moduleCode: query.moduleCode || undefined,
      status: query.status || undefined,
    })
    return {
      records: res.data.records,
      total: Number(res.data.total || 0),
      page: Number(res.data.page || query.page),
      size: Number(res.data.size || query.size),
    }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
  optionsSources,
})

/** 名称（人可读标识）钉在左侧；操作列由增强表格自动固定在右侧 */
const tableColumns: TableColumn<AiToolCatalogRow>[] = [
  { id: 'name', header: '名称', width: 260, fixed: 'left' },
  { id: 'code', header: '编码', width: 190 },
  { id: 'module', header: '模块', width: 280 },
  { id: 'description', header: '描述', minWidth: 260 },
  { id: 'risk', header: '风险', width: 120 },
  { id: 'state', header: '状态', width: 190 },
  { accessorKey: 'permissionCode', header: '权限码', width: 200, defaultHidden: true },
]

/** 表格选择集：只有 Python 工具可删，批量删除只作用于这些行 */
const selectedRows = shallowRef<AiToolCatalogRow[]>([])
const removableSelection = computed(() => selectedRows.value.filter(row => row.deletable && row.toolId))

function onSelectionChange(rowsSelected: AiToolCatalogRow[]) {
  selectedRows.value = rowsSelected
}

function batchRemove() {
  const targets = removableSelection.value
  if (!targets.length) {
    toast.warning('请先勾选可删除的 Python 工具')
    return
  }
  modal.confirm({
    title: '批量删除 Python 工具',
    content: `确定删除选中的 ${targets.length} 个工具吗？已被应用使用时需要先移除节点。`,
    onConfirm: async () => {
      let removed = 0
      for (const row of targets) {
        try {
          await apiAgent.deleteTool(row.toolId as string)
          removed++
        }
        catch { /* 单个失败不阻塞其余删除，最后统一提示 */ }
      }
      toast.success(`已删除 ${removed} 个工具`)
      await load()
    },
  })
}

/**
 * 页头操作：交给 YdTablePageLayout 的 actions 属性渲染（保留自动折叠），
 * 系统工具总开关是页面级口径提示，放在表格上方的说明条里，不占操作位。
 */
const pageActions = computed<YdTablePageAction[]>(() => [
  ...(auth('platform:agent:tool:delete')
    ? [{
        label: removableSelection.value.length ? `删除所选（${removableSelection.value.length}）` : '删除所选',
        icon: 'morph:trash-2',
        variant: 'destructive' as const,
        danger: true,
        autoFold: true,
        disabled: !removableSelection.value.length,
        onClick: batchRemove,
      }]
    : []),
  ...(auth('platform:agent:tool:edit')
    ? [{ label: '新建 Python 工具', icon: 'morph:plus', onClick: createTool }]
    : []),
])

function sourceText(source?: string) {
  return source ? sourceLabels[source] ?? source : '系统'
}

function sourceTagVariant(source?: string): TagVariant {
  return sourceTagVariants[source ?? 'system'] ?? 'outline'
}

/** 风险标签：只读弱化；写入按是否需人工确认分两档语气 */
function riskText(row: AiToolCatalogRow) {
  if (row.risk === 'READ') {
    return '只读'
  }
  if (row.risk === 'WRITE') {
    return row.requiresConfirmation ? '写入 · 需确认' : '写入'
  }
  return '未标注'
}

function riskVariant(row: AiToolCatalogRow): TagVariant {
  if (row.risk === 'READ') {
    return 'secondary'
  }
  if (row.risk === 'WRITE') {
    return row.requiresConfirmation ? 'destructive' : 'outline'
  }
  return 'outline'
}

/** 行状态：模块关闭优先于工具自身停用，与后端 available() 的判定顺序一致 */
function toolState(row: AiToolCatalogRow) {
  if (row.moduleEnabled === false) {
    return 'MODULE_CLOSED'
  }
  return row.enabled === false ? 'DISABLED' : 'ENABLED'
}

/** 测试的阻塞原因：后端测试入口同样复核模块开关与工具启用状态，行内直接给出禁用理由 */
function toolTestBlockedReason(row: AiToolCatalogRow) {
  if (row.moduleEnabled === false) {
    return '模块已关闭'
  }
  if (row.enabled === false) {
    return '工具已停用'
  }
  return ''
}

/** 行操作统一收进表格内置操作列（窄屏卡片复用同一组动作） */
const rowActions: ActionMenuItem<AiToolCatalogRow>[] = [
  {
    label: row => (toolTestBlockedReason(row) ? `测试（${toolTestBlockedReason(row)}）` : '测试'),
    icon: 'morph:flask-conical',
    visible: row => row.testable && auth('platform:agent:tool:test'),
    disabled: row => Boolean(toolTestBlockedReason(row)),
    onClick: openTestModal,
  },
  {
    label: '编辑',
    icon: 'morph:pencil',
    visible: row => row.editable && auth('platform:agent:tool:edit'),
    onClick: (row) => { void editTool(row) },
  },
  {
    label: '删除',
    icon: 'morph:trash-2',
    danger: true,
    visible: row => row.deletable && auth('platform:agent:tool:delete'),
    onClick: removeTool,
  },
]

/* ------------------------------------------------------------ 工具测试 */

const testVisible = ref(false)
const testRow = ref<AiToolCatalogRow>()
const testArgumentsText = ref('{}')
/** 编辑器的「恢复默认」目标：本次打开时生成的参数骨架 */
const testArgumentsDefault = ref('{}')
const testRunning = ref(false)
const testResult = ref<AiToolTestResult>()

const testPayloadText = computed(() => testResult.value?.payload ? JSON.stringify(testResult.value.payload, null, 2) : '')
/** 工具声明的必填参数：骨架里已保留，再在编辑器下方点名提醒 */
const testRequiredNames = computed(() => requiredArgumentNames(testRow.value?.inputSchema))

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

/** 参数占位值：按 JSON Schema 类型给骨架，声明了 default/enum 时优先用它们 */
function placeholderOf(schema: Record<string, unknown>): unknown {
  if (schema.default !== undefined) {
    return schema.default
  }
  if (Array.isArray(schema.enum) && schema.enum.length) {
    return schema.enum[0]
  }
  const declaredType = Array.isArray(schema.type) ? schema.type[0] : schema.type
  const type = typeof declaredType === 'string' ? declaredType : ''
  switch (type) {
    case 'integer':
    case 'number':
      return 0
    case 'boolean':
      return false
    case 'array':
      return []
    case 'object':
      return {}
    default:
      return ''
  }
}

/**
 * 由工具 inputSchema 生成调用参数骨架：遍历 properties 逐项给类型占位值。
 * 平台工具有的用简写 schema（`{ name: "描述" }`，值直接是字符串而不是类型对象），这类按字符串处理。
 */
function buildArgumentSkeleton(schema?: Record<string, unknown>) {
  const declaredProperties = schema?.properties
  const properties = isRecord(declaredProperties) ? declaredProperties : {}
  const skeleton: Record<string, unknown> = {}
  for (const [name, propertySchema] of Object.entries(properties)) {
    skeleton[name] = placeholderOf(isRecord(propertySchema) ? propertySchema : {})
  }
  return skeleton
}

function requiredArgumentNames(schema?: Record<string, unknown>) {
  const required = schema?.required
  if (!Array.isArray(required)) {
    return []
  }
  return required.filter((name): name is string => typeof name === 'string')
}

function openTestModal(row: AiToolCatalogRow) {
  testRow.value = row
  testArgumentsDefault.value = JSON.stringify(buildArgumentSkeleton(row.inputSchema), null, 2)
  testArgumentsText.value = testArgumentsDefault.value
  testResult.value = undefined
  testVisible.value = true
}

/** 发送前校验：必须是合法 JSON object，失败就地提示，不打断弹窗 */
function parseTestArguments() {
  let parsed: unknown
  try {
    parsed = JSON.parse(testArgumentsText.value)
  }
  catch {
    toast.error('调用参数不是合法 JSON，请先修正后再运行')
    return undefined
  }
  if (!isRecord(parsed)) {
    toast.error('调用参数必须是 JSON object')
    return undefined
  }
  return parsed
}

function runTest() {
  const row = testRow.value
  if (!row || testRunning.value) {
    return
  }
  const args = parseTestArguments()
  if (!args) {
    return
  }
  if (row.risk !== 'WRITE') {
    void executeTest(row, args)
    return
  }
  // 写入类工具会产生真实副作用，运行前必须让用户确认
  modal.confirm({
    title: '确认运行写入类工具',
    content: `「${row.name}」标记为写入类工具，测试会以当前账号权限真实调用一次并产生真实副作用；确认继续运行？`,
    onConfirm: () => executeTest(row, args),
  })
}

async function executeTest(row: AiToolCatalogRow, args: Record<string, unknown>) {
  testRunning.value = true
  // 清掉上一轮结果，避免旧 payload 被误读成本次调用返回
  testResult.value = undefined
  try {
    const res = await apiAgent.testTool({ code: row.code, arguments: args })
    testResult.value = res.data
    if (res.data.success) {
      toast.success(`「${row.name}」调用完成`)
    }
    else {
      toast.error(`「${row.name}」调用未成功`)
    }
  }
  catch {
    toast.error('工具测试请求失败，请稍后重试或查看后端日志')
  }
  finally { testRunning.value = false }
}

/* ------------------------------------------------------------ 工具模块开关（页头总开关 + 表格行内模块开关） */

const catalog = ref<AiToolModuleCatalog>()
const catalogLoading = ref(false)
/** 正在保存的开关：'system' 或模块编码，非空期间禁用全部模块开关，避免并发覆盖 */
const busyKey = ref('')

const modules = computed(() => catalog.value?.modules ?? [])
const systemEnabled = computed(() => catalog.value?.systemEnabled === true)
const configEnabled = computed(() => catalog.value?.configEnabled !== false)
const switchBusy = computed(() => busyKey.value !== '')

/** 模块目录按编码索引：行内开关的权威状态、加载方式与工具数都取自目录，目录未覆盖时退回行数据 */
const moduleCatalog = computed(() => new Map<string, AiToolModule>(modules.value.map(module => [module.code, module])))

/** 总开关旁的状态说明：禁用原因优先于开关值，避免出现「点不动的开关」没有解释 */
const masterSwitchState = computed(() => {
  if (catalogLoading.value) {
    return '加载中…'
  }
  if (!configEnabled.value) {
    return '部署配置已关闭'
  }
  return systemEnabled.value ? '已启用' : '已停用'
})
/** 总开关与模块开关同受配置项闸门；目录未就绪与保存期间一并禁用，避免把「未知」当成「已停用」 */
const masterSwitchDisabled = computed(() => switchBusy.value || catalogLoading.value || !configEnabled.value)

/** 表格上方的口径说明：按需加载是默认模式，常驻（eager）部署换一种说法 */
const moduleLoadingHint = computed(() => catalog.value?.onDemand === false
  ? '系统工具按模块提供；当前部署为常驻加载（eager），所有模块的工具都直接进入模型视野，无需模型调用 load_tools 加载。'
  : '系统工具按模块提供；模型默认只能看到常驻模块的工具，其余模块需要模型调用 load_tools 按需加载（加载只影响可见性，不授予权限）。')

/** 目录在挂载时取一次：总开关与行内模块开关的闸门与状态都依赖它 */
onMounted(() => {
  void loadCatalog()
})

async function loadCatalog() {
  catalogLoading.value = true
  try {
    const res = await apiAgent.toolModules()
    catalog.value = res.data
  }
  catch { toast.error('工具模块加载失败，请重试') }
  finally { catalogLoading.value = false }
}

/** 总开关保存后采纳返回的最新目录，并刷新表格，让每个模块的「模块已关闭」立刻跟上 */
async function toggleSystem(value?: boolean) {
  const enabled = value === true
  if (busyKey.value || catalogLoading.value || !configEnabled.value) {
    return
  }
  busyKey.value = 'system'
  try {
    const res = await apiAgent.updateSystemToolsEnabled(enabled)
    catalog.value = res.data
    toast.success(enabled ? '系统工具总开关已开启' : '系统工具总开关已关闭')
    await load()
  }
  catch { toast.error('系统工具开关保存失败，请重试') }
  finally { busyKey.value = '' }
}

/** 行内模块开关作用于整个模块：同一模块的每一行都显示它，任意一行都能切换整个模块 */
async function toggleModule(row: AiToolCatalogRow, value?: boolean) {
  const module = moduleCatalog.value.get(row.moduleCode)
  if (busyKey.value || !row.moduleCode || moduleBlockReason(row)) {
    return
  }
  busyKey.value = module?.code ?? row.moduleCode
  try {
    const res = await apiAgent.updateToolModuleEnabled(row.moduleCode, value === true)
    catalog.value = res.data
    toast.success(value === true ? `已启用「${moduleNameOf(row)}」模块` : `已停用「${moduleNameOf(row)}」模块`)
    await load()
  }
  catch { toast.error(`「${moduleNameOf(row)}」模块开关保存失败，请重试`) }
  finally { busyKey.value = '' }
}

function moduleNameOf(row: AiToolCatalogRow) {
  return moduleCatalog.value.get(row.moduleCode)?.name ?? row.moduleName
}

/**
 * 三类闸门各自给出禁用原因，空串表示可以切换。
 * 目录未返回前不判定闸门：此时行数据也已带出模块状态，免得把「尚未加载」显示成「已停用」。
 */
function moduleBlockReason(row: AiToolCatalogRow) {
  if (moduleCatalog.value.get(row.moduleCode)?.canDisable === false) {
    return '不可关闭'
  }
  if (!catalog.value) {
    return ''
  }
  if (!configEnabled.value) {
    return '部署配置已关闭'
  }
  if (!systemEnabled.value) {
    return '随总开关停用'
  }
  return ''
}

function moduleSwitchDisabled(row: AiToolCatalogRow) {
  return switchBusy.value || Boolean(moduleBlockReason(row))
}

/** 行内开关的值：目录是权威来源（同一模块的每行因此始终一致），目录未覆盖时退回行数据 */
function moduleSwitchValue(row: AiToolCatalogRow) {
  return moduleCatalog.value.get(row.moduleCode)?.enabled ?? row.moduleEnabled
}

/** 开关说明粒度与可见性：改的是整个模块，而不是当前这一个工具 */
function moduleSwitchHint(row: AiToolCatalogRow) {
  const module = moduleCatalog.value.get(row.moduleCode)
  const visibility = module?.lazy === false || row.moduleAlwaysOn
    ? '常驻模块，工具始终对模型可见'
    : '按需加载：模型需先调用 load_tools 加载'
  if (!module) {
    return `作用于整个「${moduleNameOf(row)}」模块；${visibility}`
  }
  return `作用于整个「${moduleNameOf(row)}」模块（${Number(module.toolCount ?? module.tools?.length ?? 0)} 个工具）；${visibility}`
}

/* ------------------------------------------------------------ Python 工具启用开关 */

/** 正在保存启用的 Python 工具主键：只锁该行，其余行可以继续操作 */
const toolBusyKey = ref('')

/** 只有 Python 自定义工具能逐条启停（后端也只接受 PYTHON 类型），系统/插件/MCP 工具跟随模块与服务 */
function toolSwitchVisible(row: AiToolCatalogRow) {
  return row.source === 'python' && row.editable === true
}

/** Python 工具的启用开关在总开关关闭与缺主键时不可写，各自给出原因 */
function toolSwitchBlockReason(row: AiToolCatalogRow) {
  if (!row.toolId) {
    return '缺少工具主键'
  }
  if (catalog.value && !systemEnabled.value) {
    return '随总开关停用'
  }
  return ''
}

function toolSwitchDisabled(row: AiToolCatalogRow) {
  return toolBusyKey.value === row.toolId || Boolean(toolSwitchBlockReason(row))
}

/** 单工具启停走专用端点，成功后刷新表格，让状态标签与开关值一起对齐 */
async function toggleTool(row: AiToolCatalogRow, value?: boolean) {
  const toolId = row.toolId
  if (!toolId || toolBusyKey.value || toolSwitchBlockReason(row)) {
    return
  }
  toolBusyKey.value = toolId
  const enabled = value === true
  try {
    await apiAgent.toggleTool(toolId, enabled)
    toast.success(enabled ? `已启用「${row.name}」` : `已停用「${row.name}」`)
    await load()
  }
  catch { toast.error(`「${row.name}」启用状态保存失败，请重试`) }
  finally { toolBusyKey.value = '' }
}

/* ------------------------------------------------------------ Python 工具 */

const visible = ref(false)
const editing = ref<AgentTool>()
const defaultPythonCode = 'def run(params: dict) -> dict:\n    name = str(params.get("name", ""))\n    return {\n        "success": True,\n        "message": f"Hello, {name}",\n    }'
const form = reactive({ name: '', code: '', description: '', inputSchemaJson: defaultAgentToolInputSchema, outputExampleJson: defaultAgentToolOutputExample, pythonCode: defaultPythonCode, timeoutMillis: 10000, permissionCode: '', enabled: true })

/** 工具编码规则与后端 AgentTool 一致：^[a-z][a-z0-9_-]{1,63}$ */
const toolCodeHint = '小写字母、数字、下划线与连字符，以字母开头，共 2-64 位'

function resetForm() {
  Object.assign(form, { name: '', code: '', description: '', inputSchemaJson: defaultAgentToolInputSchema, outputExampleJson: defaultAgentToolOutputExample, pythonCode: defaultPythonCode, timeoutMillis: 10000, permissionCode: '', enabled: true })
}

function createTool() {
  editing.value = undefined
  resetForm()
  visible.value = true
}

/** 目录行只带汇总信息：编辑前按主键取回完整 Python 工具配置 */
async function editTool(row: AiToolCatalogRow) {
  if (!row.toolId) {
    toast.error(`未找到「${row.code}」的工具主键，请刷新后重试`)
    return
  }
  try {
    const res = await apiAgent.pageTools({ page: 1, size: 10, keyword: row.code })
    const tool = res.data.records.find(item => item.id === row.toolId)
    if (!tool) {
      toast.error(`未找到「${row.code}」的完整配置，请刷新后重试`)
      return
    }
    editing.value = tool
    Object.assign(form, tool, {
      inputSchemaJson: tool.inputSchemaJson || defaultAgentToolInputSchema,
      outputExampleJson: tool.outputExampleJson || defaultAgentToolOutputExample,
    })
    visible.value = true
  }
  catch { toast.error('工具详情加载失败，请重试') }
}

async function save() {
  if (!form.name.trim() || !form.code.trim() || !form.outputExampleJson.trim() || !form.pythonCode.trim()) {
    toast.error('请填写工具名称、编码、输出格式示例和 Python 代码')
    return
  }
  if (!isJsonObject(form.inputSchemaJson) || !isJsonObject(form.outputExampleJson)) {
    toast.error('参数 Schema 和输出格式示例必须是 JSON object')
    return
  }
  try {
    const payload = { ...form }
    if (editing.value) {
      await apiAgent.updateTool(editing.value.id, payload)
    }
    else { await apiAgent.createTool(payload) }
    toast.success('工具已保存')
    visible.value = false
    await load()
  }
  catch { toast.error('工具保存失败，请检查工具配置') }
}

function removeTool(row: AiToolCatalogRow) {
  modal.confirm({
    title: '删除 Python 工具',
    content: `确定删除“${row.name}”吗？已被应用使用时需要先移除节点。`,
    onConfirm: async () => {
      if (!row.toolId) {
        toast.error(`未找到「${row.code}」的工具主键，请刷新后重试`)
        return
      }
      try {
        await apiAgent.deleteTool(row.toolId)
        toast.success('已删除')
        await load()
      }
      catch { toast.error('删除失败，请重试') }
    },
  })
}
</script>

<template>
  <div>
    <YdTablePageLayout title="系统工具" :actions="pageActions" :actions-threshold="3">
      <template #search="{ fold }">
        <YdDynamicSearchForm
          v-model="searchQuery"
          :config="searchConfig"
          :fold="fold"
          :options-sources="optionsSources"
          @search="search"
          @reset="resetSearch"
        />
      </template>

      <template #table>
        <div class="tools-table">
          <!-- 口径说明 + 总开关：加载只影响可见性不授予权限；常驻部署另给一种说法 -->
          <div class="tools-table__note">
            <span class="tools-table__note-text">
              <YdIcon name="morph:info" class="tools-table__note-icon" />
              <span>{{ moduleLoadingHint }}</span>
            </span>
            <span class="tools-master">
              <span class="tools-master__label">系统工具总开关</span>
              <span v-auth="'platform:agent:tool:edit'">
                <YdSwitch
                  :model-value="systemEnabled"
                  :disabled="masterSwitchDisabled"
                  aria-label="系统工具总开关"
                  @update:model-value="toggleSystem"
                />
              </span>
              <span class="tools-master__state">{{ masterSwitchState }}</span>
            </span>
          </div>
          <YdLoadingOverlay :loading="loading" class="tools-table__body">
            <YdResponsiveEnhancedTable
              row-key="code"
              table-root-class="rounded-lg overflow-hidden"
              table-class="min-w-[1300px]"

              column-visibility stripe selectable multiple
              :actions="rowActions"
              :card-hidden-columns="['description']"
              :columns="tableColumns"
              :data="rows"
              @selection-change="onSelectionChange"
            >
              <!-- 名称身份列：名称 + 来源标签同格，横向滚动时始终可见 -->
              <template #cell-name="{ row }">
                <div class="tool-name">
                  <span class="tool-name__text">{{ row.original.name }}</span>
                  <YdTag :variant="sourceTagVariant(row.original.source)">
                    {{ sourceText(row.original.source) }}
                  </YdTag>
                </div>
              </template>
              <template #cell-code="{ row }">
                <code class="tool-code">{{ row.original.code }}</code>
              </template>
              <!-- 模块：名称 + 编码 + 状态标签；开关作用于整个模块，同模块的每一行都能切换 -->
              <template #cell-module="{ row }">
                <div class="tool-module">
                  <span class="tool-module__name">{{ row.original.moduleName }}</span>
                  <code v-if="row.original.moduleCode" class="tool-code">{{ row.original.moduleCode }}</code>
                  <YdTag v-if="row.original.moduleEnabled === false" variant="secondary">
                    模块已关闭
                  </YdTag>
                  <YdTag v-else-if="row.original.moduleAlwaysOn === false" variant="secondary">
                    按需加载
                  </YdTag>
                  <span v-auth="'platform:agent:tool:edit'" class="module-switch">
                    <YdTooltip :text="moduleSwitchHint(row.original)">
                      <!-- 触发元素用普通 span：开关禁用时也能悬浮看到粒度说明 -->
                      <span class="module-switch__control">
                        <YdSwitch
                          :model-value="moduleSwitchValue(row.original)"
                          :disabled="moduleSwitchDisabled(row.original)"
                          :aria-label="`${row.original.moduleName}模块开关`"
                          @update:model-value="(value?: boolean) => toggleModule(row.original, value)"
                        />
                      </span>
                    </YdTooltip>
                    <span v-if="moduleBlockReason(row.original)" class="module-switch__state">
                      {{ moduleBlockReason(row.original) }}
                    </span>
                  </span>
                </div>
              </template>
              <template #cell-description="{ row }">
                <span v-if="row.original.description" class="tool-desc" :title="row.original.description">{{ row.original.description }}</span>
                <span v-else class="cell-muted">-</span>
              </template>
              <template #cell-risk="{ row }">
                <YdTag :variant="riskVariant(row.original)">
                  {{ riskText(row.original) }}
                </YdTag>
              </template>
              <!-- 状态：Python 工具行内启停；系统/插件工具跟随模块开关，MCP 工具的启用在 MCP 服务页管理 -->
              <template #cell-state="{ row }">
                <span v-if="toolSwitchVisible(row.original)" class="tool-switch">
                  <span v-auth="'platform:agent:tool:edit'" class="tool-switch__control">
                    <YdSwitch
                      :model-value="row.original.enabled === true"
                      :disabled="toolSwitchDisabled(row.original)"
                      :aria-label="`${row.original.name}启用开关`"
                      @update:model-value="(value?: boolean) => toggleTool(row.original, value)"
                    />
                    <span v-if="toolBusyKey === row.original.toolId" class="tool-switch__state">保存中…</span>
                    <span v-else-if="toolSwitchBlockReason(row.original)" class="tool-switch__state">
                      {{ toolSwitchBlockReason(row.original) }}
                    </span>
                  </span>
                  <!-- 只读账号不给出改不了的开关，退回状态标签 -->
                  <YdTag v-if="!auth('platform:agent:tool:edit')" :variant="row.original.enabled ? 'default' : 'secondary'">
                    {{ row.original.enabled ? '可用' : '已停用' }}
                  </YdTag>
                </span>
                <YdTag v-else-if="toolState(row.original) === 'ENABLED'" variant="default">
                  可用
                </YdTag>
                <YdTooltip
                  v-else-if="toolState(row.original) === 'MODULE_CLOSED'"
                  text="模块开关已关闭，该工具不会提供给模型；可在「模块」列直接启用该模块。"
                >
                  <YdTag variant="secondary">
                    模块已关闭
                  </YdTag>
                </YdTooltip>
                <YdTag v-else variant="secondary">
                  已停用
                </YdTag>
              </template>
              <template #cell-permissionCode="{ row }">
                <code v-if="row.original.permissionCode" class="tool-code">{{ row.original.permissionCode }}</code>
                <span v-else class="cell-muted">无需权限码</span>
              </template>
            </YdResponsiveEnhancedTable>
          </YdLoadingOverlay>
        </div>
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

    <!-- 工具测试：预填参数骨架，运行后保留结果以便调整参数重跑 -->
    <YdModal v-model="testVisible" title="工具测试" class="sm:max-w-3xl">
      <div v-if="testRow" class="test-panel">
        <dl class="test-facts">
          <div class="test-facts__item">
            <dt>名称</dt>
            <dd>{{ testRow.name }}</dd>
          </div>
          <div class="test-facts__item">
            <dt>编码</dt>
            <dd><code class="tool-code">{{ testRow.code }}</code></dd>
          </div>
          <div class="test-facts__item">
            <dt>来源</dt>
            <dd>
              <YdTag :variant="sourceTagVariant(testRow.source)">
                {{ sourceText(testRow.source) }}
              </YdTag>
            </dd>
          </div>
          <div class="test-facts__item">
            <dt>模块</dt>
            <dd>{{ testRow.moduleName }} <code class="tool-code">{{ testRow.moduleCode }}</code></dd>
          </div>
          <div class="test-facts__item">
            <dt>风险</dt>
            <dd>
              <YdTag :variant="riskVariant(testRow)">
                {{ riskText(testRow) }}
              </YdTag>
            </dd>
          </div>
        </dl>

        <p v-if="testRow.risk === 'WRITE'" class="test-warning">
          <YdIcon name="morph:triangle-alert" class="test-warning__icon" />
          <span>该工具标记为写入类，运行测试会以当前账号权限真实调用一次并产生真实副作用；仅在明确需要时运行。</span>
        </p>

        <div class="test-field">
          <span class="test-field__label">调用参数</span>
          <AgentJsonCodeEditor v-model="testArgumentsText" :default-value="testArgumentsDefault" />
          <small class="test-field__hint">
            参数按工具输入 Schema 预填骨架{{ testRequiredNames.length ? `，必填参数：${testRequiredNames.join('、')}` : '' }}；可直接修改后运行
          </small>
        </div>

        <div v-if="testResult" class="test-result">
          <div class="test-result__head">
            <YdTag :variant="testResult.success ? 'default' : 'destructive'">
              {{ testResult.success ? '调用成功' : '调用失败' }}
            </YdTag>
            <span class="test-result__meta">{{ Number(testResult.durationMillis || 0) }} ms</span>
            <YdCopyButton v-if="testPayloadText" class="test-result__copy" :text="testPayloadText" label="复制返回" />
          </div>
          <p class="test-result__message">
            {{ testResult.message }}
          </p>
          <YdAlert
            v-if="!testResult.success"
            icon="morph:triangle-alert"
            variant="destructive"
            title="工具调用未成功"
            :description="testResult.message || '后端未返回失败原因，请查看运行日志。'"
          />
          <div v-else class="test-result__payload">
            <span class="test-field__label">返回结果</span>
            <YdCodeEditor
              :model-value="testPayloadText || '{}'"
              language="json"
              readonly
              height="220px"
              aria-label="工具调用返回结果"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <YdButton variant="outline" @click="testVisible = false">
          关闭
        </YdButton>
        <YdButton v-auth="'platform:agent:tool:test'" :loading="testRunning" :disabled="!testRow" @click="runTest">
          <YdIcon v-if="!testRunning" name="morph:play" /> 运行测试
        </YdButton>
      </template>
    </YdModal>

    <!-- Python 工具：字段、校验与接口沿用原有实现 -->
    <YdModal v-model="visible" :title="editing ? '编辑 Python 工具' : '新建 Python 工具'" class="sm:max-w-4xl">
      <div class="tool-form">
        <div class="gap-4 grid sm:grid-cols-2">
          <label class="form-field required"><span>工具名称</span><YdInput v-model="form.name" class="w-full" maxlength="60" placeholder="例如：订单风险查询" /></label>
          <label class="form-field required">
            <span>工具编码</span>
            <YdInput v-model="form.code" class="w-full" :disabled="Boolean(editing)" maxlength="60" placeholder="例如：risk_score" />
            <small>{{ toolCodeHint }}</small>
          </label>
        </div>
        <label class="form-field"><span>工具描述</span><YdTextarea v-model="form.description" class="w-full" :autosize="{ minRows: 3, maxRows: 5 }" placeholder="说明工具用途，模型会根据这段描述决定何时调用" /></label>
        <div class="gap-4 grid sm:grid-cols-2">
          <label class="form-field required"><span>超时时间（毫秒）</span><YdInput v-model.number="form.timeoutMillis" class="w-full" type="number" min="100" placeholder="10000" /></label>
          <label class="form-field"><span>权限编码</span><YdInput v-model="form.permissionCode" class="w-full" placeholder="可选，例如：order:risk:query" /></label>
        </div>
        <YdCheckbox v-model="form.enabled" class="check-line">
          <span><b>启用工具</b><small>停用后 Agent 无法再调用此工具</small></span>
        </YdCheckbox>
        <div class="form-field required">
          <span>输入 JSON Schema</span>
          <AgentJsonCodeEditor v-model="form.inputSchemaJson" :default-value="defaultAgentToolInputSchema" />
          <small>使用 JSON Schema object 描述参数，供模型生成合法调用参数</small>
        </div>
        <div class="form-field required">
          <span>输出格式示例</span>
          <AgentJsonCodeEditor v-model="form.outputExampleJson" :default-value="defaultAgentToolOutputExample" />
          <small>必须是 JSON object 示例，用于说明返回字段、类型和业务含义</small>
        </div>
        <label class="form-field required">
          <span>Python 代码</span>
          <YdTextarea v-model="form.pythonCode" class="w-full" input-class="font-mono" :autosize="{ minRows: 13, maxRows: 22 }" placeholder="def run(params: dict) -> dict: ..." />
          <small>必须定义 run(params: dict) -&gt; dict；不要读取 stdin、写 stdout 或返回字符串</small>
        </label>
      </div>
      <template #footer>
        <YdButton variant="outline" @click="visible = false">
          取消
        </YdButton><YdButton @click="save">
          保存工具
        </YdButton>
      </template>
    </YdModal>
  </div>
</template>

<style scoped>
/* 单元格：长文本单行截断，完整值靠 title 悬浮查看；编码统一等宽样式。 */
.tool-name { display: flex; gap: 8px; align-items: center; min-width: 0; }
.tool-name__text { min-width: 0; overflow: hidden; color: var(--color-text-1); font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.tool-code { padding: 0 4px; border-radius: 4px; background: var(--color-fill-2); color: var(--color-text-2); font-family: ui-monospace, monospace; font-size: 11px; }
.tool-module { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; min-width: 0; }
.tool-module__name { min-width: 0; overflow: hidden; color: var(--color-text-2); text-overflow: ellipsis; white-space: nowrap; }
.tool-desc { display: block; overflow: hidden; color: var(--color-text-3); text-overflow: ellipsis; white-space: nowrap; }
.cell-muted { color: var(--color-text-3); }

/* 行内开关：开关 + 状态/禁用原因同格，窄屏允许换行而不是撑破列。 */
.module-switch,
.tool-switch { display: inline-flex; flex-wrap: wrap; gap: 6px; align-items: center; min-width: 0; }
.module-switch__control,
.tool-switch__control { display: inline-flex; gap: 6px; align-items: center; }
.module-switch__state,
.tool-switch__state { color: var(--color-text-3); font-size: 11px; white-space: nowrap; }

/* 页头总开关：与操作按钮同排，标签 + 开关 + 状态/禁用原因。 */
.tools-master { display: inline-flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.tools-master__label { color: var(--color-text-2); font-size: 13px; white-space: nowrap; }
.tools-master__state { color: var(--color-text-3); font-size: 11px; white-space: nowrap; }

/* 表格区：口径说明压在表格上方，表格本体占满剩余高度（高度链与版式保持一致）。 */
.tools-table { display: flex; flex-direction: column; gap: 8px; height: 100%; min-height: 0; }
.tools-table__note { display: flex; flex: none; flex-wrap: wrap; gap: 6px 16px; align-items: flex-start; justify-content: space-between; margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.5; }
.tools-table__note-text { display: flex; gap: 6px; align-items: flex-start; min-width: 0; }
.tools-table__note-icon { flex: none; margin-top: 2px; }
.tools-table__body { flex: 1; min-height: 0; }

/* 测试弹窗：事实块 + 参数编辑 + 结果区，竖直排布。 */
.test-panel { display: grid; gap: 16px; }
.test-facts { display: grid; gap: 8px 16px; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); margin: 0; padding: 12px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-fill-1); }
.test-facts__item { display: flex; gap: 8px; align-items: baseline; min-width: 0; }
.test-facts dt { flex: none; color: var(--color-text-3); font-size: 12px; }
.test-facts dd { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; min-width: 0; margin: 0; color: var(--color-text-1); font-size: 12px; word-break: break-all; }
.test-warning { display: flex; gap: 6px; align-items: flex-start; margin: 0; padding: 8px 10px; border: 1px solid var(--color-border-2); border-radius: 6px; color: var(--color-text-2); font-size: 12px; line-height: 1.6; }
.test-warning__icon { flex: none; margin-top: 2px; color: rgb(var(--danger-6)); }
.test-field { display: grid; gap: 7px; }
.test-field__label { color: var(--color-text-2); font-size: 12px; }
.test-field__hint { color: var(--color-text-3); font-size: 10px; line-height: 1.5; }
.test-result { display: grid; gap: 10px; padding-top: 12px; border-top: 1px solid var(--color-border-2); }
.test-result__head { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.test-result__meta { color: var(--color-text-3); font-size: 12px; font-variant-numeric: tabular-nums; }
.test-result__copy { margin-left: auto; }
.test-result__message { margin: 0; color: var(--color-text-2); font-size: 12px; line-height: 1.6; }
.test-result__payload { display: grid; gap: 7px; }

/* Python 工具表单：与原有实现一致。 */
.tool-form { display: grid; gap: 16px; }
.form-field { display: grid; gap: 7px; color: var(--color-text-2); font-size: 12px; }
.form-field.required > span::after { margin-left: 3px; color: rgb(var(--danger-6)); content: '*'; }
.form-field small { color: var(--color-text-3); font-size: 10px; line-height: 1.5; }
.check-line { display: grid; grid-template-columns: 20px minmax(0, 1fr); align-items: start; gap: 8px; padding: 10px; border: 1px solid var(--color-border-2); border-radius: 6px; font-size: 12px; cursor: pointer; }
.check-line span { display: grid; gap: 2px; }
.check-line b { color: var(--color-text-1); font-weight: 500; }
.check-line small { color: var(--color-text-3); font-size: 10px; }

@media (max-width: 640px) {
  /* 窄屏下总开关独占一行靠左，操作按钮仍靠右，避免开关被按钮挤到换行中间 */
  .tools-master { width: 100%; justify-content: flex-start; }
}
</style>

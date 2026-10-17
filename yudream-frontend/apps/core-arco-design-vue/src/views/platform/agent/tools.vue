<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { AgentTool, AiToolModule, AiToolModuleCatalog } from '@/api/modules/platform-agent'
import { useMediaQuery } from '@vueuse/core'
import { computed, shallowRef } from 'vue'
import apiAgent from '@/api/modules/platform-agent'
import AgentJsonCodeEditor from './components/AgentJsonCodeEditor.vue'
import { defaultAgentToolInputSchema, defaultAgentToolOutputExample, isJsonObject } from './config/agent-json'

const toast = useYdToast()
const modal = useYdModal()

/* ------------------------------------------------------------ 系统工具：模块目录 */

/** 宿主模块的策展图标：后端图标名取自 lucide，缺位名称会让行首渲染成空白。 */
const moduleIcons: Record<string, string> = {
  meta: 'morph:list',
  web: 'morph:globe',
  wiki: 'morph:book',
  ui: 'morph:mouse-pointer',
  cms: 'morph:layout-dashboard',
  general: 'morph:puzzle',
  custom: 'morph:square-code',
}
/** 后端仍在下发、但离线图标集合里缺位的两个名称，换成等价图标。 */
const moduleIconAliases: Record<string, string> = {
  'morph:cursor': 'morph:mouse-pointer',
  'morph:layout': 'morph:layout-dashboard',
}
const sourceLabels: Record<string, string> = { system: '系统', plugin: '插件', mcp: 'MCP', python: 'Python' }

const catalog = ref<AiToolModuleCatalog>()
const catalogLoading = ref(false)
const moduleKeyword = ref('')
/** 模块展开状态：键为模块编码；搜索命中时自动展开，用户仍可手动收起。 */
const openedModules = ref<Record<string, boolean>>({})
/** 正在保存的开关：'system' 或模块编码，非空期间禁用全部开关，避免并发覆盖。 */
const busyKey = ref('')

const modules = computed(() => catalog.value?.modules ?? [])
const systemEnabled = computed(() => catalog.value?.systemEnabled === true)
const configEnabled = computed(() => catalog.value?.configEnabled !== false)
const switchBusy = computed(() => busyKey.value !== '')
const enabledModuleCount = computed(() => modules.value.filter(module => module.enabled).length)
const moduleSummary = computed(() => systemEnabled.value
  ? `${enabledModuleCount.value} / ${modules.value.length} 个模块启用`
  : `总开关已关闭 · ${modules.value.length} 个模块`)
const moduleLoadingHint = computed(() => catalog.value?.onDemand === false
  ? '当前部署为常驻加载模式（eager）：所有模块的工具都直接进入模型视野，无需 load_tools 加载。'
  : '系统工具按模块提供；模型默认只能看到常驻模块的工具，其余模块需要模型调用 load_tools 按需加载（加载只影响可见性，不授予权限）。')

/** 关键词同时过滤模块与工具：模块命中保留全部工具，仅工具命中时只保留命中的工具。 */
const filteredModules = computed(() => {
  const query = moduleKeyword.value.trim().toLocaleLowerCase()
  if (!query) {
    return modules.value
  }
  return modules.value.flatMap((module) => {
    const moduleHit = [module.name, module.code, module.description, sourceText(module.source)]
      .some(value => value?.toLocaleLowerCase().includes(query))
    if (moduleHit) {
      return [module]
    }
    const tools = (module.tools ?? []).filter(tool => [tool.name, tool.code, tool.description, tool.permissionCode]
      .some(value => value?.toLocaleLowerCase().includes(query)))
    return tools.length ? [{ ...module, tools }] : []
  })
})

watch(filteredModules, (list) => {
  if (!moduleKeyword.value.trim()) {
    return
  }
  for (const module of list) {
    if (module.tools?.length) {
      openedModules.value[module.code] = true
    }
  }
}, { immediate: true })

onMounted(refreshAll)

async function loadCatalog() {
  catalogLoading.value = true
  try {
    const res = await apiAgent.toolModules()
    catalog.value = res.data
  }
  finally { catalogLoading.value = false }
}

async function toggleSystem(value?: boolean) {
  const enabled = value === true
  if (busyKey.value || !configEnabled.value) {
    return
  }
  busyKey.value = 'system'
  try {
    const res = await apiAgent.updateSystemToolsEnabled(enabled)
    catalog.value = res.data
    toast.success(enabled ? '系统工具总开关已开启' : '系统工具总开关已关闭')
  }
  catch { toast.error('系统工具开关保存失败，请重试') }
  finally { busyKey.value = '' }
}

async function toggleModule(module: AiToolModule, value?: boolean) {
  const enabled = value === true
  if (busyKey.value || module.canDisable === false || !configEnabled.value || !systemEnabled.value) {
    return
  }
  busyKey.value = module.code
  try {
    const res = await apiAgent.updateToolModuleEnabled(module.code, enabled)
    catalog.value = res.data
    toast.success(enabled ? `已启用「${module.name}」模块` : `已停用「${module.name}」模块`)
  }
  catch { toast.error(`「${module.name}」模块开关保存失败，请重试`) }
  finally { busyKey.value = '' }
}

function moduleSwitchDisabled(module: AiToolModule) {
  return switchBusy.value || !configEnabled.value || !systemEnabled.value || module.canDisable === false
}

/** 开关旁的状态说明：禁用原因优先于开关值，避免出现「点不动的开关」没有解释。 */
function moduleSwitchState(module: AiToolModule) {
  if (module.canDisable === false) {
    return '不可关闭'
  }
  if (!configEnabled.value) {
    return '部署配置已关闭'
  }
  if (!systemEnabled.value) {
    return '随总开关停用'
  }
  return module.enabled ? '已启用' : '已停用'
}

function moduleIcon(module: AiToolModule) {
  const curated = moduleIcons[module.code]
  if (curated) {
    return curated
  }
  const icon = (module.icon || '').trim()
  if (moduleIconAliases[icon]) {
    return moduleIconAliases[icon]
  }
  if (icon.startsWith('morph:')) {
    return icon
  }
  if (module.source === 'plugin') {
    return 'morph:package'
  }
  if (module.source === 'mcp') {
    return 'morph:plug'
  }
  return 'morph:puzzle'
}

function sourceText(source?: string) {
  return source ? sourceLabels[source] ?? source : '系统'
}

function moduleTools(module: AiToolModule) {
  return module.tools ?? []
}

function moduleCountText(module: AiToolModule) {
  const total = module.toolCount ?? moduleTools(module).length
  const visible = moduleTools(module).length
  return visible === total ? `${total} 个工具` : `匹配 ${visible} / 共 ${total} 个工具`
}

/* ------------------------------------------------------------ Python 工具 */

const loading = ref(false)
const visible = ref(false)
const editing = ref<AgentTool | null>(null)
const rows = ref<AgentTool[]>([])
const selectedRows = shallowRef<AgentTool[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
// 窄屏下分页隐藏每页条数与跳转输入，避免换行挤压
const isCompact = useMediaQuery('(max-width: 640px)')
const toolsPaginationLayout = computed(() => isCompact.value ? 'total, pager' : 'total, sizes, ->, pager, jumper')
const keyword = ref('')
const defaultPythonCode = 'def run(params: dict) -> dict:\n    name = str(params.get("name", ""))\n    return {\n        "success": True,\n        "message": f"Hello, {name}",\n    }'
const form = reactive({ name: '', code: '', description: '', inputSchemaJson: defaultAgentToolInputSchema, outputExampleJson: defaultAgentToolOutputExample, pythonCode: defaultPythonCode, timeoutMillis: 10000, permissionCode: '', enabled: true })
const toolColumns = computed<TableColumn<AgentTool>[]>(() => [
  { type: 'selection', fixed: 'left', width: 44 },
  { accessorKey: 'name', header: '名称' },
  { accessorKey: 'code', header: '编码' },
  { accessorKey: 'description', header: '描述' },
  { accessorKey: 'timeoutMillis', header: '超时(ms)' },
  { id: 'enabled', header: '状态' },
  { id: 'operation', header: '操作' },
])

async function refreshAll() {
  await Promise.all([loadCatalog(), loadPythonTools()])
}

async function loadPythonTools() {
  loading.value = true
  try {
    const res = await apiAgent.pageTools({ ...pagination, keyword: keyword.value || undefined })
    rows.value = res.data.records
    pagination.total = Number(res.data.total || 0)
    selectedRows.value = []
  }
  finally { loading.value = false }
}

function onSelectionChange(selected: AgentTool[]) {
  selectedRows.value = selected
}
function batchRemove() {
  if (!selectedRows.value.length) {
    toast.warning('请先勾选工具')
    return
  }
  modal.confirm({
    title: '批量删除 Python 工具',
    content: `确定删除选中的 ${selectedRows.value.length} 个工具吗？已被应用使用时需要先移除节点。`,
    onConfirm: async () => {
      for (const row of selectedRows.value) {
        await apiAgent.deleteTool(row.id)
      }
      toast.success(`已删除 ${selectedRows.value.length} 个工具`)
      await refreshAll()
    },
  })
}
function resetForm() {
  Object.assign(form, { name: '', code: '', description: '', inputSchemaJson: defaultAgentToolInputSchema, outputExampleJson: defaultAgentToolOutputExample, pythonCode: defaultPythonCode, timeoutMillis: 10000, permissionCode: '', enabled: true })
}
function create() {
  editing.value = null
  resetForm()
  visible.value = true
}
function edit(row: AgentTool) {
  editing.value = row
  Object.assign(form, row, {
    inputSchemaJson: row.inputSchemaJson || defaultAgentToolInputSchema,
    outputExampleJson: row.outputExampleJson || defaultAgentToolOutputExample,
  })
  visible.value = true
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
    await refreshAll()
  }
  catch { toast.error('工具保存失败，请检查工具配置') }
}
function remove(row: AgentTool) {
  modal.confirm({
    title: '删除 Python 工具',
    content: `确定删除“${row.name}”吗？已被应用使用时需要先移除节点。`,
    onConfirm: async () => {
      await apiAgent.deleteTool(row.id)
      toast.success('已删除')
      await refreshAll()
    },
  })
}
</script>

<template>
  <div>
    <YdPageHeader title="系统工具" class="mb-0">
      <template #description>
        系统工具按模块启停、按需对模型可见；Python 工具用于补充自定义工作流能力。
      </template>
      <YdButton v-auth="'platform:agent:tool:edit'" @click="create">
        <YdIcon name="morph:plus" /> 新建 Python 工具
      </YdButton>
    </YdPageHeader>
    <YdPageMain>
      <section class="tools-section">
        <YdListToolbar
          v-model:keyword="moduleKeyword"
          title="系统工具"
          placeholder="搜索模块或工具"
          :search-width="240"
        >
          <template #views>
            <span class="section-summary">{{ moduleSummary }}</span>
          </template>
          <template #actions>
            <div v-auth="'platform:agent:tool:edit'" class="master-switch">
              <span class="master-switch__label">系统工具总开关</span>
              <YdSwitch
                :model-value="systemEnabled"
                :disabled="switchBusy || !configEnabled"
                aria-label="系统工具总开关"
                @update:model-value="toggleSystem"
              />
            </div>
          </template>
        </YdListToolbar>

        <p class="section-note">
          <YdIcon name="morph:info" class="section-note__icon" />
          <span>{{ moduleLoadingHint }}</span>
        </p>

        <YdAlert
          v-if="!configEnabled"
          icon="morph:triangle-alert"
          variant="destructive"
          title="部署配置已关闭系统工具"
          description="配置项 yudream.platform.agent.system-tools-enabled 为 false，总开关与模块开关都不会生效；请先在部署配置中开启后再管理模块。"
        />
        <YdAlert
          v-else-if="!systemEnabled"
          icon="morph:power"
          title="系统工具总开关已关闭"
          description="所有模块的工具都不会提供给模型，逐模块开关在此期间不生效。"
        />

        <YdLoadingOverlay :loading="catalogLoading">
          <div v-if="filteredModules.length" class="module-list">
            <div
              v-for="module in filteredModules"
              :key="module.code"
              class="module-card"
              :class="{ 'is-off': !module.enabled }"
            >
              <YdCollapsible v-model="openedModules[module.code]" class="module-collapsible">
                <template #trigger="{ open }">
                  <span class="module-head">
                    <span class="module-icon"><YdIcon :name="moduleIcon(module)" /></span>
                    <span class="module-main">
                      <span class="module-title">
                        <strong>{{ module.name }}</strong>
                        <code>{{ module.code }}</code>
                        <YdTag variant="outline">{{ sourceText(module.source) }}</YdTag>
                        <YdTag :variant="module.lazy ? 'secondary' : 'default'">
                          {{ module.lazy ? '按需加载' : '常驻' }}
                        </YdTag>
                        <YdTag v-if="module.canDisable === false" variant="secondary">
                          不可关闭
                        </YdTag>
                      </span>
                      <span class="module-desc">{{ module.description || '暂无说明' }}</span>
                    </span>
                    <span class="module-count">{{ moduleCountText(module) }}</span>
                    <YdIcon name="morph:chevron-down" class="module-chevron" :class="{ 'is-open': open }" />
                  </span>
                </template>
                <div class="module-body">
                  <p v-if="!module.enabled && configEnabled && systemEnabled" class="module-note">
                    模块已停用，下列工具当前不会提供给模型；展开仅用于查看清单。
                  </p>
                  <div v-if="moduleTools(module).length" class="tool-list">
                    <div class="tool-row tool-row--head" aria-hidden="true">
                      <span>工具</span>
                      <span>说明</span>
                      <span>风险</span>
                      <span>权限码</span>
                    </div>
                    <div v-for="tool in moduleTools(module)" :key="tool.code" class="tool-row">
                      <span class="tool-name">
                        <span class="tool-name__text">
                          {{ tool.name }}
                          <YdTag v-if="tool.enabled === false" variant="secondary">已停用</YdTag>
                        </span>
                        <code>{{ tool.code }}</code>
                      </span>
                      <span class="tool-desc" :title="tool.description || ''">{{ tool.description || '暂无说明' }}</span>
                      <span class="tool-risk">
                        <span class="tool-label">风险</span>
                        <YdTag v-if="tool.risk === 'READ'" variant="secondary">只读</YdTag>
                        <YdTag v-else-if="tool.risk === 'WRITE'" :variant="tool.requiresConfirmation ? 'destructive' : 'outline'">
                          {{ tool.requiresConfirmation ? '写入 · 需确认' : '写入' }}
                        </YdTag>
                        <span v-else class="tool-muted">未标注</span>
                      </span>
                      <span class="tool-perm">
                        <span class="tool-label">权限</span>
                        <code v-if="tool.permissionCode">{{ tool.permissionCode }}</code>
                        <span v-else class="tool-muted">无需权限码</span>
                      </span>
                    </div>
                  </div>
                  <p v-else class="module-note">
                    该模块暂无已注册工具。
                  </p>
                </div>
              </YdCollapsible>
              <div v-auth="'platform:agent:tool:edit'" class="module-switch">
                <YdSwitch
                  :model-value="module.enabled === true"
                  :disabled="moduleSwitchDisabled(module)"
                  :aria-label="`${module.name}模块开关`"
                  @update:model-value="(value?: boolean) => toggleModule(module, value)"
                />
                <span class="module-switch__state">{{ moduleSwitchState(module) }}</span>
              </div>
            </div>
          </div>
          <YdEmpty
            v-else-if="catalogLoading"
            title="正在加载系统工具模块"
            description="正在读取平台注册的工具模块与开关状态。"
          />
          <YdEmpty v-else-if="modules.length" title="没有匹配的模块" description="换一个关键词，或清除搜索查看全部模块。">
            <YdButton variant="outline" @click="moduleKeyword = ''">
              清除搜索
            </YdButton>
          </YdEmpty>
          <YdEmpty v-else title="暂无系统工具模块" description="当前平台没有可管理的系统工具模块。" />
        </YdLoadingOverlay>
      </section>

      <section class="tools-section">
        <YdListToolbar
          v-model:keyword="keyword"
          title="Python 工具"
          placeholder="名称 / 编码"
          :search-width="240"
          @search="loadPythonTools"
          @clear="loadPythonTools"
        >
          <template #views>
            <span class="section-summary">共 {{ pagination.total }} 个</span>
          </template>
        </YdListToolbar>

        <p class="section-note">
          <YdIcon name="morph:terminal" class="section-note__icon" />
          <span>通过 <code>run(params: dict) -&gt; dict</code> 接收参数并返回结构化结果，供 Agent 应用按权限调用。</span>
        </p>

        <div v-if="selectedRows.length" class="batch-bar">
          <span>已选 {{ selectedRows.length }} 项</span>
          <YdButton v-auth="'platform:agent:tool:delete'" size="sm" variant="destructive" @click="batchRemove">
            批量删除
          </YdButton>
        </div>
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveTable row-key="id" :data="rows" :columns="toolColumns" selectable multiple @selection-change="onSelectionChange">
            <template #cell-enabled="{ row }">
              <YdTag :variant="row.original.enabled ? 'default' : 'secondary'">
                {{ row.original.enabled ? '启用' : '停用' }}
              </YdTag>
            </template>
            <template #cell-operation="{ row }">
              <div class="flex gap-2 justify-center">
                <YdButton v-auth="'platform:agent:tool:edit'" size="sm" variant="outline" @click="edit(row.original)">
                  编辑
                </YdButton>
                <YdButton v-auth="'platform:agent:tool:delete'" size="sm" variant="destructive" @click="remove(row.original)">
                  删除
                </YdButton>
              </div>
            </template>
            <template #card="{ row }">
              <YdCard class="w-full">
                <div class="flex flex-col gap-3">
                  <div class="flex items-center justify-between gap-2">
                    <span class="min-w-0 break-words text-base font-semibold">{{ row.name }}</span>
                    <YdTag :variant="row.enabled ? 'default' : 'secondary'">
                      {{ row.enabled ? '启用' : '停用' }}
                    </YdTag>
                  </div>
                  <div class="flex flex-col gap-1 text-sm">
                    <div class="flex gap-2">
                      <span class="shrink-0 text-secondary-foreground/60">编码</span>
                      <span>{{ row.code }}</span>
                    </div>
                    <div v-if="row.description" class="flex gap-2">
                      <span class="shrink-0 text-secondary-foreground/60">描述</span>
                      <span class="break-all">{{ row.description }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="shrink-0 text-secondary-foreground/60">超时</span>
                      <span>{{ row.timeoutMillis }}ms</span>
                    </div>
                    <div v-if="row.permissionCode" class="flex gap-2">
                      <span class="shrink-0 text-secondary-foreground/60">权限</span>
                      <span>{{ row.permissionCode }}</span>
                    </div>
                  </div>
                  <div class="flex flex-wrap gap-2 border-t pt-3">
                    <YdButton v-auth="'platform:agent:tool:edit'" size="sm" variant="outline" @click="edit(row)">
                      编辑
                    </YdButton>
                    <YdButton v-auth="'platform:agent:tool:delete'" size="sm" variant="destructive" @click="remove(row)">
                      删除
                    </YdButton>
                  </div>
                </div>
              </YdCard>
            </template>
          </YdResponsiveTable>
        </YdLoadingOverlay>
        <YdPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" :layout="toolsPaginationLayout" @page-change="loadPythonTools" @size-change="loadPythonTools" />
      </section>
    </YdPageMain>
    <YdModal v-model="visible" :title="editing ? '编辑 Python 工具' : '新建 Python 工具'" class="sm:max-w-4xl">
      <div class="tool-form">
        <div class="gap-4 grid sm:grid-cols-2">
          <label class="form-field required"><span>工具名称</span><YdInput v-model="form.name" class="w-full" maxlength="60" placeholder="例如：订单风险查询" /></label>
          <label class="form-field required"><span>工具编码</span><YdInput v-model="form.code" class="w-full" :disabled="Boolean(editing)" maxlength="60" placeholder="例如：order.risk.query" /></label>
        </div>
        <label class="form-field"><span>工具描述</span><YdTextarea v-model="form.description" class="w-full" :autosize="{ minRows: 3, maxRows: 5 }" placeholder="说明工具用途，模型会根据这段描述决定何时调用" /></label>
        <div class="gap-4 grid sm:grid-cols-2">
          <label class="form-field required"><span>超时时间（毫秒）</span><YdInput v-model.number="form.timeoutMillis" class="w-full" type="number" min="100" placeholder="10000" /></label>
          <label class="form-field"><span>权限编码</span><YdInput v-model="form.permissionCode" class="w-full" placeholder="可选，例如：order:risk:query" /></label>
        </div>
        <YdCheckbox v-model="form.enabled" class="check-line"><span><b>启用工具</b><small>停用后 Agent 无法再调用此工具</small></span></YdCheckbox>
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
/* 两个分区共用同一版式：工具栏 + 一行说明 + 内容区，分区之间用留白和分隔线区分。 */
.tools-section { display: grid; gap: 16px; }
.tools-section + .tools-section { margin-top: 28px; padding-top: 24px; border-top: 1px solid var(--color-border-2); }
.section-summary { color: var(--color-text-3); font-size: 12px; font-variant-numeric: tabular-nums; }
.section-note { display: flex; gap: 6px; align-items: flex-start; margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.6; }
.section-note__icon { flex: none; margin-top: 2px; }
.section-note code { padding: 0 4px; border-radius: 4px; background: var(--color-fill-2); color: var(--color-text-2); font-size: 11px; }

/* 总开关收成筛选行尾部的一枚胶囊，避免和模块开关争夺注意力。 */
.master-switch { display: inline-flex; gap: 10px; align-items: center; padding: 4px 10px 4px 12px; border: 1px solid var(--color-border-2); border-radius: 999px; background: var(--color-bg-1); }
.master-switch__label { color: var(--color-text-2); font-size: 12px; white-space: nowrap; }

/* 模块行：左列是模块信息与工具清单，右列是该模块开关；窄屏下开关落到行尾。 */
.module-list { display: grid; gap: 10px; }
.module-card { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: start; border: 1px solid var(--color-border-2); border-radius: 10px; background: var(--color-bg-1); transition: border-color .18s ease, background-color .18s ease; }
.module-card:hover { border-color: var(--color-border-3); }
.module-card.is-off { background: color-mix(in oklab, var(--color-fill-1) 55%, var(--color-bg-1)); }
.module-collapsible { min-width: 0; }
/* 折叠触发器就是整行模块头：还原按钮默认外观，只保留焦点环。 */
.module-collapsible :deep([data-slot='collapsible-trigger']) { display: block; width: 100%; padding: 0; color: inherit; font: inherit; text-align: left; cursor: pointer; background: transparent; border: 0; }
.module-collapsible :deep([data-slot='collapsible-trigger']:focus-visible) { border-radius: 10px; outline: 2px solid var(--yd-emphasis, var(--color-text-1)); outline-offset: -2px; }
.module-collapsible :deep([data-slot='collapsible-content']) { border-top: 1px solid var(--color-border-2); }
.module-head { display: grid; grid-template-columns: auto minmax(0, 1fr) auto auto; gap: 12px; align-items: center; padding: 14px 16px; }
.module-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 9px; background: var(--color-fill-2); color: var(--color-text-2); font-size: 17px; }
.module-card.is-off .module-icon { opacity: .6; }
.module-main { display: grid; gap: 4px; min-width: 0; }
.module-title { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.module-title strong { color: var(--color-text-1); font-size: 14px; font-weight: 600; }
.module-title code { color: var(--color-text-3); font-size: 11px; }
.module-desc { overflow: hidden; color: var(--color-text-3); font-size: 12px; line-height: 1.5; text-overflow: ellipsis; white-space: nowrap; }
.module-count { color: var(--color-text-3); font-size: 12px; white-space: nowrap; font-variant-numeric: tabular-nums; }
.module-chevron { color: var(--color-text-3); transition: transform .2s cubic-bezier(.32, .72, 0, 1); }
.module-card:hover .module-chevron { color: var(--color-text-1); }
.module-chevron.is-open { transform: rotate(180deg); }
.module-switch { display: flex; flex-direction: column; gap: 6px; align-items: center; padding: 22px 16px 14px; }
.module-switch__state { color: var(--color-text-3); font-size: 11px; white-space: nowrap; }
.module-body { display: grid; gap: 8px; padding: 12px 16px 14px; }
.module-note { margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.6; }

/* 模块内工具：紧凑只读清单，桌面按列对齐，窄屏逐行堆叠；不使用卡片。 */
.tool-list { display: grid; overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 8px; }
.tool-row { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(0, 2fr) auto minmax(0, 1fr); gap: 12px; align-items: center; padding: 9px 12px; border-top: 1px solid var(--color-border-2); font-size: 12px; }
.tool-row:first-child { border-top: 0; }
.tool-row--head { padding: 7px 12px; background: var(--color-fill-1); color: var(--color-text-3); font-size: 11px; }
.tool-name { display: grid; gap: 2px; min-width: 0; }
.tool-name__text { display: inline-flex; flex-wrap: wrap; gap: 6px; align-items: center; color: var(--color-text-1); font-weight: 500; }
.tool-name code { overflow: hidden; color: var(--color-text-3); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.tool-desc { overflow: hidden; color: var(--color-text-3); text-overflow: ellipsis; white-space: nowrap; }
.tool-risk, .tool-perm { display: inline-flex; gap: 6px; align-items: center; min-width: 0; }
.tool-perm code { overflow: hidden; color: var(--color-text-2); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.tool-label { display: none; color: var(--color-text-3); font-size: 11px; }
.tool-muted { color: var(--color-text-3); }

.batch-bar { display: flex; align-items: center; gap: 12px; padding: 8px 12px; border: 1px solid var(--color-border-2); border-radius: 6px; font-size: 12px; color: var(--color-text-3); }

.tool-form { display: grid; gap: 16px; }
.form-field { display: grid; gap: 7px; color: var(--color-text-2); font-size: 12px; }
.form-field.required > span::after { margin-left: 3px; color: rgb(var(--danger-6)); content: '*'; }
.form-field small { color: var(--color-text-3); font-size: 10px; line-height: 1.5; }
.check-line { display: grid; grid-template-columns: 20px minmax(0, 1fr); align-items: start; gap: 8px; padding: 10px; border: 1px solid var(--color-border-2); border-radius: 6px; font-size: 12px; cursor: pointer; }
.check-line span { display: grid; gap: 2px; }
.check-line b { color: var(--color-text-1); font-weight: 500; }
.check-line small { color: var(--color-text-3); font-size: 10px; }

@media (max-width: 768px) {
  /* 模块行退化为两段：模块信息在上，开关行贴底并横向排布。 */
  .module-card { grid-template-columns: minmax(0, 1fr); }
  .module-switch { flex-direction: row-reverse; justify-content: space-between; padding: 10px 16px; border-top: 1px solid var(--color-border-2); }
  /* 工具数换到标题块下方单独成行，避免挤占模块名。 */
  .module-head { grid-template-columns: auto minmax(0, 1fr) auto; grid-template-areas: 'icon main chevron' '. count count'; gap: 8px 10px; }
  .module-icon { grid-area: icon; }
  .module-main { grid-area: main; }
  .module-count { grid-area: count; }
  .module-chevron { grid-area: chevron; }
  /* 窄屏不再保留列标题，逐项堆叠并补上字段名。 */
  .tool-row--head { display: none; }
  .tool-row { grid-template-columns: minmax(0, 1fr); gap: 6px; }
  .tool-desc { white-space: normal; }
  .tool-label { display: inline; }
}
@media (max-width: 640px) {
  .master-switch { flex: 1 1 100%; justify-content: space-between; }
}
@media (prefers-reduced-motion: reduce) {
  .module-chevron { transition: none; }
}
</style>

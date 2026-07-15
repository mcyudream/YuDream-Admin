<script setup lang="ts">
import type { AgentTool, SystemAgentTool } from '@/api/modules/platform-agent'
import apiAgent from '@/api/modules/platform-agent'

const toast = useFaToast()
const modal = useFaModal()
const loading = ref(false)
const visible = ref(false)
const editing = ref<AgentTool | null>(null)
const rows = ref<AgentTool[]>([])
const systemTools = ref<SystemAgentTool[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const keyword = ref('')
const defaultInputSchema = '{\n  "type": "object",\n  "properties": {\n    "name": { "type": "string", "description": "名称" }\n  },\n  "required": ["name"]\n}'
const defaultOutputExample = '{\n  "success": true,\n  "message": "Hello, YuDream"\n}'
const defaultPythonCode = 'def run(params: dict) -> dict:\n    name = str(params.get("name", ""))\n    return {\n        "success": True,\n        "message": f"Hello, {name}",\n    }'
const form = reactive({ name: '', code: '', description: '', inputSchemaJson: defaultInputSchema, outputExampleJson: defaultOutputExample, pythonCode: defaultPythonCode, timeoutMillis: 10000, permissionCode: '', enabled: true })

onMounted(load)
async function load() {
  loading.value = true
  try {
    const [custom, system] = await Promise.all([apiAgent.pageTools({ ...pagination, keyword: keyword.value || undefined }), apiAgent.systemTools()])
    rows.value = custom.data.records
    pagination.total = Number(custom.data.total || 0)
    systemTools.value = system.data
  }
  finally { loading.value = false }
}
function resetForm() {
  Object.assign(form, { name: '', code: '', description: '', inputSchemaJson: defaultInputSchema, outputExampleJson: defaultOutputExample, pythonCode: defaultPythonCode, timeoutMillis: 10000, permissionCode: '', enabled: true })
}
function create() {
  editing.value = null
  resetForm()
  visible.value = true
}
function edit(row: AgentTool) {
  editing.value = row
  Object.assign(form, row, {
    inputSchemaJson: row.inputSchemaJson || defaultInputSchema,
    outputExampleJson: row.outputExampleJson || defaultOutputExample,
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
    await load()
  }
  catch { toast.error('工具保存失败，请检查工具配置') }
}
function isJsonObject(value: string) {
  try {
    const parsed = JSON.parse(value)
    return parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)
  }
  catch {
    return false
  }
}
function remove(row: AgentTool) {
  modal.confirm({
    title: '删除 Python 工具',
    content: `确定删除“${row.name}”吗？已被应用使用时需要先移除节点。`,
    onConfirm: async () => {
      await apiAgent.deleteTool(row.id)
      toast.success('已删除')
      await load()
    },
  })
}
</script>

<template>
  <div>
    <FaPageHeader title="Agent 工具" class="mb-0">
      <FaButton v-auth="'platform:agent:tool:edit'" @click="create">
        <FaIcon name="i-ri:add-line" /> 新建 Python 工具
      </FaButton>
    </FaPageHeader>
    <FaPageMain>
      <section class="system-tools">
        <div class="section-head">
          <div><h3>系统工具</h3><p>由平台能力注册，可在 Agent 应用中按权限使用。</p></div>
        </div><div class="system-tool-grid">
          <article v-for="tool in systemTools" :key="tool.code">
            <FaIcon name="i-ri:flashlight-line" /><div><strong>{{ tool.name }}</strong><code>{{ tool.code }}</code><p>{{ tool.description || '系统提供的能力' }}</p></div>
          </article><FaEmpty v-if="!systemTools.length" description="暂无已启用的系统工具" />
        </div>
      </section>
      <section class="custom-tools">
        <div class="section-head">
          <div><h3>Python 工具</h3><p>通过 <code>run(params: dict) -&gt; dict</code> 接收参数并返回结构化结果。</p></div><FaInput v-model="keyword" class="w-72" clearable placeholder="名称 / 编码" @keydown.enter="load" />
        </div><FaTable v-loading="loading" row-key="id" :data="rows" :columns="[{ accessorKey: 'name', header: '名称' }, { accessorKey: 'code', header: '编码' }, { accessorKey: 'description', header: '描述' }, { accessorKey: 'timeoutMillis', header: '超时(ms)' }, { id: 'enabled', header: '状态' }, { id: 'operation', header: '操作' }]">
          <template #cell-enabled="{ row }">
            <FaTag :variant="row.original.enabled ? 'default' : 'secondary'">
              {{ row.original.enabled ? '启用' : '停用' }}
            </FaTag>
          </template><template #cell-operation="{ row }">
            <div class="flex gap-2 justify-center">
              <FaButton v-auth="'platform:agent:tool:edit'" size="sm" variant="outline" @click="edit(row.original)">
                编辑
              </FaButton><FaButton v-auth="'platform:agent:tool:delete'" size="sm" variant="destructive" @click="remove(row.original)">
                删除
              </FaButton>
            </div>
          </template>
        </FaTable><FaPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" class="mt-3" @page-change="load" @size-change="load" />
      </section>
    </FaPageMain>
    <FaModal v-model="visible" :title="editing ? '编辑 Python 工具' : '新建 Python 工具'" class="sm:max-w-4xl">
      <div class="tool-form">
        <div class="gap-4 grid sm:grid-cols-2">
          <label class="form-field required"><span>工具名称</span><FaInput v-model="form.name" class="w-full" maxlength="60" placeholder="例如：订单风险查询" /></label>
          <label class="form-field required"><span>工具编码</span><FaInput v-model="form.code" class="w-full" :disabled="Boolean(editing)" maxlength="60" placeholder="例如：order.risk.query" /></label>
        </div>
        <label class="form-field"><span>工具描述</span><FaTextarea v-model="form.description" class="w-full" :autosize="{ minRows: 3, maxRows: 5 }" placeholder="说明工具用途，模型会根据这段描述决定何时调用" /></label>
        <div class="gap-4 grid sm:grid-cols-2">
          <label class="form-field required"><span>超时时间（毫秒）</span><FaInput v-model.number="form.timeoutMillis" class="w-full" type="number" min="100" placeholder="10000" /></label>
          <label class="form-field"><span>权限编码</span><FaInput v-model="form.permissionCode" class="w-full" placeholder="可选，例如：order:risk:query" /></label>
        </div>
        <label class="check-line"><input v-model="form.enabled" type="checkbox"> <span><b>启用工具</b><small>停用后 Agent 无法再调用此工具</small></span></label>
        <label class="form-field required">
          <span>输入 JSON Schema</span>
          <FaTextarea v-model="form.inputSchemaJson" class="w-full" input-class="font-mono" :autosize="{ minRows: 6, maxRows: 12 }" placeholder="定义工具接收的参数名称、类型和必填项" />
          <small>使用 JSON Schema object 描述参数，供模型生成合法调用参数</small>
        </label>
        <label class="form-field required">
          <span>输出格式示例</span>
          <FaTextarea v-model="form.outputExampleJson" class="w-full" input-class="font-mono" :autosize="{ minRows: 5, maxRows: 10 }" placeholder="填写 run() 返回字典的 JSON 示例" />
          <small>必须是 JSON object 示例，用于说明返回字段、类型和业务含义</small>
        </label>
        <label class="form-field required">
          <span>Python 代码</span>
          <FaTextarea v-model="form.pythonCode" class="w-full" input-class="font-mono" :autosize="{ minRows: 13, maxRows: 22 }" placeholder="def run(params: dict) -> dict: ..." />
          <small>必须定义 run(params: dict) -&gt; dict；不要读取 stdin、写 stdout 或返回字符串</small>
        </label>
      </div>
      <template #footer>
        <FaButton variant="outline" @click="visible = false">
          取消
        </FaButton><FaButton @click="save">
          保存工具
        </FaButton>
      </template>
    </FaModal>
  </div>
</template>

<style scoped>
.system-tools, .custom-tools { display: grid; gap: 16px; }.custom-tools { margin-top: 24px; }.section-head { display: flex; align-items: end; justify-content: space-between; gap: 16px; }.section-head h3, .section-head p { margin: 0; }.section-head p { margin-top: 6px; color: var(--color-text-3); font-size: 13px; }.system-tool-grid { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); }.system-tool-grid article { display: flex; gap: 12px; padding: 14px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-1); }.system-tool-grid article > :first-child { color: rgb(var(--primary-6)); }.system-tool-grid div { display: grid; gap: 5px; }.system-tool-grid p { margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.5; }.system-tool-grid code { color: var(--color-text-3); font-size: 11px; }.tool-form { display: grid; gap: 16px; }.form-field { display: grid; gap: 7px; color: var(--color-text-2); font-size: 12px; }.form-field.required > span::after { margin-left: 3px; color: rgb(var(--danger-6)); content: '*'; }.form-field small { color: var(--color-text-3); font-size: 10px; line-height: 1.5; }.check-line { display: grid; grid-template-columns: 17px minmax(0, 1fr); align-items: start; gap: 8px; padding: 10px; border: 1px solid var(--color-border-2); border-radius: 6px; font-size: 12px; cursor: pointer; }.check-line input { margin-top: 2px; accent-color: rgb(var(--primary-6)); }.check-line span { display: grid; gap: 2px; }.check-line b { color: var(--color-text-1); font-weight: 500; }.check-line small { color: var(--color-text-3); font-size: 10px; }
</style>

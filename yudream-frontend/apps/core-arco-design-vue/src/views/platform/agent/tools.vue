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
const form = reactive({ name: '', code: '', description: '', inputSchemaJson: '{\n  "type": "object",\n  "properties": {}\n}', pythonCode: 'import sys\n\ninput_text = sys.stdin.read()\nprint(input_text)', timeoutMillis: 10000, permissionCode: '', enabled: true })

onMounted(load)
async function load() {
  loading.value = true
  try {
    const [custom, system] = await Promise.all([apiAgent.pageTools({ ...pagination, keyword: keyword.value || undefined }), apiAgent.systemTools()])
    rows.value = custom.data.records; pagination.total = Number(custom.data.total || 0); systemTools.value = system.data
  }
  finally { loading.value = false }
}
function resetForm() { Object.assign(form, { name: '', code: '', description: '', inputSchemaJson: '{\n  "type": "object",\n  "properties": {}\n}', pythonCode: 'import sys\n\ninput_text = sys.stdin.read()\nprint(input_text)', timeoutMillis: 10000, permissionCode: '', enabled: true }) }
function create() { editing.value = null; resetForm(); visible.value = true }
function edit(row: AgentTool) { editing.value = row; Object.assign(form, row); visible.value = true }
async function save() {
  if (!form.name.trim() || !form.code.trim() || !form.pythonCode.trim()) { toast.error('请填写工具名称、编码和 Python 代码'); return }
  try {
    const payload = { ...form }
    if (editing.value) await apiAgent.updateTool(editing.value.id, payload); else await apiAgent.createTool(payload)
    toast.success('工具已保存'); visible.value = false; await load()
  }
  catch { toast.error('工具保存失败，请检查 JSON Schema') }
}
function remove(row: AgentTool) { modal.confirm({ title: '删除 Python 工具', content: `确定删除“${row.name}”吗？已被应用使用时需要先移除节点。`, onConfirm: async () => { await apiAgent.deleteTool(row.id); toast.success('已删除'); await load() } }) }
</script>

<template>
  <div>
    <FaPageHeader title="Agent 工具" class="mb-0"><FaButton v-auth="'platform:agent:tool:edit'" @click="create"><FaIcon name="i-ri:add-line" /> 新建 Python 工具</FaButton></FaPageHeader>
    <FaPageMain>
      <section class="system-tools"><div class="section-head"><div><h3>系统工具</h3><p>由平台能力注册，可在 Agent 应用中按权限使用。</p></div></div><div class="system-tool-grid"><article v-for="tool in systemTools" :key="tool.code"><FaIcon name="i-ri:flashlight-line" /><div><strong>{{ tool.name }}</strong><code>{{ tool.code }}</code><p>{{ tool.description || '系统提供的能力' }}</p></div></article><FaEmpty v-if="!systemTools.length" description="暂无已启用的系统工具" /></div></section>
      <section class="custom-tools"><div class="section-head"><div><h3>Python 工具</h3><p>脚本从标准输入读取 Agent 输入，并将标准输出作为工具结果。</p></div><FaInput v-model="keyword" class="w-72" clearable placeholder="名称 / 编码" @keydown.enter="load" /></div><FaTable v-loading="loading" row-key="id" :data="rows" :columns="[{ accessorKey: 'name', header: '名称' }, { accessorKey: 'code', header: '编码' }, { accessorKey: 'description', header: '描述' }, { accessorKey: 'timeoutMillis', header: '超时(ms)' }, { id: 'enabled', header: '状态' }, { id: 'operation', header: '操作' }]"><template #cell-enabled="{ row }"><FaTag :variant="row.original.enabled ? 'default' : 'secondary'">{{ row.original.enabled ? '启用' : '停用' }}</FaTag></template><template #cell-operation="{ row }"><div class="flex justify-center gap-2"><FaButton v-auth="'platform:agent:tool:edit'" size="sm" variant="outline" @click="edit(row.original)">编辑</FaButton><FaButton v-auth="'platform:agent:tool:delete'" size="sm" variant="destructive" @click="remove(row.original)">删除</FaButton></div></template></FaTable><FaPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" class="mt-3" @page-change="load" @size-change="load" /></section>
    </FaPageMain>
    <FaModal v-model="visible" :title="editing ? '编辑 Python 工具' : '新建 Python 工具'" class="sm:max-w-4xl"><div class="tool-form"><div class="grid gap-3 sm:grid-cols-2"><FaInput v-model="form.name" label="名称" /><FaInput v-model="form.code" label="编码" :disabled="Boolean(editing)" /></div><FaInput v-model="form.description" label="描述" /><div class="grid gap-3 sm:grid-cols-2"><FaInput v-model.number="form.timeoutMillis" type="number" label="超时（毫秒）" /><FaInput v-model="form.permissionCode" label="权限编码（可选）" /></div><label class="check-line"><input v-model="form.enabled" type="checkbox"> 启用工具</label><FaTextarea v-model="form.inputSchemaJson" :autosize="{ minRows: 5 }" label="输入 JSON Schema" /><FaTextarea v-model="form.pythonCode" :autosize="{ minRows: 12 }" label="Python 代码" /></div><template #footer><FaButton variant="outline" @click="visible = false">取消</FaButton><FaButton @click="save">保存</FaButton></template></FaModal>
  </div>
</template>

<style scoped>
.system-tools, .custom-tools { display: grid; gap: 16px; }.custom-tools { margin-top: 24px; }.section-head { display: flex; align-items: end; justify-content: space-between; gap: 16px; }.section-head h3, .section-head p { margin: 0; }.section-head p { margin-top: 6px; color: var(--color-text-3); font-size: 13px; }.system-tool-grid { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); }.system-tool-grid article { display: flex; gap: 12px; padding: 14px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-1); }.system-tool-grid article > :first-child { color: rgb(var(--primary-6)); }.system-tool-grid div { display: grid; gap: 5px; }.system-tool-grid p { margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.5; }.system-tool-grid code { color: var(--color-text-3); font-size: 11px; }.tool-form { display: grid; gap: 14px; }.check-line { display: flex; gap: 8px; align-items: center; font-size: 14px; }
</style>

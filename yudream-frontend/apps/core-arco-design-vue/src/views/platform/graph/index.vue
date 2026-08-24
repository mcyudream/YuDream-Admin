<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { PluginModule } from '@/api/modules/platform-plugin'
import type { GraphTable, GraphTablePayload, GraphTableStatus } from '@/api/modules/platform-graph'
import apiPlugin from '@/api/modules/platform-plugin'
import apiGraph from '@/api/modules/platform-graph'
const modal = useFaModal()
const toast = useFaToast()
const loading = ref(false)
const rows = ref<GraphTable[]>([])
const plugins = ref<PluginModule[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const keyword = ref('')
const formVisible = ref(false)
const editing = ref<GraphTable>()
const form = reactive<GraphTablePayload>({ name: '', code: '', description: '', status: 'ACTIVE', authorizedPluginCodes: [] })
const columns = computed<TableColumn<GraphTable>[]>(() => [
  { accessorKey: 'name', header: '名称', width: 180 }, { accessorKey: 'code', header: '编码', width: 180 },
  { accessorKey: 'description', header: '描述', width: 280 }, { id: 'plugins', header: '授权插件', width: 240 },
  { id: 'status', header: '状态', width: 100 }, { id: 'operation', header: '操作', width: 280, fixed: 'right' },
])
const pluginOptions = computed(() => plugins.value.map(plugin => ({
  value: plugin.code,
  label: `${plugin.name}（${plugin.code}，${pluginStatusText(plugin.status)}）`,
})))
onMounted(() => { void load(); void loadPlugins() })
async function load() { loading.value = true; try { const res = await apiGraph.pageTables({ page: pagination.page, size: pagination.size, keyword: keyword.value || undefined }); rows.value = res.data.records; pagination.total = res.data.total } finally { loading.value = false } }
async function loadPlugins() { try { plugins.value = (await apiPlugin.list()).data } catch {} }
function openCreate() { editing.value = undefined; Object.assign(form, { name: '', code: '', description: '', status: 'ACTIVE', authorizedPluginCodes: [] }); formVisible.value = true }
function openEdit(table: GraphTable) { editing.value = table; Object.assign(form, { name: table.name, code: table.code, description: table.description || '', status: table.status, authorizedPluginCodes: table.authorizedPluginCodes || [] }); formVisible.value = true }
async function save() { if (editing.value) await apiGraph.updateTable(editing.value.id, form); else await apiGraph.createTable(form); toast.success('逻辑图表已保存'); formVisible.value = false; await load() }
function toggle(table: GraphTable) { modal.confirm({ title: table.status === 'ACTIVE' ? '确认停用' : '确认启用', content: `确认${table.status === 'ACTIVE' ? '停用' : '启用'}逻辑图表「${table.name}」吗？`, onConfirm: async () => { if (table.status === 'ACTIVE') await apiGraph.disableTable(table.id); else await apiGraph.enableTable(table.id); await load() } }) }
async function test(table: GraphTable) { const res = await apiGraph.testTable(table.id); res.data.status === 'SUCCESS' ? toast.success('部署连接诊断成功') : toast.error('部署连接诊断失败', { description: res.data.errorMessage }) }
function statusText(status: GraphTableStatus) { return status === 'ACTIVE' ? '启用' : '停用' }
function pluginStatusText(status: PluginModule['status']) { return { INSTALLED: '已安装', LOADED: '已加载', ENABLED: '运行中', DISABLED: '已禁用', ERROR: '异常' }[status] }
</script>
<template>
  <div><FaPageHeader title="逻辑图表"><FaButton v-auth="'platform:graph:edit'" @click="openCreate"><FaIcon name="i-ri:add-line" />新增图表</FaButton></FaPageHeader>
    <FaPageMain><FaCard class="mb-3" title="Neo4j 逻辑隔离"><p class="text-sm text-secondary-foreground/70">Community Edition 使用部署配置提供的单一物理 Neo4j 库。逻辑图表按编码隔离 Wiki 与插件数据，页面不保存 URI、账号或密码。</p></FaCard>
      <FaResponsiveTable v-loading="loading" :columns="columns" :data="rows" row-key="id" border stripe><template #toolbar><FaSearchBar><div class="flex gap-2"><FaInput v-model="keyword" clearable placeholder="名称 / 编码 / 描述" @keydown.enter="load" /><FaButton @click="load">筛选</FaButton></div></FaSearchBar></template>
        <template #cell-plugins="{ row }"><span>{{ row.original.authorizedPluginCodes?.join('、') || '-' }}</span></template><template #cell-status="{ row }"><FaTag :variant="row.original.status === 'ACTIVE' ? 'default' : 'secondary'">{{ statusText(row.original.status) }}</FaTag></template><template #cell-operation="{ row }"><div class="flex justify-center gap-2"><FaButton size="sm" variant="outline" :disabled="row.original.status !== 'ACTIVE'" @click="test(row.original)">诊断</FaButton><FaButton size="sm" variant="ghost" @click="openEdit(row.original)">编辑</FaButton><FaButton size="sm" variant="ghost" @click="toggle(row.original)">{{ row.original.status === 'ACTIVE' ? '停用' : '启用' }}</FaButton></div></template></FaResponsiveTable>
      <FaPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" class="mt-3" @page-change="load" @size-change="load" />
    </FaPageMain><FaModal v-model="formVisible" :title="editing ? '编辑逻辑图表' : '新增逻辑图表'" show-cancel-button @confirm="save"><a-form :model="form" layout="vertical"><a-form-item label="名称" required><FaInput v-model="form.name" /></a-form-item><a-form-item label="编码" required><FaInput v-model="form.code" :disabled="!!editing" /></a-form-item><a-form-item label="描述"><FaTextarea v-model="form.description" :rows="3" /></a-form-item><a-form-item label="授权插件"><FaSelect v-model="form.authorizedPluginCodes" :options="pluginOptions" multiple allow-search placeholder="请选择授权插件" /></a-form-item><a-form-item label="状态"><FaSelect v-model="form.status" :options="[{ label: '启用', value: 'ACTIVE' }, { label: '停用', value: 'DISABLED' }]" /></a-form-item></a-form></FaModal>
  </div>
</template>

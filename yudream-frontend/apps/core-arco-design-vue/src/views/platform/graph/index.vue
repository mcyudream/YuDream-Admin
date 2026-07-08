<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { GraphConnection, GraphConnectionPayload, GraphConnectionStatus, GraphQueryLog, GraphQueryStatus } from '@/api/modules/platform-graph'
import apiGraph from '@/api/modules/platform-graph'

const modal = useFaModal()
const toast = useFaToast()

const activeTab = ref<'connections' | 'logs'>('connections')
const loading = ref(false)
const actionLoading = ref('')
const connectionRows = ref<GraphConnection[]>([])
const logRows = ref<GraphQueryLog[]>([])
const search = reactive({ keyword: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const formVisible = ref(false)
const editing = ref<GraphConnection | null>(null)
const form = reactive<GraphConnectionPayload>({
  name: '',
  code: '',
  uri: 'bolt://localhost:7687',
  username: 'neo4j',
  password: '',
  database: 'neo4j',
  status: 'ACTIVE',
})

const queryVisible = ref(false)
const querying = ref<GraphConnection | null>(null)
const queryForm = reactive({
  cypher: 'MATCH (n) RETURN n LIMIT 25',
  params: '{}',
})
const lastLog = ref<GraphQueryLog | null>(null)

const statusOptions: { label: string, value: GraphConnectionStatus }[] = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
]
const tabs = [
  { key: 'connections', label: '连接管理', icon: 'i-ri:share-circle-line' },
  { key: 'logs', label: '查询日志', icon: 'i-ri:file-list-3-line' },
] as const

const connectionColumns = computed<TableColumn<GraphConnection>[]>(() => [
  { accessorKey: 'name', header: '名称', width: 180, fixed: 'left' },
  { accessorKey: 'code', header: '编码', width: 160 },
  { accessorKey: 'uri', header: '连接地址', width: 240 },
  { accessorKey: 'username', header: '用户', width: 120 },
  { accessorKey: 'database', header: '数据库', width: 120 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { id: 'updateTime', header: '更新时间', width: 180 },
  { id: 'operation', header: '操作', width: 300, align: 'center', fixed: 'right' },
])

const logColumns = computed<TableColumn<GraphQueryLog>[]>(() => [
  { accessorKey: 'connectionCode', header: '连接', width: 160, fixed: 'left' },
  { accessorKey: 'cypher', header: 'Cypher', width: 420 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { accessorKey: 'summary', header: '摘要', width: 140 },
  { accessorKey: 'durationMillis', header: '耗时', width: 100, align: 'right' },
  { id: 'executedAt', header: '执行时间', width: 180 },
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
    if (activeTab.value === 'connections') {
      const res = await apiGraph.pageConnections(params)
      connectionRows.value = res.data.records
      pagination.total = res.data.total
    }
    else {
      const res = await apiGraph.pageLogs(params)
      logRows.value = res.data.records
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

function openCreate() {
  editing.value = null
  Object.assign(form, {
    name: '',
    code: '',
    uri: 'bolt://localhost:7687',
    username: 'neo4j',
    password: '',
    database: 'neo4j',
    status: 'ACTIVE' as GraphConnectionStatus,
  })
  formVisible.value = true
}

function openEdit(row: GraphConnection) {
  editing.value = row
  Object.assign(form, {
    name: row.name,
    code: row.code,
    uri: row.uri,
    username: row.username,
    password: '',
    database: row.database || 'neo4j',
    status: row.status,
  })
  formVisible.value = true
}

async function saveForm() {
  if (editing.value) {
    await apiGraph.updateConnection(editing.value.id, form)
  }
  else {
    await apiGraph.createConnection(form)
  }
  toast.success('图数据库连接已保存')
  formVisible.value = false
  await load()
}

function confirmDisable(row: GraphConnection) {
  modal.confirm({
    title: '确认停用',
    content: `确认停用图数据库连接「${row.name}」吗？`,
    onConfirm: async () => {
      await apiGraph.disableConnection(row.id)
      toast.success('图数据库连接已停用')
      await load()
    },
  })
}

async function testConnection(row: GraphConnection) {
  actionLoading.value = `${row.id}:test`
  try {
    const res = await apiGraph.testConnection(row.id)
    if (res.data.status === 'SUCCESS') {
      toast.success('连接测试成功', { description: `${res.data.durationMillis}ms` })
    }
    else {
      toast.error('连接测试失败', { description: res.data.errorMessage })
    }
  }
  finally {
    actionLoading.value = ''
  }
}

function openQuery(row: GraphConnection) {
  querying.value = row
  queryForm.cypher = 'MATCH (n) RETURN n LIMIT 25'
  queryForm.params = '{}'
  lastLog.value = null
  queryVisible.value = true
}

async function runQuery() {
  if (!querying.value) {
    return
  }
  const params = parseJsonObject(queryForm.params, '查询参数')
  if (!params) {
    return
  }
  actionLoading.value = 'query'
  try {
    const res = await apiGraph.query(querying.value.id, {
      cypher: queryForm.cypher,
      params,
    })
    lastLog.value = res.data
    if (res.data.status === 'SUCCESS') {
      toast.success('Cypher 执行完成', { description: `${res.data.rows?.length || 0} 行 / ${res.data.durationMillis}ms` })
    }
    else {
      toast.error('Cypher 执行失败', { description: res.data.errorMessage })
    }
    await load()
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
    return parsed as Record<string, any>
  }
  catch {
    toast.error(`${label}格式错误`, { description: '请输入合法 JSON 对象' })
    return null
  }
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function statusText(status: GraphConnectionStatus) {
  return status === 'ACTIVE' ? '启用' : '停用'
}

function statusVariant(status: GraphConnectionStatus) {
  return status === 'ACTIVE' ? 'default' : 'secondary'
}

function queryStatusText(status: GraphQueryStatus) {
  return status === 'SUCCESS' ? '成功' : '失败'
}

function queryStatusVariant(status: GraphQueryStatus) {
  return status === 'SUCCESS' ? 'default' : 'destructive'
}
</script>

<template>
  <div>
    <FaPageHeader title="图数据库" class="mb-0">
      <FaButton v-if="activeTab === 'connections'" v-auth="'platform:graph:edit'" @click="openCreate">
        <FaIcon name="i-ri:add-line" />
        新增连接
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div class="graph-tabs">
        <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
          <FaIcon :name="tab.icon" />
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <FaSearchBar>
        <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
          <FaInput v-model="search.keyword" clearable placeholder="名称 / 编码 / 地址 / Cypher" @keydown.enter="load" @clear="load" />
          <div class="flex gap-2 md:justify-end">
            <FaButton variant="outline" @click="resetSearch">重置</FaButton>
            <FaButton :loading="loading" @click="load">
              <FaIcon name="i-ri:search-line" />
              筛选
            </FaButton>
          </div>
        </div>
      </FaSearchBar>

      <div class="mx--4 my-3 border-t border-t-dashed" />

      <FaTable
        v-if="activeTab === 'connections'"
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1280px]"
        border
        stripe
        column-visibility
        :columns="connectionColumns"
        :data="connectionRows"
      >
        <template #cell-status="{ row }">
          <FaTag :variant="statusVariant(row.original.status)">{{ statusText(row.original.status) }}</FaTag>
        </template>
        <template #cell-updateTime="{ row }">
          {{ dateText(row.original.updateTime) }}
        </template>
        <template #cell-operation="{ row }">
          <div class="table-actions">
            <FaButton
              v-auth="'platform:graph:query'"
              size="sm"
              variant="outline"
              :disabled="row.original.status !== 'ACTIVE'"
              :loading="actionLoading === `${row.original.id}:test`"
              @click="testConnection(row.original)"
            >
              测试
            </FaButton>
            <FaButton v-auth="'platform:graph:query'" size="sm" variant="outline" :disabled="row.original.status !== 'ACTIVE'" @click="openQuery(row.original)">
              查询
            </FaButton>
            <FaButton v-auth="'platform:graph:edit'" size="sm" variant="ghost" @click="openEdit(row.original)">
              编辑
            </FaButton>
            <FaButton v-auth="'platform:graph:edit'" size="sm" variant="ghost" :disabled="row.original.status !== 'ACTIVE'" @click="confirmDisable(row.original)">
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
        table-class="min-w-[1260px]"
        border
        stripe
        column-visibility
        :columns="logColumns"
        :data="logRows"
      >
        <template #cell-status="{ row }">
          <FaTag :variant="queryStatusVariant(row.original.status)">{{ queryStatusText(row.original.status) }}</FaTag>
        </template>
        <template #cell-executedAt="{ row }">
          {{ dateText(row.original.executedAt) }}
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

    <FaModal v-model="formVisible" :title="editing ? '编辑图数据库连接' : '新增图数据库连接'" show-cancel-button class="sm:max-w-3xl" @confirm="saveForm">
      <a-form :model="form" layout="vertical">
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <a-form-item label="名称" required>
            <FaInput v-model="form.name" />
          </a-form-item>
          <a-form-item label="编码" required>
            <FaInput v-model="form.code" :disabled="!!editing" />
          </a-form-item>
          <a-form-item label="连接地址" required>
            <FaInput v-model="form.uri" placeholder="bolt://localhost:7687" />
          </a-form-item>
          <a-form-item label="数据库">
            <FaInput v-model="form.database" placeholder="neo4j" />
          </a-form-item>
          <a-form-item label="用户名" required>
            <FaInput v-model="form.username" />
          </a-form-item>
          <a-form-item label="密码" :required="!editing">
            <FaInput v-model="form.password" type="password" :placeholder="editing ? '留空则不修改' : '请输入密码'" />
          </a-form-item>
          <a-form-item label="状态">
            <FaSelect v-model="form.status" :options="statusOptions" />
          </a-form-item>
        </div>
      </a-form>
    </FaModal>

    <FaModal v-model="queryVisible" title="执行 Cypher 查询" show-cancel-button class="sm:max-w-5xl" :confirm-loading="actionLoading === 'query'" @confirm="runQuery">
      <div class="query-grid">
        <a-form :model="{}" layout="vertical">
          <a-form-item label="Cypher">
            <FaTextarea v-model="queryForm.cypher" rows="10" input-class="font-mono" />
          </a-form-item>
          <a-form-item label="参数 JSON">
            <FaTextarea v-model="queryForm.params" rows="6" input-class="font-mono" />
          </a-form-item>
        </a-form>
        <section class="result-panel">
          <div class="section-title">最近结果</div>
          <pre>{{ lastLog ? JSON.stringify(lastLog, null, 2) : '尚未执行' }}</pre>
        </section>
      </div>
    </FaModal>
  </div>
</template>

<style scoped>
.graph-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.graph-tabs button {
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

.graph-tabs button.active,
.graph-tabs button:hover {
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
}

.table-actions {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}

.query-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 420px);
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

.result-panel pre {
  max-height: 520px;
  min-height: 340px;
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
  .query-grid {
    grid-template-columns: 1fr;
  }
}
</style>

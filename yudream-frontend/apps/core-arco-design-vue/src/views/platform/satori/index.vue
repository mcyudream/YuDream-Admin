<script setup lang="ts">
import type { SatoriConnection, SatoriConnectionPayload, SatoriOperationLog } from '@/api/modules/platform-satori'
import apiSatori from '@/api/modules/platform-satori'

const toast = useFaToast()
const modal = useFaModal()
const loading = ref(false)
const actionKey = ref('')
const rows = ref<SatoriConnection[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const keyword = ref('')
const composerMode = ref<'TEXT' | 'MARKDOWN' | 'HTML'>('MARKDOWN')
const previewUrl = ref('')
const formVisible = ref(false)
const editing = ref<SatoriConnection | null>(null)
const logVisible = ref(false)
const logLoading = ref(false)
const logConnection = ref<SatoriConnection | null>(null)
const logs = ref<SatoriOperationLog[]>([])
const form = reactive<SatoriConnectionPayload>({ name: '', baseUrl: 'http://localhost:5500', platform: '', userId: '', token: '' })
const composer = reactive({ content: '# 消息预览\n\n支持 **Markdown** 与 HTML 渲染。', width: 720, transparent: false })

const columns = [
  { accessorKey: 'name', header: '连接名称', width: 190 },
  { accessorKey: 'baseUrl', header: 'Satori 地址', width: 300 },
  { accessorKey: 'platform', header: '平台', width: 120 },
  { accessorKey: 'userId', header: '机器人 ID', width: 160 },
  { id: 'credential', header: '凭证', width: 100 },
  { id: 'status', header: '状态', width: 100 },
  { id: 'updated', header: '更新时间', width: 180 },
  { id: 'actions', header: '操作', width: 280, fixed: 'right' },
]

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await apiSatori.pageConnections({ page: pagination.page, size: pagination.size, keyword: keyword.value || undefined })
    rows.value = res.data.records
    pagination.total = res.data.total
  }
  finally { loading.value = false }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', baseUrl: 'http://localhost:5500', platform: '', userId: '', token: '' })
  formVisible.value = true
}

function openEdit(row: SatoriConnection) {
  editing.value = row
  Object.assign(form, { name: row.name, baseUrl: row.baseUrl, platform: row.platform, userId: row.userId, token: '' })
  formVisible.value = true
}

async function save() {
  if (editing.value)
    await apiSatori.updateConnection(editing.value.id, form)
  else
    await apiSatori.createConnection(form)
  formVisible.value = false
  toast.success('连接已保存')
  await load()
}

async function toggle(row: SatoriConnection) {
  actionKey.value = `${row.id}:toggle`
  try {
    if (row.enabled) await apiSatori.disableConnection(row.id)
    else await apiSatori.enableConnection(row.id)
    await load()
  }
  finally { actionKey.value = '' }
}

async function test(row: SatoriConnection) {
  actionKey.value = `${row.id}:test`
  try {
    const result = await apiSatori.testConnection(row.id)
    toast.success('连接可用', { description: `${result.data.platform}:${result.data.userId} / ${result.data.status || result.data.adapter || 'ready'}` })
  }
  finally { actionKey.value = '' }
}

async function openLogs(row: SatoriConnection) {
  logConnection.value = row
  logVisible.value = true
  logLoading.value = true
  try {
    const result = await apiSatori.pageConnectionLogs(row.id, { page: 1, size: 100 })
    logs.value = result.data.records
  }
  finally { logLoading.value = false }
}

async function renderPreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  const response = await apiSatori.render({
    sourceType: composerMode.value === 'TEXT' ? 'MARKDOWN' : composerMode.value,
    content: composerMode.value === 'TEXT' ? composer.content : composer.content,
    width: composer.width,
    transparent: composer.transparent,
  })
  previewUrl.value = URL.createObjectURL(response)
}

function confirmToggle(row: SatoriConnection) {
  modal.confirm({ title: row.enabled ? '停用连接' : '启用连接', content: `确认${row.enabled ? '停用' : '启用'} ${row.name}？`, onConfirm: () => toggle(row) })
}

function dateText(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }
</script>

<template>
  <div>
    <FaPageHeader title="Satori 消息平台">
      <FaButton v-auth="'platform:satori:config'" @click="openCreate"><FaIcon name="i-ri:add-line" />新增连接</FaButton>
    </FaPageHeader>
    <FaPageMain>
      <div class="console-grid">
        <section class="workspace">
          <FaTable v-loading="loading" :columns="columns" :data="rows" row-key="id" border stripe table-class="min-w-[1080px]">
            <template #toolbar>
              <FaSearchBar class="w-full"><div class="flex gap-2"><FaInput v-model="keyword" clearable placeholder="名称或地址" @keydown.enter="load" /><FaButton @click="load"><FaIcon name="i-ri:search-line" />筛选</FaButton></div></FaSearchBar>
            </template>
            <template #cell-credential="{ row }"><FaTag :variant="row.original.credentialConfigured ? 'default' : 'secondary'">{{ row.original.credentialConfigured ? '已配置' : '未配置' }}</FaTag></template>
            <template #cell-status="{ row }"><FaTag :variant="row.original.enabled ? 'default' : 'secondary'">{{ row.original.enabled ? '已启用' : '已停用' }}</FaTag></template>
            <template #cell-updated="{ row }">{{ dateText(row.original.updateTime) }}</template>
            <template #cell-actions="{ row }"><div class="actions"><FaButton size="sm" variant="ghost" @click="openLogs(row.original)"><FaIcon name="i-ri:file-list-3-line" /></FaButton><FaButton size="sm" variant="outline" :loading="actionKey === `${row.original.id}:test`" @click="test(row.original)"><FaIcon name="i-ri:plug-line" /></FaButton><FaButton size="sm" variant="ghost" @click="openEdit(row.original)"><FaIcon name="i-ri:edit-line" /></FaButton><FaButton size="sm" :loading="actionKey === `${row.original.id}:toggle`" :variant="row.original.enabled ? 'ghost' : 'outline'" @click="confirmToggle(row.original)"><FaIcon :name="row.original.enabled ? 'i-ri:pause-line' : 'i-ri:play-line'" /></FaButton></div></template>
          </FaTable>
          <FaPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" class="mt-3" @page-change="load" @size-change="load" />
        </section>
        <section class="composer">
          <div class="composer-head"><h2>消息渲染</h2><div class="segmented"><button v-for="mode in ['TEXT', 'MARKDOWN', 'HTML']" :key="mode" :class="{ active: composerMode === mode }" @click="composerMode = mode as typeof composerMode">{{ mode }}</button></div></div>
          <FaTextarea v-model="composer.content" :rows="12" input-class="font-mono" />
          <div class="render-controls"><a-form-item label="宽度"><a-input-number v-model="composer.width" :min="320" :max="1600" /></a-form-item><a-checkbox v-model="composer.transparent">透明背景</a-checkbox><FaButton v-auth="'platform:render:use'" @click="renderPreview"><FaIcon name="i-ri:image-line" />渲染</FaButton></div>
          <div class="preview"><img v-if="previewUrl" :src="previewUrl" alt="消息渲染预览"><span v-else>暂无预览</span></div>
        </section>
      </div>
    </FaPageMain>
    <FaModal v-model="formVisible" :title="editing ? '编辑 Satori 连接' : '新增 Satori 连接'" show-cancel-button @confirm="save"><a-form :model="form" layout="vertical"><a-form-item label="名称" required><FaInput v-model="form.name" /></a-form-item><a-form-item label="Satori 地址" required><FaInput v-model="form.baseUrl" placeholder="https://satori.example.com" /></a-form-item><a-form-item label="Satori Platform" required><FaInput v-model="form.platform" placeholder="例如 discord、qq" /></a-form-item><a-form-item label="Satori User ID" required><FaInput v-model="form.userId" placeholder="机器人自身账号 ID" /></a-form-item><a-form-item label="令牌" :required="!editing"><FaInput v-model="form.token" type="password" :placeholder="editing ? '留空保持原令牌' : 'Bearer Token'" /></a-form-item></a-form></FaModal>
    <FaModal v-model="logVisible" :title="`${logConnection?.name || 'Satori'} 运行日志`" :show-cancel-button="false" @confirm="logVisible = false"><div v-loading="logLoading" class="log-list"><div v-for="log in logs" :key="log.id" class="log-row"><FaTag :variant="log.level === 'ERROR' ? 'danger' : log.level === 'WARN' ? 'warning' : 'secondary'">{{ log.level }}</FaTag><span class="log-time">{{ dateText(log.occurredAt) }}</span><strong>{{ log.category }}/{{ log.action }}</strong><span>{{ log.detail || '-' }}</span></div><span v-if="!logLoading && !logs.length">暂无运行日志</span></div></FaModal>
  </div>
</template>

<style scoped>
.console-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(330px, 410px); gap: 16px; align-items: start; }
.workspace, .composer { min-width: 0; }
.composer { display: grid; gap: 12px; border: 1px solid var(--color-border-2); padding: 14px; border-radius: 6px; background: var(--color-bg-2); }
.composer-head, .render-controls, .actions { display: flex; gap: 8px; align-items: center; }
.log-list { display: grid; gap: 8px; max-height: 520px; overflow: auto; }.log-row { display: grid; grid-template-columns: auto 150px 160px minmax(0, 1fr); gap: 8px; align-items: center; padding: 8px; border: 1px solid var(--color-border-2); background: var(--color-fill-1); font-size: 12px; }.log-time { color: var(--color-text-3); }
.composer-head { justify-content: space-between; }.composer h2 { margin: 0; font-size: 16px; }.segmented { display: inline-flex; border: 1px solid var(--color-border-2); border-radius: 6px; overflow: hidden; }.segmented button { height: 28px; padding: 0 8px; border: 0; background: transparent; font-size: 11px; }.segmented button.active { background: rgb(var(--primary-6)); color: white; }.render-controls { flex-wrap: wrap; justify-content: space-between; }.render-controls :deep(.arco-form-item) { margin: 0; }.preview { display: grid; place-items: center; min-height: 180px; overflow: auto; border: 1px dashed var(--color-border-2); background: var(--color-fill-1); color: var(--color-text-3); }.preview img { display: block; max-width: 100%; height: auto; }.actions { justify-content: center; } @media (max-width: 1100px) { .console-grid { grid-template-columns: 1fr; } }
</style>

<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { MilkyConnection, MilkyConnectionPayload } from '@/api/modules/platform-milky'
import apiMilky from '@/api/modules/platform-milky'
import MilkyChatWorkspace from './components/MilkyChatWorkspace.vue'

const toast = useFaToast()
const loading = ref(false)
const formVisible = ref(false)
const rows = ref<MilkyConnection[]>([])
const editing = ref<MilkyConnection | null>(null)
const chatConnection = ref<MilkyConnection | null>(null)
const chatVisible = ref(false)
const page = reactive({ page: 1, size: 20, total: 0 })
const form = reactive<MilkyConnectionPayload>({ name: '', baseUrl: 'http://127.0.0.1:3010', token: '', commandMenuImageMode: 'base64', commandMenuPublicBaseUrl: '' })

const columns: TableColumn<MilkyConnection>[] = [
  { accessorKey: 'name', header: '连接名称', width: 180 },
  { accessorKey: 'baseUrl', header: 'Milky 服务地址', width: 320 },
  { id: 'status', header: '状态', width: 100 },
  { accessorKey: 'updateTime', header: '更新时间', width: 180 },
  { id: 'actions', header: '操作', width: 240, fixed: 'right' },
]

async function load() {
  loading.value = true
  try {
    const result = await apiMilky.page({ page: page.page, size: page.size })
    rows.value = result.data.records
    page.total = result.data.total
  }
  finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', baseUrl: 'http://127.0.0.1:3010', token: '', commandMenuImageMode: 'base64', commandMenuPublicBaseUrl: '' })
  formVisible.value = true
}

function openEdit(connection: MilkyConnection) {
  editing.value = connection
  Object.assign(form, { name: connection.name, baseUrl: connection.baseUrl, token: '', commandMenuImageMode: connection.commandMenuImageMode || 'base64', commandMenuPublicBaseUrl: connection.commandMenuPublicBaseUrl || '' })
  formVisible.value = true
}

async function save() {
  if (editing.value) {
    await apiMilky.update(editing.value.id, form)
    toast.success('Milky 连接已更新')
  }
  else {
    await apiMilky.create(form)
    toast.success('Milky 连接已创建')
  }
  formVisible.value = false
  await load()
}

async function toggle(connection: MilkyConnection) {
  if (connection.enabled) {
    await apiMilky.disable(connection.id)
    toast.success('连接已停用')
  }
  else {
    await apiMilky.enable(connection.id)
    toast.success('连接已启用')
  }
  await load()
}

async function test(connection: MilkyConnection) {
  const result = await apiMilky.test(connection.id)
  const name = String(result.data.nickname ?? result.data.user_name ?? result.data.user_id ?? '')
  toast.success(name ? `连接成功：${name}` : '连接测试成功')
}

function openChat(connection: MilkyConnection) {
  chatConnection.value = connection
  chatVisible.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <FaPageHeader title="Milky 消息平台">
      <FaButton v-auth="'platform:milky:config'" @click="openCreate">
        <FaIcon name="i-ri:add-line" />
        新增连接
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaResponsiveTable v-loading="loading" :columns="columns" :data="rows" row-key="id" border stripe>
        <template #cell-status="{ row }">
          <FaTag :variant="row.original.enabled ? 'default' : 'secondary'">
            {{ row.original.enabled ? '已启用' : '已停用' }}
          </FaTag>
        </template>
        <template #cell-actions="{ row }">
          <div class="flex items-center gap-1">
            <FaButton size="sm" variant="ghost" title="编辑" @click="openEdit(row.original)">
              <FaIcon name="i-ri:edit-line" />
            </FaButton>
            <FaButton size="sm" variant="ghost" title="测试连接" @click="test(row.original)">
              <FaIcon name="i-ri:radar-line" />
            </FaButton>
            <FaButton size="sm" variant="ghost" :title="row.original.enabled ? '停用' : '启用'" @click="toggle(row.original)">
              <FaIcon :name="row.original.enabled ? 'i-ri:pause-circle-line' : 'i-ri:play-circle-line'" />
            </FaButton>
            <FaButton size="sm" variant="ghost" :disabled="!row.original.enabled" title="WebQQ" @click="openChat(row.original)">
              <FaIcon name="i-ri:chat-3-line" />
            </FaButton>
          </div>
        </template>
        <template #card="{ row }">
          <FaCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 break-words text-base font-semibold">{{ row.name }}</span>
                <FaTag :variant="row.enabled ? 'default' : 'secondary'">
                  {{ row.enabled ? '已启用' : '已停用' }}
                </FaTag>
              </div>
              <div class="flex flex-col gap-1 text-sm">
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">服务地址</span>
                  <span class="break-all">{{ row.baseUrl }}</span>
                </div>
                <div v-if="row.updateTime" class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">更新时间</span>
                  <span>{{ row.updateTime }}</span>
                </div>
              </div>
              <div class="flex flex-wrap gap-2 border-t pt-3">
                <FaButton size="sm" variant="outline" @click="openEdit(row)">
                  编辑
                </FaButton>
                <FaButton size="sm" variant="outline" @click="test(row)">
                  测试
                </FaButton>
                <FaButton size="sm" variant="outline" @click="toggle(row)">
                  {{ row.enabled ? '停用' : '启用' }}
                </FaButton>
                <FaButton size="sm" variant="ghost" :disabled="!row.enabled" @click="openChat(row)">
                  WebQQ
                </FaButton>
              </div>
            </div>
          </FaCard>
        </template>
      </FaResponsiveTable>
      <FaPagination v-model:page="page.page" v-model:size="page.size" :total="page.total" class="mt-3" @page-change="load" @size-change="load" />
    </FaPageMain>

    <FaModal v-model="formVisible" :title="editing ? '编辑 Milky 连接' : '新增 Milky 连接'" show-cancel-button @confirm="save">
      <a-form :model="form" layout="vertical">
        <a-form-item label="名称" required>
          <FaInput v-model="form.name" />
        </a-form-item>
        <a-form-item label="Milky HTTP 地址" required>
          <FaInput v-model="form.baseUrl" placeholder="http://127.0.0.1:3010" />
        </a-form-item>
        <a-form-item :label="editing ? 'Access Token（留空不修改）' : 'Access Token'" :required="!editing">
          <FaInput v-model="form.token" type="password" />
        </a-form-item>
        <a-form-item label="指令菜单图片格式">
          <FaSelect
            v-model="form.commandMenuImageMode"
            :options="[
              { label: 'Base64（开发环境推荐）', value: 'base64' },
              { label: '公开链接（公网部署）', value: 'url' },
            ]"
          />
        </a-form-item>
        <a-form-item v-if="form.commandMenuImageMode === 'url'" label="公开访问基础地址">
          <FaInput v-model="form.commandMenuPublicBaseUrl" placeholder="https://admin.example.com" />
        </a-form-item>
      </a-form>
    </FaModal>

    <FaModal v-model="chatVisible" :title="`${chatConnection?.name || 'Milky'} WebQQ`" :show-cancel-button="false" class="sm:max-w-6xl">
      <MilkyChatWorkspace v-if="chatConnection" :connection-id="chatConnection.id" />
    </FaModal>
  </div>
</template>

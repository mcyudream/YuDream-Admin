<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { PluginMarketSource, PluginMarketSourcePayload } from '@/api/modules/platform-plugin-market-source'
import apiMarketSource from '@/api/modules/platform-plugin-market-source'

const modal = useFaModal()
const toast = useFaToast()

const loading = ref(false)
const syncing = ref(false)
const rows = ref<PluginMarketSource[]>([])

const formVisible = ref(false)
const formSaving = ref(false)
const testing = ref(false)
const testMessage = ref('')
const testOk = ref(false)
const editing = ref<PluginMarketSource | null>(null)
const form = reactive<PluginMarketSourcePayload>({
  code: '',
  name: '',
  rootUrl: '',
  token: '',
  sortOrder: 100,
})

const columns = computed<TableColumn<PluginMarketSource>[]>(() => [
  { accessorKey: 'name', header: '名称', width: 200, fixed: 'left' },
  { accessorKey: 'rootUrl', header: '源地址', width: 320 },
  { id: 'enabled', header: '状态', width: 90, align: 'center' },
  { id: 'sync', header: '同步', width: 170 },
  { accessorKey: 'pluginCount', header: '插件数', width: 90, align: 'right' },
  { accessorKey: 'sortOrder', header: '排序', width: 80, align: 'right' },
  { accessorKey: 'syncedAt', header: '同步时间', width: 180 },
  { id: 'operation', header: '操作', width: 210, align: 'center', fixed: 'right' },
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await apiMarketSource.list()
    rows.value = res.data
  }
  catch {
    toast.error('加载市场源列表失败')
  }
  finally {
    loading.value = false
  }
}

function openForm(row?: PluginMarketSource) {
  editing.value = row || null
  testMessage.value = ''
  Object.assign(form, row
    ? {
        code: row.code,
        name: row.name,
        rootUrl: row.rootUrl,
        token: '',
        sortOrder: row.sortOrder ?? 100,
      }
    : {
        code: '',
        name: '',
        rootUrl: '',
        token: '',
        sortOrder: 100,
      })
  formVisible.value = true
}

async function saveForm() {
  if (!editing.value && !form.code.trim()) {
    toast.error('请填写市场源标识')
    return
  }
  if (!editing.value?.builtIn && !form.rootUrl.trim()) {
    toast.error('请填写市场源地址')
    return
  }
  formSaving.value = true
  try {
    if (editing.value) {
      await apiMarketSource.update(editing.value.id, { ...form, code: undefined })
      toast.success('市场源已保存')
    }
    else {
      await apiMarketSource.create({ ...form })
      toast.success('市场源已添加，可点击同步拉取目录')
    }
    formVisible.value = false
    await load()
  }
  finally {
    formSaving.value = false
  }
}

async function testForm() {
  if (!form.rootUrl.trim()) {
    toast.error('请先填写市场源地址')
    return
  }
  testing.value = true
  testMessage.value = ''
  try {
    const res = await apiMarketSource.test({ rootUrl: form.rootUrl, token: form.token || undefined })
    testOk.value = res.data.ok
    testMessage.value = res.data.message || (res.data.ok ? '连接成功' : '连接失败')
  }
  catch {
    testOk.value = false
    testMessage.value = '测试请求失败，请检查地址与网络'
  }
  finally {
    testing.value = false
  }
}

function confirmToggle(row: PluginMarketSource) {
  const enabling = !row.enabled
  modal.confirm({
    title: enabling ? '确认启用' : '确认禁用',
    content: `确认${enabling ? '启用' : '禁用'}市场源「${row.name}」吗？${enabling ? '' : '禁用后市场列表不再展示该源插件。'}`,
    onConfirm: async () => {
      await (enabling ? apiMarketSource.enable(row.id) : apiMarketSource.disable(row.id))
      toast.success(enabling ? '已启用' : '已禁用')
      await load()
    },
  })
}

function confirmSync(row: PluginMarketSource) {
  modal.confirm({
    title: '确认同步',
    content: `确认拉取市场源「${row.name}」的最新目录吗？`,
    onConfirm: async () => {
      await apiMarketSource.sync(row.id)
      toast.success('同步完成')
      await load()
    },
  })
}

function confirmSyncAll() {
  modal.confirm({
    title: '确认全部同步',
    content: '确认拉取全部已启用市场源的最新目录吗？视源数量可能需要一些时间。',
    onConfirm: async () => {
      syncing.value = true
      try {
        await apiMarketSource.syncAll()
        toast.success('同步完成')
        await load()
      }
      finally {
        syncing.value = false
      }
    },
  })
}

function confirmRemove(row: PluginMarketSource) {
  modal.confirm({
    title: '确认删除',
    content: `确认删除市场源「${row.name}」吗？其目录快照会一并删除，已安装插件不受影响。`,
    onConfirm: async () => {
      await apiMarketSource.remove(row.id)
      toast.success('已删除')
      await load()
    },
  })
}

function syncVariant(row: PluginMarketSource) {
  if (!row.syncedAt) {
    return 'secondary'
  }
  return row.syncStatus === 'OK' ? 'default' : 'destructive'
}

function syncText(row: PluginMarketSource) {
  if (!row.syncedAt) {
    return '未同步'
  }
  return row.syncStatus === 'OK' ? '正常' : '异常'
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}
</script>

<template>
  <FaPageHeader title="市场源管理" class="mb-0">
    <template #description>
      管理插件市场的订阅来源：添加多个自托管或官方市场源，市场页会合并展示各源插件。内置官方源地址由系统配置管理。
    </template>
  </FaPageHeader>
  <FaPageMain>
    <FaResponsiveTable
      v-loading="loading"
      row-key="id"
      table-root-class="rounded-lg overflow-hidden"
      table-class="min-w-[1080px]"
      border
      stripe
      :columns="columns"
      :data="rows"
    >
      <template #toolbar>
        <div class="flex flex-wrap items-center justify-end gap-2">
          <FaButton v-auth="'platform:plugin-market-source:run'" variant="outline" :loading="syncing" @click="confirmSyncAll">
            <FaIcon name="i-ri:refresh-line" />
            全部同步
          </FaButton>
          <FaButton v-auth="'platform:plugin-market-source:create'" @click="openForm()">
            <FaIcon name="i-ri:add-line" />
            添加市场源
          </FaButton>
        </div>
      </template>
      <template #cell-name="{ row }">
        <div class="flex flex-col gap-1">
          <div class="flex items-center gap-2">
            <span class="font-medium">{{ row.original.name }}</span>
            <FaTag v-if="row.original.builtIn" variant="secondary">内置</FaTag>
          </div>
          <span class="text-xs text-secondary-foreground/60">{{ row.original.code }}</span>
        </div>
      </template>
      <template #cell-rootUrl="{ row }">
        <span class="break-all text-sm">{{ row.original.rootUrl }}</span>
        <div v-if="row.original.tokenConfigured" class="text-xs text-secondary-foreground/60">
          <FaIcon name="i-ri:key-2-line" />
          已配置访问令牌
        </div>
      </template>
      <template #cell-enabled="{ row }">
        <FaTag :variant="row.original.enabled ? 'default' : 'secondary'">
          {{ row.original.enabled ? '启用' : '禁用' }}
        </FaTag>
      </template>
      <template #cell-sync="{ row }">
        <FaTag :variant="syncVariant(row.original)">{{ syncText(row.original) }}</FaTag>
        <div v-if="row.original.syncErrorMessage" class="mt-1 flex max-w-[200px] items-center gap-1" :title="row.original.syncErrorMessage">
          <FaTag variant="destructive">失败</FaTag>
          <span class="truncate text-xs text-secondary-foreground/60">{{ row.original.syncErrorMessage }}</span>
        </div>
      </template>
      <template #cell-operation="{ row }">
        <div class="flex justify-center gap-2">
          <FaButton v-auth="'platform:plugin-market-source:run'" variant="outline" size="sm" @click="confirmSync(row.original)">
            同步
          </FaButton>
          <FaButton v-auth="'platform:plugin-market-source:edit'" variant="outline" size="sm" @click="confirmToggle(row.original)">
            {{ row.original.enabled ? '禁用' : '启用' }}
          </FaButton>
          <FaButton v-auth="'platform:plugin-market-source:edit'" variant="link" size="sm" @click="openForm(row.original)">
            编辑
          </FaButton>
          <FaButton v-if="!row.original.builtIn" v-auth="'platform:plugin-market-source:delete'" variant="destructive" size="sm" @click="confirmRemove(row.original)">
            删除
          </FaButton>
        </div>
      </template>

      <template #card="{ row }">
        <FaCard class="w-full">
          <div class="flex flex-col gap-3">
            <div class="flex items-center justify-between gap-2">
              <span class="text-base font-semibold break-all">{{ row.name }}</span>
              <FaTag :variant="row.enabled ? 'default' : 'secondary'">
                {{ row.enabled ? '启用' : '禁用' }}
              </FaTag>
            </div>
            <div class="flex flex-col gap-1 text-sm">
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">标识</span>
                <span class="break-all">{{ row.code }}<template v-if="row.builtIn">（内置）</template></span>
              </div>
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">地址</span>
                <span class="break-all">{{ row.rootUrl }}</span>
              </div>
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">同步</span>
                <span>{{ syncText(row) }} · {{ formatTime(row.syncedAt) }}</span>
              </div>
              <div v-if="row.syncErrorMessage" class="flex items-center gap-1">
                <FaTag variant="destructive">失败</FaTag>
                <span class="break-all text-xs text-secondary-foreground/60">{{ row.syncErrorMessage }}</span>
              </div>
            </div>
            <div class="flex flex-wrap gap-2 border-t pt-3">
              <FaButton v-auth="'platform:plugin-market-source:run'" variant="outline" size="sm" @click="confirmSync(row)">
                同步
              </FaButton>
              <FaButton v-auth="'platform:plugin-market-source:edit'" variant="outline" size="sm" @click="confirmToggle(row)">
                {{ row.enabled ? '禁用' : '启用' }}
              </FaButton>
              <FaButton v-auth="'platform:plugin-market-source:edit'" variant="link" size="sm" @click="openForm(row)">
                编辑
              </FaButton>
              <FaButton v-if="!row.builtIn" v-auth="'platform:plugin-market-source:delete'" variant="destructive" size="sm" @click="confirmRemove(row)">
                删除
              </FaButton>
            </div>
          </div>
        </FaCard>
      </template>
    </FaResponsiveTable>

    <FaModal
      v-model="formVisible"
      :title="editing ? '编辑市场源' : '添加市场源'"
      show-cancel-button
      class="sm:max-w-2xl"
      :confirm-loading="formSaving"
      @confirm="saveForm"
    >
      <a-form :model="form" layout="vertical">
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <a-form-item label="市场源标识">
            <FaInput v-model="form.code" :disabled="!!editing" placeholder="小写字母、数字或连字符，如 company-mirror" />
          </a-form-item>
          <a-form-item label="名称">
            <FaInput v-model="form.name" placeholder="市场源显示名称" />
          </a-form-item>
        </div>
        <a-form-item label="源地址（index.json 完整地址）">
          <div class="flex gap-2">
            <FaInput v-model="form.rootUrl" :disabled="!!editing?.builtIn" placeholder="https://example.com/market/index.json" class="flex-1" />
            <FaButton variant="outline" :loading="testing" @click="testForm">
              测试连接
            </FaButton>
          </div>
          <div v-if="editing?.builtIn" class="mt-1 text-xs text-secondary-foreground/60">
            内置源地址由系统配置 yudream.platform.plugin.store-root-url 管理，此处不可修改。
          </div>
          <div v-if="testMessage" class="mt-1 flex items-center gap-1">
            <FaTag :variant="testOk ? 'default' : 'destructive'">{{ testOk ? '成功' : '失败' }}</FaTag>
            <span class="text-xs text-secondary-foreground/60">{{ testMessage }}</span>
          </div>
        </a-form-item>
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-[1fr_140px]">
          <a-form-item label="访问令牌（可选）">
            <FaInput v-model="form.token" type="password" :placeholder="editing?.tokenConfigured ? '已配置，留白表示保留' : '私有源的 Bearer 令牌'" />
          </a-form-item>
          <a-form-item label="排序">
            <a-input-number v-model="form.sortOrder" :min="0" class="w-full" />
          </a-form-item>
        </div>
      </a-form>
    </FaModal>
  </FaPageMain>
</template>

<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { PluginMarketPublication, PluginPublicationChannel, PluginPublicationStatus } from '@/api/modules/platform-plugin-market-source'
import { PLUGIN_PUBLICATION_STATUS_OPTIONS } from '@/api/modules/platform-plugin-market-source'
import apiMarketSource from '@/api/modules/platform-plugin-market-source'

const modal = useFaModal()
const toast = useFaToast()

const loading = ref(false)
const publications = ref<PluginMarketPublication[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const statusFilter = ref<PluginPublicationStatus | ''>('PENDING')
const reviewRequired = ref(true)
const reviewSaving = ref(false)
const actingPublicationId = ref('')

const statusOptions = computed(() => [
  { label: '全部状态', value: '' },
  ...PLUGIN_PUBLICATION_STATUS_OPTIONS,
])

const columns = computed<TableColumn<PluginMarketPublication>[]>(() => [
  { id: 'plugin', header: '插件', width: 240, fixed: 'left' },
  { accessorKey: 'pluginVersion', header: '版本', width: 110 },
  { id: 'channel', header: '通道', width: 100, align: 'center' },
  { accessorKey: 'category', header: '分类', width: 120 },
  { id: 'pubStatus', header: '状态', width: 110, align: 'center' },
  { accessorKey: 'createTime', header: '提交时间', width: 170 },
  { id: 'pubOperation', header: '操作', width: 280, align: 'center', fixed: 'right' },
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [pageRes, reviewRes] = await Promise.all([
      apiMarketSource.publications({
        status: statusFilter.value || undefined,
        page: pagination.page,
        size: pagination.size,
      }),
      apiMarketSource.reviewRequired(),
    ])
    publications.value = pageRes.data.records || []
    pagination.total = pageRes.data.total || 0
    reviewRequired.value = reviewRes.data
  }
  catch {
    toast.error('加载审核列表失败')
  }
  finally {
    loading.value = false
  }
}

function onPageChange() {
  void load()
}

function onSizeChange() {
  pagination.page = 1
  void load()
}

function onStatusChange() {
  pagination.page = 1
  void load()
}

async function toggleReviewRequired(value: boolean | undefined) {
  reviewSaving.value = true
  try {
    const res = await apiMarketSource.updateReviewRequired(value === true)
    reviewRequired.value = res.data
    toast.success(res.data ? '发布需审核已开启' : '发布需审核已关闭，新发布直接对外可见')
  }
  finally {
    reviewSaving.value = false
  }
}

function confirmAccept(row: PluginMarketPublication) {
  modal.confirm({
    title: '通过发布',
    content: `确认发布 ${row.code}@${row.pluginVersion} 吗？通过后立即对外可见。`,
    onConfirm: () => actOnPublication(row, api => api.acceptPublication(row.id), '已通过审核'),
  })
}

function confirmReject(row: PluginMarketPublication) {
  modal.confirm({
    title: '拒绝发布',
    content: `确认拒绝 ${row.code}@${row.pluginVersion} 吗？同版本不可重新上传，需发布新版本。`,
    onConfirm: () => actOnPublication(row, api => api.rejectPublication(row.id), '已拒绝'),
  })
}

function confirmUnpublish(row: PluginMarketPublication) {
  modal.confirm({
    title: '确认下架',
    content: `确认下架 ${row.code}@${row.pluginVersion} 吗？订阅方将不再拉取到该版本，文件保留备查。`,
    onConfirm: () => actOnPublication(row, api => api.unpublishPublication(row.id), '已下架'),
  })
}

function confirmDelete(row: PluginMarketPublication) {
  modal.confirm({
    title: '确认删除',
    content: `确认永久删除 ${row.code}@${row.pluginVersion} 吗？发布记录与 JAR 都会移除，不可恢复。`,
    onConfirm: () => actOnPublication(row, api => api.deletePublication(row.id), '已删除'),
  })
}

async function actOnPublication(row: PluginMarketPublication, action: (api: typeof apiMarketSource) => Promise<unknown>, success: string) {
  actingPublicationId.value = row.id
  try {
    await action(apiMarketSource)
    toast.success(success)
    await load()
  }
  finally {
    actingPublicationId.value = ''
  }
}

function publicationStatusVariant(status: PluginPublicationStatus) {
  return status === 'PUBLISHED' ? 'default' : status === 'PENDING' ? 'secondary' : 'destructive'
}

function publicationStatusText(status: PluginPublicationStatus) {
  return PLUGIN_PUBLICATION_STATUS_OPTIONS.find(item => item.value === status)?.label || status
}

function channelText(channel: PluginPublicationChannel) {
  return channel === 'PIPELINE' ? '流水线' : '界面上传'
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}
</script>

<template>
  <div>
    <FaPageHeader title="发布审核" class="mb-0">
      <template #description>
        审核本机市场源的待发布插件。通过后对外可见；拒绝与下架后同版本不可覆盖。
      </template>
    </FaPageHeader>
    <FaPageMain>
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3">
        <div class="text-sm">
          <div class="font-medium">发布需审核</div>
          <div class="mt-1 text-xs text-secondary-foreground/60">
            开启后新上传进入待审核；关闭后界面与流水线发布直接对外可见。持有跳过审核权限的作者仍可直接发布。
          </div>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm text-secondary-foreground/60">{{ reviewRequired ? '已开启' : '已关闭' }}</span>
          <FaSwitch
            v-auth="'platform:plugin-market-source:edit'"
            :model-value="reviewRequired"
            :disabled="reviewSaving"
            @update:model-value="toggleReviewRequired"
          />
        </div>
      </div>

      <div class="mb-3 flex items-center gap-2">
        <FaSelect v-model="statusFilter" :options="statusOptions" class="w-40" @update:model-value="onStatusChange" />
      </div>

      <a-spin :loading="loading" class="block w-full">
        <FaResponsiveTable
          row-key="id"
          table-root-class="rounded-lg overflow-hidden"
          table-class="min-w-[980px]"
          border
          stripe
          :columns="columns"
          :data="publications"
        >
          <template #cell-plugin="{ row }">
            <div class="flex flex-col gap-1">
              <span class="font-medium">{{ row.original.displayName || row.original.code }}</span>
              <span class="text-xs text-secondary-foreground/60">{{ row.original.code }}</span>
            </div>
          </template>
          <template #cell-channel="{ row }">
            <FaTag variant="secondary">{{ channelText(row.original.channel) }}</FaTag>
          </template>
          <template #cell-pubStatus="{ row }">
            <FaTag :variant="publicationStatusVariant(row.original.status)">
              {{ publicationStatusText(row.original.status) }}
            </FaTag>
            <div v-if="row.original.reviewNote" class="mt-1 max-w-[160px] truncate text-xs text-secondary-foreground/60" :title="row.original.reviewNote">
              {{ row.original.reviewNote }}
            </div>
          </template>
          <template #cell-pubOperation="{ row }">
            <div class="flex justify-center gap-2">
              <template v-if="row.original.status === 'PENDING'">
                <FaButton v-auth="'platform:plugin-market-source:accept'" variant="outline" size="sm" :disabled="actingPublicationId === row.original.id" @click="confirmAccept(row.original)">
                  通过
                </FaButton>
                <FaButton v-auth="'platform:plugin-market-source:accept'" variant="destructive" size="sm" :disabled="actingPublicationId === row.original.id" @click="confirmReject(row.original)">
                  拒绝
                </FaButton>
              </template>
              <FaButton
                v-if="row.original.status === 'PUBLISHED'"
                v-auth="'platform:plugin-market-source:delete'"
                variant="destructive"
                size="sm"
                :disabled="actingPublicationId === row.original.id"
                @click="confirmUnpublish(row.original)"
              >
                下架
              </FaButton>
              <FaButton v-auth="'platform:plugin-market-source:delete'" variant="destructive" size="sm" :disabled="actingPublicationId === row.original.id" @click="confirmDelete(row.original)">
                删除
              </FaButton>
            </div>
          </template>

          <template #card="{ row }">
            <FaCard class="w-full">
              <div class="flex flex-col gap-3">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-base font-semibold break-all">{{ row.displayName || row.code }} {{ row.pluginVersion }}</span>
                  <FaTag :variant="publicationStatusVariant(row.status)">
                    {{ publicationStatusText(row.status) }}
                  </FaTag>
                </div>
                <div class="flex flex-col gap-1 text-sm">
                  <div class="flex gap-2">
                    <span class="shrink-0 text-secondary-foreground/60">通道</span>
                    <span>{{ channelText(row.channel) }}</span>
                  </div>
                  <div class="flex gap-2">
                    <span class="shrink-0 text-secondary-foreground/60">分类</span>
                    <span>{{ row.category || '—' }}</span>
                  </div>
                  <div class="flex gap-2">
                    <span class="shrink-0 text-secondary-foreground/60">提交时间</span>
                    <span>{{ formatTime(row.createTime) }}</span>
                  </div>
                  <div v-if="row.reviewNote" class="flex gap-2">
                    <span class="shrink-0 text-secondary-foreground/60">备注</span>
                    <span class="break-all">{{ row.reviewNote }}</span>
                  </div>
                </div>
                <div class="flex flex-wrap gap-2 border-t pt-3">
                  <template v-if="row.status === 'PENDING'">
                    <FaButton v-auth="'platform:plugin-market-source:accept'" variant="outline" size="sm" :disabled="actingPublicationId === row.id" @click="confirmAccept(row)">
                      通过
                    </FaButton>
                    <FaButton v-auth="'platform:plugin-market-source:accept'" variant="destructive" size="sm" :disabled="actingPublicationId === row.id" @click="confirmReject(row)">
                      拒绝
                    </FaButton>
                  </template>
                  <FaButton
                    v-if="row.status === 'PUBLISHED'"
                    v-auth="'platform:plugin-market-source:delete'"
                    variant="destructive"
                    size="sm"
                    :disabled="actingPublicationId === row.id"
                    @click="confirmUnpublish(row)"
                  >
                    下架
                  </FaButton>
                  <FaButton v-auth="'platform:plugin-market-source:delete'" variant="destructive" size="sm" :disabled="actingPublicationId === row.id" @click="confirmDelete(row)">
                    删除
                  </FaButton>
                </div>
              </div>
            </FaCard>
          </template>
        </FaResponsiveTable>
      </a-spin>

      <FaPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        :sizes="[10, 20, 50]"
        class="mt-3"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />
    </FaPageMain>
  </div>
</template>

<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { InboundMailDetail, InboundMailSummary } from '@/api/modules/platform-inbound-mail'
import apiInboundMail from '@/api/modules/platform-inbound-mail'

const toast = useFaToast()

const loading = ref(false)
const detailLoading = ref(false)
const rows = ref<InboundMailSummary[]>([])
const selected = ref<InboundMailDetail | null>(null)
const detailVisible = ref(false)
const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive({ keyword: '' })

const tableColumns = computed<TableColumn<InboundMailSummary>[]>(() => [
  { id: 'seen', header: '状态', width: 90, align: 'center' },
  { accessorKey: 'subject', header: '主题', width: 280, fixed: 'left' },
  { accessorKey: 'from', header: '发件人', width: 240 },
  { accessorKey: 'to', header: '收件人', width: 220 },
  { id: 'hasAttachment', header: '附件', width: 90, align: 'center' },
  { id: 'receivedAt', header: '收件时间', width: 180 },
  { id: 'operation', header: '操作', width: 120, align: 'center', fixed: 'right' },
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await apiInboundMail.page({
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
    })
    rows.value = res.data.records
    pagination.total = Number(res.data.total || 0)
  }
  catch {
    rows.value = []
    pagination.total = 0
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

function applySearch() {
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

async function openDetail(row: InboundMailSummary) {
  detailVisible.value = true
  detailLoading.value = true
  selected.value = null
  try {
    const res = await apiInboundMail.detail(row.uid)
    selected.value = res.data
  }
  catch {
    toast.error('邮件详情加载失败')
    detailVisible.value = false
  }
  finally {
    detailLoading.value = false
  }
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function sizeText(size?: number | string) {
  const value = Number(size || 0)
  if (!value) {
    return '-'
  }
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

const htmlSrcdoc = computed(() => {
  const html = selected.value?.htmlBody?.trim()
  if (!html) {
    return ''
  }
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><base target="_blank"><style>body{margin:0;padding:16px;font:14px/1.6 system-ui,sans-serif;color:CanvasText;background:Canvas;word-break:break-word;}img{max-width:100%;height:auto;}a{color:LinkText;}</style></head><body>${html}</body></html>`
})
</script>

<template>
  <div>
    <FaPageHeader title="入站邮箱" class="mb-0" />

    <FaPageMain>
      <FaResponsiveTable
        v-loading="loading"
        row-key="uid"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1180px]"
        border
        stripe
        column-visibility
        :columns="tableColumns"
        :data="rows"
      >
        <template #toolbar>
          <FaSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
              <FaInput v-model="search.keyword" clearable placeholder="主题 / 发件人 / 正文关键词" @keydown.enter="applySearch" @clear="applySearch" />
              <div class="flex gap-2 md:justify-end">
                <FaButton variant="outline" @click="resetSearch">
                  重置
                </FaButton>
                <FaButton :loading="loading" @click="applySearch">
                  <FaIcon name="i-ri:search-line" />
                  筛选
                </FaButton>
              </div>
            </div>
          </FaSearchBar>
        </template>
        <template #cell-seen="{ row }">
          <FaTag :variant="row.original.seen ? 'secondary' : 'default'">
            {{ row.original.seen ? '已读' : '未读' }}
          </FaTag>
        </template>
        <template #cell-hasAttachment="{ row }">
          {{ row.original.hasAttachment ? '有' : '-' }}
        </template>
        <template #cell-receivedAt="{ row }">
          {{ dateText(row.original.receivedAt) }}
        </template>
        <template #cell-operation="{ row }">
          <div class="table-actions">
            <FaButton v-auth="'platform:inbound-mail:view'" size="sm" variant="outline" @click="openDetail(row.original)">
              详情
            </FaButton>
          </div>
        </template>
        <template #card="{ row }">
          <FaCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 break-words text-base font-semibold">{{ row.subject }}</span>
                <FaTag :variant="row.seen ? 'secondary' : 'default'">
                  {{ row.seen ? '已读' : '未读' }}
                </FaTag>
              </div>
              <div class="flex flex-col gap-1 text-sm">
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">发件人</span>
                  <span class="min-w-0 break-all">{{ row.from || '-' }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">收件人</span>
                  <span class="min-w-0 break-all">{{ row.to || '-' }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">收件时间</span>
                  <span>{{ dateText(row.receivedAt) }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">附件</span>
                  <span>{{ row.hasAttachment ? '有' : '无' }}</span>
                </div>
              </div>
              <div class="flex flex-wrap gap-2 border-t pt-3">
                <FaButton v-auth="'platform:inbound-mail:view'" size="sm" variant="outline" @click="openDetail(row)">
                  详情
                </FaButton>
              </div>
            </div>
          </FaCard>
        </template>
      </FaResponsiveTable>

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

    <FaModal v-model="detailVisible" :title="selected ? selected.subject : '邮件详情'" class="sm:max-w-5xl">
      <section v-loading="detailLoading" class="detail-panel">
        <template v-if="selected">
          <div class="meta-grid">
            <div>
              <span>发件人</span>
              <strong>{{ selected.from || '-' }}</strong>
            </div>
            <div>
              <span>收件人</span>
              <strong>{{ selected.to || '-' }}</strong>
            </div>
            <div>
              <span>抄送</span>
              <strong>{{ selected.cc || '-' }}</strong>
            </div>
            <div>
              <span>收件时间</span>
              <strong>{{ dateText(selected.receivedAt) }}</strong>
            </div>
          </div>
          <iframe
            v-if="htmlSrcdoc"
            class="html-frame"
            sandbox=""
            referrerpolicy="no-referrer"
            :srcdoc="htmlSrcdoc"
            title="邮件正文"
          />
          <pre v-else class="text-body">{{ selected.textBody || '（无正文）' }}</pre>
          <div v-if="selected.attachments?.length" class="attachments">
            <h4>附件</h4>
            <ul>
              <li v-for="item in selected.attachments" :key="`${item.filename}-${item.size}`">
                <span>{{ item.filename }}</span>
                <em>{{ item.contentType || 'application/octet-stream' }} · {{ sizeText(item.size) }}{{ item.inline ? ' · 内嵌' : '' }}</em>
              </li>
            </ul>
          </div>
        </template>
      </section>
    </FaModal>
  </div>
</template>

<style scoped>
.table-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.detail-panel {
  display: grid;
  gap: 16px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.meta-grid div {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.meta-grid span {
  color: var(--color-text-3);
  font-size: 12px;
}

.meta-grid strong {
  min-width: 0;
  overflow-wrap: anywhere;
  font-weight: 500;
}

.html-frame,
.text-body {
  width: 100%;
  min-height: 360px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.text-body {
  margin: 0;
  padding: 16px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font: 13px/1.6 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--color-text-1);
}

.attachments h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.attachments ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.attachments li {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.attachments em {
  color: var(--color-text-3);
  font-style: normal;
  font-size: 12px;
}

@media (max-width: 900px) {
  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup lang="ts">
import type { ActionMenuItem, StatusColumn, TableColumn } from '@yudream/components'
import type { InboundMailDetail, InboundMailSummary } from '@/api/modules/platform-inbound-mail'
import { useTablePage } from '@yudream/components'
import apiInboundMail from '@/api/modules/platform-inbound-mail'
import { fetchSearchConfig } from '@/api/modules/system-search-config'

const toast = useYdToast()
const { auth } = useAppAuth()

const detailLoading = ref(false)
const selected = ref<InboundMailDetail | null>(null)
const detailVisible = ref(false)

/** 列表：搜索条件来自 InboundMailPageQuery 的 @Searchable 注解，分页由后端切片（每页上限 50） */
const {
  loading,
  rows,
  pagination,
  searchQuery,
  searchConfig,
  search,
  resetSearch,
  onPageChange,
  onSizeChange,
} = useTablePage<InboundMailSummary>({
  queryClass: 'online.yudream.base.application.platform.mail.query.InboundMailPageQuery',
  // 与迁移前一致：默认每页 10 条
  initialSize: 10,
  loadApi: async (query) => {
    const res = await apiInboundMail.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    return {
      records: res.data.records,
      total: Number(res.data.total || 0),
      page: res.data.page,
      size: res.data.size,
    }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
})

/** 行操作：详情统一走表格内置操作列，移动端卡片底部保留同一入口 */
const rowActions: ActionMenuItem<InboundMailSummary>[] = [
  { label: '详情', icon: 'morph:eye', visible: () => auth('platform:inbound-mail:view'), onClick: openDetail },
]

/** 主题是行标识，固定左侧；长串（主题/收发件人）在单元格内换行 */
const tableColumns: TableColumn<InboundMailSummary>[] = [
  { accessorKey: 'subject', header: '主题', minWidth: 280, fixed: 'left' },
  { accessorKey: 'from', header: '发件人', minWidth: 220 },
  { accessorKey: 'to', header: '收件人', minWidth: 200 },
  { id: 'hasAttachment', header: '附件', width: 90, align: 'center' },
  { id: 'receivedAt', header: '收件时间', width: 180 },
]

/** 已读/未读是行的唯一状态：布尔值走字符串键，未读给信息色便于扫读 */
const statusColumn: StatusColumn<InboundMailSummary> = {
  statusKey: 'seen',
  header: '状态',
  width: 90,
  statusMap: {
    true: { label: '已读', variant: 'secondary', tone: 'neutral' },
    false: { label: '未读', variant: 'default', tone: 'info' },
  },
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
    <YdTablePageLayout title="入站邮箱">
      <template #search="{ fold }">
        <YdDynamicSearchForm
          v-model="searchQuery"
          :config="searchConfig"
          :fold="fold"
          @search="search"
          @reset="resetSearch"
        />
      </template>

      <template #table>
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveEnhancedTable
            row-key="uid"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1180px]"
            stripe
            column-visibility
            border
            :status-column="statusColumn"
            :actions="rowActions"
            :columns="tableColumns"
            :data="rows"
          >
            <template #cell-subject="{ row }">
              <span v-if="row.original.subject" class="min-w-0 whitespace-normal break-all">{{ row.original.subject }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-from="{ row }">
              <span v-if="row.original.from" class="min-w-0 whitespace-normal break-all">{{ row.original.from }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-to="{ row }">
              <span v-if="row.original.to" class="min-w-0 whitespace-normal break-all">{{ row.original.to }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-hasAttachment="{ row }">
              {{ row.original.hasAttachment ? '有' : '-' }}
            </template>
            <template #cell-receivedAt="{ row }">
              {{ dateText(row.original.receivedAt) }}
            </template>
            <template #card="{ row }">
              <YdCard class="w-full">
                <div class="flex flex-col gap-3">
                  <div class="flex gap-2 items-center justify-between">
                    <span class="text-base font-semibold min-w-0 break-words">{{ row.subject }}</span>
                    <YdTag :variant="row.seen ? 'secondary' : 'default'">
                      {{ row.seen ? '已读' : '未读' }}
                    </YdTag>
                  </div>
                  <div class="text-sm flex flex-col gap-1">
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">发件人</span>
                      <span class="min-w-0 break-all">{{ row.from || '-' }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">收件人</span>
                      <span class="min-w-0 break-all">{{ row.to || '-' }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">收件时间</span>
                      <span>{{ dateText(row.receivedAt) }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">附件</span>
                      <span>{{ row.hasAttachment ? '有' : '无' }}</span>
                    </div>
                  </div>
                  <div class="pt-3 border-t flex flex-wrap gap-2">
                    <YdButton v-auth="'platform:inbound-mail:view'" size="sm" variant="outline" @click="openDetail(row)">
                      详情
                    </YdButton>
                  </div>
                </div>
              </YdCard>
            </template>
          </YdResponsiveEnhancedTable>
        </YdLoadingOverlay>
      </template>

      <template #footer>
        <YdPagination
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          :sizes="[10, 20, 50]"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </template>
    </YdTablePageLayout>

    <YdModal v-model="detailVisible" :title="selected ? selected.subject : '邮件详情'" class="sm:max-w-5xl">
      <YdLoadingOverlay :loading="detailLoading">
        <section class="detail-panel">
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
      </YdLoadingOverlay>
    </YdModal>
  </div>
</template>

<style scoped>
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

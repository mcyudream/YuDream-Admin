<script setup lang="ts">
import type { ActionMenuItem, TableColumn, YdSegmentedOption } from '@yudream/components'
import type { WikiCommentReviewAction, WikiManagedComment } from '@/api/modules/platform-wiki'
import { YdAlert, YdButton, YdEmpty, YdIcon, YdLoading, YdPagination, YdScrollArea, YdSegmented, YdTag } from '@yudream/components'
import { computed, inject, onMounted, ref } from 'vue'
import { fetchWikiManagedComments, reviewWikiComment } from '@/api/modules/platform-wiki'
import { wikiCommentStatusLabel, wikiWorkbenchKey } from '../wiki-utils'

/**
 * 评论审核：新评论先落待审核，通过后才在公开页出现；隐藏与删除都是软状态、可恢复。
 * 列表按评论自身归属推导空间，跨库评论不会串到这里；作者身份只读展示，不在客户端推导。
 */
const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const modal = useYdModal()
const { auth } = useAppAuth()

const canManage = computed(() => auth('platform:wiki:comment:manage'))
const canDelete = computed(() => auth('platform:wiki:comment:delete'))
const commentEnabled = computed(() => store.space.value?.commentEnabled !== false)

type StatusFilter = 'ALL' | 'PENDING' | 'PUBLISHED' | 'HIDDEN' | 'DELETED'
const statusFilter = ref<StatusFilter>('ALL')
const counts = ref<Record<string, number>>({ PENDING: 0, PUBLISHED: 0, HIDDEN: 0, DELETED: 0 })
const filterOptions = computed<YdSegmentedOption<StatusFilter>[]>(() => [
  { label: '全部', value: 'ALL' },
  { label: counts.value.PENDING ? `待审核 ${counts.value.PENDING}` : '待审核', value: 'PENDING' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已隐藏', value: 'HIDDEN' },
  { label: '已删除', value: 'DELETED' },
])

const comments = ref<WikiManagedComment[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const loadError = ref('')

const nodeTitles = computed(() => {
  const map = new Map<string, string>()
  for (const node of store.flatTree.value) {
    map.set(node.id, node.title)
  }
  return map
})
const pageTitle = (comment: WikiManagedComment) => comment.nodeId ? (nodeTitles.value.get(comment.nodeId) || `节点 ${comment.nodeId}`) : '未关联页面'

const commentColumns: TableColumn<WikiManagedComment>[] = [
  { id: 'content', header: '评论', minWidth: 300 },
  { id: 'page', header: '页面', minWidth: 180 },
  { id: 'author', header: '作者', width: 150 },
  { id: 'status', header: '状态', width: 140 },
  { id: 'createTime', header: '时间', width: 160 },
]

const commentActions = computed<ActionMenuItem<WikiManagedComment>[]>(() => [
  { label: '打开页面', icon: 'morph:external-link', onClick: comment => openPage(comment) },
  { label: '通过', icon: 'morph:check', visible: comment => canManage.value && comment.status === 'PENDING', onClick: comment => review(comment, 'publish', '已通过并公开评论') },
  { label: '置顶', icon: 'morph:pin', visible: comment => canManage.value && comment.status === 'PUBLISHED' && !comment.pinned, onClick: comment => review(comment, 'pin', '已置顶') },
  { label: '取消置顶', icon: 'morph:pin-off', visible: comment => canManage.value && comment.status === 'PUBLISHED' && Boolean(comment.pinned), onClick: comment => review(comment, 'unpin', '已取消置顶') },
  { label: '恢复', icon: 'morph:rotate-ccw', visible: comment => canManage.value && ['HIDDEN', 'DELETED'].includes(comment.status || ''), onClick: comment => review(comment, 'restore', '已恢复') },
  { label: '隐藏', icon: 'morph:eye-off', danger: true, visible: comment => canManage.value && ['PENDING', 'PUBLISHED'].includes(comment.status || ''), onClick: comment => review(comment, 'hide', '已隐藏，公开页与检索都不再出现') },
  { label: '删除', icon: 'morph:trash-2', danger: true, visible: comment => canDelete.value && comment.status !== 'DELETED', onClick: comment => removeComment(comment) },
])

function formatTime(value?: string): string {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '-'
    : date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
}

function excerpt(text: string, limit = 160): string {
  const plain = text.replace(/\s+/g, ' ').trim()
  return plain.length > limit ? `${plain.slice(0, limit)}…` : plain
}

async function load() {
  if (!store.spaceId.value) {
    comments.value = []
    total.value = 0
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await fetchWikiManagedComments(store.spaceId.value, {
      page: page.value,
      size: size.value,
      status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
    })
    comments.value = res.data?.records || []
    total.value = res.data?.total || 0
  }
  catch (exception) {
    comments.value = []
    total.value = 0
    loadError.value = exception instanceof Error ? exception.message : '评论加载失败'
  }
  finally {
    loading.value = false
  }
}

/** 四个状态各取一次总数：分段器上直接显示待审核队列深度，避免点进去才发现有积压 */
async function loadCounts() {
  if (!store.spaceId.value) {
    counts.value = { PENDING: 0, PUBLISHED: 0, HIDDEN: 0, DELETED: 0 }
    return
  }
  try {
    const statuses: StatusFilter[] = ['PENDING', 'PUBLISHED', 'HIDDEN', 'DELETED']
    const results = await Promise.all(statuses.map(status =>
      fetchWikiManagedComments(store.spaceId.value, { page: 1, size: 1, status })))
    counts.value = Object.fromEntries(statuses.map((status, index) => [status, results[index].data?.total || 0]))
  }
  catch {
    // 计数失败不阻断列表：分段器回落为不带数字的标签
    counts.value = { PENDING: 0, PUBLISHED: 0, HIDDEN: 0, DELETED: 0 }
  }
}

async function refresh() {
  await Promise.all([load(), loadCounts()])
}

function onFilterChange() {
  if (page.value !== 1) {
    page.value = 1
  }
  void load()
}

function onSizeChange() {
  if (page.value !== 1) {
    page.value = 1
  }
  void load()
}

async function review(comment: WikiManagedComment, action: WikiCommentReviewAction, message: string) {
  await reviewWikiComment(comment.id, action)
  toast.success(message)
  await refresh()
}

function removeComment(comment: WikiManagedComment) {
  modal.confirm({
    title: '删除评论',
    content: '删除是软状态：评论从公开页与检索中移除，审计记录保留，之后仍可恢复。确定删除？',
    onConfirm: async () => {
      await reviewWikiComment(comment.id, 'delete')
      toast.success('已删除（可恢复）')
      await refresh()
    },
  })
}

async function openPage(comment: WikiManagedComment) {
  if (!comment.nodeId) {
    toast.warning('这条评论没有关联页面')
    return
  }
  await store.openPage({ nodeId: comment.nodeId })
}

onMounted(refresh)
defineExpose({ reload: refresh })
</script>

<template>
  <div class="cmt-panel">
    <YdScrollArea class="cmt-scroll">
      <div class="cmt-inner">
        <header class="cmt-head">
          <div class="cmt-head__text">
            <h2 class="cmt-head__title">
              评论审核
            </h2>
            <p class="cmt-head__desc">
              读者评论先落「待审核」，通过后才在公开页出现；隐藏与删除都是软状态，随时可以恢复。
            </p>
          </div>
          <div class="cmt-head__actions">
            <YdButton size="sm" variant="outline" :loading="loading" @click="refresh">
              <YdIcon name="morph:refresh-cw" /> 刷新
            </YdButton>
          </div>
        </header>

        <div class="cmt-toolbar">
          <YdSegmented v-model="statusFilter" :options="filterOptions" size="sm" aria-label="评论状态筛选" @change="onFilterChange" />
          <span class="cmt-toolbar__count">
            {{ statusFilter === 'ALL' ? '全部状态' : wikiCommentStatusLabel(statusFilter).label }} · {{ total }} 条
          </span>
        </div>

        <p v-if="!commentEnabled" class="cmt-note">
          <YdIcon name="morph:message-square-off" /> 本知识库当前关闭了公开评论：已有评论仍可在此审核，读者无法发表新评论。可在「设置 → 评论区」开启。
        </p>
        <p v-else-if="!canManage" class="cmt-note">
          <YdIcon name="morph:lock" /> 你当前没有评论审核权限（platform:wiki:comment:manage），只能查看；需要处理请联系管理员。
        </p>
        <p v-if="counts.PENDING" class="cmt-note cmt-note--todo">
          <YdIcon name="morph:clock" /> {{ counts.PENDING }} 条评论等待审核，通过后才会出现在公开页。
        </p>

        <div v-if="loadError" class="cmt-error">
          <YdAlert variant="destructive" title="评论加载失败" :description="loadError" />
          <YdButton variant="outline" size="sm" :loading="loading" @click="refresh">
            <YdIcon name="morph:rotate-cw" /> 重新加载
          </YdButton>
        </div>

        <div v-if="loading && !comments.length" class="cmt-loading">
          <YdLoading :size="32" text="正在加载评论…" />
        </div>
        <YdEmpty
          v-else-if="!comments.length && !loadError"
          icon="morph:message-square"
          :title="statusFilter === 'ALL' ? '暂无评论' : `没有${wikiCommentStatusLabel(statusFilter).label}的评论`"
          :description="commentEnabled
            ? '读者在公开页面引文并发表评论后会出现在这里，等待审核。'
            : '本知识库关闭了公开评论；开启后读者的评论会进入待审核。'"
        />
        <template v-else-if="comments.length">
          <YdResponsiveEnhancedTable
            row-key="id"
            stripe
            border
            column-visibility
            table-class="min-w-[980px]"
            :columns="commentColumns"
            :actions="commentActions"
            :data="comments"
          >
            <template #cell-content="{ row }">
              <div class="cmt-content">
                <span class="cmt-content__text">{{ excerpt(row.original.content) }}</span>
                <blockquote v-if="row.original.anchor?.exact" class="cmt-content__quote">
                  {{ excerpt(row.original.anchor.exact, 80) }}
                </blockquote>
                <span v-if="row.original.parentId" class="cmt-content__meta">
                  <YdTag variant="secondary">回复</YdTag>
                </span>
              </div>
            </template>
            <template #cell-page="{ row }">
              <span class="cmt-page">{{ pageTitle(row.original) }}</span>
            </template>
            <template #cell-author="{ row }">
              <div class="cmt-author">
                <span class="cmt-author__name">{{ row.original.authorName || '-' }}</span>
                <span class="cmt-author__type">{{ row.original.authorType === 'USER' ? '用户' : row.original.authorType || '-' }}</span>
              </div>
            </template>
            <template #cell-status="{ row }">
              <div class="cmt-status">
                <span class="cmt-status__label">
                  <span class="cmt-status__dot" :style="{ background: wikiCommentStatusLabel(row.original.status).color }" />
                  {{ wikiCommentStatusLabel(row.original.status).label }}
                </span>
                <YdTag v-if="row.original.pinned" variant="default">置顶</YdTag>
              </div>
            </template>
            <template #cell-createTime="{ row }">
              <span class="cmt-time">{{ formatTime(row.original.createTime) }}</span>
            </template>
          </YdResponsiveEnhancedTable>
          <YdPagination
            v-if="total > size"
            v-model:page="page"
            v-model:size="size"
            :total="total"
            class="cmt-pagination"
            @page-change="load"
            @size-change="onSizeChange"
          />
        </template>
      </div>
    </YdScrollArea>
  </div>
</template>

<style scoped>
.cmt-panel {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.cmt-scroll {
  height: 100%;
}

.cmt-inner {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.cmt-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.cmt-head__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
}

.cmt-head__desc {
  margin: 4px 0 0;
  max-width: 70ch;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.cmt-head__actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.cmt-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.cmt-toolbar__count {
  font-size: 13px;
  color: var(--color-text-3);
}

.cmt-note {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.cmt-note--todo {
  color: rgb(var(--warning-6));
}

.cmt-error {
  display: grid;
  gap: 8px;
}

.cmt-loading {
  display: flex;
  justify-content: center;
  padding: 56px 0;
}

.cmt-pagination {
  display: flex;
  justify-content: flex-end;
}

.cmt-content {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.cmt-content__text {
  color: var(--color-text-1);
  /* 长评论必须在列内折行截断：不设限会横穿到相邻列 */
  display: -webkit-box;
  overflow: hidden;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.cmt-content__quote {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-left: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
  border-left: 2px solid rgb(var(--gray-4));
}

.cmt-content__meta {
  display: flex;
  gap: 4px;
}

.cmt-page {
  color: var(--color-text-2);
}

.cmt-author {
  display: grid;
  gap: 2px;
}

.cmt-author__name {
  font-weight: 600;
  color: var(--color-text-1);
}

.cmt-author__type {
  font-size: 12px;
  color: var(--color-text-3);
}

.cmt-status {
  display: flex;
  gap: 6px;
  align-items: center;
}

.cmt-status__label {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.cmt-status__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.cmt-time {
  font-size: 12px;
  color: var(--color-text-3);
}
</style>

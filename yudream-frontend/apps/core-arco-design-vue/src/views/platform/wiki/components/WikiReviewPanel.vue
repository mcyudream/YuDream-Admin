<script setup lang="ts">
import type { WikiReviewItem } from '@/api/modules/platform-wiki'
import { computed, inject, onActivated, onMounted, ref, watch } from 'vue'
import { executeWikiReview, fetchWikiReviews } from '@/api/modules/platform-wiki'
import { wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()

const reviews = ref<WikiReviewItem[]>([])
const loading = ref(false)
const loadError = ref('')
type ReviewFilter = 'ALL' | 'PENDING' | 'DONE'
const filter = ref<ReviewFilter>('PENDING')
const filterOptions: Array<{ label: string, value: ReviewFilter }> = [
  { label: '待处理', value: 'PENDING' },
  { label: '已处理', value: 'DONE' },
  { label: '全部', value: 'ALL' },
]
const acting = ref('')

const filtered = computed(() => reviews.value.filter((item) => {
  if (filter.value === 'ALL') {
    return true
  }
  if (filter.value === 'PENDING') {
    return !['RESOLVED', 'DISMISSED', 'DONE'].includes(item.status)
  }
  return ['RESOLVED', 'DISMISSED', 'DONE'].includes(item.status)
}))

const pendingCount = computed(() => reviews.value.filter(item => !['RESOLVED', 'DISMISSED', 'DONE'].includes(item.status)).length)

// 空态区分「初始为空」与「当前筛选无结果」
const emptyState = computed(() => {
  if (filter.value === 'PENDING') {
    return { title: '没有待处理的审阅项', description: '摄入或 Lint 后发现的问题会出现在这里' }
  }
  if (reviews.value.length) {
    return { title: '没有符合条件的审阅项', description: '切换到其他筛选条件查看。' }
  }
  return { title: '暂无审阅项', description: '摄入或 Lint 后发现的问题会出现在这里' }
})

// 客户端分页（审阅项可能很多）
const page = ref(1)
const pageSize = ref(10)
const pagedReviews = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})

const itemTypeMeta: Record<string, { label: string, color: string }> = {
  COVERAGE_GAP: { label: '覆盖缺口', color: '#f59e0b' },
  CONFLICT: { label: '内容冲突', color: '#ef4444' },
  STALE: { label: '内容过期', color: '#a16207' },
  LOW_QUALITY: { label: '低质量', color: '#8b5cf6' },
  ORPHAN: { label: '孤立页面', color: '#64748b' },
}

async function load() {
  if (!store.spaceId.value) {
    reviews.value = []
    loadError.value = ''
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await fetchWikiReviews(store.spaceId.value)
    reviews.value = res.data || []
    page.value = 1
  }
  catch (exception) {
    // 保留错误与重试入口，不用空列表掩盖失败
    loadError.value = exception instanceof Error ? exception.message : '审阅项加载失败，请稍后重试'
  }
  finally {
    loading.value = false
  }
}

async function act(item: WikiReviewItem, action: string) {
  acting.value = item.id
  try {
    await executeWikiReview(item.id, action)
    toast.success('审阅项已处理')
    await load()
    if (action === 'deep_research') {
      store.openPanel('ingest')
    }
  }
  finally {
    acting.value = ''
  }
}

function typeMeta(itemType: string) {
  return itemTypeMeta[itemType] || { label: itemType, color: '#0ea5e9' }
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待处理',
    RESOLVED: '已完成',
    DISMISSED: '已忽略',
    DONE: '已完成',
  }
  return map[status] || status
}

onMounted(load)
onActivated(load)
watch(() => store.spaceId.value, load)
watch(filter, () => {
  page.value = 1
})
</script>

<template>
  <div class="rev-panel">
    <YdScrollArea class="rev-scroll">
      <div class="rev-inner">
        <div class="rev-head">
          <YdSegmented
            v-model="filter"
            class="rev-filter"
            size="sm"
            aria-label="审阅项筛选"
            :options="filterOptions"
          >
            <template #option="{ option }">
              <span class="rev-filter__option">
                {{ option.label }}
                <YdTag v-if="option.value === 'PENDING' && pendingCount" variant="secondary">{{ pendingCount }}</YdTag>
              </span>
            </template>
          </YdSegmented>
          <YdButton size="sm" variant="outline" :loading="loading" @click="load">
            <YdIcon name="morph:refresh-cw" /> 刷新
          </YdButton>
        </div>

        <div v-if="loadError" class="rev-error">
          <YdAlert variant="destructive" title="审阅项加载失败" :description="loadError" />
          <YdButton variant="outline" size="sm" :loading="loading" @click="load">
            <YdIcon name="morph:rotate-cw" /> 重新加载
          </YdButton>
        </div>

        <div v-if="loading && !reviews.length" class="rev-loading">
          <YdLoading :size="32" text="正在加载审阅项…" />
        </div>
        <YdEmpty
          v-else-if="!filtered.length && !loadError"
          icon="morph:list-checks"
          :title="emptyState.title"
          :description="emptyState.description"
        />
        <div v-else-if="filtered.length" class="rev-list">
          <YdCard v-for="item in pagedReviews" :key="item.id" class="rev-card">
            <div class="rev-card__head">
              <span class="rev-card__type" :style="{ color: typeMeta(item.itemType).color, background: `${typeMeta(item.itemType).color}1a` }">
                {{ typeMeta(item.itemType).label }}
              </span>
              <strong>{{ item.title }}</strong>
              <YdTag variant="outline" class="rev-card__status">
                {{ statusLabel(item.status) }}
              </YdTag>
            </div>
            <p class="rev-card__desc">
              {{ item.description }}
            </p>
            <div v-if="item.pageTitles?.length" class="rev-card__pages">
              <YdButton
                v-for="title in item.pageTitles"
                :key="title"
                variant="ghost"
                type="button"
                class="rev-card__page"
                @click="store.openPage({ title })"
              >
                <YdIcon name="morph:file-text" /> {{ title }}
              </YdButton>
            </div>
            <div class="rev-card__footer">
              <span v-if="item.suggestedAction" class="rev-card__suggest">
                <YdIcon name="morph:lightbulb" /> {{ item.suggestedAction }}
              </span>
              <span class="rev-card__time">{{ new Date(item.createTime).toLocaleString('zh-CN') }}</span>
              <div v-if="!['RESOLVED', 'DISMISSED', 'DONE'].includes(item.status)" class="rev-card__actions">
                <YdButton size="sm" :loading="acting === item.id" @click="act(item, 'done')">
                  <YdIcon name="morph:check" /> 完成
                </YdButton>
                <YdButton size="sm" variant="outline" :disabled="acting === item.id" @click="act(item, 'dismiss')">
                  <YdIcon name="morph:x" /> 忽略
                </YdButton>
                <YdButton
                  v-if="item.searchQueries?.length"
                  size="sm"
                  variant="secondary"
                  :disabled="acting === item.id"
                  @click="act(item, 'deep_research')"
                >
                  <YdIcon name="morph:flask-conical" /> 发起研究
                </YdButton>
              </div>
            </div>
          </YdCard>
        </div>
        <YdPagination
          v-if="filtered.length > pageSize"
          v-model:page="page"
          v-model:size="pageSize"
          :total="filtered.length"
          class="rev-pagination"
        />
      </div>
    </YdScrollArea>
  </div>
</template>

<style scoped>
.rev-panel {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.rev-scroll {
  height: 100%;
}

.rev-inner {
  display: grid;
  gap: 12px;
  max-width: 960px;
  margin: 0 auto;
  padding: 16px;
}

.rev-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.rev-filter {
  min-width: 0;
}

.rev-filter__option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

/* YdAlert 没有默认插槽：重试动作必须与告警相邻，不能作为子节点传入 */
.rev-error {
  display: grid;
  gap: 8px;
}

.rev-loading {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.rev-list {
  display: grid;
  gap: 10px;
}

.rev-pagination {
  display: flex;
  justify-content: flex-end;
}

.rev-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.rev-card__type {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.rev-card__status {
  margin-left: auto;
}

.rev-card__desc {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
}

.rev-card__pages {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.rev-card__page {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.rev-card__page:hover {
  border-color: var(--color-border-2);
  background: var(--color-fill-2);
}

.rev-card__footer {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.rev-card__suggest {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-3);
  font-size: 12px;
}

.rev-card__time {
  color: var(--color-text-3);
  font-size: 11px;
}

.rev-card__actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
  flex-wrap: wrap;
}

/* 窄屏：卡片行不横向挤压，操作按钮换行到独立一行 */
@media (max-width: 768px) {
  .rev-inner {
    padding: 12px;
  }

  .rev-head {
    align-items: stretch;
    flex-direction: column;
  }

  .rev-card__head {
    align-items: flex-start;
  }

  .rev-card__status {
    margin-left: 0;
  }

  .rev-card__footer {
    align-items: flex-start;
  }

  .rev-card__actions {
    width: 100%;
    margin-left: 0;
  }
}
</style>

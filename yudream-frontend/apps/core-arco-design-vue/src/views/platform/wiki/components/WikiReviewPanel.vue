<script setup lang="ts">
import type { WikiReviewItem } from '@/api/modules/platform-wiki'
import { computed, inject, onActivated, onMounted, ref, watch } from 'vue'
import { executeWikiReview, fetchWikiReviews } from '@/api/modules/platform-wiki'
import { wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useFaToast()

const reviews = ref<WikiReviewItem[]>([])
const loading = ref(false)
const filter = ref<'ALL' | 'PENDING' | 'DONE'>('PENDING')
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
    return
  }
  loading.value = true
  try {
    const res = await fetchWikiReviews(store.spaceId.value)
    reviews.value = res.data || []
    page.value = 1
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
    <FaScrollArea class="rev-scroll">
      <div class="rev-inner">
        <div class="rev-head">
          <div class="rev-head__tabs">
            <button type="button" :class="{ active: filter === 'PENDING' }" @click="filter = 'PENDING'">
              待处理 <FaTag v-if="pendingCount" variant="secondary">{{ pendingCount }}</FaTag>
            </button>
            <button type="button" :class="{ active: filter === 'DONE' }" @click="filter = 'DONE'">
              已处理
            </button>
            <button type="button" :class="{ active: filter === 'ALL' }" @click="filter = 'ALL'">
              全部
            </button>
          </div>
          <FaButton size="sm" variant="outline" :loading="loading" @click="load">
            <FaIcon name="i-ri:refresh-line" /> 刷新
          </FaButton>
        </div>

        <div v-if="!filtered.length && !loading" class="rev-empty">
          <FaIcon name="i-ri:checkbox-multiple-line" />
          <strong>{{ filter === 'PENDING' ? '没有待处理的审阅项' : '暂无审阅项' }}</strong>
          <p>摄入或 Lint 后发现的问题会出现在这里</p>
        </div>

        <div v-else class="rev-list">
          <FaCard v-for="item in pagedReviews" :key="item.id" class="rev-card">
            <div class="rev-card__head">
              <span class="rev-card__type" :style="{ color: typeMeta(item.itemType).color, background: `${typeMeta(item.itemType).color}1a` }">
                {{ typeMeta(item.itemType).label }}
              </span>
              <strong>{{ item.title }}</strong>
              <FaTag variant="outline" class="rev-card__status">{{ statusLabel(item.status) }}</FaTag>
            </div>
            <p class="rev-card__desc">{{ item.description }}</p>
            <div v-if="item.pageTitles?.length" class="rev-card__pages">
              <button
                v-for="title in item.pageTitles"
                :key="title"
                type="button"
                class="rev-card__page"
                @click="store.openPage({ title })"
              >
                <FaIcon name="i-ri:file-text-line" /> {{ title }}
              </button>
            </div>
            <div class="rev-card__footer">
              <span v-if="item.suggestedAction" class="rev-card__suggest">
                <FaIcon name="i-ri:lightbulb-line" /> {{ item.suggestedAction }}
              </span>
              <span class="rev-card__time">{{ new Date(item.createTime).toLocaleString('zh-CN') }}</span>
              <div v-if="!['RESOLVED', 'DISMISSED', 'DONE'].includes(item.status)" class="rev-card__actions">
                <FaButton size="sm" :loading="acting === item.id" @click="act(item, 'done')">
                  <FaIcon name="i-ri:check-line" /> 完成
                </FaButton>
                <FaButton size="sm" variant="outline" :disabled="acting === item.id" @click="act(item, 'dismiss')">
                  <FaIcon name="i-ri:close-line" /> 忽略
                </FaButton>
                <FaButton
                  v-if="item.searchQueries?.length"
                  size="sm"
                  variant="secondary"
                  :disabled="acting === item.id"
                  @click="act(item, 'deep_research')"
                >
                  <FaIcon name="i-ri:flask-line" /> 发起研究
                </FaButton>
              </div>
            </div>
          </FaCard>
        </div>
        <FaPagination
          v-if="filtered.length > pageSize"
          v-model:page="page"
          v-model:size="pageSize"
          :total="filtered.length"
          class="rev-pagination"
        />
      </div>
    </FaScrollArea>
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
}

.rev-head__tabs {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 10px;
  background: var(--color-fill-2);
}

.rev-head__tabs button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}

.rev-head__tabs button.active {
  background: var(--color-bg-1);
  color: rgb(var(--primary-6));
  font-weight: 600;
  box-shadow: 0 1px 3px rgb(0 0 0 / 8%);
}

.rev-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 50px 0;
  color: var(--color-text-3);
}

.rev-empty :deep(svg) {
  color: rgb(var(--primary-6));
  font-size: 36px;
}

.rev-empty strong {
  color: var(--color-text-1);
}

.rev-empty p {
  margin: 0;
  font-size: 12px;
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
  color: rgb(var(--primary-6));
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.rev-card__page:hover {
  border-color: rgba(var(--primary-6), 0.4);
  background: rgba(var(--primary-6), 0.06);
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
}
</style>

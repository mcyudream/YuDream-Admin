<script setup lang="ts">
import { YdFilterSelect, YdListToolbar } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const keyword = ref('')
const status = ref('ALL')
const category = ref('all')

const statusItems = [
  { value: 'ALL', label: '全部', count: 18, tone: 'neutral' as const },
  { value: 'ENABLED', label: '运行中', count: 14, tone: 'ok' as const },
  { value: 'DISABLED', label: '未启用', count: 4, tone: 'muted' as const },
  { value: 'ERROR', label: '异常', count: 0, tone: 'muted' as const },
]

const categoryOptions = [
  { label: '全部分类', value: 'all' },
  { label: '内容定制', value: 'content' },
  { label: '实时通信', value: 'realtime' },
]

const code = `<YdListToolbar
  v-model:keyword="keyword"
  v-model:status="status"
  placeholder="搜索名称或编码"
  :search-width="300"
  :status-items="statusItems"
  @search="load"
>
  <template #filters>
    <YdFilterSelect v-model="category" :options="categoryOptions" :width="168" placeholder="全部分类" />
  </template>
  <template #actions>
    <YdButton variant="outline">刷新</YdButton>
  </template>
</YdListToolbar>`
</script>

<template>
  <DemoCard
    title="YdListToolbar / YdFilterSelect"
    description="列表页筛选区统一入口：内置限宽搜索、计数即筛选的状态胶囊（底部只读比例条）、左右分区插槽；下拉筛选用 YdFilterSelect 指定宽度，替代页面自建工具栏"
    :code="code"
  >
    <YdListToolbar
      v-model:keyword="keyword"
      v-model:status="status"
      placeholder="搜索名称或编码"
      :search-width="300"
      :status-items="statusItems"
    >
      <template #filters>
        <YdFilterSelect v-model="category" :options="categoryOptions" :width="168" placeholder="全部分类" />
      </template>
      <template #actions>
        <YdButton variant="outline">
          <YdIcon name="morph:refresh-cw" />
          刷新
        </YdButton>
      </template>
    </YdListToolbar>
    <p class="list-toolbar-demo__hint">
      当前搜索词：{{ keyword || '（空）' }} · 状态：{{ status }}
    </p>
  </DemoCard>
</template>

<style scoped>
.list-toolbar-demo__hint {
  margin: 12px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
}
</style>

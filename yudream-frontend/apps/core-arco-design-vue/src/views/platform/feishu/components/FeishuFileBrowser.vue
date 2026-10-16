<script setup lang="ts">
import type { ActionMenuItem, TableColumn, UserColumn } from '@yudream/components'
import type { FeishuFailure, FeishuFileItem } from '../feishu-contract'
import { computed } from 'vue'
import { failureTitle, FOLDER_TYPE } from '../feishu-contract'
import FeishuAccessGuide from './FeishuAccessGuide.vue'

/**
 * 文件清单：有行级操作的只读管理列表，按规范使用 YdResponsiveEnhancedTable，
 * 名称列固定左、唯一操作菜单列固定右；翻页由后端 pageToken 驱动（无页码），因此用「加载更多」而不是页码控件。
 */
const props = defineProps<{
  items: FeishuFileItem[]
  loadedTotal: number
  loading: boolean
  loadingMore: boolean
  failure: FeishuFailure | null
  hasMore: boolean
  filtered: boolean
  folderName: string
}>()

const emit = defineEmits<{
  select: [id: string]
  openFolder: [item: FeishuFileItem]
  retry: []
  loadMore: []
  reset: []
}>()

/** 身份列固定左侧：文件/文件夹名称；窄屏卡片复用为标题。 */
const nameColumn: UserColumn<FeishuFileItem> = {
  nameKey: 'name',
  header: '名称',
  width: 240,
}

const columns = computed<TableColumn<FeishuFileItem>[]>(() => [
  { id: 'type', header: '类型', width: 100 },
  { id: 'ownerId', header: '归属人 ID', width: 170 },
  { id: 'modifiedTime', header: '修改时间', width: 180 },
  { id: 'url', header: '原文', width: 110 },
])

/** 行级操作按类型区分：文件夹只能下钻，文档才可打开详情；两者都共享同一个操作菜单。 */
const actions = computed<ActionMenuItem<FeishuFileItem>[]>(() => [
  {
    label: row => row.isFolder ? '打开目录' : '查看详情',
    onClick: (row) => {
      if (row.isFolder) {
        emit('openFolder', row)
        return
      }
      emit('select', row.token)
    },
  },
  {
    label: row => row.url ? '打开原文' : '无原文链接',
    icon: 'morph:external-link',
    visible: row => !row.isFolder,
    disabled: row => !row.url,
    onClick: (row) => {
      if (row.url) {
        window.open(row.url, '_blank', 'noopener,noreferrer')
      }
    },
  },
])

function typeLabel(item: FeishuFileItem) {
  return item.type === FOLDER_TYPE ? '文件夹' : item.type || '未返回'
}
</script>

<template>
  <section class="browser">
    <header class="browser__head">
      <div class="browser__heading">
        <h2>{{ props.folderName }}</h2>
        <span class="browser__meta">
          已加载 {{ loadedTotal }} 项<span v-if="filtered"> · 命中 {{ items.length }} 条</span>
        </span>
      </div>
      <p class="browser__hint">
        后端按 pageToken 分页（默认 20 条/页），搜索与类型筛选只作用于已加载条目。
      </p>
    </header>

    <YdLoadingOverlay :loading="loading" text="加载文件">
      <YdResult
        v-if="failure && !items.length"
        status="error"
        :title="failureTitle(failure.kind)"
        :description="failure.message"
      >
        <YdButton variant="outline" @click="emit('retry')">
          重新加载
        </YdButton>
      </YdResult>

      <YdAlert
        v-else-if="failure"
        class="browser__note"
        variant="destructive"
        icon="morph:circle-alert"
        :title="failureTitle(failure.kind)"
        :description="`${failure.message}（已加载内容保留）`"
      />

      <template v-if="!failure || items.length">
        <YdResponsiveEnhancedTable
          v-if="items.length"
          row-key="token"
          table-root-class="rounded-lg overflow-hidden"
          table-class="min-w-[820px]"
          stripe
          :user-column="nameColumn"
          :actions="actions"
          :columns="columns"
          :data="items"
        >
          <template #cell-type="{ row }">
            {{ typeLabel(row.original) }}
          </template>
          <template #cell-ownerId="{ row }">
            {{ row.original.ownerId || '未返回' }}
          </template>
          <template #cell-modifiedTime="{ row }">
            {{ row.original.modifiedTime || '未返回' }}
          </template>
          <template #cell-url="{ row }">
            <span class="browser__cell-note">{{ row.original.url ? '可用' : '未返回' }}</span>
          </template>
        </YdResponsiveEnhancedTable>

        <YdEmpty
          v-else-if="loadedTotal > 0"
          illustration="no-result"
          title="没有匹配的文件"
          description="当前搜索或类型筛选没有命中；调整条件或重置后再试。"
        >
          <YdButton variant="outline" @click="emit('reset')">
            清除筛选
          </YdButton>
        </YdEmpty>

        <YdEmpty
          v-else
          illustration="empty"
          title="目录为空"
          description="飞书没有向当前应用返回文件。请检查应用权限和资源授权；知识库与个人文档需要额外授权，详见下方教程。"
        >
          <YdButton variant="outline" @click="emit('retry')">
            重新加载
          </YdButton>
          <FeishuAccessGuide />
        </YdEmpty>

        <footer v-if="hasMore" class="browser__more">
          <YdButton variant="outline" :loading="loadingMore" @click="emit('loadMore')">
            加载更多
          </YdButton>
          <span class="browser__meta">后端还有下一页</span>
        </footer>
      </template>
    </YdLoadingOverlay>
  </section>
</template>

<style scoped>
.browser {
  display: grid;
  gap: 12px;
  align-content: start;
  min-width: 0;
}

.browser__head {
  display: grid;
  gap: 4px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border-2);
}

.browser__heading {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  align-items: baseline;
  justify-content: space-between;
}

.browser__heading h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
}

.browser__meta,
.browser__hint,
.browser__cell-note {
  font-size: 12px;
  color: var(--color-text-3);
}

.browser__hint {
  margin: 0;
}

.browser__note {
  font-size: 12px;
}

.browser__more {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-2);
}
</style>

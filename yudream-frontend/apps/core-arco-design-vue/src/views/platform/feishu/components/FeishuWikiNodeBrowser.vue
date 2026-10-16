<script setup lang="ts">
import type { ActionMenuItem, TableColumn, UserColumn } from '@yudream/components'
import type { FeishuFailure, FeishuSpaceItem, FeishuWikiNodeItem } from '../feishu-contract'
import { computed } from 'vue'
import { failureTitle } from '../feishu-contract'

defineProps<{
  space: FeishuSpaceItem
  nodes: FeishuWikiNodeItem[]
  trail: FeishuWikiNodeItem[]
  loading: boolean
  loadingMore: boolean
  failure: FeishuFailure | null
  hasMore: boolean
  filtered: boolean
}>()

const emit = defineEmits<{
  openNode: [node: FeishuWikiNodeItem]
  openDocument: [node: FeishuWikiNodeItem]
  goParent: []
  loadMore: []
  retry: []
}>()

const nameColumn: UserColumn<FeishuWikiNodeItem> = { nameKey: 'title', header: '名称', width: 320 }
const columns: TableColumn<FeishuWikiNodeItem>[] = [
  { id: 'resource', header: '资源类型', width: 150 },
  { id: 'relation', header: '节点类型', width: 140 },
]
const actions = computed<ActionMenuItem<FeishuWikiNodeItem>[]>(() => [
  {
    label: row => row.hasChild ? '浏览子节点' : row.objType === 'docx' ? '打开文档' : '查看资源信息',
    onClick: row => row.hasChild ? emit('openNode', row) : emit('openDocument', row),
  },
  {
    label: '打开文档',
    visible: row => row.hasChild && row.objType === 'docx' && Boolean(row.objToken),
    onClick: row => emit('openDocument', row),
  },
])
</script>

<template>
  <section class="wiki-browser">
    <header class="wiki-browser__head">
      <div class="wiki-browser__title">
        <YdButton variant="ghost" size="sm" :disabled="!trail.length" aria-label="返回知识库上级目录" @click="emit('goParent')">
          <YdIcon name="morph:arrow-left" />
          返回上级
        </YdButton>
        <div>
          <h2>{{ trail.at(-1)?.title || space.name || space.spaceId }}</h2>
          <p>{{ space.name }}<span v-if="space.visibility"> · {{ space.visibility }}</span><span v-if="space.spaceType"> · {{ space.spaceType }}</span></p>
        </div>
      </div>
      <YdCopyButton :text="space.spaceId" label="复制知识库 ID" />
    </header>

    <YdAlert
      v-if="failure"
      class="wiki-browser__failure"
      variant="destructive"
      icon="morph:circle-alert"
      :title="failureTitle(failure.kind)"
      :description="failure.message"
    >
      <YdButton variant="outline" size="sm" @click="emit('retry')">
        重试
      </YdButton>
    </YdAlert>

    <YdLoadingOverlay :loading="loading" text="加载知识库节点">
      <YdResponsiveEnhancedTable
        v-if="nodes.length"
        row-key="nodeToken"
        table-class="min-w-[640px]"
        :user-column="nameColumn"
        :columns="columns"
        :actions="actions"
        :data="nodes"
      >
        <template #cell-resource="{ row }">
          {{ row.original.objType || '类型未返回' }}
        </template>
        <template #cell-relation="{ row }">
          {{ row.original.hasChild ? '含子节点' : row.original.nodeType || '节点' }}
        </template>
      </YdResponsiveEnhancedTable>

      <YdEmpty
        v-else-if="!failure && !loading && filtered"
        illustration="no-result"
        title="没有匹配的节点"
        description="关键词仅筛选已加载节点；可清除搜索词，或继续加载下一页。"
      />

      <YdEmpty
        v-else-if="!failure && !loading && !hasMore"
        illustration="empty"
        title="当前目录没有节点"
        description="飞书没有在此层级返回节点；如预期有内容，请确认知识库管理员已授权此应用。"
      >
        <YdButton variant="outline" size="sm" @click="emit('retry')">
          重新加载
        </YdButton>
      </YdEmpty>

      <YdAlert
        v-if="!nodes.length && hasMore && !loading && !filtered"
        icon="morph:info"
        title="本页没有可见节点"
        description="飞书仍返回下一页游标；这可能是权限过滤造成的空页，请继续加载以查找可见内容。"
      />

      <footer v-if="hasMore" class="wiki-browser__more">
        <YdButton variant="outline" :loading="loadingMore" @click="emit('loadMore')">
          加载更多节点
        </YdButton>
        <span>{{ nodes.length }} 项已加载</span>
      </footer>
    </YdLoadingOverlay>

    <YdAlert
      v-if="nodes.some(node => node.objType && node.objType !== 'docx' && !node.hasChild)"
      icon="morph:info"
      title="部分资源类型暂未接入预览"
      description="此处可浏览并识别节点；当前只支持打开 docx 文档。表格、画板、文件等资源保留真实类型信息，不会伪装成可编辑文档。"
    />
  </section>
</template>

<style scoped>
.wiki-browser {
  display: grid;
  gap: 12px;
  align-content: start;
  min-width: 0;
}

.wiki-browser__head,
.wiki-browser__title,
.wiki-browser__more {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  align-items: center;
}

.wiki-browser__head {
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border-2);
}

.wiki-browser__title {
  min-width: 0;
}

.wiki-browser__title h2,
.wiki-browser__title p {
  margin: 0;
}

.wiki-browser__title h2 {
  color: var(--color-text-1);
  font-size: 16px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.wiki-browser__title p,
.wiki-browser__more span {
  color: var(--color-text-3);
  font-size: 12px;
}

.wiki-browser__more {
  padding-top: 10px;
  border-top: 1px solid var(--color-border-2);
}
</style>

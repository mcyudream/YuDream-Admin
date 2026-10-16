<script setup lang="ts">
import type { YdSortableTreeDropEvent, YdSortableTreeNode } from '@yudream/components'
import { moveYdSortableTreeNode, YdSortableTree, YdUiProvider } from '@yudream/components'
import { computed, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const data = ref<YdSortableTreeNode[]>([
  { key: 'nav-root', label: '导航配置', kind: 'directory', children: [
    { key: 'page-home', label: '首页', kind: 'page' },
    { key: 'page-admin', label: '管理工具', kind: 'directory', children: [{ key: 'page-users', label: '用户管理', kind: 'page' }] },
    { key: 'page-locked', label: '受保护项', kind: 'page', disabled: true },
  ] },
  { key: 'lazy-root', label: '延迟加载的目录', kind: 'directory', lazy: true },
  { key: '90071992547409931', label: '长整型字符串 ID', kind: 'page' },
])
const selectedKey = ref<string>()
const expandedKeys = ref<string[]>(['nav-root', 'page-admin'])
const lastDrop = ref<YdSortableTreeDropEvent>()
const loadCount = ref(0)

const nodeCount = computed(() => {
  const count = (nodes: YdSortableTreeNode[]): number => nodes.reduce((total, node) => total + 1 + (node.children ? count(node.children) : 0), 0)
  return count(data.value)
})

const directoryCount = computed(() => {
  const count = (nodes: YdSortableTreeNode[]): number => nodes.reduce((total, node) => total + (node.kind === 'directory' ? 1 : 0) + (node.children ? count(node.children) : 0), 0)
  return count(data.value)
})

function onDrop(event: YdSortableTreeDropEvent) {
  data.value = moveYdSortableTreeNode(data.value, event)
  lastDrop.value = event
}

function onLoad({ node, children }: { node: YdSortableTreeNode, children: YdSortableTreeNode[] }) {
  const attach = (nodes: YdSortableTreeNode[]): YdSortableTreeNode[] => nodes.map(item => item.key === node.key
    ? { ...item, children, lazy: false }
    : { ...item, children: item.children ? attach(item.children) : undefined })
  data.value = attach(data.value)
}

async function loadChildren(node: YdSortableTreeNode): Promise<YdSortableTreeNode[]> {
  loadCount.value += 1
  await new Promise(resolve => setTimeout(resolve, 300))
  return [{ key: `${node.key}-entry`, label: '异步加载的页面', kind: 'page' }]
}
</script>

<template>
  <YdUiProvider>
    <DemoCard title="可排序树" description="拖拽调整顺序/层级，或聚焦节点后用 Alt + 方向键重排。drop 事件包含节点、父级和深度元数据，数据由调用方受控更新。">
      <div class="sortable-layout">
        <div class="sortable-tree-wrap">
          <YdSortableTree
            v-model:selected-key="selectedKey"
            v-model:expanded-keys="expandedKeys"
            :data="data"
            :max-depth="4"
            :load-children="loadChildren"
            aria-label="可重排导航示例"
            @drop="onDrop"
            @load="onLoad"
          >
            <template #title="{ node, depth, loading }">
              <span class="sortable-title">
                <strong>{{ node.label }}</strong>
                <span class="sortable-key">{{ node.key }}</span>
                <span class="sortable-meta">
                  <span v-if="loading" class="sortable-badge">加载中</span>
                  <span class="sortable-badge">L{{ depth }}</span>
                </span>
              </span>
            </template>
          </YdSortableTree>
        </div>
        <aside class="sortable-summary" aria-label="树结构状态">
          <div class="summary-heading">
            <span>结构摘要</span>
            <YdTag variant="secondary">受控</YdTag>
          </div>
          <div class="summary-stats">
            <div><strong>{{ nodeCount }}</strong><span>节点</span></div>
            <div><strong>{{ directoryCount }}</strong><span>目录</span></div>
            <div><strong>{{ loadCount }}</strong><span>加载</span></div>
          </div>
          <p class="sortable-feedback" role="status">
            当前选中：{{ selectedKey || '无' }}
          </p>
          <p class="summary-note">
            拖动或使用 Alt + 方向键重排；禁用节点和超过最大深度的提案会被组件拒绝。
          </p>
          <pre v-if="lastDrop" class="sortable-event">{{ JSON.stringify({ dragKey: lastDrop.dragKey, dropKey: lastDrop.dropKey, dropPosition: lastDrop.dropPosition, sourceParentKey: lastDrop.sourceParentKey, targetParentKey: lastDrop.targetParentKey, trigger: lastDrop.trigger }, null, 2) }}</pre>
          <p v-else class="summary-note">
            完成一次拖拽后，这里会显示 drop 事件元数据。
          </p>
        </aside>
      </div>
    </DemoCard>
  </YdUiProvider>
</template>

<style scoped>
.sortable-layout { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(260px, 0.7fr); gap: var(--yd-content-gap); align-items: start; }
.sortable-tree-wrap { min-width: 0; }
.sortable-summary { display: grid; gap: 12px; min-width: 0; padding: var(--yd-surface-padding); background: var(--color-fill-1); border: 1px solid var(--color-border-2); border-radius: var(--yd-control-radius); }
.summary-heading { display: flex; align-items: center; justify-content: space-between; color: var(--color-text-1); font-size: 12px; font-weight: 600; }
.summary-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.summary-stats div { display: grid; gap: 2px; padding: 8px; background: var(--color-bg-1); border: 1px solid var(--color-border-2); border-radius: var(--yd-control-radius); }
.summary-stats strong { font-size: 18px; color: var(--color-text-1); }
.summary-stats span,
.summary-note { color: var(--color-text-3); font-size: 12px; line-height: 1.6; }
.summary-note { margin: 0; }

/* 标题槽位：名称与 key 在左，层级/加载等元数据靠右成列 */
.sortable-title { display: flex; flex: 1; gap: 8px; align-items: center; min-width: 0; }
.sortable-title strong { overflow: hidden; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.sortable-key { overflow: hidden; color: var(--color-text-3); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.sortable-meta { display: flex; flex: none; gap: 6px; align-items: center; padding-inline-start: 12px; margin-inline-start: auto; }
.sortable-badge { color: var(--color-text-3); font-size: 11.5px; font-variant-numeric: tabular-nums; }
.sortable-feedback { color: var(--color-text-3); font-size: 12px; margin: 0; }
.sortable-event { overflow: auto; max-height: 180px; padding: 10px; margin-top: 12px; background: var(--color-bg-1); border-radius: 6px; color: var(--color-text-2); font-size: 12px; }

@media (width <= 760px) {
  .sortable-layout { grid-template-columns: 1fr; }
}
</style>

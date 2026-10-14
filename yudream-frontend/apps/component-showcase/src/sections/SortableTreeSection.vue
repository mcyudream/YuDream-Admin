<script setup lang="ts">
import type { YdSortableTreeDropEvent, YdSortableTreeNode } from '@yudream/components'
import { moveYdSortableTreeNode, YdSortableTree, YdUiProvider } from '@yudream/components'
import { ref } from 'vue'
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
            <code>{{ node.key }}</code>
            <span v-if="loading" class="muted">加载中</span>
            <span class="muted">L{{ depth }}</span>
          </span>
        </template>
      </YdSortableTree>
      <p class="sortable-feedback" role="status">
        选中：{{ selectedKey || '无' }}；懒加载次数：{{ loadCount }}
      </p>
      <pre v-if="lastDrop" class="sortable-event">{{ JSON.stringify({ dragKey: lastDrop.dragKey, dropKey: lastDrop.dropKey, dropPosition: lastDrop.dropPosition, sourceParentKey: lastDrop.sourceParentKey, targetParentKey: lastDrop.targetParentKey, trigger: lastDrop.trigger }, null, 2) }}</pre>
    </DemoCard>
  </YdUiProvider>
</template>

<style scoped>
.sortable-title { display: flex; min-width: 0; align-items: center; gap: 8px; }
.sortable-title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sortable-title code,
.muted,
.sortable-feedback { color: var(--color-text-3); font-size: 12px; }
.sortable-event { overflow: auto; max-height: 180px; margin-top: 12px; padding: 10px; border-radius: 6px; background: var(--color-fill-1); color: var(--color-text-2); font-size: 12px; }
</style>

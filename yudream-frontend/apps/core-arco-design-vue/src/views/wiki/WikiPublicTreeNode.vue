<script setup lang="ts">
import type { WikiNode } from '@/api/modules/platform-wiki'
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  nodes: WikiNode[]
  activePath: string
  spaceSlug: string
  depth?: number
  /** 默认展开的目录节点 ID：顶层分组（如「LLM 生成」）作为第一展示层级时直接展开 */
  defaultExpandedIds?: string[]
}>(), {
  depth: 0,
  defaultExpandedIds: () => [],
})

const emit = defineEmits<{
  select: [node: WikiNode]
}>()

const expanded = ref(new Set<string>(props.defaultExpandedIds))
// 空目录（无页面且无子目录）在公开端不渲染，避免出现点不开的死节点
const visibleNodes = computed(() => props.nodes.filter(node => node.nodeType === 'PAGE' || node.children?.length))
// 目录与页面都按嵌套层级缩进：同级对齐，进入子层级整体右移
const indent = computed(() => 8 + (props.depth || 0) * 14)

/** 命中当前页面：path 与 slug 任一匹配都算，避免只有 slug 的节点漏高亮 */
function matchesActive(node: WikiNode): boolean {
  if (!props.activePath) {
    return false
  }
  return node.path === props.activePath || node.slug === props.activePath
}

function containsActive(node: WikiNode): boolean {
  if (node.nodeType === 'PAGE') {
    return matchesActive(node)
  }
  return (node.children || []).some(containsActive)
}

function isExpanded(node: WikiNode): boolean {
  return expanded.value.has(node.id)
}

function toggle(node: WikiNode) {
  const next = new Set(expanded.value)
  if (next.has(node.id)) {
    next.delete(node.id)
  }
  else {
    next.add(node.id)
  }
  expanded.value = next
}

function expandActivePath(nodes: WikiNode[]) {
  for (const node of nodes) {
    if (node.nodeType !== 'DIRECTORY' || !containsActive(node)) {
      continue
    }
    expanded.value.add(node.id)
    expandActivePath(node.children || [])
  }
}

// 页面链接保留真实 href：可复制、可被爬虫抓取，RouterLink 同时保证 SPA 无刷新跳转
function pageHref(node: WikiNode): string {
  return `/wiki/${encodeURIComponent(props.spaceSlug)}/${encodeURI(node.path || node.slug)}`
}

// 目录树或当前路径变化时，自动展开命中路径的祖先目录
watch([() => props.nodes, () => props.activePath], () => {
  expandActivePath(props.nodes)
  expanded.value = new Set(expanded.value)
}, { immediate: true })
</script>

<template>
  <ul class="wiki-tree">
    <li v-for="node in visibleNodes" :key="node.id" class="wiki-tree__item">
      <YdAction
        v-if="node.nodeType === 'DIRECTORY'"
        type="button"
        class="wiki-tree__row wiki-tree__directory"
        :class="{ 'is-expanded': isExpanded(node) }"
        :style="{ paddingLeft: `${indent}px` }"
        :aria-expanded="isExpanded(node)"
        @click="toggle(node)"
      >
        <YdIcon :name="isExpanded(node) ? 'morph:chevron-down' : 'morph:chevron-right'" />
        <span>{{ node.title }}</span>
      </YdAction>
      <RouterLink
        v-else
        class="wiki-tree__row wiki-tree__page"
        :class="{ 'is-active': matchesActive(node) }"
        :style="{ paddingLeft: `${indent + 4}px` }"
        :to="pageHref(node)"
        @click="emit('select', node)"
      >
        <YdIcon name="morph:file-text" />
        <span>{{ node.title }}</span>
      </RouterLink>
      <WikiPublicTreeNode
        v-if="node.nodeType === 'DIRECTORY' && isExpanded(node)"
        :nodes="node.children || []"
        :active-path="activePath"
        :space-slug="spaceSlug"
        :depth="(depth || 0) + 1"
        @select="emit('select', $event)"
      />
    </li>
  </ul>
</template>

<style scoped>
.wiki-tree { display: grid; gap: 2px; margin: 0; padding: 0; list-style: none; }
.wiki-tree__item { min-width: 0; }
/* 行本身是 YdAction/RouterLink 根元素：这里覆盖默认尺寸与居中，长标题可换行 */
.wiki-tree__row { display: flex; width: 100%; min-width: 0; height: auto; min-height: 34px; flex-shrink: 1; justify-content: flex-start; align-items: flex-start; gap: 6px; padding: 7px 10px 7px 8px; border-radius: 6px; color: var(--yb-site-text-2, var(--color-text-2)); font-size: 14px; font-weight: 400; line-height: 1.45; text-align: left; text-decoration: none; white-space: normal; }
.wiki-tree__row :deep(svg) { flex: 0 0 auto; margin-top: 2px; color: var(--yb-site-muted, var(--color-text-3)); }
.wiki-tree__row span { min-width: 0; overflow-wrap: anywhere; }
.wiki-tree__row:hover { background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-tree__row:focus-visible { outline: 2px solid var(--yb-site-primary, var(--color-text-2)); outline-offset: -2px; }
.wiki-tree__page.is-active { background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-heading, var(--color-text-1)); font-weight: 700; }
.wiki-tree__directory { color: var(--yb-site-heading, var(--color-text-1)); font-weight: 600; }
</style>

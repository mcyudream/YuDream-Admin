<script setup lang="ts">
import type { WikiNode, WikiPublicDocument } from '@/api/modules/platform-wiki'
import { computed } from 'vue'
import { groupWikiNavTree } from './wiki-nav-tree'
import WikiPublicTreeNode from './WikiPublicTreeNode.vue'

// 公开阅读站的左侧导航：已发布文档树 + 可阅读的原文档。
// 桌面端放在 sticky 轨道里，移动端放进 YdDrawer，复用同一份 DOM 结构与样式。
const props = defineProps<{
  tree: WikiNode[]
  activePath: string
  spaceSlug: string
  documents: WikiPublicDocument[]
  documentsLoading: boolean
  documentId: string
  treeLoading: boolean
  treeError: string
}>()

const emit = defineEmits<{
  select: [node: WikiNode]
  openDocument: [document: WikiPublicDocument]
}>()

// 原文档镜像页（path=source-{id}）从首屏树摘出：首屏第一展示目标是生成的可读内容，
// 原始资料统一归到「原文档」区（镜像与原文档条目同源，有原文档条目时镜像不再单列）。
const navTree = computed(() => groupWikiNavTree(props.tree))
const mirrorFallback = computed(() => {
  if (props.documents.length) {
    return []
  }
  return navTree.value.mirrors.map(node => ({ id: node.path || node.slug || node.id, title: node.title, to: `/wiki/${encodeURIComponent(props.spaceSlug)}/${encodeURI(node.path || node.slug)}` }))
})

const documentGroups = computed(() => {
  const groups = new Map<string, WikiPublicDocument[]>()
  for (const doc of props.documents) {
    const folder = doc.folderPath || '/'
    if (!groups.has(folder)) {
      groups.set(folder, [])
    }
    groups.get(folder)!.push(doc)
  }
  return [...groups.entries()].map(([folder, items]) => ({ folder, items }))
})
</script>

<template>
  <div class="wiki-nav">
    <div class="wiki-nav__title">
      <YdIcon name="morph:folder" /> 文档目录
    </div>
    <div v-if="treeLoading" class="wiki-nav__skeleton">
      <YdSkeleton variant="text" :count="4" />
    </div>
    <p v-else-if="treeError" class="wiki-nav__hint">
      目录暂不可用
    </p>
    <p v-else-if="!tree.length" class="wiki-nav__hint">
      暂无已发布页面
    </p>
    <nav v-else class="wiki-nav__group" aria-label="文档目录">
      <WikiPublicTreeNode
        :nodes="[navTree.group]"
        :active-path="activePath"
        :space-slug="spaceSlug"
        :default-expanded-ids="[navTree.group.id]"
        @select="emit('select', $event)"
      />
      <WikiPublicTreeNode
        v-if="navTree.rest.length"
        :nodes="navTree.rest"
        :active-path="activePath"
        :space-slug="spaceSlug"
        @select="emit('select', $event)"
      />
    </nav>
    <template v-if="documentsLoading || documents.length || mirrorFallback.length">
      <div class="wiki-nav__title wiki-nav__title--sub">
        <YdIcon name="morph:file-text" /> 原文档
      </div>
      <div v-if="documentsLoading" class="wiki-nav__skeleton">
        <YdSkeleton variant="text" :count="3" />
      </div>
      <nav v-else class="wiki-nav__group wiki-nav__group--documents" aria-label="原文档目录">
        <template v-for="group in documentGroups" :key="group.folder">
          <div v-if="group.folder !== '/'" class="wiki-nav__folder">
            {{ group.folder }}
          </div>
          <RouterLink
            v-for="doc in group.items"
            :key="doc.id"
            class="wiki-nav__doc"
            :class="{ 'is-active': doc.id === documentId }"
            :to="{ query: { doc: doc.id } }"
            @click="emit('openDocument', doc)"
          >
            {{ doc.title }}
          </RouterLink>
        </template>
        <RouterLink
          v-for="mirror in mirrorFallback"
          :key="mirror.id"
          class="wiki-nav__doc"
          :to="mirror.to"
        >
          {{ mirror.title }}
        </RouterLink>
      </nav>
    </template>
  </div>
</template>

<style scoped>
.wiki-nav { display: grid; gap: 2px; min-width: 0; }
.wiki-nav__title { display: flex; align-items: center; gap: 8px; padding: 0 10px 10px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; font-weight: 700; letter-spacing: 0.04em; }
.wiki-nav__title :deep(svg) { color: var(--yb-site-heading, var(--color-text-1)); font-size: 15px; }
.wiki-nav__title--sub { margin-top: 24px; padding-top: 18px; border-top: 1px solid var(--yb-site-border, var(--color-border-2)); }
.wiki-nav__hint { margin: 0; padding: 2px 10px 10px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 13px; }
.wiki-nav__skeleton { display: grid; gap: 8px; padding: 2px 10px 10px; }
.wiki-nav__group { display: grid; gap: 2px; }
.wiki-nav__folder { padding: 8px 10px 2px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; }
.wiki-nav__doc { display: flex; min-width: 0; min-height: 34px; align-items: flex-start; padding: 7px 10px; border-radius: 6px; color: var(--yb-site-text-2, var(--color-text-2)); font-size: 13px; line-height: 1.45; overflow-wrap: anywhere; text-decoration: none; }
.wiki-nav__doc:hover { background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-nav__doc:focus-visible { outline: 2px solid var(--yb-site-primary, var(--color-text-2)); outline-offset: -2px; }
.wiki-nav__doc.is-active { background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-heading, var(--color-text-1)); font-weight: 700; }
</style>

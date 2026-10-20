<script setup lang="ts">
// 区块插入器（参照 WordPress）：搜索、最近使用、按分类的区块网格；模板页签放可复用模板。
// 插入位置默认在选中元素之后，可切换为插入到选中容器内部；所有插入经统一命令管道、一次撤销。
import type { StudioBlockDefinition, StudioInsertPlacement } from '@/utils/site-studio-blocks'
import { computed, ref, watch } from 'vue'
import { useAppAccountStore } from '@/store/modules/app/account'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { findStudioNode } from '@/utils/site-studio'
import {
  canContainStudioBlocks,
  instantiateStudioBlock,
  resolveStudioInsertTarget,
  searchStudioBlocks,
  STUDIO_BLOCK_CATEGORIES,
  studioBlockRegistry,
} from '@/utils/site-studio-blocks'
import StudioSnippetPanel from './StudioSnippetPanel.vue'

defineOptions({ name: 'StudioBlockInserter' })

const store = useSiteStudioStore()
const account = useAppAccountStore()
const toast = useYdToast()

const tab = ref<'blocks' | 'templates'>('blocks')
const query = ref('')
const placement = ref<StudioInsertPlacement>('after')
const RECENT_LIMIT = 6

const selected = computed(() => store.doc && store.selectedNodeId ? findStudioNode(store.doc, store.selectedNodeId) : undefined)
const canInsertInside = computed(() => Boolean(selected.value && canContainStudioBlocks(selected.value)))
watch(canInsertInside, (allowed) => {
  if (!allowed) {
    placement.value = 'after'
  }
})

const blocks = computed(() => studioBlockRegistry.list())
const results = computed(() => searchStudioBlocks(blocks.value, query.value))

// ===== 最近使用：按账号保存在本机，最多 6 个 =====
const recentKey = computed(() => `yd.siteStudio.recentBlocks.${account.userId || 'anonymous'}`)
const recentNames = ref<string[]>([])
watch(recentKey, (key) => {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(key) || '[]')
    recentNames.value = Array.isArray(parsed) ? parsed.filter((name): name is string => typeof name === 'string').slice(0, RECENT_LIMIT) : []
  }
  catch {
    recentNames.value = []
  }
}, { immediate: true })
const recentBlocks = computed(() => recentNames.value.map(name => studioBlockRegistry.get(name)).filter((block): block is StudioBlockDefinition => Boolean(block)))
function remember(name: string) {
  recentNames.value = [name, ...recentNames.value.filter(item => item !== name)].slice(0, RECENT_LIMIT)
  try {
    localStorage.setItem(recentKey.value, JSON.stringify(recentNames.value))
  }
  catch {
    // 本机存储不可用时只影响“最近使用”
  }
}

const grouped = computed(() => STUDIO_BLOCK_CATEGORIES
  .map(category => ({ ...category, blocks: blocks.value.filter(block => block.category === category.key) }))
  .filter(category => category.blocks.length))

const placementHint = computed(() => {
  if (!selected.value) {
    return store.editRegion === 'page' ? '插入到当前页面末尾' : `插入到${store.editRegion === 'header' ? '页头' : '页脚'}末尾`
  }
  return placement.value === 'inside' ? '插入到选中元素内部末尾' : '插入到选中元素之后'
})

function insertBlock(block: StudioBlockDefinition) {
  if (!store.doc) {
    return
  }
  try {
    const nodes = instantiateStudioBlock(block)
    const { target, index } = resolveStudioInsertTarget(store.doc, {
      region: store.editRegion,
      pageId: store.activePageId,
      selectedId: store.selectedNodeId || undefined,
      placement: placement.value,
    })
    store.execute(nodes.map((node, offset) => ({ type: 'insert' as const, target, node, ...(index === undefined ? {} : { index: index + offset }) })))
    store.selectedNodeId = nodes[0]!.id
    remember(block.name)
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '插入失败')
  }
}
</script>

<template>
  <section class="flex flex-col gap-2" aria-label="区块插入器">
    <div class="flex gap-2 items-center justify-between">
      <h3 class="text-xs text-muted-foreground font-medium">
        插入
      </h3>
      <YdSegmented v-model="tab" size="sm" :options="[{ label: '区块', value: 'blocks' }, { label: '模板', value: 'templates' }]" aria-label="插入器视图" />
    </div>

    <template v-if="tab === 'blocks'">
      <YdInput v-model="query" placeholder="搜索区块" aria-label="搜索区块" clearable />
      <div class="text-xs text-muted-foreground flex flex-wrap gap-2 items-center">
        <span>{{ placementHint }}</span>
        <YdSegmented
          v-if="selected"
          v-model="placement"
          size="sm"
          :options="[{ label: '之后', value: 'after' }, { label: '内部', value: 'inside', disabled: !canInsertInside }]"
          aria-label="插入位置"
        />
      </div>

      <template v-if="query.trim()">
        <div v-if="results.length" class="studio-block-grid">
          <YdTooltip v-for="block in results" :key="block.name" :text="block.description || block.title">
            <YdAction v-auth="'platform:cms:edit'" type="button" class="studio-block-item" :aria-label="`插入${block.title}`" @click="insertBlock(block)">
              <YdIcon :name="block.icon" class="size-5" />
              <span>{{ block.title }}</span>
            </YdAction>
          </YdTooltip>
        </div>
        <YdEmpty v-else description="没有匹配的区块" />
      </template>

      <template v-else>
        <section v-if="recentBlocks.length" class="flex flex-col gap-1.5" aria-label="最近使用">
          <h4 class="studio-block-category">
            最近使用
          </h4>
          <div class="studio-block-grid">
            <YdTooltip v-for="block in recentBlocks" :key="block.name" :text="block.description || block.title">
              <YdAction v-auth="'platform:cms:edit'" type="button" class="studio-block-item" :aria-label="`插入${block.title}`" @click="insertBlock(block)">
                <YdIcon :name="block.icon" class="size-5" />
                <span>{{ block.title }}</span>
              </YdAction>
            </YdTooltip>
          </div>
        </section>
        <section v-for="category in grouped" :key="category.key" class="flex flex-col gap-1.5" :aria-label="category.label">
          <h4 class="studio-block-category">
            {{ category.label }}
          </h4>
          <div class="studio-block-grid">
            <YdTooltip v-for="block in category.blocks" :key="block.name" :text="block.description || block.title">
              <YdAction v-auth="'platform:cms:edit'" type="button" class="studio-block-item" :aria-label="`插入${block.title}`" @click="insertBlock(block)">
                <YdIcon :name="block.icon" class="size-5" />
                <span>{{ block.title }}</span>
              </YdAction>
            </YdTooltip>
          </div>
        </section>
      </template>
    </template>

    <StudioSnippetPanel v-else />
  </section>
</template>

<style scoped>
.studio-block-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}
.studio-block-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 10px 4px;
  font-size: 12px;
  border: 1px solid oklch(var(--border));
  border-radius: var(--yd-control-radius);
}
.studio-block-item:hover {
  background: oklch(var(--accent));
}
.studio-block-item > span {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.studio-block-category {
  margin: 4px 0 0;
  font-size: 11px;
  font-weight: 500;
  color: oklch(var(--muted-foreground));
}
</style>

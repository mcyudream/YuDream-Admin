<script setup lang="ts">
// 本地片段库面板：把选中元素保存为可复用区块，并在任意页面/主题区域插入独立副本。
// 存储走 utils/site-studio-snippets.ts：按登录用户隔离、每次读取重新校验、限额兜底。
import type { StudioContainer, StudioNode } from '@/types/site-studio'
import type { StudioSnippet } from '@/utils/site-studio-snippets'
import { computed, ref, watch } from 'vue'
import { useAppAccountStore } from '@/store/modules/app/account'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { findStudioNode } from '@/utils/site-studio'
import {
  createStudioSnippetInsertion,
  deleteStudioSnippet,
  findMissingStudioTokens,
  readStudioSnippets,
  saveStudioSnippet,
  STUDIO_SNIPPET_LIMITS,

} from '@/utils/site-studio-snippets'
import { STUDIO_VOID_TAGS } from '@/utils/site-studio-validation'

defineOptions({ name: 'StudioSnippetPanel' })

const store = useSiteStudioStore()
const account = useAppAccountStore()
const toast = useYdToast()
const modal = useYdModal()

const snippets = ref<StudioSnippet[]>([])
const saveOpen = ref(false)
const saveName = ref('')

const userKey = computed(() => String(account.userId || account.account || 'anonymous'))

function refresh() {
  try {
    snippets.value = readStudioSnippets(window.localStorage, userKey.value)
  }
  catch {
    snippets.value = []
  }
}
watch(userKey, () => {
  saveOpen.value = false
  refresh()
}, { immediate: true })

const selectedNode = computed<StudioNode | null>(() => {
  return store.doc && store.selectedNodeId ? findStudioNode(store.doc, store.selectedNodeId) || null : null
})

function nodeCount(node: StudioNode): number {
  return node.children.reduce((total, child) => total + nodeCount(child), 1)
}

function snippetLabel(snippet: StudioSnippet) {
  const count = snippet.nodes.reduce((total, node) => total + nodeCount(node), 0)
  return `${count} 个元素`
}

function openSaveForm() {
  const node = selectedNode.value
  if (!node) {
    return
  }
  saveName.value = node.text !== undefined && node.text ? node.text.slice(0, STUDIO_SNIPPET_LIMITS.name) : node.type
  saveOpen.value = true
}

function submitSave() {
  const node = selectedNode.value
  if (!node) {
    saveOpen.value = false
    return
  }
  try {
    saveStudioSnippet({ name: saveName.value, node }, window.localStorage, userKey.value)
    toast.success('区块已保存到本机片段库，可在任何页面插入独立副本')
    saveOpen.value = false
    refresh()
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '区块保存失败')
  }
}

function confirmDelete(snippet: StudioSnippet) {
  modal.confirm({
    title: '删除区块',
    content: `「${snippet.name}」将从本机片段库删除；已插入页面的内容不受影响。`,
    confirmButtonText: '删除',
    onConfirm: () => {
      try {
        deleteStudioSnippet(snippet.id, window.localStorage, userKey.value)
        refresh()
      }
      catch (error) {
        toast.error(error instanceof Error ? error.message : '区块删除失败')
      }
    },
  })
}

function resolveInsertTarget(): StudioContainer | null {
  const selected = selectedNode.value
  // 文本/void 节点不能容纳子级，回落到当前编辑目标的根集合
  const parentUsable = selected && !STUDIO_VOID_TAGS.has(selected.tag) && selected.text === undefined
  if (store.editRegion === 'page') {
    return parentUsable && store.selectedNodeId
      ? { parentId: store.selectedNodeId }
      : store.activePageId ? { pageId: store.activePageId } : null
  }
  return { region: store.editRegion }
}

function insertSnippet(snippet: StudioSnippet) {
  const target = resolveInsertTarget()
  if (!target) {
    return
  }
  try {
    const insertion = createStudioSnippetInsertion(snippet, target)
    store.execute(insertion.command)
    store.selectedNodeId = insertion.rootId
    const missing = findMissingStudioTokens(insertion.tokens, store.doc?.theme.tokens || {})
    if (missing.length) {
      toast.warning(`区块引用的主题变量未定义：${missing.join('、')}。引用已在内容中保留，可在「主题」中按名称补充。`)
    }
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '区块插入失败')
  }
}
</script>

<template>
  <section class="flex flex-col gap-2" aria-label="可复用区块">
    <div class="flex items-center justify-between">
      <h3 class="text-xs text-muted-foreground font-medium">
        可复用区块
      </h3>
      <YdButton
        v-if="selectedNode"
        v-auth="'platform:cms:edit'"
        variant="ghost"
        size="sm"
        aria-label="保存选中元素为区块"
        :disabled="store.busy"
        @click="openSaveForm"
      >
        <YdIcon name="morph:save" /> 保存选中
      </YdButton>
    </div>
    <p class="text-xs text-muted-foreground">
      保存的是内容副本；插入到别的页面时会生成全新元素 ID 并移除 DOM id，不与来源联动。
    </p>
    <YdEmpty v-if="!snippets.length" description="选中画布元素后保存为区块，可在任何页面复用" />
    <YdList v-else dense class="max-h-56 overflow-auto">
      <YdListRow v-for="snippet in snippets" :key="snippet.id">
        <div class="studio-snippet-row w-full">
          <span class="flex-1 min-w-0">
            <span class="block truncate">{{ snippet.name }}</span>
            <span class="text-xs text-muted-foreground block">
              {{ snippetLabel(snippet) }} · {{ new Date(snippet.createdAt).toLocaleDateString() }}
            </span>
          </span>
          <span class="flex gap-0.5 items-center">
            <YdButton
              v-auth="'platform:cms:edit'"
              variant="ghost"
              size="sm"
              aria-label="插入区块"
              :disabled="store.busy"
              @click="insertSnippet(snippet)"
            >
              插入
            </YdButton>
            <YdPopconfirm content="确认从本机片段库删除该区块？" @confirm="confirmDelete(snippet)">
              <YdButton variant="ghost" size="sm" aria-label="删除区块" @click.stop>
                <YdIcon name="morph:trash-2" />
              </YdButton>
            </YdPopconfirm>
          </span>
        </div>
      </YdListRow>
    </YdList>

    <YdModal
      v-model="saveOpen"
      title="保存选中元素为区块"
      show-cancel-button
      confirm-button-text="保存"
      @confirm="submitSave"
    >
      <label class="text-sm flex flex-col gap-1">
        <span>区块名称</span>
        <YdInput v-model="saveName" :placeholder="`如：营销头图（${STUDIO_SNIPPET_LIMITS.name} 字以内）`" />
      </label>
      <p class="text-xs text-muted-foreground mt-2">
        区块保存在本机，按登录用户隔离；引用的主题变量（var(--x)）原样保留，目标页面缺少变量时会提示。
      </p>
    </YdModal>
  </section>
</template>

<style scoped>
.studio-snippet-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
  border-radius: var(--yd-control-radius);
}
</style>

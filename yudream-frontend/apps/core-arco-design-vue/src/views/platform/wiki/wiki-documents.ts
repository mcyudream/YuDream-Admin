import type { WikiDraftFields } from './wiki-editor-state'
import type { WikiNode } from '@/api/modules/platform-wiki'
import { nextWorkspaceTabKey } from '@yudream/components/workspace'
import { computed, ref, shallowRef } from 'vue'
import { createWikiEditorState, wikiDraftFields } from './wiki-editor-state'

/**
 * 作者编辑区的多文档管理：一个知识库里可以同时打开多个页面，各自持有独立的草稿、保存态与冲突态。
 *
 * 契约：
 * 1. 每个文档的草稿由 `createWikiEditorState` 持有（dirty / conflict / saved / remote 语义与单页一致），
 *    组件层不再自己存第二份正文，避免「标签标题」和「编辑器正文」各说各话；
 * 2. 打开已存在的文档不会用服务端字段覆盖草稿：`editor.load` 自带同 key 保护，
 *    只有目录刷新拿回同一页面的新版快照时才按冲突语义处理；
 * 3. 关闭标签后激活谁由 `nextWorkspaceTabKey` 决定（与组件库同一套规则），
 *    是否确认丢弃草稿由调用方决定，管理器只做集合运算。
 */

export interface WikiDocument {
  /** 文档键：同一知识库内按节点唯一，跨知识库互不覆盖 */
  key: string
  spaceId: string
  nodeId: string
  /** 最近一次从目录拿到的节点快照（发布态、路径、slug 等元数据来源） */
  node: WikiNode
  editor: ReturnType<typeof createWikiEditorState>
}

export function wikiDocumentKey(spaceId: string, nodeId: string): string {
  return `${spaceId}:${nodeId}`
}

export function createWikiDocuments() {
  // shallowRef：文档内部持有 ComputedRef（dirty）与 reactive 草稿，深层 ref 化的类型会被 Vue 解包成普通值，
  // 导致 `.dirty.value` 在类型层消失；改由「换数组引用」显式通知，行为与语义一致。
  const documents = shallowRef<WikiDocument[]>([])
  const activeKey = ref('')

  const active = computed(() => documents.value.find(doc => doc.key === activeKey.value) ?? null)
  const dirtyDocuments = computed(() => documents.value.filter(doc => doc.editor.dirty.value))
  const anyDirty = computed(() => dirtyDocuments.value.length > 0)
  const anyConflict = computed(() => documents.value.some(doc => doc.editor.state.conflict))

  /** 打开或激活文档：已打开时保留草稿，仅刷新节点快照 */
  function open(spaceId: string, node: WikiNode): WikiDocument | null {
    if (!spaceId || !node?.id) {
      return null
    }
    const key = wikiDocumentKey(spaceId, node.id)
    const existing = documents.value.find(doc => doc.key === key)
    if (existing) {
      existing.node = node
      existing.editor.load(key, wikiDraftFields(node))
      documents.value = [...documents.value]
      activeKey.value = key
      return existing
    }
    const document: WikiDocument = {
      key,
      spaceId,
      nodeId: node.id,
      node,
      editor: createWikiEditorState(),
    }
    document.editor.load(key, wikiDraftFields(node))
    documents.value = [...documents.value, document]
    activeKey.value = key
    return document
  }

  /** 关闭文档并返回关闭后的激活键；文档不存在时保持现状 */
  function close(key: string): string {
    if (!documents.value.some(doc => doc.key === key)) {
      return activeKey.value
    }
    const nextActive = nextWorkspaceTabKey(documents.value, activeKey.value, key)
    documents.value = documents.value.filter(doc => doc.key !== key)
    activeKey.value = nextActive
    return nextActive
  }

  /** 目录刷新后的节点快照同步：草稿受同 key 保护，不会被服务端字段静默覆盖 */
  function syncNodes(spaceId: string, nodes: WikiNode[]) {
    if (!spaceId || !documents.value.length) {
      return
    }
    const byId = new Map(nodes.map(node => [node.id, node]))
    for (const document of documents.value) {
      if (document.spaceId !== spaceId) {
        continue
      }
      const node = byId.get(document.nodeId)
      if (!node) {
        continue
      }
      document.node = node
      document.editor.load(document.key, wikiDraftFields(node))
    }
    documents.value = [...documents.value]
  }

  /** 切换知识库：只保留目标知识库的文档，其余标签关闭 */
  function keepSpace(spaceId: string) {
    const kept = documents.value.filter(doc => doc.spaceId === spaceId)
    if (kept.length === documents.value.length) {
      return
    }
    documents.value = kept
    if (!kept.some(doc => doc.key === activeKey.value)) {
      activeKey.value = kept[kept.length - 1]?.key ?? ''
    }
  }

  /** 节点被删除后关闭其标签 */
  function closeByNode(nodeId: string) {
    const target = documents.value.find(doc => doc.nodeId === nodeId)
    if (target) {
      close(target.key)
    }
  }

  /** 全部关闭（离开工作区并确认放弃草稿后调用） */
  function reset() {
    documents.value = []
    activeKey.value = ''
  }

  function findByNode(nodeId: string): WikiDocument | null {
    return documents.value.find(doc => doc.nodeId === nodeId) ?? null
  }

  return {
    documents,
    activeKey,
    active,
    dirtyDocuments,
    anyDirty,
    anyConflict,
    open,
    close,
    syncNodes,
    keepSpace,
    closeByNode,
    reset,
    findByNode,
  }
}

export type { WikiDraftFields }

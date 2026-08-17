<script setup lang="ts">
import { computed, inject, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { publishWikiNode, saveWikiNode, unpublishWikiNode, deleteWikiNode } from '@/api/modules/platform-wiki'
import CmsMarkdownEditor from '../../cms/components/CmsMarkdownEditor.vue'
import { rewriteApiFileUrls } from '@/utils/api-file-url'
import {
  pageTypeColor,
  pageTypeLabel,
  pageTypeOptions,
  resolveWikiLink,
  wikilinksToMarkdown,
  wikiWorkbenchKey,
} from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useFaToast()
const modal = useFaModal()

const editorTitle = ref('')
const editorBody = ref('')
const editorPageType = ref('concept')
const editorRelated = ref<string[]>([])
const editorTags = ref<string[]>([])
const editorSummary = ref('')
const saving = ref(false)
const viewMode = ref<'edit' | 'preview'>('edit')
const treeKeyword = ref('')

const node = computed(() => store.selectedNode.value)

const filteredTree = computed(() => {
  const keyword = treeKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return store.flatTree.value
  }
  return store.flatTree.value.filter(item => item.title.toLowerCase().includes(keyword))
})

const previewMarkdown = computed(() => rewriteApiFileUrls(wikilinksToMarkdown(editorBody.value)))

watch(node, (value) => {
  if (!value) {
    return
  }
  editorTitle.value = value.title
  editorBody.value = value.body ?? value.markdown ?? ''
  editorPageType.value = value.pageType ?? 'concept'
  editorRelated.value = [...(value.related ?? [])]
  editorTags.value = [...(value.tags ?? [])]
  editorSummary.value = value.summary ?? ''
}, { immediate: true })

async function saveNode() {
  if (!store.spaceId.value || !node.value) {
    return
  }
  saving.value = true
  try {
    await saveWikiNode(store.spaceId.value, {
      id: node.value.id,
      title: editorTitle.value,
      slug: node.value.slug,
      nodeType: node.value.nodeType,
      sort: node.value.sort,
      body: editorBody.value,
      pageType: editorPageType.value,
      sources: node.value.sources ?? [],
      related: editorRelated.value,
      tags: editorTags.value,
      summary: editorSummary.value,
    })
    toast.success('已保存')
    await store.reloadTree()
  }
  finally {
    saving.value = false
  }
}

async function publish() {
  if (!node.value) {
    return
  }
  await publishWikiNode(node.value.id)
  toast.success('已发布')
  await store.reloadTree()
}

async function unpublish() {
  if (!node.value) {
    return
  }
  await unpublishWikiNode(node.value.id)
  toast.success('已取消发布')
  await store.reloadTree()
}

// 删除页面 / 目录（目录需先清空子节点，后端会拦截）
const deletingNodeId = ref('')

function removeNode(item: { id: string, title: string, nodeType: string }) {
  const isDir = item.nodeType === 'DIRECTORY'
  modal.confirm({
    title: `删除${isDir ? '目录' : '页面'}`,
    content: `确定删除「${item.title}」吗？${isDir ? '目录内存在子节点时无法删除。' : '删除后不可恢复。'}`,
    onConfirm: async () => {
      deletingNodeId.value = item.id
      try {
        await deleteWikiNode(item.id)
        if (node.value?.id === item.id) {
          store.selectNode(null)
        }
        toast.success('已删除')
        await store.reloadTree()
      }
      finally {
        deletingNodeId.value = ''
      }
    },
  })
}

// 新建页面 / 目录
const createVisible = ref(false)
const creating = ref(false)
const createForm = ref({ title: '', slug: '', nodeType: 'PAGE' as 'PAGE' | 'DIRECTORY' })

function openCreate(nodeType: 'PAGE' | 'DIRECTORY') {
  createForm.value = { title: '', slug: '', nodeType }
  createVisible.value = true
}

async function createNode() {
  if (!store.spaceId.value || !createForm.value.title.trim()) {
    toast.warning('请填写标题')
    return
  }
  creating.value = true
  try {
    const parent = node.value?.nodeType === 'DIRECTORY' ? node.value : null
    await saveWikiNode(store.spaceId.value, {
      title: createForm.value.title.trim(),
      slug: createForm.value.slug.trim() || createForm.value.title.trim().toLowerCase().replace(/\s+/g, '-'),
      nodeType: createForm.value.nodeType,
      parentId: parent?.id,
      sort: 0,
      body: '',
      pageType: 'concept',
    })
    toast.success('已创建')
    createVisible.value = false
    await store.reloadTree()
  }
  finally {
    creating.value = false
  }
}

function onPreviewClick(event: MouseEvent) {
  const title = resolveWikiLink(event)
  if (title) {
    store.openPage({ title })
  }
}

function removeSourceChip(source: string) {
  if (!node.value) {
    return
  }
  modal.confirm({
    title: '移除资料来源关联',
    content: `仅从页面元数据中移除「${source}」的关联标记，不会删除资料本身。`,
    onConfirm: async () => {
      if (!node.value || !store.spaceId.value) {
        return
      }
      await saveWikiNode(store.spaceId.value, {
        id: node.value.id,
        sources: (node.value.sources ?? []).filter(item => item !== source),
      })
      toast.success('已移除')
      await store.reloadTree()
    },
  })
}
</script>

<template>
  <div class="dir-panel">
    <!-- 目录树 -->
    <aside class="dir-tree">
      <div class="dir-tree__toolbar">
        <FaInput v-model="treeKeyword" clearable placeholder="搜索页面…">
          <template #start>
            <FaIcon name="i-ri:search-line" />
          </template>
        </FaInput>
        <div class="dir-tree__actions">
          <FaTooltip text="新建页面">
            <FaButton size="icon-sm" variant="ghost" @click="openCreate('PAGE')">
              <FaIcon name="i-ri:file-add-line" />
            </FaButton>
          </FaTooltip>
          <FaTooltip text="新建目录">
            <FaButton size="icon-sm" variant="ghost" @click="openCreate('DIRECTORY')">
              <FaIcon name="i-ri:folder-add-line" />
            </FaButton>
          </FaTooltip>
          <FaTooltip text="刷新目录">
            <FaButton size="icon-sm" variant="ghost" :loading="store.loadingTree.value" @click="store.reloadTree()">
              <FaIcon name="i-ri:refresh-line" />
            </FaButton>
          </FaTooltip>
        </div>
      </div>

      <FaScrollArea class="dir-tree__list">
        <button
          v-for="item in filteredTree"
          :key="item.id"
          type="button"
          class="dir-node"
          :class="{ 'dir-node--active': node?.id === item.id }"
          :style="{ paddingLeft: `${10 + item._depth * 16}px` }"
          @click="store.selectNode(item)"
        >
          <FaIcon
            :name="item.nodeType === 'DIRECTORY' ? 'i-ri:folder-3-line' : 'i-ri:file-text-line'"
            class="dir-node__icon"
            :class="{ 'dir-node__icon--dir': item.nodeType === 'DIRECTORY' }"
          />
          <span class="dir-node__title" :title="item.title">{{ item.title }}</span>
          <span
            v-if="item.nodeType === 'PAGE' && item.pageType"
            class="dir-node__type"
            :style="{ color: pageTypeColor(item.pageType), background: `${pageTypeColor(item.pageType)}1a` }"
          >{{ pageTypeLabel(item.pageType) }}</span>
          <span v-if="item.nodeType === 'PAGE' && item.publishedVersionId" class="dir-node__dot" title="已发布" />
          <span
            class="dir-node__delete"
            :class="{ 'dir-node__delete--busy': deletingNodeId === item.id }"
            :title="`删除${item.nodeType === 'DIRECTORY' ? '目录' : '页面'}`"
            @click.stop="removeNode(item)"
          >
            <FaIcon name="i-ri:delete-bin-line" />
          </span>
        </button>
        <div v-if="!filteredTree.length" class="dir-tree__empty">
          <FaIcon name="i-ri:folder-open-line" />
          <p>{{ treeKeyword ? '没有匹配的页面' : '暂无页面，先在「资料源」导入资料' }}</p>
        </div>
      </FaScrollArea>
    </aside>

    <!-- 编辑区 -->
    <section v-if="node" class="dir-editor">
      <header class="dir-editor__head">
        <div class="dir-editor__title-row">
          <FaInput v-model="editorTitle" class="dir-editor__title" placeholder="页面标题" :disabled="node.nodeType === 'DIRECTORY'" />
          <div class="dir-editor__mode">
            <button type="button" :class="{ active: viewMode === 'edit' }" @click="viewMode = 'edit'">
              <FaIcon name="i-ri:edit-line" /> 编辑
            </button>
            <button type="button" :class="{ active: viewMode === 'preview' }" @click="viewMode = 'preview'">
              <FaIcon name="i-ri:eye-line" /> 预览
            </button>
          </div>
        </div>
        <div class="dir-editor__meta">
          <FaSelect v-model="editorPageType" :options="pageTypeOptions" class="dir-editor__type" />
          <FaInput v-model="editorSummary" placeholder="一句话摘要（展示在页面头部）" class="dir-editor__summary" />
        </div>
        <div class="dir-editor__meta">
          <a-input-tag v-model:model-value="editorRelated" placeholder="相关页面（wikilink 标题）" allow-clear class="dir-editor__tags" />
          <a-input-tag v-model:model-value="editorTags" placeholder="标签" allow-clear class="dir-editor__tags" />
        </div>
        <div v-if="node.sources?.length" class="dir-editor__sources">
          <span class="dir-editor__sources-label"><FaIcon name="i-ri:database-2-line" /> 资料来源</span>
          <span v-for="source in node.sources" :key="source" class="dir-source-chip">
            {{ source }}
            <FaIcon name="i-ri:close-line" class="dir-source-chip__close" @click="removeSourceChip(source)" />
          </span>
        </div>
        <div class="dir-editor__actions">
          <FaButton :loading="saving" @click="saveNode">
            <FaIcon name="i-ri:save-3-line" /> 保存
          </FaButton>
          <FaButton v-if="node.nodeType === 'PAGE' && !node.publishedVersionId" variant="outline" @click="publish">
            <FaIcon name="i-ri:send-plane-line" /> 发布
          </FaButton>
          <FaButton v-if="node.nodeType === 'PAGE' && node.publishedVersionId" variant="outline" @click="unpublish">
            <FaIcon name="i-ri:arrow-go-back-line" /> 取消发布
          </FaButton>
          <FaButton variant="destructive" :loading="deletingNodeId === node.id" @click="removeNode(node)">
            <FaIcon name="i-ri:delete-bin-line" /> 删除
          </FaButton>
          <span v-if="node.path" class="dir-editor__path">{{ node.path }}</span>
        </div>
      </header>

      <div v-if="viewMode === 'edit'" class="dir-editor__body">
        <CmsMarkdownEditor v-model="editorBody" />
      </div>
      <FaScrollArea v-else class="dir-editor__preview" @click="onPreviewClick">
        <MdPreview
          :model-value="previewMarkdown"
          language="zh-CN"
          preview-theme="github"
          code-theme="github"
          class="dir-markdown"
        />
      </FaScrollArea>
    </section>

    <section v-else class="dir-empty">
      <FaIcon name="i-ri:node-tree" />
      <strong>选择左侧页面开始编辑</strong>
      <p>页面内容支持 [[wikilink]] 语法，预览中可点击跳转</p>
    </section>

    <FaModal
      v-model="createVisible"
      :title="createForm.nodeType === 'PAGE' ? '新建页面' : '新建目录'"
      show-cancel-button
      :confirm-button-loading="creating"
      @confirm="createNode"
    >
      <div class="dir-create-form">
        <label class="dir-field">
          <span>标题</span>
          <FaInput v-model="createForm.title" placeholder="标题" />
        </label>
        <label class="dir-field">
          <span>slug（留空自动生成）</span>
          <FaInput v-model="createForm.slug" placeholder="url-friendly-slug" />
        </label>
        <p v-if="node?.nodeType === 'DIRECTORY'" class="dir-field-hint">
          将创建在当前目录「{{ node.title }}」下
        </p>
      </div>
    </FaModal>
  </div>
</template>

<style scoped>
.dir-panel {
  display: flex;
  height: 100%;
  min-height: 0;
}

.dir-tree {
  display: flex;
  flex-direction: column;
  width: 264px;
  flex-shrink: 0;
  border-right: 1px solid var(--color-border-2);
  background: var(--color-bg-1);
}

.dir-tree__toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px;
  border-bottom: 1px solid var(--color-border-2);
}

.dir-tree__actions {
  display: flex;
  flex-shrink: 0;
}

.dir-tree__list {
  flex: 1;
  min-height: 0;
  padding: 8px;
}

.dir-node {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  width: 100%;
  min-width: 0;
  padding: 7px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-1);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  line-height: 1.45;
  text-align: left;
}

.dir-node:hover {
  background: var(--color-fill-2);
}

.dir-node--active {
  background: var(--color-fill-2);
  color: var(--color-text-1);
  font-weight: 600;
}

.dir-node__icon {
  flex-shrink: 0;
  color: var(--color-text-3);
  font-size: 15px;
}

.dir-node__icon--dir {
  color: var(--color-text-2);
}

.dir-node--active .dir-node__icon {
  color: var(--color-text-1);
}

.dir-node__title {
  display: -webkit-box;
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: normal;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.dir-node__type {
  flex-shrink: 0;
  align-self: flex-start;
  margin-left: auto;
  padding: 1px 6px;
  border-radius: 5px;
  font-size: 10px;
  font-weight: 600;
}

.dir-node__dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--color-text-3);
}

.dir-node__delete {
  display: none;
  flex-shrink: 0;
  align-items: center;
  padding: 2px;
  border-radius: 4px;
  color: var(--color-text-3);
  font-size: 13px;
}

.dir-node:hover .dir-node__delete,
.dir-node__delete--busy {
  display: inline-flex;
}

.dir-node__delete:hover {
  background: var(--color-fill-2);
  color: var(--color-danger-6, rgb(var(--danger-6)));
}

.dir-tree__empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 40px 12px;
  color: var(--color-text-3);
  font-size: 12px;
  text-align: center;
}

.dir-tree__empty :deep(svg) {
  font-size: 26px;
}

.dir-editor {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--color-bg-1);
}

.dir-editor__head {
  display: grid;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border-2);
}

.dir-editor__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dir-editor__title {
  flex: 1;
  font-weight: 600;
}

.dir-editor__mode {
  display: flex;
  flex-shrink: 0;
  padding: 3px;
  border-radius: 9px;
  background: var(--color-fill-2);
}

.dir-editor__mode button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.dir-editor__mode button.active {
  background: var(--color-bg-1);
  color: var(--color-text-1);
  font-weight: 600;
  box-shadow: 0 1px 3px rgb(0 0 0 / 8%);
}

.dir-editor__meta {
  display: flex;
  gap: 8px;
}

.dir-editor__type {
  width: 150px;
  flex-shrink: 0;
}

.dir-editor__summary {
  flex: 1;
}

.dir-editor__tags {
  flex: 1;
}

.dir-editor__sources {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.dir-editor__sources-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-3);
  font-size: 12px;
}

.dir-source-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  font-size: 11px;
}

.dir-source-chip__close {
  cursor: pointer;
}

.dir-source-chip__close:hover {
  color: var(--color-text-1);
}

.dir-editor__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dir-editor__path {
  margin-left: auto;
  color: var(--color-text-3);
  font-size: 12px;
}

.dir-editor__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px 16px;
}

.dir-editor__body :deep(.md-editor) {
  height: 100% !important;
}

.dir-editor__preview {
  flex: 1;
  min-height: 0;
  padding: 8px 24px 24px;
}

.dir-markdown :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.dir-markdown :deep(a[href^='wiki://']) {
  color: var(--color-text-1);
  text-decoration: none;
  border-bottom: 1px dashed var(--color-border-3);
  cursor: pointer;
}

.dir-empty {
  display: grid;
  flex: 1;
  place-content: center;
  justify-items: center;
  gap: 8px;
  color: var(--color-text-3);
  background: var(--color-bg-1);
}

.dir-empty :deep(svg) {
  color: var(--color-text-1);
  font-size: 40px;
}

.dir-empty strong {
  color: var(--color-text-1);
  font-size: 15px;
}

.dir-empty p {
  margin: 0;
  font-size: 12px;
}

.dir-create-form {
  display: grid;
  gap: 14px;
}

.dir-field {
  display: grid;
  gap: 6px;
}

.dir-field > span {
  color: var(--color-text-3);
  font-size: 13px;
}

.dir-field-hint {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
}
</style>

<script setup lang="ts">
import type { StudioNode } from '@/types/site-studio'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { findStudioNode } from '@/utils/site-studio'
import { uploadStudioAsset } from './studio-upload'
import StudioInspectorSection from './StudioInspectorSection.vue'
import StudioNodeBindings from './StudioNodeBindings.vue'

defineOptions({ name: 'StudioInspector' })

const store = useSiteStudioStore()
const toast = useYdToast()

const node = computed<StudioNode | null>(() => {
  return store.doc && store.selectedNodeId ? findStudioNode(store.doc, store.selectedNodeId) || null : null
})

const breakpoint = computed({
  get: () => store.breakpoint,
  set: (value) => { store.breakpoint = value },
})
const breakpointOptions = [
  { label: '桌面', value: 'desktop' },
  { label: '平板', value: 'tablet' },
  { label: '手机', value: 'mobile' },
]

// ===== 属性行编辑（键值行，提交为整表） =====

interface Row { key: string, value: string }

const attributeRows = ref<Row[]>([])
const styleRows = ref<Row[]>([])
// 最近一次重载时可见的键集合：提交时只处理这些键的删除，
// 编辑期间由 AI/撤销/画布新增的键原样保留，避免外部变化被提交覆盖
const attributeKnownKeys = ref<string[]>([])
const styleKnownKeys = ref<string[]>([])

const currentStyles = computed<Record<string, string>>(() => {
  if (!node.value) {
    return {}
  }
  if (breakpoint.value === 'desktop') {
    return node.value.styles
  }
  return node.value.responsive?.[breakpoint.value] || {}
})

/** 从当前节点状态重建编辑行；提交失败与外部变化都走这里回读 */
function reloadRows() {
  attributeRows.value = Object.entries(node.value?.attributes || {}).map(([key, value]) => ({ key, value }))
  attributeKnownKeys.value = Object.keys(node.value?.attributes || {})
  styleRows.value = Object.entries(currentStyles.value).map(([key, value]) => ({ key, value }))
  styleKnownKeys.value = Object.keys(currentStyles.value)
}

// 选中节点或断点变化时重载编辑行（含 AI/撤销在外部的修改）
watch(() => [node.value?.id, JSON.stringify(node.value?.attributes), JSON.stringify(currentStyles.value), breakpoint.value] as const, () => {
  reloadRows()
}, { immediate: true })

function commitAttributes() {
  if (!node.value) {
    return
  }
  const map: Record<string, string> = { ...node.value.attributes }
  for (const key of attributeKnownKeys.value) {
    delete map[key]
  }
  for (const row of attributeRows.value) {
    const key = row.key.trim()
    if (key) {
      map[key] = row.value
    }
  }
  try {
    store.execute({ type: 'update-node', nodeId: node.value.id, patch: { attributes: map } })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '属性保存失败')
    reloadRows()
  }
}

function isColorKey(key: string) {
  return /^(?:color|background-color|border-(?:top-|right-|bottom-|left-)?color|outline-color|text-decoration-color)$/i.test(key)
}

const quickFields = [
  { key: 'gap', label: '元素间距', placeholder: '16px' },
  { key: 'padding', label: '内边距', placeholder: '24px' },
  { key: 'width', label: '宽度', placeholder: '100% / auto' },
  { key: 'min-height', label: '最小高度', placeholder: '80px' },
  { key: 'font-size', label: '字号', placeholder: '16px' },
  { key: 'border-radius', label: '圆角', placeholder: '12px' },
]

/** 平板/手机只显示本断点覆盖值；继承的桌面值作为占位提示，清空即移除覆盖 */
function quickPlaceholder(field: { key: string, placeholder: string }) {
  const inherited = breakpoint.value !== 'desktop' ? node.value?.styles[field.key] : undefined
  return inherited ? `继承桌面：${inherited}` : field.placeholder
}

function quickStyle(key: string, value: string) {
  if (!node.value) {
    return
  }
  try {
    store.execute({ type: 'set-style', nodeId: node.value.id, styles: { [key]: value || null }, breakpoint: breakpoint.value })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '布局修改失败')
  }
}

function commitStyles() {
  if (!node.value) {
    return
  }
  const map: Record<string, string | null> = {}
  for (const key of Object.keys(currentStyles.value)) {
    map[key] = null
  }
  for (const key of styleKnownKeys.value) {
    if (styleRows.value.some(row => row.key.trim() === key)) {
      delete map[key]
    }
    else {
      map[key] = null
    }
  }
  for (const row of styleRows.value) {
    const key = row.key.trim()
    if (key) {
      map[key] = row.value
    }
  }
  try {
    store.execute({ type: 'set-style', nodeId: node.value.id, styles: map, breakpoint: breakpoint.value })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '样式保存失败')
    reloadRows()
  }
}

// ===== 文本：输入防抖提交、失焦立即提交；连续输入合并为一条撤销 =====

const TEXT_COMMIT_DELAY = 300
const textDraft = ref<{ nodeId: string, value: string } | null>(null)
let textTimer: ReturnType<typeof setTimeout> | undefined

const textValue = computed(() => textDraft.value && textDraft.value.nodeId === node.value?.id ? textDraft.value.value : node.value?.text ?? '')

function onTextInput(value: string) {
  if (!node.value) {
    return
  }
  textDraft.value = { nodeId: node.value.id, value }
  clearTimeout(textTimer)
  textTimer = setTimeout(flushText, TEXT_COMMIT_DELAY)
}

/** 提交到输入时的节点；切换选中或卸载前也会先落盘，不会写到新选中的元素上 */
function flushText() {
  clearTimeout(textTimer)
  textTimer = undefined
  const draft = textDraft.value
  textDraft.value = null
  if (!draft) {
    return
  }
  try {
    store.execute({ type: 'set-text', nodeId: draft.nodeId, text: draft.value }, { mergeKey: `text:${draft.nodeId}` })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '文本保存失败')
  }
}

watch(() => node.value?.id, flushText)
onBeforeUnmount(flushText)

// ===== 资源引用（src/href/poster 属性快速取自资源库） =====

const isMediaAttribute = computed(() => {
  const key = attributeRows.value.map(row => row.key.trim()).join('|')
  return /(?:^|\|)(?:src|poster|href)(?:\||$)/.test(key)
})

const assetOptions = computed(() => (store.doc?.assets || []).map(asset => ({
  label: `${asset.name}（${asset.mimeType}）`,
  value: asset.url,
})))

async function uploadForAttribute() {
  const documentId = store.doc?.id
  const context = store.contextKey
  const revision = store.editRevision
  const nodeId = node.value?.id
  if (!documentId || !nodeId) {
    return
  }
  try {
    const asset = await uploadStudioAsset('image/*,video/*,audio/*')
    if (!asset) {
      return
    }
    if (store.doc?.id !== documentId || store.contextKey !== context || store.editRevision !== revision || node.value?.id !== nodeId) {
      throw new Error('上传期间编辑目标已变化，资源未写入其他元素，请从资源库重新选择')
    }
    const attributes = { ...node.value.attributes, src: asset.url }
    store.execute({ type: 'batch', commands: [
      { type: 'set-assets', assets: [...store.doc.assets, asset] },
      { type: 'update-node', nodeId, patch: { attributes } },
    ] })
    toast.success('资源已上传并填入')
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '资源上传失败')
  }
}

// ===== 节点动作：上移/下移/复制/删除/选父级 =====

/** 定位节点所在兄弟集合与下标（页面根 / 主题区域 / 任意父节点子级） */
function locateSibling(nodeId: string): { siblings: StudioNode[], index: number, parentId: string | null, pageId: string | null, region: 'header' | 'footer' | null } | null {
  const document = store.doc
  if (!document) {
    return null
  }
  const rootCollections: { siblings: StudioNode[], parentId: string | null, pageId: string | null, region: 'header' | 'footer' | null }[] = [
    ...document.pages.map(page => ({ siblings: page.root, parentId: null, pageId: page.id, region: null })),
    { siblings: document.theme.header, parentId: null, pageId: null, region: 'header' as const },
    { siblings: document.theme.footer, parentId: null, pageId: null, region: 'footer' as const },
  ]
  const visit = (nodes: StudioNode[], parentId: string | null): { siblings: StudioNode[], index: number, parentId: string | null } | undefined => {
    for (const [index, candidate] of nodes.entries()) {
      if (candidate.id === nodeId) {
        return { siblings: nodes, index, parentId }
      }
      const found = visit(candidate.children, candidate.id)
      if (found) {
        return found
      }
    }
    return undefined
  }
  for (const collection of rootCollections) {
    const found = visit(collection.siblings, collection.parentId)
    if (found) {
      return { ...found, pageId: collection.pageId, region: collection.region }
    }
  }
  return null
}

function moveSelected(offset: -1 | 1) {
  if (!node.value) {
    return
  }
  const located = locateSibling(node.value.id)
  if (!located) {
    return
  }
  const nextIndex = located.index + offset
  if (nextIndex < 0 || nextIndex >= located.siblings.length) {
    return
  }
  const target = located.parentId
    ? { parentId: located.parentId }
    : located.pageId
      ? { pageId: located.pageId }
      : { region: located.region! }
  store.execute({ type: 'move', nodeId: node.value.id, target, index: nextIndex })
}

function duplicateSelected() {
  if (node.value) {
    store.execute({ type: 'duplicate', nodeId: node.value.id })
  }
}

function selectParent() {
  if (!node.value) {
    return
  }
  const located = locateSibling(node.value.id)
  if (located?.parentId) {
    store.selectedNodeId = located.parentId
  }
}

function removeSelected() {
  if (!node.value) {
    return
  }
  try {
    store.execute({ type: 'remove', nodeId: node.value.id })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '删除失败')
  }
}
</script>

<template>
  <div class="p-3 flex flex-col gap-2">
    <YdEmpty
      v-if="!node"
      description="在画布或图层树选中元素后，这里编辑它的文本、属性与样式"
    />
    <template v-else>
      <div class="studio-inspector-head">
        <div class="studio-inspector-head__title" :title="`${node.type} <${node.tag}>`">
          <strong>{{ node.type }}</strong>
          <span class="text-xs text-muted-foreground">&lt;{{ node.tag }}&gt;</span>
        </div>
        <div class="studio-inspector-head__actions">
          <YdTooltip text="选中父级">
            <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="选中父级" :disabled="!locateSibling(node.id)?.parentId" @click="selectParent">
              <YdIcon name="morph:corner-left-up" />
            </YdButton>
          </YdTooltip>
          <YdTooltip text="上移">
            <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="上移" @click="moveSelected(-1)">
              <YdIcon name="morph:arrow-up" />
            </YdButton>
          </YdTooltip>
          <YdTooltip text="下移">
            <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="下移" @click="moveSelected(1)">
              <YdIcon name="morph:arrow-down" />
            </YdButton>
          </YdTooltip>
          <YdTooltip text="复制">
            <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="复制" @click="duplicateSelected">
              <YdIcon name="morph:copy" />
            </YdButton>
          </YdTooltip>
          <YdPopconfirm content="确认删除该元素及其子级？" @confirm="removeSelected">
            <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="删除" @click.stop>
              <YdIcon name="morph:trash-2" />
            </YdButton>
          </YdPopconfirm>
        </div>
      </div>

      <StudioInspectorSection v-if="node.text !== undefined" id="content" title="内容" :summary="textValue.slice(0, 24)">
        <YdTextarea
          :model-value="textValue"
          :rows="3"
          placeholder="元素文本"
          @update:model-value="onTextInput(String($event))"
          @blur="flushText"
        />
      </StudioInspectorSection>

      <StudioInspectorSection id="layout" title="布局" :summary="currentStyles.display || node.styles.display || ''">
        <div class="gap-2 grid grid-cols-2">
          <label class="text-xs flex flex-col gap-1 min-w-0"><span>布局</span><YdSelect class="min-w-0 w-full" :model-value="currentStyles.display || node.styles.display || 'block'" :options="[{ label: '普通流', value: 'block' }, { label: '弹性布局', value: 'flex' }, { label: '网格布局', value: 'grid' }]" aria-label="元素布局" @update:model-value="quickStyle('display', String($event))" /></label>
          <label class="text-xs flex flex-col gap-1 min-w-0"><span>排列方向</span><YdSelect class="min-w-0 w-full" :model-value="currentStyles['flex-direction'] || node.styles['flex-direction'] || 'row'" :options="[{ label: '水平', value: 'row' }, { label: '垂直', value: 'column' }]" aria-label="排列方向" @update:model-value="quickStyle('flex-direction', String($event))" /></label>
          <label v-for="field in quickFields" :key="field.key" class="text-xs flex flex-col gap-1 min-w-0"><span>{{ field.label }}</span><YdInput :model-value="currentStyles[field.key] || ''" :placeholder="quickPlaceholder(field)" :aria-label="field.label" @change="quickStyle(field.key, String($event.target.value))" /></label>
        </div>
      </StudioInspectorSection>

      <StudioNodeBindings :node="node" />

      <StudioInspectorSection id="attrs" title="属性" :summary="`${attributeRows.length} 项`">
        <div class="flex gap-1 justify-end">
          <YdButton v-if="isMediaAttribute" v-auth="'platform:cms:edit'" variant="ghost" size="sm" @click="uploadForAttribute">
            <YdIcon name="morph:cloud-upload" /> 上传资源填入
          </YdButton>
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="添加属性" @click="attributeRows.push({ key: '', value: '' }); commitAttributes()">
            <YdIcon name="morph:plus" /> 添加
          </YdButton>
        </div>
        <div v-for="(row, index) in attributeRows" :key="`attr-${index}`" class="flex gap-1 items-center">
          <YdInput v-model="row.key" class="w-28" placeholder="属性名" aria-label="属性名" @change="commitAttributes" />
          <YdSelect
            v-if="row.key.trim() === 'src' && assetOptions.length"
            :model-value="row.value"
            :options="assetOptions"
            placeholder="从资源选择"
            class="flex-1"
            clearable
            aria-label="资源引用"
            @update:model-value="(value) => { row.value = String(value || ''); commitAttributes() }"
          />
          <YdInput v-else v-model="row.value" class="flex-1" placeholder="值" aria-label="属性值" @change="commitAttributes" />
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="删除属性" @click="attributeRows.splice(index, 1); commitAttributes()">
            <YdIcon name="morph:x" />
          </YdButton>
        </div>
      </StudioInspectorSection>

      <StudioInspectorSection id="styles" title="样式" :summary="`${styleRows.length} 项 · ${breakpointOptions.find(option => option.value === breakpoint)?.label}`">
        <div class="flex justify-end">
          <YdSegmented v-model="breakpoint" :options="breakpointOptions" size="sm" aria-label="样式断点" />
        </div>
        <p class="text-xs text-muted-foreground">
          {{ breakpoint === 'desktop' ? '桌面为默认样式；平板/手机为对应断点下的覆盖样式。' : '此处样式仅覆盖所选断点，桌面样式仍生效。' }}
        </p>
        <div v-for="(row, index) in styleRows" :key="`style-${index}`" class="flex gap-1 items-center">
          <YdInput v-model="row.key" class="w-28" placeholder="样式名" aria-label="样式名" @change="commitStyles" />
          <YdColorPicker
            v-if="isColorKey(row.key)"
            :model-value="row.value || '#000000'"
            format="hex"
            show-alpha
            class="flex-1"
            aria-label="颜色样式"
            @update:model-value="(value) => { row.value = value; commitStyles() }"
          />
          <YdInput v-else v-model="row.value" class="flex-1" placeholder="值" aria-label="样式值" @change="commitStyles" />
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="删除样式" @click="styleRows.splice(index, 1); commitStyles()">
            <YdIcon name="morph:x" />
          </YdButton>
        </div>
        <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="添加样式" @click="styleRows.push({ key: '', value: '' })">
          <YdIcon name="morph:plus" /> 添加样式
        </YdButton>
      </StudioInspectorSection>
    </template>
  </div>
</template>

<style scoped>
/* 标题独占一行（最少 120px 才与操作同行），窄栏时操作按钮换到下一行，不挤压标题 */
.studio-inspector-head {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  align-items: center;
  min-width: 0;
}
.studio-inspector-head__title {
  display: flex;
  flex: 1 1 120px;
  gap: 4px;
  align-items: baseline;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}
.studio-inspector-head__title strong {
  overflow: hidden;
  text-overflow: ellipsis;
}
.studio-inspector-head__actions {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  min-width: 0;
  gap: 2px;
  align-items: center;
}
</style>

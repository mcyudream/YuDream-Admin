<script setup lang="ts">
import type { FeishuBlockNode } from '../feishu-contract'
import { computed, onMounted, ref, watch } from 'vue'
import apiFeishu from '@/api/modules/platform-feishu'
import {
  deleteTargetOf,
  failureTitle,
  FEISHU_AUTHZ_NOTICE,
  FEISHU_PERMISSIONS,
  FEISHU_WRITE_CONTRACT,
  normalizeFeishuFailure,
  readOnlyReasonText,
  resolveBlockPage,
  resolveDocumentContent,
  resolveDocumentMeta,
  revisionParamOf,
  verifyChildCount,
} from '../feishu-contract'
import { createFeishuDocumentSession } from '../feishu-state'

/**
 * 文档详情：元信息 + 正文只读 + 块结构编辑。
 * 可写范围只有后端已核实的两类：文本块 elements 更新、父块下子块的追加与删除；
 * 整篇正文没有覆盖写接口，因此正文视图没有保存入口。
 */
const props = defineProps<{ documentId: string }>()
const emit = defineEmits<{ back: [] }>()

const toast = useYdToast()
const modal = useYdModal()
const isMobile = useIsMobile()
const session = createFeishuDocumentSession()
const { state, dirty } = session

const activeView = ref<'content' | 'blocks'>('content')
const viewOptions = [
  { label: '正文', value: 'content' as const },
  { label: '块结构', value: 'blocks' as const },
]
const langDraft = ref('')

const blocksLoadingMore = ref(false)
const blocks = computed(() => state.blocks)
const terminalFailure = computed(() => state.status !== 'ready' && state.status !== 'loading' && state.status !== 'idle')

async function loadMeta() {
  try {
    const { data } = await apiFeishu.getDocument(props.documentId)
    session.applyMeta(resolveDocumentMeta(data, props.documentId))
  }
  catch (error) {
    session.failMeta(normalizeFeishuFailure(error, 'document'))
  }
}

function contentLangParam() {
  const trimmed = langDraft.value.trim()
  if (!trimmed || !/^\d+$/.test(trimmed)) {
    return undefined
  }
  return Number(trimmed)
}

async function loadContent() {
  state.contentStatus = 'loading'
  const lang = contentLangParam()
  try {
    const { data } = await apiFeishu.getDocumentContent(props.documentId, lang)
    session.applyContent(resolveDocumentContent(data), lang)
  }
  catch (error) {
    session.failContent(normalizeFeishuFailure(error, 'document'))
  }
}

async function loadBlocks() {
  state.blocksStatus = 'loading'
  try {
    const { data } = await apiFeishu.getDocumentBlocks(props.documentId)
    const page = resolveBlockPage(data)
    session.applyBlocks(page.items, { nextPageToken: page.nextPageToken, hasMore: page.hasMore })
  }
  catch (error) {
    session.failBlocks(normalizeFeishuFailure(error, 'document'))
  }
}

async function loadMoreBlocks() {
  if (!state.blocksHasMore || blocksLoadingMore.value) {
    return
  }
  blocksLoadingMore.value = true
  try {
    const { data } = await apiFeishu.getDocumentBlocks(props.documentId, { pageToken: state.blocksPageToken })
    const page = resolveBlockPage(data)
    session.appendBlocks(page.items, { nextPageToken: page.nextPageToken, hasMore: page.hasMore })
  }
  catch (error) {
    session.failBlocks(normalizeFeishuFailure(error, 'document'))
  }
  finally {
    blocksLoadingMore.value = false
  }
}

async function loadAll() {
  session.select(props.documentId)
  await Promise.all([loadMeta(), loadContent(), loadBlocks()])
}

function retryAll() {
  void loadAll()
}

function reloadBlocks() {
  return loadBlocks()
}

function startEdit(node: FeishuBlockNode) {
  if (!session.beginEditBlock(node)) {
    toast.warning('该块保持只读', { description: readOnlyReasonText(node.readOnlyReason) })
  }
}

async function saveBlock() {
  const ticket = session.blockTicket()
  if (!ticket.blockId || !session.canSubmitBlock.value) {
    return
  }
  session.setSaving(true)
  try {
    const { data } = await apiFeishu.updateBlockElements(ticket.documentId, ticket.blockId, {
      elements: ticket.elements,
      documentRevisionId: revisionParamOf(state.meta?.revisionId),
    })
    session.setSaving(false)
    session.acknowledgeBlock(ticket)
    // 以写接口返回的块原文校验，未确认时如实提示，不显示乐观成功
    const verified = session.settleElementWrite(data, ticket.blockId, ticket.text)
    if (verified === 'verified') {
      toast.success('文本块已更新', { description: '写接口返回的块内容与提交文本一致。' })
      session.cancelEditBlock()
    }
    else {
      toast.warning('已提交，结果未确认', { description: state.writeNotice })
    }
  }
  catch (error) {
    session.failWrite(normalizeFeishuFailure(error, 'document'))
  }
}

async function submitChild(parentId: string) {
  if (!session.canSubmitChild.value) {
    return
  }
  const before = session.childCountOf(parentId)
  session.setChildSaving(true)
  try {
    await apiFeishu.appendBlockChildren(props.documentId, parentId, session.childAppendBody())
    session.setChildSaving(false)
    session.cancelAddChild()
    await reloadBlocks()
    const after = session.childCountOf(parentId)
    if (verifyChildCount(before, after, 'create') === 'unverified') {
      toast.warning('已提交新增子块，重读未确认', { description: '重读父块子块数没有增加，请在飞书中核对。' })
    }
    else {
      toast.success('已新增子块')
    }
  }
  catch (error) {
    session.failChild(normalizeFeishuFailure(error, 'document'))
  }
}

async function removeChild(node: FeishuBlockNode) {
  // 删除必须有权威同级下标：父块未载入或 children 未列出该块时拒绝，避免误删相邻子块
  const target = deleteTargetOf(node, state.blocks)
  if (!target.ok || !target.range || !node.parentId) {
    toast.warning('无法确认删除区间', { description: target.reason ?? '同级下标不可确认；请重新加载块结构。' })
    return
  }
  const before = session.childCountOf(node.parentId)
  try {
    await apiFeishu.deleteBlockChildren(props.documentId, node.parentId, target.range)
    await reloadBlocks()
    const after = session.childCountOf(node.parentId)
    if (verifyChildCount(before, after, 'delete') === 'unverified') {
      toast.warning('已提交删除子块，重读未确认', { description: '重读父块子块数没有减少，请在飞书中核对。' })
    }
    else {
      toast.success('已删除子块')
    }
  }
  catch (error) {
    session.failWrite(normalizeFeishuFailure(error, 'document'))
  }
}

/** 删除子块依赖已确认的子块写能力；是否可删由 deleteTargetOf 逐块判定。 */
const canDelete = computed(() => FEISHU_WRITE_CONTRACT.blockChildren === 'confirmed')

function deleteTarget(node: FeishuBlockNode) {
  if (!canDelete.value) {
    return { ok: false, reason: '子块写入未确认，删除入口停用。' }
  }
  return deleteTargetOf(node, state.blocks)
}

/** 逐块预计算删除可行性：模板只读这张表，不在渲染中重复判定。 */
const deleteTargets = computed(() => new Map(state.blocks.map(node => [node.blockId, deleteTarget(node)])))

/**
 * 离开守卫的对外接口：由父页面在切换文档/目录/视图前调用。
 * 组件自己不静默丢弃草稿，也不在无确认的情况下清空编辑内容。
 */
function hasUnsavedEdits() {
  return session.hasUnsavedEdits.value
}

function isWriting() {
  return session.isWriting.value
}

/** 只在用户明确确认放弃后调用。 */
function discardEdits() {
  session.cancelEditBlock()
  session.cancelAddChild()
  return !session.hasUnsavedEdits.value
}

/**
 * 离开确认：父页面、路由离开与浏览器关闭共用同一处判断。
 * 未确认时返回 false，调用方必须保持当前文档与草稿不变。
 */
function confirmLeave(): Promise<boolean> {
  if (session.isWriting.value) {
    toast.warning('写入进行中', { description: '本次写入结果尚未确认，请等待完成后再离开。' })
    return Promise.resolve(false)
  }
  if (!session.hasUnsavedEdits.value) {
    return Promise.resolve(true)
  }
  return new Promise((resolve) => {
    modal.confirm({
      title: '离开未保存的文档编辑？',
      content: '当前文档有未保存的文本块编辑或未提交的子块内容；继续将丢弃这些修改，未提交内容不会写入飞书。',
      confirmButtonText: '放弃修改',
      onConfirm: () => {
        discardEdits()
        resolve(true)
      },
      onCancel: () => resolve(false),
    })
  })
}

defineExpose({ hasUnsavedEdits, isWriting, discardEdits, confirmLeave })

watch(() => props.documentId, () => {
  activeView.value = 'content'
  langDraft.value = ''
  void loadAll()
})

onMounted(loadAll)
</script>

<template>
  <section class="detail">
    <header class="detail__head">
      <div class="detail__identity">
        <YdButton variant="ghost" size="sm" aria-label="返回文件清单" @click="emit('back')">
          <YdIcon name="morph:arrow-left" />
          返回清单
          <YdTag v-if="dirty || state.childParentId">
            未保存
          </YdTag>
        </YdButton>
        <div class="detail__titles">
          <h2>{{ state.meta?.title || props.documentId }}</h2>
          <p>
            <span>文档 ID {{ props.documentId }}</span>
            <span aria-hidden="true">·</span>
            <span>{{ state.meta?.revisionId ? `版本 ${state.meta.revisionId}` : '版本未返回' }}</span>
            <span aria-hidden="true">·</span>
            <span>块 {{ blocks.length }} 个{{ state.blocksHasMore ? '（还有更多）' : '' }}</span>
          </p>
        </div>
      </div>
      <div class="detail__actions">
        <YdSegmented v-model="activeView" :options="viewOptions" size="sm" aria-label="文档视图切换" />
      </div>
    </header>

    <YdLoadingOverlay :loading="state.status === 'loading'" text="加载文档">
      <YdResult
        v-if="terminalFailure && state.failure"
        status="error"
        :title="failureTitle(state.failure.kind)"
        :description="state.failure.message"
      >
        <YdButton variant="outline" @click="retryAll">
          重新加载
        </YdButton>
      </YdResult>

      <template v-else>
        <YdAlert
          v-if="state.failure?.kind === 'rate-limited'"
          class="detail__notice"
          icon="morph:gauge-circle"
          :title="failureTitle(state.failure.kind)"
          :description="`${state.failure.message}；额度按用户分钟/日计量，请稍后重试或联系管理员调整上限。`"
        />

        <template v-if="activeView === 'content'">
          <YdAlert
            v-if="state.contentFailure"
            class="detail__notice"
            :variant="state.contentFailure.kind === 'rate-limited' ? 'default' : 'destructive'"
            icon="morph:circle-alert"
            :title="failureTitle(state.contentFailure.kind)"
            :description="state.contentFailure.message"
          >
            <template #description>
              <span>{{ state.contentFailure.message }}</span>
              <YdButton variant="ghost" size="sm" @click="loadContent">
                重新载入正文
              </YdButton>
            </template>
          </YdAlert>

          <template v-else-if="state.contentStatus === 'ready'">
            <div class="detail__content-bar">
              <label class="detail__lang">
                <span>语言枚举</span>
                <YdInput v-model="langDraft" type="number" placeholder="留空使用默认" @keydown.enter="loadContent" />
              </label>
              <YdButton variant="outline" size="sm" @click="loadContent">
                载入该语言
              </YdButton>
              <span class="detail__muted">
                {{ state.contentLang === undefined ? '当前使用后端默认语言' : `当前语言枚举 ${state.contentLang}` }}
              </span>
            </div>

            <YdEmpty
              v-if="!state.content?.content"
              illustration="empty"
              title="该文档没有可显示的正文"
              description="后端返回的 raw_content 为空；可切换到块结构查看块级内容。"
            />

            <template v-else>
              <YdMarkdownEditor
                :model-value="state.content.content"
                readonly
                :height="isMobile ? '420px' : '560px'"
                aria-label="文档正文（只读）"
              />
              <p class="detail__muted">
                后端返回的是飞书 raw_content 纯文本，这里以只读 Markdown 视图呈现，仅供阅读；正文没有覆盖写接口，因此没有整篇保存入口，内容修改走块结构。
              </p>
            </template>
          </template>
        </template>

        <template v-else>
          <YdAlert class="detail__notice" icon="morph:info" title="可编辑范围" :description="FEISHU_AUTHZ_NOTICE" />
          <YdAlert
            class="detail__notice"
            icon="morph:info"
            title="写入能力"
            description="只支持单个无样式文本元素块的 elements 更新，以及父块 children 可确认下标时的子块删除；多元素、带样式/链接、图片表格等块保持只读，不做会丢格式的全块替换。"
          />

          <YdAlert
            v-if="state.blocksFailure"
            class="detail__notice"
            :variant="state.blocksFailure.kind === 'rate-limited' ? 'default' : 'destructive'"
            icon="morph:circle-alert"
            :title="failureTitle(state.blocksFailure.kind)"
            :description="state.blocksFailure.message"
          />

          <YdAlert
            v-if="state.blocksTruncated"
            class="detail__notice"
            icon="morph:triangle-alert"
            title="块未全部加载"
            :description="`文档块超过前端加载上限（${state.blocksPageCount} 页），当前仅显示已加载部分；可按需继续加载。`"
          />

          <YdAlert
            v-if="state.writeFailure"
            class="detail__notice"
            variant="destructive"
            icon="morph:circle-alert"
            :title="failureTitle(state.writeFailure.kind)"
            :description="state.writeFailure.kind === 'conflict'
              ? `${state.writeFailure.message}；已保留你的编辑内容，请重新载入块结构后确认再提交。`
              : `${state.writeFailure.message}；编辑内容已保留，可修改后重试。`"
          />

          <YdAlert
            v-if="state.writeNotice"
            class="detail__notice"
            icon="morph:triangle-alert"
            title="写入结果未确认"
            :description="state.writeNotice"
          />

          <YdLoadingOverlay :loading="state.blocksStatus === 'loading'" text="加载块结构">
            <ul v-if="blocks.length" class="block-list">
              <li
                v-for="node in blocks"
                :key="node.blockId"
                class="block-row"
                :style="{ '--block-indent': `${Math.min(node.depth, 6) * 16}px` }"
                :data-block-id="node.blockId"
              >
                <div class="block-row__main">
                  <YdTag>{{ node.typeName }}</YdTag>
                  <template v-if="state.editingId === node.blockId">
                    <div class="block-row__editor">
                      <YdTextarea v-model="state.draft" aria-label="编辑文本块" />
                      <div class="block-row__editor-actions">
                        <YdTag v-if="dirty">
                          未保存
                        </YdTag>
                        <YdButton variant="ghost" size="sm" :disabled="state.saving" @click="session.cancelEditBlock()">
                          取消
                        </YdButton>
                        <YdButton
                          v-auth="FEISHU_PERMISSIONS.edit"
                          size="sm"
                          :loading="state.saving"
                          :disabled="!session.canSubmitBlock.value"
                          @click="saveBlock"
                        >
                          保存文本块
                        </YdButton>
                      </div>
                      <p class="detail__muted">
                        该块被判定为「单个纯文本元素」，因此整块替换不会丢格式；只提交文本 elements，并发或版本冲突会被后端拒绝，届时保留你的内容并提示冲突。
                      </p>
                    </div>
                  </template>
                  <template v-else>
                    <span v-if="node.text !== undefined" class="block-row__text">{{ node.text }}</span>
                    <span v-else class="block-row__text is-missing">
                      {{ node.textMissing ? '该块未返回可解析的文本内容（保留原文不给占位文本）' : '空块' }}
                    </span>
                  </template>
                </div>

                <div v-if="state.editingId !== node.blockId" class="block-row__actions">
                  <span v-if="!node.editable" class="block-row__readonly">{{ readOnlyReasonText(node.readOnlyReason) }}</span>
                  <YdButton
                    v-else
                    v-auth="FEISHU_PERMISSIONS.edit"
                    variant="ghost"
                    size="sm"
                    @click="startEdit(node)"
                  >
                    编辑文本
                  </YdButton>

                  <YdButton
                    v-auth="FEISHU_PERMISSIONS.edit"
                    variant="ghost"
                    size="sm"
                    @click="session.beginAddChild(node.blockId)"
                  >
                    添加子块
                  </YdButton>

                  <YdTooltip v-if="!deleteTargets.get(node.blockId)?.ok" :text="deleteTargets.get(node.blockId)?.reason || ''">
                    <span class="block-row__readonly">删除需先加载父块</span>
                  </YdTooltip>
                  <YdPopconfirm
                    v-else
                    title="删除该子块？"
                    :description="`按后端契约删除 [${node.index}, ${(node.index ?? 0) + 1}) 区间（由父块 children 确认）；删除后不可恢复，请在飞书中复核结果。`"
                    @confirm="removeChild(node)"
                  >
                    <YdButton v-auth="FEISHU_PERMISSIONS.edit" variant="ghost" size="sm">
                      删除子块
                    </YdButton>
                  </YdPopconfirm>
                </div>

                <div v-if="state.childParentId === node.blockId" class="block-row__child">
                  <YdInput v-model="state.childDraft" placeholder="新增子块文本内容" aria-label="新增子块文本" @keydown.enter="submitChild(node.blockId)" />
                  <div class="block-row__child-actions">
                    <YdButton variant="ghost" size="sm" :disabled="state.childSaving" @click="session.cancelAddChild()">
                      取消
                    </YdButton>
                    <YdButton size="sm" :loading="state.childSaving" :disabled="!session.canSubmitChild.value" @click="submitChild(node.blockId)">
                      添加文本子块
                    </YdButton>
                  </div>
                  <YdAlert
                    v-if="state.childFailure"
                    variant="destructive"
                    icon="morph:circle-alert"
                    :title="failureTitle(state.childFailure.kind)"
                    :description="state.childFailure.message"
                  />
                </div>
              </li>
            </ul>

            <YdEmpty
              v-else-if="!state.blocksFailure && state.blocksStatus === 'ready'"
              illustration="empty"
              title="没有块结构"
              description="后端返回了空块列表；正文视图可能仍有内容。"
            />
          </YdLoadingOverlay>

          <footer v-if="state.blocksHasMore" class="detail__more">
            <YdButton variant="outline" :loading="blocksLoadingMore" :disabled="!session.canLoadMoreBlocks.value" @click="loadMoreBlocks">
              加载更多块
            </YdButton>
            <span class="detail__muted">后端还有下一页块（默认 20 块/页）</span>
          </footer>
        </template>

        <YdDescriptions
          class="detail__meta"
          :column="isMobile ? 1 : 2"
          :mobile-column="1"
          border
          :items="[
            { label: '文档 ID', value: props.documentId },
            { label: '标题', value: state.meta?.title || '未返回' },
            { label: '文档版本', value: state.meta?.revisionId || '未返回' },
            { label: '已加载块数', value: String(blocks.length) },
          ]"
        />
      </template>
    </YdLoadingOverlay>
  </section>
</template>

<style scoped>
.detail {
  display: grid;
  gap: 14px;
  align-content: start;
  min-width: 0;
}

.detail__head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-2);
}

.detail__identity {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.detail__titles {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.detail__titles h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
}

.detail__titles p {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.detail__notice {
  font-size: 12px;
}

.detail__content-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}

.detail__lang {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.detail__muted {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.block-list {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.block-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px 12px;
  align-items: start;
  padding: 10px 0 10px var(--block-indent, 0);
  border-bottom: 1px solid var(--color-border-2);
}

.block-row:last-child {
  border-bottom: none;
}

.block-row__main {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  min-width: 0;
}

.block-row__text {
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.block-row__text.is-missing {
  color: var(--color-text-3);
}

.block-row__editor,
.block-row__child {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.block-row__editor-actions,
.block-row__child-actions,
.block-row__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.block-row__actions {
  justify-content: flex-end;
}

.block-row__readonly {
  max-width: 220px;
  padding: 2px 6px;
  border: 1px dashed var(--color-border-2);
  border-radius: 5px;
  font-size: 11px;
  color: var(--color-text-3);
}

.block-row__child {
  grid-column: 1 / -1;
  padding: 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-fill-1);
}

.detail__more {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-2);
}

.detail__meta {
  margin-top: 4px;
}

@media (width <= 768px) {
  .block-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .block-row__actions {
    justify-content: flex-start;
  }

  .block-row__readonly {
    max-width: none;
  }
}
</style>

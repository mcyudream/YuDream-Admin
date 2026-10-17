<script setup lang="ts">
import type { WikiAuthorComment, WikiAuthorCommentAnchor } from './wiki-author-comments-api'
import type { WikiAnchorBlock, WikiQuoteAnchor, WikiQuoteAnchorMatch } from '@/views/wiki/wiki-quote-anchor'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { resolveApiErrorMessage } from '@/utils/api-error'
import dayjs from '@/utils/dayjs'
import { classifyWikiQuoteAnchor } from '@/views/wiki/wiki-quote-anchor'
import { createWikiAuthorComment, fetchWikiAuthorComments, wikiAnchorFromAuthorComment, wikiAnchorStateFromAuthorComment } from './wiki-author-comments-api'

/**
 * 作者工作区评论线程（只读已发布评论 + 待审核发表入口）。
 *
 * - 列表只消费服务端公开评论接口返回的**已发布**评论：不在本地插入待审核内容，不伪造评论数据；
 * - 发表要求登录，服务端落 PENDING，提交后只提示审核中；回复继承根评论锚点，不携带 anchor；
 * - 锚点展示只读：摘录与状态直接来自服务端（state 按当前已发布版本计算），
 *   本地只用共享纯定位模型在当前作者预览里找唯一命中，用于「定位到正文」；
 *   预览与已保存/已发布内容不一致、或无法唯一命中时不做任何猜测，只保留评论线程；
 * - 评论标记不写进 Markdown：定位只滚动/临时高亮预览 DOM，不改 editorFields.body。
 */
const props = defineProps<{
  /** 空间 slug（公开评论接口按 slug 定位空间） */
  spaceSlug: string
  /** 页面节点 ID（字符串长 ID） */
  nodeId: string
  /** 空间评论能力 + 页面已发布：不满足时不请求评论接口，只说明原因 */
  enabled: boolean
  /** 能力不可用时的原因说明 */
  disabledHint: string
  /** 是否可以发表评论（登录 + 页面已发布 + 预览内容与已保存/已发布一致）：不满足时只读 */
  canCreate: boolean
  /** 不能发表时的原因说明（可操作） */
  createDisabledReason: string
  /** 预览选区冻结的锚点草稿；为空表示整页评论 */
  pendingAnchor: WikiAuthorCommentAnchor | null
  /** 当前作者预览提取的正文块（定位用；不写回正文） */
  blocks: WikiAnchorBlock[]
  /** 预览正文与已保存/已发布内容不一致：定位仅供参考，锚点状态以服务端为准 */
  previewDiffers: boolean
}>()

const emit = defineEmits<{
  /** 定位：把匹配到的正文块滚动到视口并临时高亮 */
  locate: [match: WikiQuoteAnchorMatch]
  /** 取消当前选区引用，改为整页评论 */
  clearAnchor: []
}>()

const PAGE_SIZE = 20

const accountStore = useAppAccountStore()

const comments = ref<WikiAuthorComment[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

const draft = ref('')
const replyTarget = ref<WikiAuthorComment | null>(null)
const submitting = ref(false)
const submitError = ref('')
const submitNotice = ref('')

const isLogin = computed(() => accountStore.isLogin)
const hasMore = computed(() => comments.value.length < total.value)
/** 回复同样要求登录 + 评论能力；回复不参与锚点，因此不受草稿/发布一致性限制 */
const canReply = computed(() => props.enabled && isLogin.value)

// 请求代际：切页面/切空间后落地的旧响应直接丢弃，避免评论串台
let listRequestId = 0

function resetState() {
  comments.value = []
  total.value = 0
  page.value = 1
  loading.value = false
  loadError.value = ''
  draft.value = ''
  replyTarget.value = null
  submitting.value = false
  submitError.value = ''
  submitNotice.value = ''
}

async function loadPage(next: number) {
  if (!props.enabled || !props.spaceSlug || !props.nodeId) {
    return
  }
  const requestId = ++listRequestId
  loading.value = true
  loadError.value = ''
  try {
    const result = (await fetchWikiAuthorComments(props.spaceSlug, props.nodeId, { page: next, size: PAGE_SIZE })).data
    if (requestId !== listRequestId) {
      return
    }
    const records = result?.records || []
    comments.value = next <= 1 ? records : [...comments.value, ...records]
    total.value = result?.total ?? comments.value.length
    page.value = next
  }
  catch (exception: any) {
    if (requestId !== listRequestId) {
      return
    }
    if (next <= 1) {
      comments.value = []
      total.value = 0
    }
    loadError.value = await resolveApiErrorMessage(exception)
  }
  finally {
    if (requestId === listRequestId) {
      loading.value = false
    }
  }
}

function startReply(comment: WikiAuthorComment) {
  replyTarget.value = comment
  submitError.value = ''
  submitNotice.value = ''
}

function cancelReply() {
  replyTarget.value = null
}

/** 回复不带锚点，因此不受「预览与已发布一致」限制；新评论必须走锚点闸门 */
const canSubmit = computed(() => replyTarget.value ? canReply.value : props.canCreate)
const composerVisible = computed(() => props.canCreate || Boolean(replyTarget.value))

async function submitComment() {
  const content = draft.value.trim()
  if (!content || !canSubmit.value || submitting.value) {
    return
  }
  const reply = replyTarget.value
  submitting.value = true
  submitError.value = ''
  submitNotice.value = ''
  try {
    await createWikiAuthorComment(props.spaceSlug, props.nodeId, {
      content,
      parentId: reply?.id,
      // 回复继承根评论锚点：服务端明确拒绝回复自带锚点，这里不传
      anchor: reply ? undefined : props.pendingAnchor ?? undefined,
    })
    draft.value = ''
    replyTarget.value = null
    // 服务端把新评论落为待审核：只提示审核中，不伪造已发布状态、不本地插入列表
    if (!reply && props.pendingAnchor) {
      emit('clearAnchor')
    }
    submitNotice.value = '评论已提交，审核通过后会在本页展示。'
  }
  catch (exception: any) {
    submitError.value = await resolveApiErrorMessage(exception)
  }
  finally {
    submitting.value = false
  }
}

function formatTime(value?: string) {
  if (!value) {
    return ''
  }
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : ''
}

// —— 锚点只读视图：服务端状态 + 本地唯一命中 ——

/** YdTag 可选外观（只声明本面板用到的取值，不引入共享组件库内部类型） */
type AnchorStateVariant = 'outline' | 'secondary' | 'destructive'

interface WikiAuthorCommentAnchorView {
  anchor: WikiQuoteAnchor
  excerpt: string
  stateLabel: string
  stateVariant: AnchorStateVariant
  match?: WikiQuoteAnchorMatch
  locateHint: string
}

const ANCHOR_STATE_VIEW: Record<string, { label: string, variant: AnchorStateVariant }> = {
  active: { label: '原文已锚定', variant: 'outline' },
  reanchorable: { label: '原文已调整', variant: 'secondary' },
  orphaned: { label: '原文已变化', variant: 'destructive' },
}

interface WikiAuthorCommentView {
  comment: WikiAuthorComment
  anchor?: WikiAuthorCommentAnchorView
}

const commentViews = computed<WikiAuthorCommentView[]>(() => comments.value.map((comment) => {
  const anchor = wikiAnchorFromAuthorComment(comment.anchor)
  if (!anchor) {
    return { comment }
  }
  const stateKey = wikiAnchorStateFromAuthorComment(comment)
  const state = stateKey ? ANCHOR_STATE_VIEW[stateKey] : undefined
  const classification = classifyWikiQuoteAnchor(anchor, props.blocks)
  const match = classification.status === 'ambiguous' ? undefined : classification.match
  let locateHint = '定位到当前预览中的引用原文'
  if (!match) {
    locateHint = classification.status === 'ambiguous'
      ? '引用在当前预览中多处出现，无法唯一确定位置'
      : '当前预览里找不到这段引用，无法定位'
  }
  else if (props.previewDiffers) {
    locateHint = '预览内容与已保存/已发布内容不同，定位仅供参考'
  }
  return {
    comment,
    anchor: {
      anchor,
      excerpt: anchor.exact,
      stateLabel: state?.label || '',
      stateVariant: state?.variant || 'outline',
      match,
      locateHint,
    },
  }
}))

function locateComment(view: WikiAuthorCommentView) {
  if (!view.anchor?.match) {
    return
  }
  emit('locate', view.anchor.match)
}

watch([() => props.spaceSlug, () => props.nodeId, () => props.enabled], ([slug, nodeId, enabled]) => {
  listRequestId += 1
  resetState()
  if (enabled && slug && nodeId) {
    void loadPage(1)
  }
}, { immediate: true })

onBeforeUnmount(() => {
  listRequestId += 1
})
</script>

<template>
  <YdScrollArea class="author-comments__scroll">
    <div class="author-comments">
      <div v-if="!enabled" class="author-comments__state">
        <YdEmpty icon="morph:message-circle" title="评论不可用" :description="disabledHint" />
      </div>

      <template v-else>
        <!-- 发表入口：新评论任一闸门不满足时只说明原因；回复继承根锚点，不受草稿一致性限制 -->
        <section class="author-comments__composer">
          <template v-if="composerVisible">
            <p v-if="replyTarget" class="author-comments__replying">
              <span>回复 {{ replyTarget.authorName || '读者' }}</span>
              <YdButton variant="ghost" size="sm" @click="cancelReply">
                取消
              </YdButton>
            </p>
            <div v-if="!replyTarget && pendingAnchor" class="author-comments__quote">
              <div class="author-comments__quote-head">
                <YdIcon name="morph:quote" />
                <span>引用预览中选中的原文</span>
                <YdButton variant="ghost" size="icon-sm" aria-label="取消引用，改为整页评论" @click="emit('clearAnchor')">
                  <YdIcon name="morph:x" />
                </YdButton>
              </div>
              <p class="author-comments__quote-text" :title="pendingAnchor.exact">
                {{ pendingAnchor.exact }}
              </p>
            </div>
            <p v-else-if="!replyTarget" class="author-comments__hint">
              未选择文字：将发表整页评论。在预览里选中一段文字可引用原文。
            </p>
            <p v-else-if="!canCreate" class="author-comments__hint">
              回复继承根评论锚点，不需要当前预览与已发布内容一致。
            </p>
            <YdTextarea
              v-model="draft"
              :autosize="{ minRows: 3, maxRows: 8 }"
              maxlength="2000"
              placeholder="以当前登录身份发表评论，提交后需审核"
            />
            <div class="author-comments__actions">
              <YdButton :loading="submitting" :disabled="!draft.trim()" @click="submitComment">
                <YdIcon name="morph:send" /> {{ replyTarget ? '发表回复' : '发表评论' }}
              </YdButton>
              <span class="author-comments__hint">提交后进入待审核状态，审核通过后展示。</span>
            </div>
            <p v-if="submitError" class="author-comments__error" role="alert">
              {{ submitError }}
            </p>
            <p v-if="submitNotice" class="author-comments__notice" role="status">
              {{ submitNotice }}
            </p>
          </template>
          <YdAlert
            v-else
            variant="destructive"
            icon="morph:info"
            title="暂不能发表评论"
            :description="createDisabledReason"
          />
        </section>

        <!-- 已发布评论（只读） -->
        <section class="author-comments__thread">
          <p class="author-comments__hint">
            以下为服务端已发布评论（只读）；新评论审核通过后才会出现在这里。
          </p>

          <div v-if="loading && !comments.length && !loadError" class="author-comments__state">
            <YdLoading :size="20" text="评论加载中…" />
          </div>
          <div v-else-if="loadError" class="author-comments__state">
            <YdAlert variant="destructive" icon="morph:circle-alert" title="评论加载失败" :description="loadError" />
            <YdButton variant="outline" size="sm" @click="loadPage(1)">
              <YdIcon name="morph:refresh-cw" /> 重试
            </YdButton>
          </div>
          <YdEmpty
            v-else-if="!comments.length"
            class="author-comments__state"
            icon="morph:message-circle"
            title="还没有评论"
            description="审核通过的评论会展示在这里。"
          />

          <ul v-else class="author-comments__list">
            <li v-for="view in commentViews" :key="view.comment.id" class="author-comments__item">
              <div class="author-comments__meta">
                <strong class="author-comments__author">{{ view.comment.authorName || '读者' }}</strong>
                <YdTag v-if="view.comment.pinned" class="author-comments__pinned">
                  置顶
                </YdTag>
                <time v-if="formatTime(view.comment.createTime)" class="author-comments__time">
                  {{ formatTime(view.comment.createTime) }}
                </time>
              </div>

              <div v-if="view.anchor" class="author-comments__anchor">
                <div class="author-comments__anchor-head">
                  <YdIcon name="morph:quote" />
                  <span class="author-comments__anchor-label">引用原文</span>
                  <YdTag v-if="view.anchor.stateLabel" :variant="view.anchor.stateVariant" class="author-comments__anchor-state">
                    {{ view.anchor.stateLabel }}
                  </YdTag>
                </div>
                <p class="author-comments__anchor-text" :title="view.anchor.excerpt">
                  {{ view.anchor.excerpt }}
                </p>
                <YdButton
                  class="author-comments__anchor-locate"
                  variant="ghost"
                  size="sm"
                  :disabled="!view.anchor.match"
                  :title="view.anchor.locateHint"
                  @click="locateComment(view)"
                >
                  <YdIcon name="morph:crosshair" /> 定位到正文
                </YdButton>
              </div>

              <p class="author-comments__content">
                {{ view.comment.content }}
              </p>

              <ul v-if="view.comment.replies?.length" class="author-comments__replies">
                <li v-for="reply in view.comment.replies" :key="reply.id" class="author-comments__reply">
                  <div class="author-comments__meta">
                    <strong class="author-comments__author">{{ reply.authorName || '读者' }}</strong>
                    <time v-if="formatTime(reply.createTime)" class="author-comments__time">
                      {{ formatTime(reply.createTime) }}
                    </time>
                  </div>
                  <p class="author-comments__content">
                    {{ reply.content }}
                  </p>
                </li>
              </ul>

              <YdButton
                v-if="canReply && !view.comment.parentId"
                class="author-comments__reply-action"
                variant="ghost"
                size="sm"
                @click="startReply(view.comment)"
              >
                <YdIcon name="morph:corner-up-left" /> 回复
              </YdButton>
            </li>
          </ul>

          <YdButton v-if="hasMore" variant="outline" size="sm" :loading="loading" @click="loadPage(page + 1)">
            加载更多评论
          </YdButton>

          <p v-if="!isLogin" class="author-comments__hint">
            登录后可发表评论；匿名评论未开放。
          </p>
        </section>
      </template>
    </div>
  </YdScrollArea>
</template>

<style scoped>
.author-comments__scroll {
  flex: 1;
  min-height: 0;
}

.author-comments {
  display: grid;
  gap: 14px;
  padding: 12px;
}

.author-comments__state {
  display: grid;
  gap: 10px;
  justify-items: start;
  padding: 8px 0;
}

.author-comments__composer {
  display: grid;
  gap: 8px;
  padding: 12px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.author-comments__quote {
  display: grid;
  gap: 6px;
  padding: 8px 10px;
  background: var(--color-bg-2);
  border-left: 3px solid var(--color-border-3);
  border-radius: 4px;
}

.author-comments__quote-head {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.author-comments__quote-head :deep([data-slot="button"]) {
  margin-left: auto;
}

.author-comments__quote-text {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.author-comments__replying {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0;
  font-size: 12px;
  color: var(--color-text-2);
}

.author-comments__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.author-comments__thread {
  display: grid;
  gap: 10px;
}

.author-comments__list {
  display: grid;
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.author-comments__item {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.author-comments__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.author-comments__author {
  font-size: 12px;
  color: var(--color-text-1);
}

.author-comments__anchor {
  display: grid;
  gap: 4px;
  padding: 6px 8px;
  background: var(--color-fill-1);
  border-left: 3px solid var(--color-border-3);
  border-radius: 4px;
}

.author-comments__anchor-head {
  display: flex;
  gap: 5px;
  align-items: center;
  font-size: 11px;
  color: var(--color-text-3);
}

.author-comments__anchor-state {
  margin-left: auto;
  font-size: 10px;
}

.author-comments__anchor-text {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-2);
  overflow-wrap: anywhere;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.author-comments__anchor-locate {
  justify-self: start;
}

.author-comments__content {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.author-comments__replies {
  display: grid;
  gap: 8px;
  padding: 8px 0 0 10px;
  margin: 0;
  list-style: none;
  border-left: 2px solid var(--color-border-2);
}

.author-comments__reply {
  display: grid;
  gap: 4px;
}

.author-comments__reply-action {
  justify-self: start;
}

.author-comments__hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.author-comments__error {
  margin: 0;
  font-size: 12px;
  color: rgb(var(--danger-6));
}

.author-comments__notice {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-2);
}
</style>

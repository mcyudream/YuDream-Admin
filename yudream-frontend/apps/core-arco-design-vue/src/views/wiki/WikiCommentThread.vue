<script setup lang="ts">
import type { WikiPublicCommentsController } from './use-wiki-public-comments'
import type { WikiPendingQuote } from './use-wiki-quote-anchors'
import type { WikiCommentAnchorState } from './wiki-comment-anchor'
import type { WikiPublicComment } from './wiki-public-api'
import { computed, nextTick, ref, watch } from 'vue'
import { formatWikiCommentTime } from './use-wiki-public-comments'
import {
  resolveWikiCommentAnchorState,
  WIKI_COMMENT_ANCHOR_STATE_LABELS,
  WIKI_COMMENT_ANCHOR_STATE_TONES,
  wikiCommentAnchorHighlightable,
} from './wiki-comment-anchor'

/**
 * 正文引用评论线程面板：桌面作为右侧栏、窄屏作为 YdDrawer 内容复用同一份实现。
 *
 * - 数据与能力声明来自共享的 useWikiPublicComments（commentEnabled/登录/分页/代次/待审核反馈）；
 * - 目录抽屉与评论抽屉互斥、面板内部独立滚动，移动端正文保持 document scroll；
 * - 新发表一律走服务端 PENDING：提交后只提示审核中，不插入已发布列表，也不伪造高亮；
 * - 引用摘录与「定位原文」只在唯一命中时滚动（歧义/失锚的评论只保留线程）。
 */
const props = defineProps<{
  controller: WikiPublicCommentsController
  /** 评论 ID → 客户端锚点状态（本地歧义 + 服务端投影；无锚点评论不在表里） */
  statuses: Record<string, WikiCommentAnchorState>
  /** 当前聚焦的根评论 */
  activeId: string
  /** 待发表的选区引用：展示摘录并在提交时带上锚点草稿 */
  pending: WikiPendingQuote | null
  /** 抽屉/窄屏面板显示收起按钮 */
  closable?: boolean
}>()

const emit = defineEmits<{
  /** 打开并定位某条根评论 */
  locate: [commentId: string]
  /** 引用评论已提交（待审核）：页面据此收起引用入口 */
  posted: []
  close: []
}>()

const { commentEnabled, comments, total, loading, loadError, isLogin, hasMore, page, loginHref } = props.controller

const root = ref<HTMLElement | null>(null)
const toast = useYdToast()
const quoteDraft = ref('')
const quoteSubmitting = ref(false)
const quoteError = ref('')
const quoteNotice = ref('')
const replyTarget = ref<WikiPublicComment | null>(null)
const replyDraft = ref('')
const replySubmitting = ref(false)
const replyError = ref('')
const replyNotice = ref('')

/**
 * 引用身份（文本 + 块序号与块内偏移）：浮出入口滚动时只更新屏幕位置并重建对象，
 * 同一引用的草稿与提交反馈必须保留，只有换成另一段引用才重置。
 * 字段名就是线协议的扁平锚点字段（blockOrdinal/startOffset/endOffset）。
 */
const pendingKey = computed(() => {
  const pending = props.pending
  if (!pending) {
    return ''
  }
  return [
    pending.text,
    pending.payload.blockOrdinal ?? '',
    pending.payload.startOffset ?? '',
    pending.payload.endOffset ?? '',
  ].join('|')
})

watch(pendingKey, (key, previous) => {
  if (!key || key === previous) {
    return
  }
  quoteDraft.value = ''
  quoteError.value = ''
  quoteNotice.value = ''
})

const anchoredComments = computed(() => comments.value.filter(comment => !comment.parentId && comment.anchor?.exact))
const activeComment = computed(() => comments.value.find(comment => comment.id === props.activeId) ?? null)

/** 无锚点评论（含历史评论）没有引用态：返回 null，不造「整页评论」状态。 */
function stateOf(comment: WikiPublicComment | null | undefined): WikiCommentAnchorState | null {
  if (!comment) {
    return null
  }
  return props.statuses[comment.id] ?? resolveWikiCommentAnchorState(comment)
}

function stateLabelOf(comment: WikiPublicComment | null | undefined): string {
  const state = stateOf(comment)
  return state ? WIKI_COMMENT_ANCHOR_STATE_LABELS[state] : ''
}

function stateToneOf(comment: WikiPublicComment | null | undefined): 'default' | 'secondary' | 'outline' {
  const state = stateOf(comment)
  const tone = state ? WIKI_COMMENT_ANCHOR_STATE_TONES[state] : 'neutral'
  return tone === 'success' ? 'default' : tone === 'warning' ? 'secondary' : 'outline'
}

const activeState = computed(() => stateOf(activeComment.value))
const activeLocatable = computed(() => Boolean(activeState.value && wikiCommentAnchorHighlightable(activeState.value)))

function replyCount(comment: WikiPublicComment): number {
  return comment.replies?.length ?? 0
}

async function focusActive() {
  if (!props.activeId) {
    return
  }
  await nextTick()
  const card = root.value?.querySelector<HTMLElement>(`[data-wiki-thread-comment="${props.activeId}"]`)
  card?.scrollIntoView({ block: 'nearest' })
}

watch(() => props.activeId, () => {
  replyTarget.value = null
  replyError.value = ''
  replyNotice.value = ''
  void focusActive()
})

async function submitQuoteComment() {
  const content = quoteDraft.value.trim()
  if (!content || !props.pending || !isLogin.value || quoteSubmitting.value) {
    return
  }
  quoteSubmitting.value = true
  quoteError.value = ''
  quoteNotice.value = ''
  const result = await props.controller.createComment({ content, anchor: props.pending.payload })
  if (result.ok) {
    quoteDraft.value = ''
    // 服务端落为待审核：不加入已发布列表、不高亮，只提示审核中
    quoteNotice.value = '引用评论已提交，审核通过后会展示在本面板。'
    replyTarget.value = null
    // 提交成功后父级会收起草稿（面板与内联提示一起消失），用 toast 保证读者拿到确认
    toast.success('引用评论已提交，审核通过后展示')
    emit('posted')
  }
  else {
    quoteError.value = result.error || '评论提交失败，请稍后重试'
  }
  quoteSubmitting.value = false
}

function startReply(comment: WikiPublicComment) {
  replyTarget.value = comment
}

function cancelReply() {
  replyTarget.value = null
}

async function submitReply() {
  const content = replyDraft.value.trim()
  const target = replyTarget.value
  if (!content || !target || replySubmitting.value) {
    return
  }
  replySubmitting.value = true
  replyError.value = ''
  replyNotice.value = ''
  const result = await props.controller.createComment({ content, parentId: target.id })
  if (result.ok) {
    replyDraft.value = ''
    replyTarget.value = null
    replyNotice.value = '回复已提交，审核通过后会展示在本面板。'
  }
  else {
    replyError.value = result.error || '回复提交失败，请稍后重试'
  }
  replySubmitting.value = false
}
</script>

<template>
  <section ref="root" class="wiki-thread" data-wiki-feature="quote-thread" aria-labelledby="wiki-thread-title">
    <header class="wiki-thread__head">
      <h2 id="wiki-thread-title" class="wiki-thread__title">
        <YdIcon name="morph:messages-square" /> 文中评论
      </h2>
      <YdButton v-if="closable" type="button" variant="ghost" size="sm" aria-label="收起评论面板" @click="emit('close')">
        <YdIcon name="morph:panel-right-close" />
      </YdButton>
    </header>

    <YdLoading v-if="commentEnabled === null" :size="22" text="评论加载中…" />

    <YdEmpty
      v-else-if="commentEnabled === false"
      class="wiki-thread__state"
      icon="morph:message-circle-off"
      title="该知识库未开放评论"
      description="评论能力由服务端空间资料声明；当前空间不可读写评论。"
    />

    <template v-else>
      <div v-if="pending" class="wiki-thread__composer">
        <p class="wiki-thread__quote">
          <YdIcon name="morph:quote" /> {{ pending.text }}
        </p>
        <p class="wiki-thread__hint">
          引用的是当前已发布正文中的选中文字；提交后进入待审核状态。
        </p>
        <YdTextarea
          v-model="quoteDraft"
          :autosize="{ minRows: 2, maxRows: 6 }"
          maxlength="2000"
          placeholder="针对这段文字发表评论"
        />
        <div class="wiki-thread__actions">
          <YdButton type="button" size="sm" :loading="quoteSubmitting" :disabled="!quoteDraft.trim()" @click="submitQuoteComment">
            <YdIcon name="morph:send" /> 发表引用评论
          </YdButton>
        </div>
        <p v-if="quoteError" class="wiki-thread__error" role="alert">
          {{ quoteError }}
        </p>
        <p v-if="quoteNotice" class="wiki-thread__notice" role="status">
          {{ quoteNotice }}
        </p>
      </div>

      <div v-if="activeComment" class="wiki-thread__active" :data-wiki-thread-comment="activeComment.id">
        <div class="wiki-thread__meta">
          <strong class="wiki-thread__author">{{ activeComment.authorName || '读者' }}</strong>
          <YdTag v-if="stateLabelOf(activeComment)" :variant="stateToneOf(activeComment)">
            {{ stateLabelOf(activeComment) }}
          </YdTag>
          <time v-if="formatWikiCommentTime(activeComment.createTime)">{{ formatWikiCommentTime(activeComment.createTime) }}</time>
        </div>
        <p class="wiki-thread__content">
          {{ activeComment.content }}
        </p>
        <p v-if="activeComment.anchor" class="wiki-thread__quote">
          <YdIcon name="morph:quote" /> {{ activeComment.anchor.exact }}
        </p>
        <div class="wiki-thread__actions">
          <YdButton
            v-if="activeLocatable"
            type="button"
            variant="outline"
            size="sm"
            @click="emit('locate', activeComment.id)"
          >
            <YdIcon name="morph:crosshair" /> 定位原文
          </YdButton>
          <YdButton v-if="isLogin" type="button" variant="ghost" size="sm" @click="startReply(activeComment)">
            <YdIcon name="morph:corner-up-left" /> 回复
          </YdButton>
        </div>

        <ul v-if="activeComment.replies?.length" class="wiki-thread__replies">
          <li v-for="reply in activeComment.replies" :key="reply.id" class="wiki-thread__reply">
            <div class="wiki-thread__meta">
              <strong class="wiki-thread__author">{{ reply.authorName || '读者' }}</strong>
              <time v-if="formatWikiCommentTime(reply.createTime)">{{ formatWikiCommentTime(reply.createTime) }}</time>
            </div>
            <p class="wiki-thread__content">
              {{ reply.content }}
            </p>
          </li>
        </ul>

        <div v-if="isLogin && replyTarget?.id === activeComment.id" class="wiki-thread__composer">
          <p class="wiki-thread__hint">
            回复 {{ replyTarget.authorName || '读者' }}（回复继承根评论的引用）
          </p>
          <YdTextarea
            v-model="replyDraft"
            :autosize="{ minRows: 2, maxRows: 6 }"
            maxlength="2000"
            placeholder="回复该评论"
          />
          <div class="wiki-thread__actions">
            <YdButton type="button" size="sm" :loading="replySubmitting" :disabled="!replyDraft.trim()" @click="submitReply">
              <YdIcon name="morph:send" /> 发表回复
            </YdButton>
            <YdButton type="button" variant="ghost" size="sm" @click="cancelReply">
              取消
            </YdButton>
          </div>
          <p v-if="replyError" class="wiki-thread__error" role="alert">
            {{ replyError }}
          </p>
          <p v-if="replyNotice" class="wiki-thread__notice" role="status">
            {{ replyNotice }}
          </p>
        </div>
      </div>

      <div v-if="anchoredComments.length" class="wiki-thread__list">
        <p class="wiki-thread__section">
          文中引用评论（{{ anchoredComments.length }}）
        </p>
        <YdButton
          v-for="comment in anchoredComments"
          :key="comment.id"
          class="wiki-thread__card"
          :class="{ 'is-active': comment.id === activeId }"
          type="button"
          variant="ghost"
          size="sm"
          @click="emit('locate', comment.id)"
        >
          <span class="wiki-thread__card-excerpt"><YdIcon name="morph:quote" /> {{ comment.anchor?.exact }}</span>
          <span class="wiki-thread__card-meta">
            {{ comment.authorName || '读者' }}
            <template v-if="replyCount(comment)">· {{ replyCount(comment) }} 条回复</template>
            <template v-if="stateLabelOf(comment)">· {{ stateLabelOf(comment) }}</template>
          </span>
        </YdButton>
      </div>
      <YdEmpty
        v-else-if="!activeComment"
        class="wiki-thread__state"
        icon="morph:quote"
        title="还没有引用评论"
        description="在正文中选中一段文字，点击浮出的「评论」即可引用该文字发表评论。"
      />

      <YdButton v-if="hasMore" type="button" variant="outline" size="sm" :loading="loading" @click="controller.loadPage(page + 1)">
        加载更多评论
      </YdButton>
      <p v-if="loadError" class="wiki-thread__error" role="alert">
        {{ loadError }}
      </p>
      <p v-if="!isLogin" class="wiki-thread__login">
        <RouterLink :to="loginHref">
          登录
        </RouterLink> 后可发表引用评论；匿名评论未开放。
      </p>
      <p v-else class="wiki-thread__total">
        共 {{ total }} 条评论
      </p>
    </template>
  </section>
</template>

<style scoped>
.wiki-thread {
  display: grid;
  gap: 14px;
  align-content: start;
}

.wiki-thread__head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.wiki-thread__title {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--yb-site-heading, var(--color-text-1));
}

.wiki-thread__title :deep(svg) {
  font-size: 18px;
}

.wiki-thread__state {
  min-height: 140px;
}

.wiki-thread__composer,
.wiki-thread__active {
  display: grid;
  gap: 10px;
  padding: 14px;
  background: var(--yb-site-surface, var(--color-bg-2));
  border: 1px solid var(--yb-site-border, var(--color-border-2));
}

.wiki-thread__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--yb-site-muted, var(--color-text-3));
}

.wiki-thread__author {
  font-size: 13px;
  color: var(--yb-site-heading, var(--color-text-1));
}

.wiki-thread__content {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--yb-site-text, var(--color-text-1));
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.wiki-thread__quote {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  padding: 6px 10px;
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--yb-site-text-2, var(--color-text-2));
  overflow-wrap: anywhere;
  border-left: 2px solid var(--yb-site-primary, var(--color-border-2));
}

.wiki-thread__quote :deep(svg) {
  flex: 0 0 auto;
  margin-top: 3px;
}

.wiki-thread__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.wiki-thread__replies {
  display: grid;
  gap: 12px;
  padding: 12px 0 0 14px;
  margin: 0;
  list-style: none;
  border-left: 2px solid var(--yb-site-border, var(--color-border-2));
}

.wiki-thread__reply {
  display: grid;
  gap: 6px;
}

.wiki-thread__section {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--yb-site-muted, var(--color-text-3));
  letter-spacing: 0.04em;
}

.wiki-thread__list {
  margin-top: 6px;
}

.wiki-thread__card {
  display: grid;
  gap: 6px;
  justify-items: start;
  width: 100%;
  height: auto;
  padding: 10px 12px;
  margin-bottom: 8px;
  text-align: left;
  white-space: normal;
  background: var(--yb-site-surface, var(--color-bg-2));
  border: 1px solid var(--yb-site-border, var(--color-border-2));
}

.wiki-thread__card.is-active {
  border-color: var(--yb-site-primary, var(--color-border-2));
}

.wiki-thread__card-excerpt {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 13px;
  line-height: 1.6;
  color: var(--yb-site-text, var(--color-text-1));
  overflow-wrap: anywhere;
}

.wiki-thread__card-excerpt :deep(svg) {
  flex: 0 0 auto;
  margin-top: 3px;
}

.wiki-thread__card-meta {
  font-size: 12px;
  color: var(--yb-site-muted, var(--color-text-3));
}

.wiki-thread__hint,
.wiki-thread__login,
.wiki-thread__total {
  margin: 0;
  font-size: 12px;
  color: var(--yb-site-muted, var(--color-text-3));
}

.wiki-thread__login a {
  color: var(--yb-site-primary, var(--color-text-1));
}

.wiki-thread__error {
  margin: 0;
  font-size: 13px;
  color: var(--color-danger-6, #f53f3f);
}

.wiki-thread__notice {
  margin: 0;
  font-size: 13px;
  color: var(--yb-site-text-2, var(--color-text-2));
}
</style>

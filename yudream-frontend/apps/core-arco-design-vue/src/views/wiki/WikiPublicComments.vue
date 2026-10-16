<script setup lang="ts">
import type { WikiPublicCommentsController } from './use-wiki-public-comments'
import type { WikiCommentAnchorState } from './wiki-comment-anchor'
import type { WikiPublicComment } from './wiki-public-api'
import { ref } from 'vue'
import { formatWikiCommentTime } from './use-wiki-public-comments'
import { resolveWikiCommentAnchorState, WIKI_COMMENT_ANCHOR_STATE_LABELS } from './wiki-comment-anchor'

/**
 * 公开文章评论（整页入口）：只读已发布评论，登录后可发表整页评论或回复。
 *
 * - 数据与能力声明由 useWikiPublicComments 统一持有（与正文引用线程面板共用一份状态）；
 * - 是否展示评论区只认服务端空间资料的 commentEnabled；拿不到能力声明时不展示；
 * - 匿名发表保持关闭：无登录态不渲染输入框，写接口本身也由服务端要求登录；
 * - 新评论由服务端落为待审核状态，提交后只提示审核中，不把待审核内容塞进已发布列表；
 * - 带引用的评论展示引用摘录与定位状态，点击摘录请求定位到正文的唯一命中位置
 *   （歧义/失锚不会滚动到错误文字，只提示「无法定位」）。
 */
const props = defineProps<{
  controller: WikiPublicCommentsController
  /** 客户端锚点状态（本地歧义 + 服务端投影；无锚点评论不在表里）；缺省时按服务端状态退回 */
  statuses?: Record<string, WikiCommentAnchorState>
}>()

const emit = defineEmits<{ locate: [commentId: string] }>()

const { commentEnabled, comments, page, loading, loadError, isLogin, hasMore, loginHref } = props.controller

const draft = ref('')
const replyTarget = ref<WikiPublicComment | null>(null)
const submitting = ref(false)
const submitError = ref('')
const submitNotice = ref('')

/** 无锚点评论（含历史评论）没有引用态：返回 null，不造「整页评论」状态。 */
function anchorStateOf(comment: WikiPublicComment): WikiCommentAnchorState | null {
  return props.statuses?.[comment.id] ?? resolveWikiCommentAnchorState(comment)
}

function anchorLabelOf(comment: WikiPublicComment): string {
  const state = anchorStateOf(comment)
  return state ? WIKI_COMMENT_ANCHOR_STATE_LABELS[state] : ''
}

function startReply(comment: WikiPublicComment) {
  replyTarget.value = comment
}

function cancelReply() {
  replyTarget.value = null
}

async function submitComment() {
  const content = draft.value.trim()
  if (!content || !isLogin.value || submitting.value) {
    return
  }
  submitting.value = true
  submitError.value = ''
  submitNotice.value = ''
  const result = await props.controller.createComment({ content, parentId: replyTarget.value?.id })
  if (result.ok) {
    draft.value = ''
    replyTarget.value = null
    // 服务端把新评论落为待审核：只提示审核中，不伪造已发布状态
    submitNotice.value = '评论已提交，审核通过后会在本页展示。'
  }
  else {
    submitError.value = result.error || '评论提交失败，请稍后重试'
  }
  submitting.value = false
}
</script>

<template>
  <section
    v-if="commentEnabled"
    class="wiki-comments"
    data-wiki-feature="comments"
    data-wiki-contract="/api/public/wiki/{spaceSlug}/nodes/{nodeId}/comments"
    aria-labelledby="wiki-comments-title"
  >
    <h2 id="wiki-comments-title" class="wiki-comments__title">
      <YdIcon name="morph:message-circle-more" /> 评论
    </h2>

    <div v-if="loading && !comments.length && !loadError" class="wiki-comments__state">
      <YdLoading :size="22" text="评论加载中…" />
    </div>
    <div v-else-if="loadError" class="wiki-comments__error">
      <YdAlert variant="destructive" icon="morph:circle-alert" title="评论加载失败" :description="loadError" />
      <YdButton type="button" variant="outline" size="sm" @click="controller.loadPage(1)">
        <YdIcon name="morph:refresh-cw" /> 重试
      </YdButton>
    </div>
    <YdEmpty
      v-else-if="!comments.length"
      class="wiki-comments__state"
      icon="morph:message-circle"
      title="还没有评论"
      description="登录后可以留下第一条评论，审核通过后会展示在这里。"
    />

    <ul v-else class="wiki-comments__list">
      <li v-for="comment in comments" :key="comment.id" class="wiki-comments__item">
        <div class="wiki-comments__meta">
          <strong class="wiki-comments__author">{{ comment.authorName || '读者' }}</strong>
          <YdTag v-if="comment.pinned" class="wiki-comments__pinned">
            置顶
          </YdTag>
          <YdTag v-if="anchorLabelOf(comment)" variant="outline" class="wiki-comments__anchor-state">
            {{ anchorLabelOf(comment) }}
          </YdTag>
          <time v-if="formatWikiCommentTime(comment.createTime)" class="wiki-comments__time">{{ formatWikiCommentTime(comment.createTime) }}</time>
        </div>
        <p class="wiki-comments__content">
          {{ comment.content }}
        </p>
        <YdButton
          v-if="comment.anchor?.exact"
          class="wiki-comments__quote"
          type="button"
          variant="ghost"
          size="sm"
          @click="emit('locate', comment.id)"
        >
          <YdIcon name="morph:quote" /> {{ comment.anchor.exact }}
        </YdButton>
        <ul v-if="comment.replies?.length" class="wiki-comments__replies">
          <li v-for="reply in comment.replies" :key="reply.id" class="wiki-comments__reply">
            <div class="wiki-comments__meta">
              <strong class="wiki-comments__author">{{ reply.authorName || '读者' }}</strong>
              <time v-if="formatWikiCommentTime(reply.createTime)" class="wiki-comments__time">{{ formatWikiCommentTime(reply.createTime) }}</time>
            </div>
            <p class="wiki-comments__content">
              {{ reply.content }}
            </p>
          </li>
        </ul>
        <YdButton
          v-if="isLogin && !comment.parentId"
          class="wiki-comments__reply-action"
          type="button"
          variant="ghost"
          size="sm"
          @click="startReply(comment)"
        >
          <YdIcon name="morph:corner-up-left" /> 回复
        </YdButton>
      </li>
    </ul>

    <YdButton v-if="hasMore" type="button" variant="outline" size="sm" :loading="loading" @click="controller.loadPage(page + 1)">
      加载更多评论
    </YdButton>

    <div v-if="isLogin" class="wiki-comments__composer">
      <p v-if="replyTarget" class="wiki-comments__replying">
        <span>回复 {{ replyTarget.authorName || '读者' }}</span>
        <YdButton type="button" variant="ghost" size="sm" @click="cancelReply">
          取消
        </YdButton>
      </p>
      <YdTextarea
        v-model="draft"
        :autosize="{ minRows: 3, maxRows: 8 }"
        maxlength="2000"
        placeholder="以当前登录身份发表评论，提交后需审核"
      />
      <div class="wiki-comments__actions">
        <YdButton type="button" :loading="submitting" :disabled="!draft.trim()" @click="submitComment">
          <YdIcon name="morph:send" /> 发表评论
        </YdButton>
        <span class="wiki-comments__hint">提交后进入待审核状态，审核通过后展示。</span>
      </div>
      <p v-if="submitError" class="wiki-comments__error-text" role="alert">
        {{ submitError }}
      </p>
      <p v-if="submitNotice" class="wiki-comments__notice" role="status">
        {{ submitNotice }}
      </p>
    </div>
    <p v-else class="wiki-comments__login">
      <RouterLink :to="loginHref">
        登录
      </RouterLink> 后可发表评论；匿名评论未开放。
    </p>
  </section>
</template>

<style scoped>
.wiki-comments {
  display: grid;
  gap: 14px;
  padding-top: 24px;
  margin-top: 48px;
  border-top: 1px solid var(--yb-site-border, var(--color-border-2));
}

.wiki-comments__title {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--yb-site-heading, var(--color-text-1));
}

.wiki-comments__title :deep(svg) {
  font-size: 18px;
}

.wiki-comments__state {
  min-height: 120px;
}

.wiki-comments__error {
  display: grid;
  gap: 10px;
  justify-items: start;
}

.wiki-comments__list {
  display: grid;
  gap: 14px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.wiki-comments__item {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  background: var(--yb-site-surface, var(--color-bg-2));
  border: 1px solid var(--yb-site-border, var(--color-border-2));
}

.wiki-comments__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--yb-site-muted, var(--color-text-3));
}

.wiki-comments__author {
  font-size: 13px;
  color: var(--yb-site-heading, var(--color-text-1));
}

.wiki-comments__content {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--yb-site-text, var(--color-text-1));
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.wiki-comments__quote {
  justify-self: start;
  max-width: 100%;
  padding: 4px 10px;
  border-left: 2px solid var(--yb-site-primary, var(--color-border-2));
  color: var(--yb-site-text-2, var(--color-text-2));
  font-size: 13px;
  text-align: left;
  white-space: normal;
}

.wiki-comments__quote :deep(svg) {
  flex: 0 0 auto;
}

.wiki-comments__replies {
  display: grid;
  gap: 12px;
  padding: 12px 0 0 16px;
  margin: 4px 0 0;
  list-style: none;
  border-left: 2px solid var(--yb-site-border, var(--color-border-2));
}

.wiki-comments__reply {
  display: grid;
  gap: 6px;
}

.wiki-comments__reply-action {
  justify-self: start;
}

.wiki-comments__composer {
  display: grid;
  gap: 10px;
  padding: 16px;
  background: var(--yb-site-hover, var(--color-fill-1));
  border: 1px solid var(--yb-site-border, var(--color-border-2));
}

.wiki-comments__replying {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0;
  font-size: 13px;
  color: var(--yb-site-text-2, var(--color-text-2));
}

.wiki-comments__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.wiki-comments__hint,
.wiki-comments__login {
  margin: 0;
  font-size: 12px;
  color: var(--yb-site-muted, var(--color-text-3));
}

.wiki-comments__login a {
  color: var(--yb-site-primary, var(--color-text-1));
}

.wiki-comments__error-text {
  margin: 0;
  font-size: 13px;
  color: var(--color-danger-6, #f53f3f);
}

.wiki-comments__notice {
  margin: 0;
  font-size: 13px;
  color: var(--yb-site-text-2, var(--color-text-2));
}
</style>

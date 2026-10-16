import type { ComputedRef, Ref } from 'vue'
import type { WikiCommentAnchorPayload, WikiPublicComment } from './wiki-public-api'
import { computed, ref, watch } from 'vue'
import dayjs from '@/utils/dayjs'
import { createPublicWikiComment, fetchPublicWikiComments, fetchPublicWikiProfile } from './wiki-public-api'

/**
 * 公开文章评论的数据控制器（底部整页评论入口与正文引用线程共用一份）。
 *
 * 复用同一套行为，不在两个入口各写一份：
 * - 是否展示评论区只认服务端空间资料的 commentEnabled，拿不到能力声明一律不展示；
 * - 匿名发表未开放：写操作只在登录态渲染入口，服务端另有认证校验；
 * - 分页与请求代次：切页/切空间/切节点后落地的旧响应直接丢弃，避免评论串台；
 * - 新评论由服务端落为待审核状态，提交成功后不把待审核内容塞进已发布列表。
 */

const PAGE_SIZE = 20

export interface WikiPublicCommentsScope {
  spaceSlug: string
  nodeId: string
}

export interface WikiPublicCommentCreateInput {
  content: string
  /** 回复目标：服务端不接受回复另造锚点（锚点继承根评论），这里也不会提交 */
  parentId?: string
  /** 根评论的选区锚点草稿：只含引用文本与定位提示，版本身份由服务端盖章 */
  anchor?: WikiCommentAnchorPayload
}

export interface WikiPublicCommentCreateResult {
  ok: boolean
  error?: string
}

export interface WikiPublicCommentsController {
  /** 服务端能力声明；null 表示尚未拿到或拉取失败，不展示评论读写入口 */
  commentEnabled: Ref<boolean | null>
  comments: Ref<WikiPublicComment[]>
  total: Ref<number>
  page: Ref<number>
  loading: Ref<boolean>
  loadError: Ref<string>
  isLogin: ComputedRef<boolean>
  hasMore: ComputedRef<boolean>
  loginHref: ComputedRef<{ path: string, query: { redirect: string } }>
  loadPage: (next: number) => Promise<void>
  reload: () => Promise<void>
  createComment: (input: WikiPublicCommentCreateInput) => Promise<WikiPublicCommentCreateResult>
}

/** 评论时间展示：解析失败返回空串，不渲染半个时间戳 */
export function formatWikiCommentTime(value?: string): string {
  if (!value) {
    return ''
  }
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : ''
}

export function useWikiPublicComments(scope: () => WikiPublicCommentsScope): WikiPublicCommentsController {
  const route = useRoute()
  const accountStore = useAppAccountStore()

  const commentEnabled = ref<boolean | null>(null)
  const comments = ref<WikiPublicComment[]>([])
  const total = ref(0)
  const page = ref(1)
  const loading = ref(false)
  const loadError = ref('')

  const isLogin = computed(() => accountStore.isLogin)
  const hasMore = computed(() => comments.value.length < total.value)
  const loginHref = computed(() => ({ path: '/login', query: { redirect: route.fullPath } }))

  // 请求代际：能力声明与列表各自独立，旧响应一律不进状态
  let capabilityRequestId = 0
  let listRequestId = 0

  function reset() {
    commentEnabled.value = null
    comments.value = []
    total.value = 0
    page.value = 1
    loading.value = false
    loadError.value = ''
  }

  async function loadCapability() {
    const requestId = ++capabilityRequestId
    const { spaceSlug } = scope()
    try {
      const profile = (await fetchPublicWikiProfile(spaceSlug)).data
      if (requestId !== capabilityRequestId) {
        return
      }
      commentEnabled.value = profile?.commentEnabled === true
    }
    catch {
      if (requestId !== capabilityRequestId) {
        return
      }
      // 没有服务端能力声明就不展示评论区，不凭猜测开放读写入口
      commentEnabled.value = null
      return
    }
    if (requestId === capabilityRequestId && commentEnabled.value) {
      void loadPage(1)
    }
  }

  async function loadPage(next: number) {
    const { spaceSlug, nodeId } = scope()
    if (!spaceSlug || !nodeId) {
      return
    }
    const requestId = ++listRequestId
    loading.value = true
    loadError.value = ''
    try {
      const result = (await fetchPublicWikiComments(spaceSlug, nodeId, { page: next, size: PAGE_SIZE })).data
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
      loadError.value = exception?.message || '评论加载失败，请稍后重试'
    }
    finally {
      if (requestId === listRequestId) {
        loading.value = false
      }
    }
  }

  async function reload() {
    if (commentEnabled.value === false) {
      return
    }
    if (commentEnabled.value === null) {
      await loadCapability()
      return
    }
    await loadPage(Math.max(page.value, 1))
  }

  async function createComment(input: WikiPublicCommentCreateInput): Promise<WikiPublicCommentCreateResult> {
    const { spaceSlug, nodeId } = scope()
    const content = input.content.trim()
    if (!content) {
      return { ok: false, error: '评论内容不能为空' }
    }
    if (!isLogin.value) {
      return { ok: false, error: '请先登录后再发表评论' }
    }
    try {
      await createPublicWikiComment(spaceSlug, nodeId, {
        content,
        parentId: input.parentId,
        // 回复不携带锚点：服务端只允许根评论定锚，回复继承根评论
        anchor: input.parentId ? undefined : input.anchor,
      })
      return { ok: true }
    }
    catch (exception: any) {
      return { ok: false, error: exception?.message || '评论提交失败，请稍后重试' }
    }
  }

  watch([() => scope().spaceSlug, () => scope().nodeId], ([slug, nodeId]) => {
    capabilityRequestId += 1
    listRequestId += 1
    reset()
    if (slug && nodeId) {
      void loadCapability()
    }
  }, { immediate: true })

  onBeforeUnmount(() => {
    capabilityRequestId += 1
    listRequestId += 1
  })

  return {
    commentEnabled,
    comments,
    total,
    page,
    loading,
    loadError,
    isLogin,
    hasMore,
    loginHref,
    loadPage,
    reload,
    createComment,
  }
}

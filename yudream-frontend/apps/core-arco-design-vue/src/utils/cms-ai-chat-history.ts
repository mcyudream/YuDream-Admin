import type { YdChatMessage } from '@yudream/components'
import type { YdAgentChatSession, YdAgentChatSessionMeta, YdAgentChatSessionStore } from '@/components/YdAgentChatPanel/types'

const DB_NAME = 'yudream-cms-ai-chat-history'
const DB_VERSION = 2
const STORE_NAME = 'sessions'
const TARGET_TIME_INDEX = 'targetKeyUpdatedAt'
const ACTIVE_SESSION_PREFIX = 'yb:cms:ai-active-session:'

export interface CmsAiChatAttachmentMeta {
  name?: string
  fileType?: string
  size?: number
}

/** CMS 画布 AI 会话：按编辑目标（页面 / 首页）分组，多会话可管理 */
export interface CmsAiChatSession extends YdAgentChatSession {
  targetKey: string
  targetType: string
  targetId: string
  targetLabel: string
  attachments?: CmsAiChatAttachmentMeta[]
  createdAt: number
  updatedAt: number
  messages: YdChatMessage[]
}

export type CmsAiChatSessionMeta = Omit<CmsAiChatSession, 'messages'> & YdAgentChatSessionMeta

let dbPromise: Promise<IDBDatabase> | null = null

export function cmsAiChatTargetKey(type: string, id: string | undefined | null) {
  return `${type || 'page'}:${String(id || 'draft')}`
}

/** 记录某个编辑目标最近一次打开的会话，重新进入构建器时自动恢复 */
export function readActiveCmsAiChatSessionId(targetKey: string) {
  try {
    return localStorage.getItem(`${ACTIVE_SESSION_PREFIX}${targetKey}`) || ''
  }
  catch {
    return ''
  }
}

export function writeActiveCmsAiChatSessionId(targetKey: string, sessionId: string) {
  try {
    if (sessionId) {
      localStorage.setItem(`${ACTIVE_SESSION_PREFIX}${targetKey}`, sessionId)
    }
    else {
      localStorage.removeItem(`${ACTIVE_SESSION_PREFIX}${targetKey}`)
    }
  }
  catch {
    // localStorage 不可用时静默降级为内存会话
  }
}

export function sanitizeCmsVisibleContent(content: string) {
  return String(content || '')
    .replace(/\n?\[工具上下文\][\s\S]*?(?=\n\n\[业务上下文\]|\n\n\[附件\]|$)/g, '')
    .replace(/\n?\[业务上下文\][\s\S]*?(?=\n\n\[附件\]|$)/g, '')
    .trim()
}

function sanitizeSessionMessages(session: CmsAiChatSession): CmsAiChatSession {
  return {
    ...session,
    messages: (session.messages || []).map(message => ({
      ...message,
      content: message.role === 'assistant' ? sanitizeCmsVisibleContent(message.content) : message.content,
    })),
  }
}

export async function saveCmsAiChatSession(session: CmsAiChatSession) {
  const db = await openDb()
  const tx = db.transaction(STORE_NAME, 'readwrite')
  tx.objectStore(STORE_NAME).put(session)
  await waitForTransaction(tx)
}

export async function getCmsAiChatSession(id: string) {
  const db = await openDb()
  const tx = db.transaction(STORE_NAME, 'readonly')
  const session = await requestToPromise<CmsAiChatSession | undefined>(tx.objectStore(STORE_NAME).get(id))
  return session ? sanitizeSessionMessages(session) : undefined
}

export async function listCmsAiChatSessionMetas(targetKey: string): Promise<CmsAiChatSessionMeta[]> {
  const db = await openDb()
  const tx = db.transaction(STORE_NAME, 'readonly')
  const index = tx.objectStore(STORE_NAME).index(TARGET_TIME_INDEX)
  const range = IDBKeyRange.bound([targetKey, 0], [targetKey, Number.MAX_SAFE_INTEGER])

  return new Promise((resolve, reject) => {
    const items: CmsAiChatSessionMeta[] = []
    const request = index.openCursor(range, 'prev')
    request.onerror = () => reject(request.error)
    request.onsuccess = () => {
      const cursor = request.result
      if (!cursor) {
        resolve(items)
        return
      }
      const { messages, ...meta } = cursor.value as CmsAiChatSession
      items.push({ ...meta, messageCount: Array.isArray(messages) ? messages.length : 0 })
      cursor.continue()
    }
  })
}

export async function deleteCmsAiChatSession(id: string) {
  const db = await openDb()
  const tx = db.transaction(STORE_NAME, 'readwrite')
  tx.objectStore(STORE_NAME).delete(id)
  await waitForTransaction(tx)
}

export async function clearCmsAiChatTarget(targetKey: string) {
  const db = await openDb()
  const tx = db.transaction(STORE_NAME, 'readwrite')
  const index = tx.objectStore(STORE_NAME).index(TARGET_TIME_INDEX)
  const range = IDBKeyRange.bound([targetKey, 0], [targetKey, Number.MAX_SAFE_INTEGER])

  await new Promise<void>((resolve, reject) => {
    const request = index.openCursor(range)
    request.onerror = () => reject(request.error)
    request.onsuccess = () => {
      const cursor = request.result
      if (!cursor) {
        resolve()
        return
      }
      cursor.delete()
      cursor.continue()
    }
  })
  await waitForTransaction(tx)
  writeActiveCmsAiChatSessionId(targetKey, '')
}

/**
 * 生成 YdAgentChatPanel 的会话持久化适配器。
 * targetKey 为函数，随当前编辑目标（页面 / 首页）动态求值；
 * 新建会话会自动补齐 target 维度字段并记录为当前活动会话。
 */
export function createCmsAiChatSessionStore(options: {
  targetKey: () => string
  targetType: () => string
  targetId: () => string
  targetLabel: () => string
}): YdAgentChatSessionStore {
  return {
    list: () => listCmsAiChatSessionMetas(options.targetKey()),
    load: async (id) => {
      const session = await getCmsAiChatSession(id)
      if (session) {
        writeActiveCmsAiChatSessionId(options.targetKey(), id)
      }
      return session ?? null
    },
    save: async (session) => {
      const now = Date.now()
      const existing = await getCmsAiChatSession(session.id)
      await saveCmsAiChatSession(toPersistableSession({
        ...session,
        targetKey: options.targetKey(),
        targetType: options.targetType(),
        targetId: options.targetId(),
        targetLabel: options.targetLabel(),
        createdAt: existing?.createdAt ?? now,
        updatedAt: session.updatedAt ?? now,
      }))
      writeActiveCmsAiChatSessionId(options.targetKey(), session.id)
    },
    remove: async (id) => {
      await deleteCmsAiChatSession(id)
      if (readActiveCmsAiChatSessionId(options.targetKey()) === id) {
        writeActiveCmsAiChatSessionId(options.targetKey(), '')
      }
    },
  }
}

function toPersistableSession(session: CmsAiChatSession): CmsAiChatSession {
  // Vue ref/reactive 中的 Proxy 不能直接被 IndexedDB structuredClone；先转成纯 JSON 数据再保存。
  return JSON.parse(JSON.stringify(sanitizeSessionMessages(session))) as CmsAiChatSession
}

function openDb() {
  if (dbPromise) {
    return dbPromise
  }
  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      // V1 会话消息为 tdesign ChatMessagesData，与 YdChatMessage 结构不兼容，升级时直接重建仓库
      if (db.objectStoreNames.contains(STORE_NAME)) {
        db.deleteObjectStore(STORE_NAME)
      }
      const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' })
      store.createIndex(TARGET_TIME_INDEX, ['targetKey', 'updatedAt'])
    }
    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result)
  })
  return dbPromise
}

function requestToPromise<T>(request: IDBRequest) {
  return new Promise<T>((resolve, reject) => {
    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result as T)
  })
}

function waitForTransaction(tx: IDBTransaction) {
  return new Promise<void>((resolve, reject) => {
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

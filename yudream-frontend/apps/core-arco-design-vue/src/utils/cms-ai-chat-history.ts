import type { ChatMessagesData } from '@tdesign-vue-next/chat'

const DB_NAME = 'yudream-cms-ai-chat-history'
const DB_VERSION = 1
const STORE_NAME = 'sessions'
const TARGET_TIME_INDEX = 'targetKeyUpdatedAt'

export interface CmsAiChatAttachmentMeta {
  name?: string
  fileType?: string
  size?: number
}

export interface CmsAiChatSession {
  id: string
  targetKey: string
  targetType: string
  targetId: string
  targetLabel: string
  title: string
  preview: string
  model?: string
  thinkingEnabled?: boolean
  attachments?: CmsAiChatAttachmentMeta[]
  createdAt: number
  updatedAt: number
  messages: ChatMessagesData[]
}

export type CmsAiChatSessionSummary = Omit<CmsAiChatSession, 'messages'>

export interface CmsAiChatSessionPage {
  items: CmsAiChatSessionSummary[]
  hasMore: boolean
}

let dbPromise: Promise<IDBDatabase> | null = null

export function cmsAiChatTargetKey(type: string, id: string | undefined | null) {
  return `${type || 'page'}:${String(id || 'draft')}`
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
  return requestToPromise<CmsAiChatSession | undefined>(tx.objectStore(STORE_NAME).get(id))
}

export async function listCmsAiChatSessionSummaries(targetKey: string, offset = 0, limit = 8): Promise<CmsAiChatSessionPage> {
  const db = await openDb()
  const tx = db.transaction(STORE_NAME, 'readonly')
  const index = tx.objectStore(STORE_NAME).index(TARGET_TIME_INDEX)
  const range = IDBKeyRange.bound([targetKey, 0], [targetKey, Number.MAX_SAFE_INTEGER])

  return new Promise((resolve, reject) => {
    const items: CmsAiChatSessionSummary[] = []
    let skipped = 0
    let settled = false
    const request = index.openCursor(range, 'prev')

    request.onerror = () => reject(request.error)
    request.onsuccess = () => {
      if (settled) {
        return
      }
      const cursor = request.result
      if (!cursor) {
        settled = true
        resolve({ items, hasMore: false })
        return
      }
      if (skipped < offset) {
        skipped += 1
        cursor.continue()
        return
      }
      if (items.length >= limit) {
        settled = true
        resolve({ items, hasMore: true })
        return
      }
      const { messages, ...summary } = cursor.value as CmsAiChatSession
      items.push(summary)
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
}

function openDb() {
  if (dbPromise) {
    return dbPromise
  }
  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' })
        store.createIndex(TARGET_TIME_INDEX, ['targetKey', 'updatedAt'])
      }
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

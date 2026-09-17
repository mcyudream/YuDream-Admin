import type { AxiosError } from 'axios'

interface BackendResultLike {
  code?: number
  message?: string
  msg?: string
  error?: string
  data?: { message?: string }
}

/**
 * 已知错误关键词对应的修复指引：报错时直接告诉用户下一步去哪里处理，
 * 避免「只报错不知道怎么办」。命中多个时取第一个。
 */
const ERROR_HINTS: Array<{ pattern: RegExp, hint: string }> = [
  { pattern: /能力未启用/, hint: '请到「平台 → 能力管理」启用对应能力后重试' },
  { pattern: /系统已初始化/, hint: '请直接使用管理员账号登录' },
]

export function hintForMessage(message: string) {
  const hint = ERROR_HINTS.find(item => item.pattern.test(message))?.hint
  return hint ? `${message}。修复指引：${hint}` : message
}

async function parseJsonBlob(blob: Blob) {
  try {
    return JSON.parse(await blob.text()) as BackendResultLike
  }
  catch {
    return undefined
  }
}

async function extractBackendMessage(error: AxiosError) {
  let data = error.response?.data as BackendResultLike | Blob | string | undefined
  if (data instanceof Blob
    && String(error.response?.headers?.['content-type'] || '').includes('application/json')) {
    data = await parseJsonBlob(data)
  }
  if (data && typeof data === 'object') {
    const result = data as BackendResultLike
    return result.message || result.msg || result.error || result.data?.message || ''
  }
  if (typeof data === 'string' && data.trim()) {
    return data.trim()
  }
  return ''
}

/**
 * 从接口错误中提取可读的中文提示：优先后端 message；
 * 无后端信息时把 axios 默认英文文案本地化，并尽量带上接口路径便于定位。
 */
export async function resolveApiErrorMessage(error: AxiosError) {
  const backendMessage = await extractBackendMessage(error)
  if (backendMessage) {
    return backendMessage
  }
  const status = error.response?.status
  if (status) {
    const url = error.config?.url ? `（${error.config.url}）` : ''
    return `请求失败（HTTP ${status}）${url}`
  }
  if (error.code === 'ECONNABORTED') {
    return '请求超时，请稍后重试'
  }
  if (error.code === 'ERR_NETWORK') {
    return '网络异常或后端服务不可达，请确认后端服务已启动'
  }
  return error.message || '请求失败'
}

/** 统一的接口错误 toast：提取后端 message 并附带修复指引。 */
export async function toastApiError(error: AxiosError) {
  const message = await resolveApiErrorMessage(error)
  useFaToast().error('错误', { description: hintForMessage(message) })
}

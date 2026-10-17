import type { PluginMarketPublication, PluginPublicationChannel, PluginPublicationStatus } from '@/api/modules/platform-plugin-market-source'

/**
 * 发布物展示口径：状态色、状态词、通道词与时间/大小格式化。
 * 发布页（作者视角）与审核页（审核视角）共用，避免两页各写一份后不一致。
 */
export function publicationStatusText(status: PluginPublicationStatus) {
  const map: Record<PluginPublicationStatus, string> = {
    PENDING: '待审核',
    PUBLISHED: '已发布',
    REJECTED: '已拒绝',
    REVOKED: '已下架',
  }
  return map[status] || status
}

/** 已发布走强调色，待审核中性，拒绝与下架用危险色（都是不可用的终态）。 */
export function publicationStatusVariant(status: PluginPublicationStatus) {
  return status === 'PUBLISHED' ? 'default' as const : status === 'PENDING' ? 'secondary' as const : 'destructive' as const
}

export function publicationChannelText(channel: PluginPublicationChannel) {
  return channel === 'PIPELINE' ? '流水线' : '界面上传'
}

export function publicationTitle(row: Pick<PluginMarketPublication, 'displayName' | 'code'>) {
  return row.displayName || row.code
}

/** 提交时间带 7 位小数时截断到分钟：秒与小数只会撑破单元格。 */
export function formatPublicationTime(value?: string) {
  if (!value) {
    return '—'
  }
  const match = value.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/)
  return match ? `${match[1]} ${match[2]}` : value
}

export function formatPublicationSize(bytes?: number) {
  if (!bytes) {
    return '—'
  }
  return bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

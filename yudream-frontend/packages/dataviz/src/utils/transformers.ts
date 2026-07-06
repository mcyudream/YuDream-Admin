import type { ChartDataset, ChartLink, ChartNode } from '../types'

/**
 * 将任意原始数据规范化为 ChartDataset
 * @param raw - 原始数据
 * @returns 规范化的数据集
 */
export function normalizeDataset(raw: any): ChartDataset {
  if (!raw || typeof raw !== 'object') {
    return { id: 'default', label: '', source: [] }
  }

  if (Array.isArray(raw.nodes) && Array.isArray(raw.links)) {
    return {
      id: raw.id ?? 'graph',
      label: raw.label ?? '',
      nodes: raw.nodes as ChartNode[],
      links: raw.links as ChartLink[],
    }
  }

  if (Array.isArray(raw)) {
    if (raw.length === 0) {
      return { id: 'default', label: '', source: [] }
    }

    const first = raw[0]
    if (Array.isArray(first)) {
      return {
        id: 'default',
        label: '',
        dimensions: first.map((_, index) => `dim${index}`),
        source: raw,
      }
    }

    return {
      id: 'default',
      label: '',
      dimensions: Object.keys(first as Record<string, unknown>),
      source: raw,
    }
  }

  if (Array.isArray(raw.source)) {
    return {
      id: raw.id ?? 'default',
      label: raw.label ?? '',
      dimensions: raw.dimensions,
      source: raw.source,
    }
  }

  return {
    id: raw.id ?? 'default',
    label: raw.label ?? '',
    source: [raw],
  }
}

/** 列表工具栏的状态计数筛选项。 */
export interface YdListToolbarStatusItem {
  value: string
  label: string
  count?: number
  /** 状态点语义：ok 用强调色并脉冲，bad 用危险色，其余走中性。 */
  tone?: 'ok' | 'bad' | 'muted' | 'neutral'
}

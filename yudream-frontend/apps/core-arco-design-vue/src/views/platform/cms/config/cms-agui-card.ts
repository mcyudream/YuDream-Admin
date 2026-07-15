export type AguiCardTone = 'danger' | 'info' | 'success' | 'warning'

export interface AguiCard {
  title: string
  summary: string
  tone: AguiCardTone
  fields: Array<{ label: string, value: string }>
  actions: Array<{ label: string, action: 'copy' | 'open' | 'submit', value: string }>
}

const tones = new Set<AguiCardTone>(['info', 'success', 'warning', 'danger'])
const actions = new Set<AguiCard['actions'][number]['action']>(['open', 'submit', 'copy'])

export function normalizeAguiCard(input: unknown): AguiCard {
  const source = input && typeof input === 'object' ? input as Record<string, unknown> : {}
  const tone = String(source.tone || 'info') as AguiCardTone
  return {
    title: String(source.title || 'AG-UI 卡片').trim() || 'AG-UI 卡片',
    summary: String(source.summary || '').trim(),
    tone: tones.has(tone) ? tone : 'info',
    fields: normalizeRows(source.fields, false),
    actions: normalizeRows(source.actions, true),
  }
}

function normalizeRows(value: unknown, actionRows: false): AguiCard['fields']
function normalizeRows(value: unknown, actionRows: true): AguiCard['actions']
function normalizeRows(value: unknown, actionRows: boolean): Array<Record<string, string>> {
  if (!Array.isArray(value)) {
    return []
  }
  return value.flatMap((item) => {
    if (!item || typeof item !== 'object') {
      return []
    }
    const row = item as Record<string, unknown>
    const label = String(row.label || '').trim()
    if (!label) {
      return []
    }
    if (!actionRows) {
      return [{ label, value: String(row.value ?? '') }]
    }
    const action = String(row.action || '') as AguiCard['actions'][number]['action']
    return actions.has(action) ? [{ label, action, value: String(row.value ?? '') }] : []
  })
}

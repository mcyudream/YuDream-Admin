import type { DashboardBreakpoint, DashboardCard, DashboardGridPlacement, DashboardLayoutItem } from '@/api/modules/system-dashboard'

export const COLUMNS: Record<DashboardBreakpoint, number> = { lg: 12, md: 8, sm: 4, xs: 1 }
export const BREAKPOINTS = Object.keys(COLUMNS) as DashboardBreakpoint[]
export const CELL_HEIGHT = 100

// Use the available canvas, including any open sidebar, rather than the window.
export function breakpointFor(width: number): DashboardBreakpoint {
  return width >= 1120 ? 'lg' : width >= 800 ? 'md' : width >= 540 ? 'sm' : 'xs'
}

export function cloneItems(items: DashboardLayoutItem[]): DashboardLayoutItem[] {
  return items.map(item => ({ ...item, placements: Object.fromEntries(Object.entries(item.placements || {}).map(([key, value]) => [key, { ...value }])) }))
}

function integer(value: number | undefined, fallback: number) {
  return Number.isFinite(value) ? Math.floor(value!) : fallback
}

export function overlaps(a: DashboardGridPlacement, b: DashboardGridPlacement) {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y
}

export function constrain(placement: Partial<DashboardGridPlacement>, card: DashboardCard, breakpoint: DashboardBreakpoint): DashboardGridPlacement {
  const columns = COLUMNS[breakpoint]
  const minW = breakpoint === 'xs' ? 1 : Math.min(card.minW || 1, columns)
  const w = Math.min(columns, Math.max(minW, integer(placement.w, card.defaultW)))
  return {
    x: Math.max(0, Math.min(columns - w, integer(placement.x, 0))),
    y: Math.max(0, integer(placement.y, 0)),
    w,
    h: Math.max(card.minH || 1, integer(placement.h, card.defaultH)),
  }
}

// A pure, repeatable layout shared by view, editor and persistence. Unknown and
// hidden cards are retained, but never occupy space (a plugin may return later).
export function normalizeLayout(items: DashboardLayoutItem[], cards: DashboardCard[]): DashboardLayoutItem[] {
  const map = new Map(cards.map(card => [card.code, card]))
  const seen = new Set<string>()
  const next = cloneItems(items).filter(item => !seen.has(item.cardCode) && seen.add(item.cardCode))
  for (const breakpoint of BREAKPOINTS) {
    const occupied: DashboardGridPlacement[] = []
    const ordered = next.filter(item => item.visible && map.has(item.cardCode)).sort((a, b) => {
      const pa = a.placements[breakpoint] || a.placements.lg
      const pb = b.placements[breakpoint] || b.placements.lg
      return (pa?.y || 0) - (pb?.y || 0) || (pa?.x || 0) - (pb?.x || 0)
    })
    for (const item of ordered) {
      const p = constrain(item.placements[breakpoint] || item.placements.lg || {}, map.get(item.cardCode)!, breakpoint)
      let collision = occupied.find(other => overlaps(p, other))
      while (collision) {
        p.y = collision.y + collision.h
        collision = occupied.find(other => overlaps(p, other))
      }
      occupied.push(p)
      item.placements[breakpoint] = p
    }
  }
  return next
}

function firstSlot(occupied: DashboardGridPlacement[], w: number, h: number, columns: number) {
  // Only rectangle edges can create a new available row; avoids scanning a
  // corrupted, arbitrarily large stored y one row at a time.
  const rows = [...new Set([0, ...occupied.map(p => p.y + p.h)])].sort((a, b) => a - b)
  for (const y of rows) {
    for (let x = 0; x <= columns - w; x++) {
      const p = { x, y, w, h }
      if (occupied.every(other => !overlaps(p, other))) {
        return p
      }
    }
  }
  return { x: 0, y: Math.max(0, ...occupied.map(p => p.y + p.h)), w, h }
}

export function arrangeLayout(items: DashboardLayoutItem[], cards: DashboardCard[], mode: 'compact' | 'recommended', only?: DashboardBreakpoint) {
  const next = normalizeLayout(items, cards)
  const map = new Map(cards.map(card => [card.code, card]))
  for (const breakpoint of only ? [only] : BREAKPOINTS) {
    const columns = COLUMNS[breakpoint]
    const occupied: DashboardGridPlacement[] = []
    const priority = (item: DashboardLayoutItem) => map.get(item.cardCode)?.component === 'PROFILE_SUMMARY' ? -2 : map.get(item.cardCode)?.component === 'QUICK_ACTIONS' ? -1 : 0
    const ordered = next.filter(item => item.visible && map.has(item.cardCode)).sort((a, b) => {
      if (mode === 'recommended' && priority(a) !== priority(b)) {
        return priority(a) - priority(b)
      }
      const pa = a.placements[breakpoint]!
      const pb = b.placements[breakpoint]!
      return pa.y - pb.y || pa.x - pb.x
    })
    for (const item of ordered) {
      const card = map.get(item.cardCode)!
      let p = item.placements[breakpoint]!
      if (mode === 'recommended') {
        let w = Math.min(card.defaultW, columns)
        let h = Math.max(card.defaultH, card.minH)
        if (card.component === 'PROFILE_SUMMARY') {
          w = columns === 12 ? 4 : columns === 8 ? 3 : columns
          h = 3
        }
        else if (card.component === 'QUICK_ACTIONS') {
          w = columns === 12 ? 8 : columns === 8 ? 5 : columns
          h = 3
        }
        else if (columns === 8) {
          w = card.defaultW > 4 ? 8 : 4
        }
        p = constrain({ w, h }, card, breakpoint)
      }
      item.placements[breakpoint] = firstSlot(occupied, p.w, p.h, columns)
      occupied.push(item.placements[breakpoint]!)
    }
  }
  return next
}

export function addLayoutCard(items: DashboardLayoutItem[], cards: DashboardCard[], card: DashboardCard) {
  const next = cloneItems(items)
  let item = next.find(row => row.cardCode === card.code)
  if (!item) {
    item = { cardCode: card.code, visible: true, placements: {} }
    next.push(item)
  }
  item.visible = true
  for (const breakpoint of BREAKPOINTS) {
    const occupied = normalizeLayout(next.filter(row => row.cardCode !== card.code), cards)
      .filter(row => row.visible && cards.some(card => card.code === row.cardCode))
      .map(row => row.placements[breakpoint]!)
    const size = constrain({}, card, breakpoint)
    item.placements[breakpoint] = firstSlot(occupied, size.w, size.h, COLUMNS[breakpoint])
  }
  return normalizeLayout(next, cards)
}

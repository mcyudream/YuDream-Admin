import type { DashboardCard, DashboardLayoutItem } from '@/api/modules/system-dashboard'
import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test
import { describe, it } from 'node:test'
import { addLayoutCard, arrangeLayout, breakpointFor, BREAKPOINTS, cloneItems, COLUMNS, normalizeLayout, overlaps } from './layout'

const card = (code: string, component = 'PLUGIN'): DashboardCard => ({ code, title: code, component, source: 'SYSTEM', defaultW: 4, defaultH: 3, minW: 3, minH: 2, sort: 0, defaultOnFirstVisit: false })
const cards = [card('profile', 'PROFILE_SUMMARY'), { ...card('quick', 'QUICK_ACTIONS'), defaultW: 8, minW: 4 }, card('plugin'), card('stats')]
const items: DashboardLayoutItem[] = cards.map((card, index) => ({ cardCode: card.code, visible: true, placements: { lg: { x: 0, y: index * 5, w: 12, h: 3 } } }))

function assertValid(layout: DashboardLayoutItem[], available = cards) {
  for (const breakpoint of BREAKPOINTS) {
    const visible = layout.filter(item => item.visible && available.some(card => card.code === item.cardCode))
    visible.forEach((item, index) => {
      const p = item.placements[breakpoint]!
      assert.ok(p.x >= 0 && p.y >= 0 && p.x + p.w <= COLUMNS[breakpoint])
      assert.ok(p.h >= available.find(card => card.code === item.cardCode)!.minH)
      assert.ok(visible.slice(index + 1).every(other => !overlaps(p, other.placements[breakpoint]!)))
    })
  }
}

describe('workbench layout model', () => {
  it('responds to available canvas width when a sidebar opens', () => {
    assert.equal(breakpointFor(1300), 'lg')
    assert.equal(breakpointFor(1080), 'md')
    assert.equal(breakpointFor(700), 'sm')
    assert.equal(breakpointFor(366), 'xs')
  })

  it('repairs legacy full-width and overlapping cards without dropping a card', () => {
    const legacy = cloneItems(items)
    legacy[1].placements.lg = { x: -4, y: 0, w: 99, h: 0 }
    legacy[2].placements.xs = { x: 8, y: Number.NaN, w: 4, h: 3 }
    const repaired = normalizeLayout(legacy, cards)
    assertValid(repaired)
    assert.deepEqual(normalizeLayout(repaired, cards), repaired)
    assert.equal(repaired.length, items.length)
    assert.equal(legacy[1].placements.lg.x, -4)
  })

  it('pairs welcome and shortcuts in the first row on both desktop grids', () => {
    const layout = arrangeLayout(items, cards, 'recommended')
    for (const bp of ['lg', 'md'] as const) {
      const a = layout[0].placements[bp]!
      const b = layout[1].placements[bp]!
      assert.equal(a.y, 0)
      assert.equal(b.y, 0)
      assert.equal(a.w + b.w, COLUMNS[bp])
      assert.equal(a.h, b.h)
    }
    assertValid(layout)
    assert.deepEqual(normalizeLayout(JSON.parse(JSON.stringify(layout)), cards), layout)
  })

  it('compacts only the edited screen and preserves card dimensions', () => {
    const initial = normalizeLayout(items, cards)
    const compact = arrangeLayout(initial, cards, 'compact', 'lg')
    for (let i = 0; i < compact.length; i++) {
      assert.equal(compact[i].placements.lg!.h, initial[i].placements.lg!.h)
      assert.equal(compact[i].placements.lg!.w, initial[i].placements.lg!.w)
      assert.deepEqual(compact[i].placements.xs, initial[i].placements.xs)
      assert.equal(compact[i].placements.lg!.y, i * 3)
    }
  })

  it('retains disabled plugin preferences without leaving empty occupied space', () => {
    const unavailable = { cardCode: 'disabled-plugin', visible: true, placements: { lg: { x: 0, y: 0, w: 12, h: 100 } } }
    const hidden = { ...items[3], visible: false }
    const layout = arrangeLayout([unavailable, hidden, ...items.slice(0, 3)], cards, 'recommended')
    assert.deepEqual(layout[0], unavailable)
    assert.deepEqual(layout[1], hidden)
    assert.equal(layout[2].placements.lg!.y, 0)
    assertValid(layout)
  })

  it('adds and restores cards once, without collisions on any screen', () => {
    const initial = arrangeLayout(items.slice(0, 3), cards, 'recommended')
    const added = addLayoutCard(initial, cards, cards[3])
    assert.equal(added.filter(item => item.cardCode === 'stats').length, 1)
    assertValid(added)
    added[3].visible = false
    const restored = addLayoutCard(added, cards, cards[3])
    assert.equal(restored.length, 4)
    assert.equal(restored[3].visible, true)
    assertValid(restored)
    assert.equal(initial.length, 3)
  })
})

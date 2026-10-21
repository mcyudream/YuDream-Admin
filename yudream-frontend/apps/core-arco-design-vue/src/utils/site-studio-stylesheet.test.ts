import type { StudioAsset } from '../types/site-studio'
import assert from 'node:assert/strict'
import test from 'node:test'
import { formatStudioStylesheet, parseStudioStylesheet } from './site-studio-stylesheet'

const assets: StudioAsset[] = [{ id: 'font1', name: 'mc', url: '/api/files/mc.woff2', mimeType: 'font/woff2', kind: 'font' }]
const CSS = `
@font-face { font-family: "Minecraft"; src: url('/api/files/mc.woff2') format('woff2'); }
.mc-btn, .mc-link { color: #fff; border-image: url(/api/files/b.png) 3 fill !important; }
.mc-btn:hover { color: #ff5; }
.mc-link[aria-current="page"] { color: #5f5; }
:hover > .mc-sub { display: flex; }
[aria-expanded="true"] > .mc-sub { display: flex; }
@media (max-width: 768px) { .mc-btn { padding: 4px; } }
@keyframes splash { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.08); } }
`
test('stylesheet parses classes, states, breakpoints, keyframes and fonts and round-trips', () => {
  const parsed = parseStudioStylesheet(CSS, assets)
  assert.deepEqual(parsed.skipped, [])
  const btn = parsed.classes.find(item => item.name === 'mc-btn')!
  assert.equal(btn.styles['border-image'], 'url(/api/files/b.png) 3 fill !important')
  assert.deepEqual(btn.states, { hover: { color: '#ff5' } })
  assert.deepEqual(btn.responsive, { mobile: { padding: '4px' } })
  assert.deepEqual(parsed.classes.find(item => item.name === 'mc-link')!.states, { current: { color: '#5f5' } })
  assert.deepEqual(parsed.classes.find(item => item.name === 'mc-sub')!.states, { 'parent-hover': { display: 'flex' }, 'parent-expanded': { display: 'flex' } })
  assert.deepEqual(parsed.keyframes[0]!.frames, { '0%': { transform: 'scale(1)' }, '100%': { transform: 'scale(1)' }, '50%': { transform: 'scale(1.08)' } })
  assert.deepEqual(parsed.fonts, [{ family: 'Minecraft', assetId: 'font1' }])
  const text = formatStudioStylesheet({ theme: { tokens: {}, header: [], footer: [], ...parsed }, assets })
  const again = parseStudioStylesheet(text, assets)
  assert.deepEqual({ ...again, skipped: [] }, { ...parsed, skipped: [] })
})
test('strict mode rejects unmappable CSS; import mode skips and reports it', () => {
  for (const css of ['div.x { color: red }', '.a::before { content: "" }', '@import url(x.css);', '.a { color: red; behavior: url(x) }', '@media (min-width: 10px) { .a { color: red } }', '@font-face { font-family: X; src: url(/missing.woff2) }']) {
    assert.throws(() => parseStudioStylesheet(css, assets), css)
    assert.ok(parseStudioStylesheet(css, assets, { strict: false }).skipped.length > 0, css)
  }
})

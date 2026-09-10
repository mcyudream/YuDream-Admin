import assert from 'node:assert/strict'
import test from 'node:test'
import { orderedAddHtmlAssets } from './cms-canvas-tool-order'

test('applies add-html assets after their structure exists', () => {
  assert.deepEqual(
    orderedAddHtmlAssets({
      htmlContent: '<section class="yb-ai-card"></section>',
      cssContent: '.yb-ai-card { display: grid; }',
      jsContent: 'initCards();',
    }).map(item => item.kind),
    ['html', 'css', 'js'],
  )
})

test('skips empty add-html assets without changing order', () => {
  assert.deepEqual(
    orderedAddHtmlAssets({ htmlContent: '<section></section>', cssContent: '', jsContent: null }).map(item => item.kind),
    ['html'],
  )
})

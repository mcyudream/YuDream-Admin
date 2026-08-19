import assert from 'node:assert/strict'
import test from 'node:test'
import { executeCanvasTool } from './cms-canvas-agent-tools'

test('routes append_css through the canvas CSS merge callback', () => {
  const appended: string[] = []
  let changed = 0
  const result = executeCanvasTool({} as never, 'cms.canvas.append_css', {
    css: '.yb-ai-wiki-grid { min-width: 0; }',
  }, {
    appendCss: css => appended.push(css),
    appendDefault: () => [],
    onChanged: () => changed += 1,
  })

  assert.deepEqual(appended, ['.yb-ai-wiki-grid { min-width: 0; }'])
  assert.equal(changed, 1)
  assert.equal(result.message, 'CSS 规则已合并')
})

test('rejects an empty append_css request', () => {
  assert.throws(() => executeCanvasTool({} as never, 'cms.canvas.append_css', {}, {
    appendCss: () => {},
    appendDefault: () => [],
  }), /缺少 css/)
})

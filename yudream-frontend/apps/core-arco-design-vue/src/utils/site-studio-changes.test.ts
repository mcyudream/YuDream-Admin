import assert from 'node:assert/strict'
import test from 'node:test'
import { createStudioDocument, createStudioNode } from './site-studio'
import { describeStudioCommands } from './site-studio-changes'

test('change preview describes each command with resolved element names', () => {
  const doc = createStudioDocument('预览')
  const heading = createStudioNode('heading')
  heading.text = '旧标题'
  const section = createStudioNode('section')
  section.children = [heading]
  doc.pages[0]!.root = [section]
  const inserted = createStudioNode('section')
  inserted.children = [createStudioNode('text')]
  const lines = describeStudioCommands(doc, { type: 'batch', commands: [
    { type: 'set-text', nodeId: heading.id, text: '新的标题文字' },
    { type: 'set-style', nodeId: section.id, styles: { 'padding': '24px', 'margin': null }, breakpoint: 'mobile' },
    { type: 'insert', target: { pageId: doc.pages[0]!.id }, node: inserted },
    { type: 'remove', nodeId: 'missing-node-id-123' },
  ] })
  assert.deepEqual(lines.map(line => line.kind), ['text', 'style', 'insert', 'remove'])
  assert.equal(lines[0]!.label, '改写文本：heading「旧标题」')
  assert.equal(lines[0]!.detail, '旧标题 → 新的标题文字')
  assert.equal(lines[1]!.label, '调整样式（手机）：section · section')
  assert.equal(lines[1]!.detail, 'padding: 24px；移除 margin')
  assert.equal(lines[2]!.label, '插入 section · section 到 页面「首页」')
  assert.equal(lines[2]!.detail, '含 2 个元素')
  assert.equal(lines[3]!.label, '删除：元素 missing-')
  assert.equal(describeStudioCommands(doc, { type: 'set-text', nodeId: heading.id, text: 'x'.repeat(80) })[0]!.detail!.endsWith('…'), true)
})

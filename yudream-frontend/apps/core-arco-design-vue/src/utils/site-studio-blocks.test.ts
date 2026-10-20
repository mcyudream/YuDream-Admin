import assert from 'node:assert/strict'
import test from 'node:test'
import * as lucide from 'lucide'
import { applyStudioCommand, createStudioDocument, createStudioNode, validateStudioDocument } from './site-studio'
import { createStudioBlockRegistry, instantiateStudioBlock, resolveStudioInsertTarget, searchStudioBlocks, STUDIO_BLOCK_CATEGORIES, studioBlockRegistry } from './site-studio-blocks'

test('every core block instantiates valid nodes with fresh IDs in a known category and catalogued icon', () => {
  // morph: 图标来自 lucide 全集（kebab 名 ↔ 导出的 PascalCase 名）
  const lucideHas = (icon: string) => icon.replace(/^morph:/, '').split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('') in lucide
  const categories = new Set(STUDIO_BLOCK_CATEGORIES.map(item => item.key))
  for (const block of studioBlockRegistry.list()) {
    assert.ok(categories.has(block.category), `${block.name} 分类未知`)
    assert.ok(lucideHas(block.icon), `${block.name} 图标 ${block.icon} 不在 morph（lucide）图标集`)
    const first = instantiateStudioBlock(block)
    const second = instantiateStudioBlock(block)
    assert.notEqual(first[0]!.id, second[0]!.id, `${block.name} 两次插入共享 ID`)
    const doc = createStudioDocument('区块')
    doc.pages[0]!.root = first
    validateStudioDocument(doc)
  }
  assert.ok(studioBlockRegistry.list().length >= 20)
})

test('search ranks title prefix first and matches keywords', () => {
  const blocks = studioBlockRegistry.list()
  assert.equal(searchStudioBlocks(blocks, '标题')[0]!.name, 'core/heading')
  assert.ok(searchStudioBlocks(blocks, 'hero').some(block => block.name === 'core/cover'))
  assert.equal(searchStudioBlocks(blocks, '').length, blocks.length)
  assert.equal(searchStudioBlocks(blocks, '不存在的区块xyz').length, 0)
})

test('registry rejects invalid names and invalid block output fails closed', () => {
  const registry = createStudioBlockRegistry([])
  assert.throws(() => registry.register({ name: 'bad name', title: 'x', category: 'text', icon: 'morph:box', source: 'plugin', create: () => [] }))
  registry.register({ name: 'demo/script', title: '脚本', category: 'widgets', icon: 'morph:box', source: 'plugin', create: () => [{ ...createStudioNode('container'), tag: 'script' }] })
  assert.throws(() => instantiateStudioBlock(registry.get('demo/script')!), /不允许的 HTML 标签/)
})

test('insert target follows WordPress semantics: after selection, inside when allowed, else append', () => {
  const doc = createStudioDocument('位置')
  const section = createStudioNode('section')
  const text = createStudioNode('text')
  section.children = [text]
  doc.pages[0]!.root = [section, createStudioNode('section')]
  const pageId = doc.pages[0]!.id
  assert.deepEqual(resolveStudioInsertTarget(doc, { region: 'page', pageId }), { target: { pageId } })
  assert.deepEqual(resolveStudioInsertTarget(doc, { region: 'page', pageId, selectedId: section.id }), { target: { pageId }, index: 1 })
  assert.deepEqual(resolveStudioInsertTarget(doc, { region: 'page', pageId, selectedId: text.id }), { target: { parentId: section.id }, index: 1 })
  assert.deepEqual(resolveStudioInsertTarget(doc, { region: 'page', pageId, selectedId: section.id, placement: 'inside' }), { target: { parentId: section.id } })
  // 文本节点不能含子节点：“内部”回落为其后
  assert.deepEqual(resolveStudioInsertTarget(doc, { region: 'page', pageId, selectedId: text.id, placement: 'inside' }), { target: { parentId: section.id }, index: 1 })
  const block = instantiateStudioBlock(studioBlockRegistry.get('core/columns-2')!)
  const placed = resolveStudioInsertTarget(doc, { region: 'page', pageId, selectedId: section.id })
  const next = applyStudioCommand(doc, { type: 'batch', commands: block.map((node, offset) => ({ type: 'insert' as const, target: placed.target, node, index: (placed.index ?? 0) + offset })) })
  assert.equal(next.pages[0]!.root[1]!.type, 'columns')
})

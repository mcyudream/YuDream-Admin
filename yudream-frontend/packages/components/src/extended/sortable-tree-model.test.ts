/* eslint-disable test/no-import-node-test */
import type { YdSortableTreeNode } from './sortable-tree-model'
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { moveYdSortableTreeNode, resolveYdSortableTreeDrop } from './sortable-tree-model'

const source: YdSortableTreeNode[] = [
  { key: '90071992547409931', label: 'Alpha', children: [{ key: 'child-id', label: 'Child' }] },
  { key: 'node-b', label: 'Beta' },
  { key: 'node-c', label: 'Gamma' },
]

test('valid drop emits stable string IDs and source/target parent metadata; immutable helper reorders siblings', () => {
  const result = resolveYdSortableTreeDrop(source, 'node-c', '90071992547409931', -1)
  assert.equal(result.accepted, true)
  if (!result.accepted) {
    return
  }
  assert.equal(result.event.dragKey, 'node-c')
  assert.equal(result.event.sourceParentKey, null)
  assert.equal(result.event.targetParentKey, null)
  assert.equal(result.event.dropPosition, -1)
  const moved = moveYdSortableTreeNode(source, result.event)
  assert.deepEqual(moved.map(node => node.key), ['node-c', '90071992547409931', 'node-b'])
  assert.deepEqual(source.map(node => node.key), ['90071992547409931', 'node-b', 'node-c'])
  assert.equal(moved[1]?.key, '90071992547409931')
})

test('dropping inside reports the new parent and preserves nested branch data', () => {
  const result = resolveYdSortableTreeDrop(source, 'node-b', '90071992547409931', 0, { maxDepth: 8, trigger: 'keyboard' })
  assert.equal(result.accepted, true)
  if (!result.accepted) {
    return
  }
  assert.equal(result.event.targetParentKey, '90071992547409931')
  assert.equal(result.event.nextDepth, 2)
  assert.equal(result.event.trigger, 'keyboard')
  const moved = moveYdSortableTreeNode(source, result.event)
  assert.equal(moved[0]?.children?.at(-1)?.key, 'node-b')
})

test('disabled nodes, self-drop, descendant cycles and depth overflow reject before mutation', () => {
  const tree: YdSortableTreeNode[] = [
    { key: 'root', label: 'Root', children: [{ key: 'branch', label: 'Branch', children: [{ key: 'leaf', label: 'Leaf' }] }] },
    { key: 'locked', label: 'Locked', disabled: true },
  ]
  assert.deepEqual(resolveYdSortableTreeDrop(tree, 'root', 'leaf', 0), { accepted: false, reason: 'cycle' })
  assert.deepEqual(resolveYdSortableTreeDrop(tree, 'root', 'root', -1), { accepted: false, reason: 'self-drop' })
  assert.deepEqual(resolveYdSortableTreeDrop(tree, 'locked', 'branch', -1), { accepted: false, reason: 'disabled-node' })
  assert.deepEqual(resolveYdSortableTreeDrop(tree, 'leaf', 'branch', 0, { maxDepth: 2 }), { accepted: false, reason: 'max-depth' })
})

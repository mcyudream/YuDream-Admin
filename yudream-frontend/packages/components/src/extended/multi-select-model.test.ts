/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { equalYdMultiSelectValues, normalizeYdMultiSelectValue, removeYdMultiSelectValue, resolveYdMultiSelectInputAttrs, resolveYdMultiSelectInputState, sliceYdMultiSelectTags } from './multi-select-model'

const options = [
  { label: 'Alpha', value: 'a' },
  { label: 'Beta', value: 'b' },
  { label: 'Locked', value: 'locked', disabled: true },
]

test('保留隐藏和已禁用的既有 ID，只接纳已知可选 ID 并去重', () => {
  assert.deepEqual(normalizeYdMultiSelectValue(['b', 'a', 'b', 'locked', 'stranger'], options, ['legacy', 'locked']), ['legacy', 'locked', 'b', 'a'])
  assert.deepEqual(normalizeYdMultiSelectValue('a', options, ['legacy']), undefined)
})

test('删除标签只删除明确指定项并去重发出的选择', () => {
  assert.deepEqual(removeYdMultiSelectValue(['legacy', 'a', 'b', 'a'], 'b'), ['legacy', 'a'])
  assert.deepEqual(removeYdMultiSelectValue(['a', 'b'], 'missing'), ['a', 'b'])
  assert.equal(equalYdMultiSelectValues(['a', 'b'], ['a', 'b']), true)
  assert.equal(equalYdMultiSelectValues(['a', 'b'], ['b', 'a']), false)
})

test('表单属性到达真实输入，禁用搜索保持可聚焦只读输入', () => {
  assert.deepEqual(resolveYdMultiSelectInputAttrs({ id: 'role-ids', invalid: true, describedby: 'role-help', label: '角色' }), {
    'id': 'role-ids',
    'aria-invalid': 'true',
    'aria-describedby': 'role-help',
    'aria-label': '角色',
  })
  assert.deepEqual(resolveYdMultiSelectInputState(false, false), { readonly: true, disabled: false })
  assert.deepEqual(resolveYdMultiSelectInputState(true, true), { readonly: false, disabled: true })
})

test('maxTagCount 收起标签并准确报告隐藏数', () => {
  assert.deepEqual(sliceYdMultiSelectTags(['a', 'b', 'c'], 2), { visible: ['a', 'b'], hiddenCount: 1 })
  assert.deepEqual(sliceYdMultiSelectTags(['a', 'b'], 0), { visible: [], hiddenCount: 2 })
})

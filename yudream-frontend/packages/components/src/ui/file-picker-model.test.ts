/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { resetYdFileInput, resolveYdFilePickerTriggerAttrs, toYdFileArray } from './file-picker-model'

test('文件列表稳定转换为数组且保留浏览器给出的对象顺序', () => {
  const first = { name: 'first.txt' } as File
  const second = { name: 'second.txt' } as File
  assert.deepEqual(toYdFileArray({ 0: first, 1: second, length: 2 }), [first, second])
  assert.deepEqual(toYdFileArray(null), [])
})

test('重置原生 input 清空 value，允许再次选中同一个文件', () => {
  const input = { value: 'C:\\fakepath\\same.txt' }
  resetYdFileInput(input)
  assert.equal(input.value, '')
  resetYdFileInput(null)
})

test('文件触发器继承表单标识、无障碍名称与错误关联', () => {
  assert.deepEqual(resolveYdFilePickerTriggerAttrs({
    id: 'avatar-file',
    label: '选择头像',
    invalid: true,
    describedby: 'avatar-help',
  }), {
    'id': 'avatar-file',
    'aria-label': '选择头像',
    'aria-invalid': 'true',
    'aria-describedby': 'avatar-help',
  })
})

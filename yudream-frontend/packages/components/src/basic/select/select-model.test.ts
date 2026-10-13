/* eslint-disable test/no-import-node-test */
import type { SelectGroupOption, SelectModelValue, SelectOption } from './select-model'
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createSelectOptionKeyResolver, filterSelectOptions, flattenSelectOptions, mapSelectValueFromInternal, mapSelectValueToInternal, resolveSelectSelection } from './select-model'

const groups: (SelectOption | SelectGroupOption)[] = [
  { label: '不限', value: '' },
  { label: '数字零', value: 0 },
  { label: '文本零', value: '0' },
  {
    label: '系统角色',
    options: [
      { label: 'Administrator', value: 'admin' },
      { label: '普通成员', value: 'member' },
      { label: '禁用角色', value: 'locked', disabled: true },
    ],
  },
]

test('单选和多选没有值时不返回第一项，空数组保持为空', () => {
  assert.deepEqual(resolveSelectSelection(groups, undefined, false), [])
  assert.deepEqual(resolveSelectSelection(groups, undefined, true), [])
  assert.deepEqual(resolveSelectSelection(groups, null, false), [])
  assert.deepEqual(resolveSelectSelection(groups, null, true), [])
  assert.deepEqual(resolveSelectSelection(groups, [], true), [])
  assert.deepEqual(resolveSelectSelection(groups, 'missing', false), [])
})

test('合法 null、零和空字符串选项保持自身标签', () => {
  const nullOptions = [...groups, { label: '无归属', value: null }]
  assert.deepEqual(resolveSelectSelection(nullOptions, null, false), [{ label: '无归属', value: null }])
  assert.deepEqual(resolveSelectSelection(nullOptions, [null, '', 0], true).map(option => option.label), ['无归属', '不限', '数字零'])
  assert.deepEqual(resolveSelectSelection(groups, '', false), [{ label: '不限', value: '' }])
  assert.deepEqual(resolveSelectSelection(groups, 0, false), [{ label: '数字零', value: 0 }])
})

test('多选按模型顺序回显全部标签，数字和字符串保持不同', () => {
  assert.deepEqual(resolveSelectSelection(groups, ['member', 0, '0', 'admin'], true).map(option => option.label), ['普通成员', '数字零', '文本零', 'Administrator'])
  assert.deepEqual(resolveSelectSelection(groups, '0', false), [{ label: '文本零', value: '0' }])
})

test('展开分组选项不修改候选结构，禁用已选项仍可回显', () => {
  const before = structuredClone(groups)
  const flat = flattenSelectOptions(groups)
  assert.equal(flat.length, 6)
  assert.deepEqual(resolveSelectSelection(groups, ['locked'], true), [{ label: '禁用角色', value: 'locked', disabled: true }])
  assert.deepEqual(groups, before)
})

test('未知多选 primitive 回显真实值，未知 null 不新增可读标签，也不改模型', () => {
  const model = ['legacy-id', 19, 22n, null, 'admin'] satisfies SelectModelValue
  const before = [...model]
  assert.deepEqual(resolveSelectSelection(groups, model, true), [
    { label: 'legacy-id', value: 'legacy-id' },
    { label: '19', value: 19 },
    { label: '22', value: 22n },
    { label: 'Administrator', value: 'admin' },
  ])
  assert.deepEqual(model, before)
})

test('长 ID 始终为字符串，查询和映射不发生数值转换', () => {
  const id = '9223372036854775807'
  const options = [{ label: '长标识角色', value: id }]
  const model = [id]
  assert.equal(resolveSelectSelection(options, model, true)[0]?.value, id)
  assert.deepEqual(mapSelectValueFromInternal(mapSelectValueToInternal(model)), model)
  assert.deepEqual(filterSelectOptions(options, '9223372036854775807'), options)
  assert.equal(typeof model[0], 'string')
})

test('已知对象值按深等比较，未知对象优先可读 label 否则显示已选项', () => {
  const options: SelectOption[] = [{ label: '稳定角色', value: { id: 'role-a', detail: { level: 2 } } }]
  const selected = { detail: { level: 2 }, id: 'role-a' }
  assert.deepEqual(resolveSelectSelection(options, selected, false), options)
  assert.deepEqual(resolveSelectSelection(options, [{ id: 'legacy', label: '旧授权' }, { id: 'unlabelled' }], true).map(option => option.label), ['旧授权', '已选项'])
  assert.deepEqual(resolveSelectSelection(options, { label: '   ' }, false).map(option => option.label), ['已选项'])
})

test('空字符串哨兵对单值和数组逐项往返，保留其他值及对象引用', () => {
  const object = { id: 'object-value' }
  const values = ['', 0, '0', null, 3n, object]
  const internal = mapSelectValueToInternal(values)
  assert.notEqual(internal[0], '')
  assert.deepEqual(mapSelectValueFromInternal(internal), values)
  assert.equal(internal[5], object)
  assert.deepEqual(values, ['', 0, '0', null, 3n, object])
  assert.equal(mapSelectValueFromInternal(mapSelectValueToInternal('')), '')
  assert.equal(mapSelectValueToInternal(undefined), undefined)
  assert.equal(mapSelectValueFromInternal(undefined), undefined)
})

test('搜索大小写不敏感并查询标签和值，匹配组名保留整个组', () => {
  assert.deepEqual(filterSelectOptions(groups, '  ADMIN  '), [{ label: '系统角色', options: [{ label: 'Administrator', value: 'admin' }] }])
  assert.deepEqual(filterSelectOptions(groups, 'MEMBER'), [{ label: '系统角色', options: [{ label: '普通成员', value: 'member' }] }])
  assert.deepEqual(filterSelectOptions(groups, '系统角色'), [groups[3]])
  assert.deepEqual(filterSelectOptions(groups, '数字零'), [groups[1]])
  assert.deepEqual(filterSelectOptions(groups, '   '), groups)
})

test('搜索没有结果不清理选中数组，复原搜索后标签仍然完整', () => {
  const model = ['admin', 'member', 'legacy-id', 'locked']
  const selectedBefore = resolveSelectSelection(groups, model, true)
  assert.deepEqual(filterSelectOptions(groups, '没有匹配结果'), [])
  assert.deepEqual(model, ['admin', 'member', 'legacy-id', 'locked'])
  assert.deepEqual(resolveSelectSelection(groups, model, true), selectedBefore)
  assert.deepEqual(resolveSelectSelection(filterSelectOptions(groups, ''), model, true), selectedBefore)
})

test('搜索对象值时支持可读字段和 bigint，循环对象不会导致筛选异常', () => {
  const circular: Record<string, unknown> = { label: '循环选项' }
  circular.self = circular
  const options: SelectOption[] = [
    { label: '对象候选', value: { id: 'Object-Alpha', revision: 13n } },
    { label: '另一候选', value: circular },
  ]
  assert.deepEqual(filterSelectOptions(options, 'object-alpha'), [options[0]])
  assert.deepEqual(filterSelectOptions(options, '13'), [options[0]])
  assert.deepEqual(filterSelectOptions(options, '循环选项'), [options[1]])
})

test('重复模型值只回显一次，深等去重也不改原数组', () => {
  const model = ['admin', 'admin', { label: '旧项' }, { label: '旧项' }]
  assert.deepEqual(resolveSelectSelection(groups, model, true).map(option => option.label), ['Administrator', '旧项'])
  assert.equal(model.length, 4)
})

test('筛选后的首项按真实值保持不同 key，不复用另一个选项的 Reka 实例', () => {
  const options: SelectOption[] = [
    { label: 'Alpha', value: 'alpha' },
    { label: 'Beta', value: 'beta' },
  ]
  const key = createSelectOptionKeyResolver()
  const firstBefore = key(options[0]!)
  const betaBefore = key(options[1]!)
  const filtered = filterSelectOptions(options, 'beta') as SelectOption[]
  assert.equal(key(filtered[0]!), betaBefore)
  assert.notEqual(key(filtered[0]!), firstBefore)
})

test('primitive key 区分字符串、数值、bigint、null 与空字符串', () => {
  const key = createSelectOptionKeyResolver()
  const values = [0, '0', 0n, null, '', 'null', -0]
  const keys = values.map(value => key({ label: String(value), value }))
  assert.equal(new Set(keys).size, values.length)
  assert.equal(key({ label: '更新后的标签', value: '0' }), keys[1])
})

test('对象 key 使用引用身份，支持 bigint 与循环引用而不序列化', () => {
  const key = createSelectOptionKeyResolver()
  const first: Record<string, unknown> = { id: 1n }
  first.self = first
  const second = { id: 1n }
  const firstKey = key({ label: '对象一', value: first })
  assert.equal(key({ label: '对象一的新标签', value: first }), firstKey)
  assert.notEqual(key({ label: '对象二', value: second }), firstKey)
})

test('分组 key 保留原组身份，重名及共享子项的不同组仍然不同', () => {
  const shared = { label: '共享候选', value: 'shared' }
  const first: SelectGroupOption = { label: '相同组名', options: [shared, { label: 'Alpha', value: 'alpha' }] }
  const second: SelectGroupOption = { label: '相同组名', options: [shared, { label: 'Beta', value: 'beta' }] }
  const key = createSelectOptionKeyResolver()
  const firstKey = key(first)
  const secondKey = key(second)
  const filteredFirst = filterSelectOptions([first], '共享候选')[0] as SelectGroupOption
  const filteredSecond = filterSelectOptions([second], '共享候选')[0] as SelectGroupOption
  assert.notEqual(filteredFirst, first)
  assert.notEqual(filteredSecond, second)
  assert.notEqual(firstKey, secondKey)
  assert.equal(key(first), firstKey)
  assert.equal(key(second), secondKey)
  assert.equal(key(filteredFirst.options[0]!), key(shared))
  assert.equal(key(filteredSecond.options[0]!), key(shared))
})

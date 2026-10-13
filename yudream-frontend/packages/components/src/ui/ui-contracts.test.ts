/* eslint-disable test/no-import-node-test */
import type { VNode } from 'vue'
import type { YdFormFieldBindings } from './types'
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { Fragment, h } from 'vue'
import { bindYdFormControls, isYdFormLabelFallbackTarget } from './form-controls'
import { getYdRatePointerValue, normalizeYdRate } from './rate'

test('评分精度、边界和异常输入保持有效模型', () => {
  assert.equal(normalizeYdRate(2.3, 5, true), 2.5)
  assert.equal(normalizeYdRate(2.3, 5, false), 2)
  assert.equal(normalizeYdRate(10, 5, true), 5)
  assert.equal(normalizeYdRate(-1, 5, true), 0)
  assert.equal(normalizeYdRate(Number.NaN, 5, true), 0)
})
test('半星指针位置与整星点击区分准确', () => {
  assert.equal(getYdRatePointerValue(2, 103, 100, 24, true), 2.5)
  assert.equal(getYdRatePointerValue(2, 120, 100, 24, true), 3)
  assert.equal(getYdRatePointerValue(2, 103, 100, 24, false), 3)
})
test('字段桥继承禁用和错误，保留调用者模型处理函数', () => {
  const calls: string[] = []
  const bindings = {
    'id': 'field-control',
    'disabled': true,
    'aria-invalid': true,
    'aria-describedby': 'field-help',
    'onUpdate:modelValue': () => calls.push('validate'),
    'onInput': () => {},
    'onChange': () => {},
    'onBlur': () => {},
    'onFocus': () => {},
  } satisfies YdFormFieldBindings
  const node = h({ name: 'YdInput', render: () => null }, { 'modelValue': 'existing', 'onUpdate:modelValue': () => calls.push('model') })
  const [result] = bindYdFormControls([node], bindings)
  assert.equal(result.props?.modelValue, 'existing')
  assert.equal(result.props?.disabled, true)
  assert.equal(result.props?.['aria-invalid'], true)
  assert.equal(result.props?.id, 'field-control')
  result.props?.['onUpdate:modelValue'].forEach((fn: () => void) => fn())
  assert.deepEqual(calls, ['model', 'validate'])
  assert.equal(node.props?.disabled, undefined)
})
test('字段桥穿过 Fragment，但保留布局元素和显式控件禁用状态', () => {
  const bindings = { 'id': 'field-control', 'disabled': true, 'aria-invalid': false, 'aria-describedby': 'help', 'onUpdate:modelValue': () => {}, 'onInput': () => {}, 'onChange': () => {}, 'onBlur': () => {}, 'onFocus': () => {} } satisfies YdFormFieldBindings
  const label = h('span', '说明')
  const nodes = [h(Fragment, [label, h({ name: 'YdInput', render: () => null }, { disabled: false })])]
  const [result] = bindYdFormControls(nodes, bindings)
  const children = result.children as VNode[]
  assert.equal(children[0], label)
  assert.equal(children[1].props?.disabled, false)
  assert.equal(children[1].props?.id, 'field-control')
})
test('字段桥识别 script setup 的 __name 并只将标签回退焦点用于复合可编辑入口', () => {
  const bindings = { 'id': 'field-control', 'disabled': false, 'aria-invalid': true, 'aria-describedby': 'field-help', 'onUpdate:modelValue': () => {}, 'onInput': () => {}, 'onChange': () => {}, 'onBlur': () => {}, 'onFocus': () => {} } satisfies YdFormFieldBindings
  const node = h({ __name: 'YdCodeEditor', setup: () => () => null }, { 'aria-describedby': 'caller-help' })
  const [result] = bindYdFormControls([node], bindings)
  assert.equal(result.props?.id, 'field-control')
  assert.equal(result.props?.['aria-invalid'], true)
  assert.equal(result.props?.['aria-describedby'], 'field-help')
  assert.equal(isYdFormLabelFallbackTarget({ tagName: 'DIV', isContentEditable: true, hasAttribute: () => false }), true)
  assert.equal(isYdFormLabelFallbackTarget({ tagName: 'INPUT', isContentEditable: false, hasAttribute: () => true }), false)
  assert.equal(isYdFormLabelFallbackTarget({ tagName: 'DIV', isContentEditable: false, hasAttribute: name => name === 'tabindex' }), true)
  assert.equal(isYdFormLabelFallbackTarget({ tagName: 'DIV', isContentEditable: false, hasAttribute: () => true, getAttribute: name => name === 'aria-disabled' ? 'true' : null }), false)
  assert.equal(isYdFormLabelFallbackTarget({ tagName: 'BUTTON', isContentEditable: false, hasAttribute: () => true }), false)
})

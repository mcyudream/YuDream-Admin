/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { getYdComboboxCreateValue } from './combobox-create-model'

test('create candidate is trimmed, unique against option values and labels, and remains a string ID', () => {
  const options = [{ value: 'provider-a', label: 'Provider A' }, { value: 42, label: 'Forty two' }]
  assert.equal(getYdComboboxCreateValue('  custom-provider  ', options), 'custom-provider')
  assert.equal(getYdComboboxCreateValue(' provider-a ', options), undefined)
  assert.equal(getYdComboboxCreateValue(' PROVIDER A ', options), undefined)
  assert.equal(getYdComboboxCreateValue('42', options), undefined)
  assert.equal(getYdComboboxCreateValue('  ', options), undefined)
  assert.equal(typeof getYdComboboxCreateValue('9223372036854775807', options), 'string')
})

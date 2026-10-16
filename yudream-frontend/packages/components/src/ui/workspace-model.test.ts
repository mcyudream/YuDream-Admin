/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { nextWorkspaceTabKey, stepWorkspaceTabKey } from './workspace-model'

const tabs = [{ key: 'a' }, { key: 'b' }, { key: 'c' }]

test('关闭非当前标签时保持当前激活不变', () => {
  assert.equal(nextWorkspaceTabKey(tabs, 'a', 'c'), 'a')
  assert.equal(nextWorkspaceTabKey(tabs, 'b', 'a'), 'b')
})

test('关闭当前标签时优先激活右邻，末尾回退左邻', () => {
  assert.equal(nextWorkspaceTabKey(tabs, 'a', 'a'), 'b')
  assert.equal(nextWorkspaceTabKey(tabs, 'b', 'b'), 'c')
  // 关闭末尾：没有右邻，回退左邻
  assert.equal(nextWorkspaceTabKey(tabs, 'c', 'c'), 'b')
})

test('关闭最后一个标签返回空串，交给调用方展示空态', () => {
  assert.equal(nextWorkspaceTabKey([{ key: 'only' }], 'only', 'only'), '')
})

test('关闭未知标签不改变当前激活', () => {
  assert.equal(nextWorkspaceTabKey(tabs, 'b', 'missing'), 'b')
})

test('方向键步进不环绕，越界停在边界', () => {
  assert.equal(stepWorkspaceTabKey(tabs, 'a', 1), 'b')
  assert.equal(stepWorkspaceTabKey(tabs, 'b', 1), 'c')
  assert.equal(stepWorkspaceTabKey(tabs, 'c', 1), 'c')
  assert.equal(stepWorkspaceTabKey(tabs, 'a', -1), 'a')
  assert.equal(stepWorkspaceTabKey(tabs, 'c', -1), 'b')
})

test('无激活标签时落在第一个，空集合返回空串', () => {
  assert.equal(stepWorkspaceTabKey(tabs, '', 1), 'a')
  assert.equal(stepWorkspaceTabKey([], 'a', 1), '')
  assert.equal(stepWorkspaceTabKey([], '', -1), '')
})

import assert from 'node:assert/strict'
// 根包无 vitest 依赖，本地测试统一用 node:test 运行
// eslint-disable-next-line test/no-import-node-test
import { test } from 'node:test'
import { templateImportFeedbackKind } from './template-import-feedback'

test('does not report partial failure as success', () => {
  assert.equal(templateImportFeedbackKind({ total: 3, success: 2, failed: 1, errors: ['菜单第 4 行：编码冲突'] }), 'error')
})

test('reports warnings only after every row succeeded', () => {
  assert.equal(templateImportFeedbackKind({ total: 1, success: 1, failed: 0, errors: [], warnings: ['初始化邮件已进入队列'] }), 'warning')
})

test('reports a clean import as success', () => {
  assert.equal(templateImportFeedbackKind({ total: 1, success: 1, failed: 0, errors: [] }), 'success')
})

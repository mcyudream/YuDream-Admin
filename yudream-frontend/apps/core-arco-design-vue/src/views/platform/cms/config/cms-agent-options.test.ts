import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test -- this workspace runs lightweight TS tests with Node.
import { test } from 'node:test'
import { resolveCmsAgentCode, toCmsAgentOptions } from './cms-agent-options'

test('converts published Agent applications into CMS selector options', () => {
  assert.deepEqual(toCmsAgentOptions([
    { code: 'custom-writer', name: '内容助手', status: 'PUBLISHED' },
    { code: 'draft-agent', name: '草稿 Agent', status: 'DRAFT' },
  ]), [
    { label: '内容助手', value: 'custom-writer' },
  ])
})

test('prefers the built-in CMS Agent when no valid selection exists', () => {
  const options = [
    { label: '自定义助手', value: 'custom-writer' },
    { label: 'CMS 页面构建', value: 'builtin-cms-builder' },
  ]

  assert.equal(resolveCmsAgentCode(options, ''), 'builtin-cms-builder')
  assert.equal(resolveCmsAgentCode(options, 'custom-writer'), 'custom-writer')
})

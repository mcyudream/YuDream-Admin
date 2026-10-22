import assert from 'node:assert/strict'
import test from 'node:test'
import { aiApprovalItems } from './ai-approval.ts'

test('workspace writes and deletes become reviewable approval cards', () => {
  const [write, remove, other] = aiApprovalItems([
    { requestId: 'a', kind: 'confirmation', toolName: 'workspace.write', arguments: { path: 'notes/plan.md', content: '# 计划\n1. 第一步' } },
    { requestId: 'b', kind: 'confirmation', toolName: 'workspace.delete', arguments: { path: 'old.md' } },
    { requestId: 'c', kind: 'confirmation', toolName: 'web.fetch', arguments: { url: 'https://example.com' } },
    { requestId: 'd', kind: 'input', toolName: 'ask' },
  ])
  assert.equal(write!.title, '写入工作区文件：notes/plan.md')
  assert.match(write!.description!, /2 行/)
  assert.equal(write!.command, '# 计划\n1. 第一步')
  assert.equal(remove!.title, '删除工作区文件：old.md')
  assert.match(other!.description!, /example\.com/)
  assert.equal(aiApprovalItems(undefined).length, 0)
})

test('long write content is previewed, not dumped', () => {
  const [write] = aiApprovalItems([{ requestId: 'a', kind: 'confirmation', toolName: 'workspace.write', arguments: { path: 'big.txt', content: 'x'.repeat(2000) } }])
  assert.match(write!.command!, /其余 1400 字/)
})

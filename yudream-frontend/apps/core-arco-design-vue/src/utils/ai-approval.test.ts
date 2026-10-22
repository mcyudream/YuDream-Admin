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

test('move and run requests explain what will happen', () => {
  const [move, run, entry] = aiApprovalItems([
    { requestId: 'm', kind: 'confirmation', toolName: 'workspace.move', arguments: { from: 'a.md', to: 'docs/a.md' } },
    { requestId: 'r', kind: 'confirmation', toolName: 'workspace.run', arguments: { language: 'python', code: 'print(1)', inputs: ['data'] } },
    { requestId: 'e', kind: 'confirmation', toolName: 'workspace.run', arguments: { language: 'node', entry: 'src/app.js', outputDir: 'out' } },
  ])
  assert.equal(move!.title, '移动工作区文件：a.md → docs/a.md')
  assert.equal(run!.title, '运行 Python 代码')
  assert.equal(run!.command, 'print(1)')
  assert.match(run!.description!, /带入 data/)
  assert.match(run!.description!, /tmp\/runs/)
  assert.equal(entry!.title, '运行 Node 代码')
  assert.match(entry!.description!, /入口 src\/app\.js · 产物写入 out/)
  assert.equal(entry!.command, undefined)
})

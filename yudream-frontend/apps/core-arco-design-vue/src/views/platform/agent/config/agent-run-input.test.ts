import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test -- this workspace runs lightweight TS tests with Node.
import { test } from 'node:test'
import { agentRunCapabilities, buildAgentRunInput } from './agent-run-input'

test('扫描全部模型节点决定图片和文档能力', () => {
  const workflow = JSON.stringify({
    nodes: [
      { data: { kind: 'llm', vision: false, acceptFiles: false } },
      { data: { kind: 'llm', vision: true, acceptFiles: true } },
      { data: { kind: 'document' } },
    ],
  })

  assert.deepEqual(agentRunCapabilities(workflow), { allowImage: true, allowFiles: true })
})

test('视觉理解节点在选择 Vision 模型后允许正式运行入口上传图片', () => {
  const workflow = JSON.stringify({
    nodes: [
      { data: { kind: 'vision', vision: true } },
    ],
  })

  assert.deepEqual(agentRunCapabilities(workflow), { allowImage: true, allowFiles: false })
})

test('仅把小型文本附件加入模型输入', () => {
  const input = buildAgentRunInput('总结附件', [
    { name: 'small.txt', type: 'text/plain', size: 12, text: 'hello' },
    { name: 'large.txt', type: 'text/plain', size: 300 * 1024, text: 'too large' },
  ])

  assert.match(input, /small\.txt/)
  assert.match(input, /hello/)
  assert.doesNotMatch(input, /too large/)
})

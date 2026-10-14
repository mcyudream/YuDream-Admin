import assert from 'node:assert/strict'
import test from 'node:test'
import { readCompletedAiJsonField, readCompletedAiJsonItems } from './ai-structured-stream.ts'

test('structured deltas expose only fields and array items actually completed by the model', () => {
  const partial = '{"topic":"真实研究","rationale":"原因待生成'
  assert.equal(readCompletedAiJsonField(partial, 'topic'), '真实研究')
  assert.equal(readCompletedAiJsonField(partial, 'rationale'), undefined)
  const queries = `${partial}完成","queries":["第一条","第二`
  assert.deepEqual(readCompletedAiJsonItems(queries, 'queries'), ['第一条'])
})

test('complete issue objects appear before the whole report closes without repairing truncated JSON', () => {
  const source = '{"summary":"结构分析","issues":[{"title":"断链","description":"引用\\n失效"},{"title":"下一个'
  assert.deepEqual(readCompletedAiJsonItems(source, 'issues'), [{ title: '断链', description: '引用\n失效' }])
})

test('property scanner respects nesting and escaped quotes in generated prose', () => {
  const source = '{"metadata":{"topic":"nested"},"rationale":"contains \\"topic\\": text","topic":"root"}'
  assert.equal(readCompletedAiJsonField(source, 'topic'), 'root')
  assert.equal(readCompletedAiJsonField('{"topic":"\\u4f', 'topic'), undefined)
})

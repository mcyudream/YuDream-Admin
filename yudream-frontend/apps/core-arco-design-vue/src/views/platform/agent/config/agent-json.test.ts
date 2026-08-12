import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test -- this workspace runs lightweight TS tests with Node.
import { test } from 'node:test'
import { defaultAgentToolInputSchema, defaultAgentToolOutputExample, formatJson, isJsonObject } from './agent-json'

test('Agent 工具 JSON 默认值都是格式化的对象', () => {
  assert.equal(isJsonObject(defaultAgentToolInputSchema), true)
  assert.equal(isJsonObject(defaultAgentToolOutputExample), true)
  assert.match(defaultAgentToolInputSchema, /\n  "properties"/)
})

test('JSON object 校验拒绝数组、空值与无效 JSON', () => {
  assert.equal(isJsonObject('{"name":"tool"}'), true)
  assert.equal(isJsonObject('[]'), false)
  assert.equal(isJsonObject('null'), false)
  assert.equal(isJsonObject('{'), false)
})

test('格式化 JSON 并保留无效输入', () => {
  assert.equal(formatJson('{"name":"tool","enabled":true}'), '{\n  "name": "tool",\n  "enabled": true\n}')
  assert.equal(formatJson('{'), '{')
})

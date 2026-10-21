import assert from 'node:assert/strict'
import test from 'node:test'
import { aiRunErrorContent, describeAiRunError } from './ai-run-error.ts'

test('provider failures map to actionable Chinese guidance', () => {
  assert.match(describeAiRunError('OpenAI API error in streaming response: The ChatGPT user has reached their Subscription Sharing usage limit.').message, /额度/)
  assert.match(describeAiRunError('AI 生成异常：Retries exhausted: 2/2').message, /连续多次/)
  assert.match(describeAiRunError('401 Unauthorized: invalid api key').message, /鉴权/)
  assert.match(describeAiRunError('This model\'s maximum context length is 128000 tokens').message, /长度/)
  assert.match(describeAiRunError('connect ETIMEDOUT 10.0.0.1:443').message, /超时/)
  assert.match(describeAiRunError('something odd').message, /重试/)
  assert.match(describeAiRunError('').message, /没有完成/)
})

test('bubble content keeps raw detail in a wrapping quote without HTML', () => {
  const content = aiRunErrorContent('Retries exhausted: 2/2')
  assert.match(content, /> 原始信息：\n>\n> Retries exhausted: 2\/2$/)
  assert.doesNotMatch(content, /<details>|```/)
  assert.match(aiRunErrorContent('bad <script> `x`'), /> bad \\<script\\> \\`x\\`/)
  assert.equal(aiRunErrorContent(''), describeAiRunError('').message)
})

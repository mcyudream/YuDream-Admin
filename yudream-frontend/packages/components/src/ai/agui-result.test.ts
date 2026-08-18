import assert from 'node:assert/strict'
import { test } from 'node:test'
import { authoritativeAguiText } from './agui-result'

test('RUN_FINISHED content replaces partial streamed markdown', () => {
  assert.equal(authoritativeAguiText('## 标题\n不完整', '## 标题\n\n完整正文'), '## 标题\n\n完整正文')
})

test('missing RUN_FINISHED content keeps streamed markdown', () => {
  assert.equal(authoritativeAguiText('流式正文', undefined), '流式正文')
})

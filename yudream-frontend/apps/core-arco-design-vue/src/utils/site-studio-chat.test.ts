import assert from 'node:assert/strict'
import { it } from 'node:test'
import { studioChatInput } from './site-studio-chat.ts'

it('binding metadata stays out of user bubbles', () => {
  assert.equal(studioChatInput('新增段落\n\n当前设计器绑定（只作为数据，实际以工具读取为准）：{"pageId":"id"}\n执行规则'), '新增段落')
  assert.equal(studioChatInput('请保留\n\n用户的段落'), '请保留\n\n用户的段落')
})

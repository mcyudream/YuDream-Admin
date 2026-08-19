import assert from 'node:assert/strict'
import test from 'node:test'
import { sanitizeCmsVisibleContent } from './cms-ai-chat-history'

test('removes internal tool and business context from persisted visible answers', () => {
  const content = `修改已经完成。\n\n[工具上下文]\ncms.canvas.get_outline: 已读取 CSS {"resource":"css"}\n\n[业务上下文]\n{"cursor":1}`
  assert.equal(sanitizeCmsVisibleContent(content), '修改已经完成。')
})

test('keeps ordinary answers and bracketed user content', () => {
  const content = '可以在正文里使用 [工具] 作为普通术语。'
  assert.equal(sanitizeCmsVisibleContent(content), content)
})

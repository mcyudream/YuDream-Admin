import assert from 'node:assert/strict'
import { test } from 'node:test'
import { matchesAiSessionScope } from './ai-session-scope.ts'

test('切换到 Agent 或知识库不能继续使用普通会话的后端分派', () => {
  assert.equal(matchesAiSessionScope({ scopeType: 'GENERAL' }, { scopeType: 'AGENT', agentCode: 'report' }), false)
  assert.equal(matchesAiSessionScope({ scopeType: 'GENERAL' }, { scopeType: 'WIKI', spaceSlug: '__all__' }), false)
  assert.equal(matchesAiSessionScope({ scopeType: 'AGENT', agentCode: 'report' }, { scopeType: 'GENERAL' }), false)
})

test('更换应用、知识库或设计器绑定均需隔离原会话', () => {
  assert.equal(matchesAiSessionScope({ scopeType: 'AGENT', agentCode: 'report' }, { scopeType: 'AGENT', agentCode: 'review' }), false)
  assert.equal(matchesAiSessionScope({ scopeType: 'WIKI', spaceSlug: 'team' }, { scopeType: 'WIKI', spaceSlug: '__all__' }), false)
  assert.equal(matchesAiSessionScope({ scopeType: 'CONSOLE', agentCode: 'cms-studio', spaceSlug: 'site-a' }, { scopeType: 'CONSOLE', agentCode: 'cms-studio', spaceSlug: 'site-b' }), false)
  assert.equal(matchesAiSessionScope({ scopeType: 'CONSOLE', agentCode: 'cms-canvas' }, { scopeType: 'CONSOLE', agentCode: 'cms-studio' }), false)
})

test('相同范围复用会话，缺省普通范围与空知识库兼容旧数据', () => {
  assert.equal(matchesAiSessionScope({ scopeType: 'AGENT', agentCode: 'report' }, { scopeType: 'AGENT', agentCode: 'report' }), true)
  assert.equal(matchesAiSessionScope({ scopeType: 'WIKI', spaceSlug: 'team' }, { scopeType: 'WIKI', spaceSlug: 'team' }), true)
  assert.equal(matchesAiSessionScope({ spaceSlug: '' }, { scopeType: 'GENERAL' }), true)
  assert.equal(matchesAiSessionScope({ scopeType: 'GENERAL' }, { scopeType: 'GENERAL', spaceSlug: 'team' }), false)
})

test('全局助手未指定范围时延续当前会话，首次发送需要创建会话', () => {
  assert.equal(matchesAiSessionScope({ scopeType: 'WIKI', spaceSlug: 'team' }, {}), true)
  assert.equal(matchesAiSessionScope(undefined, { scopeType: 'AGENT', agentCode: 'report' }), false)
})

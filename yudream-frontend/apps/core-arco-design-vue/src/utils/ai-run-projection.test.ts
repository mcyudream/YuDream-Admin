import assert from 'node:assert/strict'
import test from 'node:test'
import { projectAiRunActivities, projectAiRunActivity, projectAiRunTool } from './ai-run-projection.ts'

test('completed backend tool DTOs stay completed after snapshot restoration', () => {
  assert.equal(projectAiRunTool({ toolName: 'ui.navigate', action: 'navigate', message: '已打开页面', payload: { routeName: 'users' } })?.status, 'complete')
  assert.equal(projectAiRunTool({ toolName: 'wiki.search' }, 'start')?.status, 'executing')
  assert.equal(projectAiRunTool({ toolName: 'wiki.search', status: 'error' }, 'done')?.status, 'error')
  assert.equal(projectAiRunTool({ toolName: 42 }), undefined)
})

test('Agent node progress preserves canvas identity and terminal state after snapshot normalization', () => {
  const activity = projectAiRunActivity({ nodeId: 'node-1', nodeKind: 'llm', nodeTitle: '分析材料', status: 'COMPLETED', message: '节点已完成' }, 'run-1')
  assert.equal(activity?.activityType, 'agent-node')
  assert.equal(activity?.phase, 'node-1')
  assert.equal(activity?.status, 'complete')
  assert.equal(activity?.nodeStatus, 'COMPLETED')
  assert.equal(activity?.title, '分析材料')
  assert.equal(projectAiRunActivity(activity, 'run-1')?.nodeStatus, 'COMPLETED')
  const replay = projectAiRunActivities([{ ...activity, status: 'running', nodeStatus: 'RUNNING' }, activity], 'run-1')
  assert.equal(replay.length, 1)
  assert.equal(replay[0].nodeStatus, 'COMPLETED')
})

test('accepted progress frames and earlier retrieval metadata survive run snapshots', () => {
  assert.equal(projectAiRunActivity({ title: '请求已接受', status: 'running' }, 'run-1')?.activityType, 'ai-progress')
  const activity = projectAiRunActivity({ activityType: 'wiki-retrieval', phase: 'retrieve', hits: [{ title: '文档', nodeId: '9007199254740993', excerpt: '公开内容' }, { title: 42 }] }, 'run-1')
  assert.equal(activity?.hits?.length, 1)
  assert.equal(activity?.hits?.[0].nodeId, '9007199254740993')
  assert.equal(activity?.messageId, 'run-1')
})

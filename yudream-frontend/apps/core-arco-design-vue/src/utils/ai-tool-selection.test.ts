import type { ChatToolOptions } from './ai-tool-selection.ts'
import assert from 'node:assert/strict'
import test from 'node:test'
import { emptyToolSelection, parseToolSelection, skillItems, toggleSkill, toolPayload } from './ai-tool-selection.ts'

const options: ChatToolOptions = {
  modules: [{ code: 'web', name: '网页访问', description: '', icon: '', source: 'system', tools: ['网页抓取'] }, { code: 'mcp:stub', name: '本地桩', description: '', icon: '', source: 'mcp', tools: [] }],
  skills: [{ name: 'translator', description: '翻译' }, { name: 'summarizer', description: '摘要' }],
}

test('selection parses defensively and defaults to no tools, all skills', () => {
  assert.deepEqual(parseToolSelection(null), emptyToolSelection)
  assert.deepEqual(parseToolSelection({ modules: ['web', 'web', 3], skills: 'x' }), { modules: ['web'], skills: null })
  assert.deepEqual(parseToolSelection({ modules: [], skills: [] }), { modules: [], skills: [] })
})

test('skill toggles narrow the list and collapse back to unlimited', () => {
  const all = options.skills.map(skill => skill.name)
  const narrowed = toggleSkill(emptyToolSelection, all, 'translator', false)
  assert.deepEqual(narrowed.skills, ['summarizer'])
  assert.deepEqual(skillItems(options, narrowed).map(item => item.enabled), [false, true])
  assert.equal(toggleSkill(narrowed, all, 'translator', true).skills, null)
  assert.deepEqual(toggleSkill(narrowed, all, 'summarizer', false).skills, [])
})

test('payload keeps only available modules and omits unlimited skills', () => {
  assert.deepEqual(toolPayload({ modules: ['web', 'gone'], skills: null }, options), { toolModules: ['web'] })
  assert.deepEqual(toolPayload({ modules: [], skills: ['summarizer'] }, options), { toolModules: [], skillCodes: ['summarizer'] })
})

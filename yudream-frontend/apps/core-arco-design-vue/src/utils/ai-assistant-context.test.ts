import assert from 'node:assert/strict'
import test from 'node:test'
import { defaultAiAssistantPreferences, frontendContextForPreferences, isSafeAiRoute, matchesAiAssistantShortcut, parseAiAssistantPreferences, parseAiFrontendToolRequest } from './ai-assistant-context.ts'

test('assistant context and frontend tools require explicit preference opt-in', () => {
  assert.deepEqual(parseAiAssistantPreferences(undefined), defaultAiAssistantPreferences)
  assert.equal(frontendContextForPreferences({ route: { name: 'users', title: '用户管理', path: '/system/user' }, routes: [], actions: [] }, defaultAiAssistantPreferences), undefined)
  assert.deepEqual(frontendContextForPreferences({ routes: [{ name: 'users', title: '用户管理', path: '/system/user' }] }, { ...defaultAiAssistantPreferences, navigationTools: true }), { routes: [{ name: 'users', title: '用户管理', path: '/system/user' }], navigationToolsEnabled: true, highlightToolsEnabled: false, tourToolsEnabled: false })
  assert.deepEqual(frontendContextForPreferences({ route: { name: 'theme-config', title: '主题配置' } }, { ...defaultAiAssistantPreferences, routeAwareness: true }), { route: { name: 'theme-config', title: '主题配置' }, navigationToolsEnabled: false, highlightToolsEnabled: false, tourToolsEnabled: false })
})

test('route catalog rejects URL parameters, dynamic IDs, external URLs and auth pages', () => {
  for (const path of ['/users?token=secret', '/users#secret', '/users/:id', '//evil.example', 'https://evil.example', '/users/*', '/login', '/setup', '/oauth/authorize']) {
    assert.equal(isSafeAiRoute({ name: 'route', title: '页面', path }), false, path)
  }
  assert.equal(isSafeAiRoute({ name: 'users', title: '用户管理', path: '/system/user' }), true)
})

test('frontend tool requests reject arbitrary scripts, selectors, parameters and unknown tool names', () => {
  const tool = { toolCallId: 'tool-1', name: 'ui.highlight', arguments: { actionCode: 'system:user:create' } }
  assert.equal(parseAiFrontendToolRequest(tool)?.arguments.actionCode, 'system:user:create')
  assert.equal(parseAiFrontendToolRequest({ ...tool, arguments: { selector: '#danger' } }), undefined)
  assert.equal(parseAiFrontendToolRequest({ ...tool, arguments: { ...tool.arguments, script: 'document.cookie' } }), undefined)
  assert.equal(parseAiFrontendToolRequest({ ...tool, name: 'ui.execute' }), undefined)
  assert.equal(parseAiFrontendToolRequest({ ...tool, arguments: { actionCode: 'button:nth-child(2)' } }), undefined)
  assert.equal(parseAiFrontendToolRequest({ ...tool, expiresAt: '2026-10-14T10:30:00Z' })?.expiresAt, Date.parse('2026-10-14T10:30:00Z'))
  const tour = { toolCallId: 'tool-2', name: 'ui.tour', arguments: { tourCode: 'system.welcome' } }
  assert.equal(parseAiFrontendToolRequest(tour)?.arguments.tourCode, 'system.welcome')
  assert.equal(parseAiFrontendToolRequest({ ...tour, arguments: { tourCode: '[data-tour="x"]' } }), undefined)
  assert.equal(parseAiFrontendToolRequest({ ...tour, arguments: {} }), undefined)
})

test('configurable shortcuts ignore composition and held keys', () => {
  const event = { key: 'Y', ctrlKey: true, metaKey: false, shiftKey: true, altKey: false, isComposing: false, repeat: false }
  assert.equal(matchesAiAssistantShortcut(event, 'mod-shift-y'), true)
  assert.equal(matchesAiAssistantShortcut({ ...event, isComposing: true }, 'mod-shift-y'), false)
  assert.equal(matchesAiAssistantShortcut({ ...event, repeat: true }, 'mod-shift-y'), false)
  assert.equal(matchesAiAssistantShortcut(event, 'disabled'), false)
})

test('highlight accepts the full namespaced plugin action and preserves its identity', () => {
  const actionCode = `plugin:${'p'.repeat(48)}:${'a'.repeat(119)}`
  assert.ok(actionCode.length > 120)
  const request = { toolCallId: 'tool-plugin', name: 'ui.highlight', arguments: { actionCode, routeName: 'plugin-page' } }
  assert.deepEqual(parseAiFrontendToolRequest(request)?.arguments, request.arguments)
  assert.equal(parseAiFrontendToolRequest({ ...request, arguments: { actionCode: 'plugin:test:create_item' } })?.arguments.actionCode, 'plugin:test:create_item')
  assert.equal(parseAiFrontendToolRequest({ ...request, arguments: { actionCode: `p${'a'.repeat(180)}` } }), undefined)
})

test('page awareness never grants highlight or navigation tools implicitly', () => {
  const context = { actions: [{ code: 'system:user:create', label: '新增用户', routeName: 'users' }] }
  const aware = frontendContextForPreferences(context, { ...defaultAiAssistantPreferences, pageAwareness: true })
  assert.deepEqual(aware?.actions, context.actions)
  assert.equal(aware?.navigationToolsEnabled, false)
  assert.equal(aware?.highlightToolsEnabled, false)
  const navigable = frontendContextForPreferences(context, { ...defaultAiAssistantPreferences, pageAwareness: true, navigationTools: true })
  assert.equal(navigable?.navigationToolsEnabled, true)
  assert.equal(navigable?.highlightToolsEnabled, false)
})

test('tour tools only receive the declared tour catalog when explicitly enabled', () => {
  const context = { tours: ['system.welcome', 'plugin:wiki:intro'] }
  assert.equal(frontendContextForPreferences(context, defaultAiAssistantPreferences), undefined)
  const enabled = frontendContextForPreferences(context, { ...defaultAiAssistantPreferences, tourTools: true })
  assert.deepEqual(enabled?.tours, context.tours)
  assert.equal(enabled?.tourToolsEnabled, true)
  assert.equal(enabled?.navigationToolsEnabled, false)
  assert.equal(enabled?.highlightToolsEnabled, false)
  assert.equal(frontendContextForPreferences({}, { ...defaultAiAssistantPreferences, tourTools: true }), undefined)
})

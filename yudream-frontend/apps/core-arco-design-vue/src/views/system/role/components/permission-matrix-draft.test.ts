import type { RoleManageItem } from '@/api/modules/system-role'
import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test
import { describe, it } from 'node:test'
import { matrixDraftTarget, matrixSaveChanges, reconcileMatrixDrafts, sameMatrixPermissions, setMatrixGrants, toggleMatrixGrant } from './permission-matrix-draft'

function role(overrides: Partial<RoleManageItem> = {}): RoleManageItem {
  return { id: '9223372036854775806', name: '编辑角色', code: 'EDITOR', level: 'USER', systemRole: false, permissions: ['system:user:view', 'plugin:missing:use'], permissionCount: 2, status: 'ACTIVE', ...overrides }
}
const allowed = new Set(['system:user:view', 'system:user:edit', 'system:user:delete'])

describe('matrix permission operation drafts', () => {
  it('cancels repeated toggles and never mutates the original configuration', () => {
    const source = role()
    const draft = toggleMatrixGrant({}, source, 'system:user:edit', allowed)
    assert.deepEqual(draft[source.id]?.grant, ['system:user:edit'])
    assert.deepEqual(toggleMatrixGrant(draft, source, 'system:user:edit', allowed), {})
    const removal = toggleMatrixGrant({}, source, 'system:user:view', allowed)
    assert.deepEqual(toggleMatrixGrant(removal, source, 'system:user:view', allowed), {})
    assert.deepEqual(source.permissions, ['system:user:view', 'plugin:missing:use'])
  })

  it('bulk edits only supplied active codes and preserves unknown plugin grants', () => {
    const source = role()
    const draft = setMatrixGrants({}, source, ['system:user:view', 'plugin:missing:use'], false, allowed)
    assert.deepEqual(matrixDraftTarget(draft[source.id]!), ['plugin:missing:use'])
    assert.deepEqual(draft[source.id]?.revoke, ['system:user:view'])
    assert.deepEqual(setMatrixGrants({}, source, ['plugin:new:use'], true, allowed), {})
  })

  it('protects the system super administrator independently of level and permits disabled configuration edits', () => {
    assert.deepEqual(toggleMatrixGrant({}, role({ systemType: 'SUPER_ADMIN' }), 'system:user:edit', allowed), {})
    assert.ok(toggleMatrixGrant({}, role({ level: 'SUPER_ADMIN', status: 'DEPRECATED' }), 'system:user:edit', allowed)['9223372036854775806'])
  })

  it('locks configured wildcard roles and preserves a prior draft when the server adds a wildcard', () => {
    const source = role()
    assert.deepEqual(toggleMatrixGrant({}, { ...source, permissions: ['*'] }, 'system:user:edit', allowed), {})
    assert.deepEqual(setMatrixGrants({}, { ...source, permissions: ['*'] }, ['system:user:view'], false, allowed), {})
    const initial = toggleMatrixGrant({}, source, 'system:user:edit', allowed)
    const conflicted = reconcileMatrixDrafts(initial, [{ ...source, permissions: [...source.permissions, '*'] }])
    assert.equal(conflicted[source.id]?.conflict, 'wildcard')
    assert.deepEqual(conflicted[source.id]?.grant, ['system:user:edit'])
    assert.deepEqual(matrixSaveChanges(conflicted), [])
  })

  it('keeps same-named roles separate without rounding their long identifiers', () => {
    const first = role()
    const second = role({ id: '9223372036854775807' })
    const draft = toggleMatrixGrant(toggleMatrixGrant({}, first, 'system:user:edit', allowed), second, 'system:user:delete', allowed)
    assert.deepEqual(matrixSaveChanges(draft).map(item => item.roleId), [first.id, second.id])
    assert.deepEqual(draft[first.id]?.grant, ['system:user:edit'])
    assert.deepEqual(draft[second.id]?.grant, ['system:user:delete'])
  })

  it('clears only successful roles while retaining a failed baseline and flagging another conflict', () => {
    const first = role()
    const second = role({ id: '9223372036854775807' })
    const third = role({ id: '9223372036854775808' })
    let drafts = toggleMatrixGrant({}, first, 'system:user:edit', allowed)
    drafts = toggleMatrixGrant(drafts, second, 'system:user:delete', allowed)
    drafts = toggleMatrixGrant(drafts, third, 'system:user:edit', allowed)
    const result = reconcileMatrixDrafts(drafts, [
      { ...first, permissions: matrixDraftTarget(drafts[first.id]!) },
      { ...second, permissions: [...second.permissions] },
      { ...third, permissions: [...third.permissions, 'system:user:delete'] },
    ])
    assert.equal(result[first.id], undefined)
    assert.equal(result[second.id]?.conflict, undefined)
    assert.equal(result[third.id]?.conflict, 'changed')
    assert.deepEqual(matrixSaveChanges(result).map(item => item.roleId), [second.id])
    assert.deepEqual(result[third.id]?.expectedPermissions, third.permissions)
  })

  it('never automatically rebases a conflict and marks removed or newly protected roles', () => {
    const source = role()
    const initial = toggleMatrixGrant({}, source, 'system:user:edit', allowed)
    const conflicted = reconcileMatrixDrafts(initial, [{ ...source, permissions: ['system:user:delete'] }])
    assert.equal(reconcileMatrixDrafts(conflicted, [source])[source.id]?.conflict, 'changed')
    assert.equal(reconcileMatrixDrafts(initial, [])[source.id]?.conflict, 'missing')
    assert.equal(reconcileMatrixDrafts(initial, [{ ...source, systemType: 'SUPER_ADMIN' }])[source.id]?.conflict, 'protected')
    assert.deepEqual(toggleMatrixGrant(conflicted, source, 'system:user:delete', allowed), conflicted)
    assert.ok(sameMatrixPermissions(['a', 'b'], ['b', 'a', 'a']))
  })

  it('flags a reorder-only baseline change without rebasing the original ordered CAS snapshot', () => {
    const source = role()
    const initial = toggleMatrixGrant({}, source, 'system:user:edit', allowed)
    const reordered = { ...source, permissions: [...source.permissions].reverse() }
    const result = reconcileMatrixDrafts(initial, [reordered])
    assert.equal(result[source.id]?.conflict, 'changed')
    assert.deepEqual(result[source.id]?.expectedPermissions, source.permissions)
    assert.deepEqual(result[source.id]?.grant, ['system:user:edit'])
    assert.deepEqual(matrixSaveChanges(result), [])
    const achieved = { ...source, permissions: matrixDraftTarget(initial[source.id]!).reverse() }
    assert.deepEqual(reconcileMatrixDrafts(initial, [achieved]), {})
  })

  it('preserves intent but stops saving when an edited permission disappears from the active catalog', () => {
    const source = role()
    const draft = toggleMatrixGrant({}, source, 'system:user:view', allowed)
    const result = reconcileMatrixDrafts(draft, [source], new Set(['system:user:edit']))
    assert.equal(result[source.id]?.conflict, 'catalog')
    assert.deepEqual(result[source.id]?.revoke, ['system:user:view'])
    assert.deepEqual(matrixSaveChanges(result), [])
    assert.deepEqual(result[source.id]?.expectedPermissions, source.permissions)
  })
})

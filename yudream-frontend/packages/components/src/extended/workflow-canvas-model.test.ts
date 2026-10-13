/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'

const workflowModelPath = './workflow-canvas-model.ts'
const { canEditYdWorkflowCanvas, getYdWorkflowNodeSlotNames, normalizeYdWorkflowConnection, resolveYdWorkflowDeleteKeyCode } = await import(workflowModelPath) as typeof import('./workflow-canvas-model')

test('connection payload keeps string IDs and handle IDs while dropping engine-only fields', () => {
  const payload = normalizeYdWorkflowConnection({
    source: '90071992547409931',
    target: 'target-node',
    sourceHandle: 'true',
    targetHandle: 'input',
    // A cast models fields VueFlow includes on its internal connection object.
    node: { id: 'internal-node' },
  } as unknown as Parameters<typeof normalizeYdWorkflowConnection>[0])

  assert.deepEqual(payload, {
    source: '90071992547409931',
    target: 'target-node',
    sourceHandle: 'true',
    targetHandle: 'input',
  })
  assert.equal(typeof payload.source, 'string')
})

test('readonly preserves view behavior while disabled and readonly both block mutation APIs', () => {
  assert.equal(canEditYdWorkflowCanvas({}), true)
  assert.equal(canEditYdWorkflowCanvas({ readonly: true }), false)
  assert.equal(canEditYdWorkflowCanvas({ disabled: true }), false)
  assert.equal(canEditYdWorkflowCanvas({ disabled: true, readonly: true }), false)
})

test('readonly and disabled canvases suppress configured keyboard deletion', () => {
  assert.equal(resolveYdWorkflowDeleteKeyCode({}, 'Delete'), 'Delete')
  assert.equal(resolveYdWorkflowDeleteKeyCode({ readonly: true }, 'Delete'), null)
  assert.equal(resolveYdWorkflowDeleteKeyCode({ disabled: true }, 'Delete'), null)
  assert.equal(resolveYdWorkflowDeleteKeyCode({}, null), null)
})

test('workflow renderer slot names are filtered from the current slot keys', () => {
  assert.deepEqual(getYdWorkflowNodeSlotNames(['default', 'node-agent', 'node-condition', 'toolbar']), ['node-agent', 'node-condition'])
  assert.deepEqual(getYdWorkflowNodeSlotNames(['node-agent', 'toolbar']), ['node-agent'])
})

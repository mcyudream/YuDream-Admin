/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'

const cardSelectionPath = './card-selection.ts'
const { createYdResponsiveCardSelection } = await import(cardSelectionPath) as typeof import('./card-selection')

function rowPort({ selected = false, canSelect = true } = {}) {
  const calls: Array<boolean | undefined> = []
  return {
    calls,
    row: {
      id: '90071992547409931',
      getIsSelected: () => selected,
      getCanSelect: () => canSelect,
      toggleSelected: (value?: boolean) => calls.push(value),
    },
  }
}

test('mobile card selection exposes controlled state and forwards checkbox/radio changes to its row', () => {
  const port = rowPort()
  const selection = createYdResponsiveCardSelection(port.row)

  assert.equal(selection.selectionId, '90071992547409931')
  assert.equal(selection.selectable, true)
  assert.equal(selection.selectDisabled, false)
  assert.equal(selection.selected, false)
  selection.toggleSelected(true)
  selection.toggleSelected(false)
  assert.deepEqual(port.calls, [true, false])
})

test('disabled rows keep their selected display state and reject card selection changes', () => {
  const port = rowPort({ selected: true, canSelect: false })
  const selection = createYdResponsiveCardSelection(port.row)

  assert.equal(selection.selected, true)
  assert.equal(selection.selectDisabled, true)
  selection.toggleSelected(false)
  assert.deepEqual(port.calls, [])
})

test('missing engine row is a disabled empty selection state', () => {
  const selection = createYdResponsiveCardSelection()
  assert.deepEqual(selection, {
    selected: false,
    selectable: false,
    selectDisabled: true,
    selectionId: '',
    toggleSelected: selection.toggleSelected,
  })
})

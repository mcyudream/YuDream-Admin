export interface YdResponsiveCardRowSelectionPort {
  id: string
  getIsSelected: () => boolean
  getCanSelect: () => boolean
  toggleSelected: (value?: boolean) => void
}

export interface YdResponsiveCardSelection {
  selected: boolean
  selectable: boolean
  selectDisabled: boolean
  selectionId: string
  toggleSelected: (value?: boolean) => void
}

/** Keeps the mobile card control on the same controlled TanStack row-selection path as the desktop table. */
export function createYdResponsiveCardSelection(row?: YdResponsiveCardRowSelectionPort): YdResponsiveCardSelection {
  const canSelect = row?.getCanSelect() ?? false
  return {
    selected: row?.getIsSelected() ?? false,
    selectable: canSelect,
    selectDisabled: !canSelect,
    selectionId: row?.id ?? '',
    toggleSelected(value) {
      if (row?.getCanSelect()) {
        row.toggleSelected(value)
      }
    },
  }
}

export function toYdFileArray(files: ArrayLike<File> | null | undefined): File[] {
  return files ? Array.from(files) : []
}

export function resetYdFileInput(input: Pick<HTMLInputElement, 'value'> | null | undefined): void {
  if (input) {
    input.value = ''
  }
}

export function resolveYdFilePickerTriggerAttrs(input: {
  id?: string
  label: string
  ariaLabel?: string
  invalid?: boolean | string
  describedby?: string
}) {
  return {
    'id': input.id,
    'aria-label': input.ariaLabel || input.label,
    'aria-invalid': input.invalid === true || input.invalid === 'true' ? 'true' as const : undefined,
    'aria-describedby': input.describedby,
  }
}

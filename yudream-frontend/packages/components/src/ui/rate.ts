/** Normalize both pointer and keyboard edits to the public scoring precision. */
export function normalizeYdRate(value: number, count: number, allowHalf: boolean) {
  const max = Math.max(1, Math.floor(Number.isFinite(count) ? count : 5))
  const step = allowHalf ? 0.5 : 1
  return Math.min(max, Math.max(0, Math.round((Number.isFinite(value) ? value : 0) / step) * step))
}

export function getYdRatePointerValue(index: number, pointer: number, left: number, width: number, allowHalf: boolean) {
  return index + (allowHalf && width > 0 && pointer - left < width / 2 ? 0.5 : 1)
}

export interface YdMarkdownUploadLifecycle {
  ticket: () => number
  isActive: (ticket: number) => boolean
  invalidate: () => void
  dispose: () => void
}

export function createYdMarkdownUploadLifecycle(): YdMarkdownUploadLifecycle {
  let generation = 0
  let disposed = false
  return {
    ticket: () => generation,
    isActive: ticket => !disposed && ticket === generation,
    invalidate: () => { generation += 1 },
    dispose: () => {
      disposed = true
      generation += 1
    },
  }
}

export function createYdMarkdownUploadCallback<T>(
  lifecycle: YdMarkdownUploadLifecycle,
  ticket: number,
  callback: (value: T) => void,
): (value: T) => void {
  let called = false
  return (value) => {
    if (called || !lifecycle.isActive(ticket)) {
      return
    }
    called = true
    callback(value)
  }
}

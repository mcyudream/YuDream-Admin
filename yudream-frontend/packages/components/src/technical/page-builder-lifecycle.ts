export function createYdPageBuilderFacadeMethod<TArgs extends unknown[], TResult>(
  isActive: () => boolean,
  operation: (...args: TArgs) => TResult,
): (...args: TArgs) => TResult {
  return (...args) => {
    if (!isActive()) {
      throw new Error('页面构建器实例已释放')
    }
    return operation(...args)
  }
}

export function isYdPageBuilderUploadCurrent<TEditor extends object>(
  captured: TEditor | null,
  current: TEditor | null,
  disposed: boolean,
  destroyed: (editor: TEditor) => boolean,
): captured is TEditor {
  return captured !== null && !disposed && current === captured && !destroyed(captured)
}

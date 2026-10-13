export interface YdLoaderContext {
  results: Record<string, unknown>
  signal: AbortSignal
}
export interface YdLoaderStep {
  text: string
  afterText?: string
  name?: string
  action?: (context: YdLoaderContext) => unknown | Promise<unknown>
}
export class YdLoaderStepError extends Error {
  constructor(readonly index: number, readonly reason: unknown) {
    super(reason instanceof Error ? reason.message : String(reason), { cause: reason })
    this.name = 'YdLoaderStepError'
  }
}
/** 每个动作收到同一份结果和取消信号；取消后不启动后续动作。 */
export async function runYdLoaderSteps(steps: readonly YdLoaderStep[], signal: AbortSignal, onStep: (index: number) => void) {
  const results: Record<string, unknown> = Object.create(null)
  for (const [index, step] of [...steps].entries()) {
    if (signal.aborted) {
      return { cancelled: true, results }
    }
    onStep(index)
    if (signal.aborted) {
      return { cancelled: true, results }
    }
    try {
      const value = await step.action?.({ results, signal })
      if (signal.aborted) {
        return { cancelled: true, results }
      }
      if (step.name) {
        results[step.name] = value
      }
    }
    catch (error) {
      if (signal.aborted) {
        return { cancelled: true, results }
      }
      throw new YdLoaderStepError(index, error)
    }
  }
  return { cancelled: signal.aborted, results }
}

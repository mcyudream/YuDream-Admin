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

export function isYdPageBuilderInstanceCurrent<TEditor extends object>(
  captured: TEditor | null,
  current: TEditor | null,
  disposed: boolean,
  destroyed: (editor: TEditor) => boolean,
): captured is TEditor {
  return captured !== null && !disposed && current === captured && !destroyed(captured)
}

/**
 * 引擎接管目标：GrapesJS 初始化会 `container.empty()` 并追加引擎 DOM，
 * 销毁时还会再次清空同一容器。挂载目标因此只能是专用引擎子容器；
 * 根元素承载着 Vue 渲染的状态/错误节点（含 v-if 锚点），绝不能交给引擎。
 */
export function resolveYdPageBuilderEngineContainer(
  root: HTMLElement | null,
  engine: HTMLElement | null,
): HTMLElement | null {
  if (engine && engine !== root) {
    return engine
  }
  return null
}

export interface YdPageBuilderReadinessState {
  disposed: boolean
  readyEmitted: boolean
  /** 初始内容（项目或 HTML/CSS）已写入引擎 */
  prepared: boolean
  /** 引擎已完成画布加载（load 事件或 headless/就绪文档判定） */
  canvasLoaded: boolean
}

/** 加载完成的就绪门：只发一次 ready，销毁后与迟到/重复的 load 事件都不再发。 */
export function shouldYdPageBuilderEmitReady(state: YdPageBuilderReadinessState): boolean {
  return !state.disposed && !state.readyEmitted && state.prepared && state.canvasLoaded
}

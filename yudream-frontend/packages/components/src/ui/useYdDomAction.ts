import type { YdDomActionInstance, YdDomActionOptions } from './dom-action-renderer'
import { YdIcon } from '../adapters/index'
import { mountYdDomAction } from './dom-action-renderer'
import YdAction from './YdAction.vue'

export type { YdDomActionInstance, YdDomActionOptions, YdDomActionUpdate } from './dom-action-renderer'

/** Mounts one themed YD semantic action into a non-Vue host-owned container. */
export function useYdDomAction(container: Element, options: YdDomActionOptions): YdDomActionInstance {
  return mountYdDomAction(container, YdAction, YdIcon, options)
}

import type { InjectionKey } from 'vue'

/**
 * 表格操作区传送目标：YdTablePageLayout 提供自身操作栏末尾容器的元素 id，
 * 表格的「列显隐」控件优先传送过去与操作按钮并排；未包裹于布局时留在表格自带工具栏行。
 */
export const tableActionsTargetKey = Symbol('yd-table-actions-target') as InjectionKey<string>

<script setup lang="ts">
/**
 * YdWorkspaceTabs：工作区多标签栏（VS Code 编辑器标签的 YD 收口）。
 *
 * 与 YdTabs 的分工：YdTabs 是「分区内容切换」；本组件只管一条可溢出滚动的文件标签条，
 * 标签内容由调用方渲染，不承载页面内容。
 *
 * 行为契约：
 * 1. 关闭：中键、关闭按钮、聚焦标签按 Delete（closable !== false）都发出 `close`，
 *    组件不自行决定关闭后激活谁——调用方用 `nextWorkspaceTabKey` 计算；
 * 2. 键盘：左右方向键、Home/End 移动并激活（自动激活模式），激活标签进入视口；
 *    鼠标滚轮在标签条上横向滚动，不冒泡改变页面滚动；
 * 3. 溢出：仅在溢出时出现左右滚动按钮，标签超长省略而不撑破工具条；
 * 4. 未保存只显示脏点，不改标题，避免「保存后标题跳字」。
 */
import type { YdWorkspaceTab } from './workspace-model'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { cn } from '#utils'
import Icon from '../basic/icon/index.vue'
import { stepWorkspaceTabKey } from './workspace-model'

export type { YdWorkspaceTab }

defineOptions({ name: 'YdWorkspaceTabs' })

const props = withDefaults(defineProps<{
  /** 打开的标签集合，顺序即展示顺序 */
  tabs: YdWorkspaceTab[]
  /** 当前激活标签 key；无激活（全部关闭）时传空串 */
  modelValue: string
  ariaLabel?: string
  /** 是否显示末尾新建按钮；点击发出 `add`，新建逻辑由调用方决定 */
  addable?: boolean
  size?: 'sm' | 'md'
  class?: string
}>(), {
  ariaLabel: '工作区文件标签',
  addable: false,
  size: 'sm',
})

const emits = defineEmits<{
  'update:modelValue': [key: string]
  'close': [key: string]
  'add': []
}>()

const listRef = ref<HTMLElement | null>(null)
const tabElements = new Map<string, HTMLElement>()
const overflowing = ref(false)
let observer: ResizeObserver | undefined

/** 函数式 ref：挂载写入、卸载（null）删除，避免数组式 ref 在重渲染时累积重复节点 */
function registerTab(element: unknown, key: string) {
  if (element instanceof HTMLElement) {
    tabElements.set(key, element)
  }
  else {
    tabElements.delete(key)
  }
}

function measure() {
  const element = listRef.value
  overflowing.value = Boolean(element && element.scrollWidth > element.clientWidth + 1)
}

onMounted(() => {
  measure()
  if (typeof ResizeObserver !== 'undefined' && listRef.value) {
    observer = new ResizeObserver(measure)
    observer.observe(listRef.value)
  }
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = undefined
})

watch(() => props.tabs.map(tab => tab.key).join('|'), () => nextTick(measure))

function tabElement(key: string): HTMLElement | undefined {
  return tabElements.get(key)
}

function activate(key: string, focus = false) {
  if (!key) {
    return
  }
  if (key !== props.modelValue) {
    emits('update:modelValue', key)
  }
  void nextTick(() => {
    const element = tabElement(key)
    element?.scrollIntoView({ inline: 'nearest', block: 'nearest' })
    if (focus) {
      element?.focus()
    }
  })
}

function closeTab(tab: YdWorkspaceTab) {
  if (tab.closable === false) {
    return
  }
  emits('close', tab.key)
}

/** 中键关闭：只认 button === 1，避免左键点击在部分浏览器作为 auxclick 重复触发 */
function onAuxClick(event: MouseEvent, tab: YdWorkspaceTab) {
  if (event.button !== 1) {
    return
  }
  event.preventDefault()
  closeTab(tab)
}

function onWheel(event: WheelEvent) {
  const element = listRef.value
  if (!element) {
    return
  }
  event.preventDefault()
  const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
  element.scrollLeft += delta
}

function scrollStep(direction: number) {
  listRef.value?.scrollBy({ left: direction * 160, behavior: 'smooth' })
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
    event.preventDefault()
    activate(stepWorkspaceTabKey(props.tabs, props.modelValue, event.key === 'ArrowRight' ? 1 : -1), true)
    return
  }
  if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault()
    const target = event.key === 'Home' ? props.tabs[0] : props.tabs[props.tabs.length - 1]
    activate(target?.key ?? '', true)
    return
  }
  if (event.key === 'Delete') {
    const tab = props.tabs.find(item => item.key === props.modelValue)
    if (tab && tab.closable !== false) {
      event.preventDefault()
      closeTab(tab)
    }
  }
}
</script>

<template>
  <div
    :class="cn('yd-workspace-tabs', `yd-workspace-tabs--${props.size}`, props.class)"
    class="px-1 border-b bg-muted/40 flex gap-1 items-center"
  >
    <button
      v-if="overflowing"
      type="button"
      class="yd-workspace-tabs__arrow"
      aria-label="向左滚动标签"
      @click="scrollStep(-1)"
    >
      <Icon name="morph:chevron-left" />
    </button>
    <div
      ref="listRef"
      class="yd-workspace-tabs__list flex-1 min-w-0"
      role="tablist"
      :aria-label="ariaLabel"
      @wheel="onWheel"
      @keydown="onKeydown"
    >
      <div
        v-for="tab in tabs"
        :key="tab.key"
        :ref="(element) => registerTab(element, tab.key)"
        :data-key="tab.key"
        role="tab"
        class="yd-workspace-tabs__tab"
        :class="tab.key === modelValue ? 'yd-workspace-tabs__tab--active' : ''"
        :aria-selected="tab.key === modelValue"
        :tabindex="tab.key === modelValue ? 0 : -1"
        :title="tab.title || tab.label"
        @click="activate(tab.key)"
        @auxclick="onAuxClick($event, tab)"
      >
        <Icon v-if="tab.icon" :name="tab.icon" class="yd-workspace-tabs__icon size-3.5" />
        <span class="yd-workspace-tabs__label">{{ tab.label }}</span>
        <span v-if="tab.dirty" class="yd-workspace-tabs__dot" aria-hidden="true" />
        <span v-if="tab.dirty" class="yd-workspace-tabs__sr">未保存</span>
        <button
          v-if="tab.closable !== false"
          type="button"
          class="yd-workspace-tabs__close"
          :aria-label="`关闭 ${tab.label}`"
          @click.stop="closeTab(tab)"
        >
          <Icon name="morph:x" />
        </button>
      </div>
    </div>
    <button
      v-if="overflowing"
      type="button"
      class="yd-workspace-tabs__arrow"
      aria-label="向右滚动标签"
      @click="scrollStep(1)"
    >
      <Icon name="morph:chevron-right" />
    </button>
    <button
      v-if="addable"
      type="button"
      class="yd-workspace-tabs__arrow flex-shrink-0"
      aria-label="新建页面"
      @click="emits('add')"
    >
      <Icon name="morph:plus" />
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * YdListToolbar：管理列表页统一筛选区（宿主表格页标准版式）。
 *
 * 版式规范：
 * 1. 第一行放列表标题与视图切换（tab）：左侧标题、右侧 tab，独立成行；
 * 2. 第二行是筛选行：搜索与各筛选控件等宽排成一行，行尾是操作区（重置 / 筛选 / 表格列控件）；
 * 3. 表格页**不提供刷新按钮**：查询由「筛选」触发，列表数据变化随查询与翻页发生；
 * 4. 可选状态计数行（`statusItems`）位于筛选行下方，计数即筛选。
 *
 * 用法：`v-model:keyword` 绑搜索词，`v-model:status` 绑状态筛选；`#title`/`#views` 放标题与 tab，
 * `#filters` 放下拉筛选，`#actions` 放行尾操作（含表格列控件）。
 */
import type { YdListToolbarStatusItem } from './list-toolbar-model'
import { computed, useSlots } from 'vue'
import Input from '../basic/input/index.vue'

export type { YdListToolbarStatusItem }

defineOptions({ name: 'YdListToolbar' })

const props = withDefaults(defineProps<{
  /** 列表标题；也可用 #title 插槽自定义。 */
  title?: string
  placeholder?: string
  /** 搜索单元的参考宽度，与筛选控件同网格等宽。 */
  searchWidth?: string | number
  /** 状态计数筛选项；传入即渲染计数胶囊行。 */
  statusItems?: YdListToolbarStatusItem[]
  /** 状态行底部显示只读比例条。 */
  statusRatio?: boolean
}>(), {
  placeholder: '搜索',
  searchWidth: 200,
  statusRatio: true,
})

const emits = defineEmits<{
  /** 搜索框回车：页面据此触发查询。 */
  search: []
  /** 清空搜索词：页面据此恢复默认列表。 */
  clear: []
}>()
const keyword = defineModel<string>('keyword', { default: '' })
const status = defineModel<string>('status', { default: '' })

const slots = useSlots()
const hasHeader = computed(() => Boolean(props.title) || Boolean(slots.title) || Boolean(slots.views))
const cssWidth = computed(() => typeof props.searchWidth === 'number' ? `${props.searchWidth}px` : props.searchWidth)

/** 比例条只用计数推导，不引入其他数据来源。 */
const ratioSegments = computed(() => {
  const items = (props.statusItems || []).filter(item => item.value !== 'ALL')
  const total = items.reduce((sum, item) => sum + (item.count || 0), 0)
  if (!total) {
    return []
  }
  return items
    .filter(item => (item.count || 0) > 0)
    .map(item => ({ tone: item.tone || 'neutral', percent: Math.round((item.count || 0) / total * 100) }))
})
</script>

<template>
  <div class="yd-list-toolbar">
    <div v-if="hasHeader" class="yd-list-toolbar__header">
      <div class="yd-list-toolbar__title">
        <slot name="title">
          {{ title }}
        </slot>
      </div>
      <div v-if="$slots.views" class="yd-list-toolbar__views">
        <slot name="views" />
      </div>
    </div>

    <div class="yd-list-toolbar__row" role="search">
      <slot name="search">
        <!-- 外层 wrapper 才能限宽：FaInput 把 class 放在内部 InputGroup 上，输入框自身撑满父级 -->
        <span class="yd-list-toolbar__search-wrap" :style="{ minWidth: cssWidth }">
          <Input
            v-model="keyword"
            class="yd-list-toolbar__search"
            clearable
            :placeholder="placeholder"
            :aria-label="placeholder"
            @keydown.enter="emits('search')"
            @clear="emits('clear')"
          />
        </span>
      </slot>
      <slot name="filters" />
      <div v-if="$slots.actions" class="yd-list-toolbar__actions">
        <slot name="actions" />
      </div>
    </div>

    <div v-if="statusItems?.length" class="yd-list-toolbar__status">
      <button
        v-for="item in statusItems"
        :key="item.value"
        class="yd-list-toolbar__chip"
        :class="{ 'is-active': status === item.value }"
        :data-tone="item.tone || 'neutral'"
        type="button"
        :aria-pressed="status === item.value"
        @click="status = item.value"
      >
        <span v-if="item.tone === 'ok' || item.tone === 'bad'" class="yd-list-toolbar__dot" />
        <span>{{ item.label }}</span>
        <strong v-if="item.count !== undefined">{{ item.count }}</strong>
      </button>
      <div v-if="statusRatio && ratioSegments.length" class="yd-list-toolbar__ratio" aria-hidden="true">
        <span
          v-for="segment in ratioSegments"
          :key="segment.tone"
          :data-tone="segment.tone"
          :style="{ width: `${segment.percent}%` }"
        />
      </div>
    </div>
  </div>
</template>

<style src="./ui.css"></style>

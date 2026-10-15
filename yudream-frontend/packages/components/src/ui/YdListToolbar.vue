<script setup lang="ts">
/**
 * YdListToolbar：管理列表页统一的工具栏。
 *
 * 把重复出现的三类问题一次收口：
 * 1. 搜索框被 1fr 拉满整行 —— 内置搜索并限宽（默认 320px），窄容器按可用宽度收缩；
 * 2. 状态计数各页各写 —— 传 `statusItems` 即得到"计数即筛选"的胶囊行与比例条；
 * 3. 工具栏把父容器撑宽 —— 行内允许换行、宽度不写死，避免横向溢出。
 *
 * 用法：`v-model:keyword` 绑搜索词，`v-model:status` 绑状态筛选，`#filters` 放下拉筛选，
 * `#actions` 放右侧操作按钮。
 */
import type { YdListToolbarStatusItem } from './list-toolbar-model'
import { computed } from 'vue'
import Input from '../basic/input/index.vue'

export type { YdListToolbarStatusItem }

defineOptions({ name: 'YdListToolbar' })

const props = withDefaults(defineProps<{
  placeholder?: string
  /** 搜索框最大宽度；窄容器下按可用宽度收缩。 */
  searchWidth?: string | number
  /** 状态计数筛选项；传入即渲染计数胶囊行。 */
  statusItems?: YdListToolbarStatusItem[]
  /** 状态行底部显示只读比例条。 */
  statusRatio?: boolean
}>(), {
  placeholder: '搜索',
  searchWidth: 320,
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
  <div class="yd-list-toolbar" role="search">
    <div class="yd-list-toolbar__row">
      <div class="yd-list-toolbar__filters">
        <slot name="search">
          <!-- 外层 wrapper 才能限宽：FaInput 把 class 放在内部 InputGroup 上，输入框自身撑满父级 -->
          <span class="yd-list-toolbar__search-wrap" :style="{ maxWidth: cssWidth }">
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
      </div>
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

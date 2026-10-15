<script setup lang="ts">
import type { YdTreeNode } from './YdTree.vue'
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'
import { computed, ref } from 'vue'
import YdIcon from '../basic/icon/index.vue'
import YdTree from './YdTree.vue'

const props = withDefaults(defineProps<{
  data: YdTreeNode[]
  multiple?: boolean
  checkable?: boolean
  filterable?: boolean
  disabled?: boolean
  placeholder?: string
  clearable?: boolean
  id?: string
  ariaInvalid?: boolean | string
  ariaDescribedby?: string
}>(), { placeholder: '请选择', clearable: true })
const emit = defineEmits<{ change: [value: string | string[]] }>()
const model = defineModel<string | string[]>({ default: '' })
const open = ref(false)
const selectedKeys = computed<string[]>(() => Array.isArray(model.value) ? model.value : model.value ? [model.value] : [])
const ariaInvalidValue = computed(() => props.ariaInvalid === true || props.ariaInvalid === 'true' ? 'true' : props.ariaInvalid === false || props.ariaInvalid === 'false' ? 'false' : undefined)
const labelMap = computed(() => {
  const result = new Map<string, string>()
  const visit = (nodes: YdTreeNode[]) => nodes.forEach((node) => {
    result.set(node.key, node.label)
    if (node.children) {
      visit(node.children)
    }
  })
  visit(props.data)
  return result
})
const label = computed(() => selectedKeys.value.map(key => labelMap.value.get(key) || key).join('、'))
function select(keys: string[] | string) {
  const next = props.multiple ? (Array.isArray(keys) ? keys : keys ? [keys] : []) : Array.isArray(keys) ? keys[0] || '' : keys
  model.value = next
  emit('change', next)
  if (!props.multiple) {
    open.value = false
  }
}
function clear() {
  model.value = props.multiple ? [] : ''
  emit('change', model.value)
  open.value = false
}
</script>

<template>
  <div class="yd-tree-select-wrap">
    <PopoverRoot v-model:open="open">
      <PopoverTrigger as-child>
        <button type="button" :disabled="disabled" class="yd-tree-select" :aria-expanded="open" :aria-controls="id ? `${id}-panel` : undefined" :aria-invalid="ariaInvalidValue" :aria-describedby="ariaDescribedby">
          <span :class="{ 'is-placeholder': !selectedKeys.length }">{{ label || placeholder }}</span>
          <YdIcon name="morph:chevron-down" />
        </button>
      </PopoverTrigger>
      <PopoverPortal>
        <PopoverContent :id="id ? `${id}-panel` : undefined" class="yd-collection-popup yd-tree-select__panel" align="start" :side-offset="6" @open-auto-focus="$event.preventDefault()">
          <YdTree :data="data" :selectable="!multiple" :checkable="multiple || checkable" :filterable="filterable" :selected-keys="selectedKeys" :checked-keys="selectedKeys" @selection-change="node => select(node ? [node.key] : [])" @check-change="nodes => select(nodes.map(node => node.key))" />
        </PopoverContent>
      </PopoverPortal>
    </PopoverRoot>
    <button v-if="clearable && selectedKeys.length && !disabled" type="button" aria-label="清除选择" class="yd-tree-select__clear" @click="clear">
      <YdIcon name="morph:x" />
    </button>
    <input v-bind="$attrs" type="hidden" :value="Array.isArray(model) ? model.join(',') : model" :disabled="disabled" :aria-invalid="ariaInvalidValue" :aria-describedby="ariaDescribedby">
  </div>
</template>

<style scoped>
.yd-tree-select-wrap { position: relative; display: flex; align-items: center; min-width: 0; }
.yd-tree-select { display: flex; align-items: center; gap: 0.5rem; width: 100%; min-height: var(--yd-control-height); padding: 0.45rem 0.7rem; color: oklch(var(--foreground)); text-align: left; background: oklch(var(--background)); border: 1px solid oklch(var(--border)); border-radius: var(--yd-control-radius); cursor: pointer; }
.yd-tree-select > span { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.yd-tree-select .is-placeholder { color: oklch(var(--muted-foreground)); }
.yd-tree-select:disabled { cursor: not-allowed; opacity: 0.55; }
.yd-tree-select__clear { position: absolute; top: 50%; right: 2.1rem; display: inline-flex; padding: 0; color: oklch(var(--muted-foreground)); background: transparent; border: 0; cursor: pointer; transform: translateY(-50%); }
</style>

<style>
/* 弹层传送到 body，用全局类命中；复合选择器保证覆盖共享弹层默认值。 */
.yd-collection-popup.yd-tree-select__panel { padding: 0.5rem; max-height: 20rem; min-width: var(--reka-popper-anchor-width); overflow: auto; }
</style>

<script setup lang="ts" generic="T extends RowData = RowData">
import type { RowData } from '@tanstack/vue-table'
import type { TableColumn } from '../table/index.vue'
import type { YdTablePickerQuery, YdTablePickerResult } from './types'
import { computed, ref, shallowRef, useSlots, watch } from 'vue'
import FaButton from '../button/index.vue'
import FaCheckbox from '../checkbox/index.vue'
import FaIcon from '../icon/index.vue'
import FaInput from '../input/index.vue'
import FaModal from '../modal/index.vue'
import FaPagination from '../pagination/index.vue'
import FaTable from '../table/index.vue'
import FaTag from '../tag/index.vue'
import { useToast } from '../toast/index'

defineOptions({
  name: 'YdTablePicker',
})

const props = withDefaults(defineProps<{
  /** 选中行的 key 列表（一律 string，雪花 ID 禁止转 Number） */
  modelValue?: string[]
  /** 业务列配置（与 FaTable 一致），选择列由组件内部前置 */
  columns: TableColumn<T>[]
  /** 动态取数函数：点击弹窗、翻页、搜索时调用 */
  fetcher: (query: YdTablePickerQuery) => Promise<YdTablePickerResult<T>>
  /** 行唯一键字段 */
  rowKey?: string
  /** 表面标签与已选列表展示的文本字段 */
  labelKey?: string
  /** 多选（默认）/ 单选 */
  multiple?: boolean
  /** 外部已选项的文本回显（编辑场景），key -> 展示文本 */
  initialLabels?: Record<string, string>
  title?: string
  placeholder?: string
  searchPlaceholder?: string
  pageSize?: number
  pageSizes?: number[]
  disabled?: boolean
  clearable?: boolean
  emptyText?: string
}>(), {
  modelValue: () => [],
  rowKey: 'id',
  labelKey: 'label',
  multiple: true,
  initialLabels: undefined,
  title: '选择数据',
  placeholder: '请选择',
  searchPlaceholder: '输入关键字后回车搜索',
  pageSize: 10,
  pageSizes: () => [10, 20, 50],
  disabled: false,
  clearable: true,
  emptyText: '暂无数据',
})

const emit = defineEmits<{
  'update:modelValue': [keys: string[]]
  'change': [keys: string[]]
}>()

const toast = useToast()

const open = ref(false)
const loading = ref(false)
const rows = shallowRef<T[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(props.pageSize)
const keyword = ref('')
const appliedKeyword = ref('')

/** 弹窗内的选中态（key 集合），跨页保持 */
const selectedKeys = ref<Set<string>>(new Set())
/** key -> 展示文本缓存，保证翻页/再次打开后标签仍可显示 */
const labelCache = new Map<string, string>()
const cacheVersion = ref(0)

function keyOf(row: T) {
  return String((row as Record<string, unknown>)[props.rowKey] ?? '')
}

function labelOf(row: T) {
  const value = (row as Record<string, unknown>)[props.labelKey]
  return value == null ? keyOf(row) : String(value)
}

const selectedLabels = computed(() => {
  // labelCache 为普通 Map，靠 cacheVersion 触发重算
  void cacheVersion.value
  return props.modelValue.map(key => ({ key, label: labelCache.get(key) ?? props.initialLabels?.[key] ?? key }))
})

async function fetchData() {
  loading.value = true
  try {
    const result = await props.fetcher({ page: page.value, size: size.value, keyword: appliedKeyword.value })
    rows.value = result.list
    total.value = result.total
    for (const row of result.list) {
      labelCache.set(keyOf(row), labelOf(row))
    }
    cacheVersion.value++
  }
  catch {
    rows.value = []
    total.value = 0
    toast.error('数据加载失败，请稍后重试')
  }
  finally {
    loading.value = false
  }
}

function openPicker() {
  if (props.disabled) {
    return
  }
  selectedKeys.value = new Set(props.modelValue)
  page.value = 1
  size.value = props.pageSize
  keyword.value = ''
  appliedKeyword.value = ''
  open.value = true
  fetchData()
}

function reload() {
  if (open.value) {
    fetchData()
  }
}

function search() {
  appliedKeyword.value = keyword.value.trim()
  page.value = 1
  fetchData()
}

function resetSearch() {
  keyword.value = ''
  appliedKeyword.value = ''
  page.value = 1
  fetchData()
}

function toggleRow(row: T) {
  const key = keyOf(row)
  if (!key) {
    return
  }
  labelCache.set(key, labelOf(row))
  const next = new Set(selectedKeys.value)
  if (props.multiple) {
    if (next.has(key)) {
      next.delete(key)
    }
    else {
      next.add(key)
    }
  }
  else {
    next.clear()
    next.add(key)
  }
  selectedKeys.value = next
  cacheVersion.value++
}

const currentPageKeys = computed(() => rows.value.map(row => keyOf(row)).filter(Boolean))
const pageAllChecked = computed(() => currentPageKeys.value.length > 0 && currentPageKeys.value.every(key => selectedKeys.value.has(key)))
const pagePartiallyChecked = computed(() => !pageAllChecked.value && currentPageKeys.value.some(key => selectedKeys.value.has(key)))

function toggleCurrentPage() {
  const next = new Set(selectedKeys.value)
  if (pageAllChecked.value) {
    for (const key of currentPageKeys.value) {
      next.delete(key)
    }
  }
  else {
    for (const row of rows.value) {
      const key = keyOf(row)
      if (key) {
        labelCache.set(key, labelOf(row))
        next.add(key)
      }
    }
  }
  selectedKeys.value = next
  cacheVersion.value++
}

function confirm() {
  const keys = [...selectedKeys.value]
  emit('update:modelValue', keys)
  emit('change', keys)
  open.value = false
}

function removeKey(key: string) {
  const keys = props.modelValue.filter(item => item !== key)
  selectedKeys.value = new Set(keys)
  emit('update:modelValue', keys)
  emit('change', keys)
}

function clearAll() {
  selectedKeys.value = new Set()
  emit('update:modelValue', [])
  emit('change', [])
}

watch(page, fetchData)
watch(size, () => {
  page.value = 1
  fetchData()
})

defineExpose({ open: openPicker, reload, clear: clearAll })

const selectionColumn: TableColumn<T> = {
  id: '__yd_picker_selection__',
  header: '',
  width: 44,
  enableSorting: false,
}
const tableColumns = computed<TableColumn<T>[]>(() => [selectionColumn, ...props.columns])

/** 仅转发 FaTable 的列/空态插槽，filters 等业务插槽不进入表格 */
const slots = useSlots()
const tableSlots = computed(() => Object.keys(slots).filter(name => /^(?:cell|header|footer)-/.test(name) || ['empty', 'toolbar', 'caption'].includes(name)))
</script>

<template>
  <div
    class="yd-table-picker"
    :class="{ 'is-disabled': disabled }"
    role="button"
    :aria-disabled="disabled"
    @click="openPicker"
  >
    <div v-if="selectedLabels.length" class="yd-table-picker__tags">
      <FaTag
        v-for="item in selectedLabels"
        :key="item.key"
        closable
        class="yd-table-picker__tag"
        @close.stop="removeKey(item.key)"
      >
        {{ item.label }}
      </FaTag>
    </div>
    <span v-else class="yd-table-picker__placeholder">{{ placeholder }}</span>

    <span class="yd-table-picker__suffix">
      <button
        v-if="clearable && selectedLabels.length && !disabled"
        type="button"
        class="yd-table-picker__clear"
        aria-label="清空"
        @click.stop="clearAll"
      >
        <FaIcon name="i-ri:close-circle-fill" />
      </button>
      <FaIcon name="i-ri:search-line" class="yd-table-picker__trigger-icon" />
    </span>
  </div>

  <FaModal v-model="open" :title="title" class="max-w-[94vw] w-[720px]" content-class="flex flex-col gap-3">
    <div class="yd-table-picker__searchbar">
      <FaInput
        v-model="keyword"
        clearable
        :placeholder="searchPlaceholder"
        class="w-64"
        @keyup.enter="search"
      />
      <FaButton size="sm" @click="search">
        查询
      </FaButton>
      <FaButton size="sm" variant="outline" @click="resetSearch">
        重置
      </FaButton>
      <slot name="filters" :reload="reload" />
    </div>

    <div class="yd-table-picker__table">
      <FaTable
        border
        :columns="tableColumns"
        :data="rows"
        :row-key="rowKey"
        :empty-text="emptyText"
        @row-click="toggleRow"
      >
        <template #header-__yd_picker_selection__>
          <FaCheckbox
            v-if="multiple"
            :model-value="pageAllChecked ? true : pagePartiallyChecked ? 'indeterminate' : false"
            @click.stop
            @update:model-value="toggleCurrentPage"
          >
            <span class="sr-only">全选当前页</span>
          </FaCheckbox>
        </template>
        <template #cell-__yd_picker_selection__="{ row }">
          <FaCheckbox
            :model-value="selectedKeys.has(keyOf(row.original as T))"
            @click.stop
            @update:model-value="toggleRow(row.original as T)"
          >
            <span class="sr-only">选择 {{ labelOf(row.original as T) }}</span>
          </FaCheckbox>
        </template>
        <!-- 透传业务列自定义单元格插槽 -->
        <template v-for="name in tableSlots" :key="name" #[name]="slotProps">
          <slot :name="name" v-bind="slotProps ?? {}" />
        </template>
      </FaTable>
      <div v-if="loading" class="yd-table-picker__loading">
        <FaIcon name="i-ri:loader-4-line" class="animate-spin" />
      </div>
    </div>

    <div class="yd-table-picker__pager">
      <span class="yd-table-picker__selected-count">已选 {{ selectedKeys.size }} 项</span>
      <FaPagination v-model:page="page" v-model:size="size" :total="total" :sizes="pageSizes" layout="total, sizes, ->, pager" />
    </div>

    <template #footer>
      <div class="yd-table-picker__footer">
        <FaButton variant="outline" @click="open = false">
          取消
        </FaButton>
        <FaButton @click="confirm">
          确定
        </FaButton>
      </div>
    </template>
  </FaModal>
</template>

<style scoped>
.yd-table-picker {
  display: flex;
  min-height: 36px;
  width: 100%;
  cursor: pointer;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border: 1px solid var(--color-border-2);
  border-radius: var(--border-radius-medium, 6px);
  background: var(--color-bg-1);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.yd-table-picker:hover {
  border-color: var(--color-text-3);
}

.yd-table-picker.is-disabled {
  cursor: not-allowed;
  background: var(--color-fill-2);
  opacity: 0.6;
}

.yd-table-picker__tags {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
  gap: 4px;
  padding: 2px 0;
}

.yd-table-picker__tag {
  max-width: 180px;
}

.yd-table-picker__placeholder {
  flex: 1;
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-table-picker__suffix {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  color: var(--color-text-3);
}

.yd-table-picker__clear {
  display: flex;
  align-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
}

.yd-table-picker__clear:hover {
  color: var(--color-text-1);
}

.yd-table-picker__trigger-icon {
  font-size: 15px;
}

.yd-table-picker__searchbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.yd-table-picker__table {
  position: relative;
  height: 320px;
  overflow: auto;
}

.yd-table-picker__loading {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-1);
  color: var(--color-text-3);
  font-size: 20px;
  opacity: 0.7;
}

.yd-table-picker__pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.yd-table-picker__selected-count {
  color: var(--color-text-3);
  font-size: 12px;
}

.yd-table-picker__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>

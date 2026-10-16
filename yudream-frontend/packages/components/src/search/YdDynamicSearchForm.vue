<script setup lang="ts">
import type { SearchConfig, SearchFieldConfig, SearchOption, SearchOptionsSource } from './search-config-types'
import { computed, inject, onMounted, ref, watch } from 'vue'
import YdButton from '../basic/button/index.vue'
import YdCheckboxGroup from '../basic/checkbox-group/index.vue'
import YdIcon from '../basic/icon/index.vue'
import YdInput from '../basic/input/index.vue'
import YdNumberRange from '../basic/number-range/index.vue'
import YdRadioGroup from '../basic/radio-group/index.vue'
import YdRangePicker from '../basic/range-picker/index.vue'
import YdSelect from '../basic/select/index.vue'
import { tablePageSearchFoldKey } from '../layout/table-page-layout-context'
import { ControlType } from './search-config-types'

defineOptions({ name: 'YdDynamicSearchForm' })

const props = withDefaults(defineProps<YdDynamicSearchFormProps>(), {
  fold: false,
  showActions: true,
})

const emit = defineEmits<{
  search: []
  reset: []
}>()

export interface YdDynamicSearchFormProps {
  /** 搜索配置 */
  config?: SearchConfig
  /** 是否折叠 */
  fold?: boolean
  /** 选项数据源映射 */
  optionsSources?: SearchOptionsSource[]
  /** 是否显示操作按钮 */
  showActions?: boolean
  /**
   * 选项缓存重载键：值变化时清空选项缓存并重新加载。
   * 用于选项依赖其他搜索字段的级联场景（如部门变化后角色选项要收敛）。
   */
  reloadKey?: string | number
}

// 表单数据
const formData = defineModel<Record<string, any>>({ required: true })

// 选项缓存
const optionsCache = ref<Record<string, SearchOption[]>>({})

// 可见字段(根据fold状态筛选)
const visibleFields = computed(() => {
  if (!props.config?.fields) {
    return []
  }
  if (!props.fold) {
    return props.config.fields
  }
  return props.config.fields.filter(f => f.showWhenFolded)
})

// 按分组归类字段
const fieldsByGroup = computed(() => {
  const groups: Record<string, SearchFieldConfig[]> = { default: [] }

  for (const field of visibleFields.value) {
    const groupKey = field.group || 'default'
    if (!groups[groupKey]) {
      groups[groupKey] = []
    }
    groups[groupKey].push(field)
  }

  return groups
})

// 加载选项
async function loadOptions(field: SearchFieldConfig) {
  if (!field.optionsSource) {
    return
  }

  // 从缓存中获取
  if (optionsCache.value[field.optionsSource]) {
    return
  }

  // 查找数据源
  const source = props.optionsSources?.find(s => s.id === field.optionsSource)
  if (!source) {
    console.warn(`选项数据源未找到: ${field.optionsSource}`)
    return
  }

  try {
    const options = await source.loader()
    // 空结果不缓存：数据源可能因依赖数据未就绪而暂时为空
    if (options.length) {
      optionsCache.value[field.optionsSource] = options
    }
  }
  catch (error) {
    console.error(`加载选项失败: ${field.optionsSource}`, error)
  }
}

// 获取字段的选项列表
function getFieldOptions(field: SearchFieldConfig): SearchOption[] {
  // 显式声明的数据源优先(枚举/外键都建议显式声明,以便提供业务文案)
  if (field.optionsSource) {
    return optionsCache.value[field.optionsSource] || []
  }

  // Boolean 未声明数据源时给内置是/否,值用字符串以便直接提交给后端
  if (field.fieldType === 'Boolean' || field.fieldType === 'boolean') {
    return [
      { label: '是', value: 'true' },
      { label: '否', value: 'false' },
    ]
  }

  return []
}

/** 日期范围桥接：对外是 [start, end] 数组，落到表单上拆为 fieldNameStart/fieldNameEnd */
function getDateRange(field: SearchFieldConfig): (string | number)[] | undefined {
  const start = formData.value[`${field.fieldName}Start`]
  const end = formData.value[`${field.fieldName}End`]
  return start && end ? [start, end] : undefined
}

function setDateRange(field: SearchFieldConfig, value?: (string | number)[]) {
  formData.value[`${field.fieldName}Start`] = value?.[0]
  formData.value[`${field.fieldName}End`] = value?.[1]
}

/** 数值范围桥接：对外是 [min, max] 数组，落到表单上拆为 fieldNameMin/fieldNameMax */
function getNumberRange(field: SearchFieldConfig): (number | undefined)[] | undefined {
  const min = formData.value[`${field.fieldName}Min`]
  const max = formData.value[`${field.fieldName}Max`]
  return min === undefined && max === undefined ? undefined : [min, max]
}

function setNumberRange(field: SearchFieldConfig, value?: (number | undefined)[]) {
  formData.value[`${field.fieldName}Min`] = value?.[0]
  formData.value[`${field.fieldName}Max`] = value?.[1]
}

/** 由 YdTablePageLayout 注入：筛选/重置完成后自动收起搜索区（独立使用时为空） */
const collapseSearch = inject(tablePageSearchFoldKey, undefined)

// 处理搜索
function handleSearch() {
  emit('search')
  collapseSearch?.(true)
}

// 处理重置
function handleReset() {
  // 清空表单数据
  Object.keys(formData.value).forEach((key) => {
    delete formData.value[key]
  })
  emit('reset')
  collapseSearch?.(true)
}

// 监听配置变化,预加载选项
watch(
  () => props.config,
  (config) => {
    if (!config?.fields) {
      return
    }
    // 预加载所有需要选项的字段
    config.fields.forEach((field) => {
      if (field.optionsSource) {
        loadOptions(field)
      }
    })
  },
  { immediate: true },
)

/** 重载全部选项：清缓存后重新加载（级联场景由 reloadKey 触发） */
function reloadOptions() {
  optionsCache.value = {}
  if (!props.config?.fields) {
    return
  }
  props.config.fields.forEach((field) => {
    if (field.optionsSource) {
      loadOptions(field)
    }
  })
}

// 级联依赖变化：清空缓存重拉选项
watch(() => props.reloadKey, () => {
  reloadOptions()
})

onMounted(() => {
  // 初始加载所有选项
  if (props.config?.fields) {
    props.config.fields.forEach((field) => {
      if (field.optionsSource) {
        loadOptions(field)
      }
    })
  }
})
</script>

<template>
  <div class="yd-dynamic-search-form">
    <!-- 无配置时显示提示 -->
    <div v-if="!config || !config.fields || config.fields.length === 0" class="yd-dynamic-search-form__empty">
      <span class="text-sm text-muted-foreground">暂无搜索配置</span>
    </div>

    <!-- 搜索字段网格：所有控件等宽撑满列 -->
    <div v-else class="yd-dynamic-search-form__grid">
      <!-- 默认分组 -->
      <template v-for="field in fieldsByGroup.default" :key="field.fieldName">
        <div class="yd-dynamic-search-form__field">
          <label class="yd-dynamic-search-form__label">
            {{ field.label }}
          </label>

          <!-- TEXT 控件 -->
          <YdInput
            v-if="field.controlType === ControlType.TEXT"
            v-model="formData[field.fieldName]"
            class="w-full"
            :placeholder="field.placeholder || `请输入${field.label}`"
            clearable
          />

          <!-- SELECT 控件 -->
          <YdSelect
            v-else-if="field.controlType === ControlType.SELECT"
            v-model="formData[field.fieldName]"
            class="w-full"
            :options="getFieldOptions(field) as any"
            :placeholder="field.placeholder || `请选择${field.label}`"
            clearable
          />

          <!-- DATE_RANGE 控件 -->
          <YdRangePicker
            v-else-if="field.controlType === ControlType.DATE_RANGE"
            class="w-full"
            :model-value="getDateRange(field)"
            :placeholder="['开始日期', '结束日期']"
            allow-clear
            @update:model-value="(v: (string | number)[] | undefined) => setDateRange(field, v)"
          />

          <!-- NUMBER_RANGE 控件 -->
          <YdNumberRange
            v-else-if="field.controlType === ControlType.NUMBER_RANGE"
            class="w-full"
            :model-value="getNumberRange(field)"
            @update:model-value="(v: (number | undefined)[] | undefined) => setNumberRange(field, v)"
          />

          <!-- CHECKBOX 控件 -->
          <YdCheckboxGroup
            v-else-if="field.controlType === ControlType.CHECKBOX"
            v-model="formData[field.fieldName]"
            class="w-full"
            :options="getFieldOptions(field) as any"
          />

          <!-- RADIO 控件 -->
          <YdRadioGroup
            v-else-if="field.controlType === ControlType.RADIO"
            v-model="formData[field.fieldName]"
            class="w-full"
            :options="getFieldOptions(field) as any"
          />

          <!-- 默认TEXT控件 -->
          <YdInput
            v-else
            v-model="formData[field.fieldName]"
            class="w-full"
            :placeholder="field.placeholder || `请输入${field.label}`"
            clearable
          />

          <span v-if="field.hint" class="yd-dynamic-search-form__hint">
            {{ field.hint }}
          </span>
        </div>
      </template>

      <!-- 其他分组 -->
      <template v-for="(fields, groupName) in fieldsByGroup" :key="groupName">
        <template v-if="groupName !== 'default'">
          <div class="yd-dynamic-search-form__group-title">
            {{ groupName }}
          </div>
          <template v-for="field in fields" :key="field.fieldName">
            <div class="yd-dynamic-search-form__field">
              <label class="yd-dynamic-search-form__label">
                {{ field.label }}
              </label>

              <!-- TEXT 控件 -->
              <YdInput
                v-if="field.controlType === ControlType.TEXT"
                v-model="formData[field.fieldName]"
                class="w-full"
                :placeholder="field.placeholder || `请输入${field.label}`"
                clearable
              />

              <!-- SELECT 控件 -->
              <YdSelect
                v-else-if="field.controlType === ControlType.SELECT"
                v-model="formData[field.fieldName]"
                class="w-full"
                :options="getFieldOptions(field) as any"
                :placeholder="field.placeholder || `请选择${field.label}`"
                clearable
              />

              <!-- DATE_RANGE 控件 -->
              <YdRangePicker
                v-else-if="field.controlType === ControlType.DATE_RANGE"
                class="w-full"
                :model-value="getDateRange(field)"
                :placeholder="['开始日期', '结束日期']"
                allow-clear
                @update:model-value="(v: (string | number)[] | undefined) => setDateRange(field, v)"
              />

              <!-- NUMBER_RANGE 控件 -->
              <YdNumberRange
                v-else-if="field.controlType === ControlType.NUMBER_RANGE"
                class="w-full"
                :model-value="getNumberRange(field)"
                @update:model-value="(v: (number | undefined)[] | undefined) => setNumberRange(field, v)"
              />

              <!-- CHECKBOX 控件 -->
              <YdCheckboxGroup
                v-else-if="field.controlType === ControlType.CHECKBOX"
                v-model="formData[field.fieldName]"
                class="w-full"
                :options="getFieldOptions(field) as any"
              />

              <!-- RADIO 控件 -->
              <YdRadioGroup
                v-else-if="field.controlType === ControlType.RADIO"
                v-model="formData[field.fieldName]"
                class="w-full"
                :options="getFieldOptions(field) as any"
              />

              <!-- 默认TEXT控件 -->
              <YdInput
                v-else
                v-model="formData[field.fieldName]"
                class="w-full"
                :placeholder="field.placeholder || `请输入${field.label}`"
                clearable
              />

              <span v-if="field.hint" class="yd-dynamic-search-form__hint">
                {{ field.hint }}
              </span>
            </div>
          </template>
        </template>
      </template>

      <!-- 操作按钮 -->
      <div v-if="showActions" class="yd-dynamic-search-form__actions">
        <YdButton variant="outline" @click="handleReset">
          重置
        </YdButton>
        <YdButton @click="handleSearch">
          <YdIcon name="morph:search" />
          筛选
        </YdButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.yd-dynamic-search-form {
  width: 100%;
}

.yd-dynamic-search-form__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.yd-dynamic-search-form__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px 16px;
  align-items: start;
}

/* 窄一档退三列、再退两列、最后单列 */
@media (width <= 1280px) {
  .yd-dynamic-search-form__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (width <= 960px) {
  .yd-dynamic-search-form__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 640px) {
  .yd-dynamic-search-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.yd-dynamic-search-form__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.yd-dynamic-search-form__label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-2);
}

.yd-dynamic-search-form__hint {
  font-size: 12px;
  color: var(--color-text-3);
}

.yd-dynamic-search-form__group-title {
  padding-top: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-2);
  border-top: 1px solid var(--color-border-2);
  grid-column: 1 / -1;
}

.yd-dynamic-search-form__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  grid-column: 1 / -1;
  padding-top: 4px;
}

/* 响应式适配 */
@media (width <= 640px) {
  .yd-dynamic-search-form__actions {
    justify-content: stretch;
  }

  .yd-dynamic-search-form__actions > * {
    flex: 1;
  }
}
</style>

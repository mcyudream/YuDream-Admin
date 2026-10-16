<script setup lang="ts">
import type { SearchConfig, SearchFieldConfig, SearchOption, SearchOptionsSource } from './search-config-types'
import { computed, onMounted, ref, watch } from 'vue'
import YdButton from '../basic/button/index.vue'
import YdCheckboxGroup from '../basic/checkbox-group/index.vue'
import YdDatePicker from '../basic/date-picker/index.vue'
import YdIcon from '../basic/icon/index.vue'
import YdInput from '../basic/input/index.vue'
import YdNumberField from '../basic/number-field/index.vue'
import YdRadioGroup from '../basic/radio-group/index.vue'
import YdSelect from '../basic/select/index.vue'
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
    optionsCache.value[field.optionsSource] = options
  }
  catch (error) {
    console.error(`加载选项失败: ${field.optionsSource}`, error)
  }
}

// 获取字段的选项列表
function getFieldOptions(field: SearchFieldConfig): SearchOption[] {
  // Boolean类型的内置选项
  if (field.fieldType === 'Boolean' || field.fieldType === 'boolean') {
    return [
      { label: '是', value: 1 }, // 用数字代替boolean
      { label: '否', value: 0 },
    ]
  }

  // 枚举类型(需要从optionsSource获取)
  if (field.optionsSource) {
    return optionsCache.value[field.optionsSource] || []
  }

  return []
}

// 处理搜索
function handleSearch() {
  emit('search')
}

// 处理重置
function handleReset() {
  // 清空表单数据
  Object.keys(formData.value).forEach((key) => {
    delete formData.value[key]
  })
  emit('reset')
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

    <!-- 搜索字段网格 -->
    <div v-else class="yd-dynamic-search-form__grid">
      <!-- 默认分组 -->
      <template v-for="field in fieldsByGroup.default" :key="field.fieldName">
        <div
          class="yd-dynamic-search-form__field"
          :style="{ minWidth: field.width }"
        >
          <label class="yd-dynamic-search-form__label">
            {{ field.label }}
          </label>

          <!-- TEXT 控件 -->
          <YdInput
            v-if="field.controlType === ControlType.TEXT"
            v-model="formData[field.fieldName]"
            :placeholder="field.placeholder || `请输入${field.label}`"
            clearable
          />

          <!-- SELECT 控件 -->
          <YdSelect
            v-else-if="field.controlType === ControlType.SELECT"
            v-model="formData[field.fieldName]"
            :options="getFieldOptions(field) as any"
            :placeholder="field.placeholder || `请选择${field.label}`"
            clearable
          />

          <!-- DATE_RANGE 控件 -->
          <div v-else-if="field.controlType === ControlType.DATE_RANGE" class="flex gap-2 items-center">
            <YdDatePicker
              v-model="formData[`${field.fieldName}Start`]"
              placeholder="开始日期"
            />
            <span>至</span>
            <YdDatePicker
              v-model="formData[`${field.fieldName}End`]"
              placeholder="结束日期"
            />
          </div>

          <!-- NUMBER_RANGE 控件 -->
          <div v-else-if="field.controlType === ControlType.NUMBER_RANGE" class="flex gap-2 items-center">
            <YdNumberField
              v-model="formData[`${field.fieldName}Min`]"
              placeholder="最小值"
            />
            <span>至</span>
            <YdNumberField
              v-model="formData[`${field.fieldName}Max`]"
              placeholder="最大值"
            />
          </div>

          <!-- CHECKBOX 控件 -->
          <YdCheckboxGroup
            v-else-if="field.controlType === ControlType.CHECKBOX"
            v-model="formData[field.fieldName]"
            :options="getFieldOptions(field) as any"
          />

          <!-- RADIO 控件 -->
          <YdRadioGroup
            v-else-if="field.controlType === ControlType.RADIO"
            v-model="formData[field.fieldName]"
            :options="getFieldOptions(field) as any"
          />

          <!-- 默认TEXT控件 -->
          <YdInput
            v-else
            v-model="formData[field.fieldName]"
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
            <div
              class="yd-dynamic-search-form__field"
              :style="{ minWidth: field.width }"
            >
              <label class="yd-dynamic-search-form__label">
                {{ field.label }}
              </label>

              <!-- TEXT 控件 -->
              <YdInput
                v-if="field.controlType === ControlType.TEXT"
                v-model="formData[field.fieldName]"
                :placeholder="field.placeholder || `请输入${field.label}`"
                clearable
              />

              <!-- SELECT 控件 -->
              <YdSelect
                v-else-if="field.controlType === ControlType.SELECT"
                v-model="formData[field.fieldName]"
                :options="getFieldOptions(field) as any"
                :placeholder="field.placeholder || `请选择${field.label}`"
                clearable
              />

              <!-- DATE_RANGE 控件 -->
              <div v-else-if="field.controlType === ControlType.DATE_RANGE" class="flex gap-2 items-center">
                <YdDatePicker
                  v-model="formData[`${field.fieldName}Start`]"
                  placeholder="开始日期"
                />
                <span>至</span>
                <YdDatePicker
                  v-model="formData[`${field.fieldName}End`]"
                  placeholder="结束日期"
                />
              </div>

              <!-- NUMBER_RANGE 控件 -->
              <div v-else-if="field.controlType === ControlType.NUMBER_RANGE" class="flex gap-2 items-center">
                <YdNumberField
                  v-model="formData[`${field.fieldName}Min`]"
                  placeholder="最小值"
                />
                <span>至</span>
                <YdNumberField
                  v-model="formData[`${field.fieldName}Max`]"
                  placeholder="最大值"
                />
              </div>

              <!-- CHECKBOX 控件 -->
              <YdCheckboxGroup
                v-else-if="field.controlType === ControlType.CHECKBOX"
                v-model="formData[field.fieldName]"
                :options="getFieldOptions(field) as any"
              />

              <!-- RADIO 控件 -->
              <YdRadioGroup
                v-else-if="field.controlType === ControlType.RADIO"
                v-model="formData[field.fieldName]"
                :options="getFieldOptions(field) as any"
              />

              <!-- 默认TEXT控件 -->
              <YdInput
                v-else
                v-model="formData[field.fieldName]"
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
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px 12px;
  align-items: end;
}

.yd-dynamic-search-form__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
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
  grid-column: 1 / -1;
  padding-top: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-2);
  border-top: 1px solid var(--color-border-2);
}

.yd-dynamic-search-form__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  grid-column: 1 / -1;
}

/* 响应式适配 */
@media (width <= 768px) {
  .yd-dynamic-search-form__grid {
    grid-template-columns: 1fr;
  }

  .yd-dynamic-search-form__actions {
    justify-content: stretch;
  }

  .yd-dynamic-search-form__actions > * {
    flex: 1;
  }
}
</style>

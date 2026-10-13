<script setup lang="ts">
import type { TableColumn, YdTablePickerQuery, YdTablePickerResult } from '@yudream/components'
import { YdTablePicker } from '@yudream/components'
import { ref, watch } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const inputBasic = ref('')
const inputClearable = ref('可清空的内容')
const inputPassword = ref('')
const textareaValue = ref('')
const selectSingle = ref<string>()
const selectMultiple = ref<string[]>(['1', '3'])
const selectGrouped = ref<string[]>(['1', '5'])
const selectAllowSearch = ref(true)
const switchValue = ref(true)
const switchOff = ref(false)
const switchOn = ref(true)

const selectOptions = [
  { label: 'Vue 3', value: '1' },
  { label: 'TypeScript', value: '2' },
  { label: 'YD 组件', value: '3' },
]

const selectGroupedOptions = [
  { label: '前端技术', options: selectOptions },
  {
    label: '服务端技术',
    options: [
      { label: 'Java', value: '4' },
      { label: 'Node.js', value: '5' },
      { label: 'Redis（暂不可选）', value: '6', disabled: true },
    ],
  },
]

function clearSelectValues() {
  selectSingle.value = undefined
  selectMultiple.value = []
  selectGrouped.value = []
}

interface PluginItem {
  id: string
  name: string
  owner: string
  category: string
  version: string
  updatedAt: string
}

const pluginTerms = ['数据同步', '语义检索', '图谱投影', '消息推送', '权限审计', '表单引擎', '流程编排', '文档协作', '指标看板', '日志采集', '告警中心', '开放接口']
const pluginOwners = ['林舟', '陈念', '周衡', '沈若', '梁一', '苏眠']
const pluginCategories = ['数据', '集成', '效率', '安全']

const pluginPool: PluginItem[] = Array.from({ length: 87 }, (_, i) => ({
  id: `p-${String(i + 1).padStart(3, '0')}`,
  name: `${pluginTerms[i % pluginTerms.length]} ${String(i + 1).padStart(2, '0')}`,
  owner: pluginOwners[i % pluginOwners.length],
  category: pluginCategories[i % pluginCategories.length],
  version: `v1.${i % 9}.${i % 5}`,
  updatedAt: `2026-0${(i % 8) + 1}-${String((i % 27) + 1).padStart(2, '0')}`,
}))

const pickerColumns: TableColumn<PluginItem>[] = [
  { accessorKey: 'name', header: '名称' },
  { accessorKey: 'owner', header: '负责人', width: 100 },
  { accessorKey: 'category', header: '分类', width: 100 },
  { accessorKey: 'version', header: '版本', width: 100 },
  { accessorKey: 'updatedAt', header: '更新时间', width: 120 },
]

const pickerCategory = ref('')

/** 模拟后端：延迟 + 关键字/分类过滤 + 服务端分页 */
async function pluginFetcher(query: YdTablePickerQuery): Promise<YdTablePickerResult<PluginItem>> {
  await new Promise(resolve => setTimeout(resolve, 320))
  let list = pluginPool
  if (query.keyword) {
    list = list.filter(item => item.name.includes(query.keyword) || item.owner.includes(query.keyword))
  }
  if (pickerCategory.value) {
    list = list.filter(item => item.category === pickerCategory.value)
  }
  const start = (query.page - 1) * query.size
  return { list: list.slice(start, start + query.size), total: list.length }
}

const pickerMulti = ref<string[]>(['p-003', 'p-018'])
const pickerSingle = ref<string[]>([])
const multiPickerRef = ref<{ reload: () => void } | null>(null)

watch(pickerCategory, () => multiPickerRef.value?.reload())

const pickerCode = `<YdTablePicker
  v-model="keys"
  :columns="columns"
  :fetcher="fetcher"
  row-key="id"
  label-key="name"
  title="选择插件"
  :initial-labels="{ 'p-003': '数据同步 03' }"
/>
<!-- fetcher: ({ page, size, keyword }) => Promise<{ list, total }> -->`
</script>

<template>
  <DemoCard title="YdInput" description="基础 / 可清空 / 密码 / 禁用">
    <div class="gap-3 grid max-w-md">
      <YdInput v-model="inputBasic" placeholder="请输入内容" />
      <YdInput v-model="inputClearable" clearable placeholder="可清空" />
      <YdInput v-model="inputPassword" type="password" placeholder="请输入密码" />
      <YdInput model-value="禁用状态" disabled />
    </div>
  </DemoCard>

  <DemoCard title="YdTextarea" description="基础 / 禁用">
    <div class="gap-3 grid max-w-md">
      <YdTextarea v-model="textareaValue" :rows="3" placeholder="请输入多行内容" />
      <YdTextarea model-value="禁用状态的多行文本" :rows="2" disabled />
    </div>
  </DemoCard>

  <DemoCard title="YdSelect" description="没有选择 / 多选标签回显 / 可选前端搜索 / 分组 / 禁用">
    <div class="gap-4 grid max-w-md">
      <div class="flex flex-wrap gap-3 items-center justify-between">
        <YdLabel class="text-sm flex gap-2 items-center">
          <YdSwitch v-model="selectAllowSearch" aria-label="启用分组多选的前端搜索" />
          启用前端搜索
        </YdLabel>
        <YdButton size="sm" variant="outline" aria-label="清空单选和多选示例的全部绑定值" @click="clearSelectValues">
          清空全部选择
        </YdButton>
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">单选（初始为空）</span>
        <YdSelect v-model="selectSingle" :options="selectOptions" aria-label="技术单选空态示例" />
        <output class="text-xs text-muted-foreground" aria-live="polite">实际绑定值：{{ JSON.stringify(selectSingle) ?? 'undefined' }}</output>
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">多选（已选标签回显）</span>
        <YdSelect v-model="selectMultiple" :options="selectOptions" multiple aria-label="技术多选标签回显示例" />
        <output class="text-xs text-muted-foreground break-words" aria-live="polite">实际绑定值：{{ JSON.stringify(selectMultiple) }}</output>
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">分组多选（{{ selectAllowSearch ? '允许搜索' : '关闭搜索' }}）</span>
        <YdSelect
          v-model="selectGrouped"
          :options="selectGroupedOptions"
          multiple
          :allow-search="selectAllowSearch"
          search-placeholder="搜索前端或服务端技术"
          empty-text="没有匹配的技术"
          aria-label="可搜索的技术分组多选示例"
        />
        <output class="text-xs text-muted-foreground break-words" aria-live="polite">实际绑定值：{{ JSON.stringify(selectGrouped) }}</output>
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">禁用单选</span>
        <YdSelect model-value="1" :options="selectOptions" disabled aria-label="禁用的技术单选示例" />
      </div>
    </div>
  </DemoCard>

  <DemoCard title="YdSwitch" description="基础 / 禁用">
    <div class="demo-row">
      <YdSwitch v-model="switchValue" />
      <YdSwitch v-model="switchOff" disabled />
      <YdSwitch v-model="switchOn" disabled />
    </div>
  </DemoCard>

  <DemoCard
    title="YdTablePicker"
    description="弹出式选择输入框：表面为多元素选择框，点击弹出带表格、关键字搜索、翻页的动态数据选择窗；filters 插槽可扩展筛选条件"
    :code="pickerCode"
  >
    <div class="gap-4 grid max-w-xl">
      <div>
        <div class="picker-label">
          多选（带回显 + 分类筛选）
        </div>
        <YdTablePicker
          ref="multiPickerRef"
          v-model="pickerMulti"
          :columns="pickerColumns"
          :fetcher="pluginFetcher"
          row-key="id"
          label-key="name"
          title="选择插件"
          placeholder="点击选择插件"
          :initial-labels="{ 'p-003': pluginPool[2].name, 'p-018': pluginPool[17].name }"
        >
          <template #filters>
            <YdSelect
              v-model="pickerCategory"
              :options="[{ label: '全部分类', value: '' }, ...pluginCategories.map(item => ({ label: item, value: item }))]"
              class="w-32"
            />
          </template>
        </YdTablePicker>
        <div class="picker-echo">
          已选：{{ pickerMulti.join('、') || '无' }}
        </div>
      </div>

      <div>
        <div class="picker-label">
          单选
        </div>
        <YdTablePicker
          v-model="pickerSingle"
          :columns="pickerColumns"
          :fetcher="pluginFetcher"
          row-key="id"
          label-key="name"
          title="选择负责人"
          placeholder="点击选择"
          :multiple="false"
        />
        <div class="picker-echo">
          已选：{{ pickerSingle.join('、') || '无' }}
        </div>
      </div>
    </div>
  </DemoCard>
</template>

<style scoped>
.picker-label {
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--color-text-2);
}

.picker-echo {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-3);
  word-break: break-all;
}
</style>

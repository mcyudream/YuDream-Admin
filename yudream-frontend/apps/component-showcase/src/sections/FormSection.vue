<script setup lang="ts">
import type { TableColumn, YdTablePickerQuery, YdTablePickerResult } from '@yudream/components'
import { YdTablePicker } from '@yudream/components'
import { ref, watch } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const inputBasic = ref('')
const inputClearable = ref('可清空的内容')
const inputPassword = ref('')
const textareaValue = ref('')
const selectSingle = ref('1')
const selectMultiple = ref<unknown[]>([])
const switchValue = ref(true)
const switchOff = ref(false)
const switchOn = ref(true)

const selectOptions = [
  { label: 'Vue 3', value: '1' },
  { label: 'TypeScript', value: '2' },
  { label: 'Arco Design', value: '3' },
]

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
  <DemoCard title="FaInput" description="基础 / 可清空 / 密码 / 禁用">
    <div class="gap-3 grid max-w-md">
      <FaInput v-model="inputBasic" placeholder="请输入内容" />
      <FaInput v-model="inputClearable" clearable placeholder="可清空" />
      <FaInput v-model="inputPassword" type="password" placeholder="请输入密码" />
      <FaInput model-value="禁用状态" disabled />
    </div>
  </DemoCard>

  <DemoCard title="FaTextarea" description="基础 / 禁用">
    <div class="gap-3 grid max-w-md">
      <FaTextarea v-model="textareaValue" :rows="3" placeholder="请输入多行内容" />
      <FaTextarea model-value="禁用状态的多行文本" :rows="2" disabled />
    </div>
  </DemoCard>

  <DemoCard title="FaSelect" description="单选 / 多选 / 禁用">
    <div class="gap-3 grid max-w-md">
      <FaSelect v-model="selectSingle" :options="selectOptions" />
      <FaSelect v-model="selectMultiple" :options="selectOptions" multiple />
      <FaSelect model-value="1" :options="selectOptions" disabled />
    </div>
  </DemoCard>

  <DemoCard title="FaSwitch" description="基础 / 禁用">
    <div class="demo-row">
      <FaSwitch v-model="switchValue" />
      <FaSwitch v-model="switchOff" disabled />
      <FaSwitch v-model="switchOn" disabled />
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
            <FaSelect
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
  color: var(--color-text-2);
  font-size: 12px;
}

.picker-echo {
  margin-top: 6px;
  color: var(--color-text-3);
  font-size: 12px;
  word-break: break-all;
}
</style>

<script setup lang="ts">
import type { TableColumn, YdTablePickerQuery, YdTablePickerResult } from '@yudream/components'
import { YdTablePicker } from '@yudream/components'
import { ref } from 'vue'

interface PluginItem {
  id: string
  name: string
  owner: string
  category: string
  version: string
  updatedAt: string
}

const terms = ['数据同步', '语义检索', '图谱投影', '消息推送', '权限审计', '表单引擎', '流程编排', '文档协作', '指标看板', '日志采集', '告警中心', '开放接口']
const owners = ['林舟', '陈念', '周衡', '沈若', '梁一', '苏眠']
const categories = ['数据', '集成', '效率', '安全']

const pool: PluginItem[] = Array.from({ length: 87 }, (_, i) => ({
  id: `p-${String(i + 1).padStart(3, '0')}`,
  name: `${terms[i % terms.length]} ${String(i + 1).padStart(2, '0')}`,
  owner: owners[i % owners.length],
  category: categories[i % categories.length],
  version: `v1.${i % 9}.${i % 5}`,
  updatedAt: `2026-0${(i % 8) + 1}-${String((i % 27) + 1).padStart(2, '0')}`,
}))

const columns: TableColumn<PluginItem>[] = [
  { accessorKey: 'name', header: '名称' },
  { accessorKey: 'owner', header: '负责人', width: 100 },
  { accessorKey: 'category', header: '分类', width: 100 },
  { accessorKey: 'version', header: '版本', width: 100 },
  { accessorKey: 'updatedAt', header: '更新时间', width: 120 },
]

/** 模拟后端取数：延迟 + 关键字过滤 + 服务端分页 */
async function fetcher(query: YdTablePickerQuery): Promise<YdTablePickerResult<PluginItem>> {
  await new Promise(resolve => setTimeout(resolve, 300))
  const list = query.keyword
    ? pool.filter(item => item.name.includes(query.keyword) || item.owner.includes(query.keyword))
    : pool
  const start = (query.page - 1) * query.size
  return { list: list.slice(start, start + query.size), total: list.length }
}

const selected = ref<string[]>(['p-003', 'p-018'])
</script>

<template>
  <div style="display: grid; max-width: 560px; gap: 8px;">
    <YdTablePicker
      v-model="selected"
      :columns="columns"
      :fetcher="fetcher"
      row-key="id"
      label-key="name"
      title="选择插件"
      placeholder="点击选择插件"
      :initial-labels="{ 'p-003': pool[2].name, 'p-018': pool[17].name }"
    />
    <span style="color: var(--color-text-3, var(--vp-c-text-2)); font-size: 12px; word-break: break-all;">
      已选 key：{{ selected.join('、') || '无' }}
    </span>
  </div>
</template>

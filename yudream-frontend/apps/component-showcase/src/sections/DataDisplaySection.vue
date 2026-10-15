<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const descriptions = [
  { label: '用户名', value: 'Husky' },
  { label: '用户 ID', value: '9007199254740993' },
  { label: '邮箱', value: 'husky@yudream.online' },
  { label: '部门', value: '平台研发部' },
  { label: '状态', value: '正常' },
  { label: '备注', value: '', span: 2 },
]

const keyword = ref('')
const searchFold = ref(false)

interface Member {
  id: string
  name: string
  role: string
  status: string
  lastActive: string
}

const tableData: Member[] = [
  { id: '9007199254740993', name: '林舟', role: '管理员', status: '正常', lastActive: '2026-10-15 09:25' },
  { id: '9007199254740994', name: '陈念', role: '编辑', status: '正常', lastActive: '2026-10-14 18:02' },
  { id: '9007199254740995', name: '周衡', role: '编辑', status: '停用', lastActive: '2026-09-30 11:40' },
  { id: '9007199254740996', name: '沈若', role: '访客', status: '正常', lastActive: '2026-10-13 16:55' },
]

const columns: TableColumn<Member>[] = [
  { accessorKey: 'name', header: '姓名' },
  { accessorKey: 'role', header: '角色' },
  { accessorKey: 'status', header: '状态' },
  { accessorKey: 'lastActive', header: '最近活跃' },
]
</script>

<template>
  <DemoCard title="YdDescriptions" description="只读描述列表：items 配置条目，column / border / direction 控制布局，空值显示 emptyText">
    <div class="gap-6 grid">
      <YdDescriptions :items="descriptions" :column="2" border size="sm" />
      <YdDescriptions :items="descriptions.slice(0, 3)" :column="3" direction="vertical" />
    </div>
  </DemoCard>

  <DemoCard title="YdTrend" description="趋势标识：value 为必填字符串，type up / down，reverse 反转颜色语义，variant 切换风格">
    <div class="flex flex-wrap gap-x-8 gap-y-3 items-center">
      <span class="text-sm flex gap-2 items-center">
        访问量 <YdTrend value="12.5%" type="up" />
      </span>
      <span class="text-sm flex gap-2 items-center">
        转化率 <YdTrend value="3.2%" type="down" />
      </span>
      <span class="text-sm flex gap-2 items-center">
        错误率（reverse：下降是好事） <YdTrend value="1.8%" type="down" reverse />
      </span>
      <YdTrend value="8.4%" type="up" variant="filled" />
      <YdTrend value="2.1%" type="down" variant="soft" size="large" />
    </div>
  </DemoCard>

  <DemoCard title="YdTable" description="桌面表格：TanStack 列契约（accessorKey / header），sortable 开启排序，stripe 斑马纹">
    <YdTable
      :columns="columns"
      :data="tableData"
      row-key="id"
      stripe
      sortable
      empty-text="暂无成员"
    >
      <template #cell-status="{ row }">
        <YdTag :variant="row.original.status === '正常' ? 'secondary' : 'destructive'">
          {{ row.original.status }}
        </YdTag>
      </template>
    </YdTable>
  </DemoCard>

  <DemoCard title="YdSearchBar" description="筛选区容器（另见页面布局页）：这里演示配合表格的完整查询区">
    <YdSearchBar v-model:fold="searchFold" background>
      <template #default="{ fold }">
        <div class="gap-3 grid sm:grid-cols-3">
          <YdInput v-model="keyword" placeholder="按姓名搜索" clearable />
          <YdSelect
            v-if="!fold"
            :options="[{ label: '全部角色', value: '' }, { label: '管理员', value: '管理员' }, { label: '编辑', value: '编辑' }]"
            placeholder="角色"
          />
          <div v-if="!fold" class="flex gap-2 items-center">
            <YdButton size="sm">
              查询
            </YdButton>
            <YdButton size="sm" variant="outline">
              重置
            </YdButton>
          </div>
        </div>
      </template>
    </YdSearchBar>
  </DemoCard>
</template>

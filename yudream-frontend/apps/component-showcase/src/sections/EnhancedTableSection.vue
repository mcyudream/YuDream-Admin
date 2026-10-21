<script setup lang="ts">
import type { ActionMenuItem, EnhancedTableProps, StatusColumn, UserColumn } from '@yudream/components'
import { useYdToast, YdEnhancedTable, YdResponsiveEnhancedTable, YdSegmented } from '@yudream/components'
import { computed, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const toast = useYdToast()

interface Member {
  id: string
  username: string
  name: string
  email: string
  phone: string
  status: 'active' | 'suspended' | 'invited'
  role: string
  avatar?: string
  trace?: string
}

const members = ref<Member[]>([
  {
    id: '9007199254740993',
    username: 'linzhou',
    name: '林舟',
    email: 'linzhou@yudream.online',
    phone: '+86 138 0000 0001',
    status: 'active',
    role: '管理员',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Lin',
    trace: '9f2c7a1e4b6d8f0a3c5e7b9d1f2a4c6e8b0d2f4a6c8e0b2d4f6a8c0e2b4d6f8a',
  },
  {
    id: '9007199254740994',
    username: 'chennian',
    name: '陈念',
    email: 'chennian@yudream.online',
    phone: '+86 138 0000 0002',
    status: 'suspended',
    role: '编辑',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Chen',
  },
  {
    id: '9007199254740995',
    username: 'zhouheng',
    name: '周衡',
    email: 'zhouheng@yudream.online',
    phone: '+86 138 0000 0003',
    status: 'invited',
    role: '编辑',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Zhou',
  },
  {
    id: '9007199254740996',
    username: 'shenruo',
    name: '沈若',
    email: 'shenruo@yudream.online',
    phone: '+86 138 0000 0004',
    status: 'active',
    role: '访客',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Shen',
  },
])

/** 用户信息列：头像 + 姓名 + 邮箱组合 */
const userColumn: UserColumn<Member> = {
  nameKey: 'name',
  emailKey: 'email',
  avatarKey: 'avatar',
  header: '成员',
  width: 280,
}

/** 状态列：值 -> 徽章文案与变体 */
const statusColumn: StatusColumn<Member> = {
  statusKey: 'status',
  header: '状态',
  statusMap: {
    active: { label: '正常', variant: 'default' },
    suspended: { label: '已停用', variant: 'destructive' },
    invited: { label: '待加入', variant: 'secondary' },
  },
  width: 110,
}

/** 操作菜单：支持按行禁用，danger 标记危险项 */
const actions: ActionMenuItem<Member>[] = [
  {
    label: '编辑成员',
    icon: 'morph:edit',
    disabled: row => row.status === 'suspended',
    onClick: row => toast.success(`编辑：${row.name}`),
  },
  {
    label: '查看资料',
    icon: 'morph:user',
    onClick: row => toast(`查看：${row.name}`),
  },
  {
    label: '重置密码',
    icon: 'morph:key',
    disabled: row => row.status !== 'active',
    onClick: row => toast.success(`已向 ${row.email} 发送重置链接`),
  },
  {
    label: '移除成员',
    icon: 'morph:trash',
    danger: true,
    disabled: row => row.role === '管理员',
    onClick: row => toast.error(`已移除：${row.name}`),
  },
]

const columns = [
  { accessorKey: 'username', header: '账号', width: 160 },
  { accessorKey: 'phone', header: '手机号', width: 170 },
  { accessorKey: 'role', header: '角色', width: 110 },
]

/** 固定列演示：宽表列，总宽超出内容区后才能看到固定列效果 */
const fixedColumns: EnhancedTableProps<Member>['columns'] = [
  ...columns,
  { accessorKey: 'email', header: '邮箱', width: 240 },
  { accessorKey: 'id', header: '用户 ID', width: 200 },
  { accessorKey: 'trace', header: '追踪 ID', width: 180 },
]

type UserColumnFixedMode = 'left' | 'right' | 'none'

const fixedMode = ref<UserColumnFixedMode>('left')
const fixedOptions: { label: string, value: UserColumnFixedMode }[] = [
  { label: '左固定（默认）', value: 'left' },
  { label: '右固定', value: 'right' },
  { label: '不固定', value: 'none' },
]

/** userColumn.fixed：默认固定在左侧，false 取消固定，也可固定在右侧 */
const fixedUserColumn = computed<UserColumn<Member>>(() => ({
  ...userColumn,
  fixed: fixedMode.value === 'none' ? false : fixedMode.value,
}))

/** 行级可见性与动态文案：停用/启用按行状态切换，无权限的项直接不出现 */
const responsiveActions: ActionMenuItem<Member>[] = [
  {
    label: row => row.status === 'suspended' ? '恢复成员' : '停用成员',
    icon: 'morph:ban',
    visible: row => row.role !== '管理员',
    onClick: row => toast.success(`已切换状态：${row.name}`),
  },
  {
    label: '查看资料',
    icon: 'morph:user',
    onClick: row => toast(`查看：${row.name}`),
  },
  {
    label: '移除成员',
    icon: 'morph:trash',
    danger: true,
    visible: row => row.role !== '管理员',
    onClick: row => toast.error(`已移除：${row.name}`),
  },
]
</script>

<template>
  <DemoCard
    title="YdEnhancedTable"
    description="图文并茂表格：userColumn 组合头像/姓名/邮箱，statusColumn 映射状态徽章，actions 收纳行操作下拉"
  >
    <YdEnhancedTable
      :data="members"
      :user-column="userColumn"
      :status-column="statusColumn"
      :columns="columns"
      :actions="actions"
      row-key="id"

      stripe border
    />
    <ul class="text-xs text-muted-foreground mt-3 space-y-1">
      <li>已停用的成员不能编辑；非正常状态不能重置密码；管理员不能被移除。</li>
      <li>移动端列表使用 <code>YdResponsiveEnhancedTable</code>：窄屏自动切换卡片，本组桌面表格即其桌面形态。</li>
    </ul>
  </DemoCard>

  <DemoCard
    title="YdResponsiveEnhancedTable"
    description="增强表格的响应式版本：桌面仍是增强表格，768px 以下切换为卡片；卡片按桌面可见列渲染 cell 插槽与 cell 渲染函数"
  >
    <YdResponsiveEnhancedTable
      :data="members"
      :user-column="userColumn"
      :status-column="statusColumn"
      :columns="columns"
      :actions="responsiveActions"
      :card-hidden-columns="['phone']"
      row-key="id"
    />
    <ul class="text-xs text-muted-foreground mt-3 space-y-1">
      <li><code>cardHiddenColumns</code> 可在卡片里隐藏宽列（本例隐藏手机号），列显示开关也同步生效。</li>
      <li><code>actions</code> 的 label 可传函数、visible 可做行级隐藏，卡片底部与桌面下拉保持一致。</li>
      <li>需要完全自绘卡片时用 <code>#card</code> 插槽，与 <code>YdResponsiveTable</code> 的使用方式一致。</li>
    </ul>
  </DemoCard>

  <DemoCard
    title="YdEnhancedTable · 固定用户列"
    description="userColumn.fixed 控制用户列固定位置：默认左固定，false 取消固定，也可固定在右侧；操作列保持固定右侧"
  >
    <YdSegmented v-model="fixedMode" :options="fixedOptions" size="sm" aria-label="用户列固定方式" />
    <YdEnhancedTable
      :data="members"
      :user-column="fixedUserColumn"
      :status-column="statusColumn"
      :columns="fixedColumns"
      :actions="actions"
      row-key="id"
      class="min-w-0"

      stripe border
    />
    <ul class="text-xs text-muted-foreground mt-3 space-y-1">
      <li>横向滚动这张宽表：用户列默认吸附在左侧并带边缘阴影，切到「不固定」后随内容一起滚动，「右固定」则吸附在右侧操作列之前。</li>
      <li>单元格默认可换行：长 ID/哈希（如「追踪 ID」）在列宽内折行，不再溢出覆盖相邻列；只需单行的列在 <code>cellClass</code> 传 <code>whitespace-nowrap</code>。</li>
    </ul>
  </DemoCard>
</template>

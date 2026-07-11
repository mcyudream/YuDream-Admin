<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { QqBindingCode, QqBindingPolicy, SystemCommand } from '@/api/modules/system-command'
import apiCommands from '@/api/modules/system-command'

const toast = useFaToast()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SystemCommand[]>([])
const policy = reactive<QqBindingPolicy>({ requireBoundQq: false, lockProfileQq: false })
const bindingUserId = ref('')
const bindingCode = ref<QqBindingCode | null>(null)
const issuingCode = ref(false)
const columns = computed<TableColumn<SystemCommand>[]>(() => [
  { id: 'command', header: '命令', width: 150 },
  { accessorKey: 'name', header: '名称', width: 180 },
  { id: 'permission', header: '权限码', width: 260 },
  { accessorKey: 'pluginCode', header: '插件', width: 170 },
  { accessorKey: 'description', header: '说明', minWidth: 240 },
  { id: 'anonymous', header: '匿名', width: 100, align: 'center' },
])

async function load() {
  loading.value = true
  try {
    const [commands, bindingPolicy] = await Promise.all([apiCommands.list(), apiCommands.policy()])
    rows.value = commands.data
    Object.assign(policy, bindingPolicy.data)
  } finally { loading.value = false }
}
async function savePolicy(value: boolean) {
  saving.value = true
  try { Object.assign(policy, (await apiCommands.updatePolicy(value)).data); toast.success('QQ 绑定策略已保存') }
  finally { saving.value = false }
}
async function issueBindingCode() {
  if (!bindingUserId.value.trim()) { toast.error('请输入系统用户 ID'); return }
  issuingCode.value = true
  try {
    bindingCode.value = (await apiCommands.issueQqBindingCode(bindingUserId.value.trim())).data
    toast.success('绑定码已生成')
  } finally { issuingCode.value = false }
}
onMounted(load)
</script>

<template>
  <div>
    <FaPageHeader title="指令管理" class="mb-0" />
    <FaPageMain>
      <div class="mb-4 flex items-center justify-between border border-[var(--color-border-2)] p-4">
        <div><div class="font-medium">要求绑定 QQ</div><div class="text-sm text-muted-foreground">开启后，未绑定 QQ 仅可使用绑定指令，且个人资料不可修改 QQ。</div></div>
        <a-switch :model-value="policy.requireBoundQq" :loading="saving" @change="savePolicy(Boolean($event))" />
      </div>
      <div class="mb-4 border border-[var(--color-border-2)] p-4">
        <div class="mb-3 font-medium">生成 QQ 绑定码</div>
        <div class="flex flex-wrap items-center gap-3">
          <FaInput v-model="bindingUserId" class="w-72" placeholder="输入系统用户 ID" />
          <FaButton :loading="issuingCode" @click="issueBindingCode"><FaIcon name="i-ri:key-2-line" />生成绑定码</FaButton>
          <template v-if="bindingCode"><code class="rounded bg-[var(--color-fill-1)] px-3 py-2 text-base font-semibold">{{ bindingCode.code }}</code><span class="text-sm text-muted-foreground">有效至 {{ bindingCode.expiresAt }}</span></template>
        </div>
      </div>
      <FaTable :columns="columns" :data="rows" :loading="loading" row-key="code" border stripe>
        <template #cell-command="{ row }"><code>/{{ row.original.command }}</code></template>
        <template #cell-permission="{ row }">{{ row.original.permission || '无需权限' }}</template>
        <template #cell-anonymous="{ row }"><FaTag :variant="row.original.allowAnonymous ? 'success' : 'secondary'">{{ row.original.allowAnonymous ? '允许' : '不允许' }}</FaTag></template>
      </FaTable>
    </FaPageMain>
  </div>
</template>

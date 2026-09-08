<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { MessagingBindingCode, MessagingBindingTarget, QqBindingPolicy, SystemCommand } from '@/api/modules/system-command'
import apiCommands from '@/api/modules/system-command'

const toast = useFaToast()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SystemCommand[]>([])
const policy = reactive<QqBindingPolicy>({ requireBoundQq: false, lockProfileQq: false })
const bindingUserId = ref('')
const bindingTargets = ref<MessagingBindingTarget[]>([])
const loadingTargets = ref(false)
const bindingCode = ref<MessagingBindingCode | null>(null)
const issuingCode = ref(false)
const issuingConnectionId = ref('')
const columns = computed<TableColumn<SystemCommand>[]>(() => [
  { id: 'command', header: '命令', width: 150 },
  { accessorKey: 'name', header: '名称', width: 180 },
  { id: 'permission', header: '权限码', width: 260 },
  { accessorKey: 'pluginCode', header: '插件', width: 170 },
  { accessorKey: 'description', header: '说明', minWidth: 240 },
  { id: 'anonymous', header: '匿名', width: 100, align: 'center' },
])

function protocolLabel(protocol?: string) {
  if (protocol === 'official') {
    return '官方 QQ 机器人'
  }
  if (protocol === 'milky') {
    return 'Milky'
  }
  return protocol || '消息协议'
}

function bindingTargetLabel(target: { botName?: string; name?: string; connectionName?: string }) {
  return target.botName?.trim() || target.connectionName?.trim() || target.name?.trim() || '未命名连接'
}

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
  try { Object.assign(policy, (await apiCommands.updatePolicy(value)).data); toast.success('消息绑定策略已保存') }
  finally { saving.value = false }
}
async function loadBindingTargets() {
  if (!bindingUserId.value.trim()) {
    toast.error('请输入系统用户 ID')
    return
  }
  loadingTargets.value = true
  bindingCode.value = null
  try {
    bindingTargets.value = (await apiCommands.listMessagingBindingTargets(bindingUserId.value.trim())).data || []
    if (!bindingTargets.value.length) {
      toast.info('该用户暂无已启用的机器人连接')
    }
  }
  finally { loadingTargets.value = false }
}
async function issueBindingCode(connectionId: string) {
  if (!bindingUserId.value.trim()) { toast.error('请输入系统用户 ID'); return }
  issuingCode.value = true
  issuingConnectionId.value = connectionId
  try {
    bindingCode.value = (await apiCommands.issueMessagingBindingCode(bindingUserId.value.trim(), connectionId)).data
    toast.success('绑定码已生成')
  } finally {
    issuingCode.value = false
    issuingConnectionId.value = ''
  }
}
onMounted(load)
</script>

<template>
  <div>
    <FaPageHeader title="指令管理" class="mb-0" />
    <FaPageMain>
      <div class="mb-4 flex items-center justify-between border border-[var(--color-border-2)] p-4">
        <div><div class="font-medium">要求绑定消息身份</div><div class="text-sm text-muted-foreground">开启后，未绑定 Milky QQ 或官方机器人身份的用户仅可使用绑定指令。官方绑定写入协议身份，不会覆盖 QQ 号。</div></div>
        <a-switch :model-value="policy.requireBoundQq" :loading="saving" @change="savePolicy(Boolean($event))" />
      </div>
      <div class="mb-4 border border-[var(--color-border-2)] p-4">
        <div class="mb-3 font-medium">按机器人连接生成绑定码</div>
        <div class="mb-3 text-sm text-muted-foreground">先选择系统用户，再按已启用连接分别生成绑定码。用户需向对应机器人发送 `/绑定 绑定码`。</div>
        <div class="flex flex-wrap items-center gap-3">
          <FaInput v-model="bindingUserId" class="w-72" placeholder="输入系统用户 ID" />
          <FaButton :loading="loadingTargets" @click="loadBindingTargets"><FaIcon name="i-ri:robot-2-line" />加载连接</FaButton>
        </div>
        <div v-if="bindingCode" class="mt-3 flex flex-wrap items-center gap-3">
          <code class="rounded bg-[var(--color-fill-1)] px-3 py-2 text-base font-semibold">{{ bindingCode.code }}</code>
          <span class="text-sm text-muted-foreground">向「{{ bindingTargetLabel(bindingCode) }}」发送 `/绑定 {{ bindingCode.code }}`，有效至 {{ bindingCode.expiresAt }}</span>
        </div>
        <div v-if="bindingTargets.length" class="mt-3 grid gap-3">
          <div v-for="target in bindingTargets" :key="target.connectionId" class="flex flex-wrap items-center justify-between gap-3 border border-[var(--color-border-2)] px-3 py-2">
            <div class="min-w-0">
              <div class="font-medium">{{ bindingTargetLabel(target) }}</div>
              <div class="text-sm text-muted-foreground">{{ protocolLabel(target.protocol) }}{{ target.botName && target.name && target.botName !== target.name ? ` · ${target.name}` : '' }}</div>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <FaTag :variant="target.bound ? 'default' : 'secondary'">{{ target.bound ? '已绑定' : '未绑定' }}</FaTag>
              <FaButton size="sm" :loading="issuingCode && issuingConnectionId === target.connectionId" @click="issueBindingCode(target.connectionId)">
                <FaIcon name="i-ri:key-2-line" />生成绑定码
              </FaButton>
            </div>
          </div>
        </div>
      </div>
      <FaResponsiveTable :columns="columns" :data="rows" :loading="loading" row-key="code" border stripe>
        <template #cell-command="{ row }"><code>/{{ row.original.command }}</code></template>
        <template #cell-permission="{ row }">{{ row.original.permission || '无需权限' }}</template>
        <template #cell-anonymous="{ row }"><FaTag :variant="row.original.allowAnonymous ? 'default' : 'secondary'">{{ row.original.allowAnonymous ? '允许' : '不允许' }}</FaTag></template>
        <template #card="{ row }">
          <FaCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-start justify-between gap-2">
                <div class="flex min-w-0 flex-col gap-1">
                  <span class="min-w-0 break-words text-base font-semibold">{{ row.name }}</span>
                  <code class="text-sm text-secondary-foreground/70">/{{ row.command }}</code>
                </div>
                <FaTag :variant="row.allowAnonymous ? 'default' : 'secondary'">
                  {{ row.allowAnonymous ? '允许' : '不允许' }}
                </FaTag>
              </div>
              <div class="flex flex-col gap-1 text-sm">
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">权限码</span>
                  <span class="break-all">{{ row.permission || '无需权限' }}</span>
                </div>
                <div v-if="row.pluginCode" class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">插件</span>
                  <span>{{ row.pluginCode }}</span>
                </div>
              </div>
              <div v-if="row.description" class="text-sm text-secondary-foreground/80">
                {{ row.description }}
              </div>
            </div>
          </FaCard>
        </template>
      </FaResponsiveTable>
    </FaPageMain>
  </div>
</template>

<script setup lang="ts">
import type { TableColumn, YdTablePickerQuery, YdTablePickerResult } from '@yudream/components'
import type { ChatQuotaReservation } from '@/api/modules/platform-chat'
import type { UserPickerRow } from '@/api/modules/system-user'
import { YdAlert, YdAvatar, YdButton, YdCard, YdForm, YdFormItem, YdInput, YdNumberField, YdResponsiveTable, YdSelect, YdTablePicker } from '@yudream/components'
import { computed, ref, watch } from 'vue'
import { listUnresolvedChatQuota, reconcileChatQuota } from '@/api/modules/platform-chat'
import apiUser from '@/api/modules/system-user'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { toBackendAssetUrl } from '@/utils/backend-url'

const account = useAppAccountStore()
const { auth } = useAppAuth()
const runtime = useAiAssistantStore()
const canView = computed(() => auth('platform:chat:quota:view'))
const canEdit = computed(() => auth('platform:chat:quota:edit'))
const canSelectUser = computed(() => auth('system:user:view'))
const scope = ref('current')
const userSelection = ref<string[]>([])
const usageDate = ref(runtime.quota?.usageDate || '')
const rows = ref<ChatQuotaReservation[]>([])
const loading = ref(false)
const failure = ref('')
const selected = ref<ChatQuotaReservation>()
const promptTokens = ref(0)
const completionTokens = ref(0)
const saving = ref(false)
const userId = computed(() => scope.value === 'system' ? '0' : scope.value === 'user' ? userSelection.value[0] : String(account.userId))
const columns: TableColumn<ChatQuotaReservation>[] = [
  { accessorKey: 'usageDate', header: '账期', width: 110 },
  { accessorKey: 'heldTokens', header: '待核对 token', width: 140 },
  { accessorKey: 'knownTokens', header: '已确认 token', width: 140 },
  { accessorKey: 'runId', header: '运行记录', width: 160 },
  { id: 'actions', header: '操作', width: 110 },
]
const userColumns: TableColumn<UserPickerRow>[] = [
  { id: 'userAvatar', header: '', width: 56 },
  { accessorKey: 'username', header: '用户名' },
  { accessorKey: 'nickname', header: '昵称' },
]
/** 选择账号时无头像则用首字兜底（中文取前 2 字，拉丁取首字母）。 */
function userAvatarFallback(user?: UserPickerRow) {
  const name = (user?.nickname || user?.username || '').trim()
  if (!name) {
    return '?'
  }
  return name.charCodeAt(0) < 128 ? name.slice(0, 1).toUpperCase() : name.slice(0, 2)
}
async function fetchUsers(query: YdTablePickerQuery): Promise<YdTablePickerResult<UserPickerRow>> {
  const result = (await apiUser.page({ page: query.page, size: query.size, keyword: query.keyword })).data
  return {
    list: result.records.map(user => ({
      id: user.id,
      username: user.username,
      nickname: user.nickname || user.username,
      avatar: user.avatar ? toBackendAssetUrl(user.avatar) : undefined,
    })),
    total: result.total,
  }
}
async function load() {
  if (!canView.value || !userId.value) {
    return
  }
  const owner = String(account.userId)
  const target = userId.value
  loading.value = true
  failure.value = ''
  try {
    const response = await listUnresolvedChatQuota({ userId: target, usageDate: usageDate.value || undefined })
    if (owner === String(account.userId) && target === userId.value) {
      rows.value = response.data
    }
  }
  catch (error) {
    failure.value = error instanceof Error ? error.message : '读取待核对用量失败'
  }
  finally {
    loading.value = false
  }
}
function select(row: ChatQuotaReservation) {
  selected.value = row
  promptTokens.value = 0
  completionTokens.value = 0
}
async function reconcile() {
  if (!selected.value || !canEdit.value) {
    return
  }
  saving.value = true
  failure.value = ''
  if (promptTokens.value + completionTokens.value < selected.value.knownTokens) {
    saving.value = false
    failure.value = '最终实际用量不能小于已确认用量'
    return
  }
  try {
    await reconcileChatQuota({ ...selected.value, promptTokens: promptTokens.value, completionTokens: completionTokens.value, totalTokens: promptTokens.value + completionTokens.value })
    selected.value = undefined
    await Promise.all([load(), runtime.refreshQuota()])
  }
  catch (error) {
    failure.value = error instanceof Error ? error.message : '核对用量失败'
  }
  finally {
    saving.value = false
  }
}
watch([userId, canView], () => {
  selected.value = undefined
  void load()
}, { immediate: true })
</script>

<template>
  <section v-if="canView" aria-label="AI 用量核对" class="gap-3 grid">
    <h3>待核对用量</h3>
    <p class="text-sm">
      填写供应商确认的最终全部用量，服务端扣除已经记账的部分后补账并释放剩余扣留额度。
    </p>
    <YdAlert v-if="failure" variant="destructive" :title="failure" />
    <YdSelect v-model="scope" :options="[{ label: '当前账号', value: 'current' }, { label: '系统任务', value: 'system' }, ...(canSelectUser ? [{ label: '选择账号', value: 'user' }] : [])]" aria-label="用量账号范围" />
    <YdTablePicker v-if="scope === 'user' && canSelectUser" v-model="userSelection" :columns="userColumns" :fetcher="fetchUsers" row-key="id" label-key="nickname" :multiple="false" title="选择用量账号" placeholder="请选择账号">
      <template #cell-userAvatar="{ row }">
        <YdAvatar :src="row.original.avatar || ''" :fallback="userAvatarFallback(row.original)" class="size-8" />
      </template>
    </YdTablePicker>
    <div class="yd-quota-reconciliation__filters flex flex-wrap gap-2">
      <YdInput v-model="usageDate" type="date" aria-label="账期日期" />
      <YdButton variant="outline" :loading="loading" @click="load">
        查询
      </YdButton>
    </div>
    <YdResponsiveTable :columns="columns" :data="rows" row-key="reservationId">
      <template #actions="{ row }">
        <YdButton v-if="canEdit" variant="outline" size="sm" @click="select(row)">
          核对
        </YdButton>
      </template>
      <template #card="{ row }">
        <YdCard>
          <p>{{ row.usageDate }} · 待核对 {{ row.heldTokens.toLocaleString() }} tokens</p>
          <p>已确认 {{ row.knownTokens.toLocaleString() }} tokens</p>
          <p>运行记录 {{ row.runId || '未关联运行' }}</p>
          <YdButton v-if="canEdit" variant="outline" size="sm" @click="select(row)">
            核对
          </YdButton>
        </YdCard>
      </template>
    </YdResponsiveTable>
    <YdForm v-if="selected" :model="{ promptTokens, completionTokens }" layout="vertical">
      <p>核对运行 {{ selected.runId || selected.reservationId }} 的实际用量。</p>
      <p>已确认并记账 {{ selected.knownTokens.toLocaleString() }} tokens；以下填写最终全部用量。</p>
      <YdFormItem label="实际输入 token">
        <YdNumberField :model-value="promptTokens" :min="0" :step="1" :disabled="saving" @update:model-value="value => { if (value !== undefined) promptTokens = value }" />
      </YdFormItem>
      <YdFormItem label="实际输出 token">
        <YdNumberField :model-value="completionTokens" :min="0" :step="1" :disabled="saving" @update:model-value="value => { if (value !== undefined) completionTokens = value }" />
      </YdFormItem>
      <p>实际总用量 {{ promptTokens + completionTokens }} tokens</p>
      <div class="flex gap-2">
        <YdButton variant="outline" :disabled="saving" @click="selected = undefined">
          返回
        </YdButton>
        <YdButton :loading="saving" :disabled="promptTokens + completionTokens < selected.knownTokens" @click="reconcile">
          确认实际用量并补账
        </YdButton>
      </div>
    </YdForm>
  </section>
</template>

<style scoped>
.yd-quota-reconciliation__filters > :first-child {
  min-width: 0;
  flex: 1 1 12rem;
}

@media (width <= 480px) {
  .yd-quota-reconciliation__filters > * {
    width: 100%;
  }
}
</style>

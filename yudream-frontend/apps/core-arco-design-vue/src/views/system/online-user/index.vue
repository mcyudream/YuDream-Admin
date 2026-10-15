<script setup lang="ts">
import type { ActionMenuItem, TableColumn, UserColumn } from '@yudream/components'
import type { OnlineUser } from '@/api/modules/system-monitor'
import apiExcel from '@/api/modules/system-excel'
import apiMonitor from '@/api/modules/system-monitor'
import { saveExcelResponse } from '@/utils/excel'

const modal = useYdModal()
const toast = useYdToast()
const appAccountStore = useAppAccountStore()

const loading = ref(false)
const rows = ref<OnlineUser[]>([])
const keyword = ref('')

const { auth } = useAppAuth()

/** 会话身份列：用户名 + 邮箱，卡片头部与桌面首列共用 */
const sessionUserColumn: UserColumn<OnlineUser> = {
  nameKey: 'username',
  emailKey: 'email',
  avatarKey: 'avatar',
  header: '用户',
  width: 220,
}

/** 会话操作统一收进下拉 */
const sessionActions: ActionMenuItem<OnlineUser>[] = [
  { label: '强制下线', icon: 'morph:log-out', danger: true, visible: () => auth('system:monitor:online:kickout'), onClick: confirmKickout },
]

const tableColumns = computed<TableColumn<OnlineUser>[]>(() => [
  { accessorKey: 'username', header: '用户名', width: 140 },
  { accessorKey: 'nickname', header: '昵称', width: 140 },
  { accessorKey: 'email', header: '邮箱', width: 220 },
  { accessorKey: 'device', header: '设备', width: 120 },
  { id: 'timeout', header: '会话剩余时间', width: 150 },
  { id: 'token', header: '会话标识', width: 260 },
  { id: 'operation', header: '操作', width: 120, align: 'center', fixed: 'right' },
])

const onlineCountText = computed(() => `当前在线 ${rows.value.length} 人`)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await apiMonitor.onlineUsers({
      keyword: keyword.value || undefined,
      limit: 100,
    })
    rows.value = withCurrentSessionFallback(res.data)
  }
  finally {
    loading.value = false
  }
}

function withCurrentSessionFallback(data: OnlineUser[]) {
  if (data.length || !appAccountStore.token) {
    return data
  }
  return [{
    token: appAccountStore.token,
    username: appAccountStore.account,
    nickname: appAccountStore.account,
    avatar: appAccountStore.avatar || undefined,
    device: '当前浏览器',
    timeout: undefined,
  }]
}

function resetSearch() {
  keyword.value = ''
  load()
}

function ttlText(value?: number) {
  if (value == null) {
    return '-'
  }
  if (value < 0) {
    return '永久'
  }
  if (value < 60) {
    return `${value}s`
  }
  if (value < 3600) {
    return `${Math.floor(value / 60)}m`
  }
  return `${Math.floor(value / 3600)}h`
}

function shortToken(token?: string) {
  if (!token) {
    return '-'
  }
  return token.length > 28 ? `${token.slice(0, 16)}...${token.slice(-8)}` : token
}

function confirmKickout(row: OnlineUser) {
  modal.confirm({
    title: '确认下线',
    content: `确认强制下线 ${row.username || row.userId || row.token} ？`,
    onConfirm: async () => {
      await apiMonitor.kickoutOnlineUser(row.token)
      toast.success('已下线')
      await load()
    },
  })
}

async function exportOnlineUsers() {
  const res = await apiExcel.exportOnlineUsers({ keyword: keyword.value || undefined })
  saveExcelResponse(res, '在线用户.xlsx')
}
</script>

<template>
  <div>
    <YdPageHeader :title="onlineCountText" class="mb-0">
      <YdButton v-auth="'system:monitor:online:export'" variant="outline" @click="exportOnlineUsers">
        <YdIcon name="morph:file-spreadsheet" />
        导出
      </YdButton>
    </YdPageHeader>

    <YdPageMain>
      <YdLoadingOverlay :loading="loading">
        <YdResponsiveEnhancedTable
          row-key="token"
          table-root-class="rounded-lg overflow-hidden"
          table-class="min-w-[1080px]"
          column-visibility
          :user-column="sessionUserColumn"
          :actions="sessionActions"
          :columns="tableColumns"
          :data="rows"
        >
          <template #toolbar>
            <YdSearchBar class="w-full">
              <div class="gap-3 grid grid-cols-1 md:grid-cols-[minmax(260px,360px)_auto] md:items-center">
                <YdInput v-model="keyword" clearable placeholder="用户名 / 昵称 / 会话标识" @keydown.enter="load" @clear="load" />
                <div class="flex gap-2 md:justify-end">
                  <YdButton variant="outline" @click="resetSearch">
                    重置
                  </YdButton>
                </div>
              </div>
            </YdSearchBar>
          </template>
          <template #empty>
            <div class="empty-state">
              暂无在线会话，请确认已重启后端并重新登录
            </div>
          </template>
          <template #cell-timeout="{ row }">
            {{ ttlText(row.original.timeout) }}
          </template>
          <template #cell-token="{ row }">
            <code>{{ shortToken(row.original.token) }}</code>
          </template>
        </YdResponsiveEnhancedTable>
      </YdLoadingOverlay>
    </YdPageMain>
  </div>
</template>

<style scoped>
code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.empty-state {
  padding: 32px;
  color: var(--color-text-3);
  text-align: center;
}
</style>

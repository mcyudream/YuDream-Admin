<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { OnlineUser } from '@/api/modules/system-monitor'
import apiExcel from '@/api/modules/system-excel'
import apiMonitor from '@/api/modules/system-monitor'
import { saveExcelResponse } from '@/utils/excel'

const modal = useFaModal()
const toast = useFaToast()
const appAccountStore = useAppAccountStore()

const loading = ref(false)
const rows = ref<OnlineUser[]>([])
const keyword = ref('')

const tableColumns = computed<TableColumn<OnlineUser>[]>(() => [
  { accessorKey: 'username', header: '\u7528\u6237\u540d', width: 140 },
  { accessorKey: 'nickname', header: '\u6635\u79f0', width: 140 },
  { accessorKey: 'email', header: '\u90ae\u7bb1', width: 220 },
  { accessorKey: 'device', header: '\u8bbe\u5907', width: 120 },
  { id: 'timeout', header: '\u4f1a\u8bdd\u5269\u4f59\u65f6\u95f4', width: 150 },
  { id: 'token', header: '\u4f1a\u8bdd\u6807\u8bc6', width: 260 },
  { id: 'operation', header: '\u64cd\u4f5c', width: 120, align: 'center', fixed: 'right' },
])

const onlineCountText = computed(() => `\u5f53\u524d\u5728\u7ebf ${rows.value.length} \u4eba`)

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
    device: '\u5f53\u524d\u6d4f\u89c8\u5668',
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
    return '\u6c38\u4e45'
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
    title: '\u786e\u8ba4\u4e0b\u7ebf',
    content: `\u786e\u8ba4\u5f3a\u5236\u4e0b\u7ebf ${row.username || row.userId || row.token} \uff1f`,
    onConfirm: async () => {
      await apiMonitor.kickoutOnlineUser(row.token)
      toast.success('\u5df2\u4e0b\u7ebf')
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
    <FaPageHeader :title="onlineCountText" class="mb-0">
      <FaButton v-auth="'system:monitor:online:export'" variant="outline" @click="exportOnlineUsers">
        <FaIcon name="i-ri:file-excel-2-line" />
        导出
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaTable
        v-loading="loading"
        row-key="token"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1080px]"
        border
        stripe
        column-visibility
        :columns="tableColumns"
        :data="rows"
      >
        <template #toolbar>
          <FaSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,360px)_auto] md:items-center">
              <FaInput v-model="keyword" clearable placeholder="&#29992;&#25143;&#21517; / &#26165;&#31216; / &#20250;&#35805;&#26631;&#35782;" @keydown.enter="load" @clear="load" />
              <div class="flex gap-2 md:justify-end">
                <FaButton variant="outline" @click="resetSearch">
                  &#37325;&#32622;
                </FaButton>
              </div>
            </div>
          </FaSearchBar>
        </template>
        <template #empty>
          <div class="empty-state">
            &#26242;&#26080;&#22312;&#32447;&#20250;&#35805;&#65292;&#35831;&#30830;&#35748;&#24050;&#37325;&#21551;&#21518;&#31471;&#24182;&#37325;&#26032;&#30331;&#24405;
          </div>
        </template>
        <template #cell-timeout="{ row }">
          {{ ttlText(row.original.timeout) }}
        </template>
        <template #cell-token="{ row }">
          <code>{{ shortToken(row.original.token) }}</code>
        </template>
        <template #cell-operation="{ row }">
          <FaButton v-auth="'system:monitor:online:kickout'" variant="destructive" size="sm" @click="confirmKickout(row.original)">
            &#19979;&#32447;
          </FaButton>
        </template>
      </FaTable>
    </FaPageMain>
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

<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { AiRunSnapshot } from '@/api/modules/platform-ai-runs'
import { YdAlert, YdButton, YdCard, YdDrawer, YdLoadingOverlay, YdPageHeader, YdPageMain, YdResponsiveTable, YdSwitch, YdTag } from '@yudream/components'
import { computed, ref, watch } from 'vue'
import AiRunProgress from '@/components/AiRunProgress/index.vue'
import { useAiSystemRunsStore } from '@/store/modules/ai-system-runs'
import { isActiveAiRun } from '@/utils/ai-run-stream'

const runtime = useAiSystemRunsStore()
const account = useAppAccountStore()
const { auth } = useAppAuth()
const canView = computed(() => account.isLogin && auth('platform:ai:system:view'))
const canCancel = computed(() => auth('platform:ai:system:edit'))
const canApprove = computed(() => auth('platform:ai:system:accept'))
const activeOnly = ref(true)
const selected = ref('')
const drawerOpen = ref(false)
const rows = computed(() => Object.values(runtime.runs).filter(run => !activeOnly.value || isActiveAiRun(run.status)).sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || ''))))
const columns: TableColumn<AiRunSnapshot>[] = [
  { accessorKey: 'module', header: '任务模块', width: 180 },
  { accessorKey: 'sessionId', header: '工作上下文', width: 220 },
  { id: 'status', header: '状态', width: 120 },
  { accessorKey: 'createdAt', header: '创建时间', width: 220 },
  { id: 'actions', header: '操作', width: 150 },
]
function label(status: string) {
  return ({ QUEUED: '排队中', RUNNING: '执行中', WAITING: '等待输入', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已停止' })[status] || status
}
function open(run: AiRunSnapshot) {
  selected.value = run.id
  drawerOpen.value = true
}
function load() {
  if (canView.value && account.userId) {
    void runtime.load(String(account.userId), activeOnly.value)
  }
}
watch([canView, () => String(account.userId), activeOnly], load, { immediate: true })
</script>

<template>
  <YdPageHeader title="AI 系统任务" />
  <YdPageMain class="yd-system-ai-runs">
    <YdAlert v-if="!canView" variant="destructive" title="没有查看系统 AI 任务的权限" />
    <template v-else>
      <p class="mb-4">
        观察系统后台任务的实际模型输出、工具和进度。离开此页不会取消任务。
      </p>
      <YdAlert v-if="runtime.error" variant="destructive" :title="runtime.error" />
      <YdLoadingOverlay :loading="runtime.loading">
        <YdResponsiveTable :data="rows" :columns="columns" row-key="id">
          <template #toolbar>
            <div class="flex flex-wrap gap-3 items-center">
              <YdSwitch v-model="activeOnly" aria-label="仅显示运行中任务" />
              <span>仅显示运行中任务</span>
              <YdButton variant="outline" @click="load">
                刷新任务
              </YdButton>
            </div>
          </template>
          <template #status="{ row }">
            <YdTag :variant="row.status === 'FAILED' ? 'destructive' : 'secondary'">
              {{ label(row.status) }}
            </YdTag>
          </template>
          <template #actions="{ row }">
            <YdButton variant="outline" size="sm" @click="open(row)">
              查看实时进度
            </YdButton>
          </template>
          <template #card="{ row }">
            <YdCard>
              <strong>{{ row.module }}</strong>
              <p>{{ label(row.status) }} · {{ row.createdAt }}</p>
              <p class="yd-system-ai-runs__session">{{ row.sessionId }}</p>
              <YdButton variant="outline" size="sm" @click="open(row)">
                查看实时进度
              </YdButton>
            </YdCard>
          </template>
        </YdResponsiveTable>
      </YdLoadingOverlay>
      <YdDrawer v-model="drawerOpen" title="系统 AI 任务进度" :footer="false" content-class="w-[760px] max-w-full">
        <AiRunProgress v-if="selected && drawerOpen" :run-id="selected" scope="system" :can-cancel="canCancel" :can-approve="canApprove" />
      </YdDrawer>
    </template>
  </YdPageMain>
</template>

<style scoped>
.yd-system-ai-runs__session {
  overflow-wrap: anywhere;
}

@media (width <= 768px) {
  .yd-system-ai-runs :deep(.yd-responsive-table__toolbar) {
    align-items: stretch;
  }
}
</style>

<script setup lang="ts">
// 发布版本视图：后端真实分页记录（每页固定 12 条）；恢复只把所选版本写回当前草稿
// （不影响线上版本与历史）；停止发布只清线上指针（草稿与历史保留）。动作分别受
// platform:cms:edit / platform:cms:publish 控制；确认前捕获主题与版本号，确认时
// 复验，站点上下文已变化的操作一律放弃，不误恢复到别的主题。
import type { StudioRelease } from '@/api/modules/platform-cms-studio'
import { computed, onMounted, ref, watch } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'

defineOptions({ name: 'StudioReleasesView' })

const store = useSiteStudioStore()
const toast = useYdToast()
const modal = useYdModal()

const PAGE_SIZE = 12
const size = ref(PAGE_SIZE)
const canEdit = computed(() => useAppAuth().auth('platform:cms:edit'))
const publishedId = computed(() => store.state?.publishedReleaseId || '')

const page = computed({
  get: () => store.releasesPage || 1,
  set: (value: number) => {
    void store.loadReleases(value)
  },
})

onMounted(() => {
  void store.loadReleases(1)
})

/** 切主题不卸载本视图：store 按主题隔离并已清理，这里跟随重取第一页 */
watch(() => store.themeCode, () => {
  void store.loadReleases(1)
})

function formatTime(value?: string) {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function contextChanged(captured: { themeCode: string, revision: string }) {
  return store.themeCode !== captured.themeCode || (store.state?.revision || '') !== captured.revision
}

/** 恢复只覆盖草稿；文案一次说明未保存更改的丢弃与草稿覆盖 */
function confirmRestore(release: StudioRelease) {
  const captured = { themeCode: store.themeCode, revision: store.state?.revision || '' }
  modal.confirm({
    title: '恢复到该版本？',
    content: `${store.dirty ? '当前草稿有未保存的更改，恢复会丢弃这些更改。' : ''}把版本 ${release.revision} 恢复为当前草稿；恢复只覆盖草稿，不影响已发布版本与历史记录。`,
    confirmButtonText: '恢复',
    onConfirm: async () => {
      if (contextChanged(captured)) {
        toast.error('站点上下文已变化，本次恢复已放弃，请重试')
        return
      }
      try {
        await store.restoreRelease(release.id)
        toast.success(`已恢复到版本 ${release.revision}`)
      }
      catch {
        toast.error(store.saveError || '恢复失败')
      }
    },
  })
}

function confirmUnpublish() {
  const captured = { themeCode: store.themeCode, revision: store.state?.revision || '' }
  modal.confirm({
    title: '停止对外发布？',
    content: '停止后公开站 /site 将回退旧版 CMS/原生主题，不再展示本主题的站点文档；当前草稿与全部历史版本保留，可随时再次发布。',
    confirmButtonText: '停止发布',
    onConfirm: async () => {
      if (contextChanged(captured)) {
        toast.error('站点上下文已变化，本次操作已放弃，请重试')
        return
      }
      try {
        await store.unpublish()
        toast.success('已停止对外发布，公开站回退旧版')
      }
      catch {
        toast.error(store.saveError || '停止发布失败')
      }
    },
  })
}
</script>

<template>
  <div class="flex flex-col h-full min-h-0">
    <div class="px-4 pt-4 flex flex-wrap gap-2 items-center sm:px-6">
      <div class="flex-1 min-w-0">
        <h2 class="text-sm font-semibold m-0">
          发布版本（{{ Number(store.releasesTotal) || 0 }}）
        </h2>
        <p class="text-xs text-muted-foreground m-0">
          以下为后端真实发布记录；{{ publishedId ? '当前线上版本已高亮。' : '当前没有线上版本。' }}
        </p>
      </div>
      <YdButton
        v-if="publishedId"
        v-auth="'platform:cms:publish'"
        variant="destructive"
        :loading="store.publishing"
        @click="confirmUnpublish"
      >
        停止对外发布
      </YdButton>
    </div>

    <div class="px-4 pb-2 pt-3 flex-1 min-h-0 overflow-auto sm:px-6">
      <YdResult
        v-if="store.releasesError"
        status="error"
        title="发布版本加载失败"
        :description="store.releasesError"
      >
        <YdButton variant="outline" @click="store.loadReleases(1)">
          重试
        </YdButton>
      </YdResult>
      <YdLoading v-else-if="store.releasesLoading && !store.releases.length" />
      <YdEmpty
        v-else-if="!store.releases.length"
        description="还没有发布版本；编辑完成后点右上角「发布」生成第一个版本"
      />
      <YdList v-else dense aria-label="发布版本列表">
        <YdListRow v-for="release in store.releases" :key="release.id">
          <div class="py-1 flex flex-1 flex-wrap gap-2 min-w-0 items-center">
            <code class="text-sm font-mono min-w-0 break-all">{{ release.revision }}</code>
            <span class="text-xs text-muted-foreground">{{ formatTime(release.createdAt) }}</span>
            <span class="yd-status-tone" :data-tone="release.id === publishedId ? 'success' : 'neutral'">
              {{ release.id === publishedId ? '当前线上版本' : '历史版本' }}
            </span>
          </div>
          <YdButton
            v-if="canEdit"
            variant="outline"
            size="sm"
            :aria-label="`恢复到版本 ${release.revision}`"
            @click="confirmRestore(release)"
          >
            <YdIcon name="morph:history" /> 恢复到此版本
          </YdButton>
        </YdListRow>
      </YdList>
    </div>

    <div v-if="!store.releasesError && Number(store.releasesTotal) > PAGE_SIZE" class="px-4 pb-4 sm:px-6">
      <YdPagination
        v-model:page="page"
        v-model:size="size"
        :total="Number(store.releasesTotal)"
        layout="total, ->, pager"
      />
    </div>
  </div>
</template>

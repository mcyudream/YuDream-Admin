<script setup lang="ts">
import type { ActionMenuItem, TableColumn, YdSegmentedOption } from '@yudream/components'
import type { WikiSpace } from '@/api/modules/platform-wiki'
import { computed, inject, ref } from 'vue'
import { deleteWikiSpace, saveWikiSpace } from '@/api/modules/platform-wiki'
import { wikiCoverOf, wikiCoverStyle } from '../wiki-cover'
import { wikiWorkbenchKey } from '../wiki-utils'

// 知识库入口：只承担「发现知识库 + 创建/删除 + 进入编辑或治理」，不再混放面板导航。
// 卡片密度：封面是窄横幅，名称为主视觉；页面数、最近更新与需要处理的状态都在卡片里直说。
const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const modal = useYdModal()

const keyword = ref('')
const publicFilter = ref<'all' | 'public' | 'private'>('all')
const viewMode = ref<'card' | 'table'>('card')
const deletingSpaceId = ref('')

const viewModeOptions: YdSegmentedOption<'card' | 'table'>[] = [
  { label: '卡片', value: 'card' },
  { label: '列表', value: 'table' },
]
const publicFilterOptions = [
  { label: '全部知识库', value: 'all' },
  { label: '仅公开', value: 'public' },
  { label: '仅私有', value: 'private' },
]

const publicCount = computed(() => store.spaces.value.filter(item => item.publicReadEnabled).length)

const filteredSpaces = computed(() => {
  const text = keyword.value.trim().toLocaleLowerCase()
  return store.spaces.value.filter((item) => {
    if (publicFilter.value === 'public' && !item.publicReadEnabled) {
      return false
    }
    if (publicFilter.value === 'private' && item.publicReadEnabled) {
      return false
    }
    if (!text) {
      return true
    }
    return `${item.name} ${item.slug} ${item.description || ''}`.toLocaleLowerCase().includes(text)
  })
})

const initialLoading = computed(() => store.spacesLoading.value && !store.spacesLoaded.value)
const listFailed = computed(() => !store.spacesLoaded.value && Boolean(store.spacesError.value))

function resetFilters() {
  keyword.value = ''
  publicFilter.value = 'all'
}

function publicUrl(item: WikiSpace) {
  return `${window.location.origin}/wiki/${encodeURIComponent(item.slug)}`
}

async function copyPublicUrl(item: WikiSpace) {
  try {
    await navigator.clipboard.writeText(publicUrl(item))
    toast.success('公开链接已复制')
  }
  catch {
    toast.warning(`复制失败，请手动复制：${publicUrl(item)}`)
  }
}

// 封面来自后端真实字段：UPLOAD 呈现公开文件图片，SEEDED 按 seed 确定性呈现，NONE 才回落空状态图案
function coverOf(item: WikiSpace) {
  return wikiCoverOf(item)
}

function coverTitle(item: WikiSpace) {
  const cover = coverOf(item)
  if (cover.type === 'UPLOAD') {
    return cover.alt || `${item.name} 的上传封面`
  }
  if (cover.type === 'SEEDED') {
    return `随机封面（seed ${cover.seed}），同一 seed 呈现结果稳定`
  }
  return '尚未设置封面：当前显示空状态图案，可在治理区「设置 → 封面」配置'
}

function spaceInitial(item: WikiSpace) {
  return (item.name || item.slug || '?').trim().slice(0, 1).toUpperCase()
}

/** 页面/目录计数：老响应缺聚合字段时返回 null，卡片不伪造 0（0 是真实值，null 是未知） */
function pageCountOf(item: WikiSpace): number | null {
  return typeof item.pageCount === 'number' ? item.pageCount : null
}

function directoryCountOf(item: WikiSpace): number | null {
  return typeof item.directoryCount === 'number' ? item.directoryCount : null
}

function unpublishedCountOf(item: WikiSpace): number {
  return typeof item.unpublishedPageCount === 'number' ? item.unpublishedPageCount : 0
}

function failedIngestCountOf(item: WikiSpace): number {
  return typeof item.failedIngestCount === 'number' ? item.failedIngestCount : 0
}

function updatedAtOf(item: WikiSpace): string {
  return typeof item.lastUpdatedAt === 'string' ? item.lastUpdatedAt : ''
}

/** 卡片/表格共用的用量文案：13 页面 · 3 目录；字段缺失时不显示 */
function usageText(item: WikiSpace): string {
  const pages = pageCountOf(item)
  const directories = directoryCountOf(item)
  if (pages === null && directories === null) {
    return ''
  }
  const parts: string[] = []
  if (pages !== null) {
    parts.push(`${pages} 页面`)
  }
  if (directories !== null && directories > 0) {
    parts.push(`${directories} 目录`)
  }
  return parts.join(' · ')
}

// 列表视图：桌面表格与移动卡片共用同一份 actions 定义
const columns: TableColumn<WikiSpace>[] = [
  { accessorKey: 'name', header: '知识库', minWidth: 220 },
  { accessorKey: 'publicReadEnabled', header: '公开状态', width: 110 },
  { id: 'usage', header: '内容与更新', width: 190 },
  { accessorKey: 'description', header: '描述', minWidth: 200 },
  { id: 'retrieval', header: '检索配置', width: 190 },
]

const actions: ActionMenuItem<WikiSpace>[] = [
  { label: '进入编辑', icon: 'morph:pencil', onClick: item => store.openAuthor({ space: item }) },
  { label: '治理管理', icon: 'morph:settings-2', onClick: item => store.openManage(undefined, item) },
  { label: '复制公开链接', icon: 'morph:link', visible: item => Boolean(item.publicReadEnabled), onClick: item => void copyPublicUrl(item) },
  { label: '删除知识库', icon: 'morph:trash-2', danger: true, onClick: item => removeSpace(item) },
]

function selectSpace(item: WikiSpace) {
  if (!item.id) {
    return
  }
  store.openAuthor({ space: item })
}

// 新建知识库：入口页独有的用例
const createVisible = ref(false)
const creating = ref(false)
const createForm = ref({ name: '', slug: '', description: '' })

function openCreate() {
  createForm.value = { name: '', slug: '', description: '' }
  createVisible.value = true
}

async function createSpace() {
  if (!createForm.value.name.trim() || !createForm.value.slug.trim()) {
    toast.warning('请填写名称与 slug')
    return
  }
  creating.value = true
  try {
    const response = await saveWikiSpace({
      name: createForm.value.name.trim(),
      slug: createForm.value.slug.trim(),
      description: createForm.value.description.trim(),
      publicReadEnabled: false,
      externalSearchEnabled: false,
      graphEnabled: true,
      chunkSize: 800,
      chunkOverlap: 120,
      topK: 8,
      queryExpansionEnabled: true,
      rerankEnabled: false,
      contextWindowTokens: 64000,
      sourceGroundedDefault: false,
      watchEnabled: false,
    })
    toast.success('知识库已创建')
    createVisible.value = false
    await store.reloadSpaces()
    const created = response?.data
    if (created?.id) {
      store.openAuthor({ space: created })
    }
  }
  finally {
    creating.value = false
  }
}

// 删除知识库：后端级联删除目录/页面、资料源、摄入任务与审核项
function removeSpace(item: WikiSpace) {
  if (!item.id) {
    return
  }
  modal.confirm({
    title: '删除知识库',
    content: `确定删除知识库「${item.name}」吗？其中的目录、页面、资料源、摄入任务与审核项将被一并删除，且不可恢复。`,
    onConfirm: async () => {
      deletingSpaceId.value = item.id!
      try {
        await deleteWikiSpace(item.id!)
        toast.success('知识库已删除')
        if (item.id === store.spaceId.value) {
          store.clearSpace()
        }
        await store.reloadSpaces()
      }
      finally {
        deletingSpaceId.value = ''
      }
    },
  })
}
</script>

<template>
  <div class="entry">
    <!-- 页头与工具栏各占一行：标题/动作在上，搜索/筛选/视图切换收进同一条工具栏 -->
    <div class="entry__head">
      <div class="entry__head-text">
        <h1 class="entry__title">
          知识库
        </h1>
        <p class="entry__subtitle">
          {{ store.spaces.value.length }} 个知识库 · 已开启公开阅读 {{ publicCount }} 个
        </p>
      </div>
      <div class="entry__head-actions">
        <YdTooltip text="刷新列表">
          <YdButton variant="outline" size="icon-sm" :loading="store.spacesLoading.value" aria-label="刷新知识库列表" @click="store.reloadSpaces()">
            <YdIcon name="morph:refresh-cw" />
          </YdButton>
        </YdTooltip>
        <YdButton @click="openCreate">
          <YdIcon name="morph:plus" />
          新建知识库
        </YdButton>
      </div>
    </div>

    <div class="entry__toolbar">
      <YdInput v-model="keyword" clearable placeholder="搜索名称、slug 或描述" aria-label="搜索知识库" class="entry__search">
        <template #start>
          <YdIcon name="morph:search" />
        </template>
      </YdInput>
      <YdSelect v-model="publicFilter" :options="publicFilterOptions" aria-label="公开状态筛选" class="entry__status" />
      <YdSegmented v-model="viewMode" :options="viewModeOptions" size="sm" aria-label="展示方式" class="entry__mode" />
      <span class="entry__result-count">
        <template v-if="keyword.trim() || publicFilter !== 'all'">{{ filteredSpaces.length }} / {{ store.spaces.value.length }}</template>
      </span>
    </div>

    <div class="entry__body">
      <!-- 三态：加载 / 失败 / 真空，失败不冒充「暂无知识库」 -->
      <div v-if="initialLoading" class="entry__state">
        <div class="entry__skeleton">
          <div v-for="index in 6" :key="index" class="entry__skeleton-card">
            <YdSkeleton variant="rect" height="100%" />
          </div>
        </div>
      </div>
      <div v-else-if="listFailed" class="entry__state">
        <YdResult status="error" title="知识库列表加载失败" :description="store.spacesError.value">
          <template #action>
            <YdButton variant="outline" :loading="store.spacesLoading.value" @click="store.reloadSpaces()">
              <YdIcon name="morph:refresh-cw" />
              重新加载
            </YdButton>
          </template>
        </YdResult>
      </div>
      <div v-else-if="!store.spaces.value.length" class="entry__state">
        <YdEmpty
          icon="morph:library"
          title="还没有知识库"
          description="新建知识库后即可导入资料、生成页面并开始检索。"
        >
          <template #action>
            <YdButton @click="openCreate">
              <YdIcon name="morph:plus" />
              新建知识库
            </YdButton>
          </template>
        </YdEmpty>
      </div>
      <div v-else-if="!filteredSpaces.length" class="entry__state">
        <YdEmpty
          illustration="no-result"
          title="没有匹配的知识库"
          description="换个关键词，或清空筛选后重新查看。"
        >
          <template #action>
            <YdButton variant="outline" @click="resetFilters">
              清空筛选
            </YdButton>
          </template>
        </YdEmpty>
      </div>

      <YdScrollArea v-else class="entry__scroll">
        <div class="entry__inner">
          <div v-if="viewMode === 'card'" class="entry__grid">
            <!-- 封面卡：窄封面横幅 + 信息区（名称/描述/用量/待处理状态），操作条悬停时从封面底部浮出 -->
            <YdCard
              v-for="item in filteredSpaces"
              :key="item.id || item.slug"
              class="entry-card"
              content-class="entry-card__content"
              :aria-label="`进入知识库 ${item.name}`"
              role="link"
              tabindex="0"
              @click="selectSpace(item)"
              @keydown.enter.prevent="selectSpace(item)"
            >
              <div class="entry-card__cover">
                <img
                  v-if="coverOf(item).hasImage"
                  :src="coverOf(item).url"
                  :alt="coverOf(item).alt || `${item.name} 封面`"
                  :title="coverTitle(item)"
                  class="entry-card__cover-img"
                >
                <div
                  v-else-if="coverOf(item).type === 'SEEDED'"
                  class="entry-card__cover-art"
                  :style="wikiCoverStyle(item)"
                  :title="coverTitle(item)"
                />
                <!-- 未设置封面：中性的点阵空状态，不用名称哈希渐变冒充已有封面 -->
                <div v-else class="entry-card__cover-art entry-card__cover-art--empty" :title="coverTitle(item)">
                  <YdIcon name="morph:image" />
                </div>
                <div class="entry-card__cover-top">
                  <YdTag :variant="item.publicReadEnabled ? 'default' : 'secondary'">
                    {{ item.publicReadEnabled ? '公开' : '私有' }}
                  </YdTag>
                  <span v-if="!coverOf(item).hasImage && coverOf(item).type === 'NONE'" class="entry-card__cover-note">未设置封面</span>
                </div>
                <div class="entry-card__bar" @click.stop @keydown.stop>
                  <YdButton size="sm" variant="ghost" class="entry-bar-btn" :disabled="!item.id" :aria-label="`进入编辑 ${item.name}`" @click="selectSpace(item)">
                    <YdIcon name="morph:pencil" />
                    编辑
                  </YdButton>
                  <YdButton size="sm" variant="ghost" class="entry-bar-btn" :disabled="!item.id" :aria-label="`治理管理 ${item.name}`" @click="store.openManage(undefined, item)">
                    <YdIcon name="morph:settings-2" />
                    治理
                  </YdButton>
                  <YdButton size="sm" variant="ghost" class="entry-bar-btn" :disabled="!item.publicReadEnabled" :title="item.publicReadEnabled ? '复制公开链接' : '知识库未公开，无公开链接'" :aria-label="`复制公开链接 ${item.name}`" @click="copyPublicUrl(item)">
                    <YdIcon name="morph:link" />
                    链接
                  </YdButton>
                  <YdButton size="sm" variant="ghost" class="entry-bar-btn entry-bar-btn--danger" :loading="deletingSpaceId === item.id" :aria-label="`删除知识库 ${item.name}`" @click="removeSpace(item)">
                    <YdIcon name="morph:trash-2" />
                    删除
                  </YdButton>
                </div>
              </div>

              <div class="entry-card__body">
                <div class="entry-card__head">
                  <span class="entry-card__initial" aria-hidden="true">{{ spaceInitial(item) }}</span>
                  <div class="entry-card__head-text">
                    <strong class="entry-card__name" :title="item.name">{{ item.name }}</strong>
                    <span class="entry-card__slug" :title="`/wiki/${item.slug}`">/wiki/{{ item.slug }}</span>
                  </div>
                </div>
                <p class="entry-card__desc" :title="item.description || undefined">
                  {{ item.description || '未填写描述' }}
                </p>
                <div class="entry-card__meta">
                  <span v-if="usageText(item)" class="entry-card__meta-item">
                    <YdIcon name="morph:file-text" />
                    {{ usageText(item) }}
                  </span>
                  <span v-if="updatedAtOf(item)" class="entry-card__meta-item" :title="`最近更新：${updatedAtOf(item)}`">
                    <YdIcon name="morph:clock" />
                    更新于 <YdTimeAgo :date="updatedAtOf(item)" />
                  </span>
                  <span v-else-if="pageCountOf(item) === 0" class="entry-card__meta-item">
                    <YdIcon name="morph:clock" />
                    还没有页面
                  </span>
                </div>
                <div v-if="unpublishedCountOf(item) || failedIngestCountOf(item)" class="entry-card__flags">
                  <span v-if="unpublishedCountOf(item)" class="entry-flag entry-flag--warn">
                    未发布 {{ unpublishedCountOf(item) }}
                  </span>
                  <span v-if="failedIngestCountOf(item)" class="entry-flag entry-flag--danger">
                    摄入失败 {{ failedIngestCountOf(item) }}
                  </span>
                </div>
              </div>
            </YdCard>

            <!-- 引导卡：少数据时补齐网格，同时给出唯一的新建入口（与页头按钮同义） -->
            <YdButton variant="outline" class="entry-create" @click="openCreate">
              <span class="entry-create__inner">
                <YdIcon name="morph:plus" class="entry-create__icon" />
                <span class="entry-create__title">新建知识库</span>
                <span class="entry-create__hint">导入资料、生成页面、开始检索</span>
              </span>
            </YdButton>
          </div>

          <YdResponsiveEnhancedTable
            v-else
            row-key="id"

            stripe column-visibility border
            table-class="min-w-[980px]"
            :columns="columns"
            :actions="actions"
            :data="filteredSpaces"
          >
            <template #cell-name="{ row }">
              <div class="entry-cell">
                <strong class="entry-cell__name">{{ row.original.name }}</strong>
                <span class="entry-cell__slug">/wiki/{{ row.original.slug }}</span>
              </div>
            </template>
            <template #cell-publicReadEnabled="{ row }">
              <YdTag :variant="row.original.publicReadEnabled ? 'default' : 'secondary'">
                {{ row.original.publicReadEnabled ? '公开' : '私有' }}
              </YdTag>
            </template>
            <template #cell-usage="{ row }">
              <div class="entry-cell">
                <span class="entry-cell__desc">{{ usageText(row.original) || '-' }}</span>
                <span v-if="unpublishedCountOf(row.original) || failedIngestCountOf(row.original)" class="entry-cell__flags">
                  <span v-if="unpublishedCountOf(row.original)" class="entry-flag entry-flag--warn">未发布 {{ unpublishedCountOf(row.original) }}</span>
                  <span v-if="failedIngestCountOf(row.original)" class="entry-flag entry-flag--danger">摄入失败 {{ failedIngestCountOf(row.original) }}</span>
                </span>
                <span v-else-if="updatedAtOf(row.original)" class="entry-cell__desc">
                  更新于 <YdTimeAgo :date="updatedAtOf(row.original)" />
                </span>
              </div>
            </template>
            <template #cell-description="{ row }">
              <span class="entry-cell__desc" :title="row.original.description || undefined">{{ row.original.description || '-' }}</span>
            </template>
            <template #cell-retrieval="{ row }">
              <span class="entry-cell__desc">topK {{ row.original.topK }} · 分块 {{ row.original.chunkSize }}/{{ row.original.chunkOverlap }}</span>
            </template>
          </YdResponsiveEnhancedTable>
        </div>
      </YdScrollArea>
    </div>

    <YdModal
      v-model="createVisible"
      title="新建知识库"
      show-cancel-button
      :confirm-button-loading="creating"
      @confirm="createSpace"
    >
      <div class="entry-form">
        <label class="entry-field">
          <span>名称</span>
          <YdInput v-model="createForm.name" placeholder="例如：产品知识库" aria-label="知识库名称" />
        </label>
        <label class="entry-field">
          <span>路径 slug</span>
          <YdInput v-model="createForm.slug" placeholder="例如：product" aria-label="知识库路径 slug" />
        </label>
        <label class="entry-field">
          <span>描述</span>
          <YdTextarea v-model="createForm.description" placeholder="这个知识库用来做什么？" :rows="3" aria-label="知识库描述" />
        </label>
      </div>
    </YdModal>
  </div>
</template>

<style scoped>
.entry {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  height: 100%;
  min-height: 0;
}

/* 页头：H1 20/600 + 计数副标题，动作收在右侧，不再跨行分散 */
.entry__head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 10px;
}

.entry__head-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.entry__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--color-text-1);
}

.entry__subtitle {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.entry__head-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
}

/* 工具栏：搜索 + 筛选 + 视图切换同一条横栏，与列表用一条细边分隔 */
.entry__toolbar {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 0 16px 12px;
  border-bottom: 1px solid var(--color-border-2);
}

.entry__search {
  flex: 1 1 240px;
  max-width: 360px;
}

.entry__status {
  flex: 0 0 150px;
}

.entry__mode {
  flex-shrink: 0;
}

.entry__result-count {
  font-size: 12px;
  color: var(--color-text-3);
  font-variant-numeric: tabular-nums;
}

.entry__body {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: var(--color-fill-1);
}

.entry__state {
  display: grid;
  flex: 1;
  align-content: center;
  justify-items: center;
  min-width: 0;
  min-height: 0;
  padding: 24px 16px;
  overflow: auto;
}

.entry__skeleton {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  width: 100%;
}

.entry__skeleton-card {
  height: 200px;
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.entry__scroll {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.entry__inner {
  padding: 16px;
}

/* 少数据时网格居中：列宽有上限，两张卡不会再孤零零贴在左上角 */
.entry__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 320px));
  gap: 14px;
  justify-content: center;
}

/* 卡片/正文默认内边距来自未分层工具类与适配层，同优先级拼顺序会输，用重叠选择器压掉 */
.entry-card.entry-card {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.16s ease, border-color 0.16s ease;
}

.entry-card:hover {
  border-color: var(--color-border-3);
  box-shadow: 0 4px 12px rgb(0 0 0 / 8%);
}

/* 焦点环复用共享外观令牌，不直接消费品牌色令牌 */
.entry-card:focus-visible {
  outline: 2px solid var(--yd-focus-ring);
  outline-offset: 2px;
}

/* 封面需要贴边：YdCard 正文槽在子组件内部、没有本组件作用域属性，必须 :deep 穿透；
   其内边距来自未分层工具类/适配层，靠 .entry-card 前缀把优先级垫高 */
.entry-card :deep(.entry-card__content) {
  display: flex;
  flex-direction: column;
  padding: 0;
}

/* 封面是窄横幅而不是整卡：名称为主视觉，字母只留小尺寸方块 */
.entry-card__cover {
  position: relative;
  height: 104px;
  overflow: hidden;
  background-color: var(--color-fill-2);
}

.entry-card__cover-img,
.entry-card__cover-art {
  width: 100%;
  height: 100%;
}

.entry-card__cover-img {
  object-fit: cover;
}

/* 未设置封面：中性点阵 + 小图标，低饱和、不冒充已有封面 */
.entry-card__cover-art--empty {
  display: grid;
  place-items: center;
  font-size: 20px;
  color: var(--color-text-4);
  background-color: var(--color-fill-2);
  background-image: radial-gradient(var(--color-border-3) 1px, transparent 1px);
  background-size: 12px 12px;
}

.entry-card__cover-top {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 8px 10px;
}

.entry-card__cover-note {
  padding: 1px 7px;
  font-size: 11px;
  color: rgb(255 255 255 / 92%);
  background: rgb(0 0 0 / 34%);
  border-radius: 999px;
}

/* hover 浮出的知识库设置条：深色实底（不用毛玻璃），从封面底部滑入；按钮等宽带文字标签 */
.entry-card__bar {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  gap: 2px;
  align-items: center;
  padding: 5px 8px;
  background: rgb(10 12 16 / 82%);
  opacity: 0;
  transform: translateY(100%);
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.entry-card:hover .entry-card__bar,
.entry-card:focus-within .entry-card__bar {
  opacity: 1;
  transform: translateY(0);
}

/* 触屏没有 hover，设置条常驻 */
@media (hover: none) {
  .entry-card__bar {
    opacity: 1;
    transform: none;
  }
}

/* 四枚等宽按钮塞进卡片宽度：收紧左右内边距，跳过适配层默认节奏 */
.entry-card__bar .entry-bar-btn {
  flex: 1 1 0;
  padding-right: 6px;
  padding-left: 6px;
  color: rgb(255 255 255 / 90%);
}

.entry-card__bar .entry-bar-btn:hover {
  color: #fff;
  background: rgb(255 255 255 / 14%);
}

.entry-card__bar .entry-bar-btn--danger:hover {
  color: rgb(255 122 122);
  background: rgb(255 90 90 / 16%);
}

.entry-card__body {
  display: grid;
  gap: 8px;
  padding: 12px 14px 14px;
}

.entry-card__head {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

/* 小尺寸字母 Logo：28px 方块，不抢名称为主视觉 */
.entry-card__initial {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 28px;
  height: 28px;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 8px;
}

.entry-card__head-text {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.entry-card__name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
  white-space: nowrap;
}

.entry-card__slug {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.entry-card__desc {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-text-2);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  min-height: 39px;
}

.entry-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.entry-card__meta-item {
  display: inline-flex;
  gap: 5px;
  align-items: center;
}

/* 待处理状态：只有真实 > 0 时才出现，颜色成体系（未发布=警告、摄入失败=危险） */
.entry-card__flags,
.entry-cell__flags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.entry-flag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  font-size: 11px;
  border-radius: 999px;
}

.entry-flag--warn {
  color: rgb(var(--warning-7));
  background: rgb(var(--warning-1));
}

.entry-flag--danger {
  color: rgb(var(--danger-7));
  background: rgb(var(--danger-1));
}

/* 新建引导卡：网格里的虚线空位，用 YD 按钮承载（业务模板不自绘原生按钮）；
   重叠类名垫高优先级，压掉按钮自身的底色、描边与固定高度 */
.entry-create.entry-create {
  display: grid;
  place-content: center;
  justify-items: center;
  height: auto;
  min-height: 200px;
  padding: 16px;
  color: var(--color-text-3);
  background: transparent;
  border: 1px dashed var(--color-border-3);
  border-radius: 8px;
  box-shadow: none;
}

.entry-create.entry-create:hover {
  color: var(--color-text-1);
  background: var(--color-bg-1);
  border-color: var(--color-text-4);
}

.entry-create__inner {
  display: grid;
  gap: 4px;
  justify-items: center;
}

.entry-create__icon {
  font-size: 20px;
}

.entry-create__title {
  font-size: 14px;
  font-weight: 600;
  color: inherit;
}

.entry-create__hint {
  font-size: 12px;
}

.entry-cell {
  display: grid;
  min-width: 0;
}

.entry-cell__name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  white-space: nowrap;
}

.entry-cell__slug,
.entry-cell__desc {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.entry-cell__flags {
  margin-top: 3px;
}

.entry-form {
  display: grid;
  gap: 14px;
}

.entry-field {
  display: grid;
  gap: 6px;
}

.entry-field > span {
  font-size: 13px;
  color: var(--color-text-3);
}

@media (width <= 768px) {
  .entry__toolbar {
    align-items: stretch;
  }

  .entry__search,
  .entry__status {
    flex: 1 1 100%;
    max-width: none;
  }

  .entry__inner {
    padding: 12px;
  }

  .entry__grid,
  .entry__skeleton {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

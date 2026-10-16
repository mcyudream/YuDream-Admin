<script setup lang="ts">
import type { ActionMenuItem, TableColumn, YdSegmentedOption } from '@yudream/components'
import type { WikiSpace } from '@/api/modules/platform-wiki'
import { computed, inject, ref } from 'vue'
import { deleteWikiSpace, saveWikiSpace } from '@/api/modules/platform-wiki'
import { wikiCoverOf, wikiCoverStyle } from '../wiki-cover'
import { wikiWorkbenchKey } from '../wiki-utils'

// 知识库入口：只承担「发现知识库 + 创建/删除 + 进入编辑或治理」，不再混放面板导航
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

// 封面来自后端真实字段：UPLOAD 呈现公开文件图片，SEEDED 按 seed 确定性呈现，NONE 才回落名称哈希占位
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
  return '尚未设置封面：当前按名称显示占位图案，可在治理区「设置 → 封面」配置'
}

function spaceInitial(item: WikiSpace) {
  return (item.name || item.slug || '?').trim().slice(0, 1).toUpperCase()
}

// 列表视图：桌面表格与移动卡片共用同一份 actions 定义
const columns: TableColumn<WikiSpace>[] = [
  { accessorKey: 'name', header: '知识库', minWidth: 220 },
  { accessorKey: 'publicReadEnabled', header: '公开状态', width: 110 },
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
    <YdPageHeader
      class="entry__header"
      title="知识库"
      description="选择知识库进入专注编辑，或进入治理区维护资料、摄入与质量"
    >
      <YdButton variant="outline" :loading="store.spacesLoading.value" @click="store.reloadSpaces()">
        <YdIcon name="morph:refresh-cw" />
        刷新列表
      </YdButton>
      <YdButton @click="openCreate">
        <YdIcon name="morph:plus" />
        新建知识库
      </YdButton>
    </YdPageHeader>

    <div class="entry__filters">
      <YdInput v-model="keyword" clearable placeholder="搜索名称、slug 或描述" aria-label="搜索知识库" class="entry__search">
        <template #start>
          <YdIcon name="morph:search" />
        </template>
      </YdInput>
      <YdSelect v-model="publicFilter" :options="publicFilterOptions" aria-label="公开状态筛选" class="entry__status" />
      <YdSegmented v-model="viewMode" :options="viewModeOptions" size="sm" aria-label="展示方式" class="entry__mode" />
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
            <!-- 飞书式封面卡：封面铺满整卡不留白，名称/描述压暗角渐变，hover 底部浮出设置条 -->
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
              <div class="entry-card__cover" :style="wikiCoverStyle(item)">
                <img
                  v-if="coverOf(item).hasImage"
                  :src="coverOf(item).url"
                  :alt="coverOf(item).alt || `${item.name} 封面`"
                  :title="coverTitle(item)"
                  class="entry-card__cover-img"
                >
                <span v-else class="entry-card__cover-initial" :title="coverTitle(item)">{{ spaceInitial(item) }}</span>
                <div class="entry-card__top">
                  <YdTag :variant="item.publicReadEnabled ? 'default' : 'secondary'">
                    {{ item.publicReadEnabled ? '公开' : '私有' }}
                  </YdTag>
                  <span v-if="!coverOf(item).hasImage && coverOf(item).type === 'NONE'" class="entry-card__cover-note" :title="coverTitle(item)">未设置封面</span>
                  <span class="entry-card__cover-slug">{{ item.slug }}</span>
                </div>
                <div class="entry-card__info">
                  <strong class="entry-card__name">{{ item.name }}</strong>
                  <p class="entry-card__desc" :title="item.description || undefined">{{ item.description || '未填写描述' }}</p>
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
            </YdCard>
          </div>

          <YdResponsiveEnhancedTable
            v-else
            row-key="id"

            stripe column-visibility border
            table-class="min-w-[880px]"
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
/* 入口页用宿主页面滚动：面板只填满父级，滚动交给 .main-container 之外的本地滚动区 */
.entry {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  height: 100%;
  min-height: 0;
}

.entry__header {
  flex-shrink: 0;
  margin-bottom: 0;
}

.entry__filters {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 12px 16px;
  background: var(--color-bg-1);
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
  margin-left: auto;
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
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.entry__scroll {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.entry__inner {
  padding: 16px;
}

.entry__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

/* 卡片/正文默认内边距来自未分层工具类与适配层，同优先级拼顺序会输，用重叠选择器压掉 */
.entry-card.entry-card {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.entry-card:hover {
  box-shadow: 0 8px 20px rgb(0 0 0 / 12%);
  transform: translateY(-2px);
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

/* 封面铺满整卡：UPLOAD 呈现真实图片，SEEDED 按 seed 确定性渐变，未设置封面才回落名称哈希占位 */
.entry-card__cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background-color: var(--color-fill-2);
}

.entry-card__cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.entry-card__top {
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
  color: rgb(255 255 255 / 90%);
  background: rgb(0 0 0 / 28%);
  border-radius: 999px;
}

.entry-card__cover-initial {
  font-size: 44px;
  font-weight: 700;
  line-height: 1;
  color: rgb(255 255 255 / 88%);
}

.entry-card__cover-slug {
  padding: 1px 7px;
  margin-left: auto;
  font-size: 11px;
  color: rgb(255 255 255 / 90%);
  background: rgb(0 0 0 / 28%);
  border-radius: 999px;
}

/* 名称/描述压在封面底部的暗角渐变上，保证任何封面下文字可读 */
.entry-card__info {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  gap: 3px;
  padding: 28px 12px 10px;
  background: linear-gradient(180deg, rgb(0 0 0 / 0%) 0%, rgb(0 0 0 / 58%) 100%);
}

.entry-card__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  color: #fff;
  text-shadow: 0 1px 2px rgb(0 0 0 / 30%);
  white-space: nowrap;
}

.entry-card__desc {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: rgb(255 255 255 / 78%);
  white-space: nowrap;
}

/* hover 浮出的知识库设置条：半透明深色 + 背景模糊，从底部滑入；按钮等宽带文字标签 */
.entry-card__bar {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  gap: 2px;
  align-items: center;
  padding: 5px 8px;
  background: rgb(10 12 16 / 68%);
  opacity: 0;
  backdrop-filter: blur(8px);
  transform: translateY(100%);
  transition: opacity 0.18s ease, transform 0.18s ease;
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
  .entry__filters {
    align-items: stretch;
  }

  .entry__search,
  .entry__status {
    flex: 1 1 100%;
    max-width: none;
  }

  .entry__mode {
    margin-left: 0;
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

<script setup lang="ts">
import type { WikiIngestTask } from '@/api/modules/platform-wiki'
import { computed, inject, onMounted, ref, watch } from 'vue'
import { fetchWikiIngestTasks } from '@/api/modules/platform-wiki'
import { ingestStatusLabel, pageTypeLabel, wikiPendingCapabilities, wikiWorkbenchKey } from '../wiki-utils'

// 概览：只用现有接口与本地已加载数据呈现事实，缺失能力显式列出而不是留空或造假。
// 版面优先级：需要处理的事（失败任务）置顶；目录统计是主视觉；说明性内容折叠收尾。
const store = inject(wikiWorkbenchKey)!

const tasks = ref<WikiIngestTask[]>([])
const tasksLoading = ref(false)
const tasksError = ref('')
/** 待接入能力默认折叠：它是说明性内容，不占概览的主视线 */
const pendingOpen = ref(false)

const pages = computed(() => store.flatTree.value.filter(item => item.nodeType === 'PAGE'))
const directories = computed(() => store.flatTree.value.filter(item => item.nodeType === 'DIRECTORY'))
const published = computed(() => pages.value.filter(item => Boolean(item.publishedVersionId)))
const unpublished = computed(() => pages.value.length - published.value.length)

const typeBreakdown = computed(() => {
  const counter = new Map<string, number>()
  for (const page of pages.value) {
    const key = page.pageType || '未标记'
    counter.set(key, (counter.get(key) || 0) + 1)
  }
  return [...counter.entries()]
    .map(([key, count]) => ({ key, label: key === '未标记' ? key : pageTypeLabel(key), count }))
    .sort((left, right) => right.count - left.count)
    .slice(0, 6)
})

const taskSummary = computed(() => {
  const counter = new Map<string, number>()
  for (const task of tasks.value) {
    counter.set(task.status, (counter.get(task.status) || 0) + 1)
  }
  return [...counter.entries()]
    .map(([status, count]) => ({ status, label: ingestStatusLabel(status).label, count, color: ingestStatusLabel(status).color }))
    .sort((left, right) => right.count - left.count)
})

const failedTaskCount = computed(() => tasks.value.filter(task => task.status === 'FAILED').length)
/** 进行中的任务：除失败外唯一需要用户感知的队列状态 */
const runningTaskCount = computed(() => tasks.value.filter(task => task.status === 'RUNNING' || task.status === 'QUEUED').length)

async function loadTasks() {
  if (!store.spaceId.value) {
    tasks.value = []
    tasksError.value = ''
    return
  }
  tasksLoading.value = true
  tasksError.value = ''
  try {
    const response = await fetchWikiIngestTasks(store.spaceId.value)
    tasks.value = response.data || []
  }
  catch (exception) {
    // 失败保留错误与重试，不用「0 个任务」掩盖接口失败
    tasksError.value = exception instanceof Error ? exception.message : '摄入任务加载失败，请稍后重试'
  }
  finally {
    tasksLoading.value = false
  }
}

onMounted(loadTasks)
watch(() => store.spaceId.value, loadTasks)
</script>

<template>
  <div class="overview">
    <YdScrollArea class="overview__scroll">
      <div class="overview__inner">
        <!-- 需要处理的事排在最前：失败任务会阻塞资料进入索引，优先级高于统计数字 -->
        <section v-if="failedTaskCount" class="overview-alert" role="alert" aria-label="摄入失败提醒">
          <YdIcon name="morph:triangle-alert" class="overview-alert__icon" />
          <div class="overview-alert__text">
            <strong>{{ failedTaskCount }} 个摄入任务失败</strong>
            <p>失败任务不会进入索引，可到摄入队列重试或清理后重新入库。</p>
          </div>
          <YdButton size="sm" variant="outline" @click="store.openManage('ingest')">
            打开摄入队列
          </YdButton>
        </section>

        <section class="overview__section" aria-label="目录统计">
          <div class="overview__section-head">
            <h3 class="overview__title">
              目录统计
            </h3>
            <span class="overview__section-note">来自当前已加载的目录树，切换知识库或刷新后同步</span>
          </div>
          <div class="overview__stats">
            <YdStat title="页面" :value="pages.length" description="页面节点总数">
              <template #figure>
                <YdIcon name="morph:file-text" />
              </template>
            </YdStat>
            <YdStat title="目录" :value="directories.length" description="用于组织的目录节点">
              <template #figure>
                <YdIcon name="morph:folder" />
              </template>
            </YdStat>
            <YdStat title="已发布" :value="published.length" description="公开页可访问的页面">
              <template #figure>
                <YdIcon name="morph:circle-check" />
              </template>
            </YdStat>
            <YdStat title="未发布" :value="unpublished" description="仅工作台可见，待发布">
              <template #figure>
                <YdIcon name="morph:circle-dot" :class="{ 'overview__figure--attention': unpublished > 0 }" />
              </template>
              <template #value>
                <span :class="{ 'overview__value--attention': unpublished > 0 }">{{ unpublished }}</span>
              </template>
            </YdStat>
          </div>
        </section>

        <div class="overview__columns">
          <div class="overview__col">
            <section class="overview__section" aria-label="知识库信息">
              <div class="overview__section-head">
                <h3 class="overview__title">
                  知识库信息
                </h3>
                <div class="overview__section-actions">
                  <YdButton size="sm" @click="store.openAuthor()">
                    <YdIcon name="morph:pencil" /> 进入编辑
                  </YdButton>
                  <YdButton size="sm" variant="outline" @click="store.openManage('settings')">
                    <YdIcon name="morph:settings" /> 设置
                  </YdButton>
                </div>
              </div>
              <div class="overview__facts">
                <div class="overview-fact">
                  <span class="overview-fact__label">名称</span>
                  <span class="overview-fact__value">{{ store.space.value?.name || '-' }}</span>
                </div>
                <div class="overview-fact">
                  <span class="overview-fact__label">公开路径</span>
                  <span class="overview-fact__value">/wiki/{{ store.space.value?.slug || '-' }}</span>
                </div>
                <div class="overview-fact">
                  <span class="overview-fact__label">公开阅读</span>
                  <span class="overview-fact__value">{{ store.space.value?.publicReadEnabled ? '已开启' : '未开启' }}</span>
                </div>
                <div class="overview-fact">
                  <span class="overview-fact__label">图谱</span>
                  <span class="overview-fact__value">{{ store.space.value?.graphEnabled ? '已启用' : '未启用' }}</span>
                </div>
                <div class="overview-fact overview-fact--wide">
                  <span class="overview-fact__label">检索</span>
                  <span class="overview-fact__value">
                    topK {{ store.space.value?.topK ?? '-' }} · 分块 {{ store.space.value?.chunkSize ?? '-' }}/{{ store.space.value?.chunkOverlap ?? '-' }}
                  </span>
                </div>
                <div class="overview-fact overview-fact--wide">
                  <span class="overview-fact__label">描述</span>
                  <span class="overview-fact__value">{{ store.space.value?.description || '未填写' }}</span>
                </div>
              </div>
            </section>

            <section class="overview__section" aria-label="摄入任务摘要">
              <div class="overview__section-head">
                <h3 class="overview__title">
                  摄入任务
                </h3>
                <div class="overview__section-actions">
                  <YdButton size="sm" variant="outline" @click="store.openManage('ingest')">
                    <YdIcon name="morph:layers" /> 打开摄入队列
                  </YdButton>
                </div>
              </div>
              <div v-if="tasksLoading && !tasks.length" class="overview__loading">
                <YdLoading :size="24" text="正在加载摄入任务" />
              </div>
              <template v-else-if="tasksError">
                <YdAlert
                  variant="destructive"
                  icon="morph:triangle-alert"
                  title="摄入任务加载失败"
                  :description="tasksError"
                />
                <div class="overview__actions">
                  <YdButton size="sm" variant="outline" :loading="tasksLoading" @click="loadTasks">
                    <YdIcon name="morph:refresh-cw" /> 重新加载
                  </YdButton>
                </div>
              </template>
              <template v-else-if="tasks.length">
                <div class="overview__chips">
                  <span v-for="item in taskSummary" :key="item.status" class="overview-chip">
                    <span class="overview-chip__dot" :style="{ background: item.color }" />
                    {{ item.label }} · {{ item.count }}
                  </span>
                </div>
                <p class="overview__note">
                  <template v-if="failedTaskCount">
                    有 {{ failedTaskCount }} 个失败任务，可在摄入队列中重试或清理。
                  </template>
                  <template v-else-if="runningTaskCount">
                    {{ runningTaskCount }} 个任务进行中，完成后对应资料即可被检索到。
                  </template>
                  <template v-else>
                    最近任务没有失败记录。
                  </template>
                </p>
              </template>
              <p v-else class="overview__note">
                还没有摄入任务：到「资料库」导入文件或 URL 后会自动排队。
              </p>
            </section>
          </div>

          <div class="overview__col">
            <section v-if="typeBreakdown.length" class="overview__section" aria-label="页面类型分布">
              <div class="overview__section-head">
                <h3 class="overview__title">
                  页面类型分布
                </h3>
              </div>
              <div class="overview__chips">
                <span v-for="item in typeBreakdown" :key="item.key" class="overview-chip">
                  {{ item.label }} · {{ item.count }}
                </span>
              </div>
            </section>

            <section class="overview__section overview__section--fold" aria-label="待接入能力">
              <YdCollapsible v-model="pendingOpen">
                <template #trigger="{ open }">
                  <span class="overview-fold__head">
                    <span class="overview-fold__title">待接入能力</span>
                    <span class="overview-fold__count">{{ wikiPendingCapabilities.length }} 项</span>
                    <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" class="overview-fold__chevron" />
                  </span>
                </template>
                <p class="overview__note">
                  以下能力缺少后端契约，界面保持明确的不可用状态，不会出现假开关或假数据。
                </p>
                <ul class="overview__pending">
                  <li v-for="item in wikiPendingCapabilities" :key="item.key" class="overview-pending">
                    <span class="overview-pending__label">{{ item.label }}</span>
                    <span class="overview-pending__reason">{{ item.reason }}</span>
                  </li>
                </ul>
              </YdCollapsible>
            </section>
          </div>
        </div>
      </div>
    </YdScrollArea>
  </div>
</template>

<style scoped>
.overview {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.overview__scroll {
  height: 100%;
}

/* 全宽版面：宽屏用双栏，栏宽上限交给内容本身，不再把整页压进居中窄列 */
.overview__inner {
  display: grid;
  gap: 14px;
  width: 100%;
  max-width: 1280px;
  padding: 4px 0 32px;
  margin: 0 auto;
}

.overview__columns {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.overview__col {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.overview__section {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.overview__section--fold {
  padding: 0;
}

.overview__section-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.overview__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
}

.overview__section-note {
  font-size: 12px;
  color: var(--color-text-3);
}

.overview__section-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-left: auto;
}

/* 失败提醒：左侧色条表达优先级，不用阴影堆层级 */
.overview-alert {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-left: 3px solid rgb(var(--danger-6));
  border-radius: 8px;
}

.overview-alert__icon {
  flex-shrink: 0;
  font-size: 18px;
  color: rgb(var(--danger-6));
}

.overview-alert__text {
  display: grid;
  flex: 1;
  gap: 2px;
  min-width: 200px;
}

.overview-alert__text strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
}

.overview-alert__text p {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

/* 目录统计：四个大数字一行，宽屏四栏、中屏两栏、窄屏单栏 */
.overview__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.overview__value--attention {
  color: rgb(var(--warning-6));
}

.overview__figure--attention {
  color: rgb(var(--warning-6));
}

.overview__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
}

.overview-fact {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.overview-fact--wide {
  grid-column: 1 / -1;
}

.overview-fact__label {
  font-size: 12px;
  color: var(--color-text-3);
}

.overview-fact__value {
  font-size: 13px;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
}

.overview__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.overview__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.overview-chip {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 2px 9px;
  font-size: 12px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 999px;
}

.overview-chip__dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.overview__note {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.overview__loading {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}

/* 折叠区：折叠头就是可点区，展开内容与折叠头共用卡片内边距；
   触发器由 reka-ui 渲染为裸 <button>，这里压掉浏览器默认按钮外观 */
.overview__section--fold :deep([data-slot="collapsible-trigger"]) {
  display: block;
  width: 100%;
  padding: 0;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.overview__section--fold :deep([data-slot="collapsible-trigger"]:focus-visible) {
  outline: 2px solid var(--yd-focus-ring);
  outline-offset: -2px;
}

.overview-fold__head {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 14px 16px;
  cursor: pointer;
}

.overview-fold__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
}

.overview-fold__count {
  font-size: 12px;
  color: var(--color-text-3);
}

.overview-fold__chevron {
  margin-left: auto;
  color: var(--color-text-3);
}

.overview__section--fold :deep([data-slot="collapsible-content"]) {
  display: grid;
  gap: 10px;
  padding: 0 16px 14px;
}

.overview__pending {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.overview-pending {
  display: grid;
  gap: 2px;
}

.overview-pending__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
}

.overview-pending__reason {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

@media (width <= 1100px) {
  .overview__columns {
    grid-template-columns: minmax(0, 1fr);
  }

  .overview__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 640px) {
  .overview__stats,
  .overview__facts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

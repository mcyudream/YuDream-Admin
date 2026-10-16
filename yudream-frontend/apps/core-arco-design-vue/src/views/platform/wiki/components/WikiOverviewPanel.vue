<script setup lang="ts">
import type { WikiIngestTask } from '@/api/modules/platform-wiki'
import { computed, inject, onMounted, ref, watch } from 'vue'
import { fetchWikiIngestTasks } from '@/api/modules/platform-wiki'
import { ingestStatusLabel, pageTypeLabel, wikiPendingCapabilities, wikiWorkbenchKey } from '../wiki-utils'

// 概览：只用现有接口与本地已加载数据呈现事实，缺失能力显式列出而不是留空或造假
const store = inject(wikiWorkbenchKey)!

const tasks = ref<WikiIngestTask[]>([])
const tasksLoading = ref(false)
const tasksError = ref('')

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
        <section class="overview__section" aria-label="知识库信息">
          <h3 class="overview__title">
            知识库信息
          </h3>
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
              <span class="overview-fact__label">检索</span>
              <span class="overview-fact__value">
                topK {{ store.space.value?.topK ?? '-' }} · 分块 {{ store.space.value?.chunkSize ?? '-' }}/{{ store.space.value?.chunkOverlap ?? '-' }}
              </span>
            </div>
            <div class="overview-fact">
              <span class="overview-fact__label">图谱</span>
              <span class="overview-fact__value">{{ store.space.value?.graphEnabled ? '已启用' : '未启用' }}</span>
            </div>
            <div class="overview-fact">
              <span class="overview-fact__label">描述</span>
              <span class="overview-fact__value">{{ store.space.value?.description || '未填写' }}</span>
            </div>
          </div>
          <div class="overview__actions">
            <YdButton size="sm" @click="store.openAuthor()">
              <YdIcon name="morph:pencil" /> 进入编辑
            </YdButton>
            <YdButton size="sm" variant="outline" @click="store.openManage('sources')">
              <YdIcon name="morph:database" /> 资料库
            </YdButton>
            <YdButton size="sm" variant="outline" @click="store.openManage('settings')">
              <YdIcon name="morph:settings" /> 设置
            </YdButton>
          </div>
        </section>

        <section class="overview__section" aria-label="目录统计">
          <h3 class="overview__title">
            目录统计
          </h3>
          <div class="overview__stats">
            <YdStat title="页面" :value="pages.length" description="当前知识库的页面节点总数" />
            <YdStat title="目录" :value="directories.length" description="用于组织的目录节点" />
            <YdStat title="已发布" :value="published.length" description="有已发布版本、可在公开页访问" />
            <YdStat title="未发布" :value="unpublished" description="仅在工作台可见" />
          </div>
          <div v-if="typeBreakdown.length" class="overview__types">
            <span class="overview__types-label">页面类型分布</span>
            <span v-for="item in typeBreakdown" :key="item.key" class="overview-chip">
              {{ item.label }} · {{ item.count }}
            </span>
          </div>
          <p class="overview__note">
            统计来自当前已加载的目录树；切换知识库或刷新目录后会同步更新。
          </p>
        </section>

        <section class="overview__section" aria-label="摄入任务摘要">
          <h3 class="overview__title">
            摄入任务
          </h3>
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
            <div class="overview__types">
              <span v-for="item in taskSummary" :key="item.status" class="overview-chip" :style="{ color: item.color }">
                {{ item.label }} · {{ item.count }}
              </span>
            </div>
            <p class="overview__note">
              <template v-if="failedTaskCount">
                有 {{ failedTaskCount }} 个失败任务，可在摄入队列中重试或清理。
              </template>
              <template v-else>
                最近任务没有失败记录。
              </template>
            </p>
          </template>
          <p v-else class="overview__note">
            还没有摄入任务：到「资料库」导入文件或 URL 后会自动排队。
          </p>
          <div class="overview__actions">
            <YdButton size="sm" variant="outline" @click="store.openManage('ingest')">
              <YdIcon name="morph:layers" /> 打开摄入队列
            </YdButton>
          </div>
        </section>

        <section class="overview__section" aria-label="待接入能力">
          <h3 class="overview__title">
            待接入能力
          </h3>
          <p class="overview__note">
            以下能力缺少后端契约，界面保持明确的不可用状态，不会出现假开关或假数据。
          </p>
          <ul class="overview__pending">
            <li v-for="item in wikiPendingCapabilities" :key="item.key" class="overview-pending">
              <span class="overview-pending__label">{{ item.label }}</span>
              <span class="overview-pending__reason">{{ item.reason }}</span>
            </li>
          </ul>
        </section>
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

.overview__inner {
  display: grid;
  gap: 16px;
  max-width: 1080px;
  padding: 16px 16px 40px;
  margin: 0 auto;
}

.overview__section {
  display: grid;
  gap: 10px;
  padding: 16px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.overview__title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.overview__facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px 16px;
}

.overview-fact {
  display: grid;
  gap: 2px;
  min-width: 0;
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

.overview__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.overview__types {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.overview__types-label {
  font-size: 12px;
  color: var(--color-text-3);
}

.overview-chip {
  padding: 1px 8px;
  font-size: 11px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 999px;
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
</style>

<script setup lang="ts">
import type { WikiResearchPlan } from '@/api/modules/platform-wiki'
import { YdAgentAnswer, YdAlert, YdChatReasoning, YdThoughtChain } from '@yudream/components'
import { computed, inject, ref, watch } from 'vue'
import { planWikiResearch, startWikiResearch } from '@/api/modules/platform-wiki'
import { useAiRunTask } from '@/composables/use-ai-run-task'
import { readCompletedAiJsonField, readCompletedAiJsonItems } from '@/utils/ai-structured-stream'
import { wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()

const seed = ref('')
const plan = ref<WikiResearchPlan | null>(null)
const starting = ref(false)
const dismissedRunId = ref('')
const task = useAiRunTask(() => store.spaceId.value ? `wiki-research:${store.spaceId.value}` : undefined)
const { run: aiRun, running: planning, activities, failure } = task
const draft = computed<WikiResearchPlan | null>(() => {
  const source = aiRun.value?.structuredContent || ''
  const topic = readCompletedAiJsonField(source, 'topic')
  const rationale = readCompletedAiJsonField(source, 'rationale')
  const queries = readCompletedAiJsonItems(source, 'queries').filter((query): query is string => typeof query === 'string')
  return typeof topic === 'string' || typeof rationale === 'string' || queries.length
    ? { topic: typeof topic === 'string' ? topic : '', rationale: typeof rationale === 'string' ? rationale : '', queries }
    : null
})
const displayPlan = computed(() => aiRun.value?.id === dismissedRunId.value ? null : plan.value || draft.value)
const summaryDocument = computed(() => ({ version: 1 as const, blocks: [{ type: 'markdown' as const, text: [displayPlan.value?.topic ? `## ${displayPlan.value.topic}` : '', displayPlan.value?.rationale || ''].filter(Boolean).join('\n\n') }] }))
watch(() => [aiRun.value?.id, aiRun.value?.status, aiRun.value?.result], () => {
  const result = aiRun.value?.result
  if (aiRun.value?.status === 'COMPLETED' && typeof result?.topic === 'string' && typeof result.rationale === 'string' && Array.isArray(result.queries)) {
    plan.value = { topic: result.topic, rationale: result.rationale, queries: result.queries.filter((query): query is string => typeof query === 'string') }
  }
  else {
    plan.value = null
  }
}, { immediate: true })

async function makePlan() {
  if (planning.value) {
    return
  }
  if (!store.spaceId.value || !seed.value.trim()) {
    toast.warning('请输入研究主题')
    return
  }
  plan.value = null
  dismissedRunId.value = aiRun.value?.id || ''
  await task.start(() => planWikiResearch(store.spaceId.value, seed.value.trim()))
}

function removeQuery(index: number) {
  plan.value?.queries.splice(index, 1)
}

function dismissPlan() {
  dismissedRunId.value = aiRun.value?.id || ''
  plan.value = null
}

async function start() {
  if (!store.spaceId.value || !plan.value) {
    return
  }
  starting.value = true
  try {
    await startWikiResearch(store.spaceId.value, { topic: plan.value.topic, queries: plan.value.queries })
    toast.success('研究任务已启动，可在摄入队列查看进度')
    dismissPlan()
    seed.value = ''
    store.openPanel('ingest')
  }
  finally {
    starting.value = false
  }
}
</script>

<template>
  <div class="res-panel">
    <YdScrollArea class="res-scroll">
      <div class="res-inner">
        <YdCard class="res-hero">
          <div class="res-hero__icon">
            <YdIcon name="morph:flask-conical" />
          </div>
          <strong>深度研究</strong>
          <p>给一个研究起点，系统会规划检索查询、执行检索与网络调研，并把结果写成 Wiki 页面</p>
          <div class="res-hero__bar">
            <YdInput v-model="seed" clearable placeholder="研究起点 / 主题，例如：支付渠道的费率对比" @keydown.enter="makePlan">
              <template #start>
                <YdIcon name="morph:compass" />
              </template>
            </YdInput>
            <YdButton :loading="planning" @click="makePlan">
              <YdIcon name="morph:wand-sparkles" /> 生成研究计划
            </YdButton>
          </div>
        </YdCard>

        <div v-if="failure" class="res-error">
          <YdAlert variant="destructive" :title="failure" />
          <YdButton variant="outline" size="sm" @click="task.reconnect">
            恢复后端进度
          </YdButton>
        </div>
        <YdCard v-if="aiRun" class="res-plan">
          <p class="res-run-status">
            {{ planning ? '计划正在生成，切换页面后任务继续执行' : aiRun.status === 'COMPLETED' ? '计划已完成' : aiRun.status === 'CANCELLED' ? '计划已停止' : '计划生成失败' }}
          </p>
          <YdChatReasoning v-if="aiRun.reasoning" :content="aiRun.reasoning" :pending="planning" />
          <YdThoughtChain v-if="activities.length" :items="activities" title="研究计划进度" :default-expanded="false" />
          <YdButton v-if="planning" variant="outline" size="sm" @click="task.stop">
            停止生成
          </YdButton>
        </YdCard>
        <YdCard v-if="displayPlan" class="res-plan">
          <div class="res-plan__head">
            <YdIcon name="morph:file-text" class="res-plan__icon" />
            <div>
              <YdAgentAnswer :document="summaryDocument" />
            </div>
          </div>
          <div class="res-plan__queries">
            <span class="res-plan__queries-label">检索查询（{{ displayPlan.queries.length }}）</span>
            <span v-for="(query, index) in displayPlan.queries" :key="query" class="res-query">
              <YdIcon name="morph:search" /> {{ query }}
              <YdButton variant="ghost" size="icon-sm" :disabled="planning || !plan" aria-label="移除检索查询" @click="removeQuery(index)"><YdIcon name="morph:x" /></YdButton>
            </span>
          </div>
          <div class="res-plan__actions">
            <YdButton :loading="starting" :disabled="planning || !plan?.queries.length" @click="start">
              <YdIcon name="morph:rocket" /> 启动研究
            </YdButton>
            <YdButton variant="outline" :disabled="planning" @click="dismissPlan">
              放弃计划
            </YdButton>
          </div>
        </YdCard>
      </div>
    </YdScrollArea>
  </div>
</template>

<style scoped>
/* YdAlert 没有默认插槽：动作必须与告警相邻，不能作为子节点传入 */
.res-error {
  display: grid;
  gap: 8px;
}

.res-panel {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.res-scroll {
  height: 100%;
}

.res-inner {
  display: grid;
  gap: 14px;
  max-width: 860px;
  padding: 16px;
  margin: 0 auto;
}

.res-hero {
  text-align: center;
}

.res-hero__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin: 6px auto 12px;
  font-size: 26px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 14px;
}

.res-hero strong {
  font-size: 17px;
}

.res-hero > p {
  max-width: 460px;
  margin: 8px auto 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-3);
}

.res-hero__bar {
  display: flex;
  gap: 10px;
  max-width: 560px;
  margin: 18px auto 4px;
}

.res-plan__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.res-plan__icon {
  margin-top: 2px;
  font-size: 20px;
  color: var(--color-text-2);
}

.res-plan__head strong {
  font-size: 15px;
}

.res-plan__head p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-3);
}

.res-plan__queries {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.res-plan__queries-label {
  width: 100%;
  font-size: 12px;
  color: var(--color-text-3);
}

.res-query {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 5px 12px;
  font-size: 12px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 999px;
}

.res-run-status {
  font-size: 12px;
  color: var(--color-text-3);
}

.res-plan__actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
</style>

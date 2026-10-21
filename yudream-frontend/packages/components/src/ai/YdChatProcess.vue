<script setup lang="ts">
import type { YdChatActivity, YdChatGraphNode, YdChatRetrievalHit } from './useYdChatStream'
import { computed, ref, watch } from 'vue'
import { ydActivityPhaseLabel } from './agent-workbench'
import YdChatGraph from './YdChatGraph.vue'

const props = withDefaults(defineProps<{
  activities: YdChatActivity[]
  compact?: boolean
}>(), {
  compact: false,
})

const emits = defineEmits<{
  retrievalSelect: [hit: YdChatRetrievalHit]
  graphNodeSelect: [node: YdChatGraphNode]
}>()

const expandedRetrievals = ref<Record<number, boolean>>({})
const expandedGraphs = ref<Record<number, boolean>>({})

const visibleActivities = computed(() => props.activities.filter(activity => activity.activityType))
// 普通步骤（请求、模型、工具）折叠成一行摘要，展开才列出；知识库命中与图谱等富内容始终展示
const RICH_TYPES = ['wiki-retrieval', 'wiki-graph']
const steps = computed(() => visibleActivities.value.filter(activity => !RICH_TYPES.includes(activity.activityType || '')))
const rich = computed(() => visibleActivities.value.map((activity, index) => ({ activity, index })).filter(item => RICH_TYPES.includes(item.activity.activityType || '')))
const stepsExpanded = ref(false)
const runningStep = computed(() => [...steps.value].reverse().find(activity => activityStatus(activity) === 'running'))
const failedSteps = computed(() => steps.value.filter(activity => activityStatus(activity) === 'error').length)
const summary = computed(() => {
  if (runningStep.value) {
    return { status: 'running', icon: 'morph:loader-circle', text: `${activityLabel(runningStep.value)}${steps.value.length > 1 ? ` · 第 ${steps.value.length} 步` : ''}` }
  }
  if (failedSteps.value) {
    return { status: 'error', icon: 'morph:triangle-alert', text: `${steps.value.length} 步，${failedSteps.value} 步失败` }
  }
  return { status: 'complete', icon: 'morph:circle-check', text: `已完成 ${steps.value.length} 步` }
})

function activityStatus(activity: YdChatActivity): string {
  const status = activity.status?.trim().toLowerCase().replaceAll('_', '-')
  if (status === 'cancelled' || status === 'canceled' || status === 'stopped') return 'cancelled'
  if (status === 'error' || status === 'failed' || status === 'failure') return 'error'
  if (['complete', 'completed', 'success', 'succeeded', 'finished', 'done'].includes(status || '')) return 'complete'
  return 'running'
}

function activityIcon(activity: YdChatActivity): string {
  if (activityStatus(activity) === 'cancelled') return 'morph:circle-stop'
  if (activity.activityType === 'wiki-retrieval') return 'morph:search'
  if (activity.activityType === 'wiki-graph') return 'morph:waypoints'
  return activityStatus(activity) === 'error' ? 'morph:triangle-alert' : activityStatus(activity) === 'complete' ? 'morph:circle-check' : 'morph:loader-circle'
}

function activityLabel(activity: YdChatActivity): string {
  if (activity.title) return activity.title
  if (activity.activityType === 'wiki-retrieval') return '检索知识库'
  if (activity.activityType === 'wiki-graph') return '分析关联图谱'
  return ydActivityPhaseLabel(activity.phase) || activity.phase || '处理任务'
}

/** 说明与标题重复（仅差句末标点）时不再显示 */
function activityDetail(activity: YdChatActivity): string {
  const detail = (activity.content || (activity.activityType !== 'wiki-retrieval' ? activity.query : '') || '').trim()
  const plain = (value: string) => value.replace(/[。.!！…]+$/, '')
  return detail && plain(detail) !== plain(activityLabel(activity)) ? detail : ''
}

function scoreText(score?: number): string {
  return typeof score === 'number' ? score.toFixed(score < 1 ? 2 : 0) : ''
}

function toggleRetrieval(index: number) {
  expandedRetrievals.value[index] = !expandedRetrievals.value[index]
}

function toggleGraph(index: number) {
  expandedGraphs.value[index] = !expandedGraphs.value[index]
}

watch(() => props.activities, (activities) => {
  activities.forEach((activity, index) => {
    if (activity.activityType === 'wiki-graph' && activity.graph?.nodes.length && expandedGraphs.value[index] === undefined) {
      expandedGraphs.value[index] = true
    }
  })
}, { deep: true, immediate: true })
</script>

<template>
  <section v-if="visibleActivities.length" class="yd-chat-process" aria-label="回答过程">
    <div class="yd-chat-process__timeline">
      <template v-if="steps.length">
        <button v-if="steps.length > 1" type="button" class="yd-chat-process__summary" :class="`is-${summary.status}`" :aria-expanded="stepsExpanded" @click="stepsExpanded = !stepsExpanded">
          <FaIcon :name="summary.icon" class="yd-chat-process__status" :class="{ 'is-spinning': summary.status === 'running' }" />
          <span>{{ summary.text }}</span>
          <FaIcon :name="stepsExpanded ? 'morph:chevron-up' : 'morph:chevron-down'" class="yd-chat-process__chevron" />
        </button>
        <template v-for="(activity, index) in (steps.length > 1 && !stepsExpanded ? [] : steps)" :key="`${activity.messageId}-${activity.activityType}-step-${index}`">
          <div class="yd-chat-process__step" :class="[`is-${activityStatus(activity)}`, { 'is-nested': steps.length > 1 }]">
            <FaIcon :name="activityIcon(activity)" class="yd-chat-process__status" :class="{ 'is-spinning': activityStatus(activity) === 'running' }" />
            <div class="yd-chat-process__main">
              <div class="yd-chat-process__head" :title="activity.phase">
                <span>{{ activityLabel(activity) }}</span>
              </div>
              <p v-if="activityDetail(activity)" class="yd-chat-process__description">
                {{ activityDetail(activity) }}
              </p>
            </div>
          </div>
        </template>
      </template>
      <template v-for="{ activity, index } in rich" :key="`${activity.messageId}-${activity.activityType}-${index}`">
        <div class="yd-chat-process__step" :class="`is-${activityStatus(activity)}`">
          <FaIcon :name="activityIcon(activity)" class="yd-chat-process__status" :class="{ 'is-spinning': activityStatus(activity) === 'running' }" />
          <div class="yd-chat-process__main">
            <div class="yd-chat-process__head" :title="activity.phase">
              <span>{{ activityLabel(activity) }}</span>
            </div>
            <p v-if="activityDetail(activity)" class="yd-chat-process__description">
              {{ activityDetail(activity) }}
            </p>
          </div>
        </div>

        <div v-if="activity.activityType === 'wiki-retrieval' && activity.hits?.length" class="yd-chat-process__detail">
          <button v-if="activity.hits.length > 3" type="button" class="yd-chat-process__toggle" @click="toggleRetrieval(index)">
            <span>{{ expandedRetrievals[index] ? '收起命中' : `展开 ${activity.hits.length} 条命中` }}</span>
            <FaIcon :name="expandedRetrievals[index] ? 'morph:chevron-up' : 'morph:chevron-down'" />
          </button>
          <div class="yd-chat-process__hits">
            <button
              v-for="hit in activity.hits.slice(0, expandedRetrievals[index] ? undefined : 3)"
              :key="`${hit.nodeId || hit.path || hit.title}-${hit.excerpt || ''}`"
              type="button"
              class="yd-chat-hit"
              @click="emits('retrievalSelect', hit)"
            >
              <span class="yd-chat-hit__head">
                <strong>{{ hit.title }}</strong>
                <small v-if="hit.kind">{{ hit.kind }}</small>
                <small v-if="scoreText(hit.score)">{{ scoreText(hit.score) }}</small>
              </span>
              <span v-if="hit.excerpt" class="yd-chat-hit__excerpt">{{ hit.excerpt }}</span>
            </button>
          </div>
        </div>

        <div v-if="activity.activityType === 'wiki-graph' && activity.graph" class="yd-chat-process__detail">
          <button type="button" class="yd-chat-process__toggle" @click="toggleGraph(index)">
            <span>{{ activity.graph.nodes.length }} 个节点 · {{ activity.graph.edges.length }} 条关系</span>
            <FaIcon :name="expandedGraphs[index] ? 'morph:chevron-up' : 'morph:chevron-down'" />
          </button>
          <YdChatGraph
            v-if="expandedGraphs[index]"
            :graph="activity.graph"
            :compact="compact"
            @node-select="emits('graphNodeSelect', $event)"
          />
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.yd-chat-process {
  margin-bottom: 10px;
  color: var(--color-text-2);
  font-size: 12px;
}

.yd-chat-process__timeline {
  display: grid;
  gap: 8px;
}

.yd-chat-process__step {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.yd-chat-process__status {
  width: 16px;
  margin-top: 1px;
  color: var(--color-text-3);
  font-size: 15px;
}

.yd-chat-process__step.is-running .yd-chat-process__status { color: rgb(var(--primary-6)); }
.yd-chat-process__step.is-complete .yd-chat-process__status { color: rgb(var(--success-6)); }
.yd-chat-process__step.is-cancelled .yd-chat-process__status { color: var(--color-text-3); }
.yd-chat-process__step.is-error .yd-chat-process__status { color: rgb(var(--danger-6)); }
.yd-chat-process__status.is-spinning { animation: yd-chat-process-spin 1s linear infinite; }

.yd-chat-process__main { min-width: 0; }
.yd-chat-process__head { display: flex; align-items: center; gap: 6px; min-height: 18px; color: var(--color-text-2); font-weight: 500; }
.yd-chat-process__summary { display: inline-flex; align-items: center; gap: 8px; justify-self: start; max-width: 100%; padding: 2px 0; border: 0; background: transparent; color: var(--color-text-2); cursor: pointer; font: inherit; font-weight: 500; text-align: left; }
.yd-chat-process__summary span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.yd-chat-process__summary.is-running .yd-chat-process__status { color: rgb(var(--primary-6)); }
.yd-chat-process__summary.is-complete .yd-chat-process__status { color: rgb(var(--success-6)); }
.yd-chat-process__summary.is-error .yd-chat-process__status { color: rgb(var(--danger-6)); }
.yd-chat-process__chevron { color: var(--color-text-3); font-size: 12px; }
.yd-chat-process__step.is-nested { margin-left: 8px; padding-left: 14px; border-left: 1px solid var(--color-border-2); }
.yd-chat-process__description { margin: 2px 0 0; color: var(--color-text-3); line-height: 1.55; }
.yd-chat-process__detail { margin: -3px 0 2px 24px; }
.yd-chat-process__toggle { display: inline-flex; align-items: center; gap: 3px; padding: 3px 0; border: 0; background: transparent; color: rgb(var(--primary-6)); cursor: pointer; font: inherit; font-size: 11px; }
.yd-chat-process__hits { display: grid; gap: 4px; margin-top: 3px; }
.yd-chat-hit { display: grid; gap: 2px; width: 100%; padding: 6px 8px; border: 1px solid var(--color-border-2); border-radius: 8px; background: transparent; color: var(--color-text-2); cursor: pointer; font: inherit; text-align: left; }
.yd-chat-hit:hover { border-color: rgba(var(--primary-6), 0.45); background: var(--color-fill-1); }
.yd-chat-hit__head { display: flex; align-items: center; gap: 6px; min-width: 0; }
.yd-chat-hit__head strong { overflow: hidden; color: var(--color-text-1); font-size: 12px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.yd-chat-hit__head small { flex-shrink: 0; color: var(--color-text-3); font-size: 11px; }
.yd-chat-hit__excerpt { display: -webkit-box; overflow: hidden; color: var(--color-text-3); font-size: 11px; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }

@keyframes yd-chat-process-spin { to { transform: rotate(360deg); } }
</style>
